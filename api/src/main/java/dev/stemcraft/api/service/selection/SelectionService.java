package dev.stemcraft.api.service.selection;

import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service for previewing WorldEdit selections and highlighting regions/locations.
 */
public interface SelectionService {

    /**
     * Gets the player's complete WorldEdit selection.
     */
    @Nullable SCRegion getWorldEditSelection(@NotNull Player player);

    /**
     * Gets the player's WorldEdit selection for preview, including incomplete selectors.
     */
    @Nullable SCRegion getWorldEditPreviewSelection(@NotNull Player player);

    /**
     * Gets the player's WorldEdit primary position, if set.
     */
    @Nullable Location getWorldEditPrimaryPosition(@NotNull Player player);

    /**
     * Sets the player's WorldEdit selection from a stored region.
     */
    void setWorldEditSelection(@NotNull Player player, @NotNull SCRegion region);

    /**
     * Sets the player's WorldEdit selection to a single block at the given location.
     */
    void setWorldEditSelection(@NotNull Player player, @NotNull Location location);

    /**
     * Clears the player's active WorldEdit selection.
     */
    void clearWorldEditSelection(@NotNull Player player);

    /**
     * Renders a region preview once to a specific player.
     */
    void showRegion(@NotNull Player viewer, @NotNull SCRegion region);

    /**
     * Renders a region preview once to players in a world.
     */
    void showRegion(@NotNull World world, @NotNull SCRegion region);

    /**
     * Renders a location preview once to a specific player.
     */
    void showLocation(@NotNull Player viewer, @NotNull Location location);

    /**
     * Renders a location preview once to players in a world.
     */
    void showLocation(@NotNull World world, @NotNull Location location);

    /**
     * Keeps a region highlighted for a player until cleared or expired.
     */
    void highlightRegion(@NotNull String id, @NotNull Player viewer, @NotNull SCRegion region, long durationTicks);
    default void highlightRegion(@NotNull String id, @NotNull Player viewer, @NotNull SCRegion region) {
        highlightRegion(id, viewer, region, -1L);
    }

    /**
     * Keeps a region highlighted for all players in a world until cleared or expired.
     */
    void highlightRegion(@NotNull String id, @NotNull World world, @NotNull SCRegion region, long durationTicks);
    default void highlightRegion(@NotNull String id, @NotNull World world, @NotNull SCRegion region) {
        highlightRegion(id, world, region, -1L);
    }

    /**
     * Keeps a location highlighted for a player until cleared or expired.
     */
    void highlightLocation(@NotNull String id, @NotNull Player viewer, @NotNull Location location, long durationTicks);
    default void highlightLocation(@NotNull String id, @NotNull Player viewer, @NotNull Location location) {
        highlightLocation(id, viewer, location, -1L);
    }

    /**
     * Keeps a location highlighted for all players in a world until cleared or expired.
     */
    void highlightLocation(@NotNull String id, @NotNull World world, @NotNull Location location, long durationTicks);
    default void highlightLocation(@NotNull String id, @NotNull World world, @NotNull Location location) {
        highlightLocation(id, world, location, -1L);
    }

    /**
     * Flashes a block for one player using client-side block changes.
     */
    void flashBlock(@NotNull String id, @NotNull Player viewer, @NotNull Location location, long durationTicks);
    default void flashBlock(@NotNull String id, @NotNull Player viewer, @NotNull Location location) {
        flashBlock(id, viewer, location, -1L);
    }

    /**
     * Clears a previously registered highlight.
     */
    void clearHighlight(@NotNull String id);

    /**
     * Clears all highlights whose ids start with the given prefix.
     */
    void clearHighlights(@NotNull String prefix);
}
