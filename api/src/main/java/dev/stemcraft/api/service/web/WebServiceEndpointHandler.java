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

import java.util.Map;

/**
 * Functional interface for handling web service endpoints.
 */
public interface WebServiceEndpointHandler {

    /**
     * Web endpoint handler.

     * Supported handler return types:
     * - null
     *   sends an empty body with HTTP 200 and Content-Type: text/plain; charset=utf-8}.

     * - Any non-Map object
     *   toString() is used as the response body with HTTP 200 and
     *   Content-Type: text/plain; charset=utf-8}.

     * - Map<?, ?>
     * Allows full control of the response. The following keys are recognised:
     *   - responseCode: Integer; HTTP status code. Optional, defaults to 200.
     *   - contentType: String; sets the Content-Type header. Optional, defaults to
     *     "text/plain; charset=utf-8"}.
     *   - file: java.io.File; if present, this file is streamed as the response body.
     *     In this case "body" is ignored.
     *   - body: any object; converted to String and used as the response body when
     *     "file" is not provided.
     *
     * @param method The HTTP method (e.g., GET, POST).
     * @param uri The request URI.
     * @param queryParams The query parameters as a map.
     * @return The response object as described above.
     */
    Object handle(String method, String uri, Map<String, String> queryParams);
}