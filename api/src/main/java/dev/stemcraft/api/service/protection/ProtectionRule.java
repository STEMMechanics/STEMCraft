package dev.stemcraft.api.service.protection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ProtectionRule {
    boolean shouldApply(@NotNull Player player, @NotNull ProtectionRequest request);
}
