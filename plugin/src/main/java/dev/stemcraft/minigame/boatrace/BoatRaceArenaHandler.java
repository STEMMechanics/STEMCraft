package dev.stemcraft.minigame.boatrace;

import com.destroystokyo.paper.Title;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoatRaceArenaHandler implements MiniGameArenaHandler {
    private static final int STARTING_COUNTDOWN_SECONDS = 10;
    private static final int RUNNING_COUNTDOWN_SECONDS = 600;
    private static final int ENDING_COUNTDOWN_SECONDS = 12;

    private final STEMCraftAPI api;
    private final BoatRaceMiniGame boatRace;

    public BoatRaceArenaHandler(STEMCraftAPI api, BoatRaceMiniGame boatRace) {
        this.api = api;
        this.boatRace = boatRace;
        registerVehicleListeners();
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
        if (!startingGrid.isEmpty() && arena.getMaxPlayers() > startingGrid.size()) {
            result.addError("Max players exceeds configured starting grid slots.", "maxPlayers");
        }

        if (arenaRegion != null && finishRegion != null && !arenaRegion.contains(finishRegion)) {
            result.addError("Finish region must be inside the arena region.", "finishRegion");
        }

        for (int i = 0; i < stageRegions.size(); i++) {
            SCRegion stage = stageRegions.get(i);
            if (arenaRegion != null && !arenaRegion.contains(stage)) {
                result.addError("Checkpoint " + (i + 1) + " must be inside the arena region.", "stages." + i);
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

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion != null) {
            api.regions().addListener(listenerPrefix(arena.id()) + "boundary", arenaRegion, new RegionListener() {
                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                    if (arena.hasPlayer(player)
                        && (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING)) {
                        resetToCheckpoint(arena, player, "You left the course. Returned to your checkpoint.");
                    } else if (arena.hasOccupant(player)
                        && (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING || arena.getStatus() == MiniGameArena.ArenaStatus.RESETTING)) {
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

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        despawnAllBoats(arena);
        api.regions().removeListener(listenerPrefix(arena.id()) + "*");
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
                resetToCheckpoint(arena, player, "You crashed out. Returned to your checkpoint.");
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
            resetTitles(arena);
            arena.stopWinnerCelebration();
            clearRaceState(arena);
            teleportPlayersToLobby(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.STARTING) {
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
            Player winner = resolveWinnerPlayer(arena);
            if (winner != null) {
                startWinnerCelebration(arena, winner);
                broadcastToOccupants(arena, "<gold>Race Over!</gold> <yellow>" + winner.getName() + "</yellow> <gray>wins the race.</gray>");
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
            Player leader = currentLeader(arena);
            boatRace.setWinner(arena, leader);
            arena.setStatus(MiniGameArena.ArenaStatus.ENDING, ENDING_COUNTDOWN_SECONDS);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        registerJoinOrder(arena, player);
        boatRace.stageProgress(arena).put(player.getUniqueId(), 0);
        boatRace.checkpointLocations(arena).put(player.getUniqueId(), fallbackGridLocation(arena, player));

        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
            api.tasks().nextTick(() -> {
                if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
                    arena.setStatus(MiniGameArena.ArenaStatus.STARTING, STARTING_COUNTDOWN_SECONDS);
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
            if (arena.numPlayers() == 1) {
                Player remaining = arena.getPlayers().getFirst();
                boatRace.setWinner(arena, remaining);
                arena.setStatus(MiniGameArena.ArenaStatus.ENDING, ENDING_COUNTDOWN_SECONDS);
            } else if (arena.numPlayers() < arena.getMinPlayers()) {
                arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
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
            if (playerDoesntHaveBoat(arena, player, event.getVehicle().getUniqueId())) {
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
            if (playerDoesntHaveBoat(arena, rider, boat.getUniqueId())) {
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
        boatRace.stageProgress(arena).clear();
        boatRace.assignedGridSlots(arena).clear();
        boatRace.checkpointLocations(arena).clear();
        boatRace.clearRaceTimer(arena);
        boatRace.setWinner(arena, null);
    }

    private void removePlayerState(@NotNull MiniGameArena arena, @NotNull Player player) {
        despawnBoat(arena, player.getUniqueId());
        boatRace.joinOrder(arena).remove(player.getUniqueId());
        boatRace.assignedGridSlots(arena).remove(player.getUniqueId());
        boatRace.stageProgress(arena).remove(player.getUniqueId());
        boatRace.checkpointLocations(arena).remove(player.getUniqueId());
    }

    private void handleStageEnter(@NotNull MiniGameArena arena, @NotNull Player player, int stageIndex, @NotNull Location checkpoint) {
        UUID uuid = player.getUniqueId();
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

    private void finishRace(@NotNull MiniGameArena arena, @NotNull Player winner) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return;
        }

        long startedAt = boatRace.raceStartMillis(arena);
        long durationMillis = startedAt <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAt);
        BoatRaceMiniGame.FinishRecord finishRecord = boatRace.recordFinish(arena, winner, durationMillis);
        if (finishRecord.durationMillis() > 0L) {
            if (finishRecord.arenaBest()) {
                arena.success(winner, "Finished in " + boatRace.formatMillis(finishRecord.durationMillis()) + ". New arena record!");
            } else if (finishRecord.personalBest()) {
                arena.success(winner, "Finished in " + boatRace.formatMillis(finishRecord.durationMillis()) + ". New personal best!");
            } else {
                String personalBest = finishRecord.previousBestMillis() > 0L
                    ? boatRace.formatMillis(finishRecord.previousBestMillis())
                    : "-";
                arena.success(winner, "Finished in " + boatRace.formatMillis(finishRecord.durationMillis()) + ". Personal best: " + personalBest);
            }
        }

        boatRace.setWinner(arena, winner);
        arena.setStatus(MiniGameArena.ArenaStatus.ENDING, ENDING_COUNTDOWN_SECONDS);
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
        spawnBoat(arena, player, checkpoint);
        if (message != null && !message.isBlank()) {
            arena.warn(player, message);
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

    private @Nullable Player resolveWinnerPlayer(@NotNull MiniGameArena arena) {
        UUID winnerUuid = arena.get("winnerUuid", UUID.class);
        if (winnerUuid == null) {
            return null;
        }
        return Bukkit.getPlayer(winnerUuid);
    }

    private boolean playerDoesntHaveBoat(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull UUID boatId) {
        UUID assigned = boatRace.boatAssignments(arena).get(player.getUniqueId());
        return assigned == null || !assigned.equals(boatId);
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
        arena.startWinnerCelebration(anchors, ENDING_COUNTDOWN_SECONDS, Color.AQUA, Color.BLUE, Color.WHITE);
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
        Title title = new Title(
            TextUtil.colouriseToSection("<gradient:#fde047:#f97316><bold>" + secondsRemaining + "</bold></gradient>"),
            TextUtil.colouriseToSection("<gold>Race starts in</gold>"),
            0,
            20,
            4
        );
        sendTitleToOccupants(arena, title);
    }

    private void showRaceStartTitle(@NotNull MiniGameArena arena) {
        Title title = new Title(
            TextUtil.colouriseToSection("<gradient:#22c55e:#14b8a6><bold>GO!</bold></gradient>"),
            TextUtil.colouriseToSection("<aqua>Paddle hard.</aqua>"),
            0,
            20,
            8
        );
        sendTitleToOccupants(arena, title);
    }

    private void sendTitleToOccupants(@NotNull MiniGameArena arena, @NotNull Title title) {
        for (Player occupant : arena.getOccupants()) {
            occupant.sendTitle(title);
        }
    }

    private void resetTitles(@NotNull MiniGameArena arena) {
        for (Player occupant : arena.getOccupants()) {
            occupant.resetTitle();
        }
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

    private String listenerPrefix(String arenaId) {
        return NamespaceId.of(BoatRaceMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }
}
