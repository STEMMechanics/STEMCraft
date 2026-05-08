package dev.stemcraft.api.service.resourcepack;

import org.jetbrains.annotations.NotNull;

/**
 * Inclusive range of resource-pack format versions.
 */
public record PackFormatRange(int minFormat, int maxFormat) {
    private static final PackFormatRange ALL = new PackFormatRange(1, Integer.MAX_VALUE);

    public PackFormatRange {
        if (minFormat <= 0) {
            throw new IllegalArgumentException("minFormat must be positive");
        }
        if (maxFormat < minFormat) {
            throw new IllegalArgumentException("maxFormat must be greater than or equal to minFormat");
        }
    }

    public boolean contains(int format) {
        return format >= minFormat && format <= maxFormat;
    }

    public boolean intersects(@NotNull PackFormatRange other) {
        return minFormat <= other.maxFormat && maxFormat >= other.minFormat;
    }

    public @NotNull PackFormatRange intersection(@NotNull PackFormatRange other) {
        if (!intersects(other)) {
            throw new IllegalArgumentException("Ranges do not overlap");
        }
        return new PackFormatRange(
            Math.max(minFormat, other.minFormat),
            Math.min(maxFormat, other.maxFormat)
        );
    }

    public @NotNull PackFormatRange clipMin(int minValue) {
        return new PackFormatRange(Math.max(minFormat, minValue), maxFormat);
    }

    public boolean isAfter(int format) {
        return minFormat > format;
    }

    public static @NotNull PackFormatRange all() {
        return ALL;
    }
}
