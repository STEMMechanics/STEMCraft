package dev.stemcraft.minigame.minefield;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.service.region.RegionLocationSupport;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public class MinefieldArenaHandler implements MiniGameArenaHandler {
    private static final int COUNTDOWN_BEEP_THRESHOLD = 5;
    private static final String ORIGINAL_FIELD_SNAPSHOT_KEY = "originalFieldSnapshot";
    private static final String MINE_LOCATIONS_KEY = "mineLocations";
    private static final String REVEALED_LOCATIONS_KEY = "revealedLocations";

    private final STEMCraftAPI api;
    private final MinefieldMiniGame minefield;

    public MinefieldArenaHandler(STEMCraftAPI api, MinefieldMiniGame minefield) {
        this.api = api;
        this.minefield = minefield;
    }

    public void initialize() {
        registerMovementListener();
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
        if (minefield.mineRatio(arena) <= 0.0d || minefield.mineRatio(arena) >= 0.95d) {
            result.addError("Mine ratio must be greater than 0 and lower than 0.95.", "mineRatio");
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
            if (min.getBlockX() == max.getBlockX() && min.getBlockZ() == max.getBlockZ()) {
                result.addError("Field region must include more than one playable block.", "fieldRegion");
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        captureOriginalFieldSnapshot(arena);
        registerRegionListeners(arena);
        clearRoundState(arena);
        resetField(arena);
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
                    minefield.minigame().rewardWinners(arena, List.of(winner.getUniqueId()));
                    arena.startWinnerCelebration(winner.getLocation(), 4);
                }
                broadcastToOccupants(arena, "<gold>" + minefield.resultLine(arena) + "</gold>");
                playSoundToOccupants(arena, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            } else {
                broadcastToOccupants(arena, "<red>" + minefield.resultLine(arena) + "</red>");
                playSoundToOccupants(arena, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.0f);
            }
            resetParticipantsToStart(arena);
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
            mgPlayer.setScore(0);
            mgPlayer.setDeaths(0);
            mgPlayer.setKills(0);
            mgPlayer.set("progressPercent", 0);
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

        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING && arena.numPlayers() == 0) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
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

    @NotNull Set<String> carveGuaranteedPath(@NotNull FieldBounds bounds, @NotNull Location start, @NotNull Location finish, @NotNull Random random) {
        boolean alongX = Math.abs(finish.getX() - start.getX()) >= Math.abs(finish.getZ() - start.getZ());
        int startX = alongX
            ? (start.getX() <= finish.getX() ? bounds.minX() : bounds.maxX())
            : clamp(start.getBlockX(), bounds.minX(), bounds.maxX());
        int finishX = alongX
            ? (start.getX() <= finish.getX() ? bounds.maxX() : bounds.minX())
            : clamp(finish.getBlockX(), bounds.minX(), bounds.maxX());
        int startZ = alongX
            ? clamp(start.getBlockZ(), bounds.minZ(), bounds.maxZ())
            : (start.getZ() <= finish.getZ() ? bounds.minZ() : bounds.maxZ());
        int finishZ = alongX
            ? clamp(finish.getBlockZ(), bounds.minZ(), bounds.maxZ())
            : (start.getZ() <= finish.getZ() ? bounds.maxZ() : bounds.minZ());

        Set<String> path = new LinkedHashSet<>();
        int currentX = startX;
        int currentZ = startZ;
        path.add(cellKey(currentX, currentZ));

        while (currentX != finishX || currentZ != finishZ) {
            if (alongX) {
                if (currentZ != finishZ && random.nextBoolean()) {
                    currentZ += Integer.compare(finishZ, currentZ);
                } else if (currentX != finishX) {
                    currentX += Integer.compare(finishX, currentX);
                } else {
                    currentZ += Integer.compare(finishZ, currentZ);
                }
            } else {
                if (currentX != finishX && random.nextBoolean()) {
                    currentX += Integer.compare(finishX, currentX);
                } else if (currentZ != finishZ) {
                    currentZ += Integer.compare(finishZ, currentZ);
                } else {
                    currentX += Integer.compare(finishX, currentX);
                }
            }

            currentX = clamp(currentX, bounds.minX(), bounds.maxX());
            currentZ = clamp(currentZ, bounds.minZ(), bounds.maxZ());
            path.add(cellKey(currentX, currentZ));
        }

        return path;
    }

    @NotNull Set<String> buildMineLayout(@NotNull FieldBounds bounds, double mineRatio, @NotNull Set<String> protectedCells, @NotNull Random random) {
        List<String> candidates = new ArrayList<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                String key = cellKey(x, z);
                if (!protectedCells.contains(key)) {
                    candidates.add(key);
                }
            }
        }

        if (candidates.isEmpty()) {
            return Set.of();
        }

        int target = Math.max(0, Math.min(candidates.size(), (int) Math.round((bounds.width() * bounds.depth()) * mineRatio)));
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

            if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                return;
            }

            handleFieldStep(arena, player, to);
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
                    if (!arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
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
                    if (arena.hasPlayer(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
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
        revealCell(arena, player, to.getBlockX(), to.getBlockZ());
    }

    private void revealCell(@NotNull MiniGameArena arena, @NotNull Player player, int startX, int startZ) {
        Set<String> mines = mineLocations(arena);
        String startKey = cellKey(startX, startZ);
        if (revealedLocations(arena).contains(startKey)) {
            return;
        }
        if (mines.contains(startKey)) {
            triggerMine(arena, player, new Cell(startX, startZ), "Minefield failed. " + player.getName() + " found a mine.");
            return;
        }

        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }

        Queue<Cell> queue = new ArrayDeque<>();
        queue.add(new Cell(startX, startZ));
        Set<String> visited = new LinkedHashSet<>();
        int revealed = 0;

        while (!queue.isEmpty()) {
            Cell cell = queue.remove();
            if (!bounds.contains(cell.x(), cell.z())) {
                continue;
            }

            String key = cellKey(cell.x(), cell.z());
            if (!visited.add(key) || mines.contains(key) || revealedLocations(arena).contains(key)) {
                continue;
            }

            int adjacent = countAdjacentMines(cell.x(), cell.z(), mines);
            setFieldBlock(arena, cell.x(), cell.z(), adjacent > 0 ? minefield.adjacentBlock(arena) : minefield.clearBlock(arena));
            revealedLocations(arena).add(key);
            revealed++;

            if (adjacent == 0) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        queue.add(new Cell(cell.x() + dx, cell.z() + dz));
                    }
                }
            }
        }

        if (revealed <= 0) {
            return;
        }

        minefield.setRevealedSafeCount(arena, minefield.revealedSafeCount(arena) + revealed);
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.setScore(mgPlayer.getScore() + revealed);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.25f);
    }

    private void handleRoundWin(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return;
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.setScore(mgPlayer.getScore() + minefield.completionBonus(arena));
        }
        minefield.setWinner(arena, player);
        minefield.setResultLine(arena, player.getName() + " cleared the minefield.");
        arena.setStatus(MiniGameArena.ArenaStatus.ENDING, minefield.endingSeconds(arena));
    }

    private void triggerMine(@NotNull MiniGameArena arena, @NotNull Player player, @Nullable Cell cell, @NotNull String resultLine) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return;
        }

        if (cell != null) {
            setFieldBlock(arena, cell.x(), cell.z(), minefield.triggeredMineBlock(arena));
            Location explosion = new Location(arena.world(), cell.x() + 0.5d, cellY(arena) + 0.5d, cell.z() + 0.5d);
            arena.world().createExplosion(explosion, 2.0f, false, false);
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.addDeath();
        }
        minefield.setWinner(arena, null);
        minefield.setResultLine(arena, resultLine);
        arena.setStatus(MiniGameArena.ArenaStatus.ENDING, minefield.endingSeconds(arena));
    }

    private void prepareNewRound(@NotNull MiniGameArena arena) {
        clearRoundState(arena);
        resetField(arena);
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer != null) {
                mgPlayer.setScore(0);
                mgPlayer.setDeaths(0);
                mgPlayer.setKills(0);
                mgPlayer.set("progressPercent", 0);
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
        Set<String> path = carveGuaranteedPath(bounds, start, finish, new Random());
        Set<String> mines = buildMineLayout(bounds, minefield.mineRatio(arena), path, new Random());
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
        for (Player player : arena.getPlayers()) {
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
        minefield.setWinner(arena, null);
        minefield.setMineCount(arena, 0);
        minefield.setRevealedSafeCount(arena, 0);
    }

    private void captureOriginalFieldSnapshot(@NotNull MiniGameArena arena) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null) {
            return;
        }

        Map<String, BlockSnapshot> snapshot = originalFieldSnapshot(arena);
        snapshot.clear();
        World world = arena.world();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                Block block = world.getBlockAt(x, bounds.y(), z);
                BlockData blockData = block.getBlockData();
                snapshot.put(cellKey(x, z), new BlockSnapshot(block.getType(), blockData.getAsString()));
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
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                world.getBlockAt(x, bounds.y(), z).setType(material, false);
            }
        }
    }

    private void setFieldBlock(@NotNull MiniGameArena arena, int x, int z, @NotNull Material material) {
        FieldBounds bounds = bounds(arena);
        if (bounds == null || !bounds.contains(x, z)) {
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
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        if (arena.getLobbySpawn() != null) {
            player.teleport(arena.getLobbySpawn());
        }
    }

    private void clearPlayerProgress(@NotNull MiniGameArena arena, @NotNull Player player) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.set("progressPercent", 0);
        }
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

    private @NotNull Location centerOfRegion(@NotNull SCRegion region) {
        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        Location center = new Location(
            region.getWorld(),
            (min.getBlockX() + max.getBlockX() + 1) / 2.0d,
            min.getBlockY(),
            (min.getBlockZ() + max.getBlockZ() + 1) / 2.0d
        );
        if (region.contains(center)) {
            return center;
        }
        Location ground = RegionLocationSupport.randomGroundLocation(region);
        if (ground != null) {
            return ground;
        }
        Location fallback = RegionLocationSupport.randomLocation(region);
        return fallback != null ? fallback : center;
    }

    record Cell(int x, int z) { }

    record FieldBounds(int minX, int maxX, int y, int minZ, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        int width() {
            return (maxX - minX) + 1;
        }

        int depth() {
            return (maxZ - minZ) + 1;
        }
    }

    record BlockSnapshot(Material material, String blockData) { }
}
