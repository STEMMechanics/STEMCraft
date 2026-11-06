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

/** A response stream for a long-lived server-sent event connection. */
public interface WebServiceStream {
    /** Sends one SSE event and flushes it to the connected browser. */
    void send(@NotNull String event, @NotNull String data) throws IOException;

    /** Returns false after the underlying client connection has failed. */
    boolean isOpen();
}
