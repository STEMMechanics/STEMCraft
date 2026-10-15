package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.STEMCraft;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.WorldLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.util.FileUtil;
import net.pl3x.map.core.world.World;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Supplier;

/** Optional live Pl3xMap layer for generated region and structure names. */
public final class Pl3xMapNamedRegions implements net.pl3x.map.core.event.EventListener {
    private volatile boolean enabled;
    public record Style(int strokeColour, int fillColour, int lineThickness) { }
    private static final String BIOME_KEY = "stemcraft_named_regions";
    private static final String STRUCTURE_KEY = "stemcraft_named_structures";
    private static final String STRUCTURE_ICON_PREFIX = "stemcraft_named_structure_";
    private static final Set<String> ICON_NAMES=Set.of("ancient-city","bastion-remnant","buried-treasure",
        "desert-pyramid","end-city","fortress","igloo","jungle-pyramid","mansion","mineshaft","monument",
        "nether-fossil","ocean-ruins","pillager-outpost","ruined-portal","shipwreck","stronghold","swamp-hut",
        "trail-ruins","trial-chambers","village","generic");
    private final STEMCraft plugin;
    private final Supplier<Collection<NamedMapArea>> areas;
    private final NamedMapTilePublisher tiles;
    private final List<Style> biomeStyles;
    private final boolean permanentLabels;
    private final boolean biomesEnabled;
    private final String biomeLayerName;
    private final String structureLayerName;
    private final int updateIntervalSeconds;
    private final int structureIconSize;
    private final Pl3xMapRegionWebFiles webFiles;
    public Pl3xMapNamedRegions(STEMCraft plugin, Supplier<Collection<NamedMapArea>> areas, List<Style> biomeStyles,
                               boolean permanentLabels, String biomeLayerName, String structureLayerName,
                               int updateIntervalSeconds, int structureIconSize) {
        this(plugin, areas, biomeStyles, permanentLabels, true, biomeLayerName, structureLayerName,
            updateIntervalSeconds, structureIconSize, true);
    }
    public Pl3xMapNamedRegions(STEMCraft plugin, Supplier<Collection<NamedMapArea>> areas, List<Style> biomeStyles,
                               boolean permanentLabels, boolean biomesEnabled, String biomeLayerName,
                               String structureLayerName, int updateIntervalSeconds, int structureIconSize,
                               boolean autoInstallWebFiles) {
        this(plugin, areas, null, biomeStyles, permanentLabels, biomesEnabled, biomeLayerName,
            structureLayerName, updateIntervalSeconds, structureIconSize, autoInstallWebFiles);
        if (this.biomeStyles.isEmpty()) throw new IllegalArgumentException("At least one biome map style is required");
    }
    public Pl3xMapNamedRegions(STEMCraft plugin, Supplier<Collection<NamedMapArea>> areas, NamedMapTilePublisher tiles,
                               boolean biomesEnabled, String biomeLayerName, String structureLayerName,
                               int updateIntervalSeconds, int structureIconSize) {
        this(plugin, areas, tiles, biomesEnabled, biomeLayerName, structureLayerName,
            updateIntervalSeconds, structureIconSize, true);
    }
    public Pl3xMapNamedRegions(STEMCraft plugin, Supplier<Collection<NamedMapArea>> areas, NamedMapTilePublisher tiles,
                               boolean biomesEnabled, String biomeLayerName, String structureLayerName,
                               int updateIntervalSeconds, int structureIconSize,
                               boolean autoInstallWebFiles) {
        this(plugin, areas, Objects.requireNonNull(tiles), List.of(), false, biomesEnabled, biomeLayerName,
            structureLayerName, updateIntervalSeconds, structureIconSize, autoInstallWebFiles);
    }
    private Pl3xMapNamedRegions(STEMCraft plugin, Supplier<Collection<NamedMapArea>> areas, NamedMapTilePublisher tiles,
                                List<Style> biomeStyles, boolean permanentLabels, boolean biomesEnabled,
                                String biomeLayerName, String structureLayerName, int updateIntervalSeconds,
                                int structureIconSize, boolean autoInstallWebFiles) {
        this.plugin = plugin; this.areas = areas; this.tiles = tiles;
        this.biomeStyles = List.copyOf(biomeStyles); this.permanentLabels = permanentLabels;
        this.biomesEnabled = biomesEnabled; this.biomeLayerName = biomeLayerName;
        this.structureLayerName = structureLayerName; this.updateIntervalSeconds = updateIntervalSeconds;
        this.structureIconSize = structureIconSize;
        this.webFiles = autoInstallWebFiles ? new Pl3xMapRegionWebFiles(plugin) : null;
    }
    public void enable() {
        if (enabled) return;
        enabled = true;
        Pl3xMap.api().getEventRegistry().register(this);
        installWebFiles();
        registerStructureIcons();
        Pl3xMap.api().getWorldRegistry().forEach(this::registerWorld);
    }

