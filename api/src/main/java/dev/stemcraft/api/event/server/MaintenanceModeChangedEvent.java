package dev.stemcraft.api.event.server;

import dev.stemcraft.api.event.BaseEvent;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class MaintenanceModeChangedEvent extends BaseEvent {
    @Getter
    private final boolean inMaintenanceMode;

    public MaintenanceModeChangedEvent(boolean inMaintenanceMode) {
        this.inMaintenanceMode = inMaintenanceMode;
    }
}
