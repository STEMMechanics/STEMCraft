package dev.stemcraft.api.event.minigame;

import dev.stemcraft.api.event.BaseEvent;
import dev.stemcraft.api.minigame.MiniGameArena;
import lombok.Getter;
import org.bukkit.entity.Player;

@Getter
public class ArenaPlayerJoinEvent extends BaseEvent {

    private final MiniGameArena arena;
    private final Player player;

    public ArenaPlayerJoinEvent(Player player, MiniGameArena arena) {
        this.arena = arena;
        this.player = player;
    }
}
