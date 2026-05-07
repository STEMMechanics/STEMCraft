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
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API model for the built-in region flag extension payload.
 * <p>
 * This payload stores simple boolean allow or deny values keyed by flag name. Audience-specific
 * scoping is handled by the generic {@link RegionScopedData} wrapper rather than inside this class.
 */
public class RegionFlagData implements ConfigurationSerializable {
    public static final String KEY = "stemcraft:flags";
    public static final String COMMAND_KEY = "flag";

    private final Map<String, Boolean> flags = new LinkedHashMap<>();

    /**
     * Returns the stored flag map.
     *
     * @return The mutable stored flag map.
     */
    public @NotNull Map<String, Boolean> flags() {
        return flags;
    }

    /**
     * Returns whether the given flag key exists.
     *
     * @param key The flag key.
     * @return True if the flag exists.
     */
    public boolean hasFlag(@NotNull String key) {
        return flags.containsKey(key);
    }

    /**
     * Returns the stored boolean value of the given flag key.
     *
     * @param key The flag key.
     * @return The stored value, or null when absent.
     */
    public @Nullable Boolean getFlag(@NotNull String key) {
        return flags.get(key);
    }

    /**
     * Stores a boolean flag value.
     *
     * @param key The flag key.
     * @param value The boolean value to store.
     */
    public void setFlag(@NotNull String key, boolean value) {
        flags.put(key, value);
    }

    /**
     * Removes a boolean flag value.
     *
     * @param key The flag key.
     */
    public void removeFlag(@NotNull String key) {
        flags.remove(key);
    }

    /**
     * Returns whether this payload contains no stored flags.
     *
     * @return True if empty.
     */
    public boolean isEmpty() {
        return flags.isEmpty();
    }

    @Override
    public @NonNull Map<String, Object> serialize() {
        return new LinkedHashMap<>(flags);
    }

    /**
     * Deserializes flag data from configuration data.
     *
     * @param map The serialized configuration map.
     * @return The deserialized flag data.
     */
    public static @NotNull RegionFlagData deserialize(@NotNull Map<String, Object> map) {
        RegionFlagData data = new RegionFlagData();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object rawValue = entry.getValue();
            if (rawValue instanceof Boolean bool) {
                data.flags.put(entry.getKey(), bool);
                continue;
            }

            String value = MapParse.string(map, entry.getKey(), "regionFlags");
            if (value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
                data.flags.put(entry.getKey(), Boolean.parseBoolean(value));
            }
        }
        return data;
    }
}
