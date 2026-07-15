package dev.stemcraft.service.firstjoin;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class FirstJoinSession {
    private final UUID playerId;
    private int expectedAnswer;
    private int attemptsRemaining;
    private long expiresAt;
    private final Location initialLocation;
    private String prompt;

    public FirstJoinSession(@NotNull UUID playerId,
                               int expectedAnswer,
                               int attemptsRemaining,
                               long expiresAt,
                               @NotNull Location initialLocation,
                               @NotNull String prompt) {
        this.playerId = playerId;
        this.expectedAnswer = expectedAnswer;
        this.attemptsRemaining = attemptsRemaining;
        this.expiresAt = expiresAt;
        this.initialLocation = initialLocation.clone();
        this.prompt = prompt;
    }

    public @NotNull UUID playerId() {
        return playerId;
    }

    public int expectedAnswer() {
        return expectedAnswer;
    }

    public int attemptsRemaining() {
        return attemptsRemaining;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public @NotNull Location initialLocation() {
        return initialLocation.clone();
    }

    public @NotNull String prompt() {
        return prompt;
    }

    void setQuestion(@NotNull FirstJoinQuestion question) {
        this.expectedAnswer = question.answer();
        this.prompt = question.prompt();
    }

    void updateInitialLocation(@NotNull Location location) {
        this.initialLocation.setWorld(location.getWorld());
        this.initialLocation.setX(location.getX());
        this.initialLocation.setY(location.getY());
        this.initialLocation.setZ(location.getZ());
        this.initialLocation.setYaw(location.getYaw());
        this.initialLocation.setPitch(location.getPitch());
    }

    void decrementAttempts() {
        this.attemptsRemaining = Math.max(0, this.attemptsRemaining - 1);
    }

    boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAt;
    }

    void extendExpiry(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
