package dev.stemcraft.api.service.world.generation;

/**
 * Optional generator-owned map policy, independent of Pl3xMap classes.
 * Implementations must be thread-safe and must only read the supplied snapshot column:
 * rendering runs off the server thread and must not load chunks or access live Bukkit worlds.
 */
public interface GeneratorMapRenderer {
    /** @return the display name for the generated world's basic map. */
    String displayName();

    /**
     * Select a surface from the saved column.
     * @param column read-only block data with inclusive minimum and exclusive maximum heights
     * @return surface Y, or a value below {@link Column#minY()} for a transparent pixel
     */
    int surfaceY(Column column);

    /**
     * Optionally customize the biome-corrected surface color.
     * @param column read-only source column
     * @param y selected surface height
     * @param argb default opaque surface color (water uses the biome's water color)
     * @return final ARGB pixel; zero is transparent
     */
    default int color(Column column, int y, int argb) { return argb; }

    /** Snapshot access scoped to one column; do not retain this object after rendering. */
    interface Column {
        /** @return inclusive world minimum Y. */
        int minY();
        /** @return exclusive world maximum Y. */
        int maxY();
        /** @param y in-range height @return whether this block is air. */
        boolean isAir(int y);
        /** @param y in-range height @return whether this block has a visible map color or fluid. */
        boolean isVisible(int y);
    }
}
