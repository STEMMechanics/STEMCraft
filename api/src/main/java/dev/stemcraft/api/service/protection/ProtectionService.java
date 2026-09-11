package dev.stemcraft.api.service.protection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public interface ProtectionService {

    boolean request(@NotNull Player player, @NotNull Duration duration, @NotNull ProtectionRequest request);

    default boolean request(@NotNull Player player, @NotNull ProtectionType type, @NotNull Duration duration) {
        return request(player, duration, new ProtectionRequest(type, null, null, null));
    }

    void clear(@NotNull Player player, @NotNull ProtectionType type);

    boolean isProtected(@NotNull Player player, @NotNull ProtectionType type);

    void registerRule(@NotNull String id, @NotNull ProtectionRule rule);

    void unregisterRule(@NotNull String id);
}
