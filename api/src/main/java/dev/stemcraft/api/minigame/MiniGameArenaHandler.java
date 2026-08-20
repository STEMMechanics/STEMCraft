package dev.stemcraft.api.minigame;

import dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Handler interface for mini-game arena events and actions.
 */
public interface MiniGameArenaHandler {

    /**
     * Result of a handler event, indicating whether to allow or deny the action.
     */
    enum HandlerEventResult {
        ALLOW,           // Allow the action to proceed
        ALLOW_NO_DROPS,  // Allow the action but suppress default block drops
        DENY             // Deny the action
    }

    /**
     * Validate the arena configuration.
     *
     * @param arena  The mini-game arena to validate.
     * @param result The result object to record validation issues.
     */
    default void validate(@NotNull MiniGameArena arena, ArenaValidationResult result) {
    }

    /**
     * Called when an arena is loaded.
     *
     * @param arena The mini-game arena that was loaded.
     */
    default void onArenaLoad(MiniGameArena arena) {
    }

    /**
     * Called when an arena is unloaded.
     *
     * @param arena The mini-game arena that was unloaded.
     */
    default void onArenaUnload(MiniGameArena arena) {
    }

    /**
     * Called when the arena status changes.
     *
     * @param arena     The mini-game arena whose status changed.
     * @param oldStatus The old status of the arena.
     * @param newStatus The new status of the arena.
     */
    default void onArenaStatusChanged(MiniGameArena arena, ArenaStatus oldStatus, ArenaStatus newStatus) {
    }

    /**
     * Called every second while an arena countdown is active, after the countdown is decremented.
     *
     * @param arena            The mini-game arena whose countdown ticked.
     * @param secondsRemaining The remaining countdown time in seconds after the tick.
     */
    default void onArenaCountdownTick(MiniGameArena arena, int secondsRemaining) {
    }

    /**
     * Called when the arena countdown ends.
     *
     * @param arena The mini-game arena whose countdown ended.
     */
    default void onArenaCountdownEnd(MiniGameArena arena) {
    }

    /**
     * Called when a player joins the arena.
     *
     * @param arena  The mini-game arena the player joined.
     * @param player The player who joined the arena.
     * @return The location to teleport the player to upon joining.
     */
    default Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        return null;
    }

    /**
     * Called when a player leaves the arena.
     *
     * @param arena  The mini-game arena the player left.
     * @param player The player who left the arena.
     */
    default void onPlayerLeaveArena(MiniGameArena arena, Player player) {
    }

    /**
     * Called when a spectator joins the arena.
     *
     * @param arena  The mini-game arena.
     * @param player The spectator joining.
     * @return The location to teleport the spectator to.
     */
    default Location onPlayerJoinSpectator(MiniGameArena arena, Player player) {
        return null;
    }

    /**
     * Called when a spectator leaves the arena.
     *
     * @param arena  The mini-game arena.
     * @param player The spectator leaving.
     */
    default void onPlayerLeaveSpectator(MiniGameArena arena, Player player) {
    }

    /**
     * Called when a player quits the arena.
     *
     * @param arena  The mini-game arena the player quit.
     * @param player The player who quit the arena.
     */
    default void onPlayerQuitArena(MiniGameArena arena, Player player) {
    }

    /**
     * Called when a spectator quits the arena.
     *
     * @param arena  The mini-game arena.
     * @param player The spectator who quit.
     */
    default void onPlayerQuitSpectator(MiniGameArena arena, Player player) {
    }

    /**
     * Called when a player breaks a block in the arena.
     *
     * @param arena  The mini-game arena where the block was broken.
     * @param player The player who broke the block.
     * @param block  The block that was broken.
     * @return The result of the event handling.
     */
    default HandlerEventResult onBlockPlace(MiniGameArena arena, Player player, Block block) {
        return HandlerEventResult.ALLOW;
    }

    /**
     * Called when a block is broken in the arena.
     *
     * @param arena  The mini-game arena where the block was broken.
     * @param player The player who broke the block.
     * @param block  The block that was broken.
     * @return The result of the event handling.
     */
    default HandlerEventResult onBlockBreak(MiniGameArena arena, Player player, Block block) {
        return HandlerEventResult.ALLOW;
    }

    /**
     * Called when an entity is damaged in the arena.
     *
     * @param arena The mini-game arena where the damage occurred.
     * @param event The entity damage event.
     * @return The result of the event handling.
     */
    default HandlerEventResult onEntityDamage(MiniGameArena arena, EntityDamageEvent event) {
        return HandlerEventResult.ALLOW;
    }

    /**
     * Called when an entity explosion occurs inside or against an arena.
     *
     * @param arena The mini-game arena impacted by the explosion.
     * @param event The explosion event.
     */
    default void onEntityExplode(MiniGameArena arena, EntityExplodeEvent event) {
    }

    /**
     * Called when a block explosion occurs inside or against an arena.
     *
     * @param arena The mini-game arena impacted by the explosion.
     * @param event The explosion event.
     */
    default void onBlockExplode(MiniGameArena arena, BlockExplodeEvent event) {
    }

    /**
     * Called when a player drops an item in the arena.
     *
     * @param arena  The mini-game arena where the item was dropped.
     * @param player The player who dropped the item.
     * @param item   The item that was dropped.
     * @return The result of the event handling.
     */
    default HandlerEventResult onPlayerDropItem(MiniGameArena arena, Player player, ItemStack item) {
        return HandlerEventResult.ALLOW;
    }

}
