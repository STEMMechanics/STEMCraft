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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import java.util.*;
import java.util.function.Predicate;
import static dev.stemcraft.feature.underhalls.UnderhallsStore.Pos;

/** Records player edits for terrain upgrades; normal Minecraft physics and building apply. */
public final class UnderhallsProtection {
    private final STEMCraftAPI api;
    private final UnderhallsStore store;
    private final Predicate<World> managed;
    private final List<Listener> listeners = new ArrayList<>();
    public UnderhallsProtection(STEMCraftAPI api, UnderhallsStore store, Predicate<World> managed) {
        this.api = api; this.store = store; this.managed = managed;
    }
    public boolean canPlace(Block block) { return true; }
    public boolean canBreak(Block block) { return true; }
    private void edited(Block block) {
        if (managed.test(block.getWorld())) store.placed(Pos.of(block));
    }
    public void enable() {
        listen(BlockPlaceEvent.class, event -> {
            var replaced = event instanceof BlockMultiPlaceEvent multi ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
            for (var state : replaced) edited(state.getBlock());
        });
        listen(BlockBreakEvent.class, event -> {
            Block block = event.getBlock();
            edited(block);
            if (block.getBlockData() instanceof org.bukkit.block.data.Bisected bisected) {
                Block other = block.getRelative(0, bisected.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? 1 : -1, 0);
                if (other.getType() == block.getType()) edited(other);
            }
        });
        listen(EntityExplodeEvent.class, event -> event.blockList().forEach(this::edited));
        listen(BlockExplodeEvent.class, event -> event.blockList().forEach(this::edited));
        listen(BlockPistonExtendEvent.class, event -> event.getBlocks().forEach(block -> {
            edited(block); edited(block.getRelative(event.getDirection()));
        }));
        listen(BlockPistonRetractEvent.class, event -> event.getBlocks().forEach(block -> {
            edited(block); edited(block.getRelative(event.getDirection()));
        }));
    }
    private <T extends Event> void listen(Class<T> type, dev.stemcraft.api.service.event.EventHandler<T> handler) {
        listeners.add(api.events().register(type, handler, EventPriority.MONITOR, true));
    }
    public void disable() { listeners.forEach(HandlerList::unregisterAll); listeners.clear(); }
}
