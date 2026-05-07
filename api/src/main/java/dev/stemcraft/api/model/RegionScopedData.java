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
 * Wrapper for extension payloads that are scoped to a target audience.
 * <p>
 * Supported built-in scopes are:
 * <ul>
 *     <li>{@code all}</li>
 *     <li>{@code members}</li>
 *     <li>{@code nonmembers}</li>
 * </ul>
 */
public class RegionScopedData implements ConfigurationSerializable {
    public static final String ALL = "all";
    public static final String MEMBERS = "members";
    public static final String NONMEMBERS = "nonmembers";

    private final Map<String, Object> scopes = new LinkedHashMap<>();

    /**
     * Returns the stored scope map.
     *
     * @return The mutable scope map.
     */
    public @NotNull Map<String, Object> scopes() {
        return scopes;
    }

    /**
     * Returns whether the given scope exists.
     *
     * @param scope The scope key.
     * @return True if the scope exists.
     */
    public boolean has(@NotNull String scope) {
        return scopes.containsKey(scope);
    }

    /**
     * Returns the stored value for a scope.
     *
     * @param scope The scope key.
     * @return The stored value, or null when absent.
     */
    public @Nullable Object get(@NotNull String scope) {
        return scopes.get(scope);
    }

    /**
     * Stores a value for a scope.
     *
     * @param scope The scope key.
     * @param value The value to store.
     */
    public void set(@NotNull String scope, @Nullable Object value) {
        if (value == null) {
            scopes.remove(scope);
            return;
        }
        scopes.put(scope, value);
    }

    /**
     * Removes a stored scope.
     *
     * @param scope The scope key.
     */
    public void remove(@NotNull String scope) {
        scopes.remove(scope);
    }

    /**
     * Returns whether the wrapper contains no scoped values.
     *
     * @return True if empty.
     */
    public boolean isEmpty() {
        return scopes.isEmpty();
    }

    /**
     * Serializes this scope wrapper for configuration storage.
     *
     * @return The serialized configuration map.
     */
    @Override
    public @NonNull Map<String, Object> serialize() {
        return Map.of("scopes", new LinkedHashMap<>(scopes));
    }

    /**
     * Deserializes scoped data from configuration data.
     *
     * @param map The serialized configuration map.
     * @return The deserialized scoped data.
     */
    public static @NotNull RegionScopedData deserialize(@NotNull Map<String, Object> map) {
        RegionScopedData data = new RegionScopedData();
        Object rawScopes = map.get("scopes");
        if (rawScopes instanceof Map<?, ?>) {
            data.scopes.putAll(MapParse.map(rawScopes, "regionScopedData.scopes"));
        }
        return data;
    }

    /**
     * Returns whether a raw value looks like serialized scoped data.
     *
     * @param raw The raw candidate value.
     * @return True if the raw value looks like serialized scoped data.
     */
    public static boolean isSerializedScopedData(@Nullable Object raw) {
        return raw instanceof Map<?, ?> rawMap && rawMap.containsKey("scopes");
    }
}
