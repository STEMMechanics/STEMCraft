package dev.stemcraft.minigame.parkour;

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
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class ParkourArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final ParkourMiniGame parkour;

    public ParkourArenaHandler(STEMCraftAPI api, ParkourMiniGame parkour) {
        this.api = api;
        this.parkour = parkour;
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        SCRegion lobbyRegion = arena.get("lobbyRegion", SCRegion.class);
        SCRegion finishRegion = arena.get("finishRegion", SCRegion.class);

        if (lobbyRegion == null) {
            result.addError("Lobby region is not defined.", "lobbyRegion");
        }
        if (arenaRegion == null) {
            result.addError("Arena region is not defined.", "arenaRegion");
        }
        if (finishRegion == null) {
            result.addError("Finish region is not defined.", "finishRegion");
        }
        if (arena.getLobbySpawn() == null && lobbyRegion != null) {
            result.addError("Lobby spawn could not be derived from the lobby region.", "lobbyRegion");
        }
        if (arenaRegion != null && finishRegion != null && !arenaRegion.contains(finishRegion)) {
            result.addError("Finish region must be contained within the arena region.", "finishRegion");
        }
        if (arenaRegion != null && lobbyRegion != null && !arenaRegion.contains(lobbyRegion)) {
            result.addError("Lobby region must be contained within the arena region.", "lobbyRegion");
        }
    }

    @Override
    public void onArenaLoad(MiniGameArena arena) {
        SCRegion arenaRegion = arena.get("arenaRegion", SCRegion.class);
        parkour.syncLobbyRegion(arena);
        SCRegion lobbyRegion = arena.get("lobbyRegion", SCRegion.class);
        SCRegion finishRegion = arena.get("finishRegion", SCRegion.class);
        String prefix = listenerPrefix(arena.id());

        if (arenaRegion != null) {
            api.regions().addListener(prefix + "arena", arenaRegion, new RegionListener() {
                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region) {
                    if (!arena.hasPlayer(player)) {
                        return;
                    }
                    MiniGamePlayer mgPlayer = arena.getPlayer(player);
                    if (mgPlayer != null && parkour.isRunning(mgPlayer)) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
                        parkour.resetRun(arena, player, "Run failed. Returned to the start.");
                        return;
                    }

                    arena.removePlayer(player);
                }
            });
        }

        if (lobbyRegion != null) {
            api.regions().addListener(prefix + "lobby", lobbyRegion, new RegionListener() {
                @Override
                public void onEnter(@NotNull Player player, @NotNull SCRegion region) {
                    if (!arena.hasPlayer(player)) {
                        return;
                    }
                    MiniGamePlayer mgPlayer = arena.getPlayer(player);
                    if (mgPlayer != null && !parkour.isRunning(mgPlayer)) {
                        parkour.armRun(arena, player);
                    }
                    enforceAdventureMode(arena, player, false);
                }

                @Override
                public void onExit(@NotNull Player player, @NotNull SCRegion region) {
                    if (!arena.hasPlayer(player)) {
                        return;
                    }
                    if (parkour.startRun(arena, player)) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.75f, 1.35f);
                    }
                    enforceAdventureMode(arena, player, false);
                }
            });
        }

        if (finishRegion != null) {
            api.regions().addListener(prefix + "finish", finishRegion, new RegionListener() {
                @Override
                public void onEnter(@NotNull Player player, @NotNull SCRegion region) {
                    if (!arena.hasPlayer(player)) {
                        return;
                    }
                    MiniGamePlayer before = arena.getPlayer(player);
                    if (before == null || !parkour.isRunning(before)) {
                        return;
                    }
                    parkour.completeRun(arena, player);
                    MiniGamePlayer mgPlayer = arena.getPlayer(player);
                    if (mgPlayer != null) {
                        long lastRun = parkour.lastRunMillis(mgPlayer);
                        boolean finished = lastRun > 0L;
                        if (finished) {
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onArenaUnload(MiniGameArena arena) {
        api.regions().removeListener(listenerPrefix(arena.id()) + "*");
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
        if (!(event.getEntity() instanceof Player player)) {
            return HandlerEventResult.ALLOW;
        }

        MiniGamePlayer mgPlayer = arena.getPlayer(player);
        if (mgPlayer == null) {
            return HandlerEventResult.ALLOW;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
            || player.getHealth() - event.getFinalDamage() <= 0.0d) {
            if (!parkour.isRunning(mgPlayer)) {
                return HandlerEventResult.DENY;
            }

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            parkour.resetRun(arena, player, "Run failed. Returned to the start.");
        }
        return HandlerEventResult.DENY;
    }

    @Override
    public HandlerEventResult onPlayerDropItem(MiniGameArena arena, Player player, ItemStack item) {
        return HandlerEventResult.DENY;
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        parkour.clearRun(arena, player);
        parkour.armRun(arena, player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        enforceAdventureMode(arena, player, true);
        return arena.getLobbySpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        parkour.clearRun(arena, player);
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        parkour.clearRun(arena, player);
    }

    private String listenerPrefix(String arenaId) {
        return NamespaceId.of(ParkourMiniGame.namespace(), NamespaceId.sanitizePath(arenaId) + "_");
    }

    private void enforceAdventureMode(@NotNull MiniGameArena arena, @NotNull Player player, boolean delayed) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);

        if (!delayed) {
            return;
        }

        api.tasks().runLater(2L, () -> {
            if (!arena.hasPlayer(player)) {
                return;
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
        });
    }
}
