/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.workshop;

import java.time.Instant;

/** Snapshot of the website workshop synchronisation state. */
public record WorkshopSyncStatus(
        boolean enabled,
        boolean inProgress,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastError,
        int websiteWorkshopCount
) {
    public WorkshopSyncStatus {
        lastError = lastError == null ? "" : lastError;
    }
}
