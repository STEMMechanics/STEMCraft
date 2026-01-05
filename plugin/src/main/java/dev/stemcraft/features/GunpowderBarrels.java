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

package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Barrel;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GunpowderBarrels extends BaseFeature {
    // Tuning
    private int fuseTicks = 60;          // 3 seconds (20 ticks = 1s)
    private float power = 3.5f;          // explosion strength
    private boolean setFire = true;
    private boolean breakBlocks = true;

    // Prevent double-trigger per barrel position (short-lived)
    private final Set<String> armed = ConcurrentHashMap.newKeySet();

    /**
     * Constructor for GunpowderBarrels.
     */
    public GunpowderBarrels(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is being enabled.
     */
    @Override
    public void onEnable() {

        // Load config values
        ConfigSection config = getConfigSection();
        fuseTicks = config.getInt("fuse-ticks", fuseTicks);
        power = (float) config.getDouble("explosion-power", power);
        setFire = config.getBoolean("set-fire", setFire);
        breakBlocks = config.getBoolean("break-blocks", breakBlocks);


        // 1) Player lights a barrel -> fuse (time to run)
        api.events().register(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

            Block barrelBlock = event.getClickedBlock();
            if (barrelBlock == null || barrelBlock.getType() != Material.BARREL) return;

            ItemStack item = event.getItem();
            if (item == null) return;

            Material m = item.getType();
            boolean lighter = (m == Material.FLINT_AND_STEEL || m == Material.FIRE_CHARGE);
            if (!lighter) return;

            if (!(barrelBlock.getState() instanceof Barrel barrel)) return;
            if (!barrel.getInventory().contains(Material.GUNPOWDER)) return;

            String key = key(barrelBlock);
            if (!armed.add(key)) return; // already lit/armed

            // Optional: place fire above as a visible indicator (if air)
            Block above = barrelBlock.getRelative(BlockFace.UP);
            if (above.getType() == Material.AIR) {
                BlockIgniteEvent ignite = new BlockIgniteEvent(
                        above, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, event.getPlayer()
                );
                Bukkit.getPluginManager().callEvent(ignite);
                if (!ignite.isCancelled()) {
                    above.setType(Material.FIRE);
                }
            }

            // Fuse feedback
            Player player = event.getPlayer();
            player.playSound(barrelBlock.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1f);

            // Start fuse -> explode later
            api.tasks().runLater(fuseTicks, () -> {
                try {
                    // Barrel might already be gone
                    if (barrelBlock.getType() != Material.BARREL) return;
                    if (!(barrelBlock.getState() instanceof Barrel b2)) return;
                    if (!b2.getInventory().contains(Material.GUNPOWDER)) return;

                    explodeBarrel(barrelBlock);
                } finally {
                    armed.remove(key);
                }
            });

            event.setCancelled(true);
        }, EventPriority.NORMAL, true);

        // 2) Barrel burns away -> explode if gunpowder
        api.events().register(BlockBurnEvent.class, event -> {
            Block b = event.getBlock();
            if (b.getType() != Material.BARREL) return;
            if (!(b.getState() instanceof Barrel barrel)) return;
            if (!barrel.getInventory().contains(Material.GUNPOWDER)) return;

            String key = key(b);
            if (!armed.add(key)) return;

            // Stop vanilla burn break, we handle it
            event.setCancelled(true);

            api.tasks().nextTick(() -> {
                try {
                    if (b.getType() == Material.BARREL) explodeBarrel(b);
                } finally {
                    armed.remove(key);
                }
            });
        }, EventPriority.NORMAL, true);

        // 3) Explosion hits barrel -> chain explode if gunpowder
        api.events().register(EntityExplodeEvent.class, event -> {
            handleExplosionBlockList(event.blockList());
        }, EventPriority.NORMAL, true);

        api.events().register(BlockExplodeEvent.class, event -> {
            handleExplosionBlockList(event.blockList());
        }, EventPriority.NORMAL, true);
    }

    /**
     * Handle explosion block list to chain explode barrels with gunpowder.
     */
    private void handleExplosionBlockList(List<Block> affected) {
        // copy to avoid concurrent modification
        for (Block b : new ArrayList<>(affected)) {
            if (b.getType() != Material.BARREL) continue;
            if (!(b.getState() instanceof Barrel barrel)) continue;
            if (!barrel.getInventory().contains(Material.GUNPOWDER)) continue;

            String key = key(b);
            if (!armed.add(key)) continue;

            // Remove from original explosion so we control break/drops
            affected.remove(b);

            // Chain next tick
            api.tasks().nextTick(() -> {
                try {
                    if (b.getType() == Material.BARREL) explodeBarrel(b);
                } finally {
                    armed.remove(key);
                }
            });
        }
    }

    /**
     * Explode the barrel at the given block location.
     */
    private void explodeBarrel(Block barrelBlock) {
        // Remove first to avoid re-processing and odd drops
        barrelBlock.setType(Material.AIR, false);

        Location loc = barrelBlock.getLocation().add(0.5, 0.5, 0.5);
        World w = loc.getWorld();
        if (w == null) return;

        w.createExplosion(loc, power, setFire, breakBlocks);
    }

    private static String key(Block b) {
        World w = b.getWorld();
        return w.getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }
}