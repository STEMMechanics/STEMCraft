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

package dev.stemcraft.service.minigame;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.minigame.MiniGameService;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.service.BaseService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Implementation of the MiniGameService interface.
 */
public class MiniGameServiceImpl extends BaseService implements MiniGameService {
    private final Map<String, MiniGameImpl> minigames = new HashMap<>();
    private final Map<String, MiniGameArenaHandler> handlers = new HashMap<>();
    private final Map<String, Map<String, MiniGameArenaImpl>> arenasByNamespace = new HashMap<>();
    private final Map<UUID, ArenaOccupancy> players = new HashMap<>();
    private final Map<UUID, StoredPlayerState> prevPlayerStates = new HashMap<>();
    private final MiniGameTeamSelectionSupport teamSelectionSupport;

    /**
     * Constructs a MiniGameServiceImpl instance.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api    The STEMCraft API instance.
     */
    public MiniGameServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        this.teamSelectionSupport = new MiniGameTeamSelectionSupport(api, this);
    }

    @Override
    public void onReload() {
        super.onReload();
        teamSelectionSupport.reloadConfig();
    }

    /**
     * Called when the service is enabled.
     */
    @Override
    public void onEnable() {

        // Countdown Task
        api.tasks().repeating("minigame-countdown", 20, 20, () -> {
            for (MiniGame minigame : minigames.values()) {
                MiniGameArenaHandler handler = minigame.handler();
                for(MiniGameArena arena : minigame.arenas()) {
                    if (arena instanceof MiniGameArenaImpl arenaImpl) {
                        arenaImpl.pruneSupplyDrops();
                    }
                    teamSelectionSupport.tickArena(arena);
                    if (arena.getCountdown() > 0) {
                        int remaining = arena.decrementCountdown();
                        handler.onArenaCountdownTick(arena, remaining);
                        if(remaining == 0) {
                            handler.onArenaCountdownEnd(arena);
                        }
                    }
                }
            }
        });

        // Update HUD Task
        api.tasks().repeating("minigame-update-hud", 20, 20, () -> {
            for (MiniGameImpl minigame : minigames.values()) {
                for(MiniGameArena arena : minigame.arenas()) {
                    MiniGameHUD hud = minigame.getArenaActiveHUD(arena);
                    for (Player player : arena.getOccupants()) {
                        MiniGamePlayerImpl miniGamePlayer = ((MiniGameArenaImpl) arena).occupantProfile(player);
                        if (miniGamePlayer == null) {
                            continue;
                        }

                        if (hud == null) {
                            miniGamePlayer.hudDispose();
                            continue;
                        }

                        miniGamePlayer.hudUpdate(
                            hud.bossbar(miniGamePlayer),
                            hud.bossbarColor(miniGamePlayer),
                            hud.scoreboard(miniGamePlayer)
                        );
                    }
                }
            }
        });

        // WorldUnloadEvent
        api.events().register(WorldUnloadEvent.class, event -> {
            World world = event.getWorld();

            for (MiniGame minigame : minigames.values()) {
                for(MiniGameArena arena : minigame.arenas()) {
                    if (arena.world().equals(world)) {
                        minigame.removeArena(arena.id());
                    }
                }
            }
        });

        // BlockBreakEvent
        api.events().register(BlockBreakEvent.class, event -> {
            MiniGameArenaImpl arena = findParticipantArena(event.getPlayer());
            if (arena == null) return;
            MiniGameArenaHandler handler = handlers.get(arena.namespace());

            MiniGameArenaHandler.HandlerEventResult result = handler.onBlockBreak(arena, event.getPlayer(), event.getBlock());
            if (result == MiniGameArenaHandler.HandlerEventResult.DENY) {
                event.setCancelled(true);
                return;
            }
            if (result == MiniGameArenaHandler.HandlerEventResult.ALLOW_NO_DROPS) {
                event.setDropItems(false);
            }
        });

        api.events().register(BlockPlaceEvent.class, event -> {
            MiniGameArenaImpl arena = findParticipantArena(event.getPlayer());
            if (arena == null) return;
            MiniGameArenaHandler handler = handlers.get(arena.namespace());

            if (handler.onBlockPlace(arena, event.getPlayer(), event.getBlock()) == MiniGameArenaHandler.HandlerEventResult.DENY) {
                event.setCancelled(true);
                return;
            }

            ItemStack placed = event.getItemInHand();
            if (placed.getType().isAir()) {
                return;
            }

            ItemStack replenish = placed.clone();
            replenish.setAmount(1);
            if (!arena.hasUnlimitedPlacement(replenish.getType())) {
                return;
            }

            Player player = event.getPlayer();
            String namespace = arena.namespace();
            String arenaId = arena.id();
            api.tasks().nextTick(() -> {
                MiniGameArenaImpl activeArena = findParticipantArena(player);
                if (activeArena == null) {
                    return;
                }
                if (!namespace.equals(activeArena.namespace()) || !arenaId.equals(activeArena.id())) {
                    return;
                }
                if (!activeArena.hasUnlimitedPlacement(replenish.getType())) {
                    return;
                }

                player.getInventory().addItem(replenish);
                player.updateInventory();
            });
        });

        api.events().register(EntityDamageEvent.class, event -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            MiniGameArenaImpl arena = findParticipantArena(player);
            if (arena == null) return;
            MiniGameArenaHandler handler = handlers.get(arena.namespace());

            if (handler.onEntityDamage(arena, event) == MiniGameArenaHandler.HandlerEventResult.DENY) {
                event.setCancelled(true);
            }
        });

        api.events().register(FoodLevelChangeEvent.class, event -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            MiniGameArenaImpl arena = findPlayerArena(player);
            if (arena == null || !shouldPreventHunger(arena)) {
                return;
            }

            event.setCancelled(true);
            restoreFullHunger(player);
        });

        api.events().register(EntityExplodeEvent.class, event -> {
            for (MiniGameArenaImpl arena : findArenasForExplosion(event.getLocation(), event.blockList())) {
                MiniGameArenaHandler handler = handlers.get(arena.namespace());
                if (handler != null) {
                    handler.onEntityExplode(arena, event);
                }
            }
        });

        api.events().register(BlockExplodeEvent.class, event -> {
            for (MiniGameArenaImpl arena : findArenasForExplosion(event.getBlock().getLocation(), event.blockList())) {
                MiniGameArenaHandler handler = handlers.get(arena.namespace());
                if (handler != null) {
                    handler.onBlockExplode(arena, event);
                }
            }
        });

        api.events().register(PlayerDropItemEvent.class, event -> {
            MiniGameArenaImpl arena = findParticipantArena(event.getPlayer());
            if (arena == null) return;
            MiniGameArenaHandler handler = handlers.get(arena.namespace());

            if (handler.onPlayerDropItem(arena, event.getPlayer(), event.getItemDrop().getItemStack()) == MiniGameArenaHandler.HandlerEventResult.DENY) {
                event.setCancelled(true);
            }
        });

        api.events().register(EntityPickupItemEvent.class, event ->
            clearTrackedSupplyDrop(event.getItem().getUniqueId())
        );

        api.events().register(InventoryPickupItemEvent.class, event ->
            clearTrackedSupplyDrop(event.getItem().getUniqueId())
        );

        api.events().register(ItemDespawnEvent.class, event ->
            clearTrackedSupplyDrop(event.getEntity().getUniqueId())
        );

        api.events().register(ItemMergeEvent.class, event -> {
            if (findArenaTrackingSupplyDrop(event.getEntity().getUniqueId()) != null
                || findArenaTrackingSupplyDrop(event.getTarget().getUniqueId()) != null) {
                event.setCancelled(true);
            }
        });

        api.events().register(EntityShootBowEvent.class, event -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            MiniGameArenaImpl arena = findParticipantArena(player);
            if (arena == null) {
                return;
            }

            ItemStack consumable = event.getConsumable();
            if (consumable == null || !event.shouldConsumeItem()) {
                return;
            }

            ItemStack ammo = consumable.clone();
            ammo.setAmount(1);
            if (!arena.hasUnlimitedAmmo(ammo.getType())) {
                return;
            }

            if (event.getProjectile() instanceof AbstractArrow arrow) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }

            String namespace = arena.namespace();
            String arenaId = arena.id();
            api.tasks().nextTick(() -> {
                MiniGameArenaImpl activeArena = findParticipantArena(player);
                if (activeArena == null) {
                    return;
                }
                if (!namespace.equals(activeArena.namespace()) || !arenaId.equals(activeArena.id())) {
                    return;
                }
                if (!activeArena.hasUnlimitedAmmo(ammo.getType())) {
                    return;
                }

                player.getInventory().addItem(ammo);
                player.updateInventory();
            });
        });

        api.events().register(PlayerQuitEvent.class, event -> {
            MiniGameArenaImpl arena = findPlayerArena(event.getPlayer());
            if (arena != null) {
                arena.handlePlayerQuit(event.getPlayer());
            }
        });

        api.events().register(PlayerTeleportEvent.class, event -> {
            Location to = event.getTo();
            if (to.getWorld() == null) {
                return;
            }

            Player player = event.getPlayer();
            MiniGameArenaImpl arena = findPlayerArena(player);
            if (arena == null || arena.world().equals(to.getWorld())) {
                return;
            }

            String namespace = arena.namespace();
            String arenaId = arena.id();
            api.tasks().nextTick(() -> {
                MiniGameArenaImpl activeArena = findPlayerArena(player);
                if (activeArena == null) {
                    return;
                }
                if (!namespace.equals(activeArena.namespace()) || !arenaId.equals(activeArena.id())) {
                    return;
                }
                if (player.getWorld().equals(activeArena.world())) {
                    return;
                }

                activeArena.handleExternalTeleport(player);
            });
        });
    }

    @Override
    public void onDisable() {
        for (MiniGame minigame : new ArrayList<>(minigames.values())) {
            for (MiniGameArena arena : new ArrayList<>(minigame.arenas())) {
                minigame.removeArena(arena.id());
            }
        }

        arenasByNamespace.clear();
        players.clear();
        prevPlayerStates.clear();
    }

    /**
     * Creates a new mini-game with the specified namespace and arena handler.
     *
     * @param namespace The unique namespace for the mini-game.
     * @param handler   The arena handler for managing arenas of this mini-game.
     * @return The created MiniGame instance.
     */
    @Override
    public MiniGame create(String namespace, MiniGameArenaHandler handler) {
        if(minigames.containsKey(namespace)) {
            throw new IllegalArgumentException("A mini-game with the namespace '" + namespace + "' already exists.");
        }

        MiniGameImpl miniGame = new MiniGameImpl(this, namespace);
        miniGame.init();
        minigames.put(namespace, miniGame);
        handlers.put(namespace, handler);
        return miniGame;
    }

    STEMCraftAPI api() {
        return api;
    }

    MiniGameTeamSelectionSupport teamSelectionSupport() {
        return teamSelectionSupport;
    }

    MiniGameImpl getMiniGameImpl(@NotNull String namespace) {
        return minigames.get(namespace);
    }

    /**
     * Retrieves a mini-game by its namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @return The MiniGame instance, or null if not found.
     */
    @Override
    public MiniGame get(String namespace) {
        return minigames.get(namespace);
    }

    /**
     * Lists all registered mini-games.
     *
     * @return A list of all MiniGame instances.
     */
    @Override
    public List<MiniGame> list() {
        return new ArrayList<>(minigames.values());
    }

    @Override
    public boolean removePlayerFromArena(@NotNull Player player, boolean restoreLocation) {
        MiniGameArenaImpl arena = findPlayerArena(player);
        if (arena == null) {
            return false;
        }

        arena.removeOccupant(player, restoreLocation);
        return true;
    }

    /**
     * Retrieves the arena handler for a given mini-game namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @return The MiniGameArenaHandler instance, or null if not found.
     */
    public MiniGameArenaHandler getHandler(String namespace) {
        return handlers.get(namespace);
    }

    /**
     * Retrieves all arenas for a given mini-game namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @return A list of MiniGameArena instances.
     */
    public List<MiniGameArena> getArenas(String namespace) {
        Map<String, MiniGameArenaImpl> arenas = arenasByNamespace.get(namespace);
        if (arenas == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(arenas.values());
    }

    /**
     * Retrieves a specific arena by its ID within a given mini-game namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @param arenaId   The ID of the arena.
     * @return The MiniGameArena instance, or null if not found.
     */
    public MiniGameArenaImpl getArena(String namespace, String arenaId) {
        Map<String, MiniGameArenaImpl> arenas = arenasByNamespace.get(namespace);
        if (arenas == null) {
            return null;
        }
        return arenas.get(arenaId);
    }

    /**
     * Checks if an arena exists within a given mini-game namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @param arenaId   The ID of the arena.
     * @return True if the arena exists, false otherwise.
     */
    public boolean arenaExists(String namespace, String arenaId) {
        Map<String, MiniGameArenaImpl> arenas = arenasByNamespace.get(namespace);
        if (arenas == null) {
            return false;
        }
        
        return arenas.containsKey(arenaId);
    }

    /**
     * Adds an arena to the service under the specified mini-game namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @param id        The ID of the arena.
     * @param arena     The MiniGameArena instance to add.
     */
    public void addArena(String namespace, String id, MiniGameArenaImpl arena) {
        arenasByNamespace.computeIfAbsent(namespace, k -> new HashMap<>()).put(id, arena);
    }

    /**
     * Removes an arena from the service under the specified mini-game namespace.
     *
     * @param namespace The namespace of the mini-game.
     * @param arenaId   The ID of the arena to remove.
     */
    public void removeArena(String namespace, String arenaId) {
        Map<String, MiniGameArenaImpl> arenas = arenasByNamespace.get(namespace);
        if (arenas != null) {
            MiniGameArenaImpl arena = arenas.remove(arenaId);
            if (arena != null) {
                arena.clearAllSupplyDrops();
                arena.stopAllCelebrations();
                arena.removeAllOccupants();

                MiniGameArenaHandler handler = handlers.get(namespace);
                if (handler != null) {
                    handler.onArenaUnload(arena);
                }
            }
        }
    }

    /**
     * Finds the arena a player is currently in.
     *
     * @param player The player to search for.
     * @return The MiniGameArena instance the player is in, or null if not found.
     */
    public MiniGameArena findPlayerArena(@NotNull Player player, @NotNull String namespace) {
        MiniGame miniGame = minigames.get(namespace);
        if (miniGame == null) {
            return null;
        }

        for (MiniGameArena arena : miniGame.arenas()) {
            if (arena.hasOccupant(player)) {
                return arena;
            }
        }

        return null;
    }

    /**
     * Finds the arena a player is currently in across all mini-games.
     *
     * @param player The player to search for.
     * @return The MiniGameArena instance the player is in, or null if not found.
     */
    public MiniGameArenaImpl findPlayerArena(@NotNull Player player) {
        ArenaOccupancy occupancy = players.get(player.getUniqueId());
        return occupancy == null ? null : occupancy.arena();
    }

    @Nullable MiniGameArenaImpl findParticipantArena(@NotNull Player player) {
        ArenaOccupancy occupancy = players.get(player.getUniqueId());
        if (occupancy == null || occupancy.spectator()) {
            return null;
        }
        return occupancy.arena();
    }

    private void clearTrackedSupplyDrop(@NotNull UUID itemId) {
        MiniGameArenaImpl arena = findArenaTrackingSupplyDrop(itemId);
        if (arena != null) {
            arena.clearSupplyDrop(itemId);
        }
    }

    private @Nullable MiniGameArenaImpl findArenaTrackingSupplyDrop(@NotNull UUID itemId) {
        for (Map<String, MiniGameArenaImpl> arenas : arenasByNamespace.values()) {
            for (MiniGameArenaImpl arena : arenas.values()) {
                if (arena.tracksSupplyDrop(itemId)) {
                    return arena;
                }
            }
        }
        return null;
    }

    public void storePreviousPlayerState(@NotNull Player player) {
        prevPlayerStates.putIfAbsent(player.getUniqueId(), StoredPlayerState.capture(player));
    }

    void restorePreviousPlayerState(@NotNull Player player, boolean restoreLocation) {
        StoredPlayerState state = prevPlayerStates.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        clearPotionEffects(player);
        for (PotionEffect effect : state.potionEffects()) {
            player.addPotionEffect(effect);
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(cloneItems(state.storageContents()));
        inventory.setArmorContents(cloneItems(state.armorContents()));
        inventory.setItemInOffHand(state.offHand() == null ? null : state.offHand().clone());

        player.setGameMode(state.gameMode());
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.flying());
        player.setFireTicks(state.fireTicks());
        player.setFallDistance(state.fallDistance());
        player.setFoodLevel(state.foodLevel());
        player.setSaturation(state.saturation());
        player.setExhaustion(state.exhaustion());
        player.setLevel(state.level());
        player.setExp(state.exp());
        player.setHealth(Math.min(state.health(), PlayerUtil.getMaxHealth(player)));
        if (restoreLocation && state.location() != null) {
            player.teleport(state.location());
        }
    }

    public void prepareActivePlayer(@NotNull Player player) {
        clearPotionEffects(player);
        player.closeInventory();
        // Active participants start in lobby-safe adventure mode until the minigame
        // explicitly equips and promotes them into a live round.
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setLevel(0);
        player.setExp(0.0f);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[0]);
        inventory.setItemInOffHand(null);
    }

    public void prepareSpectatorPlayer(@NotNull Player player) {
        prepareActivePlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    void registerPlayerArena(@NotNull Player player, @NotNull MiniGameArenaImpl arena, boolean spectator) {
        players.put(player.getUniqueId(), new ArenaOccupancy(arena, spectator));
    }

    void unregisterPlayerArena(@NotNull Player player, @NotNull MiniGameArenaImpl arena) {
        ArenaOccupancy occupancy = players.get(player.getUniqueId());
        if (occupancy != null && occupancy.arena() == arena) {
            players.remove(player.getUniqueId());
        }
    }

    private @NotNull List<MiniGameArenaImpl> findArenasForExplosion(@NotNull Location location, @NotNull List<org.bukkit.block.Block> blocks) {
        World world = location.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }

        List<MiniGameArenaImpl> matches = new ArrayList<>();
        for (Map<String, MiniGameArenaImpl> arenas : arenasByNamespace.values()) {
            for (MiniGameArenaImpl arena : arenas.values()) {
                MiniGameArenaHandler handler = handlers.get(arena.namespace());
                if (handler == null || !handler.isActive(arena) || !arena.world().equals(world)) {
                    continue;
                }

                SCRegion region = arena.getRegion();
                if (region == null) {
                    continue;
                }

                if (region.contains(location) || explosionTouchesRegion(region, blocks)) {
                    matches.add(arena);
                }
            }
        }
        return matches;
    }

    private boolean explosionTouchesRegion(@NotNull SCRegion region, @NotNull List<org.bukkit.block.Block> blocks) {
        for (org.bukkit.block.Block block : blocks) {
            if (region.contains(block.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private void clearPotionEffects(@NotNull Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private boolean shouldPreventHunger(@NotNull MiniGameArenaImpl arena) {
        MiniGame miniGame = minigames.get(arena.namespace());
        boolean defaultValue = miniGame == null || miniGame.disablesHungerByDefault();
        return arena.get("disableHunger", Boolean.class, defaultValue);
    }

    private void restoreFullHunger(@NotNull Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
    }

    private static ItemStack[] cloneItems(@Nullable ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }

        ItemStack[] cloned = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            ItemStack item = source[i];
            cloned[i] = item == null ? null : item.clone();
        }
        return cloned;
    }

    private record ArenaOccupancy(MiniGameArenaImpl arena, boolean spectator) {}

    private record StoredPlayerState(
        Location location,
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        float exp,
        int fireTicks,
        float fallDistance,
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        ItemStack offHand,
        List<PotionEffect> potionEffects
    ) {
        static StoredPlayerState capture(@NotNull Player player) {
            PlayerInventory inventory = player.getInventory();
            List<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());

            return new StoredPlayerState(
                player.getLocation().clone(),
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getExp(),
                player.getFireTicks(),
                player.getFallDistance(),
                cloneItems(inventory.getStorageContents()),
                cloneItems(inventory.getArmorContents()),
                inventory.getItemInOffHand().clone(),
                effects
            );
        }
    }
}
