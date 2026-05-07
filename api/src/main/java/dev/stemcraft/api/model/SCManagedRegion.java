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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a persistent, named region definition managed by the region service.
 * <p>
 * A managed region wraps a region shape with stable identity, priority, and
 * extension storage for region data. A {@code null} region shape means
 * the managed region applies to the whole world named by {@link #getWorldName()}.
 */
@SuppressWarnings({"LombokGetterMayBeUsed", "LombokSetterMayBeUsed"})
public class SCManagedRegion implements ConfigurationSerializable {
    private String id;
    private String worldName;
    private SCRegion region;
    private int priority;
    private final Map<String, Object> data = new LinkedHashMap<>();

    /**
     * Creates a new managed region definition.
     *
     * @param id The unique managed-region identifier.
     * @param worldName The world name this region belongs to.
     * @param region The region geometry, or null for a whole-world region.
     * @param priority The priority used when resolving overlapping regions.
     */
    public SCManagedRegion(@NotNull String id,
                           @NotNull String worldName,
                           @Nullable SCRegion region,
                           int priority) {
        this.id = id;
        this.worldName = worldName;
        this.region = region;
        this.priority = priority;
        validateWorldBinding();
    }

    /**
     * Creates a new managed region definition with default priority zero.
     *
     * @param id The unique managed-region identifier.
     * @param worldName The world name this region belongs to.
     * @param region The region geometry, or null for a whole-world region.
     */
    public SCManagedRegion(@NotNull String id, @NotNull String worldName, @Nullable SCRegion region) {
        this(id, worldName, region, 0);
    }

    /**
     * Returns the unique managed-region identifier.
     *
     * @return The managed-region identifier.
     */
    public @NotNull String getId() {
        return id;
    }

    /**
     * Updates the unique managed-region identifier.
     *
     * @param id The managed-region identifier.
     */
    public void setId(@NotNull String id) {
        this.id = id;
    }

    /**
     * Returns the world name the region belongs to.
     *
     * @return The world name.
     */
    public @NotNull String getWorldName() {
        return worldName;
    }

    /**
     * Updates the world name the region belongs to.
     *
     * @param worldName The world name.
     */
    public void setWorldName(@NotNull String worldName) {
        this.worldName = worldName;
        validateWorldBinding();
    }

    /**
     * Returns the Bukkit world resolved from the stored world name.
     *
     * @return The loaded world, or null if it is not loaded.
     */
    public @Nullable World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    /**
     * Returns the region geometry, or null for a whole-world region.
     *
     * @return The region geometry.
     */
    public @Nullable SCRegion getRegion() {
        return region;
    }

    /**
     * Updates the region geometry for this managed region.
     *
     * @param region The region geometry, or null for a whole-world region.
     */
    public void setRegion(@Nullable SCRegion region) {
        this.region = region;
        validateWorldBinding();
    }

    /**
     * Returns the region priority used during overlap resolution.
     *
     * @return The region priority.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Updates the region priority used during overlap resolution.
     *
     * @param priority The region priority.
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Checks whether a named data value exists on this managed region.
     *
     * @param key The data key.
     * @return True if the data value exists, false otherwise.
     */
    public boolean hasData(@NotNull String key) {
        return data.containsKey(key);
    }

    /**
     * Returns the raw region data map for this managed region.
     *
     * @return The mutable raw region data map.
     */
    public @NotNull Map<String, Object> data() {
        return data;
    }

    /**
     * Reads a typed data value.
     *
     * @param key The data key.
     * @param type The expected value type.
     * @param defaultValue The fallback value when the data value does not exist or does not match the requested type.
     * @return The typed data value, or {@code defaultValue}.
     * @param <T> The expected data value type.
     */
    public <T> T getData(@NotNull String key, @NotNull Class<T> type, @Nullable T defaultValue) {
        return castValue(data.get(key), type, defaultValue);
    }

    /**
     * Reads a typed data value.
     *
     * @param key The data key.
     * @param type The expected value type.
     * @return The typed data value, or null.
     * @param <T> The expected data value type.
     */
    public <T> T getData(@NotNull String key, @NotNull Class<T> type) {
        return getData(key, type, null);
    }

    /**
     * Stores a data value.
     *
     * @param key The data key.
     * @param value The data value. A null value removes the data entry.
     */
    public void setData(@NotNull String key, @Nullable Object value) {
        if (value == null) {
            data.remove(key);
            return;
        }
        data.put(key, value);
    }

    /**
     * Removes a stored data value.
     *
     * @param key The data key.
     */
    public void removeData(@NotNull String key) {
        data.remove(key);
    }

    /**
     * Serializes this managed region definition for configuration storage.
     *
     * @return The serialized configuration map.
     */
    @Override
    public @NonNull Map<String, Object> serialize() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("world", worldName);
        out.put("priority", priority);
        if (region != null) {
            out.put("region", region.serialize());
        }
        out.put("data", new LinkedHashMap<>(data));
        return out;
    }

    /**
     * Deserializes a managed region definition from configuration data.
     *
     * @param map The serialized configuration map.
     * @return The deserialized managed region definition.
     */
    public static @NotNull SCManagedRegion deserialize(@NotNull Map<String, Object> map) {
        String id = MapParse.requireString(map, "id", "managedRegion");
        String worldName = MapParse.requireString(map, "world", "managedRegion");
        Integer priority = MapParse.integer(map, "priority", "managedRegion");

        SCRegion region = null;
        Object rawRegion = map.get("region");
        if (rawRegion instanceof SCRegion managedShape) {
            region = managedShape;
        } else if (rawRegion instanceof Map<?, ?>) {
            region = SCRegion.deserialize(MapParse.map(rawRegion, "managedRegion.region"));
        }

        SCManagedRegion managed = new SCManagedRegion(id, worldName, region, priority == null ? 0 : priority);

        Object rawData = map.get("data");
        if (rawData instanceof Map<?, ?>) {
            managed.data.putAll(MapParse.map(rawData, "managedRegion.data"));
        }

        return managed;
    }

    /**
     * Ensures a bound region shape belongs to the same world as the managed region.
     */
    private void validateWorldBinding() {
        if (region == null) {
            return;
        }

        World regionWorld = region.getWorld();
        if (regionWorld == null) {
            return;
        }

        if (!worldName.equals(regionWorld.getName())) {
            throw new IllegalArgumentException(
                "Managed region world '" + worldName + "' does not match region world '" + regionWorld.getName() + "'."
            );
        }
    }

    /**
     * Casts a stored extension value to the requested type.
     *
     * @param value The raw stored value.
     * @param type The requested type.
     * @param defaultValue The fallback value.
     * @return The typed value, or the fallback value.
     * @param <T> The requested type.
     */
    private static <T> T castValue(@Nullable Object value, @NotNull Class<T> type, @Nullable T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (!type.isInstance(value)) {
            return defaultValue;
        }
        return type.cast(value);
    }
}
