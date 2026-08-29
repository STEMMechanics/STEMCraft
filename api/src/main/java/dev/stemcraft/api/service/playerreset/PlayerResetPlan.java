package dev.stemcraft.api.service.playerreset;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlayerResetPlan(@NotNull String token, @NotNull UUID playerUuid, @NotNull String playerName,
                              @NotNull PlayerResetScope scope, @NotNull String actorName,
                              @NotNull Instant expiresAt, @NotNull List<Entry> entries) {
    public record Entry(@NotNull String handlerId, @NotNull PlayerResetPreview preview) {}
}
