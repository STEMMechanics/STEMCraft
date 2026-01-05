package dev.stemcraft.api.event.minigame;

import dev.stemcraft.api.event.BaseEvent;
import dev.stemcraft.api.minigame.MiniGameArena;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class ArenaPlayerLobbyMenuItemEvent extends BaseEvent {

    private final MiniGameArena arena;
    private final Player player;

    public ArenaPlayerLobbyMenuItemEvent(Player player, MiniGameArena arena) {
        this.arena = arena;
        this.player = player;
    }
}
