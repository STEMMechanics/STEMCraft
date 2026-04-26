package dev.stemcraft.minigame.boatrace;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class BoatRaceArenaHandler implements MiniGameArenaHandler {
    private static final int RUNNING_COUNTDOWN_SECONDS = 600;
    private static final int SNOWBALL_SLOT = 0;
    private static final int SNOWBALL_STACK_SIZE = 16;
    private static final double SNOWBALL_PUSH_STRENGTH = 0.85d;
    private static final double SNOWBALL_PUSH_LIFT = 0.08d;
    private static final long TNT_BOUNCE_COOLDOWN_MILLIS = 750L;
    private static final double TNT_BOUNCE_VERTICAL_VELOCITY = 1.35d;
    private static final double TNT_BOUNCE_HORIZONTAL_MULTIPLIER = 1.35d;
    private static final double TNT_BOUNCE_FALLBACK_SPEED = 0.75d;
    private static final double TNT_BOUNCE_MIN_HORIZONTAL_SPEED = 0.95d;
    private static final double TNT_BOUNCE_SUSTAIN_VERTICAL_VELOCITY = 0.45d;
    private static final long[] TNT_BOUNCE_SUSTAIN_DELAYS = {1L, 2L};

    private final STEMCraftAPI api;
    private final BoatRaceMiniGame boatRace;

    public BoatRaceArenaHandler(STEMCraftAPI api, BoatRaceMiniGame boatRace) {
        this.api = api;
        this.boatRace = boatRace;
        registerVehicleListeners();
        registerSnowballListeners();
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        SCRegion finishRegion = arena.get("finishRegion", SCRegion.class);
        List<SCRegion> stageRegions = boatRace.stageRegions(arena);
        List<Location> startingGrid = boatRace.startingGrid(arena);

        if (arena.getLobbySpawn() == null) {
            result.addError("Lobby spawn is not defined.", "lobbySpawn");
        }
        if (arena.getSpectatorSpawn() == null) {
            result.addError("Spectator spawn is not defined.", "spectatorSpawn");
        }
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arenaRegion");
        }
        if (finishRegion == null) {
            result.addError("Finish region is not defined.", "finishRegion");
        }
        if (startingGrid.isEmpty()) {
            result.addError("Starting grid locations are not defined.", "startingGrid");
        }
        if (arena.getMinPlayers() < 1) {
            result.addError("Boat Race arenas require at least 1 minimum player.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 1) {
            result.addError("Boat Race arenas require at least 1 maximum player.", "maxPlayers");
        }
        if (boatRace.laps(arena) < 1) {
            result.addError("Boat Race arenas require at least 1 lap.", "laps");
        }
        if (!startingGrid.isEmpty() && arena.getMaxPlayers() > startingGrid.size()) {
            result.addError("Max players exceeds configured starting grid slots.", "maxPlayers");
        }

        if (arenaRegion != null && finishRegion != null && !arenaRegion.contains(finishRegion)) {
            result.addError("Finish region must be inside the arena region.", "finishRegion");
        }

        for (int i = 0; i < stageRegions.size(); i++) {
            SCRegion stage = stageRegions.get(i);
            if (arenaRegion != null && !arenaRegion.contains(stage)) {
                result.addError("Checkpoint " + (i + 1) + " must be inside the arena region.", "checkpoints." + i);
            }
        }

        for (int i = 0; i < startingGrid.size(); i++) {
            Location grid = startingGrid.get(i);
            if (grid == null || grid.getWorld() == null) {
                result.addError("Starting grid slot " + (i + 1) + " is invalid.", "startingGrid." + i);
                continue;
            }
            if (!arena.world().equals(grid.getWorld())) {
                result.addError("Starting grid slot " + (i + 1) + " must be in the arena world.", "startingGrid." + i);
            } else if (arenaRegion != null && !arenaRegion.contains(grid)) {
                result.addError("Starting grid slot " + (i + 1) + " must be inside the arena region.", "startingGrid." + i);
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        clearRaceState(arena);
        refreshRegionListeners(arena);
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        despawnAllBoats(arena);
        removeRegionListeners(arena.id());
    }

    public void refreshRegionListeners(@NotNull MiniGameArena arena) {
        removeRegionListeners(arena.id());
        registerRegionListeners(arena);
    }

    @Override
    public HandlerEventResult onBlockPlace(MiniGameArena arena, Player player, org.bukkit.block.Block block) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onBlockBreak(MiniGameArena arena, Player player, org.bukkit.block.Block block) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onEntityDamage(MiniGameArena arena, EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return HandlerEventResult.ALLOW;
        }
        if (!arena.hasPlayer(player)) {
            return HandlerEventResult.ALLOW;
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || player.getHealth() - event.getFinalDamage() <= 0.0d) {
                resetToCheckpoint(arena, player, "You crashed out. Returned to your last checkpoint.");
            }
            return HandlerEventResult.DENY;
        }

        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onPlayerDropItem(MiniGameArena arena, Player player, ItemStack item) {
        return HandlerEventResult.DENY;
    }

    @Override
    public void onArenaStatusChanged(MiniGameArena arena, MiniGameArena.ArenaStatus oldStatus, MiniGameArena.ArenaStatus newStatus) {
        if (newStatus == MiniGameArena.ArenaStatus.WAITING) {
            arena.resetTitle();
            arena.stopWinnerCelebration();
            clearSnowballSupply(arena);
            clearRaceState(arena);
            teleportPlayersToLobby(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.STARTING) {
            clearSnowballSupply(arena);
            prepareStartingGrid(arena);
            broadcastToOccupants(arena, "<gold>Race countdown started.</gold>");
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RUNNING) {
            prepareRunningState(arena);
            showRaceStartTitle(arena);
            playSoundToOccupants(arena, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.15f);
            broadcastToOccupants(arena, "<aqua>Go!</aqua> <gray>The race is on.</gray>");
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.ENDING) {
            String winnerName = boatRace.winnerName(arena);
            clearSnowballSupply(arena);
            Player winner = resolveWinnerPlayer(arena);
            if (winner != null) {
                startWinnerCelebration(arena, winner);
                broadcastToOccupants(arena, "<gold>Race Over!</gold> <yellow>" + winner.getName() + "</yellow> <gray>wins the race.</gray>");
            } else if (!"-".equals(winnerName)) {
                broadcastToOccupants(arena, "<gold>Race Over!</gold> <yellow>" + winnerName + "</yellow> <gray>wins the race.</gray>");
            } else {
                broadcastToOccupants(arena, "<gold>Race Over!</gold> <gray>No winner was determined.</gray>");
            }
            playSoundToOccupants(arena, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.0f);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RESETTING) {
            resetArena(arena);
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
        }
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
                showStartingCountdownTitle(arena, secondsRemaining);
            }
        }
    }

    @Override
    public void onArenaCountdownEnd(MiniGameArena arena) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RUNNING, RUNNING_COUNTDOWN_SECONDS);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            concludeRace(arena);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        registerJoinOrder(arena, player);
        boatRace.lapProgress(arena).put(player.getUniqueId(), 1);
        boatRace.finishOrder(arena).remove(player.getUniqueId());
        boatRace.stageProgress(arena).put(player.getUniqueId(), 0);
        boatRace.checkpointLocations(arena).put(player.getUniqueId(), fallbackGridLocation(arena, player));

        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
            api.tasks().nextTick(() -> {
                if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
                    arena.setStatus(MiniGameArena.ArenaStatus.STARTING, boatRace.startCountdownSeconds(arena));
                }
            });
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            api.tasks().nextTick(() -> prepareStartingGrid(arena));
        }

        return arena.getLobbySpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        removePlayerState(arena, player);
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            arena.setCountdown(0);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            if (arena.numPlayers() < arena.getMinPlayers()) {
                arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
            } else if (allActiveRacersFinished(arena)) {
                concludeRace(arena);
            }
        }
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        onPlayerLeaveArena(arena, player);
    }

    private void registerVehicleListeners() {
        api.events().register(VehicleExitEvent.class, event -> {
            if (!(event.getExited() instanceof Player player)) {
                return;
            }

            MiniGameArena arena = boatRace.minigame().findPlayer(player);
            if (arena == null) {
                return;
            }
            if (arena.getStatus() != MiniGameArena.ArenaStatus.STARTING && arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                return;
            }
            if (!ownsBoat(arena, player, event.getVehicle().getUniqueId())) {
                return;
            }

            event.setCancelled(true);
            api.tasks().nextTick(() -> remountPlayerIfNeeded(arena, player));
        });

        api.events().register(VehicleDestroyEvent.class, event -> {
            MiniGameArena arena = arenaForBoat(event.getVehicle().getUniqueId());
            if (arena != null) {
                event.setCancelled(true);
            }
        });

        api.events().register(VehicleMoveEvent.class, event -> {
            if (!(event.getVehicle() instanceof Boat boat)) {
                return;
            }

            Player rider = boat.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst()
                .orElse(null);
            if (rider == null) {
                return;
            }

            MiniGameArena arena = boatRace.minigame().findPlayer(rider);
            if (arena == null) {
                return;
            }
            if (!ownsBoat(arena, rider, boat.getUniqueId())) {
                return;
            }
            if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
                maybeBounceOnTnt(arena, rider, boat, event.getFrom(), event.getTo());
                return;
            }
            if (arena.getStatus() != MiniGameArena.ArenaStatus.STARTING) {
                return;
            }

            Location gridLocation = assignedGridLocation(arena, rider);
            Location currentLocation = event.getTo();
            if (gridLocation != null) {
                if (hasDriftedFromGrid(currentLocation, gridLocation)) {
                    Location corrected = gridLocation.clone();
                    corrected.setYaw(currentLocation.getYaw());
                    corrected.setPitch(currentLocation.getPitch());
                    boat.teleport(corrected);
                    boat.setVelocity(boat.getVelocity().zero());
                }
            }
        });
    }

    private void registerSnowballListeners() {
        api.events().register(ProjectileLaunchEvent.class, event -> {
            if (!(event.getEntity() instanceof org.bukkit.entity.Snowball snowball)) {
                return;
            }

            ProjectileSource shooter = snowball.getShooter();
            if (!(shooter instanceof Player player)) {
                return;
            }

            MiniGameArena arena = boatRace.minigame().findPlayer(player);
            if (arena == null || !BoatRaceMiniGame.namespace().equals(arena.namespace())) {
                return;
            }
            if (!arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                return;
            }

            api.tasks().nextTick(() -> {
                if (arena.hasPlayer(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
                    ensureSnowballSupply(player);
                }
            });
        });

        api.events().register(ProjectileHitEvent.class, event -> {
            if (!(event.getEntity() instanceof org.bukkit.entity.Snowball snowball)) {
                return;
            }
            if (!(event.getHitEntity() instanceof Player target)) {
                return;
            }

            ProjectileSource shooter = snowball.getShooter();
            if (!(shooter instanceof Player attacker) || attacker.equals(target)) {
                return;
            }

            MiniGameArena arena = boatRace.minigame().findPlayer(attacker);
            if (arena == null || !BoatRaceMiniGame.namespace().equals(arena.namespace())) {
                return;
            }
            if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING || !arena.hasPlayer(attacker) || !arena.hasPlayer(target)) {
                return;
            }

            Vector push = snowball.getVelocity().clone();
            if (push.lengthSquared() < 1.0e-6d) {
                push = attacker.getLocation().getDirection();
            }
            push.setY(0.0d);
            if (push.lengthSquared() < 1.0e-6d) {
                return;
            }

            push.normalize().multiply(SNOWBALL_PUSH_STRENGTH).setY(SNOWBALL_PUSH_LIFT);
            Entity targetEntity = target.getVehicle() != null ? target.getVehicle() : target;
            targetEntity.setVelocity(push);
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 1.4f);
        });
    }

    private void prepareStartingGrid(@NotNull MiniGameArena arena) {
        arena.stopWinnerCelebration();
        clearRaceState(arena);
        boatRace.setWinner(arena, null);

        List<Player> players = orderedPlayers(arena);
        List<Location> grid = boatRace.startingGrid(arena);
        Location finishLookTarget = finishLookTarget(arena);
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int slot = Math.clamp(grid.size() - 1, 0, i);
            boatRace.assignedGridSlots(arena).put(player.getUniqueId(), slot);
            Location slotLocation = orientTowardFinish(grid.get(slot).clone(), finishLookTarget);
            boatRace.checkpointLocations(arena).put(player.getUniqueId(), slotLocation.clone());
            boatRace.lapProgress(arena).put(player.getUniqueId(), 1);
            boatRace.stageProgress(arena).put(player.getUniqueId(), 0);
            positionPlayerOnStartingGrid(arena, player, slotLocation);
        }

        for (Player spectator : arena.getSpectators()) {
            Location spectatorSpawn = arena.getSpectatorSpawn();
            if (spectatorSpawn != null) {
                spectator.teleport(spectatorSpawn);
            }
        }
    }

    private void prepareRunningState(@NotNull MiniGameArena arena) {
        boatRace.markRaceStarted(arena);
        for (Player player : arena.getPlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(PlayerUtil.getMaxHealth(player));
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setFireTicks(0);
            player.setFallDistance(0.0f);
            ensureSnowballSupply(player);
            remountPlayerIfNeeded(arena, player);
        }
    }

    private void resetArena(@NotNull MiniGameArena arena) {
        arena.stopWinnerCelebration();
        clearRaceState(arena);
        arena.removeAllOccupants();
    }

    private void clearRaceState(@NotNull MiniGameArena arena) {
        despawnAllBoats(arena);
        boatRace.finishOrder(arena).clear();
        boatRace.lapProgress(arena).clear();
        tntBounceCooldowns(arena).clear();
        tntBounceKeys(arena).clear();
        boatRace.stageProgress(arena).clear();
        boatRace.assignedGridSlots(arena).clear();
        boatRace.checkpointLocations(arena).clear();
        boatRace.clearRaceTimer(arena);
        boatRace.setWinner(arena, null);
    }

    private void removePlayerState(@NotNull MiniGameArena arena, @NotNull Player player) {
        despawnBoat(arena, player.getUniqueId());
        boatRace.joinOrder(arena).remove(player.getUniqueId());
        boatRace.lapProgress(arena).remove(player.getUniqueId());
        tntBounceCooldowns(arena).remove(player.getUniqueId());
        tntBounceKeys(arena).remove(player.getUniqueId());
        boatRace.assignedGridSlots(arena).remove(player.getUniqueId());
        boatRace.stageProgress(arena).remove(player.getUniqueId());
        boatRace.checkpointLocations(arena).remove(player.getUniqueId());
    }

    private void handleStageEnter(@NotNull MiniGameArena arena, @NotNull Player player, int stageIndex, @NotNull Location checkpoint) {
        UUID uuid = player.getUniqueId();
        if (boatRace.hasFinished(arena, uuid)) {
            return;
        }
        Map<UUID, Integer> progress = boatRace.stageProgress(arena);
        int expectedStage = progress.getOrDefault(uuid, 0);
        if (expectedStage != stageIndex) {
            if (stageIndex > expectedStage) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
                arena.warn(player, "<red>You skipped checkpoint " + (expectedStage + 1) + ". Go back before continuing.</red>");
            }
            return;
        }

        progress.put(uuid, stageIndex + 1);
        boatRace.checkpointLocations(arena).put(uuid, checkpoint.clone());
        arena.info(player, "<aqua>Checkpoint " + (stageIndex + 1) + " reached.</aqua>");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
    }

    private void finishRace(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return;
        }
        if (boatRace.hasFinished(arena, player.getUniqueId())) {
            return;
        }

        int currentLap = boatRace.currentLap(arena, player.getUniqueId());
        int totalLaps = boatRace.laps(arena);
        if (currentLap < totalLaps) {
            int nextLap = currentLap + 1;
            boatRace.lapProgress(arena).put(player.getUniqueId(), nextLap);
            boatRace.stageProgress(arena).put(player.getUniqueId(), 0);
            boatRace.checkpointLocations(arena).put(player.getUniqueId(), player.getLocation().clone());
            showLapAdvanceTitle(player, nextLap, totalLaps);
            String lapLabel = nextLap == totalLaps ? "Final lap" : "Lap";
            arena.info(player, "<gold>Lap " + currentLap + " complete.</gold> <aqua>" + lapLabel + ":</aqua> <yellow>" + nextLap + "/" + totalLaps + "</yellow>");
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.8f, 1.1f);
            syncCheckpointProgressAtLocation(arena, player, player.getLocation());
            return;
        }

        long startedAt = boatRace.raceStartMillis(arena);
        long durationMillis = startedAt <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAt);
        boatRace.finishOrder(arena).add(player.getUniqueId());
        boatRace.checkpointLocations(arena).put(player.getUniqueId(), player.getLocation().clone());

        BoatRaceMiniGame.FinishRecord finishRecord = boatRace.recordFinish(arena, player, durationMillis);
        int place = boatRace.finishOrder(arena).size();
        if (finishRecord.durationMillis() > 0L) {
            if (finishRecord.arenaBest()) {
                arena.success(player, "Finished " + ordinal(place) + " in " + boatRace.formatMillis(finishRecord.durationMillis()) + ". New arena record!");
            } else if (finishRecord.personalBest()) {
                arena.success(player, "Finished " + ordinal(place) + " in " + boatRace.formatMillis(finishRecord.durationMillis()) + ". New personal best!");
            } else {
                String personalBest = finishRecord.previousBestMillis() > 0L
                    ? boatRace.formatMillis(finishRecord.previousBestMillis())
                    : "-";
                arena.success(player, "Finished " + ordinal(place) + " in " + boatRace.formatMillis(finishRecord.durationMillis()) + ". Personal best: " + personalBest);
            }
        }

        if (place == 1) {
            boatRace.setWinner(arena, player);
            broadcastToOccupants(arena, "<gold>" + player.getName() + "</gold> <gray>crossed the line first.</gray>");
        } else {
            broadcastToOccupants(arena, "<aqua>" + player.getName() + "</aqua> <gray>finished in " + ordinal(place) + " place.</gray>");
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);

        if (allActiveRacersFinished(arena)) {
            concludeRace(arena);
        }
    }

    private void resetToCheckpoint(@NotNull MiniGameArena arena, @NotNull Player player, @Nullable String message) {
        Location checkpoint = boatRace.checkpointLocations(arena).get(player.getUniqueId());
        if (checkpoint == null) {
            checkpoint = fallbackGridLocation(arena, player);
        }
        if (checkpoint == null) {
            checkpoint = arena.getLobbySpawn();
        }
        if (checkpoint == null) {
            return;
        }

        player.setHealth(PlayerUtil.getMaxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            ensureSnowballSupply(player);
        }
        spawnBoat(arena, player, checkpoint);
        playCheckpointDeniedSequence(arena, player);
        if (message != null && !message.isBlank()) {
            arena.warn(player, message);
        }
    }

    private void playCheckpointDeniedSequence(@NotNull MiniGameArena arena, @NotNull Player player) {
        long[] delays = {1L, 4L, 7L, 10L, 14L, 18L, 22L};
        float[] pitches = {1.45f, 1.28f, 1.14f, 1.0f, 0.86f, 0.74f, 0.62f};
        float[] volumes = {0.7f, 0.7f, 0.68f, 0.66f, 0.64f, 0.6f, 0.56f};

        for (int i = 0; i < delays.length; i++) {
            final float pitch = pitches[i];
            final float volume = volumes[i];
            api.tasks().runLater(delays[i], () -> {
                if (!player.isOnline() || !arena.hasPlayer(player)) {
                    return;
                }
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, volume, pitch);
            });
        }
    }

    private void ensureSnowballSupply(@NotNull Player player) {
        clearSnowballSupply(player);
        ItemStack snowballs = new ItemStack(Material.SNOWBALL, SNOWBALL_STACK_SIZE);
        player.getInventory().setItem(SNOWBALL_SLOT, snowballs);
        player.updateInventory();
    }

    private void clearSnowballSupply(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            clearSnowballSupply(player);
        }
        for (Player spectator : arena.getSpectators()) {
            clearSnowballSupply(spectator);
        }
    }

    private void clearSnowballSupply(@NotNull Player player) {
        boolean updated = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == Material.SNOWBALL) {
                player.getInventory().setItem(slot, null);
                updated = true;
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.SNOWBALL) {
            player.getInventory().setItemInOffHand(null);
            updated = true;
        }
        if (updated) {
            player.updateInventory();
        }
    }

    @SuppressWarnings("unchecked")
    private @NotNull Map<UUID, Long> tntBounceCooldowns(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("tntBounceCooldowns", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull Map<UUID, String> tntBounceKeys(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("tntBounceKeys", Map.class, LinkedHashMap::new);
    }

    private void maybeBounceOnTnt(@NotNull MiniGameArena arena,
                                  @NotNull Player rider,
                                  @NotNull Boat boat,
                                  @Nullable Location from,
                                  @Nullable Location to) {
        Location contact = findTntBounceContact(from, to);
        if (contact == null) {
            return;
        }

        String bounceKey = tntBounceKey(contact);
        long now = System.currentTimeMillis();
        Long cooldownUntil = tntBounceCooldowns(arena).get(rider.getUniqueId());
        String cooldownKey = tntBounceKeys(arena).get(rider.getUniqueId());
        if (cooldownUntil != null && cooldownUntil > now && bounceKey.equals(cooldownKey)) {
            return;
        }

        Vector velocity = tntBounceVelocity(boat, from, to);
        boat.setVelocity(velocity);
        sustainTntBounceMomentum(arena, rider, boat, velocity);
        rider.playSound(rider.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.35f);
        tntBounceCooldowns(arena).put(rider.getUniqueId(), now + TNT_BOUNCE_COOLDOWN_MILLIS);
        tntBounceKeys(arena).put(rider.getUniqueId(), bounceKey);
    }

    private boolean isTntBounceBlock(@NotNull Location location) {
        return tntBounceSource(location) != null;
    }

    private @Nullable Location tntBounceSource(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        if (location.getBlock().getType() == Material.TNT) {
            return location.getBlock().getLocation();
        }

        Location below = location.clone().subtract(0.0d, 0.75d, 0.0d);
        if (below.getBlock().getType() == Material.TNT) {
            return below.getBlock().getLocation();
        }

        return null;
    }

    private @Nullable Location findTntBounceContact(@Nullable Location from, @Nullable Location to) {
        if (to == null || to.getWorld() == null) {
            return null;
        }

        Location direct = tntBounceSource(to);
        if (direct != null) {
            return direct;
        }

        if (from == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return null;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double maxDelta = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int steps = Math.max(1, (int) Math.ceil(maxDelta * 4.0d));

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Location sample = new Location(
                to.getWorld(),
                from.getX() + (dx * t),
                from.getY() + (dy * t),
                from.getZ() + (dz * t)
            );
            Location source = tntBounceSource(sample);
            if (source != null) {
                return source;
            }
        }

        return null;
    }

    private @NotNull String tntBounceKey(@NotNull Location source) {
        return source.getWorld().getName() + ":" + source.getBlockX() + ":" + source.getBlockY() + ":" + source.getBlockZ();
    }

    private @NotNull Vector tntBounceVelocity(@NotNull Boat boat, @Nullable Location from, @Nullable Location to) {
        Vector horizontal = boat.getVelocity().clone().setY(0.0d);
        if (horizontal.lengthSquared() < 1.0e-6d) {
            horizontal = movementDirection(from, to);
        }
        if (horizontal.lengthSquared() < 1.0e-6d) {
            horizontal = boat.getLocation().getDirection().setY(0.0d);
        }
        if (horizontal.lengthSquared() < 1.0e-6d) {
            horizontal = new Vector(1.0d, 0.0d, 0.0d);
        }

        double baseSpeed = Math.max(horizontal.length(), TNT_BOUNCE_FALLBACK_SPEED);
        horizontal.normalize().multiply(Math.max(baseSpeed * TNT_BOUNCE_HORIZONTAL_MULTIPLIER, TNT_BOUNCE_MIN_HORIZONTAL_SPEED));
        return new Vector(horizontal.getX(), TNT_BOUNCE_VERTICAL_VELOCITY, horizontal.getZ());
    }

    private @NotNull Vector movementDirection(@Nullable Location from, @Nullable Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return new Vector();
        }

        return new Vector(to.getX() - from.getX(), 0.0d, to.getZ() - from.getZ());
    }

    private void sustainTntBounceMomentum(@NotNull MiniGameArena arena,
                                          @NotNull Player rider,
                                          @NotNull Boat boat,
                                          @NotNull Vector launchVelocity) {
        for (long delay : TNT_BOUNCE_SUSTAIN_DELAYS) {
            api.tasks().runLater(delay, () -> {
                if (!rider.isOnline() || !arena.hasPlayer(rider) || !ownsBoat(arena, rider, boat.getUniqueId()) || !boat.isValid()) {
                    return;
                }

                Vector currentVelocity = boat.getVelocity().clone();
                boat.setVelocity(new Vector(
                    launchVelocity.getX(),
                    Math.max(currentVelocity.getY(), TNT_BOUNCE_SUSTAIN_VERTICAL_VELOCITY),
                    launchVelocity.getZ()
                ));
            });
        }
    }

    private void syncCheckpointProgressAtLocation(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location location) {
        UUID playerId = player.getUniqueId();
        List<SCRegion> stages = boatRace.stageRegions(arena);
        while (true) {
            int expectedStage = boatRace.stageProgress(arena).getOrDefault(playerId, 0);
            if (expectedStage >= stages.size()) {
                return;
            }

            SCRegion stage = stages.get(expectedStage);
            if (stage == null || !stage.contains(location)) {
                return;
            }

            handleStageEnter(arena, player, expectedStage, location);
        }
    }

    private void positionPlayerOnStartingGrid(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location location) {
        Location spawn = location.clone();
        if (spawn.getWorld() == null) {
            return;
        }

        // Join teleports are queued to the next tick. Defer grid placement until after those
        // complete so the lobby teleport cannot pull racers back out of their start boats.
        api.tasks().nextTick(() -> {
            if (!arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.STARTING) {
                return;
            }

            player.teleport(spawn);
            api.tasks().nextTick(() -> {
                if (!arena.hasPlayer(player)) {
                    return;
                }
                if (arena.getStatus() != MiniGameArena.ArenaStatus.STARTING && arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                    return;
                }

                spawnBoat(arena, player, spawn);
            });
        });
    }

    private void spawnBoat(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location location) {
        despawnBoat(arena, player.getUniqueId());

        Location spawn = location.clone();
        if (spawn.getWorld() == null) {
            return;
        }

        player.teleport(spawn);

        Entity entity = spawn.getWorld().spawnEntity(spawn, EntityType.OAK_BOAT);
        if (!(entity instanceof Boat boat)) {
            entity.remove();
            return;
        }

        boat.setRotation(spawn.getYaw(), spawn.getPitch());
        boat.setInvulnerable(true);
        boat.setPersistent(false);
        boat.setGravity(true);
        boat.setSilent(true);
        boat.setVelocity(boat.getVelocity().zero());
        if (!boat.addPassenger(player)) {
            api.tasks().nextTick(() -> {
                if (boat.isValid() && arena.hasPlayer(player) && !boat.getPassengers().contains(player)) {
                    boat.addPassenger(player);
                }
            });
        }
        boatRace.boatAssignments(arena).put(player.getUniqueId(), boat.getUniqueId());
    }

    private void despawnBoat(@NotNull MiniGameArena arena, @NotNull UUID playerId) {
        UUID boatId = boatRace.boatAssignments(arena).remove(playerId);
        if (boatId == null) {
            return;
        }
        Entity boat = findEntity(boatId);
        if (boat != null) {
            boat.remove();
        }
    }

    private void despawnAllBoats(@NotNull MiniGameArena arena) {
        for (UUID playerId : new LinkedHashSet<>(boatRace.boatAssignments(arena).keySet())) {
            despawnBoat(arena, playerId);
        }
    }

    private void remountPlayerIfNeeded(@NotNull MiniGameArena arena, @NotNull Player player) {
        UUID boatId = boatRace.boatAssignments(arena).get(player.getUniqueId());
        Entity entity = boatId == null ? null : findEntity(boatId);
        if (entity instanceof Boat boat) {
            if (!boat.getPassengers().contains(player)) {
                boat.addPassenger(player);
            }
            return;
        }

        Location checkpoint = boatRace.checkpointLocations(arena).get(player.getUniqueId());
        if (checkpoint == null) {
            checkpoint = assignedGridLocation(arena, player);
        }
        if (checkpoint != null) {
            spawnBoat(arena, player, checkpoint);
        }
    }

    private @Nullable Location assignedGridLocation(@NotNull MiniGameArena arena, @NotNull Player player) {
        Integer slot = boatRace.assignedGridSlots(arena).get(player.getUniqueId());
        if (slot == null) {
            return null;
        }
        List<Location> grid = boatRace.startingGrid(arena);
        if (slot < 0 || slot >= grid.size()) {
            return null;
        }
        return orientTowardFinish(grid.get(slot).clone(), finishLookTarget(arena));
    }

    private @Nullable Location fallbackGridLocation(@NotNull MiniGameArena arena, @NotNull Player player) {
        Location assigned = assignedGridLocation(arena, player);
        if (assigned != null) {
            return assigned;
        }
        List<Location> grid = boatRace.startingGrid(arena);
        return grid.isEmpty() ? null : orientTowardFinish(grid.getFirst().clone(), finishLookTarget(arena));
    }

    private @NotNull List<Player> orderedPlayers(@NotNull MiniGameArena arena) {
        List<UUID> order = boatRace.joinOrder(arena);
        List<Player> players = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(order)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && arena.hasPlayer(player)) {
                players.add(player);
            }
        }
        for (Player player : arena.getPlayers()) {
            if (!players.contains(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private void registerJoinOrder(@NotNull MiniGameArena arena, @NotNull Player player) {
        List<UUID> order = boatRace.joinOrder(arena);
        if (!order.contains(player.getUniqueId())) {
            order.add(player.getUniqueId());
        }
    }

    private @Nullable Player currentLeader(@NotNull MiniGameArena arena) {
        BoatRaceMiniGame.RaceStanding leader = boatRace.standings(arena).stream().findFirst().orElse(null);
        return leader == null ? null : leader.player();
    }

    private boolean allActiveRacersFinished(@NotNull MiniGameArena arena) {
        List<Player> players = arena.getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        for (Player player : players) {
            if (!boatRace.hasFinished(arena, player.getUniqueId())) {
                return false;
            }
        }
        return true;
    }

    private void concludeRace(@NotNull MiniGameArena arena) {
        if ("-".equals(boatRace.winnerName(arena))) {
            boatRace.setWinner(arena, currentLeader(arena));
        }
        arena.setStatus(MiniGameArena.ArenaStatus.ENDING, boatRace.endingSeconds(arena));
    }

    private @Nullable Player resolveWinnerPlayer(@NotNull MiniGameArena arena) {
        UUID winnerUuid = arena.get("winnerUuid", UUID.class);
        if (winnerUuid == null) {
            return null;
        }
        return Bukkit.getPlayer(winnerUuid);
    }

    private boolean ownsBoat(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull UUID boatId) {
        UUID assigned = boatRace.boatAssignments(arena).get(player.getUniqueId());
        return assigned != null && assigned.equals(boatId);
    }

    private @Nullable MiniGameArena arenaForBoat(@NotNull UUID boatId) {
        for (MiniGameArena arena : boatRace.minigame().arenas()) {
            if (boatRace.boatAssignments(arena).containsValue(boatId)) {
                return arena;
            }
        }
        return null;
    }

    private @Nullable Entity findEntity(@NotNull UUID uuid) {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private void teleportPlayersToLobby(@NotNull MiniGameArena arena) {
        Location lobby = arena.getLobbySpawn();
        if (lobby == null) {
            return;
        }
        for (Player player : arena.getPlayers()) {
            if (!player.getWorld().equals(lobby.getWorld()) || player.getLocation().distanceSquared(lobby) > 4.0d) {
                player.teleport(lobby);
            }
        }
    }

    private void startWinnerCelebration(@NotNull MiniGameArena arena, @NotNull Player winner) {
        List<Location> anchors = List.of(winner.getLocation().clone());
        arena.startWinnerCelebration(anchors, boatRace.endingSeconds(arena), Color.AQUA, Color.BLUE, Color.WHITE);
    }

    private void broadcastToOccupants(@NotNull MiniGameArena arena, @NotNull String message) {
        for (Player occupant : arena.getOccupants()) {
            arena.info(occupant, message);
        }
    }

    private void playSoundToOccupants(@NotNull MiniGameArena arena, @NotNull Sound sound, float volume, float pitch) {
        for (Player occupant : arena.getOccupants()) {
            occupant.playSound(occupant.getLocation(), sound, volume, pitch);
        }
    }

    private void showStartingCountdownTitle(@NotNull MiniGameArena arena, int secondsRemaining) {
        arena.showStartingCountdownTitle(secondsRemaining, "<gold>Race starts in</gold>");
    }

    private void showRaceStartTitle(@NotNull MiniGameArena arena) {
        arena.showTitle(
            "<gradient:#22c55e:#14b8a6><bold>GO!</bold></gradient>",
            "<aqua>Paddle hard.</aqua>",
            0,
            1000,
            400
        );
    }

    private void showLapAdvanceTitle(@NotNull Player player, int lap, int totalLaps) {
        String subtitle = lap == totalLaps
            ? "<gold>Final lap</gold> <yellow>" + lap + "/" + totalLaps + "</yellow>"
            : "<yellow>" + lap + "/" + totalLaps + "</yellow>";
        player.showTitle(Title.title(
            TextUtil.colourise("<aqua><bold>Lap " + lap + "</bold></aqua>"),
            TextUtil.colourise(subtitle),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1400), Duration.ofMillis(500))
        ));
    }

    private @NotNull String ordinal(int value) {
        int mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return value + "th";
        }
        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }

    private boolean hasDriftedFromGrid(@NotNull Location currentLocation, @NotNull Location gridLocation) {
        double dx = currentLocation.getX() - gridLocation.getX();
        double dz = currentLocation.getZ() - gridLocation.getZ();
        return (dx * dx) + (dz * dz) > 0.0025d;
    }

    private @Nullable Location finishLookTarget(@NotNull MiniGameArena arena) {
        SCRegion finish = arena.get("finishRegion", SCRegion.class);
        if (finish == null) {
            return null;
        }

        Location min = finish.getMinimumLocation();
        Location max = finish.getMaximumLocation();
        if (min == null || max == null || min.getWorld() == null || max.getWorld() == null) {
            return null;
        }

        return new Location(
            min.getWorld(),
            (min.getX() + max.getX()) / 2.0d,
            (min.getY() + max.getY()) / 2.0d,
            (min.getZ() + max.getZ()) / 2.0d
        );
    }

    private @NotNull Location orientTowardFinish(@NotNull Location source, @Nullable Location finishTarget) {
        if (finishTarget == null || finishTarget.getWorld() == null || source.getWorld() == null) {
            return source;
        }
        if (!source.getWorld().equals(finishTarget.getWorld())) {
            return source;
        }

        double dx = finishTarget.getX() - source.getX();
        double dz = finishTarget.getZ() - source.getZ();
        if (Math.abs(dx) < 1.0e-6 && Math.abs(dz) < 1.0e-6) {
            return source;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        source.setYaw(yaw);
        source.setPitch(0.0f);
        return source;
    }

    private void registerRegionListeners(@NotNull MiniGameArena arena) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion != null) {
            api.regions().addListener(listenerPrefix(arena.id()) + "boundary", arenaRegion, new RegionListener() {
                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                    if (arena.hasPlayer(player)
                        && (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING
                            || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING
                            || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING)) {
                        resetToCheckpoint(arena, player, "You left the course. Returned to your last checkpoint.");
                    } else if (arena.hasOccupant(player)
                        && arena.getStatus() == MiniGameArena.ArenaStatus.RESETTING) {
                        arena.removeOccupant(player);
                    }
                }
            });
        }

        SCRegion finishRegion = arena.get("finishRegion", SCRegion.class);
        if (finishRegion != null) {
            api.regions().addListener(listenerPrefix(arena.id()) + "finish", finishRegion, new RegionListener() {
                @Override
                public void onEnter(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                    if (!arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                        return;
                    }
                    if (boatRace.hasFinished(arena, player.getUniqueId())) {
                        return;
                    }
                    if (boatRace.stageProgress(arena.getPlayer(player)) < boatRace.stageCount(arena)) {
                        return;
                    }
                    finishRace(arena, player);
                }
            });
        }

        List<SCRegion> stages = boatRace.stageRegions(arena);
        for (int i = 0; i < stages.size(); i++) {
            final int stageIndex = i;
            SCRegion stage = stages.get(i);
            api.regions().addListener(listenerPrefix(arena.id()) + "stage_" + stageIndex, stage, new RegionListener() {
                @Override
                public void onEnter(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                    if (!arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                        return;
                    }
                    handleStageEnter(arena, player, stageIndex, to == null ? player.getLocation() : to);
                }
            });
        }
    }

    private void removeRegionListeners(@NotNull String arenaId) {
        api.regions().removeListener(listenerPrefix(arenaId) + "*");
    }

    private String listenerPrefix(String arenaId) {
        return NamespaceId.of(BoatRaceMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }
}
