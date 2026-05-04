package dev.stemcraft.api.service.resourcepack;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Build context for one resource-pack output segment.
 */
public record ResourcePackBuildContext(
    @NotNull PackFormatRange supportedRange,
    int targetFormat,
    boolean overlay,
    @Nullable String overlayDirectory
) {
    public ResourcePackBuildContext {
        if (targetFormat <= 0) {
            throw new IllegalArgumentException("targetFormat must be positive");
        }
        if (!supportedRange.contains(targetFormat)) {
            throw new IllegalArgumentException("supportedRange must include targetFormat");
        }
        if (overlay && (overlayDirectory == null || overlayDirectory.isBlank())) {
            throw new IllegalArgumentException("overlayDirectory is required for overlay contexts");
        }
        if (!overlay) {
            overlayDirectory = null;
        }
    }
}
