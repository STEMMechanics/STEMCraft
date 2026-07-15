package dev.stemcraft.api.service.profanity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ProfanitySeverity {
    MILD(25),
    MODERATE(50),
    HIGH(75),
    EXTREME(100);

    private final int score;

    ProfanitySeverity(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }

    public boolean meetsOrExceeds(@Nullable ProfanitySeverity minimum) {
        return minimum == null || ordinal() >= minimum.ordinal();
    }

    public static @NotNull ProfanitySeverity fromString(@Nullable String value, @NotNull ProfanitySeverity fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        for (ProfanitySeverity severity : values()) {
            if (severity.name().equalsIgnoreCase(value.trim().replace('-', '_'))) {
                return severity;
            }
        }
        return fallback;
    }
}
