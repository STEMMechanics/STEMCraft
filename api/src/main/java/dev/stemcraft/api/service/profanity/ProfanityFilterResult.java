package dev.stemcraft.api.service.profanity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ProfanityFilterResult(
    boolean offensive,
    @NotNull String originalText,
    @NotNull String cleanedText,
    int score,
    @Nullable ProfanitySeverity severity,
    @NotNull List<String> matchedWords
) {
    public ProfanityFilterResult {
        matchedWords = List.copyOf(matchedWords);
    }

    public boolean isClean() {
        return !offensive;
    }

    public int count() {
        return matchedWords.size();
    }

    public static @NotNull ProfanityFilterResult clean(@Nullable String text) {
        String value = text == null ? "" : text;
        return new ProfanityFilterResult(false, value, value, 0, null, List.of());
    }
}
