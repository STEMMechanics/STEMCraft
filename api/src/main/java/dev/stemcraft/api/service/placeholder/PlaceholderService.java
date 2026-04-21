package dev.stemcraft.api.service.placeholder;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PlaceholderService {
    boolean isAvailable();

    @Nullable String apply(@Nullable OfflinePlayer player, @Nullable String text);

    default @Nullable String apply(@Nullable String text) {
        return apply(null, text);
    }

    @NotNull List<String> apply(@Nullable OfflinePlayer player, @NotNull List<String> lines);

    default @NotNull List<String> apply(@NotNull List<String> lines) {
        return apply(null, lines);
    }
}
