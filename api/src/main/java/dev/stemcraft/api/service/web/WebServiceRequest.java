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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable HTTP request data passed to web endpoint handlers.
 */
public record WebServiceRequest(
        @NotNull String method,
        @NotNull String uri,
        @NotNull String path,
        @NotNull Map<String, String> queryParams,
        @NotNull Map<String, List<String>> headers,
        byte @NotNull [] body,
        @NotNull String remoteAddress
) {
    public WebServiceRequest {
        queryParams = Map.copyOf(queryParams);

        Map<String, List<String>> headerCopy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            headerCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        headers = Map.copyOf(headerCopy);
        body = body.clone();
    }

    /**
     * Get a query parameter by name.
     *
     * @param name Parameter name.
     * @return The value, or null if absent.
     */
    public @Nullable String queryParam(@NotNull String name) {
        return queryParams.get(name);
    }

    /**
     * Get the first matching header value, case-insensitively.
     *
     * @param name Header name.
     * @return The first value, or null if absent.
     */
    public @Nullable String firstHeader(@NotNull String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(name) || entry.getValue().isEmpty()) {
                continue;
            }
            return entry.getValue().getFirst();
        }

        return null;
    }

    /**
     * Get the request body as UTF-8 text.
     *
     * @return The decoded request body.
     */
    public @NotNull String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
