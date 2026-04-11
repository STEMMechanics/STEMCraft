package dev.stemcraft.service.placeholderapi;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

public interface PlaceholderApiSupport {
    void enable();

    void disable();

    String apply(@Nullable OfflinePlayer player, String text);
}
