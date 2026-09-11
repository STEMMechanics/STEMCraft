package dev.stemcraft.feature;

import dev.stemcraft.api.service.database.DatabaseService;

import javax.annotation.Nullable;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Compact persistence for NamedRegions biome samples.
 *
 * One database row represents one Minecraft chunk. The sixteen 4x4-block biome
 * samples inside that chunk are stored as integer region IDs rather than repeated
 * public UUID strings. Region merges are aliases, so merges never rewrite all
 * previously discovered cells.
 */
final class NamedRegionStorage {
    static final int CELLS_PER_AXIS = 4;
    static final int CELLS_PER_CHUNK = CELLS_PER_AXIS * CELLS_PER_AXIS;
    static final int SCHEMA_VERSION = 6;

    record Cell(String world, int cellX, int cellZ) {}
    private record ChunkKey(int world,int x,int z) {}

    private static final class RegionChunk {
        final int[] cells;

        RegionChunk() {
            this.cells = new int[CELLS_PER_CHUNK];
        }

        RegionChunk(int[] cells) {
            this.cells = cells;
        }

        RegionChunk with(int index, int regionNumber) {
            int[] copy = Arrays.copyOf(cells, cells.length);
            copy[index] = regionNumber;
            return new RegionChunk(copy);
        }

        boolean complete() {
            for (int value : cells) if (value == 0) return false;
            return true;
        }
    }

    private final DatabaseService database;
    private final Logger logger;

    private final Map<String,Integer> worldNumbers = new ConcurrentHashMap<>();
    private final Map<Integer,String> worldNames = new ConcurrentHashMap<>();

    private final Map<String,Integer> regionNumbers = new ConcurrentHashMap<>();
    private final Map<Integer,String> regionIds = new ConcurrentHashMap<>();
    private final Map<Integer,Integer> mergedRegions = new ConcurrentHashMap<>();

    /** world number -> packed chunk coordinate -> sixteen region numbers */
    private final Map<Integer,ConcurrentHashMap<Long,RegionChunk>> chunks = new ConcurrentHashMap<>();
    private final Set<ChunkKey> dirtyChunks = ConcurrentHashMap.newKeySet();

