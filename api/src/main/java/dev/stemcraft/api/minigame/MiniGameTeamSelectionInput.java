package dev.stemcraft.api.minigame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public enum MiniGameTeamSelectionInput {
    FLOOR,
    HOTBAR;

    public @NotNull String configToken() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable MiniGameTeamSelectionInput fromToken(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String normalized = token.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
            .filter(value -> value.name().equals(normalized))
            .findFirst()
            .orElse(null);
    }

    public static @NotNull Set<MiniGameTeamSelectionInput> orderedSet(@NotNull Iterable<MiniGameTeamSelectionInput> inputs) {
        LinkedHashSet<MiniGameTeamSelectionInput> ordered = new LinkedHashSet<>();
        for (MiniGameTeamSelectionInput input : inputs) {
            if (input != null) {
                ordered.add(input);
            }
        }
        return Set.copyOf(ordered);
    }
}
