package dev.stemcraft.api.service.playerreset;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface PlayerResetService {
    void register(@NotNull PlayerResetHandler handler);
    void unregister(@NotNull String handlerId);
    @NotNull List<PlayerResetHandler> handlers();
    @NotNull PlayerResetPlan plan(@NotNull UUID uuid, @NotNull String playerName,
                                  @NotNull PlayerResetScope scope, @NotNull String actorName);
    @Nullable PlayerResetPlan getPlan(@NotNull String token);
    void confirm(@NotNull String token);
}
