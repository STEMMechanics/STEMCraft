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
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Feature that restricts actions for players in creative mode without proper permission.
 */
public class RestrictCreative extends BaseFeature {
    private static final long PICKUP_WARNING_DEBOUNCE_TICKS = 30L;
    private static final long PORTAL_SETUP_TTL_MILLIS = 5 * 60 * 1000L;
    private static final EnumSet<Material> TRACKED_SETUP_BLOCKS = EnumSet.of(
        Material.OBSIDIAN,
        Material.DISPENSER,
        Material.FIRE,
        Material.SOUL_FIRE,
        Material.LAVA,
        Material.LAVA_CAULDRON,
        Material.NETHERRACK,
        Material.REDSTONE_BLOCK,
        Material.REDSTONE,
        Material.REDSTONE_TORCH,
        Material.REDSTONE_WALL_TORCH,
        Material.REPEATER,
        Material.COMPARATOR,
        Material.LEVER,
        Material.STONE_BUTTON,
        Material.POLISHED_BLACKSTONE_BUTTON,
        Material.OAK_BUTTON,
        Material.SPRUCE_BUTTON,
        Material.BIRCH_BUTTON,
        Material.JUNGLE_BUTTON,
        Material.ACACIA_BUTTON,
        Material.CHERRY_BUTTON,
        Material.DARK_OAK_BUTTON,
        Material.MANGROVE_BUTTON,
        Material.BAMBOO_BUTTON,
        Material.CRIMSON_BUTTON,
        Material.WARPED_BUTTON,
        Material.PALE_OAK_BUTTON,
        Material.STONE_PRESSURE_PLATE,
        Material.OAK_PRESSURE_PLATE,
        Material.SPRUCE_PRESSURE_PLATE,
        Material.BIRCH_PRESSURE_PLATE,
        Material.JUNGLE_PRESSURE_PLATE,
        Material.ACACIA_PRESSURE_PLATE,
        Material.CHERRY_PRESSURE_PLATE,
        Material.DARK_OAK_PRESSURE_PLATE,
        Material.MANGROVE_PRESSURE_PLATE,
        Material.BAMBOO_PRESSURE_PLATE,
        Material.CRIMSON_PRESSURE_PLATE,
        Material.WARPED_PRESSURE_PLATE,
        Material.PALE_OAK_PRESSURE_PLATE,
        Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
        Material.LIGHT_WEIGHTED_PRESSURE_PLATE
    );
    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN
    };
    private static final String PERMISSION_INTERACT = "stemcraft.creative.override.interact";
    private static final String PERMISSION_INVENTORY = "stemcraft.creative.override.inventory";
    private static final String PERMISSION_DROP_ITEMS_ON_DEATH = "stemcraft.creative.override.drop_items_on_death";
    private static final String PERMISSION_DROP_ITEMS = "stemcraft.creative.override.drop_items";
    private static final String PERMISSION_PICKUP_ITEMS = "stemcraft.creative.override.pickup_items";
    private static final String PERMISSION_PLACE_PORTALS = "stemcraft.creative.override.place_portals";
    private static final String PERMISSION_PLACE_RESTRICTED_BLOCKS = "stemcraft.creative.override.place_restricted_blocks";
    private final Map<BlockKey, Long> attributedPortalSetup = new HashMap<>();

    /**
     * Constructor for RestrictCreative.
     *
     * @param api The STEMCraft API instance.
     */
    public RestrictCreative(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is being enabled.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerInteractEntityEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE
                    && !event.getPlayer().hasPermission(PERMISSION_INTERACT)) {
                api.messages().error(player, "RESTRICT_CREATIVE_NO_INTERACT");
                event.setCancelled(true);
            }
        });

        api.events().register(InventoryClickEvent.class, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION_INVENTORY)) {
                    if (!event.getView().title().equals(player.getOpenInventory().title())) {
                        event.setCancelled(true);
                    }
                }
            }
        });

        api.events().register(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION_DROP_ITEMS_ON_DEATH)) {
                    event.getDrops().clear();
                }
            }
        });

        api.events().register(PlayerDropItemEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION_DROP_ITEMS)) {
                api.messages().error(player, "RESTRICT_CREATIVE_NO_DROPS");
                event.setCancelled(true);
            }
        });

        api.events().register(EntityPickupItemEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION_PICKUP_ITEMS)) {
                    api.tasks().debounce(
                            "restrict-creative:pickup-warning:" + player.getUniqueId(),
                            PICKUP_WARNING_DEBOUNCE_TICKS,
                            () -> api.messages().error(player, "RESTRICT_CREATIVE_NO_PICKUPS")
                    );
                    event.setCancelled(true);
                }
            }
        });

        api.events().register(PortalCreateEvent.class, event -> {
            purgeExpiredPortalSetup();

            if (event.getEntity() instanceof Player player
                && player.getGameMode() == GameMode.CREATIVE
                && !player.hasPermission(PERMISSION_PLACE_PORTALS)) {
                api.messages().error(player, "RESTRICT_CREATIVE_NO_PORTALS");
                event.setCancelled(true);
                return;
            }

            if (isAttributedToCreativeSetup(event)) {
                event.setCancelled(true);
            }
        });

        api.events().register(BlockIgniteEvent.class, event -> {
            purgeExpiredPortalSetup();

            Player player = event.getPlayer();
            if (player != null && isCreativePortalRestricted(player)) {
                markPortalSetup(event.getBlock());
                return;
            }

            Block ignitingBlock = event.getIgnitingBlock();
            if (ignitingBlock != null && isMarkedPortalSetup(ignitingBlock)) {
                markPortalSetup(event.getBlock());
            }
        }, EventPriority.MONITOR, true);

        api.events().register(BlockBreakEvent.class, event -> clearPortalSetup(event.getBlock()), EventPriority.MONITOR, true);
        api.events().register(BlockFadeEvent.class, event -> clearPortalSetup(event.getBlock()), EventPriority.MONITOR, true);

        api.events().register(BlockPlaceEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION_PLACE_RESTRICTED_BLOCKS)) {
                Material blockType = event.getBlockPlaced().getType();
                if (blockType == Material.END_PORTAL_FRAME) {
                    api.messages().error(player, "RESTRICT_CREATIVE_NO_PLACE");
                    event.setCancelled(true);
                }
            }
        });

        api.events().register(BlockPlaceEvent.class, event -> {
            Player player = event.getPlayer();
            Block placedBlock = event.getBlockPlaced();
            clearPortalSetup(placedBlock);

            if (isCreativePortalRestricted(player) && TRACKED_SETUP_BLOCKS.contains(placedBlock.getType())) {
                markPortalSetup(placedBlock);
            }
        }, EventPriority.MONITOR, true);
    }

    private boolean isCreativePortalRestricted(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && !player.hasPermission(PERMISSION_PLACE_PORTALS);
    }

    private void markPortalSetup(Block block) {
        attributedPortalSetup.put(BlockKey.of(block), System.currentTimeMillis() + PORTAL_SETUP_TTL_MILLIS);
    }

    private void clearPortalSetup(Block block) {
        attributedPortalSetup.remove(BlockKey.of(block));
    }

    private boolean isMarkedPortalSetup(Block block) {
        BlockKey key = BlockKey.of(block);
        Long expiresAt = attributedPortalSetup.get(key);
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt < System.currentTimeMillis()) {
            attributedPortalSetup.remove(key);
            return false;
        }

        return true;
    }

    private void purgeExpiredPortalSetup() {
        long now = System.currentTimeMillis();
        attributedPortalSetup.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private boolean isAttributedToCreativeSetup(PortalCreateEvent event) {
        for (BlockState blockState : event.getBlocks()) {
            Block block = blockState.getBlock();
            if (isMarkedPortalSetup(block)) {
                return true;
            }

            for (BlockFace face : ADJACENT_FACES) {
                if (isMarkedPortalSetup(block.getRelative(face))) {
                    return true;
                }
            }
        }
        return false;
    }

    private record BlockKey(String worldName, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(
                Objects.requireNonNull(block.getWorld()).getName(),
                block.getX(),
                block.getY(),
                block.getZ()
            );
        }
    }
}
