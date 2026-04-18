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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Grave rules:
 * - Normal land: buried chest at y-1, sign at y+1.
 * - Water/Lava: build a small dirt "cap" at the surface, chest beneath, and dirt around chest where blocks aren't solid.
 * - If we can't safely place without wrecking blocks, fall back to vanilla drops.
 */
public class Graves extends BaseFeature {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };
    private static final int SINGLE_CHEST_SIZE = 27;

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
            if (isLiquid(atDeath)) {
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
        if (isLiquid(surfaceType) || isHazard(surfaceType)) return false;

        Material chestType = chestBlock.getType();
        if (!isReplaceableForGrave(chestType)) return false;
        if (isLiquid(chestType) || isHazard(chestType)) return false;

        Material signType = signBlock.getType();
        if (!isReplaceableForGrave(signType)) return false;
        return !isLiquid(signType) && !isHazard(signType);
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
        boolean needsDoubleChest = requiresDoubleChest(drops);
        Block secondChestBlock = needsDoubleChest ? findDoubleChestPartner(chestBlock, false) : null;

        Chest chest = placeStorageChest(chestBlock, secondChestBlock);
        if (chest == null) {
            return false;
        }

        List<ItemStack> overflow = fillChest(chest, drops);

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
        boolean needsDoubleChest = requiresDoubleChest(drops);
        Block secondChestBlock = needsDoubleChest ? findDoubleChestPartner(chestBlock, true) : null;

        // We only replace "safe" blocks for this style to avoid griefing builds.
        if (!isLiquid(cap.getType())) return false;
        if (!canReplaceWithDirt(cap.getType())) return false;
        if (!isReplaceableForGrave(chestBlock.getType()) && chestBlock.getType() != Material.WATER && chestBlock.getType() != Material.LAVA)
            return false;
        if (!isReplaceableForGrave(signBlock.getType())) return false;
        if (secondChestBlock != null && !canReplaceForChest(secondChestBlock.getType())) return false;

        // Make the dirt cap at surface (replaces liquid)
        cap.setType(Material.DIRT, false);

        // Chest below the cap
        if (!canReplaceForChest(chestBlock.getType())) return false;
        Chest chest = placeStorageChest(chestBlock, secondChestBlock);
        if (chest == null) {
            return false;
        }

        // Dirt around the chest if not solid
        ensureSolidAroundChest(chestBlock, secondChestBlock);
        if (secondChestBlock != null) {
            ensureSolidAroundChest(secondChestBlock, chestBlock);
        }

        // Standing sign above the dirt cap
        boolean signed = placeStandingSign(signBlock, player);
        if (!signed) {
            api.messages().warn(player, "Liquid grave created but sign could not be placed.");
        }

        List<ItemStack> overflow = fillChest(chest, drops);
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

            if (isLiquid(world.getBlockAt(x, yUp, z).getType())) { anyLiquidY = yUp; break; }
            if (isLiquid(world.getBlockAt(x, yDown, z).getType())) { anyLiquidY = yDown; break; }
        }
        if (anyLiquidY == null) return null;

        // Walk upward to the topmost liquid block (surface)
        int y = anyLiquidY;
        int maxY = world.getMaxHeight() - 2;
        while (y < maxY && isLiquid(world.getBlockAt(x, y + 1, z).getType())) {
            y++;
        }
        return y;
    }

    /**
     * Ensures the blocks around the chest (sides and below) are solid by replacing non-solid blocks with dirt.
     *
     * @param chestBlock The block where the chest is placed.
     */
    private void ensureSolidAroundChest(Block chestBlock, Block partnerChestBlock) {
        // Sides at chest level
        for (BlockFace face : HORIZONTAL_FACES) {
            Block side = chestBlock.getRelative(face);
            if (side.equals(partnerChestBlock)) {
                continue;
            }
            if (!side.getType().isSolid() && canReplaceWithDirt(side.getType())) {
                side.setType(Material.DIRT, false);
            }
        }

        // Below chest
        Block below = chestBlock.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid() && canReplaceWithDirt(below.getType())) {
            below.setType(Material.DIRT, false);
        }
    }

    /**
     * Places a standing sign at the given block with RIP information.
     *
     * @param signBlock The block where the sign will be placed.
     * @param player The player who died.
     * @return True if the sign was placed successfully, false otherwise.
     */
    private boolean placeStandingSign(Block signBlock, Player player) {
        if (!isReplaceableForGrave(signBlock.getType())) return false;

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
     * Fills the chest with the given drops, returning any overflow items that couldn't fit.
     *
     * @param chest The chest to fill.
     * @param drops The list of item drops to add to the chest.
     * @return List of overflow items that couldn't fit in the chest.
     */
    private List<ItemStack> fillChest(Chest chest, List<ItemStack> drops) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> notFit = chest.getInventory().addItem(stack);
            if (!notFit.isEmpty()) overflow.addAll(notFit.values());
        }
        return overflow;
    }

    /**
     * Checks whether the drops fit in a single chest or need double-chest storage.
     *
     * @param drops The drops to test.
     * @return True when a single chest would overflow.
     */
    private boolean requiresDoubleChest(List<ItemStack> drops) {
        Inventory probe = Bukkit.createInventory(null, SINGLE_CHEST_SIZE);
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            Map<Integer, ItemStack> overflow = probe.addItem(stack.clone());
            if (!overflow.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds a safe adjacent block to expand a grave into a double chest.
     *
     * @param primaryChestBlock The main chest block.
     * @return A safe adjacent block, or null when the grave must remain single width.
     */
    private Block findDoubleChestPartner(Block primaryChestBlock, boolean allowLiquidPartner) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Block candidate = primaryChestBlock.getRelative(face);
            Material candidateType = candidate.getType();
            if (!canReplaceForChest(candidateType)) {
                continue;
            }
            if (!allowLiquidPartner && isLiquid(candidateType)) {
                continue;
            }
            if (isHazard(candidateType)) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    /**
     * Places the grave storage chest and optionally expands it into a double chest.
     *
     * @param primaryChestBlock The main chest block.
     * @param secondChestBlock Optional adjacent chest block for larger graves.
     * @return The primary chest state, or null when placement failed.
     */
    private Chest placeStorageChest(Block primaryChestBlock, Block secondChestBlock) {
        primaryChestBlock.setType(Material.CHEST, false);
        if (secondChestBlock != null) {
            secondChestBlock.setType(Material.CHEST, false);
        }

        if (!(primaryChestBlock.getState() instanceof Chest chest)) {
            return null;
        }

        return chest;
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

    /**
     * Checks if the material can be replaced when placing a chest.
     *
     * @param mat The material to check.
     * @return True if it can be replaced, false otherwise.
     */
    private boolean canReplaceForChest(Material mat) {
        // Allow replacing liquid/air/soft blocks, avoid overwriting valuable blocks.
        if (mat == Material.AIR) return true;
        if (isLiquid(mat)) return true;
        return isReplaceableForGrave(mat);
    }

    /**
     * Checks if the material can be replaced with dirt when creating a liquid grave.
     *
     * @param mat The material to check.
     * @return True if it can be replaced with dirt, false otherwise.
     */
    private boolean canReplaceWithDirt(Material mat) {
        // Dirt “patch” should only overwrite low-impact blocks.
        if (mat == Material.AIR) return true;
        if (isLiquid(mat)) return true;
        return isReplaceableForGrave(mat);
    }

    /**
     * Checks if the material is replaceable for grave placement.
     *
     * @param mat The material to check.
     * @return True if replaceable, false otherwise.
     */
    private boolean isReplaceableForGrave(Material mat) {
        if (mat == Material.AIR) return true;

        // Do not overwrite solids
        if (mat.isSolid()) return false;

        // Avoid overwriting containers / important interactables (extend as needed)
        return mat != Material.CHEST
                && mat != Material.BARREL
                && mat != Material.HOPPER
                && mat != Material.FURNACE
                && mat != Material.BLAST_FURNACE
                && mat != Material.SMOKER
                && mat != Material.SPAWNER;
    }

    /**
     * Checks if the material is a liquid (water or lava).
     *
     * @param mat The material to check.
     * @return True if liquid, false otherwise.
     */
    private boolean isLiquid(Material mat) {
        return mat == Material.WATER || mat == Material.LAVA;
    }

    /**
     * Checks if the material is a hazard (fire, cactus, etc.).
     *
     * @param mat The material to check.
     * @return True if hazard, false otherwise.
     */
    private boolean isHazard(Material mat) {
        return mat == Material.FIRE
                || mat == Material.SOUL_FIRE
                || mat == Material.CAMPFIRE
                || mat == Material.SOUL_CAMPFIRE
                || mat == Material.MAGMA_BLOCK
                || mat == Material.CACTUS
                || mat == Material.SWEET_BERRY_BUSH
                || mat == Material.POWDER_SNOW;
    }
}
