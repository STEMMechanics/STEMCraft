package dev.stemcraft.api.service.protection;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ProtectionRequest(
    @NotNull ProtectionType type,
    @Nullable String source,
    @Nullable Location from,
    @Nullable Location to
) {
}
