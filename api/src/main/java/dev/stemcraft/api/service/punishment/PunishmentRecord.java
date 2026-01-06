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

package dev.stemcraft.api.service.punishment;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a record of a punishment issued to a player.
 */
@Getter
@Accessors(fluent = true)
public final class PunishmentRecord {
    private final long id;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID actorUuid;
    private final String actorName;
    private final String type;
    private boolean alerted;
    private final String reason;
    private final Instant createdAt;
    private final Long durationSeconds; // null = permanent, 0 is a one-off, -1 is cancelled

    /**
     * Constructor
     *
     * @param id The unique ID of the punishment record.
     * @param targetUuid The UUID of the player being punished.
     * @param targetName The name of the player being punished.
     * @param actorUuid The UUID of the actor issuing the punishment.
     * @param actorName The name of the actor issuing the punishment.
     * @param type The type of punishment.
     * @param alerted Whether the punishment has been alerted.
     * @param reason The reason for the punishment.
     * @param createdAt The timestamp when the punishment was created.
     * @param durationSeconds The duration of the punishment in seconds (null for permanent).
     */
    public PunishmentRecord(long id,
                            UUID targetUuid,
                            String targetName,
                            UUID actorUuid,
                            String actorName,
                            String type,
                            boolean alerted,
                            String reason,
                            Instant createdAt,
                            Long durationSeconds) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.actorUuid = actorUuid;
        this.actorName = actorName;
        this.type = type;
        this.alerted = alerted;
        this.reason = reason;
        this.createdAt = createdAt;
        this.durationSeconds = durationSeconds;
    }

    /**
     * Marks this punishment as alerted
     */
    public void setAlerted() {
        alerted = true;
    }

    /**
     * Checks if this punishment is permanent
     *
     * @return true if the punishment is permanent, false otherwise
     */
    public boolean permanent() {
        return durationSeconds == null;
    }

    /**
     * Checks if this punishment is cancelled
     *
     * @return true if the punishment is cancelled, false otherwise
     */
    public boolean cancelled() {
        return durationSeconds == -1;
    }

    /**
     * Gets the expiration time of this punishment
     *
     * @return The expiration time as an Instant, or null if permanent
     */
    public Instant expiresAt() {
        if (permanent()) return null;
        return createdAt.plusSeconds(durationSeconds);
    }

    /**
     * Gets the Player object of the target if they are online
     *
     * @return The Player object if online, null otherwise
     */
    public Player getPlayerIfOnline() {
        Player player = Bukkit.getPlayer(targetUuid);
        if(player != null && player.isOnline()) {
            return player;
        }

        return null;
    }
}