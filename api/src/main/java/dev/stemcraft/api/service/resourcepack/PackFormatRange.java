package dev.stemcraft.api.service.resourcepack;

import org.jetbrains.annotations.NotNull;

/**
 * Inclusive range of resource-pack format versions.
 *
 * <p>Format versions are represented as decimals because newer Minecraft
 * resource-pack formats can contain a fractional component, such as 97.1.</p>
 */
public record PackFormatRange(double minFormat, double maxFormat) {
    private static final PackFormatRange ALL = new PackFormatRange(1, Double.POSITIVE_INFINITY);

    public PackFormatRange {
        if (!Double.isFinite(minFormat) || minFormat <= 0) {
            throw new IllegalArgumentException("minFormat must be positive");
        }
        if (Double.isNaN(maxFormat) || maxFormat < minFormat) {
            throw new IllegalArgumentException("maxFormat must be greater than or equal to minFormat");
        }
    }

    public boolean contains(double format) {
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

    public @NotNull PackFormatRange clipMin(double minValue) {
        return new PackFormatRange(Math.max(minFormat, minValue), maxFormat);
    }

    public boolean isAfter(double format) {
        return minFormat > format;
    }

    public static @NotNull PackFormatRange all() {
        return ALL;
    }
}
