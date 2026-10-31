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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable aggregate of player identity and registered profile sections. */
public record PlayerProfileSnapshot(
    @NotNull UUID uuid,
    @NotNull String username,
    @NotNull String platform,
    boolean online,
    @NotNull Instant generatedAt,
    @NotNull List<PlayerProfileSection> sections
) {
    public PlayerProfileSnapshot {
        sections = List.copyOf(sections);
    }

    @Override
    public @NotNull List<PlayerProfileSection> sections() {
        return List.copyOf(sections);
    }
}
