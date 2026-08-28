/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.coordinatebar;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Supplies optional, player-specific content appended to the coordinate boss bar. */
@FunctionalInterface
public interface CoordinateBarProvider {
    /**
     * Render this provider's current content.
     *
     * @return content to append, or {@code null} when nothing should be shown
     */
    @Nullable Component render(@NotNull Player player);
}
