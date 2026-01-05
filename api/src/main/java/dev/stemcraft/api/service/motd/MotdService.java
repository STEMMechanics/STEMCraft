/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.api.service.motd;

public interface MotdService {

    public enum Priority {
        DEFAULT,        // normal server state
        INFO,           // informational notices
        EVENT,          // promotions, events
        WARNING,        // degraded service
        MAINTENANCE,    // planned downtime
        EMERGENCY       // outages, critical alerts
    }

    record ResolvedMotd(String motdTitle, String motdText, Priority priority) { }

    /**
     * Set the default MOTD used when no other MOTDs are set.
     */
    void setDefault(String title, String text);

    /**
     * Get the current MOTD
     */
    ResolvedMotd current();

    /**
     * Get the MOTD based on namespace ID.
     */
    ResolvedMotd get(String namespaceId);

    /**
     * Push a new MOTD with given priority. Higher priority MOTDs override lower priority ones.
     */
    void push(String namespaceId, Priority priority, String motdTitle, String motdText);

    /**
     * Remove the MOTD associated with the given namespace ID.
     */
    void remove(String namespaceId);
}
