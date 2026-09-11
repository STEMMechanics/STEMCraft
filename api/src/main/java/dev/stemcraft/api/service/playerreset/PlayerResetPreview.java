package dev.stemcraft.api.service.playerreset;

import org.jetbrains.annotations.NotNull;

public record PlayerResetPreview(@NotNull String description, int records) {
    public PlayerResetPreview {
        if (records < 0) throw new IllegalArgumentException("records cannot be negative");
    }
}
