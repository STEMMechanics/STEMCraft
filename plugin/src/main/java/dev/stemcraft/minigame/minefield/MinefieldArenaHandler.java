package dev.stemcraft.minigame.minefield;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public class MinefieldArenaHandler implements MiniGameArenaHandler {
    private static final int COUNTDOWN_BEEP_THRESHOLD = 5;
    private static final int MARKER_TARGET_DISTANCE = 6;
    private static final String ORIGINAL_FIELD_SNAPSHOT_KEY = "originalFieldSnapshot";
    private static final String PLAYABLE_CELLS_KEY = "playableCells";
    private static final String MINE_LOCATIONS_KEY = "mineLocations";
    private static final String REVEALED_LOCATIONS_KEY = "revealedLocations";
    private static final String FLAGGED_LOCATIONS_KEY = "flaggedLocations";

    private final STEMCraftAPI api;
    private final MinefieldMiniGame minefield;

    public MinefieldArenaHandler(STEMCraftAPI api, MinefieldMiniGame minefield) {
        this.api = api;
        this.minefield = minefield;
    }

    public void initialize() {
        registerMovementListener();
        registerMarkerListener();
    }

    public void refreshArena(@NotNull MiniGameArena arena) {
        api.regions().removeListener(listenerPrefix(arena.id()) + "*");
        captureOriginalFieldSnapshot(arena);
        registerRegionListeners(arena);
        clearRoundState(arena);

        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            resetField(arena);
            resetParticipantsToStart(arena);
            resetSpectators(arena);
            if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
                queueAutoStartIfReady(arena);
            }
        }
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion arenaRegion = arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class);
        SCRegion startRegion = arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class);
        SCRegion fieldRegion = arena.get(MinefieldMiniGame.FIELD_REGION_KEY, SCRegion.class);
        SCRegion finishRegion = arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class);

        if (arena.getLobbySpawn() == null) {
            result.addError("Start region spawn could not be resolved.", "startRegion");
        }
        if (arena.getSpectatorSpawn() == null) {
            result.addError("Spectator spawn is not defined.", "spectatorSpawn");
        }
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arenaRegion");
        }
        if (startRegion == null) {
            result.addError("Start region is not defined.", "startRegion");
        }
        if (fieldRegion == null) {
            result.addError("Field region is not defined.", "fieldRegion");
        }
        if (finishRegion == null) {
            result.addError("Finish region is not defined.", "finishRegion");
        }
        if (arena.getMinPlayers() < 1) {
            result.addError("Minefield requires at least 1 minimum player.", "minPlayers");
        }
        if (arena.getMaxPlayers() < arena.getMinPlayers()) {
            result.addError("Max players cannot be lower than min players.", "maxPlayers");
        }
        if (minefield.lives(arena) < 0) {
            result.addError("Lives must be 0 or greater.", "lives");
        }

        if (arenaRegion != null && startRegion != null && !arenaRegion.contains(startRegion)) {
            result.addError("Start region must be contained within the arena region.", "startRegion");
        }
        if (arenaRegion != null && fieldRegion != null && !arenaRegion.contains(fieldRegion)) {
            result.addError("Field region must be contained within the arena region.", "fieldRegion");
        }
        if (arenaRegion != null && finishRegion != null && !arenaRegion.contains(finishRegion)) {
            result.addError("Finish region must be contained within the arena region.", "finishRegion");
        }

        if (fieldRegion != null) {
            Location min = fieldRegion.getMinimumLocation();
            Location max = fieldRegion.getMaximumLocation();
            if (min.getBlockY() != max.getBlockY()) {
                result.addError("Field region must be exactly one block tall.", "fieldRegion");
            }
            int totalCells = countPlayableCells(fieldRegion);
            if (totalCells <= 1) {
                result.addError("Field region must include more than one solid playable block.", "fieldRegion");
            }
            if (minefield.configuredMineCount(arena) >= totalCells) {
                result.addError("Mine count must leave at least one safe block.", "configuredMineCount");
            }
            if (startRegion != null && finishRegion != null) {
                FieldBounds bounds = new FieldBounds(min.getBlockX(), max.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockZ());
                Set<String> playable = playableCells(fieldRegion);
                Set<String> path = carveGuaranteedPath(
                    bounds,
                    playable,
                    MinefieldMiniGame.resolveSpawn(startRegion, arena.getLobbySpawn()),
                    centerOfRegion(finishRegion),
                    new Random(0L)
                );
                if (path.isEmpty()) {
                    result.addError("Field region must provide a solid path from the start side to the finish side.", "fieldRegion");
                }
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        refreshArena(arena);
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        api.regions().removeListener(listenerPrefix(arena.id()) + "*");
        clearRoundState(arena);
        restoreOriginalFieldSnapshot(arena);
    }

    @Override
    public HandlerEventResult onBlockPlace(MiniGameArena arena, Player player, Block block) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onBlockBreak(MiniGameArena arena, Player player, Block block) {
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onEntityDamage(MiniGameArena arena, EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !arena.hasOccupant(player)) {
            return HandlerEventResult.ALLOW;
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
            arena.stopWinnerCelebration();
            clearRoundState(arena);
            resetField(arena);
            resetParticipantsToStart(arena);
            resetSpectators(arena);
            minefield.setResultLine(arena, "Waiting for players");
            queueAutoStartIfReady(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.STARTING) {
            prepareNewRound(arena);
            preparePlayersForCountdown(arena);
            minefield.setResultLine(arena, "Round starting");
            broadcastToOccupants(arena, "<gold>Minefield is starting.</gold>");
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RUNNING) {
            preparePlayersForRun(arena);
            minefield.setResultLine(arena, "Cross the field");
            broadcastToOccupants(arena, "<blue>Run!</blue> <gray>Reveal a safe path to the finish.</gray>");
            playSoundToOccupants(arena, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.ENDING) {
            if (!"-".equals(minefield.winnerName(arena))) {
                Player winner = Bukkit.getPlayerExact(minefield.winnerName(arena));
                if (winner != null) {
                    arena.startWinnerCelebration(winner.getLocation(), 4);
                }
                broadcastToOccupants(arena, "<gold>" + minefield.resultLine(arena) + "</gold>");
                playSoundToOccupants(arena, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            } else {
                broadcastToOccupants(arena, "<red>" + minefield.resultLine(arena) + "</red>");
                playSoundToOccupants(arena, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.0f);
            }
            resetSpectators(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RESETTING) {
            arena.stopWinnerCelebration();
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
        }
    }

    @Override
    public void onArenaCountdownTick(MiniGameArena arena, int secondsRemaining) {
        if (secondsRemaining <= 0) {
            return;
        }

        if ((arena.getStatus() == MiniGameArena.ArenaStatus.STARTING || arena.getStatus() == MiniGameArena.ArenaStatus.ENDING)
            && secondsRemaining <= COUNTDOWN_BEEP_THRESHOLD) {
            float pitch = 1.0f + ((COUNTDOWN_BEEP_THRESHOLD - secondsRemaining) * 0.1f);
            playSoundToOccupants(arena, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, pitch);
            if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
                arena.showStartingCountdownTitle(secondsRemaining);
            }
        }
    }

    @Override
    public void onArenaCountdownEnd(MiniGameArena arena) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RUNNING);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        resetPlayerState(arena, player);
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            resetRoundPlayerState(arena, mgPlayer);
        }
        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING) {
            queueAutoStartIfReady(arena);
        }
        return arena.getLobbySpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        clearPlayerProgress(arena, player);
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            arena.setCountdown(0);
            return;
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING && activePlayerCount(arena) == 0) {
            endRoundWithoutWinner(arena, "Minefield failed. No runners remain.");
        }
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        onPlayerLeaveArena(arena, player);
    }

    void queueAutoStartIfReady(@NotNull MiniGameArena arena) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.WAITING) {
            return;
        }
        if (arena.numPlayers() < arena.getMinPlayers()) {
            return;
        }
        if (arena.get("suppressAutoStart", Boolean.class, false)) {
            return;
        }

        api.tasks().runLater(1L, () -> {
            if (arena.getStatus() != MiniGameArena.ArenaStatus.WAITING) {
                return;
            }
            if (arena.numPlayers() < arena.getMinPlayers()) {
                return;
            }
            if (arena.get("suppressAutoStart", Boolean.class, false)) {
                return;
            }
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, minefield.startCountdownSeconds(arena));
        });
    }

    int countAdjacentMines(int x, int z, @NotNull Set<String> mineLocations) {
        int adjacent = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (mineLocations.contains(cellKey(x + dx, z + dz))) {
                    adjacent++;
                }
            }
        }
        return adjacent;
    }

    @NotNull Set<String> carveGuaranteedPath(
        @NotNull FieldBounds bounds,
        @NotNull Set<String> playableCells,
        @NotNull Location start,
        @NotNull Location finish,
        @NotNull Random random
    ) {
        if (playableCells.isEmpty()) {
            return Set.of();
        }

        boolean alongX = Math.abs(finish.getX() - start.getX()) >= Math.abs(finish.getZ() - start.getZ());
        boolean increasing = alongX ? start.getX() <= finish.getX() : start.getZ() <= finish.getZ();
        Cell startCell = edgePlayableCell(bounds, playableCells, start, alongX, increasing ? 1 : -1);
        Cell finishCell = edgePlayableCell(bounds, playableCells, finish, alongX, increasing ? -1 : 1);
        if (startCell == null || finishCell == null) {
            return Set.of();
        }

        Queue<Cell> queue = new ArrayDeque<>();
        Map<String, String> previous = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(startCell);
        visited.add(cellKey(startCell.x(), startCell.z()));

        while (!queue.isEmpty()) {
            Cell current = queue.remove();
            if (current.equals(finishCell)) {
                return reconstructPath(startCell, finishCell, previous);
            }

            List<Cell> neighbours = new ArrayList<>(List.of(
                new Cell(current.x() + 1, current.z()),
                new Cell(current.x() - 1, current.z()),
                new Cell(current.x(), current.z() + 1),
                new Cell(current.x(), current.z() - 1)
            ));
            Collections.shuffle(neighbours, random);
            neighbours.sort(Comparator.comparingInt(cell -> distance(cell, finishCell)));

            for (Cell neighbour : neighbours) {
                String key = cellKey(neighbour.x(), neighbour.z());
                if (!bounds.contains(neighbour.x(), neighbour.z()) || !playableCells.contains(key) || !visited.add(key)) {
                    continue;
                }
                previous.put(key, cellKey(current.x(), current.z()));
                queue.add(neighbour);
            }
        }

        return Set.of();
    }

    @NotNull Set<String> buildMineLayout(
        @NotNull FieldBounds bounds,
        @NotNull Set<String> playableCells,
        int configuredMineCount,
        @NotNull Set<String> protectedCells,
        @NotNull Random random
    ) {
        List<String> candidates = new ArrayList<>();
        for (String key : playableCells) {
            if (!protectedCells.contains(key)) {
                candidates.add(key);
            }
        }

        if (candidates.isEmpty()) {
            return Set.of();
        }

        int target = Math.max(0, Math.min(candidates.size(), configuredMineCount));
        Collections.shuffle(candidates, random);
        return new LinkedHashSet<>(candidates.subList(0, target));
    }

    private void registerMovementListener() {
        api.events().register(PlayerMoveEvent.class, event -> {
            Location to = event.getTo();
            if (to == null || sameBlock(event.getFrom(), to)) {
                return;
            }

            Player player = event.getPlayer();
            MiniGameArena arena = minefield.minigame().findPlayer(player);
            if (arena == null || !MinefieldMiniGame.namespace().equals(arena.namespace())) {
                return;
            }

            if (arena.hasSpectator(player)) {
                keepSpectatorInArena(arena, player, to);
                return;
            }

            if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
                keepPlayerInStartRegion(arena, player, to);
                return;
            }

            if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING || !isPlayerActive(arena, player)) {
                return;
            }

            handleFieldStep(arena, player, to);
        });
    }

    private void registerMarkerListener() {
        api.events().register(PlayerInteractEvent.class, event -> {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }

            Player player = event.getPlayer();
            MiniGameArena arena = minefield.minigame().findPlayer(player);
            if (arena == null || !MinefieldMiniGame.namespace().equals(arena.namespace())) {
                return;
            }
            if (arena.hasSpectator(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING || !isPlayerActive(arena, player)) {
                return;
            }

            Block target = event.getClickedBlock();
            if (target == null) {
                target = player.getTargetBlockExact(MARKER_TARGET_DISTANCE);
            }
            if (target == null) {
                return;
            }

            toggleMarker(arena, player, target);
        });
    }

    private void registerRegionListeners(@NotNull MiniGameArena arena) {
        String prefix = listenerPrefix(arena.id());
        SCRegion finishRegion = arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class);
        SCRegion arenaRegion = arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class);

        if (finishRegion != null) {
            api.regions().addListener(prefix + "finish", finishRegion, new RegionListener() {
                @Override
                public void onEnter(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                    if (!arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING || !isPlayerActive(arena, player)) {
                        return;
                    }
                    handleRoundWin(arena, player);
                }
            });
        }

        if (arenaRegion != null) {
            api.regions().addListener(prefix + "arena", arenaRegion, new RegionListener() {
                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                    if (arena.hasPlayer(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING && isPlayerActive(arena, player)) {
                        triggerMine(arena, player, null, "Minefield failed. " + player.getName() + " left the arena.");
                    } else if (arena.hasSpectator(player)) {
                        resetSpectator(arena, player);
                    }
                }
            });
        }
    }

    private void handleFieldStep(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location to) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }

        if (to.getBlockX() < bounds.minX() || to.getBlockX() > bounds.maxX() || to.getBlockZ() < bounds.minZ() || to.getBlockZ() > bounds.maxZ()) {
            updateProgress(arena, player, to);
            return;
        }

        updateProgress(arena, player, to);
        if (!isPlayableCell(arena, to.getBlockX(), to.getBlockZ())) {
            return;
        }
        revealCell(arena, player, to.getBlockX(), to.getBlockZ());
    }

    private void revealCell(@NotNull MiniGameArena arena, @NotNull Player player, int startX, int startZ) {
        Set<String> mines = mineLocations(arena);
        String startKey = cellKey(startX, startZ);
        if (revealedLocations(arena).contains(startKey)) {
            return;
        }

        flaggedLocations(arena).remove(startKey);
        if (mines.contains(startKey)) {
            triggerMine(arena, player, new Cell(startX, startZ), "Minefield failed. " + player.getName() + " found a mine.");
            return;
        }

        if (!isPlayableCell(arena, startX, startZ)) {
            return;
        }

        int adjacent = countAdjacentMines(startX, startZ, mines);
        setFieldBlock(arena, startX, startZ, adjacent > 0 ? minefield.adjacentBlock(arena) : minefield.clearBlock(arena));
        revealedLocations(arena).add(startKey);
        minefield.setRevealedSafeCount(arena, minefield.revealedSafeCount(arena) + 1);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.25f);
    }

    private void toggleMarker(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Block target) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }

        int targetY = target.getY();
        if (targetY == bounds.y() + 1) {
            target = target.getRelative(0, -1, 0);
        }
        if (target.getY() != bounds.y() || !bounds.contains(target.getX(), target.getZ()) || !isPlayableCell(arena, target.getX(), target.getZ())) {
            return;
        }

        String key = cellKey(target.getX(), target.getZ());
        if (revealedLocations(arena).contains(key)) {
            return;
        }

        Set<String> flags = flaggedLocations(arena);
        if (flags.remove(key)) {
            setFieldBlock(arena, target.getX(), target.getZ(), minefield.hiddenBlock(arena));
            player.playSound(player.getLocation(), Sound.BLOCK_WOOL_BREAK, 0.5f, 1.2f);
            return;
        }

        flags.add(key);
        setFieldBlock(arena, target.getX(), target.getZ(), minefield.markerBlock(arena));
        player.playSound(player.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.5f, 1.0f);
    }

    private void handleRoundWin(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING || !isPlayerActive(arena, player)) {
            return;
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        long elapsed = elapsedRunMillis(mgPlayer);
        if (mgPlayer != null) {
            mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_TIME_MILLIS_KEY, elapsed);
        }

        long bestTime = minefield.bestTimeMillis(arena);
        if (elapsed > 0L && (bestTime == 0L || elapsed < bestTime)) {
            minefield.setBestTimeMillis(arena, elapsed);
        }

        minefield.setWinner(arena, player);
        minefield.setResultLine(arena, player.getName() + " cleared the minefield in " + MinefieldMiniGame.formatMillis(elapsed) + ".");
        arena.setStatus(MiniGameArena.ArenaStatus.ENDING, minefield.endingSeconds(arena));
    }

    private void triggerMine(@NotNull MiniGameArena arena, @NotNull Player player, @Nullable Cell cell, @NotNull String resultLine) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING || !isPlayerActive(arena, player)) {
            return;
        }

        if (cell != null) {
            flaggedLocations(arena).remove(cellKey(cell.x(), cell.z()));
            setFieldBlock(arena, cell.x(), cell.z(), minefield.triggeredMineBlock(arena));
            Location explosion = new Location(arena.world(), cell.x() + 0.5d, cellY(arena) + 0.5d, cell.z() + 0.5d);
            arena.world().createExplosion(explosion, 2.0f, false, false);
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null) {
            return;
        }

        mgPlayer.addDeath();
        int configuredLives = minefield.lives(arena);
        if (configuredLives == 0) {
            respawnRunner(arena, player, mgPlayer, configuredLives);
            return;
        }

        int livesLeft = Math.max(0, mgPlayer.get(MinefieldMiniGame.PLAYER_LIVES_LEFT_KEY, Integer.class, configuredLives) - 1);
        mgPlayer.set(MinefieldMiniGame.PLAYER_LIVES_LEFT_KEY, livesLeft);
        if (livesLeft > 0) {
            respawnRunner(arena, player, mgPlayer, livesLeft);
            return;
        }

        mgPlayer.set(MinefieldMiniGame.PLAYER_ELIMINATED_KEY, true);
        mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_TIME_MILLIS_KEY, elapsedRunMillis(mgPlayer));
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        if (arena.getSpectatorSpawn() != null) {
            player.teleport(arena.getSpectatorSpawn());
        }
        arena.info(player, "<red>You are out of lives.</red>");

        if (activePlayerCount(arena) == 0) {
            endRoundWithoutWinner(arena, resultLine);
        }
    }

    private void respawnRunner(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull MiniGamePlayer mgPlayer, int livesLeft) {
        resetRunnerState(player);
        if (arena.getLobbySpawn() != null) {
            player.teleport(arena.getLobbySpawn());
        }

        if (minefield.lives(arena) == 0) {
            arena.info(player, "<yellow>Mine triggered.</yellow> <gray>Restarting from the start.</gray>");
        } else {
            arena.info(player, "<yellow>Mine triggered.</yellow> <gray>Lives left: " + livesLeft + ".</gray>");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
        mgPlayer.set("progressPercent", 0);
    }

    private void endRoundWithoutWinner(@NotNull MiniGameArena arena, @NotNull String resultLine) {
        minefield.setWinner(arena, null);
        minefield.setResultLine(arena, resultLine);
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer != null && mgPlayer.get(MinefieldMiniGame.PLAYER_RUN_TIME_MILLIS_KEY, Long.class, 0L) <= 0L) {
                mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_TIME_MILLIS_KEY, elapsedRunMillis(mgPlayer));
            }
        }
        arena.setStatus(MiniGameArena.ArenaStatus.ENDING, minefield.endingSeconds(arena));
    }

    private void prepareNewRound(@NotNull MiniGameArena arena) {
        clearRoundState(arena);
        resetField(arena);
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer != null) {
                resetRoundPlayerState(arena, mgPlayer);
            }
        }

        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }

        SCRegion startRegion = arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class);
        SCRegion finishRegion = arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class);
        if (startRegion == null || finishRegion == null) {
            return;
        }

        Location start = arena.getLobbySpawn();
        Location finish = centerOfRegion(finishRegion);
        Set<String> playable = playableCells(arena);
        Set<String> path = carveGuaranteedPath(bounds, playable, start, finish, new Random());
        Set<String> mines = buildMineLayout(bounds, playable, minefield.configuredMineCount(arena), path, new Random());
        mineLocations(arena).clear();
        mineLocations(arena).addAll(mines);
        minefield.setMineCount(arena, mines.size());
    }

    private void preparePlayersForCountdown(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            resetPlayerState(arena, player);
            player.setWalkSpeed(0.0f);
        }
    }

    private void preparePlayersForRun(@NotNull MiniGameArena arena) {
        long startedAt = System.currentTimeMillis();
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer != null) {
                mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_START_MILLIS_KEY, startedAt);
                mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_TIME_MILLIS_KEY, 0L);
            }
            resetPlayerState(arena, player);
            player.setWalkSpeed(0.2f);
        }
    }

    private void resetParticipantsToStart(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            resetPlayerState(arena, player);
        }
    }

    private void resetSpectators(@NotNull MiniGameArena arena) {
        for (Player player : arena.getSpectators()) {
            resetSpectator(arena, player);
        }
    }

    private void resetSpectator(@NotNull MiniGameArena arena, @NotNull Player player) {
        Location target = arena.getSpectatorSpawn() != null ? arena.getSpectatorSpawn() : arena.getLobbySpawn();
        if (target != null) {
            player.teleport(target);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private void clearRoundState(@NotNull MiniGameArena arena) {
        mineLocations(arena).clear();
        revealedLocations(arena).clear();
        flaggedLocations(arena).clear();
        minefield.setWinner(arena, null);
        minefield.setMineCount(arena, 0);
        minefield.setRevealedSafeCount(arena, 0);
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer != null) {
                resetRoundPlayerState(arena, mgPlayer);
            }
        }
    }

    private void captureOriginalFieldSnapshot(@NotNull MiniGameArena arena) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }

        Map<String, BlockSnapshot> snapshot = originalFieldSnapshot(arena);
        Set<String> playable = playableCells(arena);
        snapshot.clear();
        playable.clear();
        World world = arena.world();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                Block block = world.getBlockAt(x, bounds.y(), z);
                BlockData blockData = block.getBlockData();
                String key = cellKey(x, z);
                snapshot.put(key, new BlockSnapshot(block.getType(), blockData.getAsString()));
                if (isPlayableFieldMaterial(block.getType())) {
                    playable.add(key);
                }
            }
        }
    }

    private void restoreOriginalFieldSnapshot(@NotNull MiniGameArena arena) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }

        World world = arena.world();
        for (Map.Entry<String, BlockSnapshot> entry : originalFieldSnapshot(arena).entrySet()) {
            Cell cell = parseCell(entry.getKey());
            if (cell == null) {
                continue;
            }
            Block block = world.getBlockAt(cell.x(), bounds.y(), cell.z());
            BlockSnapshot snapshot = entry.getValue();
            block.setType(snapshot.material(), false);
            block.setBlockData(Bukkit.createBlockData(snapshot.blockData()), false);
        }
    }

    private void resetField(@NotNull MiniGameArena arena) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }
        Material material = minefield.hiddenBlock(arena);
        World world = arena.world();
        for (String key : playableCells(arena)) {
            Cell cell = parseCell(key);
            if (cell == null) {
                continue;
            }
            world.getBlockAt(cell.x(), bounds.y(), cell.z()).setType(material, false);
        }
    }

    private void setFieldBlock(@NotNull MiniGameArena arena, int x, int z, @NotNull Material material) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null || !bounds.contains(x, z) || !isPlayableCell(arena, x, z)) {
            return;
        }
        arena.world().getBlockAt(x, bounds.y(), z).setType(material, false);
    }

    private void keepPlayerInStartRegion(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location to) {
        SCRegion startRegion = arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class);
        if (startRegion == null || startRegion.contains(to)) {
            return;
        }
        if (arena.getLobbySpawn() != null) {
            player.teleport(arena.getLobbySpawn());
        }
    }

    private void keepSpectatorInArena(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location to) {
        SCRegion arenaRegion = arena.get(MinefieldMiniGame.ARENA_REGION_KEY, SCRegion.class);
        if (arenaRegion == null || arenaRegion.contains(to)) {
            return;
        }
        resetSpectator(arena, player);
    }

    private void updateProgress(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location location) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null) {
            return;
        }

        SCRegion startRegion = arena.get(MinefieldMiniGame.START_REGION_KEY, SCRegion.class);
        SCRegion finishRegion = arena.get(MinefieldMiniGame.FINISH_REGION_KEY, SCRegion.class);
        if (startRegion == null || finishRegion == null) {
            return;
        }

        Location start = centerOfRegion(startRegion);
        Location finish = centerOfRegion(finishRegion);
        boolean alongX = Math.abs(finish.getX() - start.getX()) >= Math.abs(finish.getZ() - start.getZ());
        double startAxis = alongX ? start.getX() : start.getZ();
        double finishAxis = alongX ? finish.getX() : finish.getZ();
        double currentAxis = alongX ? location.getX() : location.getZ();
        double total = finishAxis - startAxis;
        if (Math.abs(total) < 0.0001d) {
            mgPlayer.set("progressPercent", 0);
            return;
        }

        double progress = ((currentAxis - startAxis) / total) * 100.0d;
        mgPlayer.set("progressPercent", clamp((int) Math.round(progress), 0, 100));
    }

    private void resetPlayerState(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        resetRunnerState(player);
        if (mgPlayer != null && mgPlayer.get(MinefieldMiniGame.PLAYER_ELIMINATED_KEY, Boolean.class, false)) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);
            if (arena.getSpectatorSpawn() != null) {
                player.teleport(arena.getSpectatorSpawn());
            }
            return;
        }

        if (arena.getLobbySpawn() != null) {
            player.teleport(arena.getLobbySpawn());
        }
    }

    private void resetRunnerState(@NotNull Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
    }

    private void clearPlayerProgress(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.set("progressPercent", 0);
            mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_START_MILLIS_KEY, 0L);
            mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_TIME_MILLIS_KEY, 0L);
            mgPlayer.set(MinefieldMiniGame.PLAYER_ELIMINATED_KEY, false);
            mgPlayer.set(MinefieldMiniGame.PLAYER_LIVES_LEFT_KEY, minefield.lives(arena));
        }
    }

    private void resetRoundPlayerState(@NotNull MiniGameArena arena, @NotNull MiniGamePlayer mgPlayer) {
        mgPlayer.setScore(0);
        mgPlayer.setDeaths(0);
        mgPlayer.setKills(0);
        mgPlayer.set("progressPercent", 0);
        mgPlayer.set(MinefieldMiniGame.PLAYER_ELIMINATED_KEY, false);
        mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_START_MILLIS_KEY, 0L);
        mgPlayer.set(MinefieldMiniGame.PLAYER_RUN_TIME_MILLIS_KEY, 0L);
        mgPlayer.set(MinefieldMiniGame.PLAYER_LIVES_LEFT_KEY, minefield.lives(arena));
    }

    private boolean isPlayerActive(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        return mgPlayer != null && !mgPlayer.get(MinefieldMiniGame.PLAYER_ELIMINATED_KEY, Boolean.class, false);
    }

    private int activePlayerCount(@NotNull MiniGameArena arena) {
        int count = 0;
        for (Player player : arena.getPlayers()) {
            if (isPlayerActive(arena, player)) {
                count++;
            }
        }
        return count;
    }

    private long elapsedRunMillis(@Nullable MiniGamePlayer player) {
        if (player == null) {
            return 0L;
        }
        long startedAt = player.get(MinefieldMiniGame.PLAYER_RUN_START_MILLIS_KEY, Long.class, 0L);
        if (startedAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - startedAt);
    }

    private boolean sameBlock(@NotNull Location from, @NotNull Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    private @Nullable FieldBounds bounds(@NotNull MiniGameArena arena) {
        SCRegion fieldRegion = arena.get(MinefieldMiniGame.FIELD_REGION_KEY, SCRegion.class);
        if (fieldRegion == null) {
            return null;
        }
        Location min = fieldRegion.getMinimumLocation();
        Location max = fieldRegion.getMaximumLocation();
        return new FieldBounds(min.getBlockX(), max.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockZ());
    }

    private int cellY(@NotNull MiniGameArena arena) {
        FieldBounds bounds = bounds(arena);
        return bounds == null ? arena.world().getMinHeight() : bounds.y();
    }

    private int countPlayableCells(@NotNull SCRegion fieldRegion) {
        return playableCells(fieldRegion).size();
    }

    @SuppressWarnings("unchecked")
    private @NotNull Map<String, BlockSnapshot> originalFieldSnapshot(@NotNull MiniGameArena arena) {
        return arena.getOrCreate(ORIGINAL_FIELD_SNAPSHOT_KEY, Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<String> mineLocations(@NotNull MiniGameArena arena) {
        return arena.getOrCreate(MINE_LOCATIONS_KEY, Set.class, LinkedHashSet::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<String> revealedLocations(@NotNull MiniGameArena arena) {
        return arena.getOrCreate(REVEALED_LOCATIONS_KEY, Set.class, LinkedHashSet::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<String> playableCells(@NotNull MiniGameArena arena) {
        return arena.getOrCreate(PLAYABLE_CELLS_KEY, Set.class, LinkedHashSet::new);
    }

    private @NotNull Set<String> playableCells(@NotNull SCRegion fieldRegion) {
        Set<String> playable = new LinkedHashSet<>();
        Location min = fieldRegion.getMinimumLocation();
        Location max = fieldRegion.getMaximumLocation();
        World world = fieldRegion.getWorld();
        if (world == null) {
            return playable;
        }

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                if (isPlayableFieldMaterial(world.getBlockAt(x, min.getBlockY(), z).getType())) {
                    playable.add(cellKey(x, z));
                }
            }
        }
        return playable;
    }

    private boolean isPlayableCell(@NotNull MiniGameArena arena, int x, int z) {
        return playableCells(arena).contains(cellKey(x, z));
    }

    private boolean isPlayableFieldMaterial(@NotNull Material material) {
        return !material.isAir();
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<String> flaggedLocations(@NotNull MiniGameArena arena) {
        return arena.getOrCreate(FLAGGED_LOCATIONS_KEY, Set.class, LinkedHashSet::new);
    }

    private @NotNull String listenerPrefix(@NotNull String arenaId) {
        return NamespaceId.of(MinefieldMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }

    private void broadcastToOccupants(@NotNull MiniGameArena arena, @NotNull String message) {
        for (Player player : arena.getOccupants()) {
            arena.info(player, message);
        }
    }

    private void playSoundToOccupants(@NotNull MiniGameArena arena, @NotNull Sound sound, float volume, float pitch) {
        for (Player player : arena.getOccupants()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private @NotNull String cellKey(int x, int z) {
        return x + "," + z;
    }

    private @Nullable Cell parseCell(@NotNull String key) {
        String[] parts = key.split(",", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new Cell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int distance(@NotNull Cell left, @NotNull Cell right) {
        return Math.abs(left.x() - right.x()) + Math.abs(left.z() - right.z());
    }

    private @Nullable Cell edgePlayableCell(
        @NotNull FieldBounds bounds,
        @NotNull Set<String> playableCells,
        @NotNull Location reference,
        boolean alongX,
        int direction
    ) {
        int edge = alongX
            ? (direction < 0 ? bounds.maxX() : bounds.minX())
            : (direction < 0 ? bounds.maxZ() : bounds.minZ());

        List<Cell> edgeCandidates = new ArrayList<>();
        for (String key : playableCells) {
            Cell cell = parseCell(key);
            if (cell == null) {
                continue;
            }
            if ((alongX && cell.x() == edge) || (!alongX && cell.z() == edge)) {
                edgeCandidates.add(cell);
            }
        }

        if (!edgeCandidates.isEmpty()) {
            edgeCandidates.sort(Comparator.comparingInt(cell -> alongX
                ? Math.abs(cell.z() - reference.getBlockZ())
                : Math.abs(cell.x() - reference.getBlockX())));
            return edgeCandidates.getFirst();
        }

        return nearestPlayableCell(playableCells, reference);
    }

    private @Nullable Cell nearestPlayableCell(@NotNull Set<String> playableCells, @NotNull Location reference) {
        Cell best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String key : playableCells) {
            Cell cell = parseCell(key);
            if (cell == null) {
                continue;
            }
            int distance = Math.abs(cell.x() - reference.getBlockX()) + Math.abs(cell.z() - reference.getBlockZ());
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    private @NotNull Set<String> reconstructPath(@NotNull Cell start, @NotNull Cell finish, @NotNull Map<String, String> previous) {
        List<String> reverse = new ArrayList<>();
        String current = cellKey(finish.x(), finish.z());
        String startKey = cellKey(start.x(), start.z());
        reverse.add(current);

        while (!current.equals(startKey)) {
            current = previous.get(current);
            if (current == null) {
                return Set.of();
            }
            reverse.add(current);
        }

        Collections.reverse(reverse);
        return new LinkedHashSet<>(reverse);
    }

    private @NotNull Location centerOfRegion(@NotNull SCRegion region) {
        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        return new Location(
            region.getWorld(),
            (min.getBlockX() + max.getBlockX() + 1) / 2.0d,
            max.getBlockY() + 1.0d,
            (min.getBlockZ() + max.getBlockZ() + 1) / 2.0d
        );
    }

    record Cell(int x, int z) { }

    record FieldBounds(int minX, int maxX, int y, int minZ, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    record BlockSnapshot(Material material, String blockData) { }
}
