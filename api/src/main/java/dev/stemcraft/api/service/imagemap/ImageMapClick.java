package dev.stemcraft.api.service.imagemap;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * A player interaction with one tile in an image-map display.
 *
 * @param displayId stable display identifier
 * @param player player who clicked the display
 * @param tileColumn zero-based tile column from the display's left edge
 * @param tileRow zero-based tile row from the display's bottom edge
 */
public record ImageMapClick(@NotNull String displayId,
                            @NotNull Player player,
                            int tileColumn,
                            int tileRow) {
}
