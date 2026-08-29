package dev.stemcraft.api.service.playerreset;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PlayerResetContext(@NotNull UUID playerUuid, @NotNull String playerName,
                                 @NotNull PlayerResetScope scope, @NotNull String actorName) {}
