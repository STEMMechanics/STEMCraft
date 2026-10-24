package dev.stemcraft.api.service.resourcepack;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One explicit resource-pack build target.
 */
public record ResourcePackBuildTarget(
    @NotNull String minecraftVersion,
    double packFormat
) {
    public ResourcePackBuildTarget {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        if (minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("minecraftVersion must not be blank");
        }
        if (!Double.isFinite(packFormat) || packFormat <= 0) {
            throw new IllegalArgumentException("packFormat must be positive");
        }
    }
}
