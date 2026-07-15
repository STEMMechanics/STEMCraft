package dev.stemcraft.service;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AuditEventRecord(
    long id,
    Instant occurredAt,
    String category,
    String subtype,
    @Nullable UUID actorUuid,
    String actorName,
    @Nullable String content,
    @Nullable String world,
    @Nullable Double x,
    @Nullable Double y,
    @Nullable Double z,
    @Nullable String detailsJson
) {}