    NamedRegionStorage(DatabaseService database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    void initialise() {
        createTables();
        migrateLegacyCells();
        load();
    }

    void clear() {
        worldNumbers.clear();
        worldNames.clear();
        regionNumbers.clear();
        regionIds.clear();
        mergedRegions.clear();
        chunks.clear();
        dirtyChunks.clear();
    }

    private void createTables() {
        requireExecute("""
            CREATE TABLE IF NOT EXISTS named_region_worlds (
                world_id INTEGER PRIMARY KEY,
                world_name TEXT NOT NULL UNIQUE
            )
            """);

        requireExecute("""
            CREATE TABLE IF NOT EXISTS named_region_ids (
                region_num INTEGER PRIMARY KEY,
                region_id TEXT NOT NULL UNIQUE
            )
            """);

        StringBuilder sql = new StringBuilder("""
            CREATE TABLE IF NOT EXISTS named_region_chunks (
                world_id INTEGER NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL
            """);
        for (int i = 0; i < CELLS_PER_CHUNK; i++)
            sql.append(", c").append(i).append(" INTEGER");
        sql.append(", PRIMARY KEY(world_id,chunk_x,chunk_z)) WITHOUT ROWID");
        requireExecute(sql.toString());

        requireExecute("""
            CREATE TABLE IF NOT EXISTS named_region_merges (
                loser_num INTEGER PRIMARY KEY,
                winner_num INTEGER NOT NULL
            ) WITHOUT ROWID
            """);
    }

    /**
     * Migrates schema v5's one-row-per-cell table into one-row-per-chunk storage.
     *
     * SQLite bit shifting gives floor-style division by four for signed integers,
     * while "& 3" gives a stable local coordinate in the 0..3 range, including
     * negative Minecraft coordinates.
     *
     * The conversion is intentionally performed as one INSERT..SELECT GROUP BY
     * inside SQLite instead of issuing millions of Java-side update statements.
     */
    private void migrateLegacyCells() {
        if (database.migrationVersion("named-regions") >= SCHEMA_VERSION) return;

        logger.info("Migrating NamedRegions biome storage to compact chunk format (schema v6)...");

        if (!database.execute("BEGIN IMMEDIATE"))
            throw new IllegalStateException("Could not begin NamedRegions compact-storage migration");

        try {
            requireExecute("""
                INSERT OR IGNORE INTO named_region_worlds(world_name)
                SELECT DISTINCT world_name
                FROM named_region_cells
                """);

            requireExecute("""
                INSERT OR IGNORE INTO named_region_ids(region_id)
                SELECT DISTINCT region_id
                FROM named_region_cells
                """);

            requireExecute("""
                INSERT OR IGNORE INTO named_region_ids(region_id)
                SELECT id
                FROM named_areas
                WHERE kind='region'
                """);

            StringBuilder columns = new StringBuilder("world_id,chunk_x,chunk_z");
            StringBuilder values = new StringBuilder(
                    "w.world_id, (c.cell_x >> 2), (c.cell_z >> 2)");

            for (int i = 0; i < CELLS_PER_CHUNK; i++) {
                columns.append(",c").append(i);
                values.append(",MAX(CASE WHEN (((c.cell_z & 3) * 4) + (c.cell_x & 3))=")
                        .append(i)
                        .append(" THEN r.region_num END)");
            }

            String migration = """
                INSERT OR REPLACE INTO named_region_chunks(%s)
                SELECT %s
                FROM named_region_cells c
                JOIN named_region_worlds w ON w.world_name=c.world_name
                JOIN named_region_ids r ON r.region_id=c.region_id
                GROUP BY w.world_id,(c.cell_x >> 2),(c.cell_z >> 2)
                """.formatted(columns, values);

            requireExecute(migration);

            // Drop the high-volume legacy structures only after the compact table is populated.
            requireExecute("DROP INDEX IF EXISTS idx_named_region_cells_region_id");
            requireExecute("DROP TABLE IF EXISTS named_region_cells");

            requireExecute("INSERT INTO migrations(name,version) VALUES ('named-regions'," + SCHEMA_VERSION
                    + ") ON CONFLICT(name) DO UPDATE SET version=excluded.version");

            if (!database.execute("COMMIT"))
                throw new IllegalStateException("Could not commit NamedRegions compact-storage migration");
        } catch (RuntimeException failure) {
            database.execute("ROLLBACK");
            throw failure;
        }

        database.execute("PRAGMA optimize");

        logger.info("NamedRegions compact chunk migration complete. "
                + "Automatic startup compaction will reclaim eligible free pages on the next restart (database.compact-on-startup).");
    }

    private void requireExecute(String sql) {
        if (!database.execute(sql))
            throw new IllegalStateException("NamedRegions compact-storage migration failed");
    }

    private void load() {
        clear();

        database.queryEach("SELECT world_id,world_name FROM named_region_worlds", null, rs -> {
            int number = rs.getInt(1);
            String name = rs.getString(2);
            worldNumbers.put(name, number);
            worldNames.put(number, name);
        });

        database.queryEach("SELECT region_num,region_id FROM named_region_ids", null, rs -> {
            int number = rs.getInt(1);
            String id = rs.getString(2);
            regionNumbers.put(id, number);
            regionIds.put(number, id);
        });

        database.queryEach("SELECT loser_num,winner_num FROM named_region_merges", null, rs ->
                mergedRegions.put(rs.getInt(1), rs.getInt(2)));

        StringBuilder sql = new StringBuilder("SELECT world_id,chunk_x,chunk_z");
        for (int i = 0; i < CELLS_PER_CHUNK; i++) sql.append(",c").append(i);
        sql.append(" FROM named_region_chunks");

        database.queryEach(sql.toString(), null, rs -> {
            int world = rs.getInt(1);
            int chunkX = rs.getInt(2);
            int chunkZ = rs.getInt(3);
            int[] cells = new int[CELLS_PER_CHUNK];

            for (int i = 0; i < CELLS_PER_CHUNK; i++) {
                int value = rs.getInt(4 + i);
                cells[i] = rs.wasNull() ? 0 : value;
            }

            chunks.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>())
                    .put(pack(chunkX, chunkZ), new RegionChunk(cells));
        });
    }

