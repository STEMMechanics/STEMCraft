package dev.stemcraft.api.event.minigame;

import dev.stemcraft.api.event.BaseEvent;
import dev.stemcraft.api.minigame.MiniGameArena;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class ArenaStatusChangedEvent extends BaseEvent {

    private final MiniGameArena arena;

    public ArenaStatusChangedEvent(MiniGameArena arena) {
        this.arena = arena;
    }

}
