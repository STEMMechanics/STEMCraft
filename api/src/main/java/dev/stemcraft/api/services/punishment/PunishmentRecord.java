package dev.stemcraft.api.services.punishment;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

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

    public long id() {
        return id;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public UUID actorUuid() {
        return actorUuid;
    }

    public String actorName() {
        return actorName;
    }

    public String type() {
        return type;
    }

    public boolean alerted() {
        return alerted;
    }

    public void setAlerted() {
        alerted = true;
    }

    public String reason() {
        return reason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long durationSeconds() {
        return durationSeconds;
    }

    public boolean permanent() {
        return durationSeconds == null;
    }

    public boolean cancelled() {
        return durationSeconds == -1;
    }

    public Instant expiresAt() {
        if (permanent()) return null;
        return createdAt.plusSeconds(durationSeconds);
    }

    public Player getPlayerIfOnline() {
        Player player = Bukkit.getPlayer(targetUuid);
        if(player != null && player.isOnline()) {
            return player;
        }

        return null;
    }
}