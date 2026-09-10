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

import org.bukkit.NamespacedKey;
import java.util.Objects;

/** Immutable public metadata, independent of the generator implementation.
 * Incompatible terrain changes require a new version. Versions must be positive.
 */
public record GeneratorDefinition(NamespacedKey key, String displayName, String description,
                                  GeneratorCategory category, boolean experimental, int version) {
    public GeneratorDefinition {
        Objects.requireNonNull(key);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(description);
        Objects.requireNonNull(category);
        if (displayName.isBlank() || version < 1) throw new IllegalArgumentException("Invalid generator metadata");
    }
}
