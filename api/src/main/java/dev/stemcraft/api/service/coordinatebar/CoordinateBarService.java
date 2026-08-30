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

    /**
     * Register or replace content appended directly to a built-in section.
     * No spacing or separator is inserted; the provider must include any desired whitespace.
     */
    void registerAmendment(@NotNull Plugin owner, @NotNull String id,
                           @NotNull CoordinateBarSection section, int priority,
                           @NotNull CoordinateBarProvider provider);

    /** Remove one built-in section amendment. */
    void unregisterAmendment(@NotNull Plugin owner, @NotNull String id,
                             @NotNull CoordinateBarSection section);

    /** Render amendments for one built-in section in display order. */
    @NotNull List<Component> renderAmendments(@NotNull CoordinateBarSection section,
                                               @NotNull Player player);

    /** Render the currently active additions for a player in display order. */
    @NotNull List<Component> render(@NotNull Player player);
}
