package dev.stemcraft.api.service.imagemap;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/** A runtime image-map mosaic managed by {@link ImageMapService}. */
public record ImageMapDisplay(@NotNull String id,
                              @NotNull Location backingBlock,
                              @NotNull BlockFace facing,
                              int columns,
                              int rows) {
    public ImageMapDisplay {
        backingBlock = backingBlock.clone();
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Image-map dimensions must be positive");
        }
    }

    @Override
    public @NotNull Location backingBlock() {
        return backingBlock.clone();
    }
}
