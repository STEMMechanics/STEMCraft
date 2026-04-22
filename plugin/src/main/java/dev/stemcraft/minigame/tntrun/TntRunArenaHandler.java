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
import org.bukkit.attribute.Attribute;
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

public class TntRunArenaHandler implements MiniGameArenaHandler {
    private static final int COUNTDOWN_BEEP_THRESHOLD = 5;

    private final STEMCraftAPI api;
    private final TntRunMiniGame tntRun;

    public TntRunArenaHandler(STEMCraftAPI api, TntRunMiniGame tntRun) {
        this.api = api;
        this.tntRun = tntRun;
        registerMovementListener();
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        List<SCRegion> floorRegions = tntRun.floorRegions(arena);
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
        if (floorRegions.isEmpty()) {
            result.addError("At least one floor region must be configured.", "floors");
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
            for (int i = 0; i < floorRegions.size(); i++) {
                SCRegion floor = floorRegions.get(i);
                if (!arenaRegion.contains(floor)) {
                    result.addError("Floor region " + (i + 1) + " must be inside the arena region.", "floors." + i);
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
                } else if (!arenaRegion.contains(grid)) {
                    result.addError("Starting grid slot " + (i + 1) + " must be inside the arena region.", "startingGrid." + i);
                }
            }

            if (voidY(arena) >= arenaRegion.getMinimumLocation().getBlockY()) {
                result.addError("Void Y must be below the arena floor.", "voidY");
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        captureFloorSnapshot(arena);
        clearRoundState(arena);
        resetFloor(arena);
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        clearRoundState(arena);
        resetFloor(arena);
        clearPendingDecayTasks(arena);
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
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || player.getHealth() - event.getFinalDamage() <= 0.0d) {
                eliminatePlayer(arena, player, "fell out of the arena");
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
            arena.stopWinnerCelebration();
            clearPendingDecayTasks(arena);
            resetFloor(arena);
            reviveEliminatedSpectators(arena);
            teleportPlayersToLobby(arena);
            teleportSpectators(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.STARTING) {
            tntRun.setWinner(arena, null);
            clearPendingDecayTasks(arena);
            resetFloor(arena);
            prepareStartingGrid(arena);
            broadcastToOccupants(arena, "<gold>TNT Run is starting.</gold>");
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RUNNING) {
            prepareRunningPlayers(arena);
            broadcastToOccupants(arena, "<red>Run!</red> <gray>The floor is collapsing.</gray>");
            playSoundToOccupants(arena, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.ENDING) {
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
            clearPendingDecayTasks(arena);
            resetFloor(arena);
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

        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
            api.tasks().nextTick(() -> {
                if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
                    arena.setStatus(MiniGameArena.ArenaStatus.STARTING, startCountdownSeconds(arena));
                }
            });
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            api.tasks().nextTick(() -> prepareStartingGrid(arena));
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

    @Override
    public Location onPlayerJoinSpectator(MiniGameArena arena, Player player) {
        Location spectatorSpawn = arena.getSpectatorSpawn();
        return spectatorSpawn != null ? spectatorSpawn : arena.getLobbySpawn();
    }

    @Override
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

            if (arena.hasSpectator(player)) {
                if (player.getY() <= voidY(arena)) {
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

            if (to.getY() <= voidY(arena)) {
                eliminatePlayer(arena, player, "fell out of the arena");
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
            if (!isFloorBlock(arena, block)) {
                continue;
            }
            scheduleBlockDecay(arena, block);
        }
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

    private boolean isFloorBlock(@NotNull MiniGameArena arena, @NotNull Block block) {
        if (block.getType().isAir()) {
            return false;
        }
        for (SCRegion floor : tntRun.floorRegions(arena)) {
            if (floor.contains(block.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private void scheduleBlockDecay(@NotNull MiniGameArena arena, @NotNull Block block) {
        String locationKey = blockKey(block);
        Set<String> pending = pendingDecays(arena);
        if (!pending.add(locationKey)) {
            return;
        }

        api.tasks().runOnceDelay(decayTaskId(arena, locationKey), fadeDelayTicks(arena), () -> {
            pending.remove(locationKey);
            if (!isFloorBlock(arena, block)) {
                return;
            }
            block.setType(org.bukkit.Material.AIR, false);
            block.getWorld().playSound(block.getLocation().add(0.5d, 0.5d, 0.5d), Sound.BLOCK_SAND_BREAK, 0.35f, 1.45f);
        });
    }

    private void eliminatePlayer(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull String reason) {
        if (!arena.hasPlayer(player)) {
            return;
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.addDeath();
        }

        tntRun.eliminatedPlayers(arena).add(player.getUniqueId());
        arena.addSpectator(player);
        broadcastToOccupants(arena, "<red>" + player.getName() + "</red> <gray>" + reason + ".</gray>");
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

    private void captureFloorSnapshot(@NotNull MiniGameArena arena) {
        Map<String, BlockSnapshot> snapshot = floorSnapshot(arena);
        snapshot.clear();

        for (SCRegion floor : tntRun.floorRegions(arena)) {
            Location min = floor.getMinimumLocation();
            Location max = floor.getMaximumLocation();
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                    for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        Location location = new Location(arena.world(), x, y, z);
                        if (!floor.contains(location)) {
                            continue;
                        }
                        Block block = arena.world().getBlockAt(x, y, z);
                        snapshot.put(blockKey(block), new BlockSnapshot(block.getType().name(), block.getBlockData().getAsString()));
                    }
                }
            }
        }
    }

    private void resetFloor(@NotNull MiniGameArena arena) {
        for (Map.Entry<String, BlockSnapshot> entry : floorSnapshot(arena).entrySet()) {
            String[] coords = entry.getKey().split(",");
            Block block = arena.world().getBlockAt(
                Integer.parseInt(coords[0]),
                Integer.parseInt(coords[1]),
                Integer.parseInt(coords[2])
            );
            entry.getValue().apply(block);
        }
    }

    @SuppressWarnings("unchecked")
    private @NotNull Map<String, BlockSnapshot> floorSnapshot(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("floorSnapshot", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<String> pendingDecays(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("pendingDecays", Set.class, LinkedHashSet::new);
    }

    private void clearRoundState(@NotNull MiniGameArena arena) {
        clearPendingDecayTasks(arena);
        pendingDecays(arena).clear();
        tntRun.resetRoundState(arena);
    }

    private void clearPendingDecayTasks(@NotNull MiniGameArena arena) {
        for (String key : new ArrayList<>(pendingDecays(arena))) {
            api.tasks().cancel(decayTaskId(arena, key));
        }
        pendingDecays(arena).clear();
    }

    private @Nullable Player resolveWinnerPlayer(@NotNull MiniGameArena arena) {
        return arena.numPlayers() == 1 ? arena.getPlayers().getFirst() : null;
    }

    private int startCountdownSeconds(@NotNull MiniGameArena arena) {
        return arena.get("startCountdownSeconds", Integer.class, 10);
    }

    private int roundSeconds(@NotNull MiniGameArena arena) {
        return arena.get("roundSeconds", Integer.class, 180);
    }

    private int endingSeconds(@NotNull MiniGameArena arena) {
        return arena.get("endingSeconds", Integer.class, 8);
    }

    private int fadeDelayTicks(@NotNull MiniGameArena arena) {
        return arena.get("fadeDelayTicks", Integer.class, 8);
    }

    private int voidY(@NotNull MiniGameArena arena) {
        return arena.get("voidY", Integer.class, arena.world().getMinHeight());
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

    private @NotNull String blockKey(@NotNull Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private record BlockSnapshot(@NotNull String materialName, @NotNull String blockData) {
        private void apply(@NotNull Block block) {
            Material material = Material.matchMaterial(materialName);
            block.setType(material == null ? Material.AIR : material, false);
            block.setBlockData(Bukkit.createBlockData(blockData), false);
        }
    }
}
