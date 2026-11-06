/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.config;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** The result of a conflict-aware configuration save attempt. */
public final class ConfigSaveResult {
    private final boolean success;
    private final boolean changed;
    private final Set<String> conflicts;
    private final String currentDiskRevision;
    private final String message;

    public ConfigSaveResult(boolean success,
                            boolean changed,
                            @NotNull Set<String> conflicts,
                            @NotNull String currentDiskRevision,
                            @NotNull String message) {
        this.success = success;
        this.changed = changed;
        this.conflicts = Collections.unmodifiableSet(new LinkedHashSet<>(conflicts));
        this.currentDiskRevision = currentDiskRevision == null ? "" : currentDiskRevision;
        this.message = message == null ? "" : message;
    }

    public boolean success() { return success; }
    public boolean successful() { return success; }
    public boolean changed() { return changed; }
    public boolean hasConflicts() { return !conflicts.isEmpty(); }
    public @NotNull Set<String> conflicts() { return conflicts; }
    public @NotNull Set<String> conflictingPaths() { return conflicts; }
    public @NotNull String currentDiskRevision() { return currentDiskRevision; }
    public @NotNull String message() { return message; }
    public @NotNull String errorMessage() { return success ? "" : message; }

    public static ConfigSaveResult success(boolean changed, String revision, String message) {
        return new ConfigSaveResult(true, changed, Set.of(), revision, message);
    }

    public static ConfigSaveResult failure(String revision, String message) {
        return new ConfigSaveResult(false, false, Set.of(), revision, message);
    }

    public static ConfigSaveResult conflict(String revision, Set<String> paths, String message) {
        return new ConfigSaveResult(false, false, paths, revision, message);
    }
}
