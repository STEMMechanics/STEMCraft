package dev.stemcraft.integration.pl3xmap;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.layer.WorldLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.world.World;
import java.util.*;
import java.util.function.Supplier;

/** Optional live Pl3xMap layer for generated region and structure names. */
public final class Pl3xMapNamedRegions {
    public record Style(int strokeColour, int fillColour, int lineThickness) { }
    private static final String BIOME_KEY = "stemcraft_named_regions";
    private static final String STRUCTURE_KEY = "stemcraft_named_structures";
    private final Supplier<Collection<NamedMapArea>> areas;
    private final Style biomeStyle;
    private final Style structureStyle;
    private final boolean permanentLabels;
    private final String biomeLayerName;
    private final String structureLayerName;
    private final int updateIntervalSeconds;
    public Pl3xMapNamedRegions(Supplier<Collection<NamedMapArea>> areas, Style biomeStyle,
                               Style structureStyle, boolean permanentLabels, String biomeLayerName,
                               String structureLayerName, int updateIntervalSeconds) {
        this.areas = areas; this.biomeStyle = biomeStyle; this.structureStyle = structureStyle;
        this.permanentLabels = permanentLabels; this.biomeLayerName = biomeLayerName;
        this.structureLayerName = structureLayerName; this.updateIntervalSeconds = updateIntervalSeconds;
    }
    public void enable() { Pl3xMap.api().getWorldRegistry().forEach(world -> {
        world.getLayerRegistry().unregister(BIOME_KEY); world.getLayerRegistry().unregister(STRUCTURE_KEY);
        world.getLayerRegistry().register(BIOME_KEY, new Layer(BIOME_KEY, biomeLayerName, world, false));
        world.getLayerRegistry().register(STRUCTURE_KEY, new Layer(STRUCTURE_KEY, structureLayerName, world, true));
    }); }
    public void disable() { Pl3xMap.api().getWorldRegistry().forEach(world -> {
        world.getLayerRegistry().unregister(BIOME_KEY); world.getLayerRegistry().unregister(STRUCTURE_KEY);
    }); }

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
                Style style = area.id().startsWith("structure:") ? structureStyle : biomeStyle;
                List<Polyline> rings = area.boundaries().stream().map(points -> Marker.polyline(area.id() + "-ring",
                    points.stream().map(point -> Point.of(point.x(), point.z())).toList()).loop()).toList();
                result.add(Marker.polygon(area.id(), rings)
                    .setOptions(Options.builder().stroke(true).strokeWeight(style.lineThickness())
                        .strokeColor(style.strokeColour()).fill(true).fillColor(style.fillColour())
                        .tooltipContent(escape(area.name()) + "<br><small>" + escape(area.type()) + "</small>")
                        .tooltipPermanent(permanentLabels).build()));
            }
            return result;
        }
    }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
