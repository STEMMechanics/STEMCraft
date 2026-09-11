/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.STEMCraft;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.markers.layer.WorldLayer;
import net.pl3x.map.core.world.World;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Optional Pl3xMap layer containing active graves. */
public final class Pl3xMapGraveMarkers {
    private static final String LAYER_KEY = "stemcraft_graves";
    private static final String ICON_KEY = "stemcraft_gravestone";
    private final STEMCraft plugin;
    private final Supplier<Collection<GraveMapMarker>> graves;

    public Pl3xMapGraveMarkers(STEMCraft plugin, Supplier<Collection<GraveMapMarker>> graves) {
        this.plugin = plugin;
        this.graves = graves;
    }

    public void enable() {
        registerIcon();
        Pl3xMap.api().getWorldRegistry().forEach(world -> {
            world.getLayerRegistry().unregister(LAYER_KEY);
            world.getLayerRegistry().register(LAYER_KEY, new GraveLayer(world));
        });
    }

    public void disable() {
        Pl3xMap.api().getWorldRegistry().forEach(world -> world.getLayerRegistry().unregister(LAYER_KEY));
        Pl3xMap.api().getIconRegistry().unregister(ICON_KEY);
    }

    private void registerIcon() {
        try (InputStream input = plugin.getResource("pl3xmap/gravestone.png")) {
            if (input == null) throw new IOException("Bundled gravestone icon is missing");
            BufferedImage image = ImageIO.read(input);
            Pl3xMap.api().getIconRegistry().register(ICON_KEY, new IconImage(ICON_KEY, image, "png"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not register the Pl3xMap gravestone icon", exception);
        }
    }

    private final class GraveLayer extends WorldLayer {
        private GraveLayer(World world) {
            super(LAYER_KEY, world, () -> "Graves");
            setUpdateInterval(1);
            setLiveUpdate(true);
            setShowControls(true);
            setDefaultHidden(false);
            setPriority(40);
            setZIndex(40);
        }

        @Override
        public Collection<Marker<?>> getMarkers() {
            List<Marker<?>> markers = new ArrayList<>();
            graves.get().stream()
                .filter(grave -> grave.world().equals(getWorld().getName()))
                .forEach(grave -> markers.add(Marker.icon(grave.id().toString(), grave.x(), grave.z(), ICON_KEY, 24)
                    .setOptions(Options.builder()
                        .tooltipContent(escapeHtml(grave.ownerName()) + "'s grave<br>"
                            + grave.x() + ", " + grave.y() + ", " + grave.z())
                        .tooltipDirection(Tooltip.Direction.RIGHT)
                        .build())));
            return markers;
        }
    }

    static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