    @Nullable String cell(String world, int cellX, int cellZ) {
        Integer worldNumber = worldNumbers.get(world);
        if (worldNumber == null) return null;

        int chunkX = Math.floorDiv(cellX, CELLS_PER_AXIS);
        int chunkZ = Math.floorDiv(cellZ, CELLS_PER_AXIS);
        Map<Long,RegionChunk> worldChunks = chunks.get(worldNumber);
        if (worldChunks == null) return null;
        RegionChunk chunk = worldChunks.get(pack(chunkX, chunkZ));
        if (chunk == null) return null;

        int index = localIndex(cellX, cellZ);
        int number = chunk.cells[index];
        if (number == 0) return null;

        String id = regionIds.get(resolveNumber(number));
        return id != null ? id : regionIds.get(number);
    }

    void setCell(String world, int cellX, int cellZ, String regionId, boolean persist) {
        int worldNumber = ensureWorldNumber(world);
        int regionNumber = ensureRegionNumber(regionId);
        int chunkX = Math.floorDiv(cellX, CELLS_PER_AXIS);
        int chunkZ = Math.floorDiv(cellZ, CELLS_PER_AXIS);
        long packed = pack(chunkX, chunkZ);

        ConcurrentHashMap<Long,RegionChunk> worldChunks =
                chunks.computeIfAbsent(worldNumber, ignored -> new ConcurrentHashMap<>());
        RegionChunk old = worldChunks.get(packed);
        if (old == null) old = new RegionChunk();

        RegionChunk updated = old.with(localIndex(cellX, cellZ), regionNumber);
        if (persist) saveChunk(worldNumber, chunkX, chunkZ, updated);
        worldChunks.put(packed, updated);
        ChunkKey key = new ChunkKey(worldNumber,chunkX,chunkZ);
        if (persist) dirtyChunks.remove(key);
        else dirtyChunks.add(key);
    }

    void flushChunk(String world, int chunkX, int chunkZ) {
        Integer worldNumber = worldNumbers.get(world);
        if (worldNumber == null) return;
        ChunkKey key = new ChunkKey(worldNumber,chunkX,chunkZ);
        if (!dirtyChunks.contains(key)) return;

        Map<Long,RegionChunk> worldChunks = chunks.get(worldNumber);
        if (worldChunks == null) return;
        RegionChunk chunk = worldChunks.get(pack(chunkX, chunkZ));
        if (chunk != null) {
            saveChunk(worldNumber, chunkX, chunkZ, chunk);
            dirtyChunks.remove(key);
        }
    }

    boolean completeChunk(String world, int chunkX, int chunkZ) {
        Integer worldNumber = worldNumbers.get(world);
        if (worldNumber == null) return false;

        Map<Long,RegionChunk> worldChunks = chunks.get(worldNumber);
        if (worldChunks == null) return false;
        RegionChunk chunk = worldChunks.get(pack(chunkX, chunkZ));
        return chunk != null && chunk.complete();
    }

    void alias(String loserId, String winnerId) {
        int loser = ensureRegionNumber(loserId);
        int winner = resolveNumber(ensureRegionNumber(winnerId));
        if (loser == winner) return;

        int saved = database.update("""
            INSERT INTO named_region_merges(loser_num,winner_num)
            VALUES(?,?)
            ON CONFLICT(loser_num) DO UPDATE SET winner_num=excluded.winner_num
            """, ps -> {
            ps.setInt(1, loser);
            ps.setInt(2, winner);
        });
        if (saved != 1) throw new IllegalStateException("Could not persist NamedRegions merge alias");
        mergedRegions.put(loser, winner);
    }

    String resolve(String id) {
        Integer number = regionNumbers.get(id);
        if (number == null) return id;

        int resolved = resolveNumber(number);
        String resolvedId = regionIds.get(resolved);
        return resolvedId == null ? id : resolvedId;
    }

