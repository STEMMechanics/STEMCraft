/* STEMCraft - Minecraft Plugin */
package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.coordinatebar.CoordinateBarSection;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.config.BundledConfigDefaults;
import dev.stemcraft.integration.pl3xmap.NamedMapArea;
import dev.stemcraft.integration.pl3xmap.Pl3xMapNamedRegions;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Permanently names procedurally discovered biome territories and generated structures. */
public final class NamedRegions extends BaseFeature {
    private static final List<String> DEFAULT_MAP_PALETTE=List.of(
        "#4054A0FF","#405BCB8A","#40F2C14E","#40E87979",
        "#40B983E3","#404CC9C0","#40ED8F4A","#4088B04B"
    );

    private record Area(
        String id,
        String world,
        String kind,
        String type,
        String name,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean locked,
        long createdAt,
        boolean discovered
    ) {
        boolean contains(Location l) {
            return world.equals(l.getWorld().getName())
                && l.getX() >= minX && l.getX() <= maxX
                && l.getY() >= minY && l.getY() <= maxY
                && l.getZ() >= minZ && l.getZ() <= maxZ;
        }
    }

    private static final String REGION="region",STRUCTURE="structure";
    private static final String COORDINATE_PROVIDER_ID="named-region";
    private static final int SAMPLE_SIZE=4;
    private static final Pattern REGION_FILE=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private static final class ScanCursor {
        final World world;
        final int regionX,regionZ;
        final BitSet generated;
        int index;

        ScanCursor(World world,int regionX,int regionZ,BitSet generated) {
            this.world=world;
            this.regionX=regionX;
            this.regionZ=regionZ;
            this.generated=generated;
        }
    }

    private final Map<String,Area> areas=new ConcurrentHashMap<>();
    private final Set<String> activeNames=ConcurrentHashMap.newKeySet();
    private final Map<String,Long> retiredNames=new ConcurrentHashMap<>();
    private final Map<UUID,String> playerArea=new HashMap<>();
    private final Set<String> worlds=new HashSet<>();
    private NamedRegionStorage regionStorage;

    private final Object mapSnapshotLock=new Object();
    private volatile Collection<NamedMapArea> mapSnapshot=List.of();
    private volatile boolean mapSnapshotDirty=true;
    private volatile long mapSnapshotBuiltAt;
    private long mapRefreshMillis=300_000L;

    private Pl3xMapNamedRegions mapLayer;
    private BukkitTask backfillTask;
    private BukkitTask regenerationTask;
    private String titleTemplate,subtitleTemplate;
    private long fadeInMillis,stayMillis,fadeOutMillis;
    private long retirementMillis;

    public NamedRegions(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        restoreMissingNameConfiguration();

        worlds.addAll(getConfigSection().getStringList("worlds").stream()
            .map(s->s.toLowerCase(Locale.ROOT)).toList());
        if(worlds.isEmpty()) worlds.add("survival");

        titleTemplate=getConfigSection().getString("display.title","");
        subtitleTemplate=getConfigSection().getString("display.subtitle","<gold>{name}</gold>");
        fadeInMillis=Math.max(0,getConfigSection().getLong("display.fade-in",500));
        stayMillis=Math.max(0,getConfigSection().getLong("display.stay",5000));
        fadeOutMillis=Math.max(0,getConfigSection().getLong("display.fade-out",1000));
        retirementMillis=Math.max(0,getConfigSection().getLong("names.retirement-days",5))*86_400_000L;

        migrateDevelopmentSchema();
        createTable();
        migrateDiscoverySchema();

        // Name migrations only need areas/history, not the old multi-million-row cell table.
        loadAreas();
        migrateLongGeneratedNames();
        migrateNumberedGeneratedNames();

        // Schema v6 converts the old one-row-per-cell table and then loads one compact object per chunk.
        regionStorage=new NamedRegionStorage(api.database(),STEMCraft.getPlugin().getLogger());
        regionStorage.initialise();

        if(getConfigSection().getBoolean("coordbar.enabled",true))
            api.coordinateBar().registerAmendment(
                STEMCraft.getPlugin(),
                COORDINATE_PROVIDER_ID,
                CoordinateBarSection.WORLD,
                50,
                this::renderCoordinateBar
            );

        api.events().register(ChunkLoadEvent.class,event->discover(event.getChunk()));
        api.events().register(PlayerMoveEvent.class,this::onMove);
        api.tabComplete().register("named-area",(player,args)->new ArrayList<>(areas.keySet()));

        api.commands().create("namedregion")
            .usage("/namedregion <info|list|find|nearby|teleport|rename|retired|fallbacks|release|regenerate>")
            .description("Inspect or rename generated regions.")
            .permission("stemcraft.command.namedregion")
            .tabCompletion("info")
            .tabCompletion("list")
            .tabCompletion("find")
            .tabCompletion("nearby")
            .tabCompletion("teleport","{named-area}")
            .tabCompletion("rename","{named-area}")
            .tabCompletion("retired")
            .tabCompletion("fallbacks")
            .tabCompletion("fallbacks","*")
            .tabCompletion("release","*")
            .tabCompletion("regenerate","*")
            .executor((unused,cmd,ctx)->executeCommand(ctx))
            .register(STEMCraft.getPlugin());

        for(World world:Bukkit.getWorlds())
            if(enabled(world))
                for(Chunk chunk:world.getLoadedChunks())
                    discover(chunk);

        enableMap();
        startBackfill();
    }

    private void restoreMissingNameConfiguration() {
        getConfigSection();
        String base=getResolvedConfigPath()+".names.";

        for(String section:List.of("sources","forms")) {
            String path=base+section;
            if(!getRootConfigSection().isSection(path))
                BundledConfigDefaults.restoreMissingSection(
                    STEMCraft.getPlugin(),
                    getRootConfigSection(),
                    List.of(path)
                );
        }
    }

    @Override
    public void onDisable() {
        api.coordinateBar().unregisterAmendment(
            STEMCraft.getPlugin(),
            COORDINATE_PROVIDER_ID,
            CoordinateBarSection.WORLD
        );

        if(mapLayer!=null) mapLayer.disable();
        if(backfillTask!=null) backfillTask.cancel();
        if(regenerationTask!=null) regenerationTask.cancel();

        backfillTask=null;
        regenerationTask=null;
        areas.clear();
        activeNames.clear();
        retiredNames.clear();
        playerArea.clear();
        if(regionStorage!=null) regionStorage.clear();

        mapSnapshot=List.of();
        mapSnapshotDirty=true;
        mapSnapshotBuiltAt=0;
    }

    private void migrateDevelopmentSchema() {
        if(api.database().migrationVersion("named-regions")>=2) return;

        api.database().execute("DROP TABLE IF EXISTS named_region_chunks");
        api.database().execute("DROP TABLE IF EXISTS named_region_merges");
        api.database().execute("DROP TABLE IF EXISTS named_region_ids");
        api.database().execute("DROP TABLE IF EXISTS named_region_worlds");
        api.database().execute("DROP TABLE IF EXISTS named_region_cells");
        api.database().execute("DROP TABLE IF EXISTS named_region_name_history");
        api.database().execute("DROP TABLE IF EXISTS named_areas");
        api.database().setMigrationVersion("named-regions",2);
    }

    private void migrateDiscoverySchema() {
        if(api.database().migrationVersion("named-regions")>=3) return;

        api.database().execute(
            "ALTER TABLE named_areas ADD COLUMN discovered INTEGER NOT NULL DEFAULT 1");
        api.database().execute(
            "UPDATE named_areas SET discovered=0 WHERE kind='structure'");
        api.database().setMigrationVersion("named-regions",3);
    }

