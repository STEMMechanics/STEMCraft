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

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Grave rules:
 * - Normal land: buried chest at y-1, sign at y+1.
 * - Water/Lava: build a small dirt "cap" at the surface, chest beneath, and dirt around chest where blocks aren't solid.
 * - If we can't safely place without wrecking blocks, fall back to vanilla drops.
 */
public class Graves extends BaseFeature {
    private static final int SEARCH_RADIUS = 10;
    private static final int SEARCH_Y_UP = 6;
    private static final int SEARCH_Y_DOWN = 12;

    // For liquid graves, how far up/down we search in the column for the liquid surface.
    private static final int LIQUID_SURFACE_SEARCH_UP = 24;
    private static final int LIQUID_SURFACE_SEARCH_DOWN = 24;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Constructor.
     *
     * @param api The STEMCraft API instance.
     */
    public Graves(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Enable the feature.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;

            List<ItemStack> drops = new ArrayList<>(event.getDrops());
            if (drops.isEmpty()) return;

            Location death = player.getLocation();
            World world = death.getWorld();
            if (world == null) return;

            boolean created;

            // If they died in liquid, force the "liquid grave" behaviour
            Material atDeath = death.getBlock().getType();
            if (GraveStorageSupport.isLiquid(atDeath)) {
                created = createLiquidGrave(api, death, player, drops);
            } else {
                Location surfaceLoc = findSafeSurfaceLocation(death);
                created = surfaceLoc != null && createBuriedGrave(api, surfaceLoc, player, drops);
            }

            if (!created) return; // leave vanilla drops
            event.getDrops().clear();
        });
    }

    /**
     * LAND: Finds a "surface block" (solid) where:
     * - chest goes at y-1
     * - sign goes at y+1
     *
     * Searches in an expanding square around the death location, checking from
     * y+SEARCH_Y_UP down to y-SEARCH_Y_DOWN for each x,z position.
     *
     * @param near Location near which to search.
     * @return Location of surface block, or null if none found.
     */
    private Location findSafeSurfaceLocation(Location near) {
        World world = near.getWorld();
        if (world == null) return null;

        int baseX = near.getBlockX();
        int baseY = near.getBlockY();
        int baseZ = near.getBlockZ();

        for (int r = 0; r <= SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;

                    int x = baseX + dx;
                    int z = baseZ + dz;

                    for (int dy = SEARCH_Y_UP; dy >= -SEARCH_Y_DOWN; dy--) {
                        int y = baseY + dy;

                        Location surfaceLoc = new Location(world, x, y, z);
                        if (isValidLandBuriedSpot(surfaceLoc)) {
                            return surfaceLoc;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks if the given location is a valid spot for a buried grave.
     *
     * @param surfaceLoc Location of the surface block.
     * @return True if valid, false otherwise.
     */
    private boolean isValidLandBuriedSpot(Location surfaceLoc) {
        Block surface = surfaceLoc.getBlock();                  // y
        Block chestBlock = surface.getRelative(BlockFace.DOWN); // y-1
        Block signBlock = surface.getRelative(BlockFace.UP);    // y+1

        Material surfaceType = surface.getType();
        if (!surfaceType.isSolid()) return false;
        if (GraveStorageSupport.isLiquid(surfaceType) || GraveStorageSupport.isHazard(surfaceType)) return false;

        Material chestType = chestBlock.getType();
        if (!GraveStorageSupport.isReplaceableForGrave(chestType)) return false;
        if (GraveStorageSupport.isLiquid(chestType) || GraveStorageSupport.isHazard(chestType)) return false;

        Material signType = signBlock.getType();
        if (!GraveStorageSupport.isReplaceableForGrave(signType)) return false;
        return !GraveStorageSupport.isLiquid(signType) && !GraveStorageSupport.isHazard(signType);
    }

    /**
     * LAND: Create a buried grave at the given surface location.
     *
     * @param api STEMCraft API instance.
     * @param surfaceLoc Location of the surface block.
     * @param player Player who died.
     * @param drops List of item drops to store in the grave.
     * @return True if grave created, false otherwise.
     */
    private boolean createBuriedGrave(STEMCraftAPI api, Location surfaceLoc, Player player, List<ItemStack> drops) {
        Block surface = surfaceLoc.getBlock();
        Block chestBlock = surface.getRelative(BlockFace.DOWN);
        Block signBlock = surface.getRelative(BlockFace.UP);
        boolean needsDoubleChest = GraveStorageSupport.requiresDoubleChest(drops);
        Block secondChestBlock = needsDoubleChest ? GraveStorageSupport.findDoubleChestPartner(chestBlock, false) : null;

        Chest chest = GraveStorageSupport.placeStorageChest(chestBlock, secondChestBlock);
        if (chest == null) {
            return false;
        }

        List<ItemStack> overflow = GraveStorageSupport.fillStorage(chest, secondChestBlock, drops);

        boolean signed = placeStandingSign(signBlock, player);
        if (!signed) {
            api.messages().warn(player, "Grave created but sign could not be placed.");
        }

        dropOverflow(surfaceLoc, overflow);
        return true;
    }

    /**
     * LIQUID: Build a dirt cap at the liquid surface, chest beneath, and dirt around chest where not solid.
     * Layout (approx):
     *
     * y+1  sign (air above)
     * y    dirt cap (replaces top liquid block)
     * y-1  chest
     * y-1  sides: dirt if not solid
     * y-2  below chest: dirt if not solid.
     *
     * @param api STEMCraft API instance.
     * @param death Location of player death.
     * @param player Player who died.
     * @param drops List of item drops to store in the grave.
     * @return True if grave created, false otherwise.
     */
    private boolean createLiquidGrave(STEMCraftAPI api, Location death, Player player, List<ItemStack> drops) {
        World world = death.getWorld();
        if (world == null) return false;

        int x = death.getBlockX();
        int z = death.getBlockZ();
        int baseY = death.getBlockY();

        Integer liquidTopY = findLiquidSurfaceTopY(world, x, baseY, z);
        if (liquidTopY == null) {
            api.messages().warn(player, "Could not find liquid surface for grave, leaving vanilla drops.");
            return false;
        }

        Block cap = world.getBlockAt(x, liquidTopY, z);      // will become dirt cap
        Block chestBlock = world.getBlockAt(x, liquidTopY - 1, z);
        Block signBlock = world.getBlockAt(x, liquidTopY + 1, z);
        boolean needsDoubleChest = GraveStorageSupport.requiresDoubleChest(drops);
        Block secondChestBlock = needsDoubleChest ? GraveStorageSupport.findDoubleChestPartner(chestBlock, true) : null;

        // We only replace "safe" blocks for this style to avoid griefing builds.
        if (!GraveStorageSupport.isLiquid(cap.getType())) return false;
        if (!GraveStorageSupport.canReplaceWithDirt(cap.getType())) return false;
        if (!GraveStorageSupport.isReplaceableForGrave(chestBlock.getType()) && chestBlock.getType() != Material.WATER && chestBlock.getType() != Material.LAVA)
            return false;
        if (!GraveStorageSupport.isReplaceableForGrave(signBlock.getType())) return false;
        if (secondChestBlock != null && !GraveStorageSupport.canReplaceForChest(secondChestBlock.getType())) return false;

        // Make the dirt cap at surface (replaces liquid)
        cap.setType(Material.DIRT, false);

        // Chest below the cap
        if (!GraveStorageSupport.canReplaceForChest(chestBlock.getType())) return false;
        Chest chest = GraveStorageSupport.placeStorageChest(chestBlock, secondChestBlock);
        if (chest == null) {
            return false;
        }

        // Dirt around the chest if not solid
        GraveStorageSupport.ensureSolidAroundChest(chestBlock, secondChestBlock);
        if (secondChestBlock != null) {
            GraveStorageSupport.ensureSolidAroundChest(secondChestBlock, chestBlock);
        }

        // Standing sign above the dirt cap
        boolean signed = placeStandingSign(signBlock, player);
        if (!signed) {
            api.messages().warn(player, "Liquid grave created but sign could not be placed.");
        }

        List<ItemStack> overflow = GraveStorageSupport.fillStorage(chest, secondChestBlock, drops);
        dropOverflow(new Location(world, x, liquidTopY, z), overflow);

        return true;
    }

    /**
     * Finds the topmost liquid block (surface) in the column at (x,z) near baseY.
     *
     * @param world World to search in.
     * @param x X coordinate.
     * @param baseY Base Y coordinate to search near.
     * @param z Z coordinate.
     * @return Y coordinate of the liquid surface, or null if none found.
     */
    private Integer findLiquidSurfaceTopY(World world, int x, int baseY, int z) {
        // Find any liquid in the column near the death Y
        Integer anyLiquidY = null;
        for (int dy = 0; dy <= Math.max(LIQUID_SURFACE_SEARCH_UP, LIQUID_SURFACE_SEARCH_DOWN); dy++) {
            int yUp = baseY + dy;
            int yDown = baseY - dy;

            if (GraveStorageSupport.isLiquid(world.getBlockAt(x, yUp, z).getType())) { anyLiquidY = yUp; break; }
            if (GraveStorageSupport.isLiquid(world.getBlockAt(x, yDown, z).getType())) { anyLiquidY = yDown; break; }
        }
        if (anyLiquidY == null) return null;

        // Walk upward to the topmost liquid block (surface)
        int y = anyLiquidY;
        int maxY = world.getMaxHeight() - 2;
        while (y < maxY && GraveStorageSupport.isLiquid(world.getBlockAt(x, y + 1, z).getType())) {
            y++;
        }
        return y;
    }

    /**
     * Places a standing sign at the given block with RIP information.
     *
     * @param signBlock The block where the sign will be placed.
     * @param player The player who died.
     * @return True if the sign was placed successfully, false otherwise.
     */
    private boolean placeStandingSign(Block signBlock, Player player) {
        if (!GraveStorageSupport.isReplaceableForGrave(signBlock.getType())) return false;

        signBlock.setType(Material.OAK_SIGN, false);
        if (!(signBlock.getState() instanceof Sign sign)) return false;

        String when = LocalDateTime.now().format(DATE_FMT);
        for (org.bukkit.block.sign.Side side : org.bukkit.block.sign.Side.values()) {
            org.bukkit.block.sign.SignSide signSide = sign.getSide(side);
            signSide.line(0, Component.text("RIP"));
            signSide.line(1, Component.text(player.getName()));
            signSide.line(2, Component.text(when));
            signSide.line(3, Component.empty());
        }
        sign.update(true, false);
        return true;
    }

    /**
     * Drops overflow items naturally at the given location.
     *
     * @param near Location near which to drop items.
     * @param overflow List of item stacks to drop.
     */
    private void dropOverflow(Location near, List<ItemStack> overflow) {
        if (overflow.isEmpty()) return;
        World world = near.getWorld();
        if (world == null) return;

        Location dropLoc = near.clone().add(0.5, 1.2, 0.5);
        for (ItemStack item : overflow) {
            world.dropItemNaturally(dropLoc, item);
        }
    }

}
