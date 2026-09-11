/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.api.service.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Service for managing holograms in the Minecraft world.
 */
public interface HologramService {
    int DEFAULT_RANGE = 20;

    /**
     * Register a hologram type handler.
     *
     * @param type type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME").
     * @param handler handler for this type.
     */
    void registerType(@NotNull String type, @NotNull HologramTypeHandler handler);

    /**
     * Create and spawn a hologram.
     *
     * @param type type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME").
     * @param context context/reference (e.g. unique ID, leaderboard name, NPC id).
     * @param location world location to spawn at.
     * @param data additional data for the hologram.
     * @return unique hologram id.
     */
    int create(@NotNull String type, @NotNull String context, @NotNull Location location, @NotNull List<String> data);

    /**
     * Update all lines for a hologram and refresh it in-world.
     *
     * @param id hologram id.
     * @param lines new lines to set.
     */
    void update(int id, @NotNull List<String> lines);

    /**
     * Update lines of all holograms of this type.
     *
     * @param type type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME").
     * @param ref context/reference (e.g. unique ID, leaderboard name, NPC id.
     */
    void update(@NotNull String type, @Nullable String ref);

    /**
     * Move a hologram to a new location.
     *
     * @param id hologram id.
     * @param newLocation new world location.
     */
    void move(int id, @NotNull Location newLocation);

    /**
     * Delete a hologram by id and remove its entities.
     *
     * @param id hologram id.
     */
    void delete(int id);

    /**
     * Delete all holograms of a given type.
     *
     * @param type type/category (e.g. "UNIQUE", "LEADERBOARD", "NPC_NAME").
     * @param context context/reference (e.g. unique ID, leaderboard name, NPC id.
     */
    void delete(@NotNull String type, @Nullable String context);

    /**
     * Find the closest hologram from a location.
     *
     * @param loc location to search from.
     * @param range maximum range to search within.
     * @return hologram id or -1 if none found within range.
     */
    int closest(@NotNull Location loc, int range);
    default int closest(@NotNull Location loc) { return closest(loc, DEFAULT_RANGE); }

    /**
     * Despawn all entities, typically on plugin disable.
     */
    void despawnAll();

    /**
     * Save a hologram to disk (or null for all).
     *
     * @param id hologram id or null for all.
     */
    void save(@Nullable Integer id);
    default void saveAll() { save(null); }

    /**
     * Creates or replaces a player-specific hologram identified by a stable type and context.
     * Dynamic holograms are runtime registrations and are not persisted by the service.
     */
    void createDynamic(@NotNull String type,
                       @NotNull String context,
                       @NotNull Location location,
                       @NotNull Predicate<Player> visibility,
                       @NotNull Function<Player, Component> content);

    /** Creates or replaces a dynamic hologram anchored above an entity such as an NPC. */
    void createDynamic(@NotNull String type,
                       @NotNull String context,
                       @NotNull UUID anchorEntityUuid,
                       double verticalOffset,
                       @NotNull Predicate<Player> visibility,
                       @NotNull Function<Player, Component> content);

    /** Invalidates all rendered instances of a dynamic hologram. */
    void refreshDynamic(@NotNull String type, @NotNull String context);

    /** Invalidates one player's rendered instance of a dynamic hologram. */
    void refreshDynamic(@NotNull String type, @NotNull String context, @NotNull Player player);

    /** Invalidates every registered dynamic hologram, for example after token changes. */
    void refreshDynamic();

    /** Deletes a dynamic hologram and all of its rendered entities. */
    void deleteDynamic(@NotNull String type, @NotNull String context);
}
