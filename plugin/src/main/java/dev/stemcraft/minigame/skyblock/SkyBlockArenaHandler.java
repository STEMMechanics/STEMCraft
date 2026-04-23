package dev.stemcraft.minigame.skyblock;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class SkyBlockArenaHandler implements MiniGameArenaHandler {
    private final STEMCraftAPI api;
    private final SkyBlockMiniGame skyBlock;

    public SkyBlockArenaHandler(STEMCraftAPI api, SkyBlockMiniGame skyBlock) {
        this.api = api;
        this.skyBlock = skyBlock;
    }

    @Override
    public void validate(@NonNull MiniGameArena arena, ArenaValidationResult result) {
        if (arena.getLobbySpawn() == null) {
            result.addError("Island spawn is not defined.", "spawn");
        }
        if (skyBlock.ownerUuid(arena) == null) {
            result.addError("Owner uuid is not defined.", "ownerUuid");
        }
        if (skyBlock.ownerName(arena).isBlank()) {
            result.addError("Owner name is not defined.", "ownerName");
        }
    }

    @Override
    public Location onPlayerJoinArena(MiniGameArena arena, Player player) {
        SkyBlockPlayerState state = skyBlock.savedPlayerState(arena);
        if (skyBlock.isOwner(arena, player) && state != null) {
            state.apply(player, true);
            return player.getLocation();
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(Math.min(PlayerUtil.getMaxHealth(player), 20.0d));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        return arena.getLobbySpawn();
    }

    @Override
    public void onPlayerLeaveArena(MiniGameArena arena, Player player) {
        captureOwnerState(arena, player);
    }

    @Override
    public void onPlayerQuitArena(MiniGameArena arena, Player player) {
        captureOwnerState(arena, player);
    }

    @Override
    public HandlerEventResult onEntityDamage(MiniGameArena arena, EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !arena.hasOccupant(player)) {
            return HandlerEventResult.ALLOW;
        }

        if (arena.hasSpectator(player)) {
            return HandlerEventResult.DENY;
        }

        if (event.getDamageSource().getDamageType() == DamageType.OUT_OF_WORLD
            || player.getHealth() - event.getFinalDamage() <= 0.0d) {
            skyBlock.endGame(arena, "Game over. Your island has been reset.");
            return HandlerEventResult.DENY;
        }

        return HandlerEventResult.ALLOW;
    }

    private void captureOwnerState(@NotNull MiniGameArena arena, @NotNull Player player) {
        if (arena.get("skipStateCapture", Boolean.class, false)) {
            return;
        }
        if (!skyBlock.isOwner(arena, player)) {
            return;
        }

        skyBlock.saveArenaState(arena, SkyBlockPlayerState.capture(player));
    }
}
