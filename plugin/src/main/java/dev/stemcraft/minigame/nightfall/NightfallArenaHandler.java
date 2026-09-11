package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.service.comet.CometLoot;
import dev.stemcraft.api.service.world.WorldChangeSession;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.bukkit.event.world.PortalCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class NightfallArenaHandler implements MiniGameArenaHandler {
    private static final long NOON_TIME = 6000L;
    private static final long THREE_PM_TIME = 9000L;
    private static final long SUNSET_TIME = 12000L;
    private static final long NIGHT_START_TIME = 13000L;
    private static final long DAY_START_TIME = 23000L;
    private static final long NINE_PM_TIME = 15000L;
    private static final long MIDNIGHT_TIME = 18000L;
    private static final long THREE_AM_TIME = 21000L;
    private static final double DAWN_FAST_FORWARD_TARGET_MULTIPLIER = 64.0d;
    private static final Sound TIME_ANNOUNCEMENT_SOUND = Sound.BLOCK_BELL_USE;
    private static final float TIME_ANNOUNCEMENT_VOLUME = 0.65f;
    private static final float TIME_ANNOUNCEMENT_PITCH = 0.85f;
    private static final String RUNTIME_TASK_ID = "nightfall-runtime";
    private static final String TIME_TASK_ID = "nightfall-time";
    private static final int DROP_RADIUS_MIN = 5;
    private static final int DROP_RADIUS_MAX = 20;
    private static final int DROP_TARGET_RESET_DISTANCE = 30;
    private static final double DROP_ANNOUNCEMENT_RANGE = 200.0d;
    private static final double DROP_ANNOUNCEMENT_RANGE_SQUARED = DROP_ANNOUNCEMENT_RANGE * DROP_ANNOUNCEMENT_RANGE;
    private static final int RANDOM_LOCATION_ATTEMPTS = 24;
    private static final double BLOOD_MOON_TNT_HORIZONTAL_TRIGGER_RANGE_SQUARED = 20.25d;
    private static final double BLOOD_MOON_TNT_STUCK_BREACH_RANGE_SQUARED = 36.0d;
    private static final float BLOOD_MOON_TNT_EXPLOSION_POWER = 4.0f;
    private static final double BLOOD_MOON_STUCK_DISTANCE_SQUARED = 0.25d;
    private static final int BLOOD_MOON_STUCK_TICKS_REQUIRED = 8;
    private static final long BLOOD_MOON_UTILITY_ITEM_HOLD_MILLIS = 1000L;
    private static final long BURNING_ZOMBIE_FIRE_INTERVAL_MILLIS = 1000L;
    private static final int BURNING_ZOMBIE_PLAYER_IGNITE_CHANCE_PERCENT = 20;
    private static final int BURNING_ZOMBIE_PLAYER_FIRE_TICKS = 80;
    private static final double BURNING_ZOMBIE_PLAYER_RANGE_SQUARED = 2.25d;
    private static final double PREP_SPAWN_CLUSTER_RADIUS = 15.0d;
    private static final double PREP_SPAWN_MIN_PLAYER_DISTANCE = 10.0d;
    private static final double PREP_SPAWN_MIN_PLAYER_DISTANCE_SQUARED =
        PREP_SPAWN_MIN_PLAYER_DISTANCE * PREP_SPAWN_MIN_PLAYER_DISTANCE;
    private static final int PREP_SPAWN_ATTEMPTS = 64;
    private static final BlockFace[] CARDINAL_FACES = new BlockFace[] {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
    };

    private final STEMCraftAPI api;
    private final NightfallMiniGame nightfall;

    public NightfallArenaHandler(STEMCraftAPI api, NightfallMiniGame nightfall) {
        this.api = api;
        this.nightfall = nightfall;
        api.tasks().repeating(RUNTIME_TASK_ID, 5L, 5L, this::tickRuntime);
        api.tasks().repeating(TIME_TASK_ID, 1L, 1L, this::tickTimeCycle);
        registerSpawnGuards();
        registerMovementGuards();
        registerDeathFallbacks();
        registerZombieCleanup();
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        Location playSpawn = nightfall.playSpawn(arena);

        if (arena.getSpectatorSpawn() == null) {
            result.addError("Spectator spawn is not defined.", "spectatorSpawn");
        }
        if (playSpawn == null) {
            result.addError("Play spawn is not defined.", "spawn");
        }
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arenaRegion");
        }
        if (arena.getMinPlayers() < 1) {
            result.addError("Nightfall arenas require at least 1 minimum player.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 1) {
            result.addError("Nightfall arenas require at least 1 maximum player.", "maxPlayers");
        }
        if (arena.getMaxPlayers() < arena.getMinPlayers()) {
            result.addError("Max players cannot be lower than min players.", "maxPlayers");
        }
        if (nightfall.prepSeconds(arena) < 0) {
            result.addError("Prep seconds cannot be negative.", "prepSeconds");
        }
        int rawDropMinSeconds = arena.get("dropMinSeconds", Integer.class, 1);
        int rawDropMaxSeconds = arena.get("dropMaxSeconds", Integer.class, 5);
        if (rawDropMinSeconds < 0) {
            result.addError("Minimum drop delay cannot be negative.", "dropMinSeconds");
        }
        if (rawDropMaxSeconds < 0) {
            result.addError("Maximum drop delay cannot be negative.", "dropMaxSeconds");
        }
        if ((rawDropMinSeconds == 0) != (rawDropMaxSeconds == 0)) {
            result.addError("Set both drop delays to 0 to disable drops completely.", "dropMaxSeconds");
        }
        if (rawDropMaxSeconds > 0 && rawDropMaxSeconds < rawDropMinSeconds) {
            result.addError("Maximum block delay cannot be lower than minimum block delay.", "dropMaxSeconds");
        }
        int dropMaxActiveItems = arena.get("dropMaxActiveItems", Integer.class, 10);
        if (dropMaxActiveItems < 0) {
            result.addError("Maximum active drops cannot be negative.", "dropMaxActiveItems");
        }
        int dropGroupDistance = arena.get("dropGroupDistance", Integer.class, 100);
        if (dropGroupDistance < 0) {
            result.addError("Drop group distance cannot be negative.", "dropGroupDistance");
        }
        if (nightfall.zombieWaveIntervalSeconds(arena) < 1) {
            result.addError("Zombie wave interval must be at least 1 second.", "zombieWaveIntervalSeconds");
        }
        if (nightfall.zombieWaveSize(arena) < 1) {
            result.addError("Zombie wave size must be at least 1.", "zombieWaveSize");
        }
        double zombieNightlyHealthMultiplier = arena.get("zombieNightlyHealthMultiplier", Double.class, 1.05d);
        if (zombieNightlyHealthMultiplier < 1.0d) {
            result.addError("Zombie nightly health multiplier must be at least 1.0.", "zombieNightlyHealthMultiplier");
        }
        if (nightfall.dayTimeSpeedMultiplier(arena) < 1.0d) {
            result.addError("Day time speed multiplier must be at least 1.0.", "dayTimeSpeedMultiplier");
        }
        if (nightfall.nightTimeSpeedMultiplier(arena) < 1.0d) {
            result.addError("Night time speed multiplier must be at least 1.0.", "nightTimeSpeedMultiplier");
        }
        int dropLootMinStacks = arena.get("dropLootMinStacks", Integer.class, 2);
        int dropLootMaxStacks = arena.get("dropLootMaxStacks", Integer.class, 4);
        if (dropLootMinStacks < 1) {
            result.addError("Minimum drop loot stacks must be at least 1.", "dropLootMinStacks");
        }
        if (dropLootMaxStacks < 1) {
            result.addError("Maximum drop loot stacks must be at least 1.", "dropLootMaxStacks");
        }
        if (dropLootMaxStacks < dropLootMinStacks) {
            result.addError("Maximum drop loot stacks cannot be lower than minimum drop loot stacks.", "dropLootMaxStacks");
        }
        if (nightfall.zombieSpawnRadiusMax(arena) < nightfall.zombieSpawnRadiusMin(arena)) {
            result.addError("Zombie spawn max radius must be at least the min radius.", "zombieSpawnRadiusMax");
        }
        int bloodMoonChancePercent = arena.get("bloodMoonChancePercent", Integer.class, 0);
        if (bloodMoonChancePercent < 0 || bloodMoonChancePercent > 100) {
            result.addError("Blood moon chance must be between 0 and 100 percent.", "bloodMoonChancePercent");
        }
        double bloodMoonZombieSpawnMultiplier = arena.get("bloodMoonZombieSpawnMultiplier", Double.class, 2.0d);
        if (bloodMoonZombieSpawnMultiplier < 1.0d) {
            result.addError("Blood moon zombie spawn multiplier must be at least 1.0.", "bloodMoonZombieSpawnMultiplier");
        }
        int bloodMoonBabyChancePercent = arena.get("bloodMoonBabyZombieChancePercent", Integer.class, 20);
        if (bloodMoonBabyChancePercent < 0 || bloodMoonBabyChancePercent > 100) {
            result.addError("Blood moon baby zombie chance must be between 0 and 100 percent.", "bloodMoonBabyZombieChancePercent");
        }
        int bloodMoonTntZombieChancePercent = arena.get("bloodMoonTntZombieChancePercent", Integer.class, 3);
        if (bloodMoonTntZombieChancePercent < 0 || bloodMoonTntZombieChancePercent > 100) {
            result.addError("Blood moon TNT zombie chance must be between 0 and 100 percent.", "bloodMoonTntZombieChancePercent");
        }

        if (playSpawn != null && !arena.world().equals(playSpawn.getWorld())) {
            result.addError("Play spawn must be in the arena world.", "spawn");
        } else if (arenaRegion != null && playSpawn != null && !arenaRegion.contains(playSpawn)) {
            result.addError("Play spawn must be inside the arena region.", "spawn");
        }

        if (arena.getLobbySpawn() != null && !arena.world().equals(arena.getLobbySpawn().getWorld())) {
            result.addError("Lobby spawn must be in the arena world.", "lobbySpawn");
        }
        if (nightfall.lobbyLocations(arena).isEmpty()) {
            result.addError("At least one lobby location is required.", "lobbyLocations");
        } else if (nightfall.lobbyLocations(arena).stream()
            .anyMatch(location -> location.getWorld() == null || !arena.world().equals(location.getWorld()))) {
            result.addError("All lobby locations must be in the arena world.", "lobbyLocations");
        }
        if (arena.getSpectatorSpawn() != null && !arena.world().equals(arena.getSpectatorSpawn().getWorld())) {
            result.addError("Spectator spawn must be in the arena world.", "spectatorSpawn");
        }

        Map<Integer, List<Material>> dropItems = nightfall.dropItems(arena);
        if (dropItems.isEmpty()) {
            result.addError("At least one drop item tier is required.", "items");
        }
        int highestTier = 0;
        for (Map.Entry<Integer, List<Material>> entry : dropItems.entrySet()) {
            Integer threshold = entry.getKey();
            List<Material> materials = entry.getValue();
            if (threshold == null || threshold < 1 || threshold > 100) {
                result.addError("Drop tier '" + threshold + "' must be between 1 and 100.", "items." + threshold);
                continue;
            }
            highestTier = Math.max(highestTier, threshold);
            if (materials == null || materials.isEmpty()) {
                result.addError("Drop tier '" + threshold + "' must contain at least one item.", "items." + threshold);
                continue;
            }
            for (Material material : materials) {
                if (material == null || material.isAir()) {
                    result.addError("Drop tier '" + threshold + "' contains an invalid material.", "items." + threshold);
                }
            }
        }
        if (highestTier < 100) {
            result.addError("Drop tiers must include a final tier of 100.", "items");
        }

        for (MiniGame minigame : api.minigames().list()) {
            for (MiniGameArena other : minigame.arenas()) {
                if (other == arena) {
                    continue;
                }
                if (other.world().equals(arena.world())) {
                    result.addError("Nightfall arenas require a dedicated world. World '" + arena.world().getName()
                        + "' is already used by " + other.namespace() + ":" + other.id() + ".", "world");
                    return;
                }
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        arena.getOrCreate("managedZombies", Set.class, LinkedHashSet::new);
        arena.getOrCreate("nightEliminatedPlayers", Set.class, LinkedHashSet::new);
        arena.getOrCreate("playerDropDueAt", Map.class, LinkedHashMap::new);
        arena.getOrCreate("playerDropTargets", Map.class, LinkedHashMap::new);
        arena.getOrCreate("pendingDeathRespawns", Map.class, LinkedHashMap::new);
        arena.getOrCreate("prepSpawnAssignments", Map.class, LinkedHashMap::new);
        arena.getOrCreate("bloodMoonZombieLastLocations", Map.class, LinkedHashMap::new);
        arena.getOrCreate("bloodMoonZombieStuckTicks", Map.class, LinkedHashMap::new);
        arena.getOrCreate("bloodMoonZombieUtilityHoldUntil", Map.class, LinkedHashMap::new);
        arena.getOrCreate("bloodMoonTntZombies", Set.class, LinkedHashSet::new);
        arena.getOrCreate("burningZombieFireDueAt", Map.class, LinkedHashMap::new);
        arena.getOrCreate("bloodMoonPendingExplosions", Integer.class, () -> 0);
        arena.set("daylightLocked", false);
        arena.set("wasNight", false);
        arena.set("currentNight", 0);
        arena.set("remainingNightSpawns", 0);
        arena.set("nextWaveAt", 0L);
        arena.set("timeAnnouncementIndex", 0);
        arena.set("nightFastForwarding", false);
        arena.set("timeSpeedCarry", 0.0d);
        arena.set("wasSunsetOrNight", false);
        arena.set("bloodMoonActive", false);
        arena.set("activeSurvivorCount", 0);
        arena.set("zombiesRemaining", 0);
        arena.set("selectedLobbyLocation", null);

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion != null) {
            api.regions().addListener(listenerPrefix(arena.id()) + "boundary", arenaRegion, new RegionListener() {
                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                    if (arena.hasPlayer(player) && shouldConfinePlayers(arena)) {
                        returnPlayerToArena(arena, player, from);
                    } else if (arena.hasOccupant(player)
                        && (arena.getStatus() == MiniGameArena.ArenaStatus.COOLDOWN
                            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING
                            || arena.getStatus() == MiniGameArena.ArenaStatus.RESETTING)) {
                        arena.removeOccupant(player);
                    }
                }
            });
        }
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        restoreDownedPlayerVisibility(arena);
        stopRecording(arena);
        rollbackWorld(arena);
        restoreWorldSettings(arena);
        clearTrackedZombies(arena);
        prepSpawnAssignments(arena).clear();
        api.regions().removeListener(listenerPrefix(arena.id()) + "*");
    }

    @Override
    public HandlerEventResult onBlockPlace(MiniGameArena arena, Player player, Block block) {
        if (!isActiveRoundStatus(arena) || isNightEliminated(arena, player)) {
            return HandlerEventResult.DENY;
        }

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion == null || !arenaRegion.contains(block.getLocation())) {
            return HandlerEventResult.DENY;
        }
        debugArenaEvent(arena, player.getName() + " placed " + block.getType().name()
            + " at " + formatBlockPosition(block.getLocation())
            + " while recording=" + api.worlds().changes(arena.world()).isRecording());
        return HandlerEventResult.ALLOW;
    }

    @Override
    public HandlerEventResult onBlockBreak(MiniGameArena arena, Player player, Block block) {
        if (!isActiveRoundStatus(arena) || isNightEliminated(arena, player)) {
            return HandlerEventResult.DENY;
        }

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion == null || !arenaRegion.contains(block.getLocation())) {
            return HandlerEventResult.DENY;
        }
        debugArenaEvent(arena, player.getName() + " broke " + block.getType().name()
            + " at " + formatBlockPosition(block.getLocation())
            + " while recording=" + api.worlds().changes(arena.world()).isRecording());
        return HandlerEventResult.ALLOW;
    }

    @Override
    public HandlerEventResult onEntityDamage(MiniGameArena arena, EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return HandlerEventResult.ALLOW;
        }
        if (!isActiveRoundStatus(arena)) {
            return HandlerEventResult.DENY;
        }
        if (isNightEliminated(arena, player)) {
            return HandlerEventResult.DENY;
        }

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player playerDamager = resolvePlayerDamager(byEntity);
            if (playerDamager != null && arena.hasPlayer(playerDamager)) {
                return HandlerEventResult.DENY;
            }
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
            || player.getHealth() - event.getFinalDamage() <= 0.0d) {
            handlePlayerDeath(arena, player);
            return HandlerEventResult.DENY;
        }

        return HandlerEventResult.ALLOW;
    }

    @Override
    public void onEntityExplode(MiniGameArena arena, EntityExplodeEvent event) {
        if (consumeBloodMoonPendingExplosion(arena)) {
            captureExplosionBlocks(arena, event.blockList());
        } else {
            event.blockList().clear();
        }
    }

    @Override
    public void onBlockExplode(MiniGameArena arena, BlockExplodeEvent event) {
        if (consumeBloodMoonPendingExplosion(arena)) {
            captureExplosionBlocks(arena, event.blockList());
        } else {
            event.blockList().clear();
        }
    }

    @Override
    public HandlerEventResult onPlayerDropItem(MiniGameArena arena, Player player, ItemStack item) {
        return isNightEliminated(arena, player) ? HandlerEventResult.DENY : HandlerEventResult.ALLOW;
    }

    @Override
    public void onArenaStatusChanged(MiniGameArena arena, MiniGameArena.ArenaStatus oldStatus, MiniGameArena.ArenaStatus newStatus) {
        if (newStatus == MiniGameArena.ArenaStatus.PREPARATION) {
            startRound(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.COOLDOWN || newStatus == MiniGameArena.ArenaStatus.ENDING) {
            if (newStatus == MiniGameArena.ArenaStatus.ENDING) {
                STEMCraft.getPlugin().entitlements().recordMinigameResult("nightfall", arena.getOccupants(),
                    arena.getPlayers().stream().map(Player::getUniqueId).toList());
            }
            endRound(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RESETTING) {
            debugArenaEvent(arena, "Entering RESETTING.");
            resetRound(arena);
            arena.set("selectedLobbyLocation", null);
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
        }
    }

    @Override
    public void onArenaCountdownTick(MiniGameArena arena, int secondsRemaining) {
        if (secondsRemaining <= 0) {
            return;
        }

        MiniGameArena.ArenaStatus status = arena.getStatus();
        if ((status == MiniGameArena.ArenaStatus.STARTING
            || status == MiniGameArena.ArenaStatus.PREPARATION
            || status == MiniGameArena.ArenaStatus.COOLDOWN
            || status == MiniGameArena.ArenaStatus.ENDING) && secondsRemaining <= 5) {
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
            arena.setStatus(MiniGameArena.ArenaStatus.PREPARATION, nightfall.prepSeconds(arena));
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.PREPARATION) {
            arena.setStatus(MiniGameArena.ArenaStatus.RUNNING);
            beginSunset(arena);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.COOLDOWN
            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        hideDownedPlayersFrom(arena, player);
        MiniGameArena.ArenaStatus status = arena.getStatus();
        if (status == MiniGameArena.ArenaStatus.WAITING || status == MiniGameArena.ArenaStatus.STARTING) {
            selectLobbyForMatch(arena);
        }
        if (status == MiniGameArena.ArenaStatus.PREPARATION || status == MiniGameArena.ArenaStatus.RUNNING) {
            initializeParticipantForRound(arena, player);
        }
        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, nightfall.startCountdownSeconds(arena));
        }
        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            return activeSpawn(arena);
        }
        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING
            || arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            return selectedLobbyForMatch(arena);
        }
        return assignedPreparationSpawn(arena, player);
    }

    @Override
    public Location onPlayerJoinSpectator(MiniGameArena arena, Player player) {
        hideDownedPlayersFrom(arena, player);
        Location spectatorSpawn = arena.getSpectatorSpawn();
        return spectatorSpawn != null ? spectatorSpawn : activeSpawn(arena);
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        debugArenaEvent(arena, player.getName() + " left arena while status=" + arena.getStatus().name()
            + ", remaining-players=" + arena.getPlayers().size());
        if (nightEliminatedPlayers(arena).remove(player.getUniqueId())) {
            showPlayerToArena(arena, player);
        }
        playerDropDueAt(arena).remove(player.getUniqueId());
        playerDropTargets(arena).remove(player.getUniqueId());
        pendingDeathRespawns(arena).remove(player.getUniqueId());
        prepSpawnAssignments(arena).remove(player.getUniqueId());
        updateArenaStateCounters(arena);
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            arena.setCountdown(0);
        } else if ((arena.getStatus() == MiniGameArena.ArenaStatus.PREPARATION
            || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING
            || arena.getStatus() == MiniGameArena.ArenaStatus.COOLDOWN
            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING)
            && arena.getPlayers().isEmpty()) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        } else if (isActiveRoundStatus(arena)) {
            checkForMatchEnd(arena);
        }
        if (arena.getPlayers().isEmpty()
            && (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING
                || arena.getStatus() == MiniGameArena.ArenaStatus.STARTING)) {
            arena.set("selectedLobbyLocation", null);
        }
    }

    private void selectLobbyForMatch(@NotNull MiniGameArena arena) {
        if (arena.get("selectedLobbyLocation", Location.class) != null) return;
        List<Location> locations = nightfall.lobbyLocations(arena);
        Location selected = locations.isEmpty() ? arena.getLobbySpawn()
            : locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
        if (selected != null) arena.set("selectedLobbyLocation", selected.clone());
    }

    private @Nullable Location selectedLobbyForMatch(@NotNull MiniGameArena arena) {
        Location selected = arena.get("selectedLobbyLocation", Location.class);
        return selected != null ? selected.clone() : arena.getLobbySpawn();
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        onPlayerLeaveArena(arena, player);
    }

    private void tickRuntime() {
        if (nightfall.minigame() == null) {
            return;
        }

        for (MiniGameArena arena : nightfall.minigame().arenas()) {
            if (!isActiveRoundStatus(arena)) {
                continue;
            }
            tickDrops(arena);
            if (isNightPhase(arena)) {
                spawnZombieWave(arena);
            }
            tickBurningZombieFire(arena);
            if (nightfall.isBloodMoonActive(arena)) {
                tickBloodMoonUtilities(arena);
            }
        }
    }

    private void tickTimeCycle() {
        if (nightfall.minigame() == null) {
            return;
        }

        for (MiniGameArena arena : nightfall.minigame().arenas()) {
            if (!isActiveRoundStatus(arena)) {
                continue;
            }
            tickNightCycle(arena);
        }
    }

    private void tickDrops(@NotNull MiniGameArena arena) {
        Map<Integer, List<Material>> dropItems = nightfall.dropItems(arena);
        List<Player> activePlayers = dropGroupRepresentatives(activeSurvivors(arena), nightfall.dropGroupDistance(arena));
        int maxActiveDrops = nightfall.dropMaxActiveItems(arena);
        if (dropItems.isEmpty() || activePlayers.isEmpty() || maxActiveDrops <= 0 || !nightfall.dropsEnabled(arena)) {
            return;
        }

        long now = System.currentTimeMillis();
        Map<UUID, Long> dueAt = playerDropDueAt(arena);
        Map<UUID, Location> dropTargets = playerDropTargets(arena);
        int activeDropCount = arena.countActiveSupplyDrops();
        Set<UUID> activePlayerIds = new LinkedHashSet<>();
        Set<String> reservedTargets = new HashSet<>();
        for (Player player : activePlayers) {
            activePlayerIds.add(player.getUniqueId());
        }
        dueAt.keySet().removeIf(playerId -> !activePlayerIds.contains(playerId));
        dropTargets.keySet().removeIf(playerId -> !activePlayerIds.contains(playerId));

        for (Player player : activePlayers) {
            UUID playerId = player.getUniqueId();
            long due = dueAt.getOrDefault(playerId, now + randomDropDelayMillis(arena));
            if (!dueAt.containsKey(playerId)) {
                dueAt.put(playerId, due);
            }

            Block block = resolveDropTargetBlock(arena, player, reservedTargets);
            if (block != null) {
                reservedTargets.add(dropTargetKey(block));
            }

            if (activeDropCount >= maxActiveDrops) {
                dueAt.put(playerId, now + 1000L);
                continue;
            }

            if (now < due) {
                continue;
            }

            if (block == null) {
                dueAt.put(playerId, now + 1000L);
                continue;
            }

            List<ItemStack> loot = buildDropLoot(arena, dropItems);
            if (loot.isEmpty()) {
                dueAt.put(playerId, now + 1000L);
                continue;
            }

            if (spawnSupplyDrop(arena, block.getLocation(), loot)) {
                activeDropCount++;
                announceSupplyDrop(arena, block.getLocation());
            }
            dueAt.put(playerId, now + randomDropDelayMillis(arena));
        }
    }

    /**
     * Returns one player per connected proximity group. If A is close to B and B is close to C,
     * all three share one drop even when A and C are farther apart than the configured distance.
     */
    private @NotNull List<Player> dropGroupRepresentatives(@NotNull List<Player> players, int groupDistance) {
        List<Player> representatives = new ArrayList<>();
        Set<UUID> assigned = new HashSet<>();
        double maxDistanceSquared = (double) groupDistance * groupDistance;

        for (Player seed : players) {
            if (!assigned.add(seed.getUniqueId())) {
                continue;
            }
            representatives.add(seed);
            List<Player> pending = new ArrayList<>();
            pending.add(seed);
            for (int index = 0; index < pending.size(); index++) {
                Player current = pending.get(index);
                for (Player candidate : players) {
                    if (assigned.contains(candidate.getUniqueId())
                            || !sameWorld(current.getLocation(), candidate.getLocation())
                            || dropHorizontalDistanceSquared(current.getLocation(), candidate.getLocation()) > maxDistanceSquared) {
                        continue;
                    }
                    assigned.add(candidate.getUniqueId());
                    pending.add(candidate);
                }
            }
        }
        return representatives;
    }

    private void tickNightCycle(@NotNull MiniGameArena arena) {
        if (arena.get("daylightLocked", Boolean.class, false)) {
            arena.set("wasNight", false);
            arena.set("wasSunsetOrNight", false);
            arena.set("bloodMoonActive", false);
            arena.set("timeSpeedCarry", 0.0d);
            updateArenaStateCounters(arena);
            return;
        }

        World world = arena.world();
        long time = applyConfiguredTimeSpeed(arena, world);
        boolean isSunsetOrNight = time >= SUNSET_TIME && time < DAY_START_TIME;
        boolean wasSunsetOrNight = arena.get("wasSunsetOrNight", Boolean.class, false);
        boolean isNight = time >= NIGHT_START_TIME && time < DAY_START_TIME;
        boolean wasNight = arena.get("wasNight", Boolean.class, false);

        if (isSunsetOrNight && !wasSunsetOrNight) {
            startSunsetWindow(arena);
        } else if (!isSunsetOrNight && wasSunsetOrNight) {
            endSunsetWindow(arena);
        }

        if (isNight && !wasNight) {
            startNight(arena);
        } else if (!isNight && wasNight) {
            endNight(arena);
        }

        arena.set("wasSunsetOrNight", isSunsetOrNight);
        arena.set("wasNight", isNight);
        boolean acceleratingTowardDawn = isNight
            && (arena.get("nightFastForwarding", Boolean.class, false) || shouldAccelerateToDawn(arena));
        if (!acceleratingTowardDawn) {
            announceTimeMilestones(arena, time);
        }
        if (isNight) {
            if (shouldAccelerateToDawn(arena)) {
                accelerateNightTowardDawn(arena, time);
            } else {
                arena.set("nightFastForwarding", false);
            }
        }
        updateArenaStateCounters(arena);
    }

    private void startSunsetWindow(@NotNull MiniGameArena arena) {
        boolean bloodMoonActive = shouldTriggerBloodMoon(arena);
        arena.set("bloodMoonActive", bloodMoonActive);
        if (bloodMoonActive) {
            broadcastPlainToOccupants(arena, nightfall.bloodMoonStartMessage());
            playSoundToOccupants(arena, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.75f, 0.7f);
        }
    }

    private void endSunsetWindow(@NotNull MiniGameArena arena) {
        arena.set("bloodMoonActive", false);
    }

    private boolean shouldTriggerBloodMoon(@NotNull MiniGameArena arena) {
        int chancePercent = nightfall.bloodMoonChancePercent(arena);
        return chancePercent > 0 && ThreadLocalRandom.current().nextInt(100) < chancePercent;
    }

    private void startNight(@NotNull MiniGameArena arena) {
        int night = arena.get("currentNight", Integer.class, 0) + 1;
        int spawns = nightfall.zombieBaseNightlySpawns(arena)
            + (Math.max(0, night - 1) * nightfall.zombieNightlySpawnIncrease(arena));
        if (nightfall.isBloodMoonActive(arena)) {
            spawns = (int) Math.ceil(spawns * nightfall.bloodMoonZombieSpawnMultiplier(arena));
        }

        arena.set("currentNight", night);
        arena.set("remainingNightSpawns", Math.max(0, spawns));
        arena.set("nextWaveAt", System.currentTimeMillis());
        arena.set("nightFastForwarding", false);
        arena.set("timeSpeedCarry", 0.0d);
        arena.set("bloodMoonCometsLaunched", 0);

        broadcastToOccupants(arena, nightfall.nightStartMessage(night));
        playSoundToOccupants(arena, Sound.ENTITY_ZOMBIE_AMBIENT, 0.75f, 0.9f);
        tryLaunchBloodMoonComet(arena, night);
        updateArenaStateCounters(arena);
    }

    private void tryLaunchBloodMoonComet(@NotNull MiniGameArena arena, int night) {
        BloodMoonCometSettings settings = nightfall.bloodMoonComets(arena);
        if (!nightfall.isBloodMoonActive(arena) || !settings.enabled() || night < settings.startNight()
            || settings.maximumPerNight() <= 0) {
            return;
        }

        int launched = arena.get("bloodMoonCometsLaunched", Integer.class, 0);
        int chance = Math.min(settings.maximumChancePercent(), settings.chancePercent()
            + Math.max(0, night - settings.startNight()) * settings.chanceIncreasePerNight());
        while (launched < settings.maximumPerNight()
            && chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance) {
            Vector direction = randomHorizontalDirection();
            Location impact = findBloodMoonCometImpact(arena, settings, direction);
            if (impact == null) {
                break;
            }

            String from = cardinalDirection(direction.clone().multiply(-1.0d));
            broadcastPlainToOccupants(arena, "A treasure-bearing comet is approaching from the " + from
                + " — take cover!");
            playSoundToOccupants(arena, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.65f);
            api.comets().launch(impact, direction, settings.loot().toArray(CometLoot[]::new));
            launched++;
            arena.set("bloodMoonCometsLaunched", launched);
        }
    }

    private @Nullable Location findBloodMoonCometImpact(@NotNull MiniGameArena arena,
                                                         @NotNull BloodMoonCometSettings settings,
                                                         @NotNull Vector direction) {
        List<Player> players = activeSurvivors(arena);
        if (players.isEmpty()) return null;

        double centerX = players.stream().mapToDouble(player -> player.getLocation().getX()).average().orElse(0.0d);
        double centerZ = players.stream().mapToDouble(player -> player.getLocation().getZ()).average().orElse(0.0d);
        World world = arena.world();
        for (int attempt = 0; attempt < RANDOM_LOCATION_ATTEMPTS; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0d);
            double distance = settings.minimumPlayerDistance() == settings.maximumPlayerDistance()
                ? settings.minimumPlayerDistance()
                : ThreadLocalRandom.current().nextDouble(settings.minimumPlayerDistance(),
                    settings.maximumPlayerDistance());
            int x = (int) Math.floor(centerX + Math.cos(angle) * distance);
            int z = (int) Math.floor(centerZ + Math.sin(angle) * distance);
            Location impact = world.getHighestBlockAt(x, z).getLocation().add(0.5d, 1.0d, 0.5d);
            if (isSafeCometImpact(arena, impact, direction, settings, players)) return impact;
        }
        return null;
    }

    private boolean isSafeCometImpact(@NotNull MiniGameArena arena, @NotNull Location impact,
                                      @NotNull Vector direction, @NotNull BloodMoonCometSettings settings,
                                      @NotNull List<Player> players) {
        double minimumSquared = settings.minimumPlayerDistance() * (double) settings.minimumPlayerDistance();
        double maximumSquared = settings.maximumPlayerDistance() * (double) settings.maximumPlayerDistance();
        boolean nearPlayers = false;
        for (Player player : players) {
            double distance = horizontalDistanceSquared(impact, player.getLocation());
            if (distance < minimumSquared) return false;
            nearPlayers |= distance <= maximumSquared;
        }
        if (!nearPlayers || !awayFromProtectedLocations(arena, impact, settings.arenaEdgeBuffer())) return false;

        Vector lateral = new Vector(-direction.getZ(), 0.0d, direction.getX()).normalize();
        for (int distance = 0; distance <= settings.pathSafetyLength(); distance += 5) {
            Location center = impact.clone().add(direction.clone().multiply(distance));
            for (int side : new int[] {-settings.arenaEdgeBuffer(), 0, settings.arenaEdgeBuffer()}) {
                Location sample = center.clone().add(lateral.clone().multiply(side));
                sample.setY(arena.world().getHighestBlockYAt(sample.getBlockX(), sample.getBlockZ()) + 1.0d);
                if (!insideArena(arena, sample)) return false;
            }
        }
        return true;
    }

    private boolean awayFromProtectedLocations(@NotNull MiniGameArena arena, @NotNull Location impact, int buffer) {
        double bufferSquared = buffer * (double) buffer;
        List<Location> protectedLocations = new ArrayList<>(nightfall.generatorLocations(arena));
        Location playSpawn = nightfall.playSpawn(arena);
        if (playSpawn != null) protectedLocations.add(playSpawn);
        Location spectatorSpawn = arena.getSpectatorSpawn();
        if (spectatorSpawn != null) protectedLocations.add(spectatorSpawn);
        return protectedLocations.stream().noneMatch(location -> sameWorld(impact, location)
            && horizontalDistanceSquared(impact, location) < bufferSquared);
    }

    private Vector randomHorizontalDirection() {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0d);
        return new Vector(Math.cos(angle), 0.0d, Math.sin(angle));
    }

    private String cardinalDirection(@NotNull Vector direction) {
        double angle = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        return new String[] {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"}
            [(int) Math.round((angle + 360.0d) / 45.0d) % 8];
    }

    private void endNight(@NotNull MiniGameArena arena) {
        arena.set("remainingNightSpawns", 0);
        arena.set("nextWaveAt", 0L);
        arena.set("nightFastForwarding", false);
        arena.set("timeSpeedCarry", 0.0d);
        igniteTrackedZombiesAtSunrise(arena);
        respawnNightEliminatedPlayers(arena);
        resetTimeAnnouncements(arena);

        if (!arena.getPlayers().isEmpty()) {
            broadcastToOccupants(arena, nightfall.sunriseMessage(arena.get("currentNight", Integer.class, 0) + 1));
            playTimeAnnouncementSound(arena);
            playSoundToOccupants(arena, Sound.ENTITY_CHICKEN_AMBIENT, 0.8f, 0.85f);
        }
        updateArenaStateCounters(arena);
    }

    private void spawnZombieWave(@NotNull MiniGameArena arena) {
        int remaining = arena.get("remainingNightSpawns", Integer.class, 0);
        List<Player> targets = activeSurvivors(arena);
        if (remaining <= 0 || targets.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextWaveAt = arena.get("nextWaveAt", Long.class, 0L);
        if (now < nextWaveAt) {
            return;
        }

        Collections.shuffle(targets);

        int toSpawn = Math.min(remaining, nightfall.zombieWaveSize(arena));
        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            Location spawnLocation = findSpawnLocationNearPlayer(arena, target);
            if (spawnLocation == null) {
                continue;
            }

            Zombie zombie = spawnLocation.getWorld().spawn(spawnLocation, Zombie.class, entity -> configureManagedZombie(arena, entity, target));
            managedZombies(arena).add(zombie.getUniqueId());
            spawned++;
        }

        arena.set("remainingNightSpawns", Math.max(0, remaining - spawned));
        arena.set("nextWaveAt", now + (nightfall.zombieWaveIntervalSeconds(arena) * 1000L));
        updateArenaStateCounters(arena);
    }

    private void tickBloodMoonUtilities(@NotNull MiniGameArena arena) {
        WorldChangeSession session = api.worlds().changes(arena.world());
        for (UUID entityId : new LinkedHashSet<>(managedZombies(arena))) {
            Entity entity = arena.world().getEntity(entityId);
            if (!(entity instanceof Zombie zombie) || zombie.isDead() || !zombie.isValid()) {
                continue;
            }

            clearExpiredZombieUtilityItem(arena, zombie);

            if (tryPrimeTntCarrier(arena, zombie)) {
                continue;
            }

            int night = Math.max(1, nightfall.currentNight(arena));
            BloodMoonEscalation escalation = nightfall.bloodMoonEscalation(arena);
            if (night >= escalation.bucketStartNight() && tryRemoveWaterTrap(arena, session, zombie, escalation, night)) {
                continue;
            }

            if (tryNeutralizeNearbyLava(arena, session, zombie)) {
                showZombieUtilityItem(arena, zombie, Material.WATER_BUCKET);
                continue;
            }

            Block origin = zombie.getLocation().getBlock();
            if (tryOpenDoor(session, origin)) {
                continue;
            }

            boolean openedDoor = false;
            for (BlockFace face : CARDINAL_FACES) {
                if (tryOpenDoor(session, origin.getRelative(face))) {
                    openedDoor = true;
                    break;
                }
            }

            if (openedDoor) {
                continue;
            }

            if (night >= escalation.axeStartNight() && tryBreakWoodenBarrier(arena, session, zombie)) {
                showZombieUtilityItem(arena, zombie, Material.IRON_AXE);
                continue;
            }

            if (night >= escalation.builderStartNight()) {
                Material buildingMaterial = tryBuildTowardTarget(arena, session, zombie);
                if (buildingMaterial != null) showZombieUtilityItem(arena, zombie, buildingMaterial);
            }
        }
    }

    private boolean tryRemoveWaterTrap(@NotNull MiniGameArena arena, @NotNull WorldChangeSession session,
                                       @NotNull Zombie zombie, @NotNull BloodMoonEscalation escalation, int night) {
        Block origin = zombie.getLocation().getBlock();
        if (!origin.isLiquid() || !isZombieStuck(arena, zombie)) {
            return false;
        }
        int radius = night >= escalation.spongeStartNight() ? Math.max(1, escalation.spongeRadius()) : 0;
        Material held = radius > 0 ? Material.SPONGE : Material.BUCKET;
        int changed = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = origin.getRelative(x, y, z);
                    if (block.getType() != Material.WATER || !insideArena(arena, block.getLocation())) {
                        continue;
                    }
                    session.captureBlock(block);
                    block.setType(Material.AIR, true);
                    changed++;
                    if (radius == 0) {
                        showZombieUtilityItem(arena, zombie, held);
                        return true;
                    }
                }
            }
        }
        if (changed > 0) {
            showZombieUtilityItem(arena, zombie, held);
        }
        return changed > 0;
    }

    private boolean tryBreakWoodenBarrier(@NotNull MiniGameArena arena, @NotNull WorldChangeSession session,
                                          @NotNull Zombie zombie) {
        if (!isZombieStuck(arena, zombie)) {
            return false;
        }
        Block origin = zombie.getLocation().getBlock();
        for (BlockFace face : CARDINAL_FACES) {
            Block block = origin.getRelative(face);
            if (!isBreakableWood(block.getType()) || !insideArena(arena, block.getLocation())) {
                continue;
            }
            session.captureBlock(block);
            block.setType(Material.AIR, true);
            return true;
        }
        return false;
    }

    private boolean insideArena(@NotNull MiniGameArena arena, @NotNull Location location) {
        SCRegion region = arena.get("arenaRegion", SCRegion.class);
        return region == null || region.contains(location);
    }

    private boolean isBreakableWood(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD")
            || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE") || name.endsWith("_TRAPDOOR")
            || (name.endsWith("_DOOR") && material != Material.IRON_DOOR);
    }

    private void tickBurningZombieFire(@NotNull MiniGameArena arena) {
        long now = System.currentTimeMillis();
        Map<UUID, Long> dueAt = burningZombieFireDueAt(arena);
        Set<UUID> tracked = managedZombies(arena);
        dueAt.keySet().removeIf(entityId -> !tracked.contains(entityId));

        for (UUID entityId : new LinkedHashSet<>(tracked)) {
            Entity entity = arena.world().getEntity(entityId);
            if (!(entity instanceof Zombie zombie) || zombie.isDead() || !zombie.isValid() || zombie.getFireTicks() <= 0) {
                dueAt.remove(entityId);
                continue;
            }
            if (bloodMoonTntZombies(arena).contains(entityId)) {
                detonateZombieTnt(arena, zombie);
                continue;
            }
            if (now < dueAt.getOrDefault(entityId, 0L)) {
                continue;
            }

            dueAt.put(entityId, now + BURNING_ZOMBIE_FIRE_INTERVAL_MILLIS);
            igniteBlockNearBurningZombie(arena, zombie);
            ignitePlayersNearBurningZombie(arena, zombie);
        }
    }

    private void igniteBlockNearBurningZombie(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        List<Block> candidates = new ArrayList<>();
        Block feet = zombie.getLocation().getBlock();
        candidates.add(feet);
        for (BlockFace face : CARDINAL_FACES) {
            candidates.add(feet.getRelative(face));
        }
        Collections.shuffle(candidates);

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        for (Block candidate : candidates) {
            if (!candidate.getType().isAir() || !candidate.getRelative(BlockFace.DOWN).getType().isSolid()) {
                continue;
            }
            if (arenaRegion != null && !arenaRegion.contains(candidate.getLocation())) {
                continue;
            }

            WorldChangeSession session = api.worlds().changes(arena.world());
            session.captureBlock(candidate);
            candidate.setType(Material.FIRE, true);
            return;
        }
    }

    private void ignitePlayersNearBurningZombie(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        for (Player player : activeSurvivors(arena)) {
            if (!sameWorld(zombie.getLocation(), player.getLocation())
                || zombie.getLocation().distanceSquared(player.getLocation()) > BURNING_ZOMBIE_PLAYER_RANGE_SQUARED
                || ThreadLocalRandom.current().nextInt(100) >= BURNING_ZOMBIE_PLAYER_IGNITE_CHANCE_PERCENT) {
                continue;
            }
            player.setFireTicks(Math.max(player.getFireTicks(), BURNING_ZOMBIE_PLAYER_FIRE_TICKS));
        }
    }

    private boolean tryOpenDoor(@NotNull WorldChangeSession session, @NotNull Block block) {
        Block doorBase = resolveDoorBase(block);
        if (doorBase == null || !(doorBase.getBlockData() instanceof Door door) || door.isOpen()) {
            return false;
        }

        session.captureBlock(doorBase);
        door.setOpen(true);
        doorBase.setBlockData(door, true);
        return true;
    }

    private Block resolveDoorBase(@NotNull Block block) {
        if (!(block.getBlockData() instanceof Door door) || !isBloodMoonDoorMaterial(block.getType())) {
            return null;
        }
        return door.getHalf() == Bisected.Half.TOP ? block.getRelative(BlockFace.DOWN) : block;
    }

    private boolean isBloodMoonDoorMaterial(@NotNull Material material) {
        return material != Material.IRON_DOOR && material.name().endsWith("_DOOR");
    }

    private boolean tryNeutralizeNearbyLava(@NotNull MiniGameArena arena,
                                            @NotNull WorldChangeSession session,
                                            @NotNull Zombie zombie) {
        Block lavaBlock = findNearbyLavaBlock(arena, zombie);
        if (lavaBlock == null) {
            return false;
        }

        session.captureBlock(lavaBlock);
        lavaBlock.setType(isLavaSourceBlock(lavaBlock) ? Material.OBSIDIAN : Material.COBBLESTONE, true);
        resetZombieStuckTracking(arena, zombie);
        return true;
    }

    private @Nullable Block findNearbyLavaBlock(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        Block origin = zombie.getLocation().getBlock();
        for (int yOffset = 1; yOffset >= -2; yOffset--) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    Block candidate = origin.getRelative(xOffset, yOffset, zOffset);
                    if (arenaRegion != null && !arenaRegion.contains(candidate.getLocation())) {
                        continue;
                    }
                    if (candidate.getType() == Material.LAVA) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isLavaSourceBlock(@NotNull Block block) {
        if (!(block.getBlockData() instanceof Levelled levelled)) {
            return true;
        }
        return levelled.getLevel() == 0;
    }

    private boolean tryPrimeTntCarrier(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        if (!bloodMoonTntZombies(arena).contains(zombie.getUniqueId())) {
            return false;
        }
        if (zombie.getFireTicks() > 0) {
            detonateZombieTnt(arena, zombie);
            return true;
        }

        LivingEntity target = zombie.getTarget();
        if (!(target instanceof Player player) || !arena.hasPlayer(player)) {
            return false;
        }

        if (!sameWorld(zombie.getLocation(), player.getLocation())) {
            return false;
        }

        double horizontalDistanceSquared = horizontalDistanceSquared(zombie.getLocation(), player.getLocation());
        if (horizontalDistanceSquared <= BLOOD_MOON_TNT_HORIZONTAL_TRIGGER_RANGE_SQUARED) {
            detonateZombieTnt(arena, zombie);
            return true;
        }

        if (horizontalDistanceSquared <= BLOOD_MOON_TNT_STUCK_BREACH_RANGE_SQUARED && isZombieStuck(arena, zombie)) {
            detonateZombieTnt(arena, zombie);
            return true;
        }

        return false;
    }

    private void detonateZombieTnt(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        Location explosionLocation = zombie.getLocation().clone().add(0.0d, 0.5d, 0.0d);
        UUID zombieId = zombie.getUniqueId();
        managedZombies(arena).remove(zombieId);
        cleanupTrackedZombie(arena, zombieId);
        zombie.remove();
        queueBloodMoonPendingExplosion(arena);
        arena.world().createExplosion(explosionLocation, BLOOD_MOON_TNT_EXPLOSION_POWER, false, true);
    }

    private @Nullable Material tryBuildTowardTarget(@NotNull MiniGameArena arena,
                                                     @NotNull WorldChangeSession session,
                                                     @NotNull Zombie zombie) {
        LivingEntity target = zombie.getTarget();
        if (!(target instanceof Player player) || !arena.hasPlayer(player)) {
            resetZombieStuckTracking(arena, zombie);
            return null;
        }

        if (!isZombieStuck(arena, zombie)) {
            return null;
        }

        Block placement = resolveZombieBuildPlacement(arena, zombie, player);
        if (placement == null) {
            return null;
        }

        Block source = findZombieBuildingSource(arena, zombie, placement);
        if (source == null) return null;
        Material material = source.getType();
        session.captureBlock(placement);
        if (ThreadLocalRandom.current().nextInt(100)
            < nightfall.bloodMoonBuilderSourceRemovalChancePercent(arena)) {
            session.captureBlock(source);
            source.setType(Material.AIR, true);
        }
        placement.setType(material, true);
        resetZombieStuckTracking(arena, zombie);
        return material;
    }

    private @Nullable Block findZombieBuildingSource(@NotNull MiniGameArena arena, @NotNull Zombie zombie,
                                                       @NotNull Block placement) {
        Block origin = zombie.getLocation().getBlock();
        List<Block> candidates = new ArrayList<>();
        candidates.add(origin.getRelative(BlockFace.DOWN));
        candidates.add(origin.getRelative(BlockFace.DOWN, 2));
        for (BlockFace face : CARDINAL_FACES) candidates.add(origin.getRelative(face).getRelative(BlockFace.DOWN));
        return candidates.stream().filter(block -> !block.equals(placement))
            .filter(block -> insideArena(arena, block.getLocation()))
            .filter(block -> isZombieBuildingMaterial(block.getType())).findFirst().orElse(null);
    }

    static boolean isZombieBuildingMaterial(@NotNull Material material) {
        return material == Material.DIRT || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT
            || material == Material.GRASS_BLOCK || material == Material.PODZOL || material == Material.MYCELIUM
            || material == Material.MUD;
    }

    private boolean isZombieStuck(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        Map<UUID, Location> lastLocations = bloodMoonZombieLastLocations(arena);
        Map<UUID, Integer> stuckTicks = bloodMoonZombieStuckTicks(arena);

        UUID entityId = zombie.getUniqueId();
        Location current = zombie.getLocation().clone();
        Location previous = lastLocations.put(entityId, current);
        if (previous == null) {
            stuckTicks.put(entityId, 0);
            return false;
        }

        if (!sameWorld(previous, current) || previous.distanceSquared(current) > BLOOD_MOON_STUCK_DISTANCE_SQUARED) {
            stuckTicks.put(entityId, 0);
            return false;
        }

        int updated = stuckTicks.getOrDefault(entityId, 0) + 1;
        stuckTicks.put(entityId, updated);
        return updated >= BLOOD_MOON_STUCK_TICKS_REQUIRED;
    }

    private @Nullable Block resolveZombieBuildPlacement(@NotNull MiniGameArena arena,
                                                        @NotNull Zombie zombie,
                                                        @NotNull Player target) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        Block origin = zombie.getLocation().getBlock();
        int stepX = Integer.compare(target.getLocation().getBlockX(), origin.getX());
        int stepZ = Integer.compare(target.getLocation().getBlockZ(), origin.getZ());
        if (stepX == 0 && stepZ == 0) {
            return null;
        }

        Block forward = origin.getRelative(stepX, 0, stepZ);
        Block support = forward.getRelative(BlockFace.DOWN);
        if (canPlaceZombieUtilityBlock(arenaRegion, support, forward, forward.getRelative(BlockFace.UP))) {
            return support;
        }

        if (target.getY() > zombie.getY() + 0.75d
            && canPlaceZombieUtilityBlock(arenaRegion, forward, forward.getRelative(BlockFace.UP), forward.getRelative(BlockFace.UP, 2))) {
            return forward;
        }

        return null;
    }

    private boolean canPlaceZombieUtilityBlock(@Nullable SCRegion arenaRegion,
                                               @NotNull Block placement,
                                               @NotNull Block feet,
                                               @NotNull Block head) {
        if (arenaRegion != null) {
            if (!arenaRegion.contains(placement.getLocation())
                || !arenaRegion.contains(feet.getLocation())
                || !arenaRegion.contains(head.getLocation())) {
                return false;
            }
        }

        if (!(placement.isPassable() || placement.isLiquid() || placement.getType().isAir())) {
            return false;
        }

        return (feet.isPassable() || feet.isLiquid()) && (head.isPassable() || head.isLiquid());
    }

    private void showZombieUtilityItem(@NotNull MiniGameArena arena, @NotNull Zombie zombie, @NotNull Material material) {
        var equipment = zombie.getEquipment();
        if (equipment == null) {
            return;
        }

        if (material.isAir()) {
            equipment.setItemInMainHand(null);
        } else {
            equipment.setItemInMainHand(new ItemStack(material));
            equipment.setItemInMainHandDropChance(0.0f);
        }
        bloodMoonZombieUtilityHoldUntil(arena).put(zombie.getUniqueId(), System.currentTimeMillis() + BLOOD_MOON_UTILITY_ITEM_HOLD_MILLIS);
    }

    private void clearExpiredZombieUtilityItem(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        Map<UUID, Long> holdUntil = bloodMoonZombieUtilityHoldUntil(arena);
        Long expiresAt = holdUntil.get(zombie.getUniqueId());
        if (expiresAt == null || System.currentTimeMillis() < expiresAt) {
            return;
        }

        var equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(null);
        }
        holdUntil.remove(zombie.getUniqueId());
    }

    private void resetZombieStuckTracking(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        bloodMoonZombieLastLocations(arena).put(zombie.getUniqueId(), zombie.getLocation().clone());
        bloodMoonZombieStuckTicks(arena).put(zombie.getUniqueId(), 0);
    }

    private boolean sameWorld(@NotNull Location first, @NotNull Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private double horizontalDistanceSquared(@NotNull Location first, @NotNull Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return (dx * dx) + (dz * dz);
    }

    private @Nullable Location findSpawnLocationNearPlayer(@NotNull MiniGameArena arena, @NotNull Player player) {
        World world = arena.world();
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        int minRadius = nightfall.zombieSpawnRadiusMin(arena);
        int maxRadius = nightfall.zombieSpawnRadiusMax(arena);

        for (int attempt = 0; attempt < RANDOM_LOCATION_ATTEMPTS; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0d, Math.PI * 2.0d);
            int radius = ThreadLocalRandom.current().nextInt(minRadius, maxRadius + 1);
            int x = (int) Math.floor(player.getX() + (Math.cos(angle) * radius));
            int z = (int) Math.floor(player.getZ() + (Math.sin(angle) * radius));

            Block ground = world.getHighestBlockAt(x, z);
            if (!ground.getType().isSolid()) {
                continue;
            }

            Location spawn = ground.getLocation().add(0.5d, 1.0d, 0.5d);
            if (arenaRegion != null && !arenaRegion.contains(spawn)) {
                continue;
            }

            Block feet = spawn.getBlock();
            Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }

            return spawn;
        }

        return null;
    }

    private @Nullable Block resolveDropTargetBlock(@NotNull MiniGameArena arena,
                                                   @NotNull Player player,
                                                   @NotNull Set<String> reservedTargets) {
        Map<UUID, Location> dropTargets = playerDropTargets(arena);
        UUID playerId = player.getUniqueId();
        Location target = dropTargets.get(playerId);
        Block dropBlock = target == null ? null : target.getBlock();

        if (dropBlock == null || needsNewDropTarget(arena, player, dropBlock, reservedTargets)) {
            dropBlock = findDropBlockNearPlayer(arena, player, reservedTargets);
            if (dropBlock == null) {
                dropTargets.remove(playerId);
                return null;
            }
            dropTargets.put(playerId, dropBlock.getLocation());
        }

        return dropBlock;
    }

    private boolean needsNewDropTarget(@NotNull MiniGameArena arena,
                                       @NotNull Player player,
                                       @NotNull Block dropBlock,
                                       @NotNull Set<String> reservedTargets) {
        Location target = dropBlock.getLocation();
        if (target.getWorld() == null || !target.getWorld().equals(player.getWorld())) {
            return true;
        }
        if (player.getLocation().distanceSquared(target) > (DROP_TARGET_RESET_DISTANCE * DROP_TARGET_RESET_DISTANCE)) {
            return true;
        }
        if (reservedTargets.contains(dropTargetKey(dropBlock))) {
            return true;
        }
        if (hasActiveSupplyDropAt(arena, dropBlock)) {
            return true;
        }
        return !isUsableDropBlock(arena, dropBlock);
    }

    private @Nullable Block findDropBlockNearPlayer(@NotNull MiniGameArena arena,
                                                    @NotNull Player player,
                                                    @NotNull Set<String> reservedTargets) {
        World world = arena.world();

        for (int attempt = 0; attempt < RANDOM_LOCATION_ATTEMPTS; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0d, Math.PI * 2.0d);
            int radius = ThreadLocalRandom.current().nextInt(DROP_RADIUS_MIN, DROP_RADIUS_MAX + 1);
            int x = (int) Math.floor(player.getX() + (Math.cos(angle) * radius));
            int z = (int) Math.floor(player.getZ() + (Math.sin(angle) * radius));

            Block ground = world.getHighestBlockAt(x, z);
            if (!ground.getType().isSolid()) {
                continue;
            }
            if (ground.isLiquid() || ground.isPassable()) {
                continue;
            }

            Block dropBlock = ground.getRelative(BlockFace.UP);
            if (reservedTargets.contains(dropTargetKey(dropBlock)) || hasActiveSupplyDropAt(arena, dropBlock)) {
                continue;
            }
            if (isUsableDropBlock(arena, dropBlock)) {
                return dropBlock;
            }
        }

        return null;
    }

    private boolean hasActiveSupplyDropAt(@NotNull MiniGameArena arena, @NotNull Block dropBlock) {
        if (dropBlock.getType() == Material.CHEST) {
            return true;
        }

        Location center = dropBlock.getLocation().clone().add(0.5d, 0.5d, 0.5d);
        return !arena.world().getNearbyEntities(
            center,
            0.75d,
            128.0d,
            0.75d,
            entity -> entity instanceof org.bukkit.entity.BlockDisplay
        ).isEmpty();
    }

    private @NotNull String dropTargetKey(@NotNull Block dropBlock) {
        return dropBlock.getWorld().getUID() + ":" + dropBlock.getX() + ":" + dropBlock.getY() + ":" + dropBlock.getZ();
    }

    private boolean isUsableDropBlock(@NotNull MiniGameArena arena, @NotNull Block dropBlock) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion != null && !arenaRegion.contains(dropBlock.getLocation())) {
            return false;
        }

        Block ground = dropBlock.getRelative(BlockFace.DOWN);
        if (!ground.getType().isSolid()) {
            return false;
        }

        Block highest = arena.world().getHighestBlockAt(dropBlock.getX(), dropBlock.getZ());
        if (highest.getY() != ground.getY()) {
            return false;
        }

        if (!(dropBlock.getType().isAir() || dropBlock.isPassable() || dropBlock.isLiquid())) {
            return false;
        }
        if (dropBlock.isLiquid()) {
            return false;
        }

        Block above = dropBlock.getRelative(BlockFace.UP);
        return above.isPassable() && !above.isLiquid();
    }

    private void startRound(@NotNull MiniGameArena arena) {
        clearTrackedZombies(arena);
        resetRuntimeState(arena);
        seedDropTimers(arena);
        storeWorldSettings(arena);
        applyRoundWorldSettings(arena);
        startRecording(arena);

        for (Player player : arena.getPlayers()) {
            initializeParticipantForRound(arena, player);
        }

        for (Player spectator : arena.getSpectators()) {
            Location spectatorSpawn = arena.getSpectatorSpawn();
            if (spectatorSpawn != null) {
                spectator.teleport(spectatorSpawn);
            }
        }

        broadcastToOccupants(arena, nightfall.roundStartMessage());
        playSoundToOccupants(arena, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.15f);
        updateArenaStateCounters(arena);
    }

    private void initializeParticipantForRound(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer miniGamePlayer = arena.getPlayer(player);
        if (miniGamePlayer == null) {
            return;
        }

        miniGamePlayer.setScore(0);
        miniGamePlayer.setKills(0);
        miniGamePlayer.setDeaths(0);
        miniGamePlayer.set("livesRemaining", 1);
        nightEliminatedPlayers(arena).remove(player.getUniqueId());
        pendingDeathRespawns(arena).remove(player.getUniqueId());
        playerDropTargets(arena).remove(player.getUniqueId());
        if (nightfall.dropsEnabled(arena)) {
            playerDropDueAt(arena).put(
                player.getUniqueId(), System.currentTimeMillis() + randomDropDelayMillis(arena));
        } else {
            playerDropDueAt(arena).remove(player.getUniqueId());
        }
        prepareParticipantForRound(player);
    }

    private void endRound(@NotNull MiniGameArena arena) {
        broadcastToOccupants(arena, nightfall.roundEndMessage(nightfall.endingSeconds(arena)));
        playSoundToOccupants(arena, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.9f);
    }

    private void resetRound(@NotNull MiniGameArena arena) {
        debugArenaEvent(arena, "Resetting round. Rolling world back now.");
        clearTrackedZombies(arena);
        stopRecording(arena);
        rollbackWorld(arena);
        restoreWorldSettings(arena);
        resetRuntimeState(arena);
        arena.removeAllOccupants();
    }

    private void beginSunset(@NotNull MiniGameArena arena) {
        if (!arena.get("daylightLocked", Boolean.class, false)) {
            return;
        }

        arena.set("daylightLocked", false);
        arena.set("wasNight", false);
        arena.set("remainingNightSpawns", 0);
        arena.set("nextWaveAt", 0L);
        arena.set("timeSpeedCarry", 0.0d);
        arena.set("wasSunsetOrNight", false);
        arena.set("bloodMoonActive", false);
        resetTimeAnnouncements(arena);

        api.worlds().setSetting(arena.world(), "time", "unset");

        broadcastToOccupants(arena, nightfall.dayUnlockedMessage());
        playSoundToOccupants(arena, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
        updateArenaStateCounters(arena);
    }

    private void handlePlayerDeath(@NotNull MiniGameArena arena, @NotNull Player player) {
        handlePlayerDeath(arena, player, true);
    }

    private void handlePlayerDeath(@NotNull MiniGameArena arena, @NotNull Player player, boolean immediateTransition) {
        MiniGamePlayer miniGamePlayer = arena.getPlayer(player);
        if (miniGamePlayer == null || isNightEliminated(arena, player)) {
            return;
        }

        miniGamePlayer.addDeath();
        Location deathLocation = player.getLocation().clone();
        Location downedLocation = resolveDownedLocation(arena, deathLocation);
        dropInventory(player, deathLocation);
        markPlayerDowned(arena, player, miniGamePlayer);
        broadcastToOccupants(arena, nightfall.playerDownedMessage(player));
        if (!immediateTransition) {
            pendingDeathRespawns(arena).put(player.getUniqueId(), downedLocation.clone());
            return;
        }

        prepareDownedParticipant(player);
        PlayerUtil.teleport(player, downedLocation);
        player.setHealth(PlayerUtil.getMaxHealth(player));
    }

    private void markPlayerDowned(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull MiniGamePlayer miniGamePlayer) {
        nightEliminatedPlayers(arena).add(player.getUniqueId());
        hidePlayerFromArena(arena, player);
        miniGamePlayer.set("livesRemaining", 0);
        updateArenaStateCounters(arena);
        checkForMatchEnd(arena);
    }

    private void checkForMatchEnd(@NotNull MiniGameArena arena) {
        if (!isActiveRoundStatus(arena)) {
            return;
        }
        if (arena.getPlayers().isEmpty() || activeSurvivorCount(arena) <= 0) {
            arena.setStatus(MiniGameArena.ArenaStatus.COOLDOWN, nightfall.endingSeconds(arena));
        }
    }

    private void dropInventory(@NotNull Player player, @NotNull Location location) {
        World world = location.getWorld();
        if (world == null) {
            clearInventory(player);
            return;
        }

        dropItems(world, location, player.getInventory().getStorageContents());
        dropItems(world, location, player.getInventory().getArmorContents());
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!offHand.getType().isAir()) {
            world.dropItemNaturally(location, offHand.clone());
        }
        clearInventory(player);
    }

    private void dropItems(@NotNull World world, @NotNull Location location, ItemStack @Nullable [] contents) {
        if (contents == null) {
            return;
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            world.dropItemNaturally(location, item.clone());
        }
    }

    private void clearInventory(@NotNull Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[0]);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void prepareParticipantForRound(@NotNull Player player) {
        clearInventory(player);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.closeInventory();
        clearSpectatorTargetIfNeeded(player);
        player.setGameMode(GameMode.SURVIVAL);
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
    }

    private void prepareDownedParticipant(@NotNull Player player) {
        clearInventory(player);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.closeInventory();
        clearSpectatorTargetIfNeeded(player);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setLevel(0);
        player.setExp(0.0f);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
    }

    private void clearSpectatorTargetIfNeeded(@NotNull Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
        }
    }

    private void resetRuntimeState(@NotNull MiniGameArena arena) {
        restoreDownedPlayerVisibility(arena);
        arena.set("daylightLocked", true);
        arena.set("wasNight", false);
        arena.set("currentNight", 0);
        arena.set("remainingNightSpawns", 0);
        arena.set("nextWaveAt", 0L);
        arena.set("timeAnnouncementIndex", 0);
        arena.set("nightFastForwarding", false);
        arena.set("timeSpeedCarry", 0.0d);
        arena.set("wasSunsetOrNight", false);
        arena.set("bloodMoonActive", false);
        arena.clearAllSupplyDrops();
        playerDropDueAt(arena).clear();
        playerDropTargets(arena).clear();
        nightEliminatedPlayers(arena).clear();
        pendingDeathRespawns(arena).clear();
        bloodMoonZombieLastLocations(arena).clear();
        bloodMoonZombieStuckTicks(arena).clear();
        bloodMoonZombieUtilityHoldUntil(arena).clear();
        bloodMoonTntZombies(arena).clear();
        burningZombieFireDueAt(arena).clear();
        arena.set("bloodMoonPendingExplosions", 0);
        arena.set("bloodMoonCometsLaunched", 0);
        updateArenaStateCounters(arena);
    }

    private void seedDropTimers(@NotNull MiniGameArena arena) {
        Map<UUID, Long> dueAt = playerDropDueAt(arena);
        dueAt.clear();
        if (!nightfall.dropsEnabled(arena)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : activeSurvivors(arena)) {
            dueAt.put(player.getUniqueId(), now + randomDropDelayMillis(arena));
        }
    }

    private void storeWorldSettings(@NotNull MiniGameArena arena) {
        String timeSetting = api.worlds().getSetting(arena.world(), "time");
        String weatherSetting = api.worlds().getSetting(arena.world(), "weather");
        String normalizedTimeSetting = normalizeSetting(timeSetting);
        String normalizedWeatherSetting = normalizeSetting(weatherSetting);
        arena.set("savedTimeSetting", normalizedTimeSetting);
        arena.set("savedWeatherSetting", normalizedWeatherSetting);
        nightfall.persistRecoveryState(arena, true, normalizedTimeSetting, normalizedWeatherSetting);
    }

    private void applyRoundWorldSettings(@NotNull MiniGameArena arena) {
        api.worlds().setSetting(arena.world(), "weather", "clear always");
        api.worlds().setSetting(arena.world(), "time", "noon always");
        arena.world().setTime(NOON_TIME);
        arena.world().setStorm(false);
        arena.world().setThundering(false);
    }

    private void restoreWorldSettings(@NotNull MiniGameArena arena) {
        api.worlds().setSetting(arena.world(), "time", normalizeSetting(arena.get("savedTimeSetting", String.class, "unset")));
        api.worlds().setSetting(arena.world(), "weather", normalizeSetting(arena.get("savedWeatherSetting", String.class, "unset")));
        arena.set("pendingWorldRollback", false);
        arena.set("savedTimeSetting", "unset");
        arena.set("savedWeatherSetting", "unset");
        nightfall.clearRecoveryState(arena);
    }

    private @NotNull String normalizeSetting(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "unset";
        }
        return value;
    }

    private void startRecording(@NotNull MiniGameArena arena) {
        WorldChangeSession session = api.worlds().changes(arena.world());
        session.stop();
        session.clear();
        session.start();
        debugArenaEvent(arena, "World change recording started. recording=" + session.isRecording());
    }

    private void stopRecording(@NotNull MiniGameArena arena) {
        WorldChangeSession session = api.worlds().changes(arena.world());
        session.stop();
        debugArenaEvent(arena, "World change recording stopped. recording=" + session.isRecording());
    }

    private void rollbackWorld(@NotNull MiniGameArena arena) {
        WorldChangeSession session = api.worlds().changes(arena.world());
        debugArenaEvent(arena, "Rolling world back. recording=" + session.isRecording());
        session.rollback(false);
        session.clear();
        debugArenaEvent(arena, "World rollback complete.");
    }

    private long randomDropDelayMillis(@NotNull MiniGameArena arena) {
        int min = nightfall.dropMinSeconds(arena);
        int max = nightfall.dropMaxSeconds(arena);
        return ThreadLocalRandom.current().nextLong(min, max + 1L) * 1000L;
    }

    private boolean spawnSupplyDrop(@NotNull MiniGameArena arena, @NotNull Location target, @NotNull List<ItemStack> loot) {
        if (loot.isEmpty()) {
            return false;
        }
        arena.spawnSupplyDropCrate(loot, target);
        return true;
    }

    private void announceSupplyDrop(@NotNull MiniGameArena arena, @NotNull Location dropLocation) {
        for (Player player : arena.getPlayers()) {
            if (!sameWorld(player.getLocation(), dropLocation)) {
                continue;
            }
            if (dropHorizontalDistanceSquared(player.getLocation(), dropLocation) > DROP_ANNOUNCEMENT_RANGE_SQUARED) {
                continue;
            }
            arena.info(player, supplyDropHint(player, dropLocation));
        }
    }

    private void configureManagedZombie(@NotNull MiniGameArena arena, @NotNull Zombie zombie, @NotNull Player target) {
        zombie.setCanPickupItems(false);
        if (shouldSpawnBabyZombie(arena)) {
            zombie.setBaby();
        } else {
            zombie.setAdult();
        }
        zombie.setTarget(target);
        applyNightlyZombieHealth(arena, zombie);
        applyBloodMoonEscalation(arena, zombie);
        if (shouldSpawnTntZombie(arena)) {
            markAsTntZombie(arena, zombie);
        }
    }

    private void applyBloodMoonEscalation(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        if (!nightfall.isBloodMoonActive(arena)) {
            return;
        }
        BloodMoonEscalation escalation = nightfall.bloodMoonEscalation(arena);
        if (nightfall.currentNight(arena) < escalation.knockbackStartNight()) {
            return;
        }
        var resistance = zombie.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (resistance != null) {
            resistance.setBaseValue(Math.clamp(escalation.knockbackResistance(), 0.0d, 1.0d));
        }
    }

    private void applyNightlyZombieHealth(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        var maxHealth = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        int night = Math.max(1, arena.get("currentNight", Integer.class, 1));
        double multiplier = Math.pow(nightfall.zombieNightlyHealthMultiplier(arena), Math.max(0, night - 1));
        double scaledMaxHealth = maxHealth.getBaseValue() * multiplier;
        maxHealth.setBaseValue(scaledMaxHealth);
        zombie.setHealth(scaledMaxHealth);
    }

    private boolean shouldSpawnBabyZombie(@NotNull MiniGameArena arena) {
        if (!nightfall.isBloodMoonActive(arena)) {
            return false;
        }

        return ThreadLocalRandom.current().nextInt(100) < nightfall.bloodMoonBabyZombieChancePercent(arena);
    }

    private boolean shouldSpawnTntZombie(@NotNull MiniGameArena arena) {
        if (!nightfall.isBloodMoonActive(arena)) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < nightfall.bloodMoonTntZombieChancePercentForNight(arena);
    }

    private void markAsTntZombie(@NotNull MiniGameArena arena, @NotNull Zombie zombie) {
        bloodMoonTntZombies(arena).add(zombie.getUniqueId());
        var equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setItemInOffHand(new ItemStack(Material.TNT));
            equipment.setItemInOffHandDropChance(0.0f);
        }
    }

    private long applyConfiguredTimeSpeed(@NotNull MiniGameArena arena, @NotNull World world) {
        double multiplier = configuredTimeSpeedMultiplier(arena, world.getTime());
        if (multiplier <= 1.0d) {
            arena.set("timeSpeedCarry", 0.0d);
            return world.getTime();
        }

        double carry = arena.get("timeSpeedCarry", Double.class, 0.0d);
        double totalExtraTicks = (multiplier - 1.0d) + carry;
        long wholeTicks = (long) Math.floor(totalExtraTicks);
        arena.set("timeSpeedCarry", totalExtraTicks - wholeTicks);
        if (wholeTicks > 0L) {
            world.setTime(world.getTime() + wholeTicks);
        }
        return world.getTime();
    }

    private double configuredTimeSpeedMultiplier(@NotNull MiniGameArena arena, long time) {
        return isNightTime(time)
            ? nightfall.nightTimeSpeedMultiplier(arena)
            : nightfall.dayTimeSpeedMultiplier(arena);
    }

    private @Nullable Material pickDropItem(@NotNull Map<Integer, List<Material>> dropItems) {
        if (dropItems.isEmpty()) {
            return null;
        }

        int roll = ThreadLocalRandom.current().nextInt(1, 101);
        Integer selectedThreshold = null;
        for (Integer threshold : dropItems.keySet().stream().sorted().toList()) {
            if (threshold == null) {
                continue;
            }
            if (threshold >= roll) {
                selectedThreshold = threshold;
                break;
            }
        }

        if (selectedThreshold == null) {
            selectedThreshold = dropItems.keySet().stream()
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        }
        if (selectedThreshold == null) {
            return null;
        }

        List<Material> materials = dropItems.get(selectedThreshold);
        if (materials == null || materials.isEmpty()) {
            return null;
        }

        return materials.get(ThreadLocalRandom.current().nextInt(materials.size()));
    }

    private @NotNull List<ItemStack> buildDropLoot(@NotNull MiniGameArena arena,
                                                   @NotNull Map<Integer, List<Material>> dropItems) {
        int minStacks = nightfall.dropLootMinStacks(arena);
        int maxStacks = nightfall.dropLootMaxStacks(arena);
        int stackCount = ThreadLocalRandom.current().nextInt(minStacks, maxStacks + 1);
        List<ItemStack> loot = new ArrayList<>(stackCount);
        for (int i = 0; i < stackCount; i++) {
            Material material = pickDropItem(dropItems);
            if (material == null || material.isAir()) {
                continue;
            }
            loot.add(new ItemStack(material));
        }
        return loot;
    }

    private @NotNull String supplyDropHint(@NotNull Player player, @NotNull Location dropLocation) {
        int distance = Math.max(1, (int) Math.round(Math.sqrt(dropHorizontalDistanceSquared(player.getLocation(), dropLocation))));
        String direction = relativeDirection(player, dropLocation);
        String suffix = distance == 1 ? "block" : "blocks";
        return "<gold>A supply drop is descending " + distance + " " + suffix + " " + direction + ".</gold>";
    }

    private double dropHorizontalDistanceSquared(@NotNull Location from, @NotNull Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (dx * dx) + (dz * dz);
    }

    private @NotNull String relativeDirection(@NotNull Player player, @NotNull Location target) {
        Vector forward = player.getLocation().getDirection().setY(0.0d);
        Vector toward = target.toVector().subtract(player.getLocation().toVector()).setY(0.0d);
        if (forward.lengthSquared() < 1.0E-6 || toward.lengthSquared() < 1.0E-6) {
            return "near you";
        }

        forward.normalize();
        toward.normalize();
        double cross = (forward.getX() * toward.getZ()) - (forward.getZ() * toward.getX());
        double dot = (forward.getX() * toward.getX()) + (forward.getZ() * toward.getZ());
        double angle = Math.toDegrees(Math.atan2(cross, dot));

        if (angle >= -22.5d && angle < 22.5d) {
            return "in front of you";
        }
        if (angle >= 22.5d && angle < 67.5d) {
            return "front-left of you";
        }
        if (angle >= 67.5d && angle < 112.5d) {
            return "to your left";
        }
        if (angle >= 112.5d && angle < 157.5d) {
            return "behind-left of you";
        }
        if (angle >= -67.5d && angle < -22.5d) {
            return "front-right of you";
        }
        if (angle >= -112.5d && angle < -67.5d) {
            return "to your right";
        }
        if (angle >= -157.5d && angle < -112.5d) {
            return "behind-right of you";
        }
        return "behind you";
    }

    private void clearTrackedZombies(@NotNull MiniGameArena arena) {
        Set<UUID> tracked = managedZombies(arena);
        for (UUID entityId : new LinkedHashSet<>(tracked)) {
            Entity entity = arena.world().getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        tracked.clear();
        bloodMoonZombieLastLocations(arena).clear();
        bloodMoonZombieStuckTicks(arena).clear();
        bloodMoonZombieUtilityHoldUntil(arena).clear();
        bloodMoonTntZombies(arena).clear();
        burningZombieFireDueAt(arena).clear();
        arena.set("bloodMoonPendingExplosions", 0);
    }

    private void igniteTrackedZombiesAtSunrise(@NotNull MiniGameArena arena) {
        Set<UUID> tracked = managedZombies(arena);
        for (UUID entityId : new LinkedHashSet<>(tracked)) {
            Entity entity = arena.world().getEntity(entityId);
            if (!(entity instanceof Zombie zombie) || zombie.isDead()) {
                tracked.remove(entityId);
                continue;
            }

            zombie.setTarget(null);
            var equipment = Objects.requireNonNull(zombie.getEquipment());
            equipment.setHelmet(null);
            zombie.setFireTicks(Math.max(zombie.getFireTicks(), 200));
        }
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> managedZombies(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("managedZombies", Set.class, LinkedHashSet::new);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> burningZombieFireDueAt(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("burningZombieFireDueAt", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> nightEliminatedPlayers(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("nightEliminatedPlayers", Set.class, LinkedHashSet::new);
    }

    private @NotNull List<Player> activeSurvivors(@NotNull MiniGameArena arena) {
        List<Player> survivors = new ArrayList<>();
        for (Player player : arena.getPlayers()) {
            if (!isNightEliminated(arena, player)) {
                survivors.add(player);
            }
        }
        return survivors;
    }

    private int activeSurvivorCount(@NotNull MiniGameArena arena) {
        return activeSurvivors(arena).size();
    }

    private boolean isNightEliminated(@NotNull MiniGameArena arena, @NotNull Player player) {
        return nightEliminatedPlayers(arena).contains(player.getUniqueId());
    }

    private boolean isNightPhase(@NotNull MiniGameArena arena) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING || arena.get("daylightLocked", Boolean.class, false)) {
            return false;
        }

        return isNightTime(arena.world().getTime());
    }

    private boolean isNightTime(long time) {
        return time >= NIGHT_START_TIME && time < DAY_START_TIME;
    }

    private void updateArenaStateCounters(@NotNull MiniGameArena arena) {
        int queued = Math.max(0, arena.get("remainingNightSpawns", Integer.class, 0));
        int alive = countManagedZombies(arena);
        arena.set("activeSurvivorCount", activeSurvivorCount(arena));
        arena.set("zombiesQueued", queued);
        arena.set("zombiesAlive", alive);
        arena.set("zombiesRemaining", queued + alive);
        updateCycleBossBar(arena);
    }

    private void updateCycleBossBar(@NotNull MiniGameArena arena) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            arena.remove("bossBarProgress");
            arena.remove("cycleCountdownLine");
            return;
        }

        PhaseCountdown countdown = currentPhaseCountdown(arena);
        arena.set("bossBarProgress", countdown.progress());
        arena.set("cycleCountdownLine", countdown.line());
    }

    private @NotNull PhaseCountdown currentPhaseCountdown(@NotNull MiniGameArena arena) {
        if (arena.get("daylightLocked", Boolean.class, false)) {
            int countdown = Math.max(0, arena.getCountdown());
            int maxCountdown = Math.max(1, arena.getCountdownMax());
            return new PhaseCountdown(
                "Sunset in " + formatCountdown(countdown),
                Math.clamp((double) countdown / (double) maxCountdown, 0.0d, 1.0d)
            );
        }

        long time = arena.world().getTime();
        boolean countdownToSunrise = time >= SUNSET_TIME && time < DAY_START_TIME;
        long remainingTicks = countdownToSunrise
            ? DAY_START_TIME - time
            : remainingTicksUntilSunset(arena, time);
        long totalTicks = countdownToSunrise
            ? DAY_START_TIME - SUNSET_TIME
            : totalDayPhaseTicks(arena);
        double effectiveMultiplier = effectiveTimeMultiplier(arena, time);
        int remainingSeconds = effectiveMultiplier <= 0.0d
            ? 0
            : (int) Math.max(0L, (long) Math.ceil(remainingTicks / (20.0d * effectiveMultiplier)));

        return new PhaseCountdown(
            (countdownToSunrise ? "Sunrise" : "Sunset") + " in " + formatCountdown(remainingSeconds),
            totalTicks <= 0L ? 1.0d : Math.clamp((double) remainingTicks / (double) totalTicks, 0.0d, 1.0d)
        );
    }

    private long remainingTicksUntilSunset(@NotNull MiniGameArena arena, long time) {
        if (arena.get("currentNight", Integer.class, 0) <= 0 && time >= NOON_TIME) {
            return Math.max(0L, SUNSET_TIME - time);
        }
        if (time >= DAY_START_TIME) {
            return (24000L - time) + SUNSET_TIME;
        }
        return Math.max(0L, SUNSET_TIME - time);
    }

    private long totalDayPhaseTicks(@NotNull MiniGameArena arena) {
        return arena.get("currentNight", Integer.class, 0) <= 0 ? (SUNSET_TIME - NOON_TIME) : ((24000L - DAY_START_TIME) + SUNSET_TIME);
    }

    private double effectiveTimeMultiplier(@NotNull MiniGameArena arena, long time) {
        double multiplier = configuredTimeSpeedMultiplier(arena, time);
        if (arena.get("nightFastForwarding", Boolean.class, false) && time >= SUNSET_TIME && time < DAY_START_TIME) {
            multiplier = Math.max(multiplier, DAWN_FAST_FORWARD_TARGET_MULTIPLIER);
        }
        return Math.max(1.0d, multiplier);
    }

    private @NotNull String formatCountdown(int seconds) {
        int mins = Math.max(0, seconds) / 60;
        int secs = Math.max(0, seconds) % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private int countManagedZombies(@NotNull MiniGameArena arena) {
        Set<UUID> tracked = managedZombies(arena);
        tracked.removeIf(entityId -> {
            Entity entity = arena.world().getEntity(entityId);
            boolean remove = !(entity instanceof Zombie zombie) || zombie.isDead() || !zombie.isValid();
            if (remove) {
                bloodMoonZombieLastLocations(arena).remove(entityId);
                bloodMoonZombieStuckTicks(arena).remove(entityId);
                bloodMoonZombieUtilityHoldUntil(arena).remove(entityId);
                bloodMoonTntZombies(arena).remove(entityId);
            }
            return remove;
        });
        return tracked.size();
    }

    private void resetTimeAnnouncements(@NotNull MiniGameArena arena) {
        arena.set("timeAnnouncementIndex", 0);
    }

    String advanceToNextCycle(@NotNull MiniGameArena arena) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.PREPARATION
            && arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return null;
        }

        World world = arena.world();
        boolean moveToSunset = arena.get("daylightLocked", Boolean.class, false)
            || !arena.get("wasNight", Boolean.class, false);

        arena.set("nightFastForwarding", false);
        arena.set("timeSpeedCarry", 0.0d);

        if (moveToSunset) {
            if (arena.get("daylightLocked", Boolean.class, false)) {
                if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                    arena.setStatus(MiniGameArena.ArenaStatus.RUNNING);
                }
                arena.set("daylightLocked", false);
                api.worlds().setSetting(world, "time", "unset");
            }

            world.setTime(SUNSET_TIME);
            arena.set("wasNight", false);
            resetTimeAnnouncements(arena);
            announceTimeMilestones(arena, world.getTime());
            updateArenaStateCounters(arena);
            debugArenaEvent(arena, "Cycle command advanced time to sunset.");
            return "sunset";
        }

        world.setTime(DAY_START_TIME);
        endNight(arena);
        arena.set("wasNight", false);
        updateArenaStateCounters(arena);
        debugArenaEvent(arena, "Cycle command advanced time to sunrise.");
        return "sunrise";
    }

    private void announceTimeMilestones(@NotNull MiniGameArena arena, long time) {
        long[] thresholds = {THREE_PM_TIME, SUNSET_TIME, NINE_PM_TIME, MIDNIGHT_TIME, THREE_AM_TIME};
        String[] messages = {
            nightfall.timeMilestoneThreePmMessage(),
            nightfall.timeMilestoneSunsetMessage(),
            nightfall.timeMilestoneNinePmMessage(),
            nightfall.timeMilestoneMidnightMessage(),
            nightfall.timeMilestoneThreeAmMessage()
        };

        int index = arena.get("timeAnnouncementIndex", Integer.class, 0);
        while (index < thresholds.length && time >= thresholds[index] && time < DAY_START_TIME) {
            broadcastPlainToOccupants(arena, messages[index]);
            playTimeAnnouncementSound(arena);
            index++;
        }
        arena.set("timeAnnouncementIndex", index);
    }

    private boolean shouldAccelerateToDawn(@NotNull MiniGameArena arena) {
        return isNightPhase(arena)
            && activeSurvivorCount(arena) > 0
            && arena.get("remainingNightSpawns", Integer.class, 0) <= 0
            && managedZombies(arena).isEmpty();
    }

    private void accelerateNightTowardDawn(@NotNull MiniGameArena arena, long time) {
        if (!arena.get("nightFastForwarding", Boolean.class, false)) {
            arena.set("nightFastForwarding", true);
            broadcastToOccupants(arena, nightfall.nightClearMessage());
        }

        long extraTicks = extraDawnFastForwardTicks(arena, time);
        arena.world().setTime(Math.min(DAY_START_TIME, time + extraTicks));
    }

    private long extraDawnFastForwardTicks(@NotNull MiniGameArena arena, long time) {
        double currentMultiplier = configuredTimeSpeedMultiplier(arena, time);
        double extraTicks = Math.max(0.0d, Math.ceil(DAWN_FAST_FORWARD_TARGET_MULTIPLIER - currentMultiplier));
        return (long) extraTicks;
    }

    private void respawnNightEliminatedPlayers(@NotNull MiniGameArena arena) {
        boolean revivedAny = false;
        for (Player player : arena.getPlayers()) {
            if (!isNightEliminated(arena, player)) {
                continue;
            }

            MiniGamePlayer miniGamePlayer = arena.getPlayer(player);
            if (miniGamePlayer == null) {
                continue;
            }

            revivePlayer(arena, player, miniGamePlayer);
            broadcastToOccupants(arena, nightfall.playerReturnedMessage(player));
            revivedAny = true;
        }

        if (revivedAny) {
            updateArenaStateCounters(arena);
        }
    }

    public int respawnDownedPlayers(@NotNull MiniGameArena arena) {
        if (!isActiveRoundStatus(arena)) {
            return -1;
        }

        int revived = 0;
        for (Player player : arena.getPlayers()) {
            if (!isNightEliminated(arena, player)) {
                continue;
            }

            MiniGamePlayer miniGamePlayer = arena.getPlayer(player);
            if (miniGamePlayer == null) {
                continue;
            }

            revivePlayer(arena, player, miniGamePlayer);
            broadcastToOccupants(arena, nightfall.playerReturnedMessage(player));
            revived++;
        }

        if (revived > 0) {
            updateArenaStateCounters(arena);
        }
        return revived;
    }

    private void revivePlayer(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull MiniGamePlayer miniGamePlayer) {
        UUID playerId = player.getUniqueId();
        nightEliminatedPlayers(arena).remove(playerId);
        showPlayerToArena(arena, player);
        pendingDeathRespawns(arena).remove(playerId);
        playerDropTargets(arena).remove(playerId);
        if (nightfall.dropsEnabled(arena)) {
            playerDropDueAt(arena).put(playerId, System.currentTimeMillis() + randomDropDelayMillis(arena));
        } else {
            playerDropDueAt(arena).remove(playerId);
        }
        miniGamePlayer.set("livesRemaining", 1);
        prepareParticipantForRound(player);
        Location respawn = activeSpawn(arena);
        if (respawn != null) {
            PlayerUtil.teleport(player, respawn);
        }
        player.setHealth(PlayerUtil.getMaxHealth(player));
    }

    private void hidePlayerFromArena(@NotNull MiniGameArena arena, @NotNull Player hiddenPlayer) {
        STEMCraft plugin = STEMCraft.getPlugin();
        for (Player viewer : arena.getOccupants()) {
            if (!viewer.getUniqueId().equals(hiddenPlayer.getUniqueId())) {
                viewer.hidePlayer(plugin, hiddenPlayer);
            }
        }
    }

    private void hideDownedPlayersFrom(@NotNull MiniGameArena arena, @NotNull Player viewer) {
        for (Player downedPlayer : arena.getPlayers()) {
            if (!viewer.getUniqueId().equals(downedPlayer.getUniqueId()) && isNightEliminated(arena, downedPlayer)) {
                viewer.hidePlayer(STEMCraft.getPlugin(), downedPlayer);
            }
        }
    }

    private void showPlayerToArena(@NotNull MiniGameArena arena, @NotNull Player shownPlayer) {
        STEMCraft plugin = STEMCraft.getPlugin();
        for (Player viewer : arena.getOccupants()) {
            if (!viewer.getUniqueId().equals(shownPlayer.getUniqueId())) {
                viewer.showPlayer(plugin, shownPlayer);
            }
        }
    }

    private void restoreDownedPlayerVisibility(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            if (isNightEliminated(arena, player)) {
                showPlayerToArena(arena, player);
            }
        }
    }

    private @NotNull Location resolveDownedLocation(@NotNull MiniGameArena arena, @NotNull Location deathLocation) {
        if (!isOutsideArena(arena, deathLocation)) {
            return deathLocation.clone();
        }

        Location fallback = activeSpawn(arena);
        return fallback != null ? fallback.clone() : deathLocation.clone();
    }

    private @Nullable Location assignedPreparationSpawn(@NotNull MiniGameArena arena, @NotNull Player player) {
        Map<UUID, Location> assignments = prepSpawnAssignments(arena);
        prunePreparationSpawnAssignments(arena, assignments);

        UUID playerId = player.getUniqueId();
        Location existing = assignments.get(playerId);
        if (isUsablePreparationSpawn(arena, existing)) {
            return existing.clone();
        }

        Location resolved = findPreparationSpawn(arena, playerId);
        if (resolved == null) {
            resolved = fallbackPreparationSpawn(arena);
        }
        if (resolved == null) {
            return null;
        }

        assignments.put(playerId, resolved.clone());
        return resolved.clone();
    }

    private void prunePreparationSpawnAssignments(@NotNull MiniGameArena arena, @NotNull Map<UUID, Location> assignments) {
        Set<UUID> activePlayerIds = new HashSet<>();
        for (Player activePlayer : arena.getPlayers()) {
            activePlayerIds.add(activePlayer.getUniqueId());
        }

        assignments.entrySet().removeIf(entry ->
            !activePlayerIds.contains(entry.getKey()) || !isUsablePreparationSpawn(arena, entry.getValue())
        );
    }

    private @Nullable Location findPreparationSpawn(@NotNull MiniGameArena arena, @NotNull UUID playerId) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        Location center = preparationSpawnCenter(arena, arenaRegion);
        if (center == null) {
            return null;
        }

        List<Location> occupied = new ArrayList<>();
        for (Map.Entry<UUID, Location> entry : prepSpawnAssignments(arena).entrySet()) {
            if (playerId.equals(entry.getKey()) || !isUsablePreparationSpawn(arena, entry.getValue())) {
                continue;
            }
            occupied.add(entry.getValue());
        }

        for (int attempt = 0; attempt < PREP_SPAWN_ATTEMPTS; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0d, Math.PI * 2.0d);
            double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * PREP_SPAWN_CLUSTER_RADIUS;
            int x = (int) Math.floor(center.getX() + (Math.cos(angle) * radius));
            int z = (int) Math.floor(center.getZ() + (Math.sin(angle) * radius));

            Location candidate = findSurfacePreparationSpawn(arena, arenaRegion, x, z);
            if (candidate == null || isTooCloseToPreparationSpawn(candidate, occupied)) {
                continue;
            }
            return candidate;
        }

        Location centerSpawn = findSurfacePreparationSpawn(arena, arenaRegion, center.getBlockX(), center.getBlockZ());
        if (centerSpawn != null && !isTooCloseToPreparationSpawn(centerSpawn, occupied)) {
            return centerSpawn;
        }

        return null;
    }

    private @Nullable Location preparationSpawnCenter(@NotNull MiniGameArena arena, @Nullable SCRegion arenaRegion) {
        if (arenaRegion == null) {
            return fallbackPreparationSpawn(arena);
        }

        Location min = arenaRegion.getMinimumLocation();
        Location max = arenaRegion.getMaximumLocation();
        double centerX = ((min.getBlockX() + max.getBlockX()) / 2.0d) + 0.5d;
        double centerZ = ((min.getBlockZ() + max.getBlockZ()) / 2.0d) + 0.5d;

        Location centerSurface = findSurfacePreparationSpawn(
            arena,
            arenaRegion,
            (int) Math.floor(centerX),
            (int) Math.floor(centerZ)
        );
        if (centerSurface != null) {
            return centerSurface;
        }

        return new Location(arena.world(), centerX, arena.world().getSpawnLocation().getY(), centerZ);
    }

    private @Nullable Location fallbackPreparationSpawn(@NotNull MiniGameArena arena) {
        Location fallback = activeSpawn(arena);
        if (fallback == null) {
            return null;
        }

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        Location surface = findSurfacePreparationSpawn(arena, arenaRegion, fallback.getBlockX(), fallback.getBlockZ());
        return surface != null ? surface : fallback.clone();
    }

    private @Nullable Location findSurfacePreparationSpawn(@NotNull MiniGameArena arena,
                                                           @Nullable SCRegion arenaRegion,
                                                           int x,
                                                           int z) {
        Block ground = arena.world().getHighestBlockAt(x, z);
        if (!ground.getType().isSolid()) {
            return null;
        }

        Location spawn = ground.getLocation().add(0.5d, 1.0d, 0.5d);
        if (arenaRegion != null && !arenaRegion.contains(spawn)) {
            return null;
        }

        return isUsablePreparationSpawn(arena, spawn) ? spawn : null;
    }

    private boolean isUsablePreparationSpawn(@NotNull MiniGameArena arena, @Nullable Location spawn) {
        if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(arena.world())) {
            return false;
        }

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion != null && !arenaRegion.contains(spawn)) {
            return false;
        }

        Block feet = spawn.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        if (!ground.getType().isSolid()) {
            return false;
        }
        if (!(feet.getType().isAir() || feet.isPassable()) || feet.isLiquid()) {
            return false;
        }
        if (!(head.getType().isAir() || head.isPassable()) || head.isLiquid()) {
            return false;
        }

        Block highest = arena.world().getHighestBlockAt(spawn.getBlockX(), spawn.getBlockZ());
        return highest.getY() == ground.getY();
    }

    private boolean isTooCloseToPreparationSpawn(@NotNull Location candidate, @NotNull List<Location> occupied) {
        for (Location other : occupied) {
            if (horizontalDistanceSquared(candidate, other) < PREP_SPAWN_MIN_PLAYER_DISTANCE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> playerDropDueAt(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("playerDropDueAt", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Location> playerDropTargets(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("playerDropTargets", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Location> bloodMoonZombieLastLocations(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("bloodMoonZombieLastLocations", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Integer> bloodMoonZombieStuckTicks(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("bloodMoonZombieStuckTicks", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> bloodMoonZombieUtilityHoldUntil(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("bloodMoonZombieUtilityHoldUntil", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> bloodMoonTntZombies(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("bloodMoonTntZombies", Set.class, LinkedHashSet::new);
    }

    private void queueBloodMoonPendingExplosion(@NotNull MiniGameArena arena) {
        arena.set("bloodMoonPendingExplosions", bloodMoonPendingExplosions(arena) + 1);
    }

    private boolean consumeBloodMoonPendingExplosion(@NotNull MiniGameArena arena) {
        int pending = bloodMoonPendingExplosions(arena);
        if (pending <= 0) {
            return false;
        }
        arena.set("bloodMoonPendingExplosions", pending - 1);
        return true;
    }

    private int bloodMoonPendingExplosions(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("bloodMoonPendingExplosions", Integer.class, 0));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Location> pendingDeathRespawns(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("pendingDeathRespawns", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Location> prepSpawnAssignments(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("prepSpawnAssignments", Map.class, LinkedHashMap::new);
    }

    private void registerSpawnGuards() {
        api.events().register(CreatureSpawnEvent.class, event -> {
            if (nightfall.minigame() == null) {
                return;
            }

            Location location = event.getLocation();
            World world = location.getWorld();
            if (world == null) {
                return;
            }

            for (MiniGameArena arena : nightfall.minigame().arenas()) {
                SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
                if (arenaRegion == null || !arena.world().equals(world) || !arenaRegion.contains(location)) {
                    continue;
                }
                if (arena.getStatus() == MiniGameArena.ArenaStatus.DISABLED || arena.getStatus() == MiniGameArena.ArenaStatus.SETUP) {
                    continue;
                }
                if (event.getEntity() instanceof Zombie) {
                    String reason = event.getSpawnReason().name();
                    if ("CUSTOM".equals(reason) || "DROWNED".equals(reason)) {
                        return;
                    }
                }
                event.setCancelled(true);
                return;
            }
        });
    }

    private void registerMovementGuards() {
        api.events().register(PlayerTeleportEvent.class, event -> {
            MiniGameArena arena = activeParticipantArena(event.getPlayer());
            if (arena == null || !shouldConfinePlayers(arena)) {
                return;
            }

            Location destination = event.getTo();
            if (isOutsideArena(arena, destination)) {
                if (isIntentionalExitTeleport(event.getCause())) {
                    event.setCancelled(true);
                    exitArenaViaTeleport(arena, event.getPlayer(), destination);
                    return;
                }
                event.setCancelled(true);
                arena.warn(event.getPlayer(), "You cannot leave the arena.");
                return;
            }

            if (isBlockedTeleportCause(event.getCause())) {
                event.setCancelled(true);
                arena.warn(event.getPlayer(), "Teleports are disabled in Nightfall.");
            }
        }, EventPriority.HIGHEST, true);

        api.events().register(PortalCreateEvent.class, event -> {
            if (nightfall.minigame() == null) {
                return;
            }

            for (MiniGameArena arena : nightfall.minigame().arenas()) {
                if (!shouldConfinePlayers(arena)) {
                    continue;
                }

                SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
                if (arenaRegion == null) {
                    continue;
                }

                boolean intersectsArena = event.getBlocks().stream()
                    .anyMatch(blockState -> arena.world().equals(blockState.getWorld())
                        && arenaRegion.contains(blockState.getLocation()));
                if (!intersectsArena) {
                    continue;
                }

                event.setCancelled(true);
                if (event.getEntity() instanceof Player player && arena.hasPlayer(player)) {
                    arena.warn(player, "Portals are disabled in Nightfall.");
                }
                return;
            }
        }, EventPriority.HIGHEST, true);

        api.events().register(PlayerBedEnterEvent.class, event -> {
            MiniGameArena arena = activeParticipantArena(event.getPlayer());
            if (arena == null || !shouldConfinePlayers(arena)) {
                return;
            }

            event.setCancelled(true);
            arena.warn(event.getPlayer(), "Sleeping is disabled in Nightfall.");
        }, EventPriority.HIGHEST, true);

        api.events().register(PlayerInteractEvent.class, event -> {
            MiniGameArena arena = activeParticipantArena(event.getPlayer());
            if (arena == null || !isNightEliminated(arena, event.getPlayer())) {
                return;
            }

            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        api.events().register(EntityPickupItemEvent.class, event -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            MiniGameArena arena = activeParticipantArena(player);
            if (arena == null || !isNightEliminated(arena, player)) {
                return;
            }

            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);

        api.events().register(EntityDamageByEntityEvent.class, event -> {
            Player playerDamager = resolvePlayerDamager(event);
            if (playerDamager == null) {
                return;
            }

            MiniGameArena arena = activeParticipantArena(playerDamager);
            if (arena == null || !isNightEliminated(arena, playerDamager)) {
                return;
            }

            event.setCancelled(true);
        }, EventPriority.HIGHEST, true);
    }

    private void registerDeathFallbacks() {
        api.events().register(PlayerDeathEvent.class, event -> {
            MiniGameArena arena = activeParticipantArena(event.getEntity());
            if (arena == null || !isActiveRoundStatus(arena)) {
                return;
            }

            event.deathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            handlePlayerDeath(arena, event.getEntity(), false);
        }, EventPriority.HIGHEST, true);

        api.events().register(PlayerRespawnEvent.class, event -> {
            MiniGameArena arena = activeParticipantArena(event.getPlayer());
            if (arena == null) {
                return;
            }

            Location respawnLocation = pendingDeathRespawns(arena).remove(event.getPlayer().getUniqueId());
            if (respawnLocation == null) {
                return;
            }

            event.setRespawnLocation(respawnLocation);

            api.tasks().nextTick(() -> {
                if (arena.hasPlayer(event.getPlayer())) {
                    MiniGamePlayer miniGamePlayer = arena.getPlayer(event.getPlayer());
                    if (miniGamePlayer == null) {
                        return;
                    }

                    if (!isNightEliminated(arena, event.getPlayer())) {
                        markPlayerDowned(arena, event.getPlayer(), miniGamePlayer);
                    }
                    prepareDownedParticipant(event.getPlayer());
                    PlayerUtil.teleport(event.getPlayer(), respawnLocation);
                    event.getPlayer().setHealth(PlayerUtil.getMaxHealth(event.getPlayer()));
                }
            });
        }, EventPriority.HIGHEST, true);
    }

    private void registerZombieCleanup() {
        api.events().register(EntityDeathEvent.class, event -> {
            if (!(event.getEntity() instanceof Zombie zombie) || nightfall.minigame() == null) {
                return;
            }

            UUID entityId = zombie.getUniqueId();
            for (MiniGameArena arena : nightfall.minigame().arenas()) {
                if (!managedZombies(arena).remove(entityId)) {
                    continue;
                }

                cleanupTrackedZombie(arena, entityId);

                Player killer = zombie.getKiller();
                if (killer != null && arena.hasPlayer(killer)) {
                    MiniGamePlayer miniGamePlayer = arena.getPlayer(killer);
                    if (miniGamePlayer != null) {
                        miniGamePlayer.addKill();
                    }
                }

                updateArenaStateCounters(arena);
                break;
            }
        });

        api.events().register(EntityTransformEvent.class, event -> {
            if (!(event.getEntity() instanceof Zombie zombie) || nightfall.minigame() == null) {
                return;
            }

            UUID originalId = zombie.getUniqueId();
            for (MiniGameArena arena : nightfall.minigame().arenas()) {
                Set<UUID> tracked = managedZombies(arena);
                if (!tracked.remove(originalId)) {
                    continue;
                }

                if (event.getTransformedEntity() instanceof Zombie transformedZombie) {
                    LivingEntity target = zombie.getTarget();
                    tracked.add(transformedZombie.getUniqueId());
                    transformedZombie.setCanPickupItems(false);
                    if (bloodMoonTntZombies(arena).remove(originalId)) {
                        markAsTntZombie(arena, transformedZombie);
                    }
                    bloodMoonZombieLastLocations(arena).remove(originalId);
                    bloodMoonZombieStuckTicks(arena).remove(originalId);
                    bloodMoonZombieUtilityHoldUntil(arena).remove(originalId);
                    if (target != null) {
                        transformedZombie.setTarget(target);
                    }
                } else {
                    cleanupTrackedZombie(arena, originalId);
                }

                updateArenaStateCounters(arena);
                break;
            }
        }, EventPriority.HIGHEST, true);
    }

    private @Nullable Player resolvePlayerDamager(@NotNull EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void cleanupTrackedZombie(@NotNull MiniGameArena arena, @NotNull UUID entityId) {
        bloodMoonZombieLastLocations(arena).remove(entityId);
        bloodMoonZombieStuckTicks(arena).remove(entityId);
        bloodMoonZombieUtilityHoldUntil(arena).remove(entityId);
        bloodMoonTntZombies(arena).remove(entityId);
        burningZombieFireDueAt(arena).remove(entityId);
    }

    private void captureExplosionBlocks(@NotNull MiniGameArena arena, @NotNull List<Block> blocks) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            blocks.clear();
            return;
        }

        WorldChangeSession session = api.worlds().changes(arena.world());
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        blocks.removeIf(block -> {
            if (arenaRegion != null && !arenaRegion.contains(block.getLocation())) {
                return true;
            }
            session.captureBlock(block);
            return false;
        });
    }

    private void broadcastToOccupants(@NotNull MiniGameArena arena, @NotNull String message, Player... exclude) {
        Set<UUID> excluded = new HashSet<>();
        for (Player player : exclude) {
            excluded.add(player.getUniqueId());
        }
        for (Player occupant : arena.getOccupants()) {
            if (!excluded.contains(occupant.getUniqueId())) {
                arena.info(occupant, message);
            }
        }
    }

    private void broadcastPlainToOccupants(@NotNull MiniGameArena arena, @NotNull String message, Player... exclude) {
        Set<UUID> excluded = new HashSet<>();
        for (Player player : exclude) {
            excluded.add(player.getUniqueId());
        }
        for (Player occupant : arena.getOccupants()) {
            if (!excluded.contains(occupant.getUniqueId())) {
                arena.send(occupant, message);
            }
        }
    }

    private void playSoundToOccupants(@NotNull MiniGameArena arena, @NotNull Sound sound, float volume, float pitch) {
        for (Player occupant : arena.getOccupants()) {
            occupant.playSound(occupant.getLocation(), sound, volume, pitch);
        }
    }

    private void playTimeAnnouncementSound(@NotNull MiniGameArena arena) {
        playSoundToOccupants(arena, TIME_ANNOUNCEMENT_SOUND, TIME_ANNOUNCEMENT_VOLUME, TIME_ANNOUNCEMENT_PITCH);
    }

    private record PhaseCountdown(@NotNull String line, double progress) {}

    private void returnPlayerToArena(@NotNull MiniGameArena arena, @NotNull Player player, @Nullable Location from) {
        Location destination = from != null && !isOutsideArena(arena, from)
            ? from.clone()
            : assignedPreparationSpawn(arena, player);
        if (destination != null) {
            player.setVelocity(new Vector());
            player.setFallDistance(0.0f);
            PlayerUtil.teleport(player, destination);
        }
        arena.warn(player, "You cannot leave the arena.");
    }

    private void exitArenaViaTeleport(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location destination) {
        Location exitDestination = destination.clone();
        MiniGameArena.ArenaStatus previousStatus = arena.getStatus();
        boolean wasLastPlayer = arena.hasPlayer(player) && arena.numPlayers() == 1;
        debugArenaEvent(arena, player.getName() + " requested intentional exit to "
            + destination.getWorld().getName() + " " + formatBlockPosition(destination));
        api.tasks().nextTick(() -> {
            if (!player.isOnline() || !arena.hasPlayer(player)) {
                return;
            }

            arena.removePlayer(player);
            if (wasLastPlayer
                && (previousStatus == MiniGameArena.ArenaStatus.PREPARATION
                    || previousStatus == MiniGameArena.ArenaStatus.RUNNING
                    || previousStatus == MiniGameArena.ArenaStatus.COOLDOWN
                    || previousStatus == MiniGameArena.ArenaStatus.ENDING)
                && api.worlds().changes(arena.world()).isRecording()) {
                debugArenaEvent(arena, "Forced RESETTING after intentional exit because recording was still active.");
                arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
            }
            api.tasks().nextTick(() -> {
                if (!player.isOnline()) {
                    return;
                }
                if (arena.hasOccupant(player)) {
                    return;
                }
                debugArenaEvent(arena, "Teleporting " + player.getName() + " out after arena removal to "
                    + exitDestination.getWorld().getName() + " " + formatBlockPosition(exitDestination));
                PlayerUtil.teleport(player, exitDestination);
            });
        });
    }

    private boolean shouldConfinePlayers(@NotNull MiniGameArena arena) {
        return switch (arena.getStatus()) {
            case WAITING, STARTING, PREPARATION, RUNNING -> true;
            default -> false;
        };
    }

    private boolean isActiveRoundStatus(@NotNull MiniGameArena arena) {
        return arena.getStatus() == MiniGameArena.ArenaStatus.PREPARATION
            || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING;
    }

    private boolean isOutsideArena(@NotNull MiniGameArena arena, @Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        if (!arena.world().equals(location.getWorld())) {
            return true;
        }

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        return arenaRegion != null && !arenaRegion.contains(location);
    }

    private boolean isBlockedTeleportCause(@Nullable PlayerTeleportEvent.TeleportCause cause) {
        if (cause == null) {
            return false;
        }

        String causeName = cause.name();
        return causeName.equals("ENDER_PEARL")
            || causeName.equals("CHORUS_FRUIT")
            || causeName.equals("NETHER_PORTAL")
            || causeName.equals("END_PORTAL")
            || causeName.equals("END_GATEWAY");
    }

    private boolean isIntentionalExitTeleport(@Nullable PlayerTeleportEvent.TeleportCause cause) {
        if (cause == null) {
            return false;
        }

        return cause == PlayerTeleportEvent.TeleportCause.COMMAND
            || cause == PlayerTeleportEvent.TeleportCause.PLUGIN;
    }

    private @Nullable MiniGameArena activeParticipantArena(@NotNull Player player) {
        if (nightfall.minigame() == null) {
            return null;
        }

        MiniGameArena arena = nightfall.minigame().findPlayer(player);
        if (arena == null || !nightfall.minigame().arenas().contains(arena) || !arena.hasPlayer(player)) {
            return null;
        }
        return arena;
    }

    private @Nullable Location activeSpawn(@NotNull MiniGameArena arena) {
        Location playSpawn = nightfall.playSpawn(arena);
        return playSpawn != null ? playSpawn : arena.getLobbySpawn();
    }

    private void debugArenaEvent(@NotNull MiniGameArena arena, @NotNull String message) {
        STEMCraft plugin = STEMCraft.getPlugin();
        if (!plugin.debugging()) {
            return;
        }

        plugin.messages().info("Nightfall[" + arena.id() + "] " + message);
    }

    private @NotNull String formatBlockPosition(@NotNull Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private String listenerPrefix(String arenaId) {
        return NamespaceId.of(NightfallMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }
}
