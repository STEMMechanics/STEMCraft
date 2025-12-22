package dev.stemcraft.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event called when a world is deleted.
 */
@Getter
public class WorldDeleteEvent extends Event {

    private static final HandlerList Handlers = new HandlerList();
    private final String worldName;

    public WorldDeleteEvent(String worldName) {
        this.worldName = worldName;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return Handlers;
    }

    public static HandlerList getHandlerList() {
        return Handlers;
    }
}