    @net.pl3x.map.core.event.EventHandler
    public void onWorldLoaded(net.pl3x.map.core.event.world.WorldLoadedEvent event) {
        if (enabled) registerWorld(event.getWorld());
    }

    @net.pl3x.map.core.event.EventHandler
    public void onMapEnabled(net.pl3x.map.core.event.server.Pl3xMapEnabledEvent event) {
        if (!enabled) return;
        // Pl3xMap's own reload clears icons and reconstructs its worlds.
        // Its web files have already been extracted before this event fires.
        installWebFiles();
        registerStructureIcons();
        Pl3xMap.api().getWorldRegistry().forEach(this::registerWorld);
    }

    private void installWebFiles() {
        if (webFiles == null) return;
        try {
            if (biomesEnabled && tiles != null) webFiles.install(FileUtil.getWebDir());
            else webFiles.uninstall(FileUtil.getWebDir());
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Could not update named-region browser files in " + FileUtil.getWebDir()
                    + "; check the generated web directory and file permissions", exception);
        }
    }

    private void registerWorld(World world) {
        world.getLayerRegistry().unregister(BIOME_KEY);
        world.getLayerRegistry().unregister(STRUCTURE_KEY);
        if (tiles != null || !biomesEnabled) clearLegacyGeometry(world);
        if (biomesEnabled && (tiles == null || tiles.supports(world.getName()))) {
            world.getLayerRegistry().register(BIOME_KEY, new Layer(BIOME_KEY, biomeLayerName, world, false));
            if (tiles != null) refreshTiles(world);
        }
        world.getLayerRegistry().register(STRUCTURE_KEY, new Layer(STRUCTURE_KEY, structureLayerName, world, true));
    }

    public void disable() {
        enabled = false;
        if (tiles != null) tiles.close();
        // Pl3xMap has no EventRegistry.unregister API; event handler lists are mutable.
        new net.pl3x.map.core.event.world.WorldLoadedEvent(null).getHandlers()
            .removeIf(handler -> handler.getListener() == this);
        new net.pl3x.map.core.event.server.Pl3xMapEnabledEvent().getHandlers()
            .removeIf(handler -> handler.getListener() == this);
        Pl3xMap.api().getWorldRegistry().forEach(world -> {
            world.getLayerRegistry().unregister(BIOME_KEY);
            world.getLayerRegistry().unregister(STRUCTURE_KEY);
            if (tiles != null || !biomesEnabled) clearLegacyGeometry(world);
        });
        ICON_NAMES.forEach(name -> Pl3xMap.api().getIconRegistry().unregister(STRUCTURE_ICON_PREFIX + name));
    }

    private void refreshTiles(World world) {
        tiles.refresh(world.getName(), world.getTilesDirectory().resolve("stemcraft-regions"), current -> {
            if (enabled && Pl3xMap.api().getWorldRegistry().get(world.getName()) == world)
                world.getServerSentEventHandler().send("stemcraft-regions", "{\"revision\":" + current + "}");
        });
    }

    private static void clearLegacyGeometry(World world) {
        // Old browsers may still request this URL after the layer is replaced or disabled.
        FileUtil.writeJson("[]", world.getMarkersDirectory().resolve(BIOME_KEY + ".json"));
    }

    private void registerStructureIcons() {
        for(String name:ICON_NAMES){String key=STRUCTURE_ICON_PREFIX+name;
            try (InputStream input = plugin.getResource("pl3xmap/structure-"+name+".png")) {
                if (input == null) throw new IOException("Bundled structure icon is missing: "+name);
                BufferedImage image = ImageIO.read(input);Pl3xMap.api().getIconRegistry().unregister(key);
                Pl3xMap.api().getIconRegistry().register(key,new IconImage(key,image,"png"));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not register the Pl3xMap structure icon: "+name,exception);
            }}
    }

    private final class Layer extends WorldLayer {
        private final boolean structures;
        private Layer(String key, String name, World world, boolean structures) { super(key, world, () -> name); this.structures=structures; setUpdateInterval(updateIntervalSeconds);
            setLiveUpdate(structures || tiles == null); setShowControls(true); setDefaultHidden(false); setPriority(35); setZIndex(35); }
        @Override public Collection<Marker<?>> getMarkers() {
            if (!enabled) return List.of();
            if (!structures && tiles != null) {
                refreshTiles(getWorld());
                // The browser extension owns viewport geometry. Neither marker JSON nor
                // Pl3xMap's live marker stream receives the whole-world boundaries.
                return List.of();
            }
            List<Marker<?>> result = new ArrayList<>();
            for (NamedMapArea area : areas.get()) {
                if (!area.world().equals(getWorld().getName())) continue;
                if (area.id().startsWith("structure:") != structures) continue;
                if (area.boundaries().isEmpty()) continue;
                String tooltip = escape(area.name()) + "<br><small>" + escape(area.type()) + "</small>";
                if (structures) {
                    NamedMapArea.MapPoint centre = centre(area);
                    String icon=iconName(area.type());
                    result.add(Marker.icon(area.id(), centre.x(), centre.z(), STRUCTURE_ICON_PREFIX+icon, structureIconSize)
                        .setOptions(Options.builder().tooltipContent(tooltip)
                            .tooltipDirection(Tooltip.Direction.RIGHT).build()));
                    continue;
                }
                Style style = biomeStyles.get(Math.floorMod(area.id().hashCode(), biomeStyles.size()));
                List<Polyline> rings = area.boundaries().stream().map(points -> Marker.polyline(area.id() + "-ring",
                    points.stream().map(point -> Point.of(point.x(), point.z())).toList()).loop()).toList();
                result.add(Marker.polygon(area.id(), rings)
                    .setOptions(Options.builder().stroke(true).strokeWeight(style.lineThickness())
                        .strokeColor(style.strokeColour()).fill(true).fillColor(style.fillColour())
                        .tooltipContent(tooltip).tooltipPermanent(permanentLabels).build()));
            }
            return result;
        }
    }
    static NamedMapArea.MapPoint centre(NamedMapArea area) {
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(List<NamedMapArea.MapPoint> boundary:area.boundaries())for(NamedMapArea.MapPoint point:boundary){
            minX=Math.min(minX,point.x());minZ=Math.min(minZ,point.z());
            maxX=Math.max(maxX,point.x());maxZ=Math.max(maxZ,point.z());}
        return new NamedMapArea.MapPoint(minX+(maxX-minX)/2,minZ+(maxZ-minZ)/2);
    }
    static String iconType(String friendlyType){return friendlyType.toLowerCase(Locale.ROOT).replace(' ','-');}
    static String iconName(String friendlyType){String type=iconType(friendlyType);return ICON_NAMES.contains(type)?type:"generic";}
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
