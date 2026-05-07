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

package dev.stemcraft.api.model;

import dev.stemcraft.api.util.MapParse;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API model for the built-in region membership extension payload.
 * <p>
 * Members may be explicit players or named groups. Group names are resolved by the plugin at
 * runtime. Player members are stored by UUID.
 */
public class RegionMemberData implements ConfigurationSerializable {
    public static final String KEY = "stemcraft:members";
    public static final String COMMAND_KEY = "member";

    private final Set<String> playerIds = new LinkedHashSet<>();
    private final Set<String> groups = new LinkedHashSet<>();

    /**
     * Returns the stored member player UUID strings.
     *
     * @return The mutable player UUID set.
     */
    public @NotNull Set<String> playerIds() {
        return playerIds;
    }

    /**
     * Returns the stored member group names.
     *
     * @return The mutable group name set.
     */
    public @NotNull Set<String> groups() {
        return groups;
    }

    /**
     * Adds a player member.
     *
     * @param playerId The player UUID.
     */
    public void addPlayer(@NotNull UUID playerId) {
        playerIds.add(playerId.toString());
    }

    /**
     * Removes a player member.
     *
     * @param playerId The player UUID.
     */
    public void removePlayer(@NotNull UUID playerId) {
        playerIds.remove(playerId.toString());
    }

    /**
     * Returns whether the player UUID is stored as a member.
     *
     * @param playerId The player UUID.
     * @return True if the player is stored as a member.
     */
    public boolean hasPlayer(@NotNull UUID playerId) {
        return playerIds.contains(playerId.toString());
    }

    /**
     * Adds a group member entry.
     *
     * @param group The group name.
     */
    public void addGroup(@NotNull String group) {
        groups.add(group);
    }

    /**
     * Removes a group member entry.
     *
     * @param group The group name.
     */
    public void removeGroup(@NotNull String group) {
        groups.remove(group);
    }

    /**
     * Returns whether the group is stored as a member entry.
     *
     * @param group The group name.
     * @return True if the group is stored as a member.
     */
    public boolean hasGroup(@NotNull String group) {
        return groups.contains(group);
    }

    /**
     * Returns whether this payload contains no members.
     *
     * @return True if the payload is empty.
     */
    public boolean isEmpty() {
        return playerIds.isEmpty() && groups.isEmpty();
    }

    /**
     * Serializes this member data object for configuration storage.
     *
     * @return The serialized configuration map.
     */
    @Override
    public @NonNull Map<String, Object> serialize() {
        return Map.of(
            "players", new ArrayList<>(playerIds),
            "groups", new ArrayList<>(groups)
        );
    }

    /**
     * Deserializes member data from configuration data.
     *
     * @param map The serialized configuration map.
     * @return The deserialized member data.
     */
    public static @NotNull RegionMemberData deserialize(@NotNull Map<String, Object> map) {
        RegionMemberData data = new RegionMemberData();
        data.playerIds.addAll(readStringList(map, "players"));
        data.groups.addAll(readStringList(map, "groups"));
        return data;
    }

    private static @NotNull List<String> readStringList(@NotNull Map<String, Object> map,
                                                         @NotNull String key) {
        List<String> out = new ArrayList<>();
        for (Object value : MapParse.list(map.get(key), "regionMembers." + key)) {
            if (value instanceof String stringValue) {
                out.add(stringValue);
            }
        }
        return out;
    }
}
