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

package dev.stemcraft.api.service.region;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.model.SCManagedRegion;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes a typed extension slot that can be registered against managed regions.
 *
 * @param <T> The extension payload type.
 */
public interface RegionExtension<T> {

    /**
     * Returns the namespaced extension key used to store and retrieve values.
     *
     * @return The namespaced extension key.
     */
    @NotNull String key();

    /**
     * Returns the payload type expected by this extension.
     *
     * @return The payload type.
     */
    @NotNull Class<T> type();

    /**
     * Returns a short human-readable description of the extension.
     *
     * @return The extension description.
     */
    @NotNull String description();

    /**
     * Called when the extension is enabled by the region service.
     *
     * @param api The STEMCraft API instance.
     * @param service The region service instance.
     */
    default void onEnable(@NotNull STEMCraftAPI api, @NotNull RegionService service) { }

    /**
     * Called when the extension is disabled by the region service.
     */
    default void onDisable() { }

    /**
     * Returns the optional `/region` subcommand label for this extension.
     *
     * @return The region subcommand label, or null when this extension does not expose a command.
     */
    default @Nullable String commandKey() { return null; }

    /**
     * Serializes a typed extension payload for managed-region persistence.
     *
     * @param value The typed extension payload.
     * @return The serialized value written into the managed-region YAML data map.
     */
    default @Nullable Object serializeValue(@Nullable T value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ConfigurationSerializable serializable) {
            //noinspection OverrideOnly
            return serializable.serialize();
        }
        return value;
    }

    /**
     * Deserializes a raw stored value back into the extension's typed payload.
     *
     * @param raw The raw stored value from managed-region YAML data.
     * @return The typed extension payload, or null when the value cannot be deserialized.
     */
    @SuppressWarnings("unchecked")
    default @Nullable T deserializeValue(@Nullable Object raw) {
        if (raw == null) {
            return null;
        }
        if (type().isInstance(raw)) {
            return type().cast(raw);
        }
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                map.put(key, entry.getValue());
            }
        }

        try {
            Method method = type().getMethod("deserialize", Map.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }

            Object result = method.invoke(null, map);
            if (type().isInstance(result)) {
                return (T) result;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }

        return null;
    }

    /**
     * Returns the tab completion suffixes for this extension's `/region set` command.
     *
     * @return The `set` command tab completions.
     */
    default @NotNull List<String[]> setTabCompletions() { return List.of(); }

    /**
     * Returns the tab completion suffixes for this extension's `/region get` command.
     *
     * @return The `get` command tab completions.
     */
    default @NotNull List<String[]> getTabCompletions() { return List.of(); }

    /**
     * Returns the tab completion suffixes for this extension's `/region clear` command.
     *
     * @return The `clear` command tab completions.
     */
    default @NotNull List<String[]> clearTabCompletions() { return List.of(); }

    /**
     * Returns human-readable lines describing this extension's current data for a managed region.
     *
     * @param region The managed region being described.
     * @return Human-readable summary lines, or an empty list when this extension has nothing to show.
     */
    default @NotNull List<String> describe(@NotNull SCManagedRegion region) { return List.of(); }

    /**
     * Handles this extension's `/region set <id> <extension-id> ... [g:<scope>]` command.
     *
     * @param ctx The command context with the root action, region id, extension id, and optional
     *            scope arguments removed.
     * @param region The managed region being edited.
     * @param scope The optional audience scope.
     */
    default void onSet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        onSet(ctx, region);
    }

    /**
     * Handles this extension's `/region set <id> <extension-id> ...` command.
     *
     * @param ctx The command context with the root action, region id, and extension id removed.
     * @param region The managed region being edited.
     */
    default void onSet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region) {
        throw new UnsupportedOperationException("Region extension '" + key() + "' does not support set.");
    }

    /**
     * Handles this extension's `/region get <id> <extension-id> ... [g:<scope>]` command.
     *
     * @param ctx The command context with the root action, region id, extension id, and optional
     *            scope arguments removed.
     * @param region The managed region being read.
     * @param scope The optional audience scope.
     */
    default void onGet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        onGet(ctx, region);
    }

    /**
     * Handles this extension's `/region get <id> <extension-id> ...` command.
     *
     * @param ctx The command context with the root action, region id, and extension id removed.
     * @param region The managed region being read.
     */
    default void onGet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region) {
        throw new UnsupportedOperationException("Region extension '" + key() + "' does not support get.");
    }

    /**
     * Handles this extension's `/region clear <id> <extension-id> ... [g:<scope>]` command.
     *
     * @param ctx The command context with the root action, region id, extension id, and optional
     *            scope arguments removed.
     * @param region The managed region being edited.
     * @param scope The optional audience scope.
     */
    default void onClear(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        onClear(ctx, region);
    }

    /**
     * Handles this extension's `/region clear <id> <extension-id> ...` command.
     *
     * @param ctx The command context with the root action, region id, and extension id removed.
     * @param region The managed region being edited.
     */
    default void onClear(@NotNull CommandContext ctx, @NotNull SCManagedRegion region) {
        throw new UnsupportedOperationException("Region extension '" + key() + "' does not support clear.");
    }
}
