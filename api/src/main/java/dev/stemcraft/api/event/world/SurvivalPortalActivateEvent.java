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

package dev.stemcraft.api.event.world;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired before consuming an activation item or recording a charge. Player is null for weather lightning and automatic matching-exit preparation.
 * Protection plugins can cancel this event. Location access returns a defensive copy.
 */
public final class SurvivalPortalActivateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final NamespacedKey type;
    private final Location origin;
    private final Player player;
    private boolean cancelled;
    public SurvivalPortalActivateEvent(NamespacedKey type, Location origin, @Nullable Player player) {
        this.type = type; this.origin = origin.clone(); this.player = player;
    }
    public NamespacedKey getPortalType() { return type; }
    public Location getOrigin() { return origin.clone(); }
    public @Nullable Player getPlayer() { return player; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
