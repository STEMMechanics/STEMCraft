/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.coordinatebar;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Extension point for adding contextual, live content to the coordinate boss bar. */
public interface CoordinateBarService {
    /**
     * Register or replace a provider owned by a plugin.
     * Lower priority values render first. Registrations belonging to disabled plugins are discarded.
     */
    void register(@NotNull Plugin owner, @NotNull String id, int priority,
                  @NotNull CoordinateBarProvider provider);

    /** Remove one provider registration. */
    void unregister(@NotNull Plugin owner, @NotNull String id);

    /** Render the currently active additions for a player in display order. */
    @NotNull List<Component> render(@NotNull Player player);
}
