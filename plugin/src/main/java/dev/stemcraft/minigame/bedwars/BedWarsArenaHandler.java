package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public class BedWarsArenaHandler implements MiniGameArenaHandler {
    private static final int STARTING_COUNTDOWN_SECONDS = 30;
    private static final int RUNNING_COUNTDOWN_SECONDS = 1800;
    private static final int ENDING_COUNTDOWN_SECONDS = 15;
    private static final int DROP_INTERVAL_SECONDS = 30;
    private static final double DROP_SPAWN_Y_OFFSET = 1.15d;

    private final STEMCraftAPI api;
    private final BedWarsMiniGame bedWars;

    public BedWarsArenaHandler(STEMCraftAPI api, BedWarsMiniGame bedWars) {
        this.api = api;
        this.bedWars = bedWars;
        registerInventoryProtection();
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arena.getLobbySpawn() == null) {
            result.addError("Lobby spawn is not defined.", "lobbySpawn");
        }
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arenaRegion");
        }
        if (arena.getTeams().size() < 2 || arena.getTeams().size() > 8) {
            result.addError("BedWars arenas must define between 2 and 8 teams.", "teams");
        }
        if (arena.getMinPlayers() < 2) {
            result.addError("BedWars arenas require at least 2 minimum players.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 2) {
            result.addError("BedWars arenas require at least 2 maximum players.", "maxPlayers");
        }

        int teamSize = teamSize(arena);
        if (teamSize < 1) {
            result.addError("BedWars arenas require a team size of at least 1.", "teamSize");
        }
        if (arena.getMaxPlayers() > arena.getTeams().size() * Math.max(1, teamSize)) {
            result.addError("Max players exceeds total team capacity.", "maxPlayers");
        }

        for (MiniGameTeam team : arena.getTeams()) {
            if (team.getSpawn() == null) {
                result.addError("Team '" + team.getName() + "' is missing a spawn.", "teams." + team.getName() + ".spawn");
            } else if (arenaRegion != null && !arenaRegion.contains(team.getSpawn())) {
                result.addError("Team '" + team.getName() + "' spawn must be inside the arena region.", "teams." + team.getName() + ".spawn");
            }

            SCRegion bedRegion = team.get("bedRegion", SCRegion.class);
            if (bedRegion == null) {
                result.addError("Team '" + team.getName() + "' is missing a bed region.", "teams." + team.getName() + ".bed");
            } else if (arenaRegion != null && !arenaRegion.contains(bedRegion)) {
                result.addError("Team '" + team.getName() + "' bed region must be inside the arena region.", "teams." + team.getName() + ".bed");
            } else if (!containsBedBlock(bedRegion)) {
                result.addError("Team '" + team.getName() + "' bed region does not contain a bed.", "teams." + team.getName() + ".bed");
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        placedBlocks(arena);
        arena.getOrCreate("eliminatedSpectators", Set.class, LinkedHashSet::new);
        arena.getOrCreate("trackedEntities", Set.class, HashSet::new);
        arena.getOrCreate("dropSurfaces", List.class, ArrayList::new);
        captureBedSnapshots(arena);
        restoreBeds(arena);

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion != null) {
            api.regions().addListener(regionListenerPrefix(arena.id()) + "boundary", arenaRegion, new RegionListener() {
                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region) {
                    if (arena.hasPlayer(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
                        handleDeath(arena, player, null);
                    } else if (arena.hasOccupant(player)
                        && (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING || arena.getStatus() == MiniGameArena.ArenaStatus.RESETTING)) {
                        arena.removeOccupant(player);
                    }
                }
            });
        }
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        clearTrackedEntities(arena);
        api.regions().removeListener(regionListenerPrefix(arena.id()) + "*");
    }

    @Override
    public HandlerEventResult onBlockPlace(MiniGameArena arena, Player player, Block block) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return HandlerEventResult.DENY;
        }

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion == null || !arenaRegion.contains(block.getLocation())) {
            return HandlerEventResult.DENY;
        }

        placedBlocks(arena).add(block.getLocation());
        return HandlerEventResult.ALLOW;
    }

    @Override
    public HandlerEventResult onBlockBreak(MiniGameArena arena, Player player, Block block) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return HandlerEventResult.DENY;
        }

        Set<Location> blockLocations = placedBlocks(arena);
        if (blockLocations.remove(block.getLocation())) {
            return HandlerEventResult.ALLOW;
        }

        MiniGameTeam bedOwner = findBedOwner(arena, block.getLocation());
        if (bedOwner == null || !isBedBlock(block.getType())) {
            return HandlerEventResult.DENY;
        }

        MiniGameTeam playerTeam = arena.getPlayerTeam(player);
        if (playerTeam == null || playerTeam.getName().equals(bedOwner.getName())) {
            return HandlerEventResult.DENY;
        }
        if (!bedOwner.get("bedAlive", Boolean.class, true)) {
            return HandlerEventResult.DENY;
        }

        destroyTeamBed(arena, bedOwner);
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            bedWars.incrementStat("beds_broken", arena, mgPlayer);
        }
        arena.broadcast("The " + bedOwner.get("displayName", String.class, bedOwner.getName()) + " bed has been destroyed!");
        return HandlerEventResult.ALLOW;
    }

    @Override
    public HandlerEventResult onEntityDamage(MiniGameArena arena, EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return HandlerEventResult.ALLOW;
        }
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return HandlerEventResult.DENY;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            handleDeath(arena, player, null);
            return HandlerEventResult.DENY;
        }

        double finalDamage = event.getFinalDamage();
        if (player.getHealth() - finalDamage > 0.0d) {
            return HandlerEventResult.ALLOW;
        }

        Player damagerPlayer = resolveDamager(event);
        handleDeath(arena, player, damagerPlayer);
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onPlayerDropItem(MiniGameArena arena, Player player, ItemStack item) {
        return HandlerEventResult.DENY;
    }

    @Override
    public void onArenaStatusChanged(MiniGameArena arena, MiniGameArena.ArenaStatus oldStatus, MiniGameArena.ArenaStatus newStatus) {
        if (newStatus == MiniGameArena.ArenaStatus.RUNNING) {
            startRound(arena);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RESETTING) {
            resetRound(arena);
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.ENDING) {
            MiniGameTeam winner = determineWinner(arena);
            arena.set("winnerTeam", winner == null ? "" : winner.getName());
            if (winner != null) {
                for (Player player : arena.getTeamPlayers(winner.getName())) {
                    MiniGamePlayer mgPlayer = arena.getPlayer(player);
                    if (mgPlayer != null) {
                        bedWars.incrementStat("wins", arena, mgPlayer);
                    }
                }
            }

            String winnerName = winner == null
                ? "No winner"
                : winner.get("displayName", String.class, winner.getName());
            for (Player player : arena.getPlayers()) {
                arena.info(player, "Game Over! " + winnerName + " wins!");
            }
            for (Player player : arena.getSpectators()) {
                arena.info(player, "Game Over! " + winnerName + " wins!");
            }
        }
    }

    @Override
    public void onArenaCountdownTick(MiniGameArena arena, int secondsRemaining) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING
            && secondsRemaining > 0
            && secondsRemaining % DROP_INTERVAL_SECONDS == 0) {
            spawnRandomDrop(arena);
        }
    }

    @Override
    public void onArenaCountdownEnd(MiniGameArena arena) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RUNNING, RUNNING_COUNTDOWN_SECONDS);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            arena.setStatus(MiniGameArena.ArenaStatus.ENDING, ENDING_COUNTDOWN_SECONDS);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        String teamId = selectAvailableTeam(arena);
        if (teamId == null) {
            teamId = arena.getRandomTeam();
        }
        if (teamId != null && !teamId.isBlank()) {
            arena.setPlayerTeam(player, teamId);
        }

        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING
            && arena.numPlayers() >= arena.getMinPlayers()
            && activeTeamCount(arena) >= 2) {
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, STARTING_COUNTDOWN_SECONDS);
        }

        clearPlayerInventory(player);
        return arena.getLobbySpawn();
    }

    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.STARTING
            && (arena.numPlayers() < arena.getMinPlayers() || activeTeamCount(arena) < 2)) {
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            arena.setCountdown(0);
        } else if (arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
            checkForRoundEnd(arena);
        }
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        onPlayerLeaveArena(arena, player);
    }

    @Override
    public void onPlayerLeaveSpectator(MiniGameArena arena, Player player) {
        eliminatedSpectators(arena).remove(player.getUniqueId());
    }

    @Override
    public void onPlayerQuitSpectator(MiniGameArena arena, Player player) {
        eliminatedSpectators(arena).remove(player.getUniqueId());
    }

    private void startRound(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        reviveEliminatedSpectators(arena);
        clearPlacedBlocks(arena);
        restoreBeds(arena);
        resetRuntimeState(arena);
        clearTrackedEntities(arena);
        rebuildDropSurfaces(arena);
        announceDropAvailability(arena);
        arena.set("winnerTeam", "");

        for (Player player : arena.getPlayers()) {
            if (arena.getPlayerTeam(player) == null) {
                String teamId = selectAvailableTeam(arena);
                if (teamId != null && !teamId.isBlank()) {
                    arena.setPlayerTeam(player, teamId);
                }
            }
            equipPlayer(arena, player);
            arena.teleportToTeamSpawn(player);
        }
        for (Player spectator : arena.getSpectators()) {
            Location spectatorSpawn = arena.getSpectatorSpawn();
            spectator.teleport(spectatorSpawn != null ? spectatorSpawn : arena.getLobbySpawn());
        }
    }

    private void resetRound(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        reviveEliminatedSpectators(arena);
        clearPlacedBlocks(arena);
        restoreBeds(arena);
        resetRuntimeState(arena);
        clearTrackedEntities(arena);
        arena.set("winnerTeam", "");
        arena.removeAllOccupants();
    }

    private void resetRuntimeState(MiniGameArena arena) {
        for (MiniGameTeam team : arena.getTeams()) {
            team.set("bedAlive", true);
            team.setScore(0);
        }

        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer == null) {
                continue;
            }
            mgPlayer.setScore(0);
            mgPlayer.setKills(0);
            mgPlayer.setDeaths(0);
            mgPlayer.set("finalKills", 0);
        }

        eliminatedSpectators(arena).clear();
    }

    private void announceDropAvailability(@NotNull MiniGameArena arena) {
        if (bedWars.dropItems(arena).isEmpty()) {
            broadcastInfoToOccupants(arena, "<yellow>Supply drops are disabled for this arena because no drop items are configured.</yellow>");
            return;
        }

        if (!dropSurfaces(arena).isEmpty()) {
            return;
        }

        java.util.logging.Logger.getLogger(BedWarsArenaHandler.class.getName())
            .warning("[STEMCraft] BedWars arena '" + arena.id() + "' has drop items configured but no valid drop surfaces were found.");
        broadcastInfoToOccupants(arena, "<yellow>Supply drops are enabled, but this arena has no valid drop surfaces.</yellow>");
    }

    private void handleDeath(MiniGameArena arena, Player player, @Nullable Player damagerPlayer) {
        MiniGamePlayer victim = arena.getPlayer(player);
        if (victim == null) {
            return;
        }
        victim.addDeath();

        MiniGamePlayer damager = null;
        if (damagerPlayer != null && arena.hasPlayer(damagerPlayer)) {
            damager = arena.getPlayer(damagerPlayer);
            if (damager != null) {
                damager.addKill();
                bedWars.incrementStat("kills", arena, damager);
            }
        }

        MiniGameTeam team = arena.getPlayerTeam(player);
        if (team != null && team.get("bedAlive", Boolean.class, true)) {
            equipPlayer(arena, player);
            arena.teleportToTeamSpawn(player);
            player.setHealth(PlayerUtil.getMaxHealth(player));
            return;
        }

        if (damager != null) {
            int finalKills = damager.get("finalKills", Integer.class, 0) + 1;
            damager.set("finalKills", finalKills);
            bedWars.incrementStat("final_kills", arena, damager);
        }

        eliminatedSpectators(arena).add(player.getUniqueId());
        arena.addSpectator(player);
        checkForRoundEnd(arena);
    }

    private void reviveEliminatedSpectators(MiniGameArena arena) {
        Set<UUID> eliminated = eliminatedSpectators(arena);
        for (Player spectator : new ArrayList<>(arena.getSpectators())) {
            if (!eliminated.remove(spectator.getUniqueId())) {
                continue;
            }
            arena.addPlayer(spectator);
            if (arena.getPlayerTeam(spectator) == null) {
                String teamId = selectAvailableTeam(arena);
                if (teamId != null && !teamId.isBlank()) {
                    arena.setPlayerTeam(spectator, teamId);
                }
            }
        }
    }

    private void equipPlayer(MiniGameArena arena, Player player) {
        clearPlayerInventory(player);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);

        MiniGameTeam team = arena.getPlayerTeam(player);
        if (team != null) {
            arena.giveKit(player, team.getName(), false);
        }
    }

    private void clearPlayerInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[0]);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void checkForRoundEnd(MiniGameArena arena) {
        int activeTeams = activeTeamCount(arena);
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return;
        }
        if (activeTeams > 1) {
            return;
        }
        if (activeTeams == 1) {
            arena.setStatus(MiniGameArena.ArenaStatus.ENDING, ENDING_COUNTDOWN_SECONDS);
        } else {
            arena.setStatus(MiniGameArena.ArenaStatus.RESETTING);
        }
    }

    private int activeTeamCount(MiniGameArena arena) {
        int activeTeams = 0;
        for (MiniGameTeam team : arena.getTeams()) {
            if (!arena.getTeamPlayers(team.getName()).isEmpty()) {
                activeTeams++;
            }
        }
        return activeTeams;
    }

    private @Nullable MiniGameTeam determineWinner(MiniGameArena arena) {
        List<MiniGameTeam> candidates = arena.getTeams().stream()
            .filter(team -> !arena.getTeamPlayers(team.getName()).isEmpty())
            .sorted(Comparator
                .comparingInt((MiniGameTeam team) -> arena.getTeamPlayers(team.getName()).size())
                .thenComparing(team -> team.get("bedAlive", Boolean.class, true)))
            .toList();
        return candidates.isEmpty() ? null : candidates.getLast();
    }

    private @Nullable String selectAvailableTeam(MiniGameArena arena) {
        int teamSize = teamSize(arena);
        return assignmentTeams(arena).stream()
            .filter(team -> arena.getTeamPlayers(team.getName()).size() < teamSize)
            .min(Comparator.comparingInt(team -> arena.getTeamPlayers(team.getName()).size()))
            .map(MiniGameTeam::getName)
            .orElse(null);
    }

    private @NotNull List<MiniGameTeam> assignmentTeams(@NotNull MiniGameArena arena) {
        List<MiniGameTeam> teams = new ArrayList<>(arena.getTeams());
        if (teams.size() <= 2) {
            return teams;
        }

        int players = Math.max(1, arena.numPlayers());
        int teamSize = teamSize(arena);
        int desiredTeams = Math.max(2, players / 3);
        int requiredTeams = Math.max(2, (players + teamSize - 1) / teamSize);
        int activeTeams = Math.clamp(desiredTeams, requiredTeams, teams.size());
        return teams.subList(0, activeTeams);
    }

    private int teamSize(MiniGameArena arena) {
        return Math.max(1, arena.get("teamSize", Integer.class, 1));
    }

    private void registerInventoryProtection() {
        api.events().register(InventoryClickEvent.class, event -> {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (bedWars.minigame() == null) {
                return;
            }

            MiniGameArena arena = bedWars.minigame().findPlayer(player);
            if (arena == null || !BedWarsMiniGame.namespace().equals(arena.namespace()) || !arena.hasPlayer(player)) {
                return;
            }

            InventoryAction action = event.getAction();
            ClickType click = event.getClick();
            if (action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ONE_SLOT
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP) {
                event.setCancelled(true);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> eliminatedSpectators(MiniGameArena arena) {
        return arena.getOrCreate("eliminatedSpectators", Set.class, LinkedHashSet::new);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> trackedEntities(MiniGameArena arena) {
        return arena.getOrCreate("trackedEntities", Set.class, HashSet::new);
    }

    @SuppressWarnings("unchecked")
    private List<Location> dropSurfaces(MiniGameArena arena) {
        return arena.getOrCreate("dropSurfaces", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<BedBlockSnapshot>> bedSnapshots(MiniGameArena arena) {
        return arena.getOrCreate("bedSnapshots", Map.class, LinkedHashMap::new);
    }

    private void captureBedSnapshots(MiniGameArena arena) {
        Map<String, List<BedBlockSnapshot>> snapshots = bedSnapshots(arena);
        snapshots.clear();
        for (MiniGameTeam team : arena.getTeams()) {
            SCRegion bedRegion = team.get("bedRegion", SCRegion.class);
            if (bedRegion == null) {
                continue;
            }
            snapshots.put(team.getName(), snapshotRegion(bedRegion));
        }
    }

    private List<BedBlockSnapshot> snapshotRegion(SCRegion region) {
        List<BedBlockSnapshot> snapshots = new ArrayList<>();
        forEachRegionBlock(region, block -> snapshots.add(new BedBlockSnapshot(
            block.getLocation().clone(),
            block.getType(),
            block.getBlockData().clone()
        )));
        return snapshots;
    }

    private void restoreBeds(MiniGameArena arena) {
        for (Map.Entry<String, List<BedBlockSnapshot>> entry : bedSnapshots(arena).entrySet()) {
            for (BedBlockSnapshot snapshot : entry.getValue()) {
                snapshot.restore();
            }
            MiniGameTeam team = arena.getTeam(entry.getKey());
            if (team != null) {
                team.set("bedAlive", true);
            }
        }
    }

    private void destroyTeamBed(MiniGameArena arena, MiniGameTeam team) {
        SCRegion bedRegion = team.get("bedRegion", SCRegion.class);
        if (bedRegion == null) {
            return;
        }

        forEachRegionBlock(bedRegion, block -> {
            if (isBedBlock(block.getType())) {
                block.setType(Material.AIR, false);
            }
        });
        team.set("bedAlive", false);
    }

    private void clearPlacedBlocks(MiniGameArena arena) {
        Set<Location> blocks = placedBlocks(arena);
        for (Location loc : new LinkedHashSet<>(blocks)) {
            loc.getBlock().setType(Material.AIR);
        }
        blocks.clear();
    }

    private void clearTrackedEntities(MiniGameArena arena) {
        Set<UUID> trackedEntities = trackedEntities(arena);
        for (UUID entityId : new LinkedHashSet<>(trackedEntities)) {
            Entity entity = arena.world().getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        trackedEntities.clear();
    }

    private void rebuildDropSurfaces(@NotNull MiniGameArena arena) {
        List<Location> surfaces = dropSurfaces(arena);
        surfaces.clear();

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion == null) {
            return;
        }

        Location min = arenaRegion.getMinimumLocation();
        Location max = arenaRegion.getMaximumLocation();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                Location surface = findSurfaceDropLocation(arenaRegion, x, z);
                if (surface != null) {
                    surfaces.add(surface);
                }
            }
        }
    }

    private @Nullable Location findSurfaceDropLocation(@NotNull SCRegion arenaRegion, int x, int z) {
        Location min = arenaRegion.getMinimumLocation();
        Location max = arenaRegion.getMaximumLocation();
        for (int y = max.getBlockY(); y >= min.getBlockY(); y--) {
            Block block = arenaRegion.getWorld().getBlockAt(x, y, z);
            if (isValidDropSurface(arenaRegion, block)) {
                return block.getLocation();
            }
        }
        return null;
    }

    private boolean isValidDropSurface(@NotNull SCRegion arenaRegion, @NotNull Block block) {
        if (!arenaRegion.contains(block.getLocation())) {
            return false;
        }

        Material type = block.getType();
        if (type.isAir() || !type.isSolid() || type.name().contains("GLASS")) {
            return false;
        }

        Block above = block.getRelative(BlockFace.UP);
        Block aboveTwo = above.getRelative(BlockFace.UP);
        Location dropLocation = block.getLocation().add(0.5d, DROP_SPAWN_Y_OFFSET, 0.5d);
        return above.isPassable()
            && aboveTwo.isPassable()
            && arenaRegion.contains(above.getLocation())
            && arenaRegion.contains(dropLocation);
    }

    private void spawnRandomDrop(@NotNull MiniGameArena arena) {
        List<Material> configuredDrops = bedWars.dropItems(arena);
        if (configuredDrops.isEmpty()) {
            return;
        }

        List<Location> surfaces = dropSurfaces(arena);
        if (surfaces.isEmpty()) {
            rebuildDropSurfaces(arena);
            surfaces = dropSurfaces(arena);
            if (surfaces.isEmpty()) {
                return;
            }
        }

        List<Location> candidates = new ArrayList<>(surfaces);
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        for (Location surface : candidates) {
            SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
            if (arenaRegion == null || !isValidDropSurface(arenaRegion, surface.getBlock())) {
                continue;
            }

            ItemStack item = new ItemStack(configuredDrops.get(ThreadLocalRandom.current().nextInt(configuredDrops.size())));
            Location dropLocation = surface.clone().add(0.5d, DROP_SPAWN_Y_OFFSET, 0.5d);
            Item droppedItem = surface.getWorld().dropItem(dropLocation, item);
            droppedItem.setPickupDelay(10);
            trackedEntities(arena).add(droppedItem.getUniqueId());
            announceSupplyDrop(arena, dropLocation);
            playSoundToOccupants(arena);
            return;
        }
    }

    private @Nullable MiniGameTeam findBedOwner(MiniGameArena arena, Location location) {
        for (MiniGameTeam team : arena.getTeams()) {
            SCRegion bedRegion = team.get("bedRegion", SCRegion.class);
            if (bedRegion != null && bedRegion.contains(location)) {
                return team;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<Location> placedBlocks(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("blocks", Set.class, HashSet::new);
    }

    private boolean containsBedBlock(SCRegion region) {
        final boolean[] found = {false};
        forEachRegionBlock(region, block -> {
            if (isBedBlock(block.getType())) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private boolean isBedBlock(Material material) {
        return material != null && material.name().endsWith("_BED");
    }

    private @Nullable Player resolveDamager(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }

        Entity damager = byEntity.getDamager();
        if (damager instanceof Player directDamager) {
            return directDamager;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }

    private void forEachRegionBlock(SCRegion region, java.util.function.Consumer<Block> consumer) {
        Location minLoc = region.getMinimumLocation();
        Location maxLoc = region.getMaximumLocation();

        for (int x = minLoc.getBlockX(); x <= maxLoc.getBlockX(); x++) {
            for (int y = minLoc.getBlockY(); y <= maxLoc.getBlockY(); y++) {
                for (int z = minLoc.getBlockZ(); z <= maxLoc.getBlockZ(); z++) {
                    Block block = region.getWorld().getBlockAt(x, y, z);
                    if (region.contains(block.getLocation())) {
                        consumer.accept(block);
                    }
                }
            }
        }
    }

    private void broadcastInfoToOccupants(@NotNull MiniGameArena arena, @NotNull String message, Player... exclude) {
        Set<Player> excluded = Set.of(exclude);
        for (Player occupant : arena.getOccupants()) {
            if (!excluded.contains(occupant)) {
                arena.info(occupant, message);
            }
        }
    }

    private void playSoundToOccupants(@NotNull MiniGameArena arena) {
        for (Player occupant : arena.getOccupants()) {
            occupant.playSound(occupant.getLocation(), Sound.ENTITY_ITEM_PICKUP, (float) 0.6, (float) 1.35);
        }
    }

    private void announceSupplyDrop(@NotNull MiniGameArena arena, @NotNull Location dropLocation) {
        for (Player occupant : arena.getOccupants()) {
            arena.info(occupant, supplyDropHint(occupant, dropLocation));
        }
    }

    private @NotNull String supplyDropHint(@NotNull Player player, @NotNull Location dropLocation) {
        int distance = Math.max(1, (int) Math.round(horizontalDistance(player.getLocation(), dropLocation)));
        String direction = relativeDirection(player, dropLocation);
        String suffix = distance == 1 ? "block" : "blocks";
        return "<gold>A supply drop has landed " + distance + " " + suffix + " " + direction + ".</gold>";
    }

    private double horizontalDistance(@NotNull Location from, @NotNull Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt((dx * dx) + (dz * dz));
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

    private String regionListenerPrefix(String arenaId) {
        return NamespaceId.of(BedWarsMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }

    private record BedBlockSnapshot(Location location, Material material, BlockData blockData) {
        void restore() {
            Block block = location.getBlock();
            block.setType(material, false);
            block.setBlockData(blockData.clone(), false);
        }
    }
}
