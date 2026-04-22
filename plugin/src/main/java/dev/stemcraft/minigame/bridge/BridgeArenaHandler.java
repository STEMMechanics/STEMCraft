/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.minigame.bridge;

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
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public class BridgeArenaHandler implements MiniGameArenaHandler {
    private static final int TEAM_SCORE_START = 7;
    private static final int STARTING_COUNTDOWN_SECONDS = 30;
    private static final int RUNNING_COUNTDOWN_SECONDS = 300;
    private static final int ENDING_COUNTDOWN_SECONDS = 15;
    private static final int DROP_INTERVAL_SECONDS = 30;
    private static final int TNT_FUSE_TICKS = 60;
    private static final double DROP_SPAWN_Y_OFFSET = 1.15d;

    private final STEMCraftAPI api;
    private final BridgeMiniGame bridge;

    public BridgeArenaHandler(STEMCraftAPI api, BridgeMiniGame bridge) {
        this.api = api;
        this.bridge = bridge;
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion bridgeRegion = arena.get("bridgeRegion", SCRegion.class);
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);

        if (arena.getLobbySpawn() == null) {
            result.addError("Lobby spawn is not defined.", "lobbySpawn");
        }
        if (bridgeRegion == null) {
            result.addError("Bridge region is not defined.", "bridgeRegion");
        }
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arenaRegion");
        }
        if (bridgeRegion != null && arenaRegion != null && !arenaRegion.contains(bridgeRegion)) {
            result.addError("Bridge region must be contained within the arena region.", "bridgeRegion");
        }
        if (arena.getTeams().size() != 2 || arena.getTeam("red") == null || arena.getTeam("blue") == null) {
            result.addError("Bridge arenas must define exactly the teams 'red' and 'blue'.", "teams");
        }
        if (arena.getMinPlayers() < 2) {
            result.addError("Bridge arenas require at least 2 minimum players.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 2) {
            result.addError("Bridge arenas require at least 2 maximum players.", "maxPlayers");
        }

        for (String teamId : Set.of("red", "blue")) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                continue;
            }
            if (team.getSpawn() == null) {
                result.addError("Team '" + teamId + "' is missing a spawn.", "teams." + teamId + ".spawn");
            } else if (arenaRegion != null && !arenaRegion.contains(team.getSpawn())) {
                result.addError("Team '" + teamId + "' spawn must be inside the arena region.", "teams." + teamId + ".spawn");
            }

            SCRegion portalRegion = team.get("portalRegion", SCRegion.class);
            if (portalRegion == null) {
                result.addError("Team '" + teamId + "' is missing a portal region.", "teams." + teamId + ".portal");
            } else if (arenaRegion != null && !arenaRegion.contains(portalRegion)) {
                result.addError("Team '" + teamId + "' portal region must be inside the arena region.", "teams." + teamId + ".portal");
            } else {
                for (String spawnTeamId : Set.of("red", "blue")) {
                    MiniGameTeam spawnTeam = arena.getTeam(spawnTeamId);
                    if (spawnTeam != null && spawnTeam.getSpawn() != null && portalRegion.contains(spawnTeam.getSpawn())) {
                        result.addError("Team '" + teamId + "' portal region must not contain the '" + spawnTeamId + "' spawn.", "teams." + teamId + ".portal");
                    }
                }
            }
        }

        MiniGameTeam red = arena.getTeam("red");
        MiniGameTeam blue = arena.getTeam("blue");
        if (red != null && blue != null) {
            SCRegion redPortal = red.get("portalRegion", SCRegion.class);
            SCRegion bluePortal = blue.get("portalRegion", SCRegion.class);
            if (redPortal != null && bluePortal != null && redPortal.intersects(bluePortal)) {
                result.addError("Red and blue portal regions must not overlap.", "teams");
            }
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        placedBlocks(arena);
        arena.getOrCreate("trackedEntities", Set.class, HashSet::new);
        arena.getOrCreate("dropSurfaces", List.class, ArrayList::new);
        String listenerPrefix = regionListenerPrefix(arena.id());

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (arenaRegion != null) {
            api.regions().addListener(listenerPrefix + "boundary", arenaRegion, new RegionListener() {
                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region, Location from, Location to) {
                    if (arena.hasPlayer(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
                        if (tryHandlePortalScore(arena, player, from, to)) {
                            return;
                        }
                        handleDeath(arena, player, null);
                    } else if (arena.hasOccupant(player) && arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
                        keepOccupantInEndingArea(arena, player);
                    } else if (arena.hasOccupant(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RESETTING) {
                        arena.removeOccupant(player);
                    }
                }
            });
        }

        for (String teamId : Set.of("red", "blue")) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                continue;
            }

            SCRegion portalRegion = team.get("portalRegion", SCRegion.class);
            if (portalRegion == null) {
                continue;
            }

            api.regions().addListener(listenerPrefix + "team_" + teamId, portalRegion, new RegionListener() {
                @Override
                public void onEnter(@NotNull Player player, @NotNull SCRegion region, @NotNull Location from, @NotNull Location to) {
                    if (!arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
                        return;
                    }
                    if (isRespawning(arena, player)) {
                        return;
                    }
                    tryHandlePortalScore(arena, player, from, to);
                }
            });
        }
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        clearTrackedEntities(arena);
        String listenerPrefix = regionListenerPrefix(arena.id());
        api.regions().removeListener(listenerPrefix + "boundary");
        for (String teamId : Set.of("red", "blue")) {
            api.regions().removeListener(listenerPrefix + "team_" + teamId);
        }
    }

    @Override
    public HandlerEventResult onBlockBreak(MiniGameArena arena, Player player, org.bukkit.block.Block block) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return HandlerEventResult.DENY;
        }

        Set<Location> blockLocations = placedBlocks(arena);
        if (blockLocations.remove(block.getLocation())) {
            return HandlerEventResult.ALLOW;
        }
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onBlockPlace(MiniGameArena arena, Player player, org.bukkit.block.Block block) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return HandlerEventResult.DENY;
        }

        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        if (block.getType() == Material.TNT) {
            if (arenaRegion != null && arenaRegion.contains(block.getLocation())) {
                primePlacedTnt(arena, player, block);
                return HandlerEventResult.ALLOW;
            }
            return HandlerEventResult.DENY;
        }

        SCRegion bridgeRegion = arena.get("bridgeRegion", SCRegion.class);
        if (bridgeRegion != null && bridgeRegion.contains(block.getLocation())) {
            placedBlocks(arena).add(block.getLocation());
            return HandlerEventResult.ALLOW;
        }
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

        double finalDamage = event.getFinalDamage();
        if (player.getHealth() - finalDamage > 0.0d) {
            return HandlerEventResult.ALLOW;
        }

        Player damagerPlayer = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            switch (damager) {
                case Player directDamager -> damagerPlayer = directDamager;
                case Projectile projectile -> {
                    ProjectileSource source = projectile.getShooter();
                    if (source instanceof Player shooter) {
                        damagerPlayer = shooter;
                    }
                }
                case TNTPrimed primedTnt when primedTnt.getSource() instanceof Player sourcePlayer ->
                        damagerPlayer = sourcePlayer;
                default -> {
                }
            }
        }

        handleDeath(arena, player, damagerPlayer);
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onPlayerDropItem(MiniGameArena arena, Player player, ItemStack item) {
        return HandlerEventResult.DENY;
    }

    @Override
    public void onEntityExplode(MiniGameArena arena, EntityExplodeEvent event) {
        pruneExplosion(arena, event.blockList());
        event.setYield(0.0f);
    }

    @Override
    public void onBlockExplode(MiniGameArena arena, BlockExplodeEvent event) {
        pruneExplosion(arena, event.blockList());
        event.setYield(0.0f);
    }

    @Override
    public void onArenaStatusChanged(MiniGameArena arena, MiniGameArena.ArenaStatus oldStatus, MiniGameArena.ArenaStatus newStatus) {
        if (newStatus == MiniGameArena.ArenaStatus.RUNNING) {
            startRound(arena);
            playSoundToOccupants(arena, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.15f);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.RESETTING) {
            resetRound(arena);
            arena.setStatus(MiniGameArena.ArenaStatus.WAITING);
            return;
        }

        if (newStatus == MiniGameArena.ArenaStatus.ENDING) {
            MiniGameTeam winner = winningTeam(arena);
            if (winner != null) {
                updateWinStreakStats(arena, winner);
            } else {
                resetWinStreakStats(arena);
            }
            moveOccupantsToEndingSpawns(arena);
            startWinnerCelebration(arena, winner);
            if (winner == null) {
                broadcastInfoToOccupants(arena, "<gold>Game Over!</gold> <gray>The round ended in a draw.</gray>");
            } else {
                broadcastInfoToOccupants(arena, "<gold>Game Over!</gold> <gray>The</gray> " + renderTeamName(winner) + " <gray>team has won.</gray>");
            }
            playSoundToOccupants(arena, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.0f);
        }
    }

    @Override
    public void onArenaCountdownTick(MiniGameArena arena, int secondsRemaining) {
        if (secondsRemaining <= 0) {
            return;
        }

        MiniGameArena.ArenaStatus status = arena.getStatus();
        if (status == MiniGameArena.ArenaStatus.RUNNING && secondsRemaining % DROP_INTERVAL_SECONDS == 0) {
            spawnRandomDrop(arena);
        }
        if ((status == MiniGameArena.ArenaStatus.STARTING || status == MiniGameArena.ArenaStatus.ENDING) && secondsRemaining <= 5) {
            float pitch = 1.0f + ((5 - secondsRemaining) * 0.1f);
            playSoundToOccupants(arena, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, pitch);
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
        if (arena.getStatus() == MiniGameArena.ArenaStatus.WAITING && arena.numPlayers() >= arena.getMinPlayers()) {
            arena.setStatus(MiniGameArena.ArenaStatus.STARTING, STARTING_COUNTDOWN_SECONDS);
        }
        clearPlayerInventory(player);
        return arena.getLobbySpawn();
    }

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

    private void startRound(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        clearPlacedBlocks(arena);
        clearTrackedEntities(arena);
        rebuildDropSurfaces(arena);
        announceDropAvailability(arena);
        resetScores(arena);
        for (Player player : arena.getPlayers()) {
            equipPlayer(arena, player);
            arena.teleportToTeamSpawn(player);
        }
        for (Player spectator : arena.getSpectators()) {
            Location spectatorSpawn = arena.getSpectatorSpawn();
            if (spectatorSpawn != null) {
                spectator.teleport(spectatorSpawn);
            }
        }
    }

    private void resetRound(MiniGameArena arena) {
        arena.stopWinnerCelebration();
        clearPlacedBlocks(arena);
        clearTrackedEntities(arena);
        resetScores(arena);
        arena.removeAllOccupants();
    }

    private void resetScores(MiniGameArena arena) {
        for (String teamId : Set.of("red", "blue")) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team != null) {
                team.setScore(TEAM_SCORE_START);
            }
        }

        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer == null) {
                continue;
            }
            mgPlayer.setScore(0);
            mgPlayer.setKills(0);
            mgPlayer.setDeaths(0);
        }
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
        World world = arena.world();
        for (UUID entityId : new LinkedHashSet<>(trackedEntities)) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        trackedEntities.clear();
    }

    private void handleDeath(MiniGameArena arena, Player player, Player damagerPlayer) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.addDeath();
        }

        if (damagerPlayer != null && arena.hasPlayer(damagerPlayer)) {
            MiniGamePlayer damager = arena.getPlayer(damagerPlayer);
            if (damager != null) {
                damager.addKill();
            }
            arena.info(damagerPlayer, "<yellow>You eliminated</yellow> <red>" + player.getName() + "</red><yellow>.</yellow>");
            broadcastInfoToOccupants(arena,
                "<red>" + player.getName() + "</red> <gray>was eliminated by</gray> <gold>" + damagerPlayer.getName() + "</gold><gray>.</gray>",
                damagerPlayer);
        } else {
            broadcastInfoToOccupants(arena,
                "<red>" + player.getName() + "</red> <gray>fell into the void.</gray>");
        }

        markRespawning(arena, player);
        equipPlayer(arena, player);
        arena.teleportToTeamSpawn(player);
        player.setHealth(PlayerUtil.getMaxHealth(player));
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

    private void addTeamPoint(MiniGameArena arena, MiniGamePlayer player, MiniGameTeam targetTeam) {
        if (player == null || targetTeam == null) {
            return;
        }

        MiniGameTeam scoringTeam = arena.getPlayerTeam(player);
        targetTeam.subScore();
        player.addScore();
        api.playerStats().increment(player.getPlayer().getUniqueId(), player.getPlayer().getName(), bridge.goalsTotalStatKey(), 1.0d);
        api.playerStats().increment(player.getPlayer().getUniqueId(), player.getPlayer().getName(), bridge.goalsArenaStatKey(arena.id()), 1.0d);
        api.holograms().update("stat_leaderboard", bridge.goalsTotalStatKey());
        api.holograms().update("stat_leaderboard", bridge.goalsArenaStatKey(arena.id()));
        broadcastInfoToOccupants(arena,
            "<gold>" + player.getPlayer().getName() + "</gold> <green>scored for</green> " + renderTeamName(scoringTeam) + "<green>!</green>");
        playSoundToOccupants(arena, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.85f, 1.2f);

        if (targetTeam.getScore() <= 0) {
            arena.setStatus(MiniGameArena.ArenaStatus.ENDING, ENDING_COUNTDOWN_SECONDS);
            return;
        }

        for (Player arenaPlayer : arena.getPlayers()) {
            equipPlayer(arena, arenaPlayer);
            arena.teleportToTeamSpawn(arenaPlayer);
        }
    }

    private MiniGameTeam winningTeam(MiniGameArena arena) {
        boolean tied = false;
        MiniGameTeam winningTeam = null;
        for (String teamId : Set.of("red", "blue")) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                continue;
            }
            if (winningTeam == null || team.getScore() > winningTeam.getScore()) {
                winningTeam = team;
                tied = false;
            } else if (winningTeam.getScore() == team.getScore()) {
                tied = true;
            }
        }
        return tied ? null : winningTeam;
    }

    private void updateWinStreakStats(@NotNull MiniGameArena arena, MiniGameTeam winner) {
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer == null) {
                continue;
            }

            boolean won = winner != null && winner.getName().equalsIgnoreCase(mgPlayer.getTeam());
            if (!won) {
                api.playerStats().set(player.getUniqueId(), player.getName(), bridge.winStreakCurrentStatKey(), 0.0d);
                continue;
            }

            double nextStreak = api.playerStats().total(player.getUniqueId(), bridge.winStreakCurrentStatKey()) + 1.0d;
            api.playerStats().set(player.getUniqueId(), player.getName(), bridge.winStreakCurrentStatKey(), nextStreak);

            double bestStreak = api.playerStats().total(player.getUniqueId(), bridge.winStreakBestStatKey());
            if (nextStreak > bestStreak) {
                api.playerStats().set(player.getUniqueId(), player.getName(), bridge.winStreakBestStatKey(), nextStreak);
            }
        }

        api.holograms().update("stat_leaderboard", bridge.winStreakCurrentStatKey());
        api.holograms().update("stat_leaderboard", bridge.winStreakBestStatKey());
    }

    private void resetWinStreakStats(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            api.playerStats().set(player.getUniqueId(), player.getName(), bridge.winStreakCurrentStatKey(), 0.0d);
        }

        api.holograms().update("stat_leaderboard", bridge.winStreakCurrentStatKey());
        api.holograms().update("stat_leaderboard", bridge.winStreakBestStatKey());
    }

    private void moveOccupantsToEndingSpawns(@NotNull MiniGameArena arena) {
        for (Player player : arena.getPlayers()) {
            equipPlayer(arena, player);
            arena.teleportToTeamSpawn(player);
        }

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

    private void announceDropAvailability(@NotNull MiniGameArena arena) {
        if (bridge.dropItems(arena).isEmpty()) {
            broadcastInfoToOccupants(arena, "<yellow>Supply drops are disabled for this arena because no drop items are configured.</yellow>");
            return;
        }

        if (!dropSurfaces(arena).isEmpty()) {
            return;
        }

        api.messages().warn("[STEMCraft] Bridge arena '" + arena.id() + "' has drop items configured but no valid drop surfaces were found.");
        broadcastInfoToOccupants(arena, "<yellow>Supply drops are enabled, but this arena has no valid drop surfaces.</yellow>");
    }

    private void startWinnerCelebration(MiniGameArena arena, MiniGameTeam winner) {
        if (winner == null) {
            arena.stopWinnerCelebration();
            return;
        }

        List<Location> celebrationAnchors = arena.getTeamPlayers(winner.getName()).stream()
            .map(Player::getLocation)
            .filter(location -> location.getWorld() != null)
            .map(Location::clone)
            .toList();
        if (celebrationAnchors.isEmpty() && winner.getSpawn() != null) {
            celebrationAnchors = List.of(winner.getSpawn().clone());
        }
        if (celebrationAnchors.isEmpty()) {
            return;
        }

        if ("red".equalsIgnoreCase(winner.getName())) {
            arena.startWinnerCelebration(celebrationAnchors, ENDING_COUNTDOWN_SECONDS, Color.RED, Color.ORANGE, Color.YELLOW);
        } else if ("blue".equalsIgnoreCase(winner.getName())) {
            arena.startWinnerCelebration(celebrationAnchors, ENDING_COUNTDOWN_SECONDS, Color.AQUA, Color.BLUE, Color.WHITE);
        } else {
            arena.startWinnerCelebration(celebrationAnchors, ENDING_COUNTDOWN_SECONDS);
        }
    }

    private String regionListenerPrefix(String arenaId) {
        return NamespaceId.of(BridgeMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
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
    private Set<UUID> respawningPlayers(MiniGameArena arena) {
        return arena.getOrCreate("respawningPlayers", Set.class, HashSet::new);
    }

    private void markRespawning(MiniGameArena arena, Player player) {
        UUID uuid = player.getUniqueId();
        respawningPlayers(arena).add(uuid);
        api.tasks().nextTick(() -> respawningPlayers(arena).remove(uuid));
    }

    private boolean isRespawning(MiniGameArena arena, Player player) {
        return respawningPlayers(arena).contains(player.getUniqueId());
    }

    private boolean tryHandlePortalScore(@NotNull MiniGameArena arena, @NotNull Player player, Location from, Location to) {
        if (from == null || to == null || !arena.hasPlayer(player) || arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return false;
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        MiniGameTeam playerTeam = arena.getPlayerTeam(player);
        MiniGameTeam portalTeam = portalTeamForPath(arena, from, to);
        if (mgPlayer == null || playerTeam == null || portalTeam == null) {
            return false;
        }
        if (portalTeam.getName().equalsIgnoreCase(playerTeam.getName())) {
            return false;
        }

        addTeamPoint(arena, mgPlayer, portalTeam);
        return true;
    }

    private MiniGameTeam portalTeamForPath(@NotNull MiniGameArena arena, @NotNull Location from, @NotNull Location to) {
        for (String teamId : Set.of("red", "blue")) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                continue;
            }

            SCRegion portalRegion = team.get("portalRegion", SCRegion.class);
            if (portalRegion != null && portalRegion.intersectsPath(from, to)) {
                return team;
            }
        }
        return null;
    }

    private void broadcastInfoToOccupants(@NotNull MiniGameArena arena, @NotNull String message, Player... exclude) {
        Set<Player> excluded = Set.of(exclude);
        for (Player occupant : arena.getOccupants()) {
            if (!excluded.contains(occupant)) {
                arena.info(occupant, message);
            }
        }
    }

    private void playSoundToOccupants(@NotNull MiniGameArena arena, @NotNull Sound sound, float volume, float pitch) {
        for (Player occupant : arena.getOccupants()) {
            occupant.playSound(occupant.getLocation(), sound, volume, pitch);
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

    private void primePlacedTnt(@NotNull MiniGameArena arena, @NotNull Player player, @NotNull Block block) {
        Location spawnLocation = block.getLocation().add(0.5d, 0.5d, 0.5d);
        api.tasks().nextTick(() -> {
            if (block.getType() != Material.TNT) {
                return;
            }

            block.setType(Material.AIR, false);
            TNTPrimed primedTnt = block.getWorld().spawn(spawnLocation, TNTPrimed.class);
            primedTnt.setFuseTicks(TNT_FUSE_TICKS);
            primedTnt.setSource(player);
            trackedEntities(arena).add(primedTnt.getUniqueId());
            playSoundToOccupants(arena, Sound.ENTITY_TNT_PRIMED, 0.9f, 1.0f);
        });
    }

    private void pruneExplosion(@NotNull MiniGameArena arena, @NotNull List<Block> blockList) {
        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            blockList.clear();
            return;
        }

        Set<Location> placedBlocks = placedBlocks(arena);
        blockList.removeIf(block -> !placedBlocks.remove(block.getLocation()));
    }

    @SuppressWarnings("unchecked")
    private @NotNull Set<Location> placedBlocks(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("blocks", Set.class, HashSet::new);
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

    private Location findSurfaceDropLocation(@NotNull SCRegion arenaRegion, int x, int z) {
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
        List<Material> configuredDrops = bridge.dropItems(arena);
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
            playSoundToOccupants(arena, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.35f);
            return;
        }
    }

    private @NotNull String renderTeamName(MiniGameTeam team) {
        if (team == null) {
            return "<gray>Unknown</gray>";
        }

        String displayName = team.get("displayName", String.class, team.getName());
        return switch (team.getName().toLowerCase()) {
            case "red" -> "<red>" + displayName + "</red>";
            case "blue" -> "<blue>" + displayName + "</blue>";
            default -> "<gray>" + displayName + "</gray>";
        };
    }
}
