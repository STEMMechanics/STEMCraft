package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.region.RegionListener;
import dev.stemcraft.api.util.NamespaceId;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MobArenaArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final MobArenaMiniGame mobArena;

    public MobArenaArenaHandler(STEMCraftAPI api, MobArenaMiniGame mobArena) {
        this.api = api;
        this.mobArena = mobArena;
    }

    boolean zonesExist(@NotNull MiniGameArena arena) {
        return arena.getMap("zones", String.class, SCRegion.class) != null;
    }

    @Nullable SCRegion getZone(@NotNull MiniGameArena arena, @NotNull String zone) {
        return arena.getMap("zones", String.class, SCRegion.class).get(zone);
    }

    @NotNull SCRegion getZone(@NotNull MiniGameArena arena, @NotNull String zone, @NotNull SCRegion def) {
        return arena.getMap("zones", String.class, SCRegion.class).getOrDefault(zone, def);
    }

    boolean containsZone(@NotNull MiniGameArena arena, @NotNull String zone) {
        return arena.getMap("zones", String.class, SCRegion.class).containsKey(zone);
    }

    @Override
    public void validate(@NotNull MiniGameArena arena, @NotNull ArenaValidationResult result) {
        if (arena.getLobbySpawn() == null) {
            result.addError("Lobby spawn is not defined.", "lobbySpawn");
        }

        if (!zonesExist(arena)) {
            result.addError("Zones are not defined.", "zones");
        } else {
            if (!containsZone(arena, "arena")) {
                result.addError("Arena zone is not defined (this may be the root cause of the arena region error).", "arenaZone");
            }
        }

        if (arena.get("spawner-configs.max", int.class) == 0) {
            result.addError("No spawner configs.", "spawner-configs");
        }

        if (arena.getMinPlayers() < 2) {
            result.addError("Mob arena arenas require at least 2 minimum players.", "minPlayers");
        }
        if (arena.getMaxPlayers() < 2) {
            result.addError("Mob arena arenas require at least 2 maximum players.", "maxPlayers");
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        arena.getOrCreate("trackedEntities", Set.class, HashSet::new);
        arena.getOrCreate("trackedAndCountedEntities", Set.class, HashSet::new);
        String listenerPrefix = regionListenerPrefix(arena.id());

        SCRegion arenaRegion = getZone(arena, "arena");
        assert arenaRegion != null;
        api.regions().addListener(listenerPrefix + "boundary", arenaRegion, new RegionListener() {
            @Override
            public void onExit(@NotNull Player player, @NotNull SCRegion region, @Nullable Location from, @Nullable Location to) {
                if (arena.hasPlayer(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING) {
                    handleDeath(arena, player, null);
                } else if (arena.hasOccupant(player) && arena.getStatus() == MiniGameArena.ArenaStatus.ENDING) {
                    keepOccupantInEndingArea(arena, player);
                } else if (arena.hasOccupant(player) && arena.getStatus() == MiniGameArena.ArenaStatus.RESETTING) {
                    arena.removeOccupant(player);
                }
            }
        });
    }

    private void handleDeath(@NotNull MiniGameArena arena, @NotNull Player player, @Nullable Mob damagerMob) {
        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer != null) {
            mgPlayer.addDeath();
        }

        if (damagerMob != null) {
            broadcastInfoToOccupants(arena,
                    "<red>" + player.getName() + "</red> <gray>was eliminated by a</gray> <gold>" + damagerMob.getName() + "</gold><gray>.</gray>");
        } else {
            broadcastInfoToOccupants(arena,
                    "<red>" + player.getName() + "</red> <gray>fell into the void.</gray>");
        }

        arena.teleportToLobby(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setHealth(PlayerUtil.getMaxHealth(player));
    }


    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
    private void broadcastInfoToOccupants(@NotNull MiniGameArena arena, @NotNull String message, Player... exclude) {
        Set<Player> excluded = Set.of(exclude);
        for (Player occupant : arena.getOccupants()) {
            if (!excluded.contains(occupant)) {
                arena.info(occupant, message);
            }
        }
    }

    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
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

    // TODO: Merge this into a static class, repeated code impl from bridge - ProjectHSI
    private String regionListenerPrefix(String arenaId) {
        return NamespaceId.of(MobArenaMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }
}
