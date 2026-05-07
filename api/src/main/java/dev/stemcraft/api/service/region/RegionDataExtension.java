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

import dev.stemcraft.api.model.RegionMemberData;
import dev.stemcraft.api.model.RegionScopedData;
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.util.PermissionUtil;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a typed extension stored in the managed-region data map.
 *
 * @param <T> The extension payload type.
 */
public interface RegionDataExtension<T> extends RegionExtension<T> {

    /**
     * Reads the extension payload from the given managed region.
     *
     * @param region The managed region to read from.
     * @return The extension payload, or null when absent.
     */
    default @Nullable T get(@NotNull SCManagedRegion region) {
        return region.getData(key(), type());
    }

    /**
     * Reads the extension payload for a specific audience scope.
     *
     * @param region The managed region to read from.
     * @param scope The audience scope.
     * @return The extension payload, or null when absent.
     */
    default @Nullable T get(@NotNull SCManagedRegion region, @Nullable String scope) {
        if (scope == null) {
            Object raw = region.data().get(key());
            if (type().isInstance(raw)) {
                return type().cast(raw);
            }
            if (raw instanceof RegionScopedData scoped) {
                Object scopedValue = scoped.get(RegionScopedData.ALL);
                return type().isInstance(scopedValue) ? type().cast(scopedValue) : null;
            }
            return null;
        }

        Object raw = region.data().get(key());
        if (raw instanceof RegionScopedData scoped) {
            Object scopedValue = scoped.get(scope);
            return type().isInstance(scopedValue) ? type().cast(scopedValue) : null;
        }
        if (RegionScopedData.ALL.equals(scope) && type().isInstance(raw)) {
            return type().cast(raw);
        }
        return null;
    }

    /**
     * Reads the extension payload resolved for a specific player.
     * <p>
     * Resolution order is:
     * <ol>
     *     <li>{@code members} or {@code nonmembers}</li>
     *     <li>{@code all}</li>
     * </ol>
     *
     * @param region The managed region to read from.
     * @param player The player to resolve for.
     * @return The resolved extension payload, or null when absent.
     */
    default @Nullable T get(@NotNull SCManagedRegion region, @NotNull Player player) {
        Object raw = region.data().get(key());
        if (!(raw instanceof RegionScopedData scoped)) {
            return get(region);
        }

        boolean isMember = isMember(region, player);
        String memberScope = isMember ? RegionScopedData.MEMBERS : RegionScopedData.NONMEMBERS;
        Object memberValue = scoped.get(memberScope);
        if (type().isInstance(memberValue)) {
            return type().cast(memberValue);
        }

        Object allValue = scoped.get(RegionScopedData.ALL);
        return type().isInstance(allValue) ? type().cast(allValue) : null;
    }

    /**
     * Returns every stored scoped value for this extension.
     * <p>
     * Unscoped values are exposed as the {@code all} scope.
     *
     * @param region The managed region to read from.
     * @return The scope-to-payload map.
     */
    default @NotNull Map<String, T> getAll(@NotNull SCManagedRegion region) {
        Map<String, T> out = new LinkedHashMap<>();
        Object raw = region.data().get(key());
        if (type().isInstance(raw)) {
            out.put(RegionScopedData.ALL, type().cast(raw));
            return out;
        }
        if (!(raw instanceof RegionScopedData scoped)) {
            return out;
        }

        for (Map.Entry<String, Object> entry : scoped.scopes().entrySet()) {
            if (type().isInstance(entry.getValue())) {
                out.put(entry.getKey(), type().cast(entry.getValue()));
            }
        }
        return out;
    }

    /**
     * Stores the extension payload on the given managed region.
     *
     * @param region The managed region to update.
     * @param value The extension payload. A null value removes the entry.
     */
    default void set(@NotNull SCManagedRegion region, @Nullable T value) {
        region.setData(key(), value);
    }

    /**
     * Stores the extension payload on the given managed region for one audience scope.
     *
     * @param region The managed region to update.
     * @param scope The audience scope.
     * @param value The extension payload. A null value removes the scoped entry.
     */
    default void set(@NotNull SCManagedRegion region, @Nullable String scope, @Nullable T value) {
        if (scope == null) {
            set(region, value);
            return;
        }

        Object raw = region.data().get(key());
        RegionScopedData scopedData;
        if (raw instanceof RegionScopedData scoped) {
            scopedData = scoped;
        } else {
            scopedData = new RegionScopedData();
            if (type().isInstance(raw)) {
                scopedData.set(RegionScopedData.ALL, raw);
            }
        }

        scopedData.set(scope, value);
        if (scopedData.isEmpty()) {
            region.removeData(key());
        } else {
            region.setData(key(), scopedData);
        }
    }

    /**
     * Removes the extension payload from a managed region.
     *
     * @param region The managed region to update.
     * @param scope The optional audience scope.
     */
    default void remove(@NotNull SCManagedRegion region, @Nullable String scope) {
        if (scope == null) {
            region.removeData(key());
            return;
        }
        set(region, scope, null);
    }

    private boolean isMember(@NotNull SCManagedRegion region, @NotNull Player player) {
        RegionMemberData memberData = region.getData(RegionMemberData.KEY, RegionMemberData.class);
        if (memberData == null || memberData.isEmpty()) {
            return false;
        }

        if (memberData.hasPlayer(player.getUniqueId())) {
            return true;
        }

        for (String group : memberData.groups()) {
            if (PermissionUtil.isInGroup(player.getUniqueId(), group)) {
                return true;
            }
        }

        return false;
    }
}
