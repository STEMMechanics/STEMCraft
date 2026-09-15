/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.feature.underhalls;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.chunkgen.UnderhallsGenerator;
import dev.stemcraft.feature.HeldLightFeature;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.*;
import java.util.function.Predicate;
import static dev.stemcraft.feature.underhalls.UnderhallsStore.Pos;

/** Protect generated terrain and record successful player placements, regardless of owner. */
public final class UnderhallsProtection {
    private final STEMCraftAPI api;
    private final UnderhallsStore store;
    private final Predicate<World> managed;
    private final List<Listener> listeners = new ArrayList<>();
    public UnderhallsProtection(STEMCraftAPI api, UnderhallsStore store, Predicate<World> managed) {
        this.api = api; this.store = store; this.managed = managed;
    }
    private boolean protectedBlock(Block block) { return managed.test(block.getWorld()) && !store.isPlaced(Pos.of(block)); }
    public boolean canPlace(Block block) {
        if (!managed.test(block.getWorld())) return true;
        int floor = UnderhallsGenerator.floor(block.getWorld());
        return block.getY() > floor && block.getY() <= floor + 5 && !UnderhallsGenerator.reserved(block.getX(), block.getZ());
    }
    public boolean canBreak(Block block) { return !protectedBlock(block); }
    public void enable() {
        listen(BlockPlaceEvent.class, EventPriority.HIGHEST, event -> {
            if (!managed.test(event.getBlock().getWorld())) return;
            var replaced = event instanceof BlockMultiPlaceEvent multi ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
            for (var state : replaced) {
                if (!canPlace(state.getBlock()) || (!state.getType().isAir() && !(state.getType() == Material.LIGHT && HeldLightFeature.isTemporaryLight(state.getBlock())) && !store.isPlaced(Pos.of(state.getBlock())))) {
                    event.setCancelled(true); return;
                }
            }
        });
        listen(BlockPlaceEvent.class, EventPriority.MONITOR, event -> {
            if (!managed.test(event.getBlock().getWorld())) return;
            var replaced = event instanceof BlockMultiPlaceEvent multi ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
            for (var state : replaced) store.placed(Pos.of(state.getBlock()));
        });
        listen(BlockBreakEvent.class, EventPriority.HIGHEST, event -> {
            if (!canBreak(event.getBlock())) event.setCancelled(true);
        });
        listen(BlockBreakEvent.class, EventPriority.MONITOR, event -> {
            if (managed.test(event.getBlock().getWorld())) {
                Block block = event.getBlock();
                store.removed(Pos.of(block));
                if (block.getBlockData() instanceof org.bukkit.block.data.Bisected bisected) {
                    Block other = block.getRelative(0, bisected.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? 1 : -1, 0);
                    if (other.getType() == block.getType()) store.removed(Pos.of(other));
                }
            }
        });
        listen(BlockPistonExtendEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockPistonRetractEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockFromToEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockPhysicsEvent.class, EventPriority.HIGHEST, e -> { if (protectedBlock(e.getBlock())) e.setCancelled(true); });
        listen(BlockBurnEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockIgniteEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockFadeEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockFormEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockGrowEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockSpreadEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(BlockFertilizeEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(StructureGrowEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getWorld())) e.setCancelled(true); });
        listen(EntityChangeBlockEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(PlayerBucketEmptyEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.setCancelled(true); });
        listen(EntityExplodeEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getEntity().getWorld())) e.blockList().clear(); });
        listen(BlockExplodeEvent.class, EventPriority.HIGHEST, e -> { if (managed.test(e.getBlock().getWorld())) e.blockList().clear(); });
        // Tools must not transform generated blocks into breakable variants.
        listen(PlayerInteractEvent.class, EventPriority.HIGHEST, e -> {
            Block block = e.getClickedBlock();
            if (block == null || !protectedBlock(block)) return;
            if (block.getBlockData() instanceof Door) {
                if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
                e.setUseInteractedBlock(Event.Result.DENY);
                if (e.getPlayer().isSneaking() && (!e.getPlayer().getInventory().getItemInMainHand().getType().isAir() ||
                    !e.getPlayer().getInventory().getItemInOffHand().getType().isAir())) return;
                e.setUseItemInHand(Event.Result.DENY);
                if (e.getHand() == EquipmentSlot.HAND) toggleDoor(block);
            } else e.setUseInteractedBlock(Event.Result.DENY);
        });
    }
    private void toggleDoor(Block clicked) {
        Door clickedData = (Door) clicked.getBlockData();
        Block bottom = clickedData.getHalf() == Bisected.Half.BOTTOM ? clicked : clicked.getRelative(0, -1, 0);
        Block top = bottom.getRelative(0, 1, 0);
        if (!(bottom.getBlockData() instanceof Door lower) || top.getType() != bottom.getType()) return;
        lower.setHalf(Bisected.Half.BOTTOM);
        lower.setOpen(!lower.isOpen());
        Door upper = (Door) lower.clone();
        upper.setHalf(Bisected.Half.TOP);
        // Terrain physics stays disabled; synchronize the two generated halves ourselves.
        bottom.setBlockData(lower, false);
        top.setBlockData(upper, false);
        bottom.getWorld().playSound(bottom.getLocation(), lower.isOpen() ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE, 1f, 1f);
    }

    private <T extends Event> void listen(Class<T> type, EventPriority priority, dev.stemcraft.api.service.event.EventHandler<T> handler) {
        listeners.add(api.events().register(type, handler, priority, true));
    }
    public void disable() { listeners.forEach(HandlerList::unregisterAll); listeners.clear(); }
}
