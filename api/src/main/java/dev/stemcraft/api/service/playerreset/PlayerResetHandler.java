package dev.stemcraft.api.service.playerreset;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface PlayerResetHandler {
    @NotNull String id();
    @NotNull Set<PlayerResetScope> scopes();
    default int priority() { return 100; }
    @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context);
    void reset(@NotNull PlayerResetContext context) throws Exception;
}