    void forEachCell(BiConsumer<Cell,String> consumer) {
        for (var worldEntry : chunks.entrySet()) {
            String world = worldNames.get(worldEntry.getKey());
            if (world == null) continue;

            for (var chunkEntry : worldEntry.getValue().entrySet()) {
                int chunkX = unpackX(chunkEntry.getKey());
                int chunkZ = unpackZ(chunkEntry.getKey());
                int[] cells = chunkEntry.getValue().cells;

                for (int i = 0; i < CELLS_PER_CHUNK; i++) {
                    int region = cells[i];
                    if (region == 0) continue;

                    String id = regionIds.get(resolveNumber(region));
                    if (id == null) continue;

                    int localX = i & 3;
                    int localZ = i >> 2;
                    consumer.accept(
                            new Cell(world, chunkX * CELLS_PER_AXIS + localX,
                                    chunkZ * CELLS_PER_AXIS + localZ),
                            id
                    );
                }
            }
        }
    }

    private synchronized int ensureWorldNumber(String world) {
        Integer current = worldNumbers.get(world);
        if (current != null) return current;

        database.update("INSERT OR IGNORE INTO named_region_worlds(world_name) VALUES(?)",
                ps -> ps.setString(1, world));

        final int[] result = {0};
        database.querySingle("SELECT world_id FROM named_region_worlds WHERE world_name=?",
                ps -> ps.setString(1, world), rs -> result[0] = rs.getInt(1));

        if (result[0] == 0)
            throw new IllegalStateException("Could not allocate NamedRegions world ID for " + world);

        worldNumbers.put(world, result[0]);
        worldNames.put(result[0], world);
        return result[0];
    }

    private synchronized int ensureRegionNumber(String id) {
        Integer current = regionNumbers.get(id);
        if (current != null) return current;

        database.update("INSERT OR IGNORE INTO named_region_ids(region_id) VALUES(?)",
                ps -> ps.setString(1, id));

        final int[] result = {0};
        database.querySingle("SELECT region_num FROM named_region_ids WHERE region_id=?",
                ps -> ps.setString(1, id), rs -> result[0] = rs.getInt(1));

        if (result[0] == 0)
            throw new IllegalStateException("Could not allocate compact NamedRegions ID for " + id);

        regionNumbers.put(id, result[0]);
        regionIds.put(result[0], id);
        return result[0];
    }

    private int resolveNumber(int start) {
        if (!mergedRegions.containsKey(start)) return start;
        int current = start;
        Set<Integer> visited = new HashSet<>();
        while (true) {
            if (!visited.add(current))
                throw new IllegalStateException("NamedRegions merge alias cycle detected at " + start);
            Integer next = mergedRegions.get(current);
            if (next == null) break;
            current = next;
        }
        for (int number : visited)
            if (number != current) mergedRegions.put(number, current);
        return current;
    }

    private void saveChunk(int worldNumber, int chunkX, int chunkZ, RegionChunk chunk) {
        StringBuilder columns = new StringBuilder("world_id,chunk_x,chunk_z");
        StringBuilder placeholders = new StringBuilder("?,?,?");
        StringBuilder updates = new StringBuilder();

        for (int i = 0; i < CELLS_PER_CHUNK; i++) {
            columns.append(",c").append(i);
            placeholders.append(",?");
            if (i > 0) updates.append(',');
            updates.append("c").append(i).append("=excluded.c").append(i);
        }

        String sql = "INSERT INTO named_region_chunks(" + columns + ") VALUES(" + placeholders + ") "
                + "ON CONFLICT(world_id,chunk_x,chunk_z) DO UPDATE SET " + updates;

        int saved = database.update(sql, ps -> {
            ps.setInt(1, worldNumber);
            ps.setInt(2, chunkX);
            ps.setInt(3, chunkZ);

            for (int i = 0; i < CELLS_PER_CHUNK; i++) {
                int value = chunk.cells[i];
                if (value == 0) ps.setNull(4 + i, Types.INTEGER);
                else ps.setInt(4 + i, value);
            }
        });
        if (saved != 1) throw new IllegalStateException("Could not persist NamedRegions chunk");
    }

    private static int localIndex(int cellX, int cellZ) {
        return Math.floorMod(cellZ, CELLS_PER_AXIS) * CELLS_PER_AXIS
                + Math.floorMod(cellX, CELLS_PER_AXIS);
    }

    private static long pack(int x, int z) {
        return ((long)x << 32) | (z & 0xffffffffL);
    }

    private static int unpackX(long value) {
        return (int)(value >> 32);
    }

    private static int unpackZ(long value) {
        return (int)value;
    }
}
