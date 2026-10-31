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

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregates player information supplied by core services and optional features.
 */
public interface PlayerProfileService {

    /** Safe implementation returned while the profile service is unavailable. */
    PlayerProfileService UNAVAILABLE = new PlayerProfileService() {
        @Override public void register(@NotNull PlayerProfileSectionProvider provider) { }
        @Override public void unregister(@NotNull String id) { }
        @Override public @NotNull List<PlayerProfileSectionDefinition> definitions() { return List.of(); }
        @Override public @Nullable PlayerProfileSnapshot snapshot(
            @NotNull UUID playerUuid,
            @NotNull Set<String> requestedSections,
            @Nullable String period
        ) { return null; }
    };

    /** Register or replace a profile section provider. */
    void register(@NotNull PlayerProfileSectionProvider provider);

    /** Remove a previously registered section provider. */
    void unregister(@NotNull String id);

    /** Return the currently registered profile sections. */
    @NotNull List<PlayerProfileSectionDefinition> definitions();

    /**
     * Build a profile snapshot.
     *
     * <p>An empty requested-section set means all registered sections. Unknown
     * requested sections are ignored so clients can use capability discovery
     * without making older servers reject newer optional sections.</p>
     *
     * @return the snapshot, or null when the server cannot resolve the player.
     */
    @Nullable PlayerProfileSnapshot snapshot(
        @NotNull UUID playerUuid,
        @NotNull Set<String> requestedSections,
        @Nullable String period
    );
}
