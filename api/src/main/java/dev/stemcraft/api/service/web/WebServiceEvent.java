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
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transport-neutral event published through the web service.
 *
 * <p>The web service decides which configured subscriptions receive the event
 * and how it is serialized. Producers only need to provide the event name,
 * optional player identity, and event-specific data.</p>
 *
 * @param eventId unique id used by consumers for idempotency
 * @param occurredAt time at which the event was created
 * @param eventType stable event name, such as {@code minecraft.player_join}
 * @param playerUuid optional player UUID
 * @param playerName optional player name
 * @param data event-specific JSON-compatible data
 */
public record WebServiceEvent(
    @NotNull UUID eventId,
    @NotNull Instant occurredAt,
    @NotNull String eventType,
    @Nullable UUID playerUuid,
    @Nullable String playerName,
    @NotNull Map<String, Object> data
) {

    /**
     * Create a new event with a generated id and timestamp.
     *
     * @param eventType stable event name
     * @param playerUuid optional player UUID
     * @param playerName optional player name
     * @param data event-specific data
     */
    public WebServiceEvent(@NotNull String eventType,
                           @Nullable UUID playerUuid,
                           @Nullable String playerName,
                           @NotNull Map<String, Object> data) {
        this(UUID.randomUUID(), Instant.now(), eventType, playerUuid, playerName, data);
    }

    /** Canonical constructor with defensive validation and copying. */
    public WebServiceEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(data, "data");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        eventType = eventType.trim();
        data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
