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

import dev.stemcraft.api.service.playerreset.*;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.integration.pl3xmap.GraveMapMarker;
import dev.stemcraft.integration.pl3xmap.Pl3xMapGraveMarkers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private static final String COORDINATE_PROVIDER_ID = "active-grave";
    private final Map<UUID, GraveRecord> activeGraves = new HashMap<>();
    private final Map<BlockKey, UUID> graveBlocks = new HashMap<>();
    private final Map<UUID, PendingRespawn> pendingRespawns = new HashMap<>();
    private @Nullable Pl3xMapGraveMarkers mapMarkers;

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
        createStorage();
        loadActiveGraves();
        Plugin plugin = STEMCraft.getPlugin();
        api.coordinateBar().register(plugin, COORDINATE_PROVIDER_ID, 100, this::renderGraveWaypoint);
        enableMapMarkers();
        api.playerResets().register(new PlayerResetHandler() {
            public @NotNull String id() { return "active-graves"; }
            public @NotNull Set<PlayerResetScope> scopes() { return Set.of(PlayerResetScope.GAMEPLAY, PlayerResetScope.COMPLETE); }
            public int priority() { return 180; }
            public @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context) {
                return new PlayerResetPreview("Active graves and their stored items", (int) activeGraves.values().stream().filter(g -> g.owner().equals(context.playerUuid())).count());
            }
            public void reset(@NotNull PlayerResetContext context) {
                List<GraveRecord> owned = activeGraves.values().stream().filter(g -> g.owner().equals(context.playerUuid())).toList();
                for (GraveRecord grave : owned) {
                    World world = Bukkit.getWorld(grave.world());
                    if (world != null) {
                        world.getBlockAt(grave.markerX(), grave.markerY(), grave.markerZ()).setType(Material.AIR, false);
                        world.getBlockAt(grave.chestX(), grave.chestY(), grave.chestZ()).setType(Material.AIR, false);
                        if (grave.secondChestX() != null) world.getBlockAt(grave.secondChestX(), grave.secondChestY(), grave.secondChestZ()).setType(Material.AIR, false);
                    }
                    activeGraves.remove(grave.id());
                    graveBlocks.entrySet().removeIf(entry -> entry.getValue().equals(grave.id()));
                }
                pendingRespawns.remove(context.playerUuid());
            }
        });

        api.events().register(PlayerDeathEvent.class, event -> {
            Player player = event.getEntity();
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;

            List<ItemStack> drops = new ArrayList<>(event.getDrops());
            if (drops.isEmpty()) return;

            Location death = player.getLocation();
            World world = death.getWorld();
            if (world == null) return;

            GravePlacement placement;

            // If they died in liquid, force the "liquid grave" behaviour
            Material atDeath = death.getBlock().getType();
            if (GraveStorageSupport.isLiquid(atDeath)) {
                placement = createLiquidGrave(api, death, player, drops);
            } else {
                Location surfaceLoc = findSafeSurfaceLocation(death);
                placement = surfaceLoc == null ? null : createBuriedGrave(api, surfaceLoc, player, drops);
            }

            if (placement == null) return; // leave vanilla drops
            event.getDrops().clear();
            GraveRecord grave = registerGrave(player, placement);
            pendingRespawns.put(player.getUniqueId(), new PendingRespawn(death.clone(), grave));
        });
        api.events().register(PlayerRespawnEvent.class, event -> api.tasks().nextTick(() -> {
            PendingRespawn pending = pendingRespawns.remove(event.getPlayer().getUniqueId());
            if (pending != null) sendDeathLocation(event.getPlayer(), pending);
        }));
        api.events().register(InventoryOpenEvent.class, event -> {
            Object holder = event.getInventory().getHolder(false);
            if (holder instanceof Chest chest) resolveGraveAt(chest.getLocation());
            else if (holder instanceof DoubleChest doubleChest) {
                if (doubleChest.getLeftSide() instanceof Chest left) resolveGraveAt(left.getLocation());
                if (doubleChest.getRightSide() instanceof Chest right) resolveGraveAt(right.getLocation());
            } else {
                Location location = event.getInventory().getLocation();
                if (location != null) resolveGraveAt(location);
            }
        });
        api.events().register(BlockBreakEvent.class, event -> resolveGraveAt(event.getBlock().getLocation()));
    }

    @Override
    public void onDisable() {
        api.coordinateBar().unregister(STEMCraft.getPlugin(), COORDINATE_PROVIDER_ID);
        if (mapMarkers != null) mapMarkers.disable();
        mapMarkers = null;
        activeGraves.clear();
        graveBlocks.clear();
        pendingRespawns.clear();
    }

    /**
     * LAND: Finds a "surface block" (solid) where:
     * - chest goes at y-1
     * - sign goes at y+1
     * <p>
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
    private @Nullable GravePlacement createBuriedGrave(STEMCraftAPI api, Location surfaceLoc, Player player, List<ItemStack> drops) {
        Block surface = surfaceLoc.getBlock();
        Block chestBlock = surface.getRelative(BlockFace.DOWN);
        Block signBlock = surface.getRelative(BlockFace.UP);
        boolean needsDoubleChest = GraveStorageSupport.requiresDoubleChest(drops);
        Block secondChestBlock = needsDoubleChest ? GraveStorageSupport.findDoubleChestPartner(chestBlock, false) : null;

        Chest chest = GraveStorageSupport.placeStorageChest(chestBlock, secondChestBlock);
        if (chest == null) {
            return null;
        }

        List<ItemStack> overflow = GraveStorageSupport.fillStorage(chest, secondChestBlock, drops);

        boolean signed = placeStandingSign(signBlock, player);
        if (!signed) {
            api.messages().warn(player, "Grave created but sign could not be placed.");
        }

        dropOverflow(surfaceLoc, overflow);
        return new GravePlacement(signBlock.getLocation(), chestBlock.getLocation(),
            secondChestBlock == null ? null : secondChestBlock.getLocation());
    }

    /**
     * LIQUID: Build a dirt cap at the liquid surface, chest beneath, and dirt around chest where not solid.
     * Layout (approx):
     * <p>
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
    private @Nullable GravePlacement createLiquidGrave(STEMCraftAPI api, Location death, Player player, List<ItemStack> drops) {
        World world = death.getWorld();
        if (world == null) return null;

        int x = death.getBlockX();
        int z = death.getBlockZ();
        int baseY = death.getBlockY();

        Integer liquidTopY = findLiquidSurfaceTopY(world, x, baseY, z);
        if (liquidTopY == null) {
            api.messages().warn(player, "Could not find liquid surface for grave, leaving vanilla drops.");
            return null;
        }

        Block cap = world.getBlockAt(x, liquidTopY, z);      // will become dirt cap
        Block chestBlock = world.getBlockAt(x, liquidTopY - 1, z);
        Block signBlock = world.getBlockAt(x, liquidTopY + 1, z);
        boolean needsDoubleChest = GraveStorageSupport.requiresDoubleChest(drops);
        Block secondChestBlock = needsDoubleChest ? GraveStorageSupport.findDoubleChestPartner(chestBlock, true) : null;

        // We only replace "safe" blocks for this style to avoid griefing builds.
        if (!GraveStorageSupport.isLiquid(cap.getType())) return null;
        if (!GraveStorageSupport.canReplaceWithDirt(cap.getType())) return null;
        if (!GraveStorageSupport.isReplaceableForGrave(chestBlock.getType()) && chestBlock.getType() != Material.WATER && chestBlock.getType() != Material.LAVA)
            return null;
        if (!GraveStorageSupport.isReplaceableForGrave(signBlock.getType())) return null;
        if (secondChestBlock != null && !GraveStorageSupport.canReplaceForChest(secondChestBlock.getType())) return null;

        // Make the dirt cap at surface (replaces liquid)
        cap.setType(Material.DIRT, false);

        // Chest below the cap
        if (!GraveStorageSupport.canReplaceForChest(chestBlock.getType())) return null;
        Chest chest = GraveStorageSupport.placeStorageChest(chestBlock, secondChestBlock);
        if (chest == null) {
            return null;
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

        return new GravePlacement(signBlock.getLocation(), chestBlock.getLocation(),
            secondChestBlock == null ? null : secondChestBlock.getLocation());
    }

    private void createStorage() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS active_graves (
              grave_id TEXT PRIMARY KEY,
              owner_uuid TEXT NOT NULL,
              owner_name TEXT NOT NULL,
              world TEXT NOT NULL,
              marker_x INTEGER NOT NULL,
              marker_y INTEGER NOT NULL,
              marker_z INTEGER NOT NULL,
              chest_x INTEGER NOT NULL,
              chest_y INTEGER NOT NULL,
              chest_z INTEGER NOT NULL,
              second_chest_x INTEGER,
              second_chest_y INTEGER,
              second_chest_z INTEGER,
              created_at INTEGER NOT NULL
            )
            """);
    }

    private void loadActiveGraves() {
        activeGraves.clear();
        graveBlocks.clear();
        api.database().queryEach("SELECT * FROM active_graves", null, result -> {
            GraveRecord grave = new GraveRecord(
                UUID.fromString(result.getString("grave_id")),
                UUID.fromString(result.getString("owner_uuid")),
                result.getString("owner_name"), result.getString("world"),
                result.getInt("marker_x"), result.getInt("marker_y"), result.getInt("marker_z"),
                result.getInt("chest_x"), result.getInt("chest_y"), result.getInt("chest_z"),
                (Integer) result.getObject("second_chest_x"), (Integer) result.getObject("second_chest_y"),
                (Integer) result.getObject("second_chest_z"), result.getLong("created_at"));
            indexGrave(grave);
        });
    }

    private GraveRecord registerGrave(Player player, GravePlacement placement) {
        Location marker = placement.marker();
        Location chest = placement.chest();
        Location second = placement.secondChest();
        GraveRecord grave = new GraveRecord(UUID.randomUUID(), player.getUniqueId(), player.getName(),
            marker.getWorld().getName(), marker.getBlockX(), marker.getBlockY(), marker.getBlockZ(),
            chest.getBlockX(), chest.getBlockY(), chest.getBlockZ(),
            second == null ? null : second.getBlockX(), second == null ? null : second.getBlockY(),
            second == null ? null : second.getBlockZ(), System.currentTimeMillis());
        indexGrave(grave);
        api.database().update("""
            INSERT INTO active_graves(grave_id,owner_uuid,owner_name,world,marker_x,marker_y,marker_z,
              chest_x,chest_y,chest_z,second_chest_x,second_chest_y,second_chest_z,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, statement -> {
            statement.setString(1, grave.id().toString());
            statement.setString(2, grave.owner().toString());
            statement.setString(3, grave.ownerName());
            statement.setString(4, grave.world());
            statement.setInt(5, grave.markerX()); statement.setInt(6, grave.markerY()); statement.setInt(7, grave.markerZ());
            statement.setInt(8, grave.chestX()); statement.setInt(9, grave.chestY()); statement.setInt(10, grave.chestZ());
            if (grave.secondChestX() == null) {
                statement.setNull(11, java.sql.Types.INTEGER); statement.setNull(12, java.sql.Types.INTEGER);
                statement.setNull(13, java.sql.Types.INTEGER);
            } else {
                statement.setInt(11, grave.secondChestX()); statement.setInt(12, grave.secondChestY());
                statement.setInt(13, grave.secondChestZ());
            }
            statement.setLong(14, grave.createdAt());
        });
        return grave;
    }

    private void indexGrave(GraveRecord grave) {
        activeGraves.put(grave.id(), grave);
        graveBlocks.put(new BlockKey(grave.world(), grave.markerX(), grave.markerY(), grave.markerZ()), grave.id());
        graveBlocks.put(new BlockKey(grave.world(), grave.chestX(), grave.chestY(), grave.chestZ()), grave.id());
        if (grave.secondChestX() != null) {
            graveBlocks.put(new BlockKey(grave.world(), grave.secondChestX(), grave.secondChestY(), grave.secondChestZ()), grave.id());
        }
    }

    private void resolveGraveAt(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        UUID graveId = graveBlocks.get(new BlockKey(world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        if (graveId == null) return;
        GraveRecord grave = activeGraves.remove(graveId);
        if (grave == null) return;
        graveBlocks.entrySet().removeIf(entry -> entry.getValue().equals(graveId));
        api.database().update("DELETE FROM active_graves WHERE grave_id=?",
            statement -> statement.setString(1, graveId.toString()));
    }

    private @Nullable Component renderGraveWaypoint(Player player) {
        GraveRecord grave = activeGraves.values().stream()
            .filter(candidate -> candidate.owner().equals(player.getUniqueId()))
            .max(java.util.Comparator.comparingLong(GraveRecord::createdAt)).orElse(null);
        if (grave == null) return null;
        String icon = api.messages().tokens().apply(api.locales().resolve(":gravestone:"));
        if (!player.getWorld().getName().equals(grave.world())) {
            World world = Bukkit.getWorld(grave.world());
            String worldName = world == null ? friendlyWorldName(grave.world()) : api.worlds().getDisplayName(world);
            return Component.text(icon + " " + worldName, NamedTextColor.WHITE);
        }
        double dx = grave.markerX() + 0.5D - player.getLocation().getX();
        double dz = grave.markerZ() + 0.5D - player.getLocation().getZ();
        return Component.text(icon + " " + compassDirection(dx, dz) + " " + Math.round(Math.hypot(dx, dz)),
            NamedTextColor.WHITE);
    }

    private void sendDeathLocation(Player player, PendingRespawn pending) {
        Location death = pending.death();
        GraveRecord grave = pending.grave();
        String worldName = api.worlds().getDisplayName(death.getWorld());
        player.sendMessage(Component.text("You died at " + death.getBlockX() + ", " + death.getBlockY() + ", "
            + death.getBlockZ() + " in " + worldName + ".", NamedTextColor.YELLOW));
        if (death.getBlockX() != grave.markerX() || death.getBlockY() != grave.markerY() || death.getBlockZ() != grave.markerZ()) {
            player.sendMessage(Component.text("Your grave is nearby at " + grave.markerX() + ", " + grave.markerY()
                + ", " + grave.markerZ() + ".", NamedTextColor.YELLOW));
        }
    }

    private void enableMapMarkers() {
        Plugin pl3xMap = Bukkit.getPluginManager().getPlugin("Pl3xMap");
        if (pl3xMap == null || !pl3xMap.isEnabled()) return;
        try {
            mapMarkers = new Pl3xMapGraveMarkers(STEMCraft.getPlugin(), this::mapGraves);
            mapMarkers.enable();
        } catch (RuntimeException | LinkageError exception) {
            mapMarkers = null;
            STEMCraft.getPlugin().getLogger().warning("Could not enable Pl3xMap grave markers: " + exception.getMessage());
        }
    }

    private Collection<GraveMapMarker> mapGraves() {
        return activeGraves.values().stream().map(grave -> new GraveMapMarker(grave.id(), grave.owner(),
            grave.ownerName(), grave.world(), grave.markerX(), grave.markerY(), grave.markerZ())).toList();
    }

    static String compassDirection(double dx, double dz) {
        if (dx == 0D && dz == 0D) return "Here";
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0D) degrees += 360D;
        return directions[(int) Math.round(degrees / 45D) % directions.length];
    }

    private static String friendlyWorldName(String world) {
        String name = world.replace('_', ' ').replace('-', ' ').trim();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(" nether")) return "Nether";
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(" the end")) return "The End";
        StringBuilder result = new StringBuilder();
        for (String word : name.split("\\s+")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private record GravePlacement(Location marker, Location chest, @Nullable Location secondChest) { }
    private record PendingRespawn(Location death, GraveRecord grave) { }
    private record BlockKey(String world, int x, int y, int z) { }
    private record GraveRecord(UUID id, UUID owner, String ownerName, String world,
                               int markerX, int markerY, int markerZ,
                               int chestX, int chestY, int chestZ,
                               @Nullable Integer secondChestX, @Nullable Integer secondChestY,
                               @Nullable Integer secondChestZ, long createdAt) { }

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
