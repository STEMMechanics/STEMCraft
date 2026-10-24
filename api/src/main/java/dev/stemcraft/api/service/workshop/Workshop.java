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
import java.time.ZonedDateTime;
import java.util.Objects;

/** Immutable workshop information exposed to other STEMCraft features. */
public record Workshop(
        String id,
        String title,
        String description,
        ZonedDateTime startAt,
        ZonedDateTime endAt,
        String location,
        String bookingUrl,
        WorkshopSource source,
        Instant updatedAt,
        boolean announcementEligible
) {
    public Workshop {
        id = requireText(id, "id");
        title = requireText(title, "title");
        description = description == null ? "" : description;
        startAt = Objects.requireNonNull(startAt, "startAt");
        if (endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("endAt must not be before startAt");
        }
        location = location == null ? "" : location;
        bookingUrl = bookingUrl == null ? "" : bookingUrl;
        source = Objects.requireNonNull(source, "source");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Returns whether the workshop remains current at the supplied instant.
     * A workshop without an end time expires at its start time.
     */
    public boolean isUpcoming(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant expiry = (endAt == null ? startAt : endAt).toInstant();
        return now.isBefore(expiry);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
