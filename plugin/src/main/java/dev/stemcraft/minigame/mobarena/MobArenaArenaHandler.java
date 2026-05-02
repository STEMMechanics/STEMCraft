package dev.stemcraft.minigame.mobarena;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MobArenaArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final MobArenaMiniGame mobArena;
    private final Map<Entity, MiniGameArena> trackedEntityMiniGameArenaMap;
    private final Map<Entity, MiniGameArena> entityMiniGameArenaMap;

    Listener entityDamageEventListener;
    Listener entityDeathEventListener;
    //Listener entityDropItemEventListener;
    Listener entityRemoveFromWorldEventListener;

    public int getTrackedMobsForMinigame(MiniGameArena arena) {
        return trackedEntityMiniGameArenaMap.values()
                .stream()
                .reduce(0, (accumulator, valueArena) ->
                        valueArena.equals(arena) ? accumulator + 1 : accumulator,
                        Integer::sum);
    }

    public Double getTrackedMobHealthForMinigame(MiniGameArena arena) {
        return trackedEntityMiniGameArenaMap.entrySet()
                .stream()
                .reduce(0.0, (accumulator, value) ->
                                value.getValue().equals(arena)
                                        ? (value.getKey() instanceof Mob
                                                ? accumulator + ((Mob) value.getKey()).getHealth()
                                                : accumulator)
                                        : accumulator,
                        Double::sum);
    }

    public MobArenaArenaHandler(STEMCraftAPI api, MobArenaMiniGame mobArena) {
        trackedEntityMiniGameArenaMap = new HashMap<>();
        entityMiniGameArenaMap = new HashMap<>();

        this.api = api;
        this.mobArena = mobArena;
    }

    public void onEnable() {
        entityDamageEventListener = api.events().register(EntityDamageEvent.class, this::onEntityDamageDirect);
        entityDeathEventListener = api.events().register(EntityDeathEvent.class, this::onEntityDeathDirect);
        entityRemoveFromWorldEventListener = api.events().register(EntityRemoveFromWorldEvent.class, this::onEntityRemoveFromWorldDirect);
        //entityDropItemEventListener = api.events().register(EntityDropItemEvent.class, this::onEntityDropItemDirect);
    }

    private void onEntityRemoveFromWorldDirect(EntityRemoveFromWorldEvent entityRemoveFromWorldEvent) {
        Entity eventEntity = entityRemoveFromWorldEvent.getEntity();

        if (trackedEntityMiniGameArenaMap.containsKey(eventEntity)) {
            // HACK: Is this really how I'm to do this? - ProjectHSI
            if (eventEntity instanceof Creeper && ((Creeper) eventEntity).getFuseTicks() >= ((Creeper) eventEntity).getMaxFuseTicks()) {
                // `eventEntity` is a creeper and has reached this event by exploding.
                handleMobDamage(eventEntity);
                handleMobDeath(eventEntity, ((Creeper) eventEntity).getIgniter(), trackedEntityMiniGameArenaMap.get(eventEntity));
            }
        }
    }

    private void onEntityExplodeDirect(EntityExplodeEvent entityExplodeEvent) {
        STEMCraft.getPlugin().getLogger().info("ent exp event");
    }

    private void onEntityCombustByEntityDirect(EntityCombustByEntityEvent entityCombustEvent) {
        STEMCraft.getPlugin().getLogger().info("ent comb by ent event");
    }

    private void onEntityCombustDirect(EntityCombustEvent entityCombustEvent) {
        Entity explodingEntity = entityCombustEvent.getEntity();
        if (trackedEntityMiniGameArenaMap.containsKey(explodingEntity)) {
            handleMobDamage(null);
            handleMobDeath(explodingEntity, null, trackedEntityMiniGameArenaMap.get(explodingEntity));
        } else {
            entityMiniGameArenaMap.remove(explodingEntity);
        }
    }

    /*private void onEntityDropItemDirect(EntityDropItemEvent entityDropItemEvent) {
        if (entityMiniGameArenaMap.containsKey(entityDropItemEvent.getEntity()))
            entityDropItemEvent.setCancelled(true);
    }*/

    boolean zonesExist(@NotNull MiniGameArena arena) {
        return arena.getMap("zones", String.class, SCRegion.class) != null;
    }

    @Nullable SCRegion getZone(@NotNull MiniGameArena arena, @NotNull String zone) {
        return arena.getMap("zones", String.class, SCRegion.class).get(zone);
    }

    @NotNull SCRegion getZone(@NotNull MiniGameArena arena, @NotNull String zone, @NotNull SCRegion def) {
        return arena.getMap("zones", String.class, SCRegion.class).getOrDefault(zone, def);
    }

    boolean containsZone(@NotNull MiniGameArena arena, @NotNull String zone) {
        return arena.getMap("zones", String.class, SCRegion.class).containsKey(zone);
    }

    private void onEntityDamageDirect(EntityDamageEvent eventDirect) {
        Entity causingEntity = eventDirect.getDamageSource().getCausingEntity();

        handleMobDamage(causingEntity);
    }

    private void onEntityDeathDirect(EntityDeathEvent eventDirect) {
        Entity dyingEntity = eventDirect.getEntity();
        Entity causingEntity = eventDirect.getDamageSource().getCausingEntity();

        if (entityMiniGameArenaMap.containsKey(dyingEntity)) {
            handleMobDeath(eventDirect.getEntity(), causingEntity, entityMiniGameArenaMap.get(dyingEntity));
            eventDirect.getDrops().clear();
            eventDirect.setDroppedExp(0);

            if (eventDirect.getDamageSource().getDamageType() == DamageType.EXPLOSION) {
                // Many mobs are likely to die in an explosion.
                // Prevent the death sound from blowing out their eardrums.
                eventDirect.setDeathSoundVolume(eventDirect.getDeathSoundVolume() / 5);
            }
        }
    }

    // TODO: Rename the parameter of this or change it, this function seems messy. - ProjectHSI
    private void handleMobDamage(Entity causingEntity) {
        if (!entityMiniGameArenaMap.containsKey(causingEntity)) {
            return;
        }

        if (trackedEntityMiniGameArenaMap.containsKey(causingEntity)) {
            MiniGameArena entityArena = entityMiniGameArenaMap.get(causingEntity);

            if (entityArena.getStatus() == MiniGameArena.ArenaStatus.RESETTING) {
                return;
            }

            updateArenaBossBar(entityArena);
        }
    }

    private void updateArenaBossBar(MiniGameArena arenaToProgress) {
        arenaToProgress.set("bossBarProgress", getTrackedMobHealthForMinigame(arenaToProgress) / arenaToProgress.get("totalMobHealthSpawnedThisRound", Double.class, 1.0));
    }

    private void handleMobDeath(Entity entity, Entity causingEntity, MiniGameArena entityArena) {
        if (entityArena.getStatus() == MiniGameArena.ArenaStatus.RESETTING) {
            return;
        }

        // TODO: Make customisable? - ProjectHSI
        // TODO: Better messages and more of them.
        if (causingEntity != null) {
            broadcastInfoToOccupants(entityArena, "A " + entity.getName() + " was just killed by '" + causingEntity.getName() + "'.");
        } else {
            broadcastInfoToOccupants(entityArena, entity.getName() + " yeeted themselves into the void");
        }

        entityMiniGameArenaMap.remove(entity);
        if (trackedEntityMiniGameArenaMap.containsKey(entity)) {
            trackedEntityMiniGameArenaMap.remove(entity);

            if (causingEntity instanceof Player) {
                MiniGamePlayer miniGamePlayer = entityArena.getPlayer((Player) causingEntity);
                if (miniGamePlayer != null) {
                    api.playerStats().increment(miniGamePlayer.getPlayer().getUniqueId(), miniGamePlayer.getPlayer().getName(), MobArenaMiniGame.KILLS_TOTAL_STAT_KEY(), 1.0d);
                    miniGamePlayer.addKill();
                }
            }

            if (!trackedEntityMiniGameArenaMap.containsValue(entityArena) && entityArena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
                STEMCraft.getPlugin().getServer().getGlobalRegionScheduler().runDelayed(STEMCraft.getPlugin(), task -> incrementRound(entityArena), 20);
            }
        }
    }

    @Override
    public HandlerEventResult onPlayerDropItem(MiniGameArena arena, Player player, ItemStack item) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onEntityDamage(MiniGameArena arena, EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return HandlerEventResult.ALLOW;
        }
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return HandlerEventResult.DENY;
        }

        Entity causingEntity = event.getDamageSource().getCausingEntity();
        if (causingEntity instanceof Player && arena.hasPlayer(player)) {
            return HandlerEventResult.DENY; // no friendly fire!
        }

        double finalDamage = event.getFinalDamage();
        if (player.getHealth() - finalDamage > 0.0d) {
            return HandlerEventResult.ALLOW;
        }

        handleDeath(arena, player, event.getDamageSource().getCausingEntity());

        return HandlerEventResult.DENY;
    }

    @Override
    public void onArenaStatusChanged(MiniGameArena arena, MiniGameArena.ArenaStatus oldStatus, MiniGameArena.ArenaStatus newStatus) {
        arena.remove("bossBarProgress");
        switch (newStatus) {
            case MiniGameArena.ArenaStatus.RUNNING -> {
                prepareRound(arena, 1);
                arena.getPlayers().forEach(player -> {
                    arena.teleport(player, arena.getRegion().getRandomGroundLocation());
                });
                playSoundToOccupants(arena, Sound.ENTITY_PLAYER_LEVELUP, 0.85f, 1.0f);
            }
            case MiniGameArena.ArenaStatus.RESETTING -> {
                killAllTrackedMobs(arena);
                resetAllTrackedMobsForArena(arena);
                arena.removeAllOccupants();
                arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            }
        }
    }

    private void killAllTrackedMobs(MiniGameArena arena) {
        entityMiniGameArenaMap.forEach((key, arenaMiniGameArena) -> {
            if (arenaMiniGameArena == arena) {
                ((Mob) key).setHealth(0);
            }
        });
    }

    private void resetAllTrackedMobsForArena(MiniGameArena arena) {
        entityMiniGameArenaMap.values().removeAll(Collections.singleton(arena));
        trackedEntityMiniGameArenaMap.values().removeAll(Collections.singleton(arena));
    }

    @Override
    public void onArenaCountdownTick(MiniGameArena arena, int secondsRemaining) {
        if (secondsRemaining <= 0) {
            return;
        }

        MiniGameArena.ArenaStatus status = arena.getStatus();
        if ((status == MiniGameArena.ArenaStatus.STARTING || status == MiniGameArena.ArenaStatus.ENDING) && secondsRemaining <= 5) {
            float pitch = 1.0f + ((5 - secondsRemaining) * 0.1f);
            playSoundToOccupants(arena, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, pitch);
            if (status == MiniGameArena.ArenaStatus.STARTING) {
                arena.showStartingCountdownTitle(secondsRemaining);
            }
        }
    }

    @Override
    public void onArenaCountdownEnd(MiniGameArena arena) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RUNNING);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            arena.setStatus(MiniGameArena.ArenaStatus.ENDING, 30);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, mobArena.startCountdownSeconds(arena));
        }
        clearPlayerInventory(player);
        player.setGameMode(GameMode.ADVENTURE);
        return arena.getLobbySpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            arena.setCountdown(0);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        onPlayerLeaveArena(arena, player);
    }

    private int determineMobSpawnCount(int round, int initialWave,
                                        int initialAmount, double incrementAmount,
                                       @NotNull MobArenaArenaRecord.SpawnerRecord.IncrementType incrementType) {
        if (round < initialWave) {
            return 0;
        } else if (round == initialWave) {
            return initialAmount;
        } else {
            switch (incrementType) {
                case Linear -> {
                    return (int) Math.floor(initialAmount + (incrementAmount * (round - initialWave)));
                }
                case Exponential -> {
                    return (int) Math.floor(initialAmount + (Math.pow(incrementAmount, (round - initialWave))));
                }
            }
        }

        return 0; // ?
    }

    private void spawnMobs(MiniGameArena arena, int round) {
        int spawnerConfigs = arena.get("spawner-configs.max", Integer.class);

        int mobsSpawned = 0;
        double totalMobHealth = 0;

        for (int i = 0; i < spawnerConfigs; i++) {
            final String spawnerConfigPrefix = "spawner-configs." + i + ".";
            final SCRegion spawnZoneRegion = arena.getMap("zones", String.class, SCRegion.class).get(arena.get(spawnerConfigPrefix + "spawnZone", String.class));
            final Class<? extends Entity> entityClass = arena.get(spawnerConfigPrefix + "entityType", EntityType.class).getEntityClass();

            if (entityClass == null)
                continue;

            int mobsToSpawn = determineMobSpawnCount(
                    round,
                    arena.get(spawnerConfigPrefix + "initialWave", Integer.class),
                    arena.get(spawnerConfigPrefix + "initialAmount", Integer.class),
                    arena.get(spawnerConfigPrefix + "incrementAmount", Double.class),
                    arena.get(spawnerConfigPrefix + "incrementType", MobArenaArenaRecord.SpawnerRecord.IncrementType.class)
            );

            mobsSpawned += mobsToSpawn;

            for (int j = 0; j < mobsToSpawn; j++) {
                Entity newEntity = arena.world().spawn(spawnZoneRegion.getRandomGroundLocation(), entityClass);
                entityMiniGameArenaMap.put(newEntity, arena);
                if (arena.get(spawnerConfigPrefix + "countTowardsMobCount", Boolean.class)) {
                    trackedEntityMiniGameArenaMap.put(newEntity, arena);
                }
                if (newEntity instanceof Mob) {
                    api.regions().trackLivingEntity((LivingEntity) newEntity);
                    totalMobHealth += ((Mob) newEntity).getHealth();
                }
                Entity spawnedVehicle = newEntity.getVehicle();
                if (spawnedVehicle != null) {
                    entityMiniGameArenaMap.put(spawnedVehicle, arena);
                }
            }
        }

        arena.set("mobsSpawnedThisRound", mobsSpawned);
        arena.set("totalMobHealthSpawnedThisRound", totalMobHealth);
        arena.set("bossBarProgress", 1.0);
    }

    private void prepareRound(MiniGameArena arena, int round) {
        arena.set("round", round);
        announceWave(arena, round);
        setupAllPlayers(arena);
        setAllPlayersStats(arena, round);
        spawnMobs(arena, round);
    }

    private void setAllPlayersStats(MiniGameArena arena, int round) {
        arena.getPlayers().forEach(player -> {
            if (api.playerStats().total(player.getUniqueId(), MobArenaMiniGame.HIGHEST_ROUND_STAT_KEY()) < round) {
                api.playerStats().set(player.getUniqueId(), player.getName(), MobArenaMiniGame.HIGHEST_ROUND_STAT_KEY(), round);
            }
        });
    }

    private void announceWave(MiniGameArena arena, int round) {
        arena.showTitle("<gold>Wave " + round + "</gold>", "Get fighting!");
    }

    private void setupPlayer(Player player) {
        // TODO: Make customisable? - ProjectHSI
        player.setHealth(PlayerUtil.getMaxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.clearActivePotionEffects();

        player.getInventory().clear();
        player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.BOW));
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, 64));
        // Delay of two ticks here prevents the shield from disappearing on the client.
        player.getScheduler().runDelayed(STEMCraft.getPlugin(), task ->
                player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD)),
                null, 2);
        player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.IRON_HELMET));
        player.getInventory().setItem(EquipmentSlot.CHEST, new ItemStack(Material.IRON_CHESTPLATE));
        player.getInventory().setItem(EquipmentSlot.LEGS, new ItemStack(Material.IRON_LEGGINGS));
        player.getInventory().setItem(EquipmentSlot.FEET, new ItemStack(Material.IRON_BOOTS));

    }

    private void setupAllPlayers(MiniGameArena arena) {
        arena.getPlayers().forEach(this::setupPlayer);
    }

    private void incrementRound(MiniGameArena arena) {
        prepareRound(arena, arena.get("round", Integer.class) + 1);
    }

    @Override
    public void validate(@NotNull MiniGameArena arena, @NotNull ArenaValidationResult result) {
        boolean enableArenaChecking;
        SCRegion arenaRegion = arena.getRegion();
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arena");
            enableArenaChecking = false;
        } else {
            enableArenaChecking = true;
        }
        if (arena.getLobbySpawn() == null) {
            result.addError("Lobby spawn is not defined.", "lobbySpawn");
        }

        if (!zonesExist(arena)) {
            result.addError("Zones are not defined.", "zones");
        }

        if (arena.getMinPlayers() < 2) {
            result.addError("Mob arena arenas require at least 2 minimum players.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 2) {
            result.addError("Mob arena arenas require at least 2 maximum players.", "maxPlayers");
        }

        // Complex validation
        Map<String, SCRegion> zones = arena.getMap("zones", String.class, SCRegion.class);
        Set<String> validZones = new HashSet<>(zones.keySet());

        zones.forEach((key, region) -> {
            if (enableArenaChecking) {
                if (!arenaRegion.contains(region)) {
                    result.addError("Zone '" + key + "' is not contained within the arena region.", "zones." + key);
                }
            }
        });

        Integer spawnerConfigsSize = arena.get("spawner-configs.max", Integer.class);
        if (spawnerConfigsSize == null) {
            result.addError("Corrupted spawner configs (resolve manually in config file).", "spawner-configs");
        } else if (spawnerConfigsSize <= 0) {
            result.addError("No spawner configs.", "spawner-configs");
        } else {
            for (int i = 0; i < spawnerConfigsSize; i++) {
                final String spawnerConfigPrefix = "spawner-configs." + i + ".";

                String spawnZone = arena.get(spawnerConfigPrefix + "spawnZone", String.class);
                if (spawnZone == null) {
                    result.addError("Spawner config '" + (i + 1) + "' was badly defined (spawnZone).", spawnerConfigPrefix + "spawn-zone");
                } else if (spawnZone.isBlank()) {
                    result.addError("Spawner config '" + (i + 1) + "' spawns in an empty spawn zone (never set?)", spawnerConfigPrefix + "spawn-zone");
                } else if (!validZones.contains(spawnZone)) {
                    result.addError("Spawner config '" + (i + 1) + "' spawns in a non-existent spawn zone '" + spawnZone + "'.", spawnerConfigPrefix + "spawn-zone");
                }
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        arena.getOrCreate("trackedEntities", Set.class, HashSet::new);
        arena.getOrCreate("trackedAndCountedEntities", Set.class, HashSet::new);
        String listenerPrefix = regionListenerPrefix(arena.id());

        SCRegion arenaRegion = arena.getRegion();
        assert arenaRegion != null;
        api.regions().addListener(listenerPrefix + "boundary", arenaRegion, new RegionListener() {
            @Override
            public void onExit(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                 if (arena.hasPlayer(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
                    handleDeath(arena, player, null);
                } else if (arena.hasOccupant(player) && arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
                    keepOccupantInEndingArea(arena, player);
                } else if (arena.hasOccupant(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RESETTING) {
                    arena.removeOccupant(player);
                }
            }

            @Override
            public void onExit(@NotNull LivingEntity entity, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                if (entityMiniGameArenaMap.get(entity) == arena) {
                    updateArenaBossBar(arena);
                    handleMobDeath(entity, null, arena);
                    entity.setHealth(0);
                }
            }
        });
    }

    public void onArenaUnload(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        resetAllTrackedMobsForArena(arena);
        String listenerPrefix = regionListenerPrefix(arena.id());
        api.regions().removeListener(listenerPrefix + "boundary");
    }

    private void handleDeath(@NotNull MiniGameArena arena, @NotNull Player player, @Nullable Entity entity) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.addDeath();
        }

        if (entity != null) {
            broadcastInfoToOccupants(arena,
                    "<red>" + player.getName() + "</red> <gray>was eliminated by a</gray> <gold>" + entity.getName() + "</gold><gray>.</gray>");
        } else {
            broadcastInfoToOccupants(arena,
                    "<red>" + player.getName() + "</red> <gray>fell into the void.</gray>");
        }

        arena.teleportToLobby(player);
        arena.addSpectator(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setHealth(PlayerUtil.getMaxHealth(player));

        if (arena.getPlayers().size() == 1) {
            arena.startWinnerCelebration(arena.getPlayers().getFirst().getLocation(), 10);
            arena.setStatus(MiniGameArena.ArenaStatus.ENDING);
            arena.setCountdown(10);
        }
    }

    // TODO: Merge this into a static class, code copied from Bridge. - ProjectHSI
    private void playSoundToOccupants(@NotNull MiniGameArena arena, @NotNull Sound sound, float volume, float pitch) {
        for (Player occupant : arena.getOccupants()) {
            occupant.playSound(occupant.getLocation(), sound, volume, pitch);
        }
    }


    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
    private void broadcastInfoToOccupants(@NotNull MiniGameArena arena, @NotNull String message, Player... exclude) {
        Set<Player> excluded = Set.of(exclude);
        for (Player occupant : arena.getOccupants()) {
            if (!excluded.contains(occupant)) {
                arena.info(occupant, message);
            }
        }
    }

    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
    private void keepOccupantInEndingArea(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (arena.hasPlayer(player)) {
            arena.teleportToTeamSpawn(player);
            return;
        }

        Location spectatorSpawn = arena.getSpectatorSpawn();
        if (spectatorSpawn == null) {
            spectatorSpawn = arena.getLobbySpawn();
        }
        if (spectatorSpawn != null) {
            player.teleport(spectatorSpawn);
        }
    }

    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
    private String regionListenerPrefix(String arenaId) {
        return NamespaceId.of(MobArenaMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }

    public int getRoundForArena(MiniGameArena arena) {
        return arena.get("round", Integer.class);
    }
}
