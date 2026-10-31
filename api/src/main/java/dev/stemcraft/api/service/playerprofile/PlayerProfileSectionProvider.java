/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

package dev.stemcraft.api.service.playerprofile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Supplies one section of the aggregate player profile.
 *
 * <p>Providers are domain-owned. The web service consumes the aggregate but
 * does not need to know which feature supplied a section.</p>
 */
public interface PlayerProfileSectionProvider {

    /** Stable section identifier used by API clients and include filters. */
    @NotNull String id();

    /**
     * Build the section data for a player.
     *
     * @param playerUuid The player UUID.
     * @param period The requested statistics period, or {@code null} for all time.
     * @return JSON-compatible section data.
     */
    @NotNull Map<String, Object> snapshot(@NotNull UUID playerUuid, @Nullable String period);

    /** Player-facing section title. */
    default @NotNull String title() {
        return id();
    }

    /** Whether this provider is currently available. */
    default boolean isAvailable() {
        return true;
    }
}
