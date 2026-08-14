package dev.stemcraft.api.service.imagemap;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

/** Creates and updates multi-map image displays mounted on walls. */
public interface ImageMapService {
    /** Creates or replaces a display. The location is its bottom-left backing block. */
    @NotNull ImageMapDisplay create(@NotNull String id,
                                    @NotNull Location backingBlock,
                                    @NotNull BlockFace facing,
                                    int columns,
                                    int rows);

    /** Renders an image across a display, scaling it to the display dimensions. */
    boolean render(@NotNull String id, @NotNull BufferedImage image);

    /** Removes a display and its managed item frames. */
    boolean delete(@NotNull String id);

    /** Returns whether a display is currently registered. */
    boolean exists(@NotNull String id);
}
