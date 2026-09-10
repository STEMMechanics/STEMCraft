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

package dev.stemcraft.api.service.world.portal;

import org.bukkit.NamespacedKey;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;

/** Config-defined Survival portal access. All methods must be called on the server thread. */
public interface WorldPortalService {
    record PortalType(NamespacedKey key, NamespacedKey generatorKey, String activation) {}
    record Portal(UUID id, NamespacedKey type, UUID sourceWorld, int x, int y, int z, boolean active) {}
    Collection<PortalType> getTypes();
    Collection<Portal> getPortals();
    /** Returns the saved counterpart, even when its world is unloaded. No chunks are loaded. */
    default Optional<Portal> getLinkedPortal(UUID portalId) { return Optional.empty(); }
    /** Removes activation state; does not destroy player-built blocks. */
    boolean deactivate(UUID portalId);
}
