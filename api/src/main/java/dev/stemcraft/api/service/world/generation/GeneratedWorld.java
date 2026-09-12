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

package dev.stemcraft.api.service.world.generation;

import java.util.UUID;
import java.util.Objects;

/**
 * Snapshot of the generator attached to a loaded world, not a pending configuration change.
 * @param worldId UUID of the currently loaded world
 * @param generator definition attached to its generator instance
 * @param seed authoritative world seed
 */
public record GeneratedWorld(UUID worldId, GeneratorDefinition generator, long seed) {
    public GeneratedWorld {
        Objects.requireNonNull(worldId);
        Objects.requireNonNull(generator);
    }
}
