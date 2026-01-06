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

package dev.stemcraft.api.service.motd;

/**
 * Service for managing the Message of the Day (MOTD) displayed to players.
 */
public interface MotdService {

    public enum Priority {
        DEFAULT,        // normal server state
        INFO,           // informational notices
        EVENT,          // promotions, events
        WARNING,        // degraded service
        MAINTENANCE,    // planned downtime
        EMERGENCY       // outages, critical alerts
    }

    /**
     * Represents a resolved MOTD with title, text, and priority.
     */
    record ResolvedMotd(String motdTitle, String motdText, Priority priority) { }

    /**
     * Set the default MOTD used when no other MOTDs are set.
     *
     * @param title The title of the default MOTD.
     * @param text The text/body of the default MOTD.
     */
    void setDefault(String title, String text);

    /**
     * Get the current MOTD
     *
     * @return The highest priority MOTD currently set.
     */
    ResolvedMotd current();

    /**
     * Get the MOTD based on namespace ID.
     *
     * @param namespaceId The namespace ID of the MOTD.
     * @return The MOTD associated with the given namespace ID.
     */
    ResolvedMotd get(String namespaceId);

    /**
     * Push a new MOTD with given priority. Higher priority MOTDs override lower priority ones.
     *
     * @param namespaceId The namespace ID for the MOTD.
     * @param priority The priority of the MOTD.
     * @param motdTitle The title of the MOTD.
     * @param motdText The text/body of the MOTD.
     */
    void push(String namespaceId, Priority priority, String motdTitle, String motdText);

    /**
     * Remove the MOTD associated with the given namespace ID.
     *
     * @param namespaceId The namespace ID of the MOTD to remove.
     */
    void remove(String namespaceId);
}
