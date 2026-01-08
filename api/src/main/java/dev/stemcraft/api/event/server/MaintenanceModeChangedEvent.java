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

package dev.stemcraft.api.event.server;

import dev.stemcraft.api.event.BaseEvent;
import lombok.Getter;

/**
 * Event triggered when the server's maintenance mode status changes.
 */
public class MaintenanceModeChangedEvent extends BaseEvent {
    @Getter
    private final boolean inMaintenanceMode;

    /**
     * Constructs a new MaintenanceModeChangedEvent.
     *
     * @param inMaintenanceMode True if the server is now in maintenance mode, false otherwise.
     */
    public MaintenanceModeChangedEvent(boolean inMaintenanceMode) {
        this.inMaintenanceMode = inMaintenanceMode;
    }
}