    private void createTable() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS named_areas (
                id TEXT PRIMARY KEY,
                world_name TEXT NOT NULL,
                kind TEXT NOT NULL,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                min_x INTEGER NOT NULL,
                min_y INTEGER NOT NULL,
                min_z INTEGER NOT NULL,
                max_x INTEGER NOT NULL,
                max_y INTEGER NOT NULL,
                max_z INTEGER NOT NULL,
                locked INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """);

        /*
         * Keep the legacy table available only until schema v6 runs. Existing v5 databases
         * need it as the migration source; v6+ databases must not recreate it.
         */
        if(api.database().migrationVersion("named-regions")<NamedRegionStorage.SCHEMA_VERSION) {
            api.database().execute("""
                CREATE TABLE IF NOT EXISTS named_region_cells (
                    world_name TEXT NOT NULL,
                    cell_x INTEGER NOT NULL,
                    cell_z INTEGER NOT NULL,
                    region_id TEXT NOT NULL,
                    PRIMARY KEY(world_name,cell_x,cell_z)
                )
                """);
            api.database().execute(
                "CREATE INDEX IF NOT EXISTS idx_named_region_cells_region_id "
                    +"ON named_region_cells(region_id)");
        }

        api.database().execute("""
            CREATE TABLE IF NOT EXISTS named_region_name_history (
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                former_region_id TEXT NOT NULL,
                assigned_at INTEGER NOT NULL,
                retired_at INTEGER NOT NULL,
                available_at INTEGER NOT NULL,
                reason TEXT NOT NULL
            )
            """);
    }

    private void loadAreas() {
        areas.clear();
        activeNames.clear();
        retiredNames.clear();

        api.database().queryEach(
            "SELECT id,world_name,kind,type,name,min_x,min_y,min_z,max_x,max_y,max_z,"
                +"locked,created_at,discovered FROM named_areas",
            null,
            rs->{
                Area area=read(rs);
                areas.put(area.id,area);
                activeNames.add(normaliseName(area.name));
            }
        );

        long now=System.currentTimeMillis();
        api.database().queryEach(
            "SELECT name,available_at FROM named_region_name_history WHERE available_at>?",
            ps->ps.setLong(1,now),
            rs->retiredNames.merge(normaliseName(rs.getString(1)),rs.getLong(2),Math::max)
        );
    }

    private Area read(ResultSet r) throws SQLException {
        return new Area(
            r.getString(1),r.getString(2),r.getString(3),r.getString(4),
            r.getString(5),r.getInt(6),r.getInt(7),r.getInt(8),
            r.getInt(9),r.getInt(10),r.getInt(11),
            r.getInt(12)!=0,r.getLong(13),r.getInt(14)!=0
        );
    }

    private void migrateLongGeneratedNames() {
        if(api.database().migrationVersion("named-regions")>=4) return;

        int renamed=0;
        for(Area old:new ArrayList<>(areas.values())) {
            boolean numberedFallback=isNumberedFallback(old.name);
            if(old.locked
                ||wordCount(old.name)<=nameWordLimit(old.type)&&!numberedFallback)
                continue;

            activeNames.remove(normaliseName(old.name));
            String replacement=availableName(
                numberedFallback?List.of():shortNameCandidates(old.name,nameWordLimit(old.type)),
                old.type,
                old.id
            );

            retire(old,numberedFallback
                ?"replaced numbered fallback"
                :"shortened by name word-limit migration");

            Area updated=new Area(
                old.id,old.world,old.kind,old.type,replacement,
                old.minX,old.minY,old.minZ,old.maxX,old.maxY,old.maxZ,
                false,old.createdAt,old.discovered
            );

            areas.put(updated.id,updated);
            activeNames.add(normaliseName(updated.name));

            api.database().update(
                "UPDATE named_areas SET name=? WHERE id=?",
                ps->{
                    ps.setString(1,updated.name);
                    ps.setString(2,updated.id);
                }
            );
            renamed++;
        }

        api.database().setMigrationVersion("named-regions",4);
        if(renamed>0) {
            STEMCraft.getPlugin().getLogger().info(
                "Normalised "+renamed+" generated region name(s).");
            invalidateMapSnapshot();
        }
    }

    /**
     * Version 4 originally only shortened names containing more than two words.
     * Numbered fallback names were added later, so they need their own migration.
     */
    private void migrateNumberedGeneratedNames() {
        if(api.database().migrationVersion("named-regions")>=5) return;

        int renamed=0;
        for(Area old:new ArrayList<>(areas.values())) {
            if(old.locked||!isNumberedFallback(old.name)) continue;

            activeNames.remove(normaliseName(old.name));
            String replacement=availableName(List.of(),old.type,old.id);
            retire(old,"replaced numbered fallback");

            Area updated=new Area(
                old.id,old.world,old.kind,old.type,replacement,
                old.minX,old.minY,old.minZ,old.maxX,old.maxY,old.maxZ,
                false,old.createdAt,old.discovered
            );

            areas.put(updated.id,updated);
            activeNames.add(normaliseName(updated.name));

            api.database().update(
                "UPDATE named_areas SET name=? WHERE id=?",
                ps->{
                    ps.setString(1,updated.name);
                    ps.setString(2,updated.id);
                }
            );
            renamed++;
        }

        api.database().setMigrationVersion("named-regions",5);
        if(renamed>0) {
            STEMCraft.getPlugin().getLogger().info(
                "Replaced "+renamed+" numbered generated region name(s).");
            invalidateMapSnapshot();
        }
    }

    private void discover(Chunk chunk) {
        if(!enabled(chunk.getWorld())) return;

        int baseX=chunk.getX()*16/SAMPLE_SIZE;
        int baseZ=chunk.getZ()*16/SAMPLE_SIZE;

        /*
         * Update all sixteen samples in memory first, then persist the whole chunk once.
         * This removes the old 16 INSERT/REPLACE statements per discovered chunk.
         */
        for(int dx=0;dx<16/SAMPLE_SIZE;dx++)
            for(int dz=0;dz<16/SAMPLE_SIZE;dz++)
                ensureCell(chunk.getWorld(),baseX+dx,baseZ+dz,false);

        regionStorage.flushChunk(chunk.getWorld().getName(),chunk.getX(),chunk.getZ());

        for(GeneratedStructure structure:chunk.getStructures())
            ensureStructure(chunk.getWorld(),structure);
    }

    private void startBackfill() {
        if(!getConfigSection().getBoolean("map.backfill-existing-chunks",true)) return;

        Deque<ScanCursor> queue=new ArrayDeque<>();

        for(World world:Bukkit.getWorlds()) {
            if(!enabled(world)) continue;

            Path folder=world.getWorldFolder().toPath().resolve("region");
            if(!Files.isDirectory(folder)) continue;

            try(var files=Files.list(folder)) {
                files.forEach(path->{
                    Matcher matcher=REGION_FILE.matcher(path.getFileName().toString());
                    if(!matcher.matches()) return;

                    try {
                        BitSet generated=generatedChunks(path);
                        if(!generated.isEmpty())
                            queue.add(new ScanCursor(
                                world,
                                Integer.parseInt(matcher.group(1)),
                                Integer.parseInt(matcher.group(2)),
                                generated
                            ));
                    } catch(IOException exception) {
                        STEMCraft.getPlugin().getLogger().warning(
                            "Could not read region header "+path.getFileName()+": "
                                +exception.getMessage());
                    }
                });
            } catch(Exception exception) {
                STEMCraft.getPlugin().getLogger().warning(
                    "Could not scan region files for "+world.getName()+": "
                        +exception.getMessage());
            }
        }

        if(queue.isEmpty()) return;

        int period=Math.max(1,getConfigSection().getInt("map.backfill-period-ticks",10));
        int maxInFlight=Math.max(1,getConfigSection().getInt("map.backfill-max-in-flight",1));
        boolean onlyWhenEmpty=getConfigSection().getBoolean("map.backfill-only-when-empty",true);
        int[] inFlight={0};

        backfillTask=Bukkit.getScheduler().runTaskTimer(STEMCraft.getPlugin(),()->{
            if(onlyWhenEmpty&&!Bukkit.getOnlinePlayers().isEmpty()) return;

            int checks=0;
            while(!queue.isEmpty()&&inFlight[0]<maxInFlight&&checks<128) {
                ScanCursor cursor=queue.getFirst();
                int local=cursor.generated.nextSetBit(cursor.index);

                if(local<0) {
                    queue.removeFirst();
                    continue;
                }

                cursor.index=local+1;
                int chunkX=cursor.regionX*32+(local&31);
                int chunkZ=cursor.regionZ*32+(local>>5);
                checks++;

                if(regionStorage.completeChunk(cursor.world.getName(),chunkX,chunkZ))
                    continue;

                boolean alreadyLoaded=cursor.world.isChunkLoaded(chunkX,chunkZ);
                inFlight[0]++;

                cursor.world.getChunkAtAsync(chunkX,chunkZ,false,false,chunk->{
                    try {
                        if(chunk!=null) discover(chunk);
                    } finally {
                        if(chunk!=null&&!alreadyLoaded)
                            cursor.world.unloadChunkRequest(chunkX,chunkZ);
                        inFlight[0]--;
                    }
                });
            }

            if(queue.isEmpty()&&inFlight[0]==0&&backfillTask!=null) {
                backfillTask.cancel();
                backfillTask=null;
                STEMCraft.getPlugin().getLogger().info("Named-region backfill complete.");
            }
        },20L,period);
    }

    static BitSet generatedChunks(Path regionFile) throws IOException {
        byte[] header;
        try(InputStream input=Files.newInputStream(regionFile)) {
            header=input.readNBytes(4096);
        }

        BitSet generated=new BitSet(1024);
        for(int index=0;index<header.length/4;index++) {
            int offset=index*4;
            if(header[offset]!=0
                ||header[offset+1]!=0
                ||header[offset+2]!=0
                ||header[offset+3]!=0)
                generated.set(index);
        }
        return generated;
    }

    private Area ensureCell(World world,int cellX,int cellZ) {
        return ensureCell(world,cellX,cellZ,true);
    }

    private Area ensureCell(World world,int cellX,int cellZ,boolean persist) {
        String assigned=regionStorage.cell(world.getName(),cellX,cellZ);

        if(assigned!=null) {
            String resolved=resolveRegion(assigned);
            Area existing=areas.get(resolved);
            if(existing!=null) return existing;
        }

        int blockX=cellX*SAMPLE_SIZE;
        int blockZ=cellZ*SAMPLE_SIZE;
        Biome biome=world.getBiome(
            blockX+SAMPLE_SIZE/2,
            world.getSeaLevel(),
            blockZ+SAMPLE_SIZE/2
        );
        String type=biomeFamily(biome.getKey().getKey());

        List<Area> neighbours=neighbouringRegions(world,cellX,cellZ,type);
        Area area=mergeRegions(neighbours);

        int minX=blockX;
        int minZ=blockZ;
        int maxX=blockX+SAMPLE_SIZE-1;
        int maxZ=blockZ+SAMPLE_SIZE-1;

        if(area==null) {
            String key=cellKey(world.getName(),cellX,cellZ);
            String id="region:"+UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            long now=System.currentTimeMillis();

            area=new Area(
                id,world.getName(),REGION,type,name(type,id),
                minX,world.getMinHeight(),minZ,
                maxX,world.getMaxHeight(),maxZ,
                false,now,true
            );
            save(area);
        } else {
            area=new Area(
                area.id,area.world,area.kind,area.type,area.name,
                Math.min(area.minX,minX),area.minY,Math.min(area.minZ,minZ),
                Math.max(area.maxX,maxX),area.maxY,Math.max(area.maxZ,maxZ),
                area.locked,area.createdAt,area.discovered
            );
            areas.put(area.id,area);
            updateBounds(area);
        }

        regionStorage.setCell(world.getName(),cellX,cellZ,area.id,persist);
        invalidateMapSnapshot();
        return area;
    }

    private List<Area> neighbouringRegions(World world,int x,int z,String type) {
        Map<String,Area> found=new LinkedHashMap<>();

        for(int[] offset:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            String id=regionStorage.cell(
                world.getName(),
                x+offset[0],
                z+offset[1]
            );

            if(id!=null) id=resolveRegion(id);
            Area area=id==null?null:areas.get(id);

            if(area!=null&&area.type.equals(type))
                found.put(area.id,area);
        }

        return new ArrayList<>(found.values());
    }

    private String resolveRegion(String id) {
        return regionStorage.resolve(id);
    }

    private @Nullable Area mergeRegions(List<Area> candidates) {
        if(candidates.isEmpty()) return null;
        if(candidates.size()==1) return candidates.getFirst();

        candidates.sort(
            Comparator.comparing(Area::locked).reversed()
                .thenComparingLong(Area::createdAt)
                .thenComparing(Area::id)
        );

        Area winner=candidates.getFirst();

        for(Area loser:candidates.subList(1,candidates.size())) {
            if(winner.locked&&loser.locked) continue;

            String winnerId=winner.id;
            Area expanded=new Area(
                winner.id,winner.world,winner.kind,winner.type,winner.name,
                Math.min(winner.minX,loser.minX),winner.minY,
                Math.min(winner.minZ,loser.minZ),
                Math.max(winner.maxX,loser.maxX),winner.maxY,
                Math.max(winner.maxZ,loser.maxZ),
                winner.locked,winner.createdAt,winner.discovered
            );

            retire(loser,"merged into "+winnerId);

            /*
             * v6: one alias row replaces the old full-table
             * UPDATE named_region_cells SET region_id=... WHERE region_id=...
             */
            regionStorage.alias(loser.id,winnerId);

            api.database().update(
                "DELETE FROM named_areas WHERE id=?",
                ps->ps.setString(1,loser.id)
            );

            areas.remove(loser.id);
            areas.put(winner.id,expanded);
            updateBounds(expanded);
            winner=expanded;
        }

        return winner;
    }

    private void retire(Area area,String reason) {
        long now=System.currentTimeMillis();
        long available=now+retirementMillis;

        api.database().update(
            "INSERT INTO named_region_name_history("
                +"name,type,former_region_id,assigned_at,retired_at,available_at,reason"
                +") VALUES(?,?,?,?,?,?,?)",
            ps->{
                ps.setString(1,area.name);
                ps.setString(2,area.type);
                ps.setString(3,area.id);
                ps.setLong(4,area.createdAt);
                ps.setLong(5,now);
                ps.setLong(6,available);
                ps.setString(7,reason);
            }
        );

        activeNames.remove(normaliseName(area.name));
        retiredNames.merge(normaliseName(area.name),available,Math::max);
    }

    private void updateBounds(Area area) {
        api.database().update(
            "UPDATE named_areas SET min_x=?,min_z=?,max_x=?,max_z=? WHERE id=?",
            ps->{
                ps.setInt(1,area.minX);
                ps.setInt(2,area.minZ);
                ps.setInt(3,area.maxX);
                ps.setInt(4,area.maxZ);
                ps.setString(5,area.id);
            }
        );
    }

    private void ensureStructure(World world,GeneratedStructure generated) {
        NamespacedKey structureKey=RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.STRUCTURE)
            .getKey(generated.getStructure());

        if(structureKey==null) return;

        BoundingBox bounds=generated.getBoundingBox();
        String raw=structureKey.getKey();
        String type=structureFamily(raw);

        if(!getConfigSection().getBoolean(
            "structures."+type+".enabled",
            !Set.of("buried-treasure","nether-fossil").contains(type)
        )) return;

        String signature=
            world.getName()+':'+raw+':'
            +(int)bounds.getMinX()+':'+(int)bounds.getMinY()+':'+(int)bounds.getMinZ()+':'
            +(int)bounds.getMaxX()+':'+(int)bounds.getMaxY()+':'+(int)bounds.getMaxZ();

        String id="structure:"+UUID.nameUUIDFromBytes(
            signature.getBytes(StandardCharsets.UTF_8));

        if(areas.containsKey(id)) return;

        long now=System.currentTimeMillis();
        save(new Area(
            id,world.getName(),STRUCTURE,type,name(type,id),
            (int)Math.floor(bounds.getMinX()),
            (int)Math.floor(bounds.getMinY()),
            (int)Math.floor(bounds.getMinZ()),
            (int)Math.ceil(bounds.getMaxX()),
            (int)Math.ceil(bounds.getMaxY()),
            (int)Math.ceil(bounds.getMaxZ()),
            false,now,false
        ));
    }

    private void save(Area area) {
        int inserted=api.database().update(
            "INSERT OR IGNORE INTO named_areas("
                +"id,world_name,kind,type,name,min_x,min_y,min_z,max_x,max_y,max_z,"
                +"locked,created_at,discovered"
                +") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            ps->{
                ps.setString(1,area.id);
                ps.setString(2,area.world);
                ps.setString(3,area.kind);
                ps.setString(4,area.type);
                ps.setString(5,area.name);
                ps.setInt(6,area.minX);
                ps.setInt(7,area.minY);
                ps.setInt(8,area.minZ);
                ps.setInt(9,area.maxX);
                ps.setInt(10,area.maxY);
                ps.setInt(11,area.maxZ);
                ps.setInt(12,area.locked?1:0);
                ps.setLong(13,area.createdAt);
                ps.setInt(14,area.discovered?1:0);
            }
        );

        if(inserted>0) {
            areas.put(area.id,area);
            activeNames.add(normaliseName(area.name));
            invalidateMapSnapshot();
        }
    }

    private void onMove(PlayerMoveEvent event) {
        if(!event.hasChangedBlock()||!enabled(event.getTo().getWorld())) return;

        Area area=areaAt(event.getTo());
        if(area==null)
            area=ensureCell(
                event.getTo().getWorld(),
                Math.floorDiv(event.getTo().getBlockX(),SAMPLE_SIZE),
                Math.floorDiv(event.getTo().getBlockZ(),SAMPLE_SIZE)
            );

        if(STRUCTURE.equals(area.kind)&&!area.discovered)
            area=markDiscovered(area);

        String previous=playerArea.put(event.getPlayer().getUniqueId(),area.id);
        if(!area.id.equals(previous))
            announce(event.getPlayer(),area);
    }

    private @Nullable Area areaAt(Location location) {
        Area structure=areas.values().stream()
            .filter(a->STRUCTURE.equals(a.kind)&&a.contains(location))
            .findFirst()
            .orElse(null);

        if(structure!=null) return structure;

        String id=regionStorage.cell(
            location.getWorld().getName(),
            Math.floorDiv(location.getBlockX(),SAMPLE_SIZE),
            Math.floorDiv(location.getBlockZ(),SAMPLE_SIZE)
        );

        if(id!=null) id=resolveRegion(id);
        return id==null?null:areas.get(id);
    }

    private Area markDiscovered(Area old) {
        Area found=new Area(
            old.id,old.world,old.kind,old.type,old.name,
            old.minX,old.minY,old.minZ,old.maxX,old.maxY,old.maxZ,
            old.locked,old.createdAt,true
        );

        areas.put(found.id,found);
        api.database().update(
            "UPDATE named_areas SET discovered=1 WHERE id=?",
            ps->ps.setString(1,found.id)
        );
        invalidateMapSnapshot();
        return found;
    }

    private void announce(Player player,Area area) {
        String title=render(titleTemplate,area);
        String subtitle=render(subtitleTemplate,area);

        if(title.isBlank()&&subtitle.isBlank()) return;

        player.showTitle(Title.title(
            title.isBlank()?Component.empty():TextUtil.colourise(title),
            subtitle.isBlank()?Component.empty():TextUtil.colourise(subtitle),
            Title.Times.times(
                Duration.ofMillis(fadeInMillis),
                Duration.ofMillis(stayMillis),
                Duration.ofMillis(fadeOutMillis)
            )
        ));
    }

    private @Nullable Component renderCoordinateBar(Player player) {
        if(!enabled(player.getWorld())) return null;

        Area area=areaAt(player.getLocation());
        if(area==null) return null;

        return Component.text(
            getConfigSection().getString("coordbar.format"," ({name})")
                .replace("{name}",area.name)
                .replace("{type}",friendly(area.type))
        );
    }

    private String render(String template,Area area) {
        return template==null
            ?""
            :template.replace("{name}",area.name)
                .replace("{type}",friendly(area.type));
    }

    private String name(String type,String id) {
        List<String> configured=getConfigSection().getStringList("names.pools."+type);
        List<String> values=configured.isEmpty()
            ?generatedNames(type)
            :compactNames(configured,nameWordLimit(type));

        long now=System.currentTimeMillis();
        int start=Math.floorMod(id.hashCode(),values.size());

        for(int i=0;i<values.size();i++) {
            String candidate=values.get((start+i)%values.size());
            String normalised=normaliseName(candidate);
            Long retiredUntil=retiredNames.get(normalised);

            if(retiredUntil!=null&&retiredUntil<=now)
                retiredNames.remove(normalised,retiredUntil);

            if(!activeNames.contains(normalised)&&!retiredNames.containsKey(normalised))
                return candidate;
        }

        return availableName(List.of(),type,id);
    }

    private @Nullable String pooledName(String type,String id) {
        List<String> configured=getConfigSection().getStringList("names.pools."+type);
        String candidate=findAvailableName(
            configured.isEmpty()?List.of():compactNames(configured,nameWordLimit(type)),
            id.hashCode()
        );

        return candidate!=null
            ?candidate
            :findAvailableName(generatedNames(type),id.hashCode());
    }

    private record NamePools(List<String> configured,List<String> generated) {}

    private NamePools namePools(String type) {
        List<String> configured=getConfigSection().getStringList("names.pools."+type);
        return new NamePools(
            configured.isEmpty()?List.of():compactNames(configured,nameWordLimit(type)),
            generatedNames(type)
        );
    }

    private @Nullable String pooledName(
        String type,
        String id,
        Map<String,NamePools> cache
    ) {
        NamePools pools=cache.computeIfAbsent(type,this::namePools);
        String candidate=findAvailableName(pools.configured,id.hashCode());

        return candidate!=null
            ?candidate
            :findAvailableName(pools.generated,id.hashCode());
    }

    private String availableName(List<String> preferred,String type,String id) {
        String candidate=findAvailableName(preferred,id.hashCode());
        if(candidate!=null) return candidate;

        candidate=findAvailableName(generatedNames(type),id.hashCode());
        if(candidate!=null) return candidate;

        return fallbackName(type,id);
    }

    private @Nullable String findAvailableName(List<String> candidates,int seed) {
        if(candidates.isEmpty()) return null;

        long now=System.currentTimeMillis();
        int start=Math.floorMod(seed,candidates.size());

        for(int i=0;i<candidates.size();i++) {
            String candidate=candidates.get((start+i)%candidates.size());
            String normalised=normaliseName(candidate);
            Long retiredUntil=retiredNames.get(normalised);

            if(retiredUntil!=null&&retiredUntil<=now)
                retiredNames.remove(normalised,retiredUntil);

            if(!activeNames.contains(normalised)&&!retiredNames.containsKey(normalised))
                return candidate;
        }

        return null;
    }

    private static String letterCode(int value) {
        long remaining=Integer.toUnsignedLong(value);
        StringBuilder out=new StringBuilder();

        do {
            out.append((char)('a'+remaining%26));
            remaining/=26;
        } while(remaining>0);

        return out.reverse().toString();
    }

    private static String normaliseName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    static boolean isFallbackName(String name,String type,String id) {
        return name.equals(fallbackName(type,id));
    }

    private static String fallbackName(String type,String id) {
        return friendly(type).replace(" ","")+" "+letterCode(id.hashCode());
    }

    private List<String> generatedNames(String type) {
        List<String> configured=getConfigSection().getStringList("names.sources."+type);
        if(configured.isEmpty())
            configured=getConfigSection().getStringList("names.sources.default");
        if(configured.isEmpty())
            throw new IllegalStateException("No named-region sources configured for "+type);

        List<String> configuredForms=getConfigSection().getStringList("names.forms."+type);
        if(configuredForms.isEmpty())
            configuredForms=getConfigSection().getStringList("names.forms.default");
        if(configuredForms.isEmpty())
            throw new IllegalStateException("No named-region forms configured for "+type);

        return generateNames(
            configured,
            configuredForms,
            List.of(expansionPrefixes()),
            nameWordLimit(type)
        );
    }

    static int nameWordLimit(String type) {
        return switch(type) {
            case "desert","forest","jungle","taiga","ocean","mountains","snow","swamp",
                 "badlands","savanna","plains","mushroom-fields" -> 2;
            default -> 3;
        };
    }

    static List<String> generateNames(
        Collection<String> sources,
        Collection<String> forms,
        Collection<String> prefixes
    ) {
        return generateNames(sources,forms,prefixes,2);
    }

    static List<String> generateNames(
        Collection<String> sources,
        Collection<String> forms,
        Collection<String> prefixes,
        int maxWords
    ) {
        List<String> roots=new ArrayList<>();
        List<String> out=new ArrayList<>();

        for(String source:sources) {
            String value=source.trim();
            if(value.isEmpty()) continue;

            if(wordCount(value)==1) roots.add(value);
            else out.add(value);
        }

        List<String> base=new ArrayList<>();
        for(String root:roots) {
            out.add(root);
            for(String form:forms)
                base.add(form.replace("{root}",root));
        }

        out.addAll(base);

        for(String qualifier:split(
            "Northern,Southern,Eastern,Western,Upper,Lower,Inner,Outer,High,Far"))
            for(String name:base)
                out.add(qualify(name,qualifier));

        for(String prefix:prefixes)
            for(String root:roots)
                if(!prefix.equalsIgnoreCase(root))
                    out.add(prefix+" "+root);

        return compactNames(out,maxWords);
    }

    private static List<String> compactNames(Collection<String> names,int maxWords) {
        LinkedHashMap<String,String> unique=new LinkedHashMap<>();

        for(String name:names)
            for(String candidate:shortNameCandidates(name,maxWords))
                unique.putIfAbsent(normaliseName(candidate),candidate);

        if(unique.isEmpty())
            throw new IllegalArgumentException("A named-region pool must contain at least one word");

        return List.copyOf(unique.values());
    }

    static List<String> shortNameCandidates(String name) {
        return shortNameCandidates(name,2);
    }

    static List<String> shortNameCandidates(String name,int maxWords) {
        List<String> original=Arrays.stream(name.trim().split("\\s+"))
            .filter(word->!word.isBlank())
            .toList();

        if(original.size()<=maxWords)
            return original.isEmpty()?List.of():List.of(String.join(" ",original));

        List<String> words=original.stream()
            .filter(word->!Set.of("the","of").contains(word.toLowerCase(Locale.ROOT)))
            .toList();

        LinkedHashSet<String> candidates=new LinkedHashSet<>();

        if(maxWords==3) {
            for(int first=0;first<words.size();first++)
                for(int second=first+1;second<words.size();second++)
                    for(int third=second+1;third<words.size();third++)
                        candidates.add(
                            words.get(first)+" "+words.get(second)+" "+words.get(third));
        }

        for(int gap=1;gap<words.size();gap++)
            for(int first=0;first+gap<words.size();first++)
                candidates.add(words.get(first)+" "+words.get(first+gap));

        return List.copyOf(candidates);
    }

    private static int wordCount(String name) {
        return (int)Arrays.stream(name.trim().split("\\s+"))
            .filter(word->!word.isBlank())
            .count();
    }

    static boolean isNumberedFallback(String name) {
        return name.trim().matches(".*\\s\\d+$");
    }

    private static String qualify(String name,String qualifier) {
        return name.startsWith("The ")
            ?"The "+qualifier+" "+name.substring(4)
            :qualifier+" "+name;
    }

    private static String[] expansionPrefixes() {
        return split(
            "Amber,Ancient,Autumn,Azure,Birch,Black,Blooming,Blue,Bold,Bright,Bronze,Cedar,"
            +"Cinder,Clouded,Clover,Copper,Coral,Crimson,Crystal,Dappled,Dawn,Deep,Distant,Dragon,"
            +"Dusky,Eagle,Eastern,Elder,Emerald,Evening,Falcon,Fallen,Far,Fern,First,Flint,Forgotten,"
            +"Fox,Frosted,Gilded,Golden,Green,Harbour,Hazel,Hidden,High,Hollow,Inner,Iron,Ivory,Jade,"
            +"Last,Leafy,Little,Lone,Lost,Lower,Lunar,Maple,Misty,Moonlit,Mossy,New,Northern,Oak,"
            +"Ochre,Old,Open,Outer,Painted,Pale,Pearl,Pine,Quiet,Raven,Red,Remote,River,Riven,Robin,"
            +"Royal,Ruby,Sacred,Sapphire,Scarlet,Secret,Shadow,Shattered,Silent,Silver,Southern,Starry,"
            +"Still,Stone,Stormy,Sunlit,Swift,Thorn,Thunder,Twilight,Upper,Violet,Wandering,Western,"
            +"Whispering,White,Wild,Willow,Windy,Winter,Wolf,Wren,Ashen,Barley,Briar,Brook,Cascade,"
            +"Daisy,Deer,Elm,Evergreen,Feather,Fire,Foxglove,Granite,Harvest,Hawthorn,Heather,Juniper,"
            +"Lark,Marsh,Meadow,Orchid,Primrose,Reed,Rowan,Rust,Sage,Skylark,Slate,Solstice,Sparrow,"
            +"Tempest,Timber,Valiant,Whalefall"
        );
    }

    private static String[] split(String value) {
        return value.split(",");
    }

    private void executeCommand(CommandContext ctx) {
        String action=ctx.getArg(0,"info").toLowerCase(Locale.ROOT);

        switch(action) {
            case "info" -> {
                ctx.checkNotConsole();
                Area area=areaAt(ctx.asPlayer().getLocation());
                if(area==null) ctx.returnInfo("No named region here.");
                else describe(ctx,area);
            }
            case "list" -> showList(
                ctx,
                areas.values().stream()
                    .sorted(Comparator.comparing(Area::name))
                    .toList(),
                "Named regions"
            );
            case "find" -> {
                String query=ctx.getArgsAsString(1,"").trim().toLowerCase(Locale.ROOT);
                if(query.isEmpty()) {
                    ctx.returnError("Use /namedregion find <name>.");
                    return;
                }

                List<Area> active=areas.values().stream()
                    .filter(a->a.name.toLowerCase(Locale.ROOT).contains(query))
                    .sorted(Comparator.comparing(Area::name))
                    .toList();

                if(!active.isEmpty())
                    showList(ctx,active,"Active matches for "+query);

                final boolean[] retired={false};
                api.database().queryEach(
                    "SELECT name,type,former_region_id,available_at "
                        +"FROM named_region_name_history "
                        +"WHERE lower(name) LIKE ? ORDER BY retired_at DESC LIMIT 20",
                    ps->ps.setString(1,"%"+query+"%"),
                    rs->{
                        if(!retired[0]) ctx.info("Retired matches:");
                        retired[0]=true;

                        long remaining=Math.max(
                            0,rs.getLong(4)-System.currentTimeMillis());

                        ctx.info(
                            rs.getString(1)+" - "+friendly(rs.getString(2))
                                +" ["+rs.getString(3)+"] - available in "
                                +((remaining+86_399_999L)/86_400_000L)+" day(s)"
                        );
                    }
                );

                if(active.isEmpty()&&!retired[0])
                    ctx.returnInfo("No active or retired names match "+query+".");
            }
            case "nearby" -> {
                ctx.checkNotConsole();
                Location location=ctx.asPlayer().getLocation();

                showList(
                    ctx,
                    areas.values().stream()
                        .filter(a->a.world.equals(location.getWorld().getName()))
                        .sorted(Comparator.comparingDouble(a->distanceSquared(location,a)))
                        .limit(10)
                        .toList(),
                    "Nearby named regions"
                );
            }
            case "teleport" -> {
                ctx.checkNotConsole();
                Area area=areas.get(ctx.getArg(1,""));

                if(area==null) {
                    ctx.returnError("Unknown region ID.");
                    return;
                }

                World world=Bukkit.getWorld(area.world);
                if(world==null) {
                    ctx.returnError("That world is not loaded.");
                    return;
                }

                double x=(area.minX+area.maxX)/2.0;
                double z=(area.minZ+area.maxZ)/2.0;
                int y=STRUCTURE.equals(area.kind)
                    ?area.maxY+1
                    :world.getHighestBlockYAt((int)x,(int)z)+1;

                ctx.asPlayer().teleport(new Location(world,x,y,z));
                ctx.returnSuccess("Teleported to "+area.name+".");
            }
            case "rename" -> {
                String id=ctx.getArg(1,"");
                String newName=ctx.getArgsAsString(2,"").trim();
                Area old=areas.get(id);

                if(old==null) {
                    ctx.returnError("Use /namedregion rename <id> [name].");
                    return;
                }

                boolean generated=newName.isEmpty();
                retire(old,generated
                    ?"automatically renamed by admin"
                    :"renamed by admin");

                String chosenName=generated?name(old.type,old.id):newName;

                Area renamed=new Area(
                    old.id,old.world,old.kind,old.type,chosenName,
                    old.minX,old.minY,old.minZ,old.maxX,old.maxY,old.maxZ,
                    true,old.createdAt,old.discovered
                );

                activeNames.add(normaliseName(chosenName));
                areas.put(id,renamed);

                api.database().update(
                    "UPDATE named_areas SET name=?,locked=1 WHERE id=?",
                    ps->{
                        ps.setString(1,chosenName);
                        ps.setString(2,id);
                    }
                );

                invalidateMapSnapshot();
                ctx.returnSuccess("Renamed "+id+" to "+chosenName+".");
            }
            case "retired" -> showRetired(
                ctx,
                ctx.getArgsAsString(1,"").trim()
            );
            case "fallbacks" -> {
                String requestedType=ctx.getArgsAsString(1,"").trim();
                String type=requestedType.equals("*")
                    ?""
                    :normaliseType(requestedType);

                List<Area> found=areas.values().stream()
                    .filter(a->type.isEmpty()||a.type.equals(type))
                    .filter(a->isFallbackName(a.name,a.type,a.id))
                    .sorted(Comparator.comparing(Area::type).thenComparing(Area::name))
                    .toList();

                showList(
                    ctx,
                    found,
                    type.isEmpty()
                        ?"Fallback-named regions"
                        :"Fallback-named "+friendly(type)+" regions"
                );
            }
            case "release" -> releaseNames(
                ctx,
                ctx.getArgsAsString(1,"").trim()
            );
            case "regenerate" -> regenerateNames(
                ctx,
                ctx.getArg(1,"")
            );
            default -> ctx.returnError(
                "Use /namedregion info, list, find, nearby, teleport, rename, retired, "
                    +"fallbacks, release, or regenerate."
            );
        }
    }

    private void showRetired(CommandContext ctx,String requestedType) {
        String type=normaliseType(requestedType);
        long now=System.currentTimeMillis();
        List<String> found=new ArrayList<>();

        String sql=
            "SELECT name,type,former_region_id,available_at,reason "
            +"FROM named_region_name_history WHERE available_at>?"
            +(type.isEmpty()?"":" AND type=?")
            +" ORDER BY available_at,name LIMIT 50";

        api.database().queryEach(
            sql,
            ps->{
                ps.setLong(1,now);
                if(!type.isEmpty()) ps.setString(2,type);
            },
            rs->{
                long remaining=Math.max(0,rs.getLong(4)-now);
                long days=(remaining+86_399_999L)/86_400_000L;

                found.add(
                    rs.getString(1)+" - "+friendly(rs.getString(2))
                        +" ["+rs.getString(3)+"] - "+days+" day(s) - "+rs.getString(5)
                );
            }
        );

        if(found.isEmpty()) {
            ctx.returnInfo(
                type.isEmpty()
                    ?"Currently retired names: none."
                    :"Currently retired "+friendly(type)+" names: none."
            );
            return;
        }

        ctx.info(
            (type.isEmpty()
                ?"Currently retired names"
                :"Currently retired "+friendly(type)+" names")
            +" (showing up to 50):"
        );
        found.forEach(ctx::info);
    }

    private void releaseNames(CommandContext ctx,String requested) {
        if(requested.isEmpty()) {
            ctx.returnError("Use /namedregion release <name|*>.");
            return;
        }

        long now=System.currentTimeMillis();
        int released;

        if(requested.equals("*")) {
            released=api.database().update(
                "UPDATE named_region_name_history SET available_at=0 WHERE available_at>?",
                ps->ps.setLong(1,now)
            );
            retiredNames.clear();
        } else {
            released=api.database().update(
                "UPDATE named_region_name_history SET available_at=0 "
                    +"WHERE available_at>? AND lower(name)=lower(?)",
                ps->{
                    ps.setLong(1,now);
                    ps.setString(2,requested);
                }
            );

            if(released>0)
                retiredNames.remove(normaliseName(requested));
        }

        if(released==0) {
            ctx.returnInfo("No matching currently retired names were found.");
            return;
        }

        ctx.returnSuccess(
            "Released "+released+" retired name"+(released==1?"":"s")+".");
    }

    private void regenerateNames(CommandContext ctx,String requested) {
        if(requested.isBlank()) {
            ctx.returnError("Use /namedregion regenerate <region-id|*>.");
            return;
        }

        if(!requested.equals("*")) {
            Area area=areas.get(requested);
            if(area==null) {
                ctx.returnError("Unknown region ID.");
                return;
            }

            String oldName=area.name;
            if(!regenerate(area,"regenerated by admin")) {
                ctx.returnInfo(
                    "No pooled "+friendly(area.type)+" name is currently available.");
                return;
            }

            ctx.returnSuccess(
                "Regenerated "+oldName+" as "+areas.get(area.id).name+".");
            return;
        }

        startBulkRegeneration(ctx);
    }

    private boolean regenerate(Area old,String reason) {
        String normalised=normaliseName(old.name);
        activeNames.remove(normalised);

        Long previousRetirement=retiredNames.put(normalised,Long.MAX_VALUE);
        String replacement=pooledName(old.type,old.id);

        if(previousRetirement==null) retiredNames.remove(normalised);
        else retiredNames.put(normalised,previousRetirement);

        return finishRegeneration(old,reason,normalised,replacement);
    }

    private boolean regenerate(
        Area old,
        String reason,
        Map<String,NamePools> cache
    ) {
        String normalised=normaliseName(old.name);
        activeNames.remove(normalised);

        Long previousRetirement=retiredNames.put(normalised,Long.MAX_VALUE);
        String replacement=pooledName(old.type,old.id,cache);

        if(previousRetirement==null) retiredNames.remove(normalised);
        else retiredNames.put(normalised,previousRetirement);

        return finishRegeneration(old,reason,normalised,replacement);
    }

    private boolean finishRegeneration(
        Area old,
        String reason,
        String normalised,
        @Nullable String replacement
    ) {
        if(replacement==null) {
            activeNames.add(normalised);
            return false;
        }

        retire(old,reason);

        Area updated=new Area(
            old.id,old.world,old.kind,old.type,replacement,
            old.minX,old.minY,old.minZ,old.maxX,old.maxY,old.maxZ,
            old.locked,old.createdAt,old.discovered
        );

        areas.put(updated.id,updated);
        activeNames.add(normaliseName(replacement));

        api.database().update(
            "UPDATE named_areas SET name=? WHERE id=?",
            ps->{
                ps.setString(1,replacement);
                ps.setString(2,updated.id);
            }
        );

        invalidateMapSnapshot();
        return true;
    }

    private void startBulkRegeneration(CommandContext ctx) {
        if(regenerationTask!=null) {
            ctx.returnInfo("A fallback-name regeneration is already running.");
            return;
        }

        List<String> ids=areas.values().stream()
            .filter(a->isFallbackName(a.name,a.type,a.id))
            .map(Area::id)
            .toList();

        if(ids.isEmpty()) {
            ctx.returnInfo("No fallback-named regions were found.");
            return;
        }

        final int total=ids.size();
        final int batchSize=10;
        final int progressInterval=250;
        int[] processed={0},renamed={0},fallbacks={0};
        Map<String,NamePools> cache=new HashMap<>();

        ctx.info("Started fallback-name regeneration for "+total+" regions.");

        regenerationTask=Bukkit.getScheduler().runTaskTimer(
            STEMCraft.getPlugin(),
            ()->{
                int end=Math.min(total,processed[0]+batchSize);

                while(processed[0]<end) {
                    Area area=areas.get(ids.get(processed[0]));

                    if(area!=null&&isFallbackName(area.name,area.type,area.id)) {
                        if(regenerate(area,"bulk-regenerated fallback",cache))
                            renamed[0]++;
                        else
                            fallbacks[0]++;
                    } else {
                        renamed[0]++;
                    }

                    processed[0]++;
                }

                if(processed[0]==total) {
                    ctx.success(
                        regenerationProgress(
                            processed[0],total,renamed[0],fallbacks[0])
                            +" Complete."
                    );
                    regenerationTask.cancel();
                    regenerationTask=null;
                } else if(processed[0]%progressInterval<batchSize) {
                    ctx.info(
                        regenerationProgress(
                            processed[0],total,renamed[0],fallbacks[0])
                    );
                }
            },
            1L,
            1L
        );
    }

    static String regenerationProgress(
        int processed,
        int total,
        int regenerated,
        int fallbacks
    ) {
        return processed+" / "+total+" processed; "
            +regenerated+" regenerated; "
            +fallbacks+(fallbacks==1?" remains fallback.":" remain fallbacks.");
    }

    private static String normaliseType(String type) {
        return type.toLowerCase(Locale.ROOT).replace('_','-').replace(' ','-');
    }

    private void showList(CommandContext ctx,List<Area> found,String heading) {
        if(found.isEmpty()) {
            ctx.returnInfo(heading+": none.");
            return;
        }

        ctx.info(heading+" ("+found.size()+"):");
        for(Area area:found.stream().limit(50).toList())
            describe(ctx,area);

        if(found.size()>50)
            ctx.info(
                "Showing the first 50 results. Use /namedregion find <name> "
                    +"to narrow the list.");
    }

    private void describe(CommandContext ctx,Area area) {
        ctx.info(
            area.name+" - "+friendly(area.type)+" - "+area.world
                +" ["+area.id+"] @ "
                +((area.minX+area.maxX)/2)+", "
                +((area.minZ+area.maxZ)/2)
        );
    }

    private static double distanceSquared(Location location,Area area) {
        double x=(area.minX+area.maxX)/2.0-location.getX();
        double z=(area.minZ+area.maxZ)/2.0-location.getZ();
        return x*x+z*z;
    }

    private static String cellKey(String world,int x,int z) {
        return world+':'+x+':'+z;
    }

    static String biomeFamily(String biome) {
        if(biome.contains("desert")) return "desert";
        if(biome.contains("badlands")) return "badlands";
        if(biome.contains("jungle")||biome.contains("bamboo")) return "jungle";
        if(biome.contains("swamp")) return "swamp";
        if(biome.contains("ocean")||biome.contains("beach")) return "ocean";
        if(biome.contains("snow")||biome.contains("ice")||biome.contains("frozen")) return "snow";
        if(biome.contains("peak")||biome.contains("slope")
            ||biome.contains("mountain")||biome.contains("windswept")) return "mountains";
        if(biome.contains("taiga")||biome.contains("spruce")) return "taiga";
        if(biome.contains("savanna")) return "savanna";
        if(biome.contains("mushroom")) return "mushroom-fields";
        if(biome.contains("forest")||biome.contains("grove")) return "forest";
        return "plains";
    }

    static String structureFamily(String type) {
        if(type.startsWith("village_")) return "village";
        if(type.startsWith("ruined_portal")) return "ruined-portal";
        if(type.startsWith("mineshaft")) return "mineshaft";
        if(type.startsWith("shipwreck")) return "shipwreck";
        if(type.startsWith("ocean_ruin")) return "ocean-ruins";
        return type.replace('_','-');
    }

    private static String friendly(String value) {
        StringJoiner out=new StringJoiner(" ");

        for(String word:value.split("[-_]"))
            out.add(
                Character.toUpperCase(word.charAt(0))
                    +word.substring(1)
            );

        return out.toString();
    }

    private boolean enabled(World world) {
        return worlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    private void enableMap() {
        if(!getConfigSection().getBoolean("map.enabled",true)) return;

        Plugin plugin=Bukkit.getPluginManager().getPlugin("Pl3xMap");
        if(plugin==null||!plugin.isEnabled()) return;

        try {
            var biomeStyles=mapBiomeStyles();
            String biomeLayer=getConfigSection().getString(
                "map.layers.biomes","Biome Regions");
            String structureLayer=getConfigSection().getString(
                "map.layers.structures","Discovered Structures");
            int updateSeconds=Math.max(
                60,
                getConfigSection().getInt("map.update-minutes",5)*60
            );

            mapRefreshMillis=updateSeconds*1000L;

            int structureIconSize=Math.max(
                12,
                getConfigSection().getInt("map.structures.icon-size",24)
            );

            mapLayer=new Pl3xMapNamedRegions(
                STEMCraft.getPlugin(),
                this::mapAreas,
                biomeStyles,
                getConfigSection().getBoolean("map.permanent-labels",false),
                biomeLayer,
                structureLayer,
                updateSeconds,
                structureIconSize
            );
            mapLayer.enable();
        } catch(RuntimeException exception) {
            STEMCraft.getPlugin().getLogger().warning(
                "Could not enable named-region map layer: "
                    +exception.getMessage());
        }
    }

    private List<Pl3xMapNamedRegions.Style> mapBiomeStyles() {
        int thickness=Math.max(
            1,
            getConfigSection().getInt("map.biomes.line-thickness",2)
        );

        List<String> palette=getConfigSection().contains("map.biomes.palette")
            ?getConfigSection().getStringList("map.biomes.palette")
            :DEFAULT_MAP_PALETTE;

        if(palette.isEmpty())
            return List.of(
                new Pl3xMapNamedRegions.Style(
                    mapColour("map.biomes.stroke-colour","#FFFF9800"),
                    mapColour("map.biomes.fill-colour","#40FF9800"),
                    thickness
                )
            );

        List<Pl3xMapNamedRegions.Style> styles=new ArrayList<>();
        for(String configured:palette) {
            int fill=mapColourValue(configured,"#40FF9800");
            styles.add(
                new Pl3xMapNamedRegions.Style(
                    0xFF000000|(fill&0xFFFFFF),
                    fill,
                    thickness
                )
            );
        }

        return List.copyOf(styles);
    }

    private int mapColour(String path,String fallback) {
        String raw=getConfigSection().getString(path,fallback)
            .trim()
            .replace("#","")
            .replaceFirst("(?i)^0x","");

        return mapColourValue(raw,fallback);
    }

    static int mapColourValue(String configured,String fallback) {
        String raw=configured.trim()
            .replace("#","")
            .replaceFirst("(?i)^0x","");

        if(raw.length()==6) raw="FF"+raw;
        if(raw.length()!=8)
            return (int)Long.parseLong(fallback.substring(1),16);

        try {
            return (int)Long.parseLong(raw,16);
        } catch(NumberFormatException ignored) {
            return (int)Long.parseLong(fallback.substring(1),16);
        }
    }

    private Collection<NamedMapArea> mapAreas() {
        long now=System.currentTimeMillis();

        if(!shouldRefreshMapSnapshot(
            mapSnapshotDirty,mapSnapshotBuiltAt,now,mapRefreshMillis))
            return mapSnapshot;

        synchronized(mapSnapshotLock) {
            now=System.currentTimeMillis();

            if(!shouldRefreshMapSnapshot(
                mapSnapshotDirty,mapSnapshotBuiltAt,now,mapRefreshMillis))
                return mapSnapshot;

            mapSnapshot=buildMapAreas();
            mapSnapshotBuiltAt=now;
            mapSnapshotDirty=false;
            return mapSnapshot;
        }
    }

    static boolean shouldRefreshMapSnapshot(
        boolean dirty,
        long builtAt,
        long now,
        long intervalMillis
    ) {
        return dirty
            &&(builtAt==0||now>=builtAt&&now-builtAt>=intervalMillis);
    }

    private Collection<NamedMapArea> buildMapAreas() {
        List<NamedMapArea> result=new ArrayList<>();

        for(Area area:areas.values())
            if(STRUCTURE.equals(area.kind)&&area.discovered)
                result.add(
                    new NamedMapArea(
                        area.id,
                        area.world,
                        area.name,
                        friendly(area.type),
                        List.of(List.of(
                            new NamedMapArea.MapPoint(area.minX,area.minZ),
                            new NamedMapArea.MapPoint(area.maxX,area.minZ),
                            new NamedMapArea.MapPoint(area.maxX,area.maxZ),
                            new NamedMapArea.MapPoint(area.minX,area.maxZ)
                        ))
                    )
                );

        Map<String,Set<Long>> byRegion=new HashMap<>();

        /*
         * v6 iterates compact chunk cells directly: no String key creation/splitting and
         * no 16-entry ConcurrentHashMap per Minecraft chunk.
         */
        regionStorage.forEachCell((cell,regionId)->
            byRegion.computeIfAbsent(regionId,ignored->new HashSet<>())
                .add(pack(cell.cellX(),cell.cellZ()))
        );

        for(Map.Entry<String,Set<Long>> entry:byRegion.entrySet()) {
            Area area=areas.get(resolveRegion(entry.getKey()));
            if(area==null) continue;

            result.add(
                new NamedMapArea(
                    area.id,
                    area.world,
                    area.name,
                    friendly(area.type),
                    boundaryLoops(entry.getValue())
                )
            );
        }

        return List.copyOf(result);
    }

    private void invalidateMapSnapshot() {
        mapSnapshotDirty=true;
    }

    static List<List<NamedMapArea.MapPoint>> boundaryLoops(Set<Long> cells) {
        Map<Long,Deque<Long>> edges=new HashMap<>();

        for(long packed:cells) {
            int x=unpackX(packed);
            int z=unpackZ(packed);
            int bx=x*SAMPLE_SIZE;
            int bz=z*SAMPLE_SIZE;

            if(!cells.contains(pack(x,z-1)))
                edge(edges,bx,bz,bx+SAMPLE_SIZE,bz);
            if(!cells.contains(pack(x+1,z)))
                edge(edges,bx+SAMPLE_SIZE,bz,bx+SAMPLE_SIZE,bz+SAMPLE_SIZE);
            if(!cells.contains(pack(x,z+1)))
                edge(edges,bx+SAMPLE_SIZE,bz+SAMPLE_SIZE,bx,bz+SAMPLE_SIZE);
            if(!cells.contains(pack(x-1,z)))
                edge(edges,bx,bz+SAMPLE_SIZE,bx,bz);
        }

        List<List<NamedMapArea.MapPoint>> loops=new ArrayList<>();

        while(!edges.isEmpty()) {
            long start=edges.keySet().iterator().next();
            long current=start;
            List<NamedMapArea.MapPoint> loop=new ArrayList<>();
            int guard=0;

            do {
                loop.add(
                    new NamedMapArea.MapPoint(
                        unpackX(current),
                        unpackZ(current)
                    )
                );

                Deque<Long> next=edges.get(current);
                if(next==null||next.isEmpty()) break;

                long following=next.removeFirst();
                if(next.isEmpty()) edges.remove(current);
                current=following;
            } while(current!=start&&guard++<100000);

            if(loop.size()>=3) loops.add(loop);
        }

        return loops;
    }

    private static void edge(
        Map<Long,Deque<Long>> edges,
        int x1,
        int z1,
        int x2,
        int z2
    ) {
        edges.computeIfAbsent(pack(x1,z1),ignored->new ArrayDeque<>())
            .add(pack(x2,z2));
    }

    static long pack(int x,int z) {
        return ((long)x<<32)|(z&0xffffffffL);
    }

    private static int unpackX(long value) {
        return (int)(value>>32);
    }

    private static int unpackZ(long value) {
        return (int)value;
    }
}
