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
import net.pl3x.map.core.world.World;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Supplier;

/** Optional live Pl3xMap layer for generated region and structure names. */
public final class Pl3xMapNamedRegions {
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
    private final List<Style> biomeStyles;
    private final boolean permanentLabels;
    private final String biomeLayerName;
    private final String structureLayerName;
    private final int updateIntervalSeconds;
    private final int structureIconSize;
    public Pl3xMapNamedRegions(STEMCraft plugin, Supplier<Collection<NamedMapArea>> areas, List<Style> biomeStyles,
                               boolean permanentLabels, String biomeLayerName, String structureLayerName,
                               int updateIntervalSeconds, int structureIconSize) {
        this.plugin = plugin; this.areas = areas; this.biomeStyles = List.copyOf(biomeStyles);
        if (this.biomeStyles.isEmpty()) throw new IllegalArgumentException("At least one biome map style is required");
        this.permanentLabels = permanentLabels; this.biomeLayerName = biomeLayerName;
        this.structureLayerName = structureLayerName; this.updateIntervalSeconds = updateIntervalSeconds;
        this.structureIconSize = structureIconSize;
    }
    public void enable() { registerStructureIcons(); Pl3xMap.api().getWorldRegistry().forEach(world -> {
        world.getLayerRegistry().unregister(BIOME_KEY); world.getLayerRegistry().unregister(STRUCTURE_KEY);
        world.getLayerRegistry().register(BIOME_KEY, new Layer(BIOME_KEY, biomeLayerName, world, false));
        world.getLayerRegistry().register(STRUCTURE_KEY, new Layer(STRUCTURE_KEY, structureLayerName, world, true));
    }); }
    public void disable() { Pl3xMap.api().getWorldRegistry().forEach(world -> {
        world.getLayerRegistry().unregister(BIOME_KEY); world.getLayerRegistry().unregister(STRUCTURE_KEY);
    }); ICON_NAMES.forEach(name->Pl3xMap.api().getIconRegistry().unregister(STRUCTURE_ICON_PREFIX+name)); }

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
            setLiveUpdate(true); setShowControls(true); setDefaultHidden(false); setPriority(35); setZIndex(35); }
        @Override public Collection<Marker<?>> getMarkers() {
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
                        .tooltipContent(tooltip)
                        .tooltipPermanent(permanentLabels).build()));
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
