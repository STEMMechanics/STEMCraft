/* STEMCraft - Minecraft Plugin */
package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.coordinatebar.CoordinateBarSection;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.integration.pl3xmap.NamedMapArea;
import dev.stemcraft.integration.pl3xmap.Pl3xMapNamedRegions;
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
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Permanently names procedurally discovered biome territories and generated structures. */
public final class NamedRegions extends BaseFeature {
    private record Area(String id, String world, String kind, String type, String name,
                        int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean locked, long createdAt,
                        boolean discovered) {
        boolean contains(Location l) { return world.equals(l.getWorld().getName()) && l.getX() >= minX && l.getX() <= maxX
            && l.getY() >= minY && l.getY() <= maxY && l.getZ() >= minZ && l.getZ() <= maxZ; }
    }
    private static final String REGION = "region", STRUCTURE = "structure";
    private static final String COORDINATE_PROVIDER_ID = "named-region";
    private final Map<String, Area> areas = new ConcurrentHashMap<>();
    private final Map<String, String> cellRegions = new ConcurrentHashMap<>();
    private final Map<String, String> mergedRegions = new ConcurrentHashMap<>();
    private final Set<String> activeNames = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> retiredNames = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArea = new HashMap<>();
    private final Set<String> worlds = new HashSet<>();
    private static final Map<String, List<String>> DEFAULT_NAME_CACHE = new ConcurrentHashMap<>();
    private final Object mapSnapshotLock = new Object();
    private volatile Collection<NamedMapArea> mapSnapshot = List.of();
    private volatile boolean mapSnapshotDirty = true;
    private volatile long mapSnapshotBuiltAt;
    private long mapRefreshMillis = 300_000L;
    private Pl3xMapNamedRegions mapLayer;
    private BukkitTask backfillTask;
    private String titleTemplate, subtitleTemplate;
    private long fadeInMillis, stayMillis, fadeOutMillis;
    private long retirementMillis;
    private static final int SAMPLE_SIZE = 4;
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final class ScanCursor { final World world; final int regionX,regionZ; final BitSet generated; int index;
        ScanCursor(World world,int regionX,int regionZ,BitSet generated){this.world=world;this.regionX=regionX;this.regionZ=regionZ;this.generated=generated;} }

