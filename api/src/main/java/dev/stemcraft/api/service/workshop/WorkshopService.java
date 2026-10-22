/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.workshop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Public internal service for reading and managing STEMMechanics workshops.
 * Implementations own persistence and website synchronisation; callers only
 * depend on this interface.
 */
public interface WorkshopService {
    WorkshopService UNAVAILABLE = new WorkshopService() {
        @Override public boolean isAvailable() { return false; }
        @Override public boolean isEnabled() { return false; }
        @Override public @NotNull List<Workshop> upcoming() { return List.of(); }
        @Override public @Nullable Workshop nextUpcoming() { return null; }
        @Override public @Nullable Workshop find(@NotNull String id) { return null; }
        @Override public @NotNull List<Workshop> within(@NotNull Instant from, @NotNull Instant to) { return List.of(); }
        @Override public void refresh() { }
        @Override public @Nullable Workshop addManual(@NotNull Workshop workshop) { return null; }
        @Override public boolean updateManual(@NotNull Workshop workshop) { return false; }
        @Override public boolean removeManual(@NotNull String id) { return false; }
        @Override public @NotNull WorkshopSyncStatus syncStatus() {
            return new WorkshopSyncStatus(false, false, null, null, "Workshops is unavailable.", 0);
        }
    };

    /** Whether this service is currently provided by an enabled feature. */
    default boolean isAvailable() { return true; }

    /** Whether the feature is enabled in its current configuration. */
    boolean isEnabled();

    /** Return all non-expired workshops in chronological order. */
    @NotNull List<Workshop> upcoming();

    /** Return the first non-expired workshop, or {@code null} when none exist. */
    @Nullable Workshop nextUpcoming();

    /** Find a workshop by its administrative ID. */
    @Nullable Workshop find(@NotNull String id);

    /** Return workshops whose effective event window intersects the range. */
    @NotNull List<Workshop> within(@NotNull Instant from, @NotNull Instant to);

    /** Request an asynchronous website refresh. */
    void refresh();

    /** Add a manual record. The workshop source must be {@link WorkshopSource#MANUAL}. */
    @Nullable Workshop addManual(@NotNull Workshop workshop);

    /** Update an existing manual record. Website records are read-only. */
    boolean updateManual(@NotNull Workshop workshop);

    /** Remove an existing manual record. Website records are read-only. */
    boolean removeManual(@NotNull String id);

    /** Return the latest synchronisation state. */
    @NotNull WorkshopSyncStatus syncStatus();

    /** Convenience factory for manual records. */
    static Workshop manual(
            @NotNull String id,
            @NotNull String title,
            @NotNull String description,
            @NotNull ZonedDateTime startAt,
            @Nullable ZonedDateTime endAt,
            @NotNull String location,
            @NotNull String bookingUrl,
            boolean announcementEligible
    ) {
        return new Workshop(id, title, description, startAt, endAt, location, bookingUrl,
                WorkshopSource.MANUAL, Instant.now(), announcementEligible);
    }
}
