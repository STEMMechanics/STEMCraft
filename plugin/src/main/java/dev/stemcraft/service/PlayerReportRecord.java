package dev.stemcraft.service;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record PlayerReportRecord(
    long id,
    Instant occurredAt,
    UUID reporterUuid,
    String reporterName,
    String message,
    @Nullable String world,
    @Nullable Double x,
    @Nullable Double y,
    @Nullable Double z,
    @Nullable String onlineSnapshotJson,
    boolean alerted,
    boolean resolved,
    @Nullable Instant resolvedAt,
    @Nullable UUID resolvedByUuid,
    @Nullable String resolvedByName,
    @Nullable String resolutionNote
) {}
