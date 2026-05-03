package dev.stemcraft.minigame.mobarena;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArena.ArenaStatus;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.minigame.mobarena.MobArenaSpawnerRecord.IncrementType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * <p>The arena handler for Mob Arena arenas.</p>
 */
final class MobArenaArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final MobArenaMiniGame mobArena;
    private final Map<Entity, MiniGameArena> trackedEntityMiniGameArenaMap;
    private final Map<Entity, MiniGameArena> entityMiniGameArenaMap;

    private Listener entityDamageEventListener = null;
    private Listener entityDeathEventListener = null;
    private Listener entityRemoveFromWorldEventListener = null;
    private Listener entityTransformEventListener = null;

    /**
     * <p>Gets the number of tracked mobs from the given arena.</p>
     *
     * @param arena The arena to return the number of tracked mobs from.
     * @return The number of tracked mobs in the given arena.
     */
    public int getTrackedMobsForMinigame(@NotNull final MiniGameArena arena) {
        return trackedEntityMiniGameArenaMap.values()
                .stream()
                .reduce(0, (accumulator, valueArena) ->
                        valueArena.equals(arena) ? accumulator + 1 : accumulator,
                        Integer::sum);
    }

    /**
     * <p>Gets the collective sum of health for all tracked mobs in the given arena.</p>
     *
     * @param arena The arena to return the collective sum of health for all tracked mobs from.
     * @return The collective sum of health for all tracked mobs in the given arena.
     */
    public Double getTrackedMobHealthForMinigame(@NotNull final MiniGameArena arena) {
        return trackedEntityMiniGameArenaMap.entrySet()
                .stream()
                .reduce(0.0, (accumulator, value) ->
                                value.getValue().equals(arena)
                                        ? (value.getKey() instanceof Damageable
                                                ? accumulator + ((Damageable) value.getKey()).getHealth()
                                                : accumulator)
                                        : accumulator,
                        Double::sum);
    }

    /**
     * <p>Creates a new {@code MobArenaArenaHandler}.</p>
     *
     * @param api      The {@link STEMCraftAPI} to use.
     * @param mobArena The {@link MobArenaMiniGame} to use.
     */
    public MobArenaArenaHandler(@NotNull final STEMCraftAPI api, @NotNull final MobArenaMiniGame mobArena) {
        trackedEntityMiniGameArenaMap = new HashMap<>();
        entityMiniGameArenaMap = new HashMap<>();

        this.api = api;
        this.mobArena = mobArena;
    }

    /**
     * <p>Enables the Mob Arena arena handler.</p>
     * <p>This registers listeners for {@code EntityDamageEvent}, {@code EntityDeathEvent}, {@code EntityRemoveFromWorldEvent}, and {@code EntityTransformEvent}</p>
     */
    public void onEnable() {
        entityDamageEventListener = api.events().register(EntityDamageEvent.class, this::onEntityDamageDirect);
        entityDeathEventListener = api.events().register(EntityDeathEvent.class, this::onEntityDeathDirect);
        entityRemoveFromWorldEventListener = api.events().register(EntityRemoveFromWorldEvent.class, this::onEntityRemoveFromWorldDirect);
        entityTransformEventListener = api.events().register(EntityTransformEvent.class, this::onEntityTransformDirect);
    }

    /**
     * <p>The listener for the {@code EntityTransformEvent} from the Bukkit API (as compared to the STEMCraft Arena events system).</p>
     *
     * @param entityTransformEvent The {@link EntityTransformEvent} as passed by Bukkit.
     */
    private void onEntityTransformDirect(@NotNull final EntityTransformEvent entityTransformEvent) {
        @NotNull final Entity entity = entityTransformEvent.getEntity();

        if (!entityMiniGameArenaMap.containsKey(entity)) {
            return;
        }

        final boolean isEntityBeingTracked = trackedEntityMiniGameArenaMap.containsKey(entity);

        @NotNull final MiniGameArena arena = entityMiniGameArenaMap.get(entity); // guaranteed here

        entityTransformEvent.getTransformedEntities().forEach(transformedEntity -> {
            if (transformedEntity instanceof Attributable) {
                @Nullable final AttributeInstance maxHealthAttributeInstance = ((Attributable) transformedEntity).getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttributeInstance != null) {
                    arena.set("totalMobHealthSpawnedThisRound", arena.get("totalMobHealthSpawnedThisRound", Double.class) + maxHealthAttributeInstance.getValue());
                }
            }
            entityMiniGameArenaMap.put(transformedEntity, arena);
            if (isEntityBeingTracked) {
                trackedEntityMiniGameArenaMap.put(transformedEntity, arena);
            }
        });

        entityMiniGameArenaMap.remove(entity);
        trackedEntityMiniGameArenaMap.remove(entity);

        if (entity instanceof Attributable) {
            @Nullable final AttributeInstance maxHealthAttributeInstance = ((Attributable) entity).getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttributeInstance != null) {
                arena.set("totalMobHealthSpawnedThisRound", arena.get("totalMobHealthSpawnedThisRound", Double.class) - maxHealthAttributeInstance.getValue());
            }
        }

        updateArenaBossBar(arena);
    }

    /**
     * <p>The listener for the {@code EntityRemoveFromWorldEvent} from the Bukkit API (as compared to the STEMCraft Arena events system).</p>
     *
     * @param entityRemoveFromWorldEvent The {@link EntityRemoveFromWorldEvent} as passed by Bukkit.
     */
    private void onEntityRemoveFromWorldDirect(@NotNull final EntityRemoveFromWorldEvent entityRemoveFromWorldEvent) {
        @NotNull final Entity eventEntity = entityRemoveFromWorldEvent.getEntity();

        if (entityMiniGameArenaMap.containsKey(eventEntity)) {
            @NotNull final MiniGameArena arena = entityMiniGameArenaMap.get(eventEntity);

            // HACK: Is this really how I'm to do this? - ProjectHSI
            if (eventEntity instanceof Creeper && ((Creeper) eventEntity).getFuseTicks() >= ((Creeper) eventEntity).getMaxFuseTicks()) {
                // `eventEntity` is a creeper and has reached this event by exploding.

                updateArenaBossBar(arena);
                doDeathMessage(eventEntity, ((Creeper) eventEntity).getIgniter(), entityMiniGameArenaMap.get(eventEntity), MobDeathReason.Exploded);
            }
            if (trackedEntityMiniGameArenaMap.containsKey(eventEntity)) {
                removeEntityAndIncrementWave(eventEntity, null, entityMiniGameArenaMap.get(eventEntity));
            }
        }
    }

    /**
     * <p>The listener for the {@code EntityDamageEvent} from the Bukkit API (as compared to the STEMCraft Arena events system).</p>
     *
     * @param eventEntityDamageEventDirect The {@link EntityDamageEvent} as passed by Bukkit.
     */
    private void onEntityDamageDirect(@NotNull final EntityDamageEvent eventEntityDamageEventDirect) {
        @NotNull final Entity entity =  eventEntityDamageEventDirect.getEntity();
        @Nullable final Entity causingEntity = eventEntityDamageEventDirect.getDamageSource().getCausingEntity();

        if (entityMiniGameArenaMap.containsKey(entity)) {
            if (entity instanceof Damageable) {
                handleMobDamage(entity);

                final double finalDamage = eventEntityDamageEventDirect.getFinalDamage();
                if (((Damageable) entity).getHealth() - finalDamage > 0.0d) {
                    return;
                }
            }

            doDeathMessage(entity, causingEntity, entityMiniGameArenaMap.get(entity), getMobDeathReason(eventEntityDamageEventDirect.getCause()));
        }
    }

    /**
     * <p>The listener for the {@code EntityDamageEvent} from the Bukkit API (as compared to the STEMCraft Arena events system).</p>
     *
     * @param entityDeathEventDirect The {@link EntityDeathEvent} as passed by Bukkit.
     */
    private void onEntityDeathDirect(@NonNull final EntityDeathEvent entityDeathEventDirect) {
        @NotNull final Entity dyingEntity = entityDeathEventDirect.getEntity();
        @Nullable final Entity causingEntity = entityDeathEventDirect.getDamageSource().getCausingEntity();

        if (entityMiniGameArenaMap.containsKey(dyingEntity)) {
            @NotNull final MiniGameArena arena = entityMiniGameArenaMap.get(dyingEntity);
            if (arena.getStatus() != ArenaStatus.RESETTING) {
                removeEntityAndIncrementWave(dyingEntity, causingEntity, entityMiniGameArenaMap.get(dyingEntity));
            }
            entityDeathEventDirect.getDrops().clear();
            entityDeathEventDirect.setDroppedExp(0);
        }
    }

    /**
     * <p>Gets a Mob Arena {@link MobDeathReason} from a Bukkit {@code DamageCause}</p>
     *
     * @param damageCause A Bukkit {@link DamageCause}, typically obtained from {@link EntityDamageEvent}
     * @return The {@code damageCause} represented by a {@link MobDeathReason}.
     */
    @Contract(pure = true)
    private @Nullable MobDeathReason getMobDeathReason(@NotNull final DamageCause damageCause) {
        switch (damageCause) {
            case ENTITY_EXPLOSION -> {
                return MobDeathReason.Exploded;
            }
            case VOID -> {
                return MobDeathReason.LeftRegion;
            }
        }

        return null;
    }

    /**
     * <p>Handles damage received by a mob.</p>
     *
     * @param entity The mob that took the damage.
     */
    private void handleMobDamage(@NotNull final Entity entity) {
        if (!entityMiniGameArenaMap.containsKey(entity)) {
            return;
        }

        @NotNull final MiniGameArena entityArena = entityMiniGameArenaMap.get(entity);

        if (trackedEntityMiniGameArenaMap.containsKey(entity)) {
            if (entityArena.getStatus() == ArenaStatus.RESETTING) {
                return;
            }

            updateArenaBossBar(entityArena);
        }
    }

    /**
     * <p>Updates the Minecraft Boss Bar for a Mob Arena arena.</p>
     *
     * <p>This function updates the arena with the following formula (simplified):</p>
     * <pre>
     *      E(HealthOfMobs)
     *     ------------------
     *     E(MaxHealthOfMobs)
     * </pre>
     *
     * @param arenaToProgress The arena whose boss bar to update.
     */
    private void updateArenaBossBar(@NotNull final MiniGameArena arenaToProgress) {
         arenaToProgress.set("bossBarProgress", getTrackedMobHealthForMinigame(arenaToProgress) / arenaToProgress.get("totalMobHealthSpawnedThisRound", Double.class, 1.0));
    }

    /**
     * <p>An enum of possible reasons for a mob to die.</p>
     */
    private enum MobDeathReason {
        Exploded,
        LeftRegion
    }

    /**
     * <p>Renders a death message to all participants of the arena informing them that a mob died.</p>
     *
     * <p>This overload passes {@code null} to the last argument.</p>
     *
     * @param entity The entity that died.
     * @param causingEntity That entity that caused the entity to die (or {@code null} if no entity caused it).
     * @param entityArena The arena that the entity belongs to.
     */
    private void doDeathMessage(@NotNull final Entity entity, @Nullable final Entity causingEntity, @NotNull final MiniGameArena entityArena) { doDeathMessage(entity, causingEntity, entityArena, null); }

    /**
     * <p>Renders a death message to all participants of the arena informing them that a mob died.</p>
     *
     * @param entity The entity that died.
     * @param causingEntity That entity that caused the first entity to die (or {@code null} if no entity caused it).
     * @param entityArena The arena that the entity belongs to.
     * @param mobDeathReason The reason that the mob died (maybe null).
     */
    private void doDeathMessage(@NotNull final Entity entity, @Nullable final Entity causingEntity, @NotNull final MiniGameArena entityArena, @Nullable final MobDeathReason mobDeathReason) {
        if (entityArena.getStatus() == ArenaStatus.RESETTING) {
            return;
        }

        // TODO: Make customisable? - ProjectHSI
        // TODO: Better messages and more of them. - ProjectHSI
        if (mobDeathReason == null) {
            if (causingEntity != null) {
                broadcastInfoToOccupants(entityArena, "A <red>" + entity.getName() + "</red> was just killed by <gold>" + causingEntity.getName() + "</gold>.");
            } else {
                broadcastInfoToOccupants(entityArena, "<red>" + entity.getName() + "</red> died.");
            }
        } else {
            switch (mobDeathReason) {
                case Exploded -> {
                    if (causingEntity != null) {
                        broadcastInfoToOccupants(entityArena, "A <red>" + entity.getName() + "</red> was just EXPLODED by <gold>" + causingEntity.getName() + "</gold>!");
                    } else {
                        broadcastInfoToOccupants(entityArena, "A <red>" + entity.getName() + "</red> just EXPLODED!");
                    }
                }
                case LeftRegion -> {
                    if (causingEntity != null) {
                        broadcastInfoToOccupants(entityArena, "A <red>" + entity.getName() + "</red> was just forced to leave the arena by <gold>" + causingEntity.getName() + "</gold>.");
                    } else {
                        broadcastInfoToOccupants(entityArena, "A <red>" + entity.getName() + "</red> just left the arena.");
                    }
                }
            }
        }
    }

    /**
     * <p>Removes an entity from the game.</p>
     *
     * <p>
     *     This function will:
     *     <ul>
     *         <li>Remove the entity from the list of entities to be cleaned up at the end of the game.</li>
     *         <li>Remove the entity from the list of tracked entities.</li>
     *         <li>Increment the stats for the player that caused the entity to die (or do nothing if not applicable).</li>
     *         <li>If there are no more tracked mobs, then, after 20 ticks (1 second), begin a new round.</li>
     *     </ul>
     * </p>
     *
     * @param entity The entity that died.
     * @param causingEntity That entity that caused the first entity to die (or {@code null} if no entity caused it).
     * @param entityArena The arena that the entity belongs to.
     */
    private void removeEntityAndIncrementWave(@NonNull final Entity entity, @Nullable final Entity causingEntity, @NonNull final MiniGameArena entityArena) {
        entityMiniGameArenaMap.remove(entity);
        if (trackedEntityMiniGameArenaMap.containsKey(entity)) {
            trackedEntityMiniGameArenaMap.remove(entity);

            if (causingEntity instanceof Player) {
                @NotNull final MiniGamePlayer miniGamePlayer = entityArena.getPlayer((Player) causingEntity);
                if (miniGamePlayer != null) {
                    api.playerStats().increment(miniGamePlayer.getPlayer().getUniqueId(), miniGamePlayer.getPlayer().getName(), MobArenaMiniGame.KILLS_TOTAL_STAT_KEY(), 1.0d);
                    miniGamePlayer.addKill();
                }
            }

            if (!trackedEntityMiniGameArenaMap.containsValue(entityArena) && entityArena.getStatus() == ArenaStatus.RUNNING) {
                STEMCraft.getPlugin().getServer().getGlobalRegionScheduler().runDelayed(STEMCraft.getPlugin(), task -> incrementRound(entityArena), 20);
            }
        }
    }

    /**
     * <p>Denies any and all player item drops.</p>
     *
     * @param arena  The mini-game arena where the item was dropped.
     * @param player The player who dropped the item.
     * @param item   The item that was dropped.
     * @return       Whether the player can drop the item or not.
     */
    @Override
    public HandlerEventResult onPlayerDropItem(final MiniGameArena arena, final Player player, final ItemStack item) {
        return HandlerEventResult.DENY;
    }

    /**
     * <p>Handles and controls players taking damage.</p>
     *
     * @param arena The mini-game arena where the damage occurred.
     * @param event The entity damage event.
     * @return      Whether the entity will take damage or not.
     */
    @Override
    public HandlerEventResult onEntityDamage(@NotNull final MiniGameArena arena, @NotNull final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof final Player player)) {
            return HandlerEventResult.ALLOW;
        }
        if (arena.getStatus() != ArenaStatus.RUNNING) {
            return HandlerEventResult.DENY;
        }

        final Entity causingEntity = event.getDamageSource().getCausingEntity();
        if (causingEntity instanceof Player && arena.hasPlayer(player)) {
            return HandlerEventResult.DENY; // no friendly fire!
        }

        final double finalDamage = event.getFinalDamage();
        if (player.getHealth() - finalDamage > 0.0d) {
            return HandlerEventResult.ALLOW;
        }

        handleDeath(arena, player, event.getDamageSource().getCausingEntity());

        return HandlerEventResult.DENY;
    }

    /**
     * <p>Handles arena state changes.</p>
     *
     * @param arena     The mini-game arena whose status changed.
     * @param oldStatus The old status of the arena.
     * @param newStatus The new status of the arena.
     */
    @Override
    public void onArenaStatusChanged(final MiniGameArena arena, final ArenaStatus oldStatus, final ArenaStatus newStatus) {
        arena.remove("bossBarProgress");
        switch (newStatus) {
            case RUNNING -> {
                prepareWave(arena, 1);
                arena.getPlayers().forEach(player -> arena.teleport(player, arena.getRegion().getRandomGroundLocation()));
                playSoundToOccupants(arena, Sound.ENTITY_PLAYER_LEVELUP, 0.85f, 1.0f);
            }
            case RESETTING -> {
                killAllTrackedMobs(arena);
                resetAllTrackedMobsForArena(arena);
                arena.removeAllOccupants();
                arena.setStatus(ArenaStatus.WAITING);
            }
        }
    }

    /**
     * <p>Kills all mobs that are to be cleaned up.</p>
     *
     * @param arena The Mob Arena arena to kill all the entities tracked for it.
     */
    private void killAllTrackedMobs(@NotNull final MiniGameArena arena) {
        entityMiniGameArenaMap.forEach((key, arenaMiniGameArena) -> {
            if (arenaMiniGameArena == arena) {
                ((Damageable) key).setHealth(0);
            }
        });
    }

    /**
     * <p>Untracks all mobs tied to a given arena..</p>
     *
     * @param arena The Mob Arena arena to untrack all the entities tracked for it.
     */
    private void resetAllTrackedMobsForArena(@NotNull final MiniGameArena arena) {
        entityMiniGameArenaMap.values().removeAll(Collections.singleton(arena));
        trackedEntityMiniGameArenaMap.values().removeAll(Collections.singleton(arena));
    }

    /**
     * <p>Handles the arena countdown counting down.</p>
     *
     * @param arena            The mini-game arena whose countdown ticked.
     * @param secondsRemaining The remaining countdown time in seconds after the tick.
     */
    @Override
    public void onArenaCountdownTick(final MiniGameArena arena, final int secondsRemaining) {
        if (secondsRemaining <= 0) {
            return;
        }

        final ArenaStatus status = arena.getStatus();
        if ((status == ArenaStatus.STARTING || status == ArenaStatus.ENDING) && secondsRemaining <= 5) {
            final float pitch = 1.0f + ((5 - secondsRemaining) * 0.1f);
            playSoundToOccupants(arena, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, pitch);
            if (status == ArenaStatus.STARTING) {
                arena.showStartingCountdownTitle(secondsRemaining);
            }
        }
    }

    /**
     * <p>Handles the arena countdown ending (to perform state changes).</p>
     *
     * @param arena The mini-game arena whose countdown ended.
     */
    @Override
    public void onArenaCountdownEnd(final MiniGameArena arena) {
        if (arena.getStatus() == ArenaStatus.STARTING) {
            arena.setStatus(ArenaStatus.RUNNING);
        } else if (arena.getStatus() == ArenaStatus.RUNNING) {
            arena.setStatus(ArenaStatus.ENDING, 30);
        } else if (arena.getStatus() == ArenaStatus.ENDING) {
            arena.setStatus(ArenaStatus.RESETTING);
        }
    }

    /**
     * <p>Handles whenever a player joins the arena to start it, and to spawn them in a position.</p>
     *
     * @param arena  The mini-game arena the player joined.
     * @param player The player who joined the arena.
     * @return Where to teleport the player.
     */
    @Override
    public Location onPlayerJoinArena(final MiniGameArena arena, final Player player) {
        if (arena.getStatus() == ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
            arena.setStatus(ArenaStatus.STARTING, mobArena.startCountdownSeconds(arena));
        }
        clearPlayerInventory(player);
        player.setGameMode(GameMode.ADVENTURE);
        return arena.getLobbySpawn();
    }

    /**
     * <p>Handles whenever a player leaves the arena.</p>
     *
     * @param arena  The mini-game arena the player left.
     * @param player The player who left the arena.
     */
    @Override
    public void onPlayerLeaveArena(final MiniGameArena arena, final Player player) {
        if (arena.getStatus() == ArenaStatus.STARTING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(ArenaStatus.WAITING);
            arena.setCountdown(0);
        } else if (arena.getStatus() == ArenaStatus.RUNNING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(ArenaStatus.RESETTING);
        }
    }

    /**
     * <p>Handles whenever a player quits the arena.</p>
     *
     * @param arena  The mini-game arena the player left.
     * @param player The player who left the arena.
     */
    @Override
    public void onPlayerQuitArena(final MiniGameArena arena, final Player player) {
        onPlayerLeaveArena(arena, player);
    }

    /**
     * <p>Determines the amount of mobs to spawn given the round, initial wave, initial amount, increment amount,
     * and increment type of a mob.</p>
     *
     * @param round The current round.
     * @param initialWave The initial wave to spawn the mob in.
     * @param initialAmount The initial amount of the mob to spawn.
     * @param incrementAmount How much to increase the amount of mobs to spawn in by.
     * @param incrementType How to increase the amount of mobs to spawn in by.
     * @return The amount of mobs to spawn in.
     */
    private int determineMobSpawnCount(final int round, final int initialWave,
                                       final int initialAmount, final double incrementAmount,
                                       @NotNull final IncrementType incrementType) {
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

    /**
     * <p>Spawns in mobs for a given arena and round.</p>
     *
     * @param arena The arena to spawn mobs in.
     * @param round The current round of the arena.
     */
    private void spawnMobs(@NotNull final MiniGameArena arena, final int round) {
        final int spawnerConfigs = arena.get("spawner-configs.max", Integer.class);

        int mobsSpawned = 0;
        double totalMobHealth = 0;

        for (int i = 0; i < spawnerConfigs; i++) {
            final String spawnerConfigPrefix = "spawner-configs." + i + ".";
            final SCRegion spawnZoneRegion = arena.getMap("zones", String.class, SCRegion.class).get(arena.get(spawnerConfigPrefix + "spawnZone", String.class));
            final Class<? extends Entity> entityClass = arena.get(spawnerConfigPrefix + "entityType", EntityType.class).getEntityClass();

            if (entityClass == null)
                continue;

            final int mobsToSpawn = determineMobSpawnCount(
                    round,
                    arena.get(spawnerConfigPrefix + "initialWave", Integer.class),
                    arena.get(spawnerConfigPrefix + "initialAmount", Integer.class),
                    arena.get(spawnerConfigPrefix + "incrementAmount", Double.class),
                    arena.get(spawnerConfigPrefix + "incrementType", IncrementType.class)
            );

            mobsSpawned += mobsToSpawn;

            for (int j = 0; j < mobsToSpawn; j++) {
                @NotNull final Entity newEntity = arena.world().spawn(spawnZoneRegion.getRandomGroundLocation(), entityClass);
                entityMiniGameArenaMap.put(newEntity, arena);
                if (arena.get(spawnerConfigPrefix + "countTowardsMobCount", Boolean.class)) {
                    trackedEntityMiniGameArenaMap.put(newEntity, arena);
                    if (newEntity instanceof Damageable) {
                        totalMobHealth += ((Damageable) newEntity).getHealth();
                    }
                }
                if (newEntity instanceof LivingEntity) {
                    api.regions().trackLivingEntity((LivingEntity) newEntity);
                }
                @Nullable final Entity spawnedVehicle = newEntity.getVehicle();
                if (spawnedVehicle != null) {
                    entityMiniGameArenaMap.put(spawnedVehicle, arena);
                }
            }
        }

        arena.set("mobsSpawnedThisRound", mobsSpawned);
        arena.set("totalMobHealthSpawnedThisRound", totalMobHealth);
        arena.set("bossBarProgress", 1.0);
    }

    /**
     * <p>Prepares a new wave in the given arena.</p>
     *
     * @param arena The arena to prepare the wave for.
     * @param wave The wave to prepare for.
     */
    private void prepareWave(@NotNull final MiniGameArena arena, final int wave) {
        arena.set("wave", wave);
        announceWave(arena, wave);
        setupAllPlayers(arena);
        setAllPlayersStats(arena, wave);
        spawnMobs(arena, wave);
    }

    /**
     * <p>Updates the stats of all players in an arena.</p>
     *
     * @param arena The arena whose players will have their stats set.
     * @param wave The wave of the arena.
     */
    private void setAllPlayersStats(@NotNull final MiniGameArena arena, final int wave) {
        final int finalRound = wave - 1;
        arena.getPlayers().forEach(player -> {
            if (api.playerStats().total(player.getUniqueId(), MobArenaMiniGame.HIGHEST_ROUND_STAT_KEY()) < finalRound) {
                api.playerStats().set(player.getUniqueId(), player.getName(), MobArenaMiniGame.HIGHEST_ROUND_STAT_KEY(), finalRound);
            }
        });
    }

    /**
     * <p>Announce a wave.</p>
     *
     * @param arena The arena whose players will be announced to.
     * @param wave The new wave of the arena that will be announced.
     */
    private void announceWave(@NotNull final MiniGameArena arena, final int wave) {
        arena.showTitle("<gold>Wave " + wave + "</gold>", "Get fighting!");
    }

    /**
     * <p>Sets up a player (I.E. sets health, food, and inventory).</p>
     *
     * @param player The player to setup.
     */
    private void setupPlayer(@NotNull final Player player) {
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

    /**
     * <p>Sets up all players in a given arena.</p>
     *
     * @param arena The arena whose players to set up.
     */
    private void setupAllPlayers(@NotNull final MiniGameArena arena) {
        arena.getPlayers().forEach(this::setupPlayer);
    }

    /**
     * <p>Increments an arena's wave and sets it up for that new round.</p>
     *
     * @param arena The arena whose round will be incremented.
     */
    private void incrementRound(@NotNull final MiniGameArena arena) {
        prepareWave(arena, arena.get("wave", Integer.class) + 1);
    }

    /**
     * <p>Validates an arena.</p>
     *
     * @param arena  The mini-game arena to validate.
     * @param result The result object to record validation issues.
     */
    @Override
    public void validate(@NotNull final MiniGameArena arena, @NotNull final ArenaValidationResult result) {
        final boolean enableArenaChecking;
        final SCRegion arenaRegion = arena.getRegion();
        //noinspection VariableNotUsedInsideIf // Used to toggle arena checking above.
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arena");
            enableArenaChecking = false;
        } else {
            enableArenaChecking = true;
        }
        if (arena.getLobbySpawn() == null) {
            result.addError("Lobby spawn is not defined.", "lobbySpawn");
        }

        if (arena.getMap("zones", String.class, SCRegion.class) == null) {
            result.addError("Zones are not defined.", "zones");
        }

        if (arena.getMinPlayers() < 1) {
            result.addError("Mob arena arenas require at least 1 minimum players.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 1) {
            result.addError("Mob arena arenas require at least 1 maximum players.", "maxPlayers");
        }

        // Complex validation
        final Map<String, SCRegion> zones = arena.getMap("zones", String.class, SCRegion.class);
        final Set<String> validZones = new HashSet<>(zones.keySet());

        zones.forEach((key, region) -> {
            if (enableArenaChecking) {
                if (!arenaRegion.contains(region)) {
                    result.addError("Zone '" + key + "' is not contained within the arena region.", "zones." + key);
                }
            }
        });

        final Integer spawnerConfigsSize = arena.get("spawner-configs.max", Integer.class);
        if (spawnerConfigsSize == null) {
            result.addError("Corrupted spawner configs (resolve manually in config file).", "spawner-configs");
        } else if (spawnerConfigsSize <= 0) {
            result.addError("No spawner configs.", "spawner-configs");
        } else {
            for (int i = 0; i < spawnerConfigsSize; i++) {
                final String spawnerConfigPrefix = "spawner-configs." + i + ".";

                final String spawnZone = arena.get(spawnerConfigPrefix + "spawnZone", String.class);
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

    /**
     * <p>Handles an arena loading.</p>
     *
     * @param arena The mini-game arena that was loaded.
     */
    @Override
    public void onArenaLoad(final MiniGameArena arena) {
        arena.getOrCreate("trackedEntities", Set.class, HashSet::new);
        arena.getOrCreate("trackedAndCountedEntities", Set.class, HashSet::new);
        @NotNull final String listenerPrefix = regionListenerPrefix(arena.id());

        final SCRegion arenaRegion = arena.getRegion();
        assert arenaRegion != null;
        api.regions().addListener(listenerPrefix + "boundary", arenaRegion, new RegionListener() {
            @Override
            public void onExit(@NotNull final Player player, @NotNull final SCRegion region, @Nullable final Location from, @Nullable final Location to) {
                 if (arena.hasPlayer(player) && arena.getStatus() == ArenaStatus.RUNNING) {
                    handleDeath(arena, player, null);
                } else if (arena.hasOccupant(player) && arena.getStatus() == ArenaStatus.ENDING) {
                    keepOccupantInEndingArea(arena, player);
                } else if (arena.hasOccupant(player) && arena.getStatus() == ArenaStatus.RESETTING) {
                    arena.removeOccupant(player);
                }
            }

            @Override
            public void onExit(@NotNull final LivingEntity entity, @NotNull final SCRegion region, @Nullable final Location from, @Nullable final Location to) {
                if (entityMiniGameArenaMap.get(entity) == arena) {
                    updateArenaBossBar(arena);
                    doDeathMessage(entity, null, arena, MobDeathReason.LeftRegion);
                    entity.setHealth(0);
                }
            }
        });
    }

    /**
     * <p>Handles when an arena unloads.</p>
     *
     * @param arena The mini-game arena that was unloaded.
     */
    @Override
    public void onArenaUnload(@NotNull final MiniGameArena arena) {
        arena.stopWinnerCelebration();
        resetAllTrackedMobsForArena(arena);
        @NotNull final String listenerPrefix = regionListenerPrefix(arena.id());
        api.regions().removeListener(listenerPrefix + "boundary");
    }

    /**
     * <p>Handles the death of a player in an arena.</p>
     *
     * @param arena         The arena that the player died in.
     * @param player        The player that died.
     * @param causingEntity The entity that killed the player.
     */
    private void handleDeath(@NotNull final MiniGameArena arena, @NotNull final Player player, @Nullable final Entity causingEntity) {
        final MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.addDeath();
        }

        if (causingEntity != null) {
            broadcastInfoToOccupants(arena,
                    "<red>" + player.getName() + "</red> <gray>was eliminated by a</gray> <gold>" + causingEntity.getName() + "</gold><gray>.</gray>");
        } else {
            broadcastInfoToOccupants(arena,
                    "<red>" + player.getName() + "</red> <gray>fell into the void.</gray>");
        }

        @NotNull final Location playerFormerLocation = player.getLocation();

        arena.teleportToLobby(player);
        arena.addSpectator(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setHealth(PlayerUtil.getMaxHealth(player));

        if (arena.getPlayers().size() == 1) {
            arena.startWinnerCelebration(arena.getPlayers().getFirst().getLocation(), 10);
            broadcastInfoToOccupants(arena,
                    "<rainbow>Congratulations!</rainbow> " + player.getName() + " got up to wave <gold>" + arena.get("wave", Integer.class) + "</gold>!");
            arena.setStatus(ArenaStatus.ENDING);
            arena.setCountdown(10);
        } else if (arena.getPlayers().isEmpty()) {
            arena.startWinnerCelebration(playerFormerLocation, 10);
            arena.setStatus(ArenaStatus.ENDING);
            arena.setCountdown(10);
            broadcastInfoToOccupants(arena,
                    "<rainbow>Congratulations!</rainbow> You got up to wave <gold>" + arena.get("wave", Integer.class) + "</gold>!");
        }
    }

    // TODO: Merge this into a static class, code copied from Bridge. - ProjectHSI
    private void playSoundToOccupants(@NotNull final MiniGameArena arena, @NotNull final Sound sound, final float volume, final float pitch) {
        for (final Player occupant : arena.getOccupants()) {
            occupant.playSound(occupant.getLocation(), sound, volume, pitch);
        }
    }


    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
    private void broadcastInfoToOccupants(@NotNull final MiniGameArena arena, @NotNull final String message, final Player... exclude) {
        final Set<Player> excluded = Set.of(exclude);
        for (final Player occupant : arena.getOccupants()) {
            if (!excluded.contains(occupant)) {
                arena.info(occupant, message);
            }
        }
    }

    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
    private void keepOccupantInEndingArea(@NotNull final MiniGameArena arena, @NotNull final Player player) {
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
    private @NotNull String regionListenerPrefix(@NotNull final String arenaId) {
        return NamespaceId.of(MobArenaMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }

    /**
     * <p>Returns the wave of a given arena.</p>
     *
     * @param arena The arena whose wave to get.
     * @return The wave of the given arena.
     */
    public int getWaveForArena(@NotNull final MiniGameArena arena) {
        return arena.get("wave", Integer.class);
    }
}
