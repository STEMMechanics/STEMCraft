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

package dev.stemcraft.api.service.web;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Service for managing a webserver within the STEMCraft plugin.
 *
 * <p>The implementation also provides a public, read-only {@code GET /status}
 * endpoint. Its existing response fields are always preserved. When
 * {@code web-server.status.players.enabled} is enabled, the response adds a
 * {@code players} array containing current online Minecraft usernames only.</p>
 */
public interface WebService {

    /**
     * Start the webserver.
     */
    void start();

    /**
     * Stop the webserver.
     */
    void stop();

    /**
     * Register an endpoint handler which is called if the uri starts with the path string.
     *
     * @param path The path prefix to match.
     * @param handler The handler to call for matching requests.
     */
    void registerEndpointHandler(@NotNull String path, @NotNull WebServiceEndpointHandler handler);

    /**
     * Escape HTML special characters in a string.
     *
     * @param in The input string.
     * @return The escaped string.
     */
    static @NotNull String escapeHtml(@Nullable String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Get the public URL of the webserver.
     *
     * @return The public URL.
     */
    @NotNull String getPublicUrl();
}
