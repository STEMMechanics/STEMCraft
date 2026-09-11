package dev.stemcraft.api.service.playerreset;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record PlayerResetResult(boolean successful, @NotNull List<String> completedHandlers,
                                @Nullable String failedHandler, @Nullable String error) {}
