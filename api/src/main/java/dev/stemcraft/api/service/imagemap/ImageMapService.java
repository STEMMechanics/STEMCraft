package dev.stemcraft.api.service.imagemap;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

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

    /** Registers or replaces the callback invoked when a player clicks a display tile. */
    default void onClick(@NotNull String id, @NotNull Consumer<ImageMapClick> callback) {
        throw new UnsupportedOperationException("This image-map implementation does not support clicks");
    }

    /** Removes the click callback for a display without deleting the display. */
    default void clearClickHandler(@NotNull String id) { }
}
