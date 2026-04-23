package dev.stemcraft.minigame.tntrun;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class TntRunArenaHandler implements MiniGameArenaHandler {
    private static final int COUNTDOWN_BEEP_THRESHOLD = 5;
    private static final long DECAY_SCAN_PERIOD_TICKS = 2L;
    private static final long STATIONARY_DECAY_GRACE_TICKS = 8L;

    private final STEMCraftAPI api;
    private final TntRunMiniGame tntRun;

    public TntRunArenaHandler(STEMCraftAPI api, TntRunMiniGame tntRun) {
        this.api = api;
        this.tntRun = tntRun;
    }

    public void initalize() {
        registerMovementListener();
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        List<Location> startingGrid = tntRun.startingGrid(arena);

        if (arena.getLobbySpawn() == null) {
            result.addError("Lobby spawn is not defined.", "lobbySpawn");
        }
        if (arena.getSpectatorSpawn() == null) {
            result.addError("Spectator spawn is not defined.", "spectatorSpawn");
        }
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arenaRegion");
        }
        if (startingGrid.isEmpty()) {
            result.addError("Starting grid locations are not defined.", "startingGrid");
        }
        if (arena.getMinPlayers() < 2) {
            result.addError("TNT Run arenas require at least 2 minimum players.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 2) {
            result.addError("TNT Run arenas require at least 2 maximum players.", "maxPlayers");
        }
        if (!startingGrid.isEmpty() && arena.getMaxPlayers() > startingGrid.size()) {
            result.addError("Max players exceeds configured starting grid slots.", "maxPlayers");
        }

        if (arenaRegion != null) {
            for (int i = 0; i < startingGrid.size(); i++) {
                Location grid = startingGrid.get(i);
                if (grid == null || grid.getWorld() == null) {
                    result.addError("Starting grid slot " + (i + 1) + " is invalid.", "startingGrid." + i);
                    continue;
                }
                if (!arena.world().equals(grid.getWorld())) {
                    result.addError("Starting grid slot " + (i + 1) + " must be in the arena world.", "startingGrid." + i);
                } else if (!arenaRegion.contains(grid)) {
                    result.addError("Starting grid slot " + (i + 1) + " must be inside the arena region.", "startingGrid." + i);
                }
            }

            if (voidY(arena) >= arenaRegion.getMinimumLocation().getBlockY()) {
                result.addError("Void Y must be below the arena minimum Y.", "voidY");
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        captureArenaSnapshot(arena);
        clearRoundState(arena);
        resetArenaBlocks(arena);
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        clearRoundState(arena);
        if (shouldRestoreSnapshotOnUnload(arena.getStatus())) {
            resetArenaBlocks(arena);
        }
        clearPendingDecayTasks(arena);
        cancelDecayTracker(arena);
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

        if (arena.hasSpectator(player)) {
            return HandlerEventResult.DENY;
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING || arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                return HandlerEventResult.DENY;
            }
            if (shouldEliminateOnDamage(player, event)) {
                eliminatePlayer(arena, player);
                return HandlerEventResult.DENY;
            }
            return allowsLiveDamage(event) ? HandlerEventResult.ALLOW : HandlerEventResult.DENY;
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
            clearPendingDecayTasks(arena);
            cancelDecayTracker(arena);
            clearOccupiedSupportTracking(arena);
            resetArenaBlocks(arena);
            reviveEliminatedSpectators(arena);
            teleportPlayersToLobby(arena);
            teleportSpectators(arena);
            queueAutoStartIfReady(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.STARTING) {
            tntRun.setWinner(arena, null);
            clearPendingDecayTasks(arena);
            cancelDecayTracker(arena);
            clearOccupiedSupportTracking(arena);
            captureArenaSnapshot(arena);
            prepareStartingGrid(arena);
            scheduleStartingGridRefresh(arena, 1L);
            broadcastToOccupants(arena, "<gold>TNT Run is starting.</gold>");
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RUNNING) {
            prepareRunningPlayers(arena);
            startDecayTracker(arena);
            broadcastToOccupants(arena, "<red>Run!</red> <gray>The arena is collapsing.</gray>");
            playSoundToOccupants(arena, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.ENDING) {
            cancelDecayTracker(arena);
            Player winner = resolveWinnerPlayer(arena);
            tntRun.setWinner(arena, winner);
            if (winner != null) {
                arena.startWinnerCelebration(winner.getLocation(), 4);
                broadcastToOccupants(arena, "<gold>TNT Run Over!</gold> <yellow>" + winner.getName() + "</yellow> <gray>wins.</gray>");
            } else {
                arena.stopWinnerCelebration();
                broadcastToOccupants(arena, "<gold>TNT Run Over!</gold> <gray>No winner was determined.</gray>");
            }
            playSoundToOccupants(arena, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.0f);
            teleportSpectators(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RESETTING) {
            resetArenaAfterRound(arena);
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
            arena.setStatus(MiniGameArena.ArenaStatus.RUNNING, roundSeconds(arena));
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            arena.setStatus(MiniGameArena.ArenaStatus.ENDING, endingSeconds(arena));
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        tntRun.registerJoinOrder(arena, player);
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.setScore(0);
            mgPlayer.setDeaths(0);
            mgPlayer.setKills(0);
        }

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);

        if (arena.get("suppressAutoStart", Boolean.class, false)) {
            return arena.getLobbySpawn();
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING) {
            queueAutoStartIfReady(arena);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            api.tasks().runLater(1L, () -> prepareStartingGrid(arena));
        }

        return arena.getLobbySpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        tntRun.unregisterJoinOrder(arena, player);
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING && arena.numPlayers() < arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            arena.setCountdown(0);
            return;
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            checkForRoundEnd(arena);
        }
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        onPlayerLeaveArena(arena, player);
    }

    public void onPlayerLeaveSpectator(MiniGameArena arena, Player player) {
        tntRun.eliminatedPlayers(arena).remove(player.getUniqueId());
    }

    @Override
    public void onPlayerQuitSpectator(MiniGameArena arena, Player player) {
        onPlayerLeaveSpectator(arena, player);
    }

    private void registerMovementListener() {
        api.events().register(PlayerMoveEvent.class, event -> {
            Location to = event.getTo();
            Player player = event.getPlayer();
            MiniGameArena arena = tntRun.minigame().findPlayer(player);
            if (arena == null || !TntRunMiniGame.namespace().equals(arena.namespace())) {
                return;
            }

            SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
            Predicate<Location> insideArena = arenaRegion == null ? null : arenaRegion::contains;
            if (arena.hasSpectator(player)) {
                if (shouldResetSpectatorPosition(to, insideArena, voidY(arena))) {
                    Location spectatorSpawn = arena.getSpectatorSpawn() != null ? arena.getSpectatorSpawn() : arena.getLobbySpawn();
                    if (spectatorSpawn != null) {
                        player.teleport(spectatorSpawn);
                    }
                }
                return;
            }

            if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
                keepPlayerOnAssignedSpawn(arena, player, event.getFrom());
                return;
            }

            if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                return;
            }

            if (shouldEliminateRunner(to, insideArena, voidY(arena))) {
                eliminatePlayer(arena, player);
                return;
            }

            Location from = event.getFrom();
            if (movedBlock(from, to)) {
                scheduleDecayUnderLocation(arena, from);
            }
        });
    }

    private boolean movedBlock(@NotNull Location from, @NotNull Location to) {
        return from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ();
    }

    private void keepPlayerOnAssignedSpawn(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Location fallback) {
        Integer slot = tntRun.assignedSpawnSlots(arena).get(player.getUniqueId());
        if (slot == null || slot < 0 || slot >= tntRun.startingGrid(arena).size()) {
            return;
        }

        Location spawn = tntRun.startingGrid(arena).get(slot);
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }

        if (player.getLocation().distanceSquared(spawn) > 0.16d) {
            player.teleport(spawn);
        } else if (fallback.distanceSquared(spawn) > 0.16d) {
            player.teleport(spawn);
        }
    }

    private void scheduleDecayUnderLocation(@NotNull MiniGameArena arena, @NotNull Location location) {
        for (Block block : supportingBlocks(location)) {
            if (!isDecayBlock(arena, block)) {
                continue;
            }
            scheduleBlockDecay(arena, block);
        }
    }

    void scheduleDecayUnderActivePlayers(@NotNull MiniGameArena arena) {
        Map<String, Block> occupiedSupports = occupiedSupportBlocks(arena);
        for (String blockKey : updateOccupiedSupportAges(arena, occupiedSupports.keySet())) {
            Block block = occupiedSupports.get(blockKey);
            if (block != null) {
                scheduleBlockDecay(arena, block);
            }
        }
    }

    private @NotNull Map<String, Block> occupiedSupportBlocks(@NotNull MiniGameArena arena) {
        Map<String, Block> occupiedSupports = new LinkedHashMap<>();
        for (Player player : arena.getPlayers()) {
            for (Block block : supportingBlocks(player.getLocation())) {
                if (!isDecayBlock(arena, block)) {
                    continue;
                }
                occupiedSupports.put(blockKey(block), block);
            }
        }
        return occupiedSupports;
    }

    @SuppressWarnings("unchecked")
    private @NotNull Map<String, Long> occupiedSupportAges(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("occupiedSupportAges", Map.class, LinkedHashMap::new);
    }

    @NotNull Set<String> updateOccupiedSupportAges(@NotNull MiniGameArena arena, @NotNull Set<String> occupiedKeys) {
        Map<String, Long> ages = occupiedSupportAges(arena);
        ages.keySet().removeIf(key -> !occupiedKeys.contains(key));

        Set<String> ready = new LinkedHashSet<>();
        for (String key : occupiedKeys) {
            long ticks = ages.getOrDefault(key, 0L) + DECAY_SCAN_PERIOD_TICKS;
            ages.put(key, ticks);
            if (ticks >= STATIONARY_DECAY_GRACE_TICKS) {
                ready.add(key);
            }
        }
        return ready;
    }

    private @NotNull Set<Block> supportingBlocks(@NotNull Location location) {
        Set<Block> blocks = new LinkedHashSet<>();
        if (location.getWorld() == null) {
            return blocks;
        }

        int y = location.getBlockY() - 1;
        double[] offsets = {-0.29d, 0.29d};
        for (double xOffset : offsets) {
            for (double zOffset : offsets) {
                blocks.add(location.getWorld().getBlockAt(
                    (int) Math.floor(location.getX() + xOffset),
                    y,
                    (int) Math.floor(location.getZ() + zOffset)
                ));
            }
        }
        return blocks;
    }

    boolean shouldEliminateRunner(@Nullable Location to, @Nullable Predicate<Location> insideArena, int voidY) {
        if (to == null) {
            return false;
        }
        if (to.getY() <= voidY) {
            return true;
        }
        return insideArena != null && !insideArena.test(to);
    }

    boolean shouldResetSpectatorPosition(@Nullable Location to, @Nullable Predicate<Location> insideArena, int voidY) {
        return shouldEliminateRunner(to, insideArena, voidY);
    }

    private boolean isDecayBlock(@NotNull MiniGameArena arena, @NotNull Block block) {
        if (block.getType().isAir() || !block.getType().isSolid()) {
            return false;
        }
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        return arenaRegion != null && arenaRegion.contains(block.getLocation());
    }

    private void scheduleBlockDecay(@NotNull MiniGameArena arena, @NotNull Block block) {
        String locationKey = blockKey(block);
        Set<String> pending = pendingDecays(arena);
        if (!pending.add(locationKey)) {
            return;
        }

        api.tasks().runOnceDelay(decayTaskId(arena, locationKey), fadeDelayTicks(arena), () -> {
            pending.remove(locationKey);
            if (!isDecayBlock(arena, block)) {
                return;
            }
            block.setType(org.bukkit.Material.AIR, false);
            block.getWorld().playSound(block.getLocation().add(0.5d, 0.5d, 0.5d), Sound.BLOCK_SAND_BREAK, 0.35f, 1.45f);
        });
    }

    private void eliminatePlayer(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (!arena.hasPlayer(player)) {
            return;
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.addDeath();
        }

        tntRun.eliminatedPlayers(arena).add(player.getUniqueId());
        arena.addSpectator(player);
        broadcastToOccupants(arena, "<red>" + player.getName() + "</red> <gray>" + "fell out of the arena" + ".</gray>");
        playSoundToOccupants(arena, Sound.ENTITY_WITHER_HURT, 0.6f, 1.25f);
        checkForRoundEnd(arena);
    }

    private void checkForRoundEnd(@NotNull MiniGameArena arena) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return;
        }

        if (arena.numPlayers() > 1) {
            return;
        }

        arena.setStatus(MiniGameArena.ArenaStatus.ENDING, endingSeconds(arena));
    }

    private void prepareStartingGrid(@NotNull MiniGameArena arena) {
        List<Player> orderedPlayers = orderedPlayers(arena);
        Map<UUID, Integer> assignedSlots = tntRun.assignedSpawnSlots(arena);
        assignedSlots.clear();

        for (int i = 0; i < orderedPlayers.size(); i++) {
            Player player = orderedPlayers.get(i);
            int slot = Math.min(i, tntRun.startingGrid(arena).size() - 1);
            Location spawn = tntRun.startingGrid(arena).get(slot);
            assignedSlots.put(player.getUniqueId(), slot);

            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setWalkSpeed(0.0f);
            player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setFireTicks(0);
            player.setFallDistance(0.0f);
            if (spawn != null) {
                player.teleport(spawn);
            }
        }
    }

    private void prepareRunningPlayers(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setWalkSpeed(0.2f);
            player.setFallDistance(0.0f);
        }
    }

    private @NotNull List<Player> orderedPlayers(@NotNull MiniGameArena arena) {
        List<Player> orderedPlayers = new ArrayList<>();
        List<UUID> joinOrder = tntRun.joinOrder(arena);
        for (UUID uuid : joinOrder) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && arena.hasPlayer(player)) {
                orderedPlayers.add(player);
            }
        }
        for (Player player : arena.getPlayers()) {
            if (!orderedPlayers.contains(player)) {
                orderedPlayers.add(player);
            }
        }
        return orderedPlayers;
    }

    private void reviveEliminatedSpectators(@NotNull MiniGameArena arena) {
        Set<UUID> eliminated = tntRun.eliminatedPlayers(arena);
        if (eliminated.isEmpty()) {
            return;
        }

        arena.set("suppressAutoStart", true);
        try {
            for (Player spectator : new ArrayList<>(arena.getSpectators())) {
                if (!eliminated.remove(spectator.getUniqueId())) {
                    continue;
                }
                arena.addPlayer(spectator);
            }
        } finally {
            arena.set("suppressAutoStart", false);
        }
    }

    private void teleportPlayersToLobby(@NotNull MiniGameArena arena) {
        Location lobby = arena.getLobbySpawn();
        if (lobby == null) {
            return;
        }
        for (Player player : arena.getPlayers()) {
            player.setWalkSpeed(0.2f);
            player.teleport(lobby);
        }
    }

    private void teleportSpectators(@NotNull MiniGameArena arena) {
        Location spectatorSpawn = arena.getSpectatorSpawn();
        if (spectatorSpawn == null) {
            spectatorSpawn = arena.getLobbySpawn();
        }
        if (spectatorSpawn == null) {
            return;
        }
        for (Player spectator : arena.getSpectators()) {
            spectator.teleport(spectatorSpawn);
        }
    }

    void captureArenaSnapshot(@NotNull MiniGameArena arena) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        Map<String, BlockSnapshot> snapshot = arenaSnapshot(arena);
        snapshot.clear();
        if (arenaRegion == null) {
            return;
        }

        Location min = arenaRegion.getMinimumLocation();
        Location max = arenaRegion.getMaximumLocation();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Location location = new Location(arena.world(), x, y, z);
                    if (!arenaRegion.contains(location)) {
                        continue;
                    }
                    snapshotBlock(snapshot, arena.world().getBlockAt(x, y, z));
                }
            }
        }
    }

    void resetArenaBlocks(@NotNull MiniGameArena arena) {
        for (Map.Entry<String, BlockSnapshot> entry : arenaSnapshot(arena).entrySet()) {
            String[] coords = entry.getKey().split(",");
            Block block = arena.world().getBlockAt(
                Integer.parseInt(coords[0]),
                Integer.parseInt(coords[1]),
                Integer.parseInt(coords[2])
            );
            applySnapshot(block, entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private @NotNull Map<String, BlockSnapshot> arenaSnapshot(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("arenaSnapshot", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<String> pendingDecays(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("pendingDecays", Set.class, LinkedHashSet::new);
    }

    private void clearRoundState(@NotNull MiniGameArena arena) {
        clearPendingDecayTasks(arena);
        pendingDecays(arena).clear();
        clearOccupiedSupportTracking(arena);
        tntRun.resetRoundState(arena);
    }

    void resetArenaAfterRound(@NotNull MiniGameArena arena) {
        arena.stopWinnerCelebration();
        clearRoundState(arena);
        resetArenaBlocks(arena);
        arena.removeAllOccupants();
    }

    private void clearPendingDecayTasks(@NotNull MiniGameArena arena) {
        for (String key : new ArrayList<>(pendingDecays(arena))) {
            api.tasks().cancel(decayTaskId(arena, key));
        }
        pendingDecays(arena).clear();
    }

    private void clearOccupiedSupportTracking(@NotNull MiniGameArena arena) {
        occupiedSupportAges(arena).clear();
    }

    void startDecayTracker(@NotNull MiniGameArena arena) {
        String taskId = decayScanTaskId(arena);
        api.tasks().cancel(taskId);
        api.tasks().repeating(taskId, 0L, DECAY_SCAN_PERIOD_TICKS, () -> {
            if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                api.tasks().cancel(taskId);
                return;
            }

            scheduleDecayUnderActivePlayers(arena);
        });
    }

    void cancelDecayTracker(@NotNull MiniGameArena arena) {
        api.tasks().cancel(decayScanTaskId(arena));
    }

    void scheduleStartingGridRefresh(@NotNull MiniGameArena arena, long delayTicks) {
        api.tasks().runLater(delayTicks, () -> {
            if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
                prepareStartingGrid(arena);
            }
        });
    }

    void queueAutoStartIfReady(@NotNull MiniGameArena arena) {
        if (arena.get("suppressAutoStart", Boolean.class, false)) {
            return;
        }
        if (arena.getStatus() != MiniGameArena.ArenaStatus.WAITING || arena.numPlayers() < arena.getMinPlayers()) {
            return;
        }

        api.tasks().runLater(1L, () -> {
            if (arena.get("suppressAutoStart", Boolean.class, false)) {
                return;
            }
            if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
                arena.setStatus(MiniGameArena.ArenaStatus.STARTING, tntRun.startCountdownSeconds(arena));
            }
        });
    }

    private @Nullable Player resolveWinnerPlayer(@NotNull MiniGameArena arena) {
        return arena.numPlayers() == 1 ? arena.getPlayers().getFirst() : null;
    }

    private int roundSeconds(@NotNull MiniGameArena arena) {
        return arena.get("roundSeconds", Integer.class, 180);
    }

    private int endingSeconds(@NotNull MiniGameArena arena) {
        return tntRun.endingSeconds(arena);
    }

    private int fadeDelayTicks(@NotNull MiniGameArena arena) {
        return arena.get("fadeDelayTicks", Integer.class, 8);
    }

    boolean shouldEliminateOnDamage(@NotNull Player player, @NotNull EntityDamageEvent event) {
        return event.getCause() == EntityDamageEvent.DamageCause.VOID
            || player.getHealth() - event.getFinalDamage() <= 0.0d;
    }

    boolean allowsLiveDamage(@NotNull EntityDamageEvent event) {
        return switch (event.getCause()) {
            case LAVA, FIRE, FIRE_TICK, HOT_FLOOR -> true;
            default -> false;
        };
    }

    int voidY(@NotNull MiniGameArena arena) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        int configured = arena.get("voidY", Integer.class, Integer.MIN_VALUE);
        int worldMinHeight = arena.world().getMinHeight();
        Location arenaMinimum = arenaRegion == null ? null : arenaRegion.getMinimumLocation();
        return resolveVoidY(arenaMinimum, configured, worldMinHeight);
    }

    int resolveVoidY(@Nullable Location arenaMinimum, int configuredVoidY, int worldMinHeight) {
        int defaultVoidY = arenaMinimum == null
            ? worldMinHeight
            : arenaMinimum.getBlockY() - 6;

        if (configuredVoidY == Integer.MIN_VALUE) {
            return defaultVoidY;
        }

        // Older arenas persisted the create-time sentinel of world min-height.
        if (arenaMinimum != null && configuredVoidY == worldMinHeight) {
            return defaultVoidY;
        }

        return configuredVoidY;
    }

    boolean shouldRestoreSnapshotOnUnload(@NotNull MiniGameArena.ArenaStatus status) {
        return switch (status) {
            case STARTING, PREPARATION, RUNNING, COOLDOWN, ENDING, RESETTING -> true;
            default -> false;
        };
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

    private @NotNull String decayTaskId(@NotNull MiniGameArena arena, @NotNull String locationKey) {
        return NamespaceId.of(TntRunMiniGame.namespace(), arena.id() + "_decay_" + locationKey.replace(',', '_'));
    }

    private @NotNull String decayScanTaskId(@NotNull MiniGameArena arena) {
        return NamespaceId.of(TntRunMiniGame.namespace(), arena.id() + "_decay_scan");
    }

    private @NotNull String blockKey(@NotNull Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    void applySnapshot(@NotNull Block block, @NotNull BlockSnapshot snapshot) {
        Material material = Material.matchMaterial(snapshot.materialName());
        block.setType(material == null ? Material.AIR : material, false);
        block.setBlockData(createBlockData(snapshot.blockData()), false);
    }

    void snapshotBlock(@NotNull Map<String, BlockSnapshot> snapshot, @NotNull Block block) {
        snapshot.put(blockKey(block), new BlockSnapshot(block.getType().name(), block.getBlockData().getAsString()));
    }

    org.bukkit.block.data.BlockData createBlockData(@NotNull String blockData) {
        return Bukkit.createBlockData(blockData);
    }

    private record BlockSnapshot(@NotNull String materialName, @NotNull String blockData) {
    }
}
