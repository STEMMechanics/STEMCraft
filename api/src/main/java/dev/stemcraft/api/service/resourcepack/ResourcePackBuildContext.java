package dev.stemcraft.api.service.resourcepack;

import dev.stemcraft.api.config.ConfigSectionView;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Build context passed to one generator for one build target.
 */
public record ResourcePackBuildContext(
    @NotNull ResourcePackBuildTarget target,
    @NotNull ResourcePackWriter writer,
    @NotNull ConfigSectionView config
) {
    public ResourcePackBuildContext {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(config, "config");
    }
}
