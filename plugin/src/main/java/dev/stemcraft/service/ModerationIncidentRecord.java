package dev.stemcraft.service;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ModerationIncidentRecord(
    long id,
    Instant occurredAt,
    UUID playerUuid,
    String playerName,
    String messageType,
    String originalText,
    @Nullable String cleanedText,
    @Nullable String matchedWords,
    boolean blocked,
    String actionTaken,
    int strikeCount,
    @Nullable String reasonCode,
    @Nullable String reasonDetail,
    @Nullable String world,
    @Nullable Double x,
    @Nullable Double y,
    @Nullable Double z,
    @Nullable String contextJson,
    boolean resolved,
    @Nullable Instant resolvedAt,
    @Nullable UUID resolvedByUuid,
    @Nullable String resolvedByName,
    @Nullable String resolutionAction,
    @Nullable String resolutionNote
) {}
