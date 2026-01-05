package dev.stemcraft.api.event.world;

import dev.stemcraft.api.event.BaseEvent;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event called when a world is deleted.
 */
@Getter
public class WorldDeleteEvent extends BaseEvent {

    private final String worldName;

    public WorldDeleteEvent(String worldName) {
        this.worldName = worldName;
    }
}