    public NamedRegions(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        worlds.addAll(getConfigSection().getStringList("worlds").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
        if (worlds.isEmpty()) worlds.add("survival");
        titleTemplate = getConfigSection().getString("display.title", "");
        subtitleTemplate = getConfigSection().getString("display.subtitle", "<gold>{name}</gold>");
        fadeInMillis = Math.max(0, getConfigSection().getLong("display.fade-in", 500));
        stayMillis = Math.max(0, getConfigSection().getLong("display.stay", 5000));
        fadeOutMillis = Math.max(0, getConfigSection().getLong("display.fade-out", 1000));
        retirementMillis = Math.max(0, getConfigSection().getLong("names.retirement-days", 30)) * 86_400_000L;
        migrateDevelopmentSchema(); createTable(); migrateDiscoverySchema(); loadAreas(); migrateLongGeneratedNames();
        if(getConfigSection().getBoolean("coordbar.enabled",true))
            api.coordinateBar().registerAmendment(STEMCraft.getPlugin(),COORDINATE_PROVIDER_ID,
                CoordinateBarSection.WORLD,50,this::renderCoordinateBar);
        api.events().register(ChunkLoadEvent.class, event -> discover(event.getChunk()));
        api.events().register(PlayerMoveEvent.class, this::onMove);
        api.tabComplete().register("named-area", (player, args) -> new ArrayList<>(areas.keySet()));
        api.commands().create("namedregion").usage("/namedregion <info|list|find|nearby|teleport|rename>")
            .description("Inspect or rename generated regions.").permission("stemcraft.command.namedregion")
            .tabCompletion("info").tabCompletion("list").tabCompletion("find")
            .tabCompletion("nearby").tabCompletion("teleport", "{named-area}")
            .tabCompletion("rename", "{named-area}")
            .executor((unused, cmd, ctx) -> executeCommand(ctx)).register(STEMCraft.getPlugin());
        for (World world : Bukkit.getWorlds()) if (enabled(world)) for (Chunk chunk : world.getLoadedChunks()) discover(chunk);
        enableMap();
        startBackfill();
    }

    @Override public void onDisable() { api.coordinateBar().unregisterAmendment(STEMCraft.getPlugin(),COORDINATE_PROVIDER_ID,CoordinateBarSection.WORLD);
        if (mapLayer != null) mapLayer.disable(); if(backfillTask!=null)backfillTask.cancel();
        backfillTask=null;areas.clear(); cellRegions.clear(); mergedRegions.clear(); activeNames.clear(); retiredNames.clear(); playerArea.clear();
        mapSnapshot=List.of();mapSnapshotDirty=true;mapSnapshotBuiltAt=0; }
    private void migrateDevelopmentSchema() {
        if (api.database().migrationVersion("named-regions") >= 2) return;
        api.database().execute("DROP TABLE IF EXISTS named_region_chunks");
        api.database().execute("DROP TABLE IF EXISTS named_region_cells");
        api.database().execute("DROP TABLE IF EXISTS named_region_name_history");
        api.database().execute("DROP TABLE IF EXISTS named_areas");
        api.database().setMigrationVersion("named-regions", 2);
    }
    private void migrateDiscoverySchema(){if(api.database().migrationVersion("named-regions")>=3)return;
        api.database().execute("ALTER TABLE named_areas ADD COLUMN discovered INTEGER NOT NULL DEFAULT 1");
        api.database().execute("UPDATE named_areas SET discovered=0 WHERE kind='structure'");
        api.database().setMigrationVersion("named-regions",3);
    }
    private void createTable() { api.database().execute("""
        CREATE TABLE IF NOT EXISTS named_areas (id TEXT PRIMARY KEY, world_name TEXT NOT NULL, kind TEXT NOT NULL,
        type TEXT NOT NULL, name TEXT NOT NULL, min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
        max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL, locked INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL)
        """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS named_region_cells (world_name TEXT NOT NULL, cell_x INTEGER NOT NULL,
            cell_z INTEGER NOT NULL, region_id TEXT NOT NULL, PRIMARY KEY(world_name,cell_x,cell_z))
            """);
        api.database().execute("CREATE INDEX IF NOT EXISTS idx_named_region_cells_region_id ON named_region_cells(region_id)");
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS named_region_name_history (name TEXT NOT NULL, type TEXT NOT NULL,
            former_region_id TEXT NOT NULL, assigned_at INTEGER NOT NULL, retired_at INTEGER NOT NULL,
            available_at INTEGER NOT NULL, reason TEXT NOT NULL)
            """);
    }
    private void loadAreas() { areas.clear();activeNames.clear();retiredNames.clear();api.database().queryEach(
        "SELECT id,world_name,kind,type,name,min_x,min_y,min_z,max_x,max_y,max_z,locked,created_at,discovered FROM named_areas", null,
        rs -> { Area a = read(rs); areas.put(a.id, a);activeNames.add(normaliseName(a.name)); });
        long now=System.currentTimeMillis();api.database().queryEach(
            "SELECT name,available_at FROM named_region_name_history WHERE available_at>?",ps->ps.setLong(1,now),
            rs->retiredNames.merge(normaliseName(rs.getString(1)),rs.getLong(2),Math::max));
        cellRegions.clear(); api.database().queryEach("SELECT world_name,cell_x,cell_z,region_id FROM named_region_cells", null,
            rs -> cellRegions.put(cellKey(rs.getString(1), rs.getInt(2), rs.getInt(3)), rs.getString(4)));
    }
    private Area read(ResultSet r) throws SQLException { return new Area(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
        r.getString(5), r.getInt(6), r.getInt(7), r.getInt(8), r.getInt(9), r.getInt(10), r.getInt(11), r.getInt(12)!=0, r.getLong(13),r.getInt(14)!=0); }

    private void migrateLongGeneratedNames() {
        if (api.database().migrationVersion("named-regions") >= 4) return;
        int renamed = 0;
        for (Area old : new ArrayList<>(areas.values())) {
            if (old.locked || wordCount(old.name) <= 2) continue;
            activeNames.remove(normaliseName(old.name));
            String replacement = availableName(shortNameCandidates(old.name), old.type, old.id);
            retire(old, "shortened by two-word name migration");
            Area updated = new Area(old.id, old.world, old.kind, old.type, replacement, old.minX, old.minY, old.minZ,
                old.maxX, old.maxY, old.maxZ, false, old.createdAt, old.discovered);
            areas.put(updated.id, updated);
            activeNames.add(normaliseName(updated.name));
            api.database().update("UPDATE named_areas SET name=? WHERE id=?", ps -> {
                ps.setString(1, updated.name); ps.setString(2, updated.id);
            });
            renamed++;
        }
        api.database().setMigrationVersion("named-regions", 4);
        if (renamed > 0) {
            STEMCraft.getPlugin().getLogger().info("Shortened " + renamed + " generated region name(s) to two words.");
            invalidateMapSnapshot();
        }
    }

    private void discover(Chunk chunk) {
        if (!enabled(chunk.getWorld())) return;
        int baseX=chunk.getX()*16/SAMPLE_SIZE,baseZ=chunk.getZ()*16/SAMPLE_SIZE;
        for(int dx=0;dx<16/SAMPLE_SIZE;dx++)for(int dz=0;dz<16/SAMPLE_SIZE;dz++)ensureCell(chunk.getWorld(),baseX+dx,baseZ+dz);
        for (GeneratedStructure structure : chunk.getStructures()) ensureStructure(chunk.getWorld(), structure);
    }
    private void startBackfill(){if(!getConfigSection().getBoolean("map.backfill-existing-chunks",true))return;
        Deque<ScanCursor> queue=new ArrayDeque<>();
        for(World world:Bukkit.getWorlds())if(enabled(world)){Path folder=world.getWorldFolder().toPath().resolve("region");if(!Files.isDirectory(folder))continue;
            try(var files=Files.list(folder)){files.forEach(path->{Matcher matcher=REGION_FILE.matcher(path.getFileName().toString());
                if(matcher.matches())try { BitSet generated=generatedChunks(path);if(!generated.isEmpty())queue.add(new ScanCursor(world,
                    Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2)),generated)); }
                catch(IOException exception){STEMCraft.getPlugin().getLogger().warning("Could not read region header "+path.getFileName()+": "+exception.getMessage());}});}
            catch(Exception exception){STEMCraft.getPlugin().getLogger().warning("Could not scan region files for "+world.getName()+": "+exception.getMessage());}}
        if(queue.isEmpty())return;
        int period=Math.max(1,getConfigSection().getInt("map.backfill-period-ticks",10));
        int maxInFlight=Math.max(1,getConfigSection().getInt("map.backfill-max-in-flight",1));
        boolean onlyWhenEmpty=getConfigSection().getBoolean("map.backfill-only-when-empty",true);
        int[] inFlight={0};
        backfillTask=Bukkit.getScheduler().runTaskTimer(STEMCraft.getPlugin(),()->{
            if(onlyWhenEmpty&&!Bukkit.getOnlinePlayers().isEmpty())return;
            int checks=0;while(!queue.isEmpty()&&inFlight[0]<maxInFlight&&checks<128){ScanCursor cursor=queue.getFirst();
                int local=cursor.generated.nextSetBit(cursor.index);if(local<0){queue.removeFirst();continue;}cursor.index=local+1;
                int chunkX=cursor.regionX*32+(local&31),chunkZ=cursor.regionZ*32+(local>>5);checks++;
                String sampleKey=cellKey(cursor.world.getName(),chunkX*4,chunkZ*4);
                if(cellRegions.containsKey(sampleKey))continue;
                boolean alreadyLoaded=cursor.world.isChunkLoaded(chunkX,chunkZ);inFlight[0]++;
                cursor.world.getChunkAtAsync(chunkX,chunkZ,false,false,chunk->{
                    try { if(chunk!=null&&alreadyLoaded)discover(chunk); }
                    finally { if(chunk!=null&&!alreadyLoaded)cursor.world.unloadChunkRequest(chunkX,chunkZ);inFlight[0]--; }
                });
            }
            if(queue.isEmpty()&&inFlight[0]==0&&backfillTask!=null){backfillTask.cancel();backfillTask=null;STEMCraft.getPlugin().getLogger().info("Named-region backfill complete.");}
        },20L,period);
    }
    static BitSet generatedChunks(Path regionFile) throws IOException {
        byte[] header;
        try(InputStream input=Files.newInputStream(regionFile)){header=input.readNBytes(4096);}
        BitSet generated=new BitSet(1024);
        for(int index=0;index<header.length/4;index++){
            int offset=index*4;
            if(header[offset]!=0||header[offset+1]!=0||header[offset+2]!=0||header[offset+3]!=0)generated.set(index);
        }
        return generated;
    }
    private Area ensureCell(World world,int cellX,int cellZ) {
        String key=cellKey(world.getName(),cellX,cellZ);String assigned=cellRegions.get(key);
        if(assigned!=null){String resolved=resolveRegion(assigned);Area existing=areas.get(resolved);
            if(existing!=null){if(!resolved.equals(assigned))cellRegions.put(key,resolved);return existing;}}
        int blockX=cellX*SAMPLE_SIZE,blockZ=cellZ*SAMPLE_SIZE;
        Biome biome=world.getBiome(blockX+SAMPLE_SIZE/2,world.getSeaLevel(),blockZ+SAMPLE_SIZE/2);
        String type = biomeFamily(biome.getKey().getKey());
        List<Area> neighbours=neighbouringRegions(world,cellX,cellZ,type);Area area=mergeRegions(neighbours);
        int minX=blockX,minZ=blockZ,maxX=blockX+SAMPLE_SIZE-1,maxZ=blockZ+SAMPLE_SIZE-1;
        if (area == null) {
            String id = "region:" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            long now=System.currentTimeMillis(); area = new Area(id,world.getName(),REGION,type,name(type,id),minX,
                world.getMinHeight(),minZ,maxX,world.getMaxHeight(),maxZ,false,now,true);
            save(area);
        } else {
            area = new Area(area.id, area.world, area.kind, area.type, area.name, Math.min(area.minX,minX), area.minY,
                Math.min(area.minZ,minZ), Math.max(area.maxX,maxX), area.maxY, Math.max(area.maxZ,maxZ),area.locked,area.createdAt,area.discovered);
            areas.put(area.id, area); updateBounds(area);
        }
        Area result = area;
        api.database().update("INSERT OR REPLACE INTO named_region_cells(world_name,cell_x,cell_z,region_id) VALUES(?,?,?,?)",ps->{
            ps.setString(1,world.getName());ps.setInt(2,cellX);ps.setInt(3,cellZ);ps.setString(4,result.id);});cellRegions.put(key,result.id);invalidateMapSnapshot();return result;
    }
    private List<Area> neighbouringRegions(World world,int x,int z,String type){Map<String,Area> found=new LinkedHashMap<>();
        for(int[]o:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){String id=cellRegions.get(cellKey(world.getName(),x+o[0],z+o[1]));
            if(id!=null)id=resolveRegion(id);Area a=id==null?null:areas.get(id);
            if(a!=null&&a.type.equals(type))found.put(a.id,a);}return new ArrayList<>(found.values());
    }
    private String resolveRegion(String id){String current=id,next;
        while((next=mergedRegions.get(current))!=null&&!next.equals(current))current=next;
        if(!current.equals(id))mergedRegions.put(id,current);return current;}
    private @Nullable Area mergeRegions(List<Area> candidates){if(candidates.isEmpty())return null;if(candidates.size()==1)return candidates.getFirst();
        candidates.sort(Comparator.comparing(Area::locked).reversed().thenComparingLong(Area::createdAt).thenComparing(Area::id));
        Area winner=candidates.getFirst();
        for(Area loser:candidates.subList(1,candidates.size())){
            if(winner.locked&&loser.locked)continue;
            String winnerId=winner.id;
            Area expanded=new Area(winner.id,winner.world,winner.kind,winner.type,winner.name,Math.min(winner.minX,loser.minX),winner.minY,
                Math.min(winner.minZ,loser.minZ),Math.max(winner.maxX,loser.maxX),winner.maxY,Math.max(winner.maxZ,loser.maxZ),winner.locked,winner.createdAt,winner.discovered);
            retire(loser,"merged into "+winnerId);api.database().update("UPDATE named_region_cells SET region_id=? WHERE region_id=?",ps->{ps.setString(1,winnerId);ps.setString(2,loser.id);});
            mergedRegions.put(loser.id,winnerId);api.database().update("DELETE FROM named_areas WHERE id=?",ps->ps.setString(1,loser.id));
            areas.remove(loser.id);areas.put(winner.id,expanded);updateBounds(expanded);winner=expanded;
        }return winner;
    }
    private void retire(Area area,String reason){long now=System.currentTimeMillis(),available=now+retirementMillis;
        api.database().update("INSERT INTO named_region_name_history(name,type,former_region_id,assigned_at,retired_at,available_at,reason) VALUES(?,?,?,?,?,?,?)",ps->{
            ps.setString(1,area.name);ps.setString(2,area.type);ps.setString(3,area.id);ps.setLong(4,area.createdAt);ps.setLong(5,now);ps.setLong(6,available);ps.setString(7,reason);});
        activeNames.remove(normaliseName(area.name));retiredNames.merge(normaliseName(area.name),available,Math::max);}
    private void updateBounds(Area a) { api.database().update("UPDATE named_areas SET min_x=?,min_z=?,max_x=?,max_z=? WHERE id=?", ps -> {
        ps.setInt(1,a.minX); ps.setInt(2,a.minZ); ps.setInt(3,a.maxX); ps.setInt(4,a.maxZ); ps.setString(5,a.id); }); }
    private void ensureStructure(World world, GeneratedStructure generated) {
        BoundingBox b = generated.getBoundingBox(); String raw = generated.getStructure().key().value(), type = structureFamily(raw);
        if (!getConfigSection().getBoolean("structures." + type + ".enabled", !Set.of("buried-treasure", "nether-fossil").contains(type))) return;
        String signature = world.getName()+':'+raw+':'+(int)b.getMinX()+':'+(int)b.getMinY()+':'+(int)b.getMinZ()+':'+(int)b.getMaxX()+':'+(int)b.getMaxY()+':'+(int)b.getMaxZ();
        String id = "structure:" + UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8));
        if (areas.containsKey(id)) return;
        long now=System.currentTimeMillis();save(new Area(id, world.getName(), STRUCTURE, type, name(type, id), (int)Math.floor(b.getMinX()), (int)Math.floor(b.getMinY()),
            (int)Math.floor(b.getMinZ()), (int)Math.ceil(b.getMaxX()), (int)Math.ceil(b.getMaxY()), (int)Math.ceil(b.getMaxZ()),false,now,false));
    }
    private void save(Area a) { int inserted = api.database().update(
        "INSERT OR IGNORE INTO named_areas(id,world_name,kind,type,name,min_x,min_y,min_z,max_x,max_y,max_z,locked,created_at,discovered) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", ps -> {
            ps.setString(1,a.id); ps.setString(2,a.world); ps.setString(3,a.kind); ps.setString(4,a.type); ps.setString(5,a.name);
            ps.setInt(6,a.minX); ps.setInt(7,a.minY); ps.setInt(8,a.minZ); ps.setInt(9,a.maxX); ps.setInt(10,a.maxY); ps.setInt(11,a.maxZ);
            ps.setInt(12,a.locked?1:0);ps.setLong(13,a.createdAt);ps.setInt(14,a.discovered?1:0);
        }); if (inserted > 0){areas.put(a.id, a);activeNames.add(normaliseName(a.name));invalidateMapSnapshot();} }

    private void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !enabled(event.getTo().getWorld())) return;
        Area area = areaAt(event.getTo()); if (area == null) area = ensureCell(event.getTo().getWorld(),Math.floorDiv(event.getTo().getBlockX(),SAMPLE_SIZE),Math.floorDiv(event.getTo().getBlockZ(),SAMPLE_SIZE));
        if(STRUCTURE.equals(area.kind)&&!area.discovered)area=markDiscovered(area);
        String previous = playerArea.put(event.getPlayer().getUniqueId(), area.id); if (!area.id.equals(previous)) announce(event.getPlayer(), area);
    }
    private @Nullable Area areaAt(Location location) {
        Area structure = areas.values().stream().filter(a -> STRUCTURE.equals(a.kind) && a.contains(location)).findFirst().orElse(null);
        if (structure != null) return structure;
        String id = cellRegions.get(cellKey(location.getWorld().getName(),Math.floorDiv(location.getBlockX(),SAMPLE_SIZE),Math.floorDiv(location.getBlockZ(),SAMPLE_SIZE)));
        if(id!=null)id=resolveRegion(id);
        return id == null ? null : areas.get(id);
    }
    private Area markDiscovered(Area old){Area found=new Area(old.id,old.world,old.kind,old.type,old.name,old.minX,old.minY,old.minZ,
        old.maxX,old.maxY,old.maxZ,old.locked,old.createdAt,true);areas.put(found.id,found);
        api.database().update("UPDATE named_areas SET discovered=1 WHERE id=?",ps->ps.setString(1,found.id));invalidateMapSnapshot();return found;}
    private void announce(Player player, Area area) {
        String title = render(titleTemplate, area), subtitle = render(subtitleTemplate, area); if (title.isBlank() && subtitle.isBlank()) return;
        player.showTitle(Title.title(title.isBlank()?Component.empty():TextUtil.colourise(title), subtitle.isBlank()?Component.empty():TextUtil.colourise(subtitle),
            Title.Times.times(Duration.ofMillis(fadeInMillis), Duration.ofMillis(stayMillis), Duration.ofMillis(fadeOutMillis))));
    }
    private @Nullable Component renderCoordinateBar(Player player){if(!enabled(player.getWorld()))return null;Area area=areaAt(player.getLocation());
        if(area==null)return null;return Component.text(getConfigSection().getString("coordbar.format"," ({name})")
            .replace("{name}",area.name).replace("{type}",friendly(area.type)));}
    private String render(String template, Area area) { return template == null ? "" : template.replace("{name}", area.name).replace("{type}", friendly(area.type)); }
    private String name(String type, String id) { List<String> configured = getConfigSection().getStringList("names.pools." + type);
        List<String> values = configured.isEmpty() ? defaultNames(type) : compactNames(configured);long now=System.currentTimeMillis();
        int start = Math.floorMod(id.hashCode(), values.size()); for(int i=0;i<values.size();i++) { String candidate=values.get((start+i)%values.size());
            String normalised=normaliseName(candidate);Long retiredUntil=retiredNames.get(normalised);
            if(retiredUntil!=null&&retiredUntil<=now)retiredNames.remove(normalised,retiredUntil);
            if(!activeNames.contains(normalised)&&!retiredNames.containsKey(normalised))return candidate;
        } return availableName(List.of(), type, id); }
    private String availableName(List<String> preferred, String type, String id) {
        List<String> candidates = new ArrayList<>(preferred);
        int preferredCount = candidates.size(); candidates.addAll(defaultNames(type));
        long now = System.currentTimeMillis();
        for (int i=0;i<candidates.size();i++) {
            int index = i < preferredCount ? Math.floorMod(id.hashCode()+i, preferredCount) : i;
            String candidate=candidates.get(index),normalised=normaliseName(candidate);
            Long retiredUntil=retiredNames.get(normalised);
            if(retiredUntil!=null&&retiredUntil<=now)retiredNames.remove(normalised,retiredUntil);
            if(!activeNames.contains(normalised)&&!retiredNames.containsKey(normalised))return candidate;
        }
        return friendly(type).replace(" ", "") + " " + Integer.toUnsignedString(id.hashCode(), 36);
    }
    private static String normaliseName(String name){return name.toLowerCase(Locale.ROOT);}

    /** Generates a large, unique pool containing only one- and two-word names. */
    static List<String> defaultNames(String type) { return DEFAULT_NAME_CACHE.computeIfAbsent(type, NamedRegions::generateDefaultNames); }
    private static List<String> generateDefaultNames(String type) { List<String> base = new ArrayList<>(160);
        for (String root : roots(type)) for (String form : forms(type)) base.add(form.replace("{root}", root));
        List<String> out = new ArrayList<>(1800); out.addAll(base); out.addAll(warcraftNames(type));
        for (String qualifier : split("Northern,Southern,Eastern,Western,Upper,Lower,Inner,Outer,High,Far"))
            for (String name : base) out.add(qualify(name, qualifier));
        return compactNames(out); }
    private static List<String> compactNames(Collection<String> names) {
        LinkedHashMap<String,String> unique = new LinkedHashMap<>();
        for (String name : names) for (String candidate : shortNameCandidates(name))
            unique.putIfAbsent(normaliseName(candidate), candidate);
        if (unique.isEmpty()) throw new IllegalArgumentException("A named-region pool must contain at least one word");
        return List.copyOf(unique.values());
    }
    static List<String> shortNameCandidates(String name) {
        List<String> original = Arrays.stream(name.trim().split("\\s+")).filter(word -> !word.isBlank()).toList();
        if (original.size() <= 2) return original.isEmpty() ? List.of() : List.of(String.join(" ", original));
        List<String> words = original.stream()
            .filter(word -> !Set.of("the", "of").contains(word.toLowerCase(Locale.ROOT))).toList();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (int gap=1;gap<words.size();gap++) for(int first=0;first+gap<words.size();first++)
            candidates.add(words.get(first)+" "+words.get(first+gap));
        return List.copyOf(candidates);
    }
    private static int wordCount(String name) { return (int)Arrays.stream(name.trim().split("\\s+")).filter(word -> !word.isBlank()).count(); }
    private static String qualify(String name, String qualifier) {
        return name.startsWith("The ") ? "The " + qualifier + " " + name.substring(4) : qualifier + " " + name;
    }
    private static String[] roots(String type) { return switch(type) {
        case "desert" -> split("Ratta,Suncleft,Amber,Mirage,Scorched,Dunewatch,Sunspire,Ashwind,Golden,Redglass,Sunscar,Dustveil,Ochre,Firewind,Glassreach,Sandstone,Daybreak,Brass,Heatwave,Solstice");
        case "forest" -> split("Elder,Whisper,Greenveil,Mosslight,Raven,Oakheart,Fernwatch,Wildwood,Moonpine,Thorn,Ashgrove,Briar,Deerwood,Elmshade,Foxglove,Hazel,Leafsong,Rowan,Silverwood,Wren");
        case "jungle" -> split("Emerald,Vine,Jaguar,Rainveil,Canopy,Temple,Orchid,Sunleaf,Parrot,Ancient,Bamboo,Cascade,Cocoa,Feather,Fernfall,Mistvine,Palm,Python,Stormleaf,Toucan");
        case "ocean" -> split("Azure,Coral,Tempest,Sapphire,Deepwater,Tide,Mariner,Stormglass,Seabird,Moonwake,Bluewater,Current,Dolphin,Foam,Leviathan,Pearl,Reef,Saltwind,Seafarer,Whalefall");
        case "mountains" -> split("Ironpeak,Cloudspire,Frosthorn,Highwatch,Stonecrown,Eagle,Thunder,Whitecap,Skyrend,Crag,Alpine,Boulder,Cliffwatch,Granite,Hawkwatch,Ironcrest,Slate,Stormpeak,Summit,Windcrown");
        case "snow" -> split("Frostmere,Whitewind,Winter,Icefall,Snowglass,Pale,Northwatch,Coldharbour,Frozen,Wolf,Blizzard,Crystal,Everfrost,Glacier,Hailstone,Hoarfrost,Polar,Rime,Sleet,Wintertide");
        case "swamp" -> split("Mire,Blackwater,Fen,Witchlight,Reed,Murk,Willow,Fogmere,Cypress,Bog,Brackwater,Bullrush,Dragonfly,Gloomwater,Lily,Mosshollow,Newt,Peat,Stillwater,Tanglewood");
        case "badlands" -> split("Redstone,Copper,Ashmesa,Rust,Sunscar,Canyon,Ochre,Dryfall,Embercliff,Terracotta,Claystone,Dustbowl,Flint,Gulch,Ironmesa,Painted,Redrock,Sandscar,Sunbaked,Vermilion");
        case "savanna" -> split("Goldgrass,Acacia,Sunveld,Lion,Amberplain,Longgrass,Drywind,Copperleaf,Horizon,Thornveld,Baobab,Cheetah,Dawnveld,Elephant,Grasssea,Marula,Pride,Sunland,Tallgrass,Zebra");
        case "taiga" -> split("Pinewatch,Wolfwood,Spruce,Northwood,Needle,Elk,Frostpine,Darkfir,Coldwood,Bear,Cedar,Firshade,Lynx,Moose,Northernpine,Pinecone,Ravenwood,Snowfir,Timber,Wolverine");
        case "mushroom-fields" -> split("Mycelium,Mooshroom,Sporecap,Redcap,Fungal,Mooncap,Mistycap,Giantcap,Oddwood,Toadstool,Bluecap,Glowspore,Greycap,Moldmere,Puffball,Shroomlight,Sporefall,Stemwood,Violetcap,Wondercap");
        case "plains" -> split("Ratta,Greenfield,Meadow,Westreach,Sunfield,Windmere,Goldmead,Openvale,Larkspur,Clover,Barley,Brookfield,Daisy,Dawnmead,Flax,Harvest,Hayfield,Primrose,Skylark,Wheatland");
        case "mineshaft" -> split("Ratta,Deepdelve,Ember,Blackpick,Oldstone,Coppervein,Ironroot,Underhill,Lostpick,Grimcoal,Blacklamp,Deepstone,Goldvein,Ironpick,Lantern,Lode,Oldrail,Prospector,Silverpick,Underdark");
        case "village" -> split("Ratta,Stoneford,Oakstead,Millhaven,Westbridge,Greenwick,Amberton,Rivermeet,Highfield,Willowby,Alderwick,Bellford,Brookstead,Cedarham,Dunwich,Fairmead,Mapleton,Redbrook,Thatchwick,Woodhaven");
        case "shipwreck" -> split("Maribel,Stormcrow,Sea Wraith,Golden Gull,North Star,Wayfarer,Tide Runner,Blue Finch,Argent Dawn,Red Sail,Albatross,Black Pearl,Corsair,Fair Wind,Flying Fish,Grey Petrel,Ocean Rose,Sea Drake,Silver Spray,Wanderer");
        case "ruined-portal" -> split("Fallen,Shattered,Ashen,Silent,Lost,Crimson,Broken,Forgotten,Ancient,Withered,Blighted,Charred,Ember,Fractured,Hollow,Obsidian,Riven,Scorched,Smouldering,Sundered");
        default -> split("Ratta,Ancient,Hidden,Forgotten,Silent,Ember,Stone,Shadow,Golden,Lost,Broken,Cinder,Deep,Echo,Fallen,Hollow,Iron,Moon,Old,Veiled"); }; }
    /** World of Warcraft zone names grouped by the closest Minecraft biome or structure family. */
    static List<String> warcraftNames(String type) { return List.of(switch(type) {
        case "desert" -> split("Durotar,Tanaris,Silithus,Uldum,Vol'dun,Netherstorm,Hellfire Peninsula,Blasted Lands");
        case "forest" -> split("Elwynn Forest,Eversong Woods,Silverpine Forest,Tirisfal Glades,Ashenvale,Darkshore,Felwood,Duskwood,Feralas,Teldrassil,Moonglade,Ghostlands,Val'sharah,Drustvar,Ardenweald,Jade Forest,Emerald Dream,Azuremyst Isle,Bloodmyst Isle");
        case "jungle" -> split("Stranglethorn Vale,Sholazar Basin,Tanaan Jungle,Un'Goro Crater,Zuldazar,Nazmir,Gorgrond,Krasarang Wilds");
        case "ocean" -> split("Vashj'ir,Nazjatar,Tiragarde Sound,Stormsong Valley,Azshara,Darkmoon Island,Timeless Isle,Forbidden Reach");
        case "mountains" -> split("Dun Morogh,Redridge Mountains,Stonetalon Mountains,Alterac Mountains,Arathi Highlands,Twilight Highlands,Highmountain,Kun-Lai Summit,Storm Peaks,Searing Gorge,Burning Steppes,Deepholm");
        case "snow" -> split("Winterspring,Dragonblight,Icecrown,Frostfire Ridge,Azure Span,Borean Tundra,Howling Fjord,Crystalsong Forest,Wintergrasp,Storm Peaks");
        case "swamp" -> split("Dustwallow Marsh,Zangarmarsh,Wetlands,Nazmir");
        case "badlands" -> split("Badlands,Desolace,Blasted Lands,Searing Gorge,Burning Steppes,Thousand Needles,Deadwind Pass,Antoran Wastes");
        case "savanna", "plains" -> split("Mulgore,Westfall,Nagrand,The Barrens,Arathi Highlands,Hillsbrad Foothills,Ohn'ahran Plains,Talador,Shadowmoon Valley");
        case "taiga" -> split("Grizzly Hills,Howling Fjord,Stormheim,Highmountain,Azure Span,Drustvar");
        case "mushroom-fields" -> split("Zangarmarsh,Deepholm,Korthia,Maldraxxus");
        case "mineshaft" -> split("Deadmines,Deepwind Gorge,Silvershard Mines,Stonevault,Darkflame Cleft");
        case "village" -> split("Goldshire,Darkshire,Lakeshire,Astranaar,Everlook,Southshore,Crossroads,Ratchet,Brill,Sentinel Hill,Halfhill,Dornogal");
        case "shipwreck" -> split("Lost Fleet,Shipwreck Shore,Faldir's Cove,Steamwheedle Port,Menethil Harbor,Stormwind Harbor");
        case "ruined-portal" -> split("Dark Portal,Emerald Dream,Darkmoon Faire");
        default -> split("Hallowfall,Azj-Kahet,Shadowlands,Dragon Isles,Broken Isles,Argus,Outland,Northrend,Pandaria,Draenor,Kalimdor,Khaz Algar,Undermine,Revendreth,Bastion,The Maw,Forbidden Reach,Emerald Dream");
    }); }
    private static String[] forms(String type) { return switch(type) {
        case "desert" -> split("{root} Desert,{root} Sands,{root} Expanse,Dunes of {root},{root} Wastes,{root} Dunes,{root} Barrens,The {root}");
        case "forest","jungle","taiga" -> split("{root} Forest,{root} Wood,{root} Wilds,The {root},{root} Grove,{root} Thicket,{root} Timberlands,The Woods of {root}");
        case "ocean" -> split("{root} Sea,{root} Ocean,{root} Waters,The {root},{root} Deep,{root} Expanse,{root} Sound,The Seas of {root}");
        case "mountains" -> split("{root} Mountains,{root} Range,{root} Peaks,The {root},{root} Heights,{root} Ridge,{root} Highlands,The Spine of {root}");
        case "snow" -> split("{root} Tundra,{root} Expanse,{root} Fields,The {root},{root} Wastes,{root} Icefield,{root} Snows,The Frosts of {root}");
        case "swamp" -> split("{root} Marsh,{root} Swamp,{root} Fen,The {root},{root} Wetlands,{root} Mire,{root} Morass,The Bogs of {root}");
        case "badlands" -> split("{root} Badlands,{root} Mesa,{root} Reach,The {root},{root} Barrens,{root} Bluffs,{root} Canyon,The Cliffs of {root}");
        case "savanna","plains" -> split("{root} Plains,{root} Fields,{root} Reach,The {root},{root} Grasslands,{root} Prairie,{root} Meadow,The Veld of {root}");
        case "mushroom-fields" -> split("{root} Fields,{root} Isles,{root} Reach,The {root},{root} Gardens,{root} Colony,{root} Wilds,The Caps of {root}");
        case "mineshaft" -> split("{root} Mine,{root} Mines,{root} Delve,The {root} Diggings,{root} Shaft,{root} Tunnels,{root} Lode,The Pits of {root}");
        case "village" -> split("{root},{root} Village,{root} Settlement,The Hamlet of {root},{root} Township,{root} Borough,{root} Crossroads,The Houses of {root}");
        case "shipwreck" -> split("The {root},Wreck of the {root},{root}'s Rest,The Lost {root},{root}'s Grave,Remains of the {root},The Sunken {root},Last Voyage of the {root}");
        case "ruined-portal" -> split("The {root} Gate,{root} Portal,{root} Threshold,The Arch of {root},{root} Gateway,{root} Rift,{root} Crossing,The Rupture of {root}");
        default -> split("{root} Hold,{root} Reach,{root} Ruins,The {root},{root} Keep,{root} Remnant,{root} Enclave,The Halls of {root}"); }; }
    private static String[] split(String value) { return value.split(","); }

    private void executeCommand(CommandContext ctx) {
        String action = ctx.getArg(0, "info").toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> {
                ctx.checkNotConsole(); Area area = areaAt(ctx.asPlayer().getLocation());
                if (area == null) ctx.returnInfo("No named region here."); else describe(ctx, area);
            }
            case "list" -> showList(ctx, areas.values().stream().sorted(Comparator.comparing(Area::name)).toList(), "Named regions");
            case "find" -> {
                String query = ctx.getArgsAsString(1, "").trim().toLowerCase(Locale.ROOT);
                if (query.isEmpty()) { ctx.returnError("Use /namedregion find <name>."); return; }
                List<Area> active=areas.values().stream().filter(a -> a.name.toLowerCase(Locale.ROOT).contains(query))
                    .sorted(Comparator.comparing(Area::name)).toList();
                if(!active.isEmpty())showList(ctx,active,"Active matches for "+query);
                final boolean[] retired={false};api.database().queryEach("SELECT name,type,former_region_id,available_at FROM named_region_name_history WHERE lower(name) LIKE ? ORDER BY retired_at DESC LIMIT 20",
                    ps->ps.setString(1,"%"+query+"%"),rs->{if(!retired[0])ctx.info("Retired matches:");retired[0]=true;long remaining=Math.max(0,rs.getLong(4)-System.currentTimeMillis());
                        ctx.info(rs.getString(1)+" - "+friendly(rs.getString(2))+" ["+rs.getString(3)+"] - available in "+((remaining+86_399_999L)/86_400_000L)+" day(s)");});
                if(active.isEmpty()&&!retired[0])ctx.returnInfo("No active or retired names match "+query+".");
            }
            case "nearby" -> {
                ctx.checkNotConsole(); Location location = ctx.asPlayer().getLocation();
                showList(ctx, areas.values().stream().filter(a -> a.world.equals(location.getWorld().getName()))
                    .sorted(Comparator.comparingDouble(a -> distanceSquared(location, a))).limit(10).toList(), "Nearby named regions");
            }
            case "teleport" -> {
                ctx.checkNotConsole(); Area area = areas.get(ctx.getArg(1, ""));
                if (area == null) { ctx.returnError("Unknown region ID."); return; }
                World world = Bukkit.getWorld(area.world); if (world == null) { ctx.returnError("That world is not loaded."); return; }
                double x=(area.minX+area.maxX)/2.0, z=(area.minZ+area.maxZ)/2.0;
                int y=STRUCTURE.equals(area.kind)?area.maxY+1:world.getHighestBlockYAt((int)x,(int)z)+1;
                ctx.asPlayer().teleport(new Location(world,x,y,z)); ctx.returnSuccess("Teleported to " + area.name + ".");
            }
            case "rename" -> {
                String id=ctx.getArg(1,""), newName=ctx.getArgsAsString(2,"").trim(); Area old=areas.get(id);
                if(old==null||newName.isEmpty()){ctx.returnError("Use /namedregion rename <id> <name>.");return;}
                Area renamed=new Area(old.id,old.world,old.kind,old.type,newName,old.minX,old.minY,old.minZ,old.maxX,old.maxY,old.maxZ,true,old.createdAt,old.discovered);
                retire(old,"renamed by admin");activeNames.add(normaliseName(newName));areas.put(id,renamed);
                api.database().update("UPDATE named_areas SET name=?,locked=1 WHERE id=?",ps->{ps.setString(1,newName);ps.setString(2,id);});
                invalidateMapSnapshot();
                ctx.returnSuccess("Renamed "+id+" to "+newName+".");
            }
            default -> ctx.returnError("Use /namedregion info, list, find, nearby, teleport, or rename.");
        }
    }
    private void showList(CommandContext ctx, List<Area> found, String heading) {
        if(found.isEmpty()){ctx.returnInfo(heading+": none.");return;} ctx.info(heading+" ("+found.size()+"):");
        for(Area area:found.stream().limit(50).toList())describe(ctx,area);
        if(found.size()>50)ctx.info("Showing the first 50 results. Use /namedregion find <name> to narrow the list.");
    }
    private void describe(CommandContext ctx, Area a) { ctx.info(a.name+" - "+friendly(a.type)+" - "+a.world+" ["+a.id+"] @ "+
        ((a.minX+a.maxX)/2)+", "+((a.minZ+a.maxZ)/2)); }
    private static double distanceSquared(Location l, Area a) { double x=(a.minX+a.maxX)/2.0-l.getX(),z=(a.minZ+a.maxZ)/2.0-l.getZ();return x*x+z*z; }
    private static String cellKey(String world,int x,int z){return world+':'+x+':'+z;}

    static String biomeFamily(String b) { if(b.contains("desert"))return "desert"; if(b.contains("badlands"))return "badlands";
        if(b.contains("jungle")||b.contains("bamboo"))return "jungle"; if(b.contains("swamp"))return "swamp";
        if(b.contains("ocean")||b.contains("beach"))return "ocean"; if(b.contains("snow")||b.contains("ice")||b.contains("frozen"))return "snow";
        if(b.contains("peak")||b.contains("slope")||b.contains("mountain")||b.contains("windswept"))return "mountains";
        if(b.contains("taiga")||b.contains("spruce"))return "taiga"; if(b.contains("savanna"))return "savanna";
        if(b.contains("mushroom"))return "mushroom-fields"; if(b.contains("forest")||b.contains("grove"))return "forest"; return "plains"; }
    static String structureFamily(String type) { if(type.startsWith("village_"))return "village"; if(type.startsWith("ruined_portal"))return "ruined-portal";
        if(type.startsWith("mineshaft"))return "mineshaft"; if(type.startsWith("shipwreck"))return "shipwreck";
        if(type.startsWith("ocean_ruin"))return "ocean-ruins"; return type.replace('_','-'); }
    private static String friendly(String value) { StringJoiner out = new StringJoiner(" "); for(String word:value.split("[-_]"))
        out.add(Character.toUpperCase(word.charAt(0))+word.substring(1)); return out.toString(); }
    private boolean enabled(World world) { return worlds.contains(world.getName().toLowerCase(Locale.ROOT)); }
    private void enableMap() { if(!getConfigSection().getBoolean("map.enabled",true))return; Plugin p=Bukkit.getPluginManager().getPlugin("Pl3xMap");
        if(p==null||!p.isEnabled())return; try {
            var biomeStyle=new Pl3xMapNamedRegions.Style(mapColour("map.biomes.stroke-colour","#FFFF9800"),
                mapColour("map.biomes.fill-colour","#40FF9800"),Math.max(1,getConfigSection().getInt("map.biomes.line-thickness",2)));
            var structureStyle=new Pl3xMapNamedRegions.Style(mapColour("map.structures.stroke-colour","#FFFFC107"),
                mapColour("map.structures.fill-colour","#40FFC107"),Math.max(1,getConfigSection().getInt("map.structures.line-thickness",2)));
            String biomeLayer=getConfigSection().getString("map.layers.biomes","Biome Regions");
            String structureLayer=getConfigSection().getString("map.layers.structures","Discovered Structures");
            int updateSeconds=Math.max(60,getConfigSection().getInt("map.update-minutes",5)*60);
            mapRefreshMillis=updateSeconds*1000L;
            mapLayer=new Pl3xMapNamedRegions(this::mapAreas,biomeStyle,structureStyle,getConfigSection().getBoolean("map.permanent-labels",false),
                biomeLayer,structureLayer,updateSeconds);mapLayer.enable(); }
        catch(RuntimeException e){STEMCraft.getPlugin().getLogger().warning("Could not enable named-region map layer: "+e.getMessage());} }
    private int mapColour(String path,String fallback){String raw=getConfigSection().getString(path,fallback).trim().replace("#","").replaceFirst("(?i)^0x","");
        if(raw.length()==6)raw="FF"+raw;if(raw.length()!=8)return(int)Long.parseLong(fallback.substring(1),16);
        try{return(int)Long.parseLong(raw,16);}catch(NumberFormatException ignored){return(int)Long.parseLong(fallback.substring(1),16);}}
    private Collection<NamedMapArea> mapAreas() {
        long now=System.currentTimeMillis();
        if(!shouldRefreshMapSnapshot(mapSnapshotDirty,mapSnapshotBuiltAt,now,mapRefreshMillis))return mapSnapshot;
        synchronized(mapSnapshotLock){
            now=System.currentTimeMillis();
            if(!shouldRefreshMapSnapshot(mapSnapshotDirty,mapSnapshotBuiltAt,now,mapRefreshMillis))return mapSnapshot;
            mapSnapshot=buildMapAreas();mapSnapshotBuiltAt=now;mapSnapshotDirty=false;return mapSnapshot;
        }
    }
    static boolean shouldRefreshMapSnapshot(boolean dirty,long builtAt,long now,long intervalMillis){
        return dirty&&(builtAt==0||now>=builtAt&&now-builtAt>=intervalMillis);
    }
    private Collection<NamedMapArea> buildMapAreas() {
        List<NamedMapArea> result=new ArrayList<>();
        for(Area a:areas.values())if(STRUCTURE.equals(a.kind)&&a.discovered)result.add(new NamedMapArea(a.id,a.world,a.name,friendly(a.type),List.of(List.of(
            new NamedMapArea.MapPoint(a.minX,a.minZ),new NamedMapArea.MapPoint(a.maxX,a.minZ),
            new NamedMapArea.MapPoint(a.maxX,a.maxZ),new NamedMapArea.MapPoint(a.minX,a.maxZ)))));
        Map<String,Set<Long>> byRegion=new HashMap<>();
        for(Map.Entry<String,String> entry:cellRegions.entrySet()){String[] key=entry.getKey().split(":");if(key.length<3)continue;
            int x=Integer.parseInt(key[key.length-2]),z=Integer.parseInt(key[key.length-1]);
            byRegion.computeIfAbsent(resolveRegion(entry.getValue()),ignored->new HashSet<>()).add(pack(x,z));}
        for(Map.Entry<String,Set<Long>> entry:byRegion.entrySet()){Area a=areas.get(entry.getKey());if(a==null)continue;
            result.add(new NamedMapArea(a.id,a.world,a.name,friendly(a.type),boundaryLoops(entry.getValue())));}return List.copyOf(result);
    }
    private void invalidateMapSnapshot(){mapSnapshotDirty=true;}
    static List<List<NamedMapArea.MapPoint>> boundaryLoops(Set<Long> cells){Map<Long,Deque<Long>> edges=new HashMap<>();
        for(long packed:cells){int x=unpackX(packed),z=unpackZ(packed),bx=x*SAMPLE_SIZE,bz=z*SAMPLE_SIZE;
            if(!cells.contains(pack(x,z-1)))edge(edges,bx,bz,bx+SAMPLE_SIZE,bz);
            if(!cells.contains(pack(x+1,z)))edge(edges,bx+SAMPLE_SIZE,bz,bx+SAMPLE_SIZE,bz+SAMPLE_SIZE);
            if(!cells.contains(pack(x,z+1)))edge(edges,bx+SAMPLE_SIZE,bz+SAMPLE_SIZE,bx,bz+SAMPLE_SIZE);
            if(!cells.contains(pack(x-1,z)))edge(edges,bx,bz+SAMPLE_SIZE,bx,bz);}
        List<List<NamedMapArea.MapPoint>> loops=new ArrayList<>();
        while(!edges.isEmpty()){long start=edges.keySet().iterator().next(),current=start;List<NamedMapArea.MapPoint> loop=new ArrayList<>();int guard=0;
            do{loop.add(new NamedMapArea.MapPoint(unpackX(current),unpackZ(current)));Deque<Long> next=edges.get(current);if(next==null||next.isEmpty())break;
                long following=next.removeFirst();if(next.isEmpty())edges.remove(current);current=following;}while(current!=start&&guard++<100000);
            if(loop.size()>=3)loops.add(loop);}return loops;
    }
    private static void edge(Map<Long,Deque<Long>> edges,int x1,int z1,int x2,int z2){edges.computeIfAbsent(pack(x1,z1),ignored->new ArrayDeque<>()).add(pack(x2,z2));}
    static long pack(int x,int z){return((long)x<<32)|(z&0xffffffffL);}private static int unpackX(long value){return(int)(value>>32);}private static int unpackZ(long value){return(int)value;}
}
