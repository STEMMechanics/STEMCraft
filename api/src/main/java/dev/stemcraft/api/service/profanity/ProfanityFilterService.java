package dev.stemcraft.api.service.profanity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProfanityFilterService {
    boolean isEnabled();

    @NotNull ProfanityFilterResult check(@Nullable String text);

    @NotNull ProfanityFilterResult check(@Nullable String text, @Nullable ProfanitySeverity minimumSeverity);
}
