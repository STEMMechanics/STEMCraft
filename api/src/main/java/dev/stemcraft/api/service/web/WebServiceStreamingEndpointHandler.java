/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.web;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/** Handles an optional long-lived streaming HTTP endpoint. */
@FunctionalInterface
public interface WebServiceStreamingEndpointHandler {
    /**
     * Handles the request. Return {@code false} without writing to the stream
     * to let ordinary endpoint handling process the request instead.
     */
    boolean handle(@NotNull WebServiceRequest request, @NotNull WebServiceStream stream) throws IOException;
}
