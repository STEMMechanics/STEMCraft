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

package dev.stemcraft.service.world.recorder;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldChangeSession;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.service.world.WorldServiceImpl;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorldChangeRecorder is responsible for recording changes made to the world,
 * such as block placements, block breaks, and entity spawns. It listens to various
 * events and captures the state of blocks and entities before they are modified.
 */
public class WorldChangeRecorder implements WorldBaseSetting {
    final STEMCraftAPI api;
    final WorldServiceImpl worldService;
    private final Map<World, WorldChangeSessionImpl> sessions = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param api The STEMCraft API instance.
     * @param worldService The WorldServiceImpl instance.
     */
    public WorldChangeRecorder(STEMCraftAPI api, WorldServiceImpl worldService) {
        this.api = api;
        this.worldService = worldService;
    }

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    public String key() {
        return "changes";
    }

    /**
     * Called when the recorder is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param unused The WorldService instance (not used).
     */
    public void onEnable(STEMCraftAPI api, WorldService unused) {
        if(api.database().migrationVersion("world-changes") < 1) {
            if(api.database().execute("CREATE TABLE IF NOT EXISTS world_changes_blocks(" +
                    "id INT AUTO_INCREMENT," +
                    "world VARCHAR(64) NOT NULL," +
                    "x INT NOT NULL," +
                    "y INT NOT NULL," +
                    "z INT NOT NULL," +
                    "material VARCHAR(64) NOT NULL," +
                    "data TEXT," +
                    "inventory BLOB," +
                    "PRIMARY KEY (id)" +
                    ");" +
                "CREATE INDEX IF NOT EXISTS world_changes_entities (" +
                    "world VARCHAR(64) NOT NULL," +
                    "uuid VARCHAR(36) NOT NULL" +
                ");"
            )) {
                api.database().setMigrationVersion("world-changes", 1);
            } else {
                api.messages().error("Failed to create world_changes table for WorldChangeRecorder!");
            }
        }

        // BlockBreakEvent
        api.events().register(BlockBreakEvent.class, event -> captureBlockState(event.getBlock()));

        // BlockPlaceEvent
        api.events().register(BlockPlaceEvent.class, event -> {
            if (event instanceof BlockMultiPlaceEvent multi) {
                for (BlockState replaced : multi.getReplacedBlockStates()) {
                    captureBlockState(replaced);
                }
            } else {
                captureBlockState(event.getBlockReplacedState());
            }

            // Crude fix for Aikar's hopper patch on Paper
            Block placed = event.getBlock();
            if (placed.getType() == Material.HOPPER) {
                Block above = placed.getRelative(BlockFace.UP);
                BlockState aboveState = above.getState();
                if (aboveState instanceof Container || aboveState instanceof Campfire) {
                    // This captures the real contents before any hopper tick
                    captureBlockState(aboveState);
                }
            }
        });

        // BlockBurnEvent
        api.events().register(BlockBurnEvent.class, event -> captureBlockState(event.getBlock()));

        // BlockIgniteEvent
        api.events().register(BlockIgniteEvent.class, event -> captureBlockState(event.getBlock()));

        // BlockSpreadEvent (fire)
        api.events().register(BlockExplodeEvent.class, event -> {
            for (Block block : event.blockList()) {
                captureBlockState(block);
            }
        });

        // EntityExplodeEvent
        api.events().register(EntityExplodeEvent.class, event -> {
            for (Block block : event.blockList()) {
                captureBlockState(block);
            }
        });

        // BlockFromToEvent (liquid flow)
        api.events().register(BlockFromToEvent.class, event -> captureBlockState(event.getToBlock()));

        // BlockFadeEvent (ice melting, snow melting)
        api.events().register(BlockFadeEvent.class, event -> captureBlockState(event.getBlock()));

        // BlockFormEvent (snow forming, ice forming)
        api.events().register(BlockFormEvent.class, event -> {
            // Snow, ice, etc
            captureBlockState(event.getBlock());
        });

        // Bukkit's BlockSpreadEvent is used for both plant spreading and fire spreading
        api.events().register(BlockSpreadEvent.class, event -> captureBlockState(event.getBlock()));

        // LeavesDecayEvent
        api.events().register(LeavesDecayEvent.class, event -> captureBlockState(event.getBlock()));

        // StructureGrowEvent
        api.events().register(StructureGrowEvent.class, event -> {
            // Trees etc
            for (org.bukkit.block.BlockState state : event.getBlocks()) {
                captureBlockState(state.getBlock());
            }
        });

        // EntityChangeBlockEvent
        api.events().register(EntityChangeBlockEvent.class, event -> {
            if (event.getEntityType() == EntityType.ENDERMAN
                    || event.getEntityType() == EntityType.FALLING_BLOCK
                    || event.getEntityType() == EntityType.SILVERFISH) {
                captureBlockState(event.getBlock());
            }
        });

        // PlayerInteractEvent (doors, trapdoors, fence gates)
        api.events().register(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Block block = event.getClickedBlock();
            if (block == null) return;

            BlockData data = block.getBlockData();

            // Doors, trapdoors, fence gates
            if (data instanceof Openable || data instanceof Campfire) {
                captureBlockState(block);
            }
        });

        // PlayerBucketEmptyEvent
        api.events().register(PlayerBucketEmptyEvent.class, event -> captureBlockState(event.getBlockClicked().getRelative(event.getBlockFace())));

        // ItemSpawnEvent
        api.events().register(ItemSpawnEvent.class, event -> captureEntity(event.getEntity()));

        // EntitySpawnEvent
        api.events().register(EntitySpawnEvent.class, event -> captureEntity(event.getEntity()));

        // EntityPlaceEvent
        api.events().register(EntityPlaceEvent.class, event -> captureEntity(event.getEntity()));

        // SpongeAbsorbEvent (sponge absorbing water)
        api.events().register(SpongeAbsorbEvent.class, event -> {
            World world = event.getBlock().getWorld();

            for (BlockState pending : event.getBlocks()) {
                // World is still in previous state here, so this is the *water* snapshot
                Block liveBlock = world.getBlockAt(pending.getX(), pending.getY(), pending.getZ());
                captureBlockState(liveBlock.getState());
            }
        });

        api.events().register(InventoryOpenEvent.class, event -> {
            // snapshot the top inventory's container if it is block-based (chest, barrel, etc)
            if(event.getView().getTopInventory().getLocation() == null) return;
            captureBlockState(event.getView().getTopInventory().getLocation().getBlock().getState());
        });

        api.events().register(InventoryMoveItemEvent.class, event -> {
            // hopper world

            // record source container (if block-backed)
            if(event.getSource().getLocation() != null) {
                captureBlockState(event.getSource().getLocation().getBlock().getState());
            }

            // record destination container (if block-backed)
            if(event.getDestination().getLocation() != null) {
                captureBlockState(event.getDestination().getLocation().getBlock().getState());
            }
        });

        api.events().register(InventoryClickEvent.class, event -> {
            // top inventory is the container UI (chest, barrel, etc)
            if(event.getView().getTopInventory().getLocation() == null) return;
            captureBlockState(event.getView().getTopInventory().getLocation().getBlock().getState());
        });

        api.events().register(InventoryDragEvent.class, event -> {
            if(event.getView().getTopInventory().getLocation() == null) return;
            captureBlockState(event.getView().getTopInventory().getLocation().getBlock().getState());
        });

        // BlockCookEvent
        api.events().register(BlockCookEvent.class, event -> {
            // First cook tick after recording starts will snapshot this campfire/furnace
            captureBlockState(event.getBlock().getState());
        });

        // BlockPistonExtendEvent
        api.events().register(BlockPistonExtendEvent.class, event -> {
            // Record the piston base before it changes state
            captureBlockState(event.getBlock());

            // Record all blocks that are about to be moved by the piston
            for (Block moved : event.getBlocks()) {
                captureBlockState(moved);
            }

            // Record the block in front where the piston head will appear
            Block front = event.getBlock().getRelative(event.getDirection(), event.getBlocks().size() + 1);
            captureBlockState(front);
        });

        // BlockPistonRetractEvent
        api.events().register(BlockPistonRetractEvent.class, event -> {
            // Record the piston base before it retracts
            captureBlockState(event.getBlock());

            // Record all blocks that are about to be moved back by the piston (sticky)
            for (Block moved : event.getBlocks()) {
                captureBlockState(moved);
            }

            // Record the block directly in front of the piston where the head will disappear from
            Block front = event.getBlock().getRelative(event.getDirection(), 1);
            captureBlockState(front);
        });
    }

    /**
     * Called when the recorder is disabled.
     */
    public void onDisable() {
        sessions.forEach((world, session) -> session.save());
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return A list of tab completion string arrays.
     */
    @Override
    public List<String[]> tabCompletions() {
        return List.of(
                new String[]{"record"},
                new String[]{"stop"},
                new String[]{"rollback"}
        );
    }

    /**
     * Handle the command for this setting.
     *
     * @param ctx The command context.
     * @param config The configuration section.
     * @param world The world the command is being executed in.
     */
    @Override
    public void onCommand(CommandContext ctx, ConfigSection config, World world) {
        switch(ctx.getArgLower(0)) {
            case "record" -> {
                getSession(world).start();
                ctx.returnSuccess("Started recording changes in world '" + world.getName() + "'.");
            }
            case "stop" -> {
                getSession(world).stop();
                ctx.returnSuccess("Stopped recording changes in world '" + world.getName() + "'.");
            }
            case "rollback" -> {
                WorldChangeSession session = getSession(world);
                session.rollback(true);
                session.clear();
                ctx.returnSuccess("Rolled back recorded changes in world '" + world.getName() + "'.");
            }
            default -> ctx.returnError("Unknown subcommand for changes setting: " + ctx.getArgLower(0));
        }
    }

    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world The world to set the setting for.
     * @param config The configuration section.
     * @param value The value to set.
     */
    @Override
    public void set(World world, ConfigSection config, String value) {
        // not used
    }

    /**
     * Gets the WorldChangeSession for the given world.
     *
     * @param world The world to get the session for.
     * @return The WorldChangeSession for the world.
     */
    public WorldChangeSession getSession(World world) {
        if (!sessions.containsKey(world)) {
            WorldChangeSessionImpl session = new WorldChangeSessionImpl(api, world);
            sessions.put(world, session);
            return session;
        }

        return sessions.get(world);
    }

    /**
     * Capture the current state of this block for later rollback.
     *
     * @param state The block state to capture.
     */
    private void captureBlockState(BlockState state) {
        World world = state.getWorld();

        WorldChangeSession worldState = sessions.get(world);
        worldState.captureBlockState(state);
    }

    private void captureBlockState(Block block) {
        captureBlockState(block.getState());
    }

    /**
     * Capture the entity for later rollback.
     *
     * @param entity The entity to capture.
     */
    private void captureEntity(Entity entity) {
        World world = entity.getWorld();

        WorldChangeSession worldState = sessions.get(world);
        worldState.captureEntity(entity);
    }
}