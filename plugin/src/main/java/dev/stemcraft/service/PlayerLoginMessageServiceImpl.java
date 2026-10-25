/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Coordinates private messages generated during a player's login.
 *
 * <p>Features can register a message by category. The first queued message in
 * each category wins according to priority, which lets a configured welcome
 * message replace the default announcement while still keeping the ordering
 * of welcome, away, and other login notices deterministic.</p>
 */
public final class PlayerLoginMessageServiceImpl extends BaseService {
    private static final long MINIMUM_FLUSH_DELAY_TICKS = 21L;
    private static final String TASK_PREFIX = "service:player-login-messages:";

    private final Map<UUID, LoginSession> sessions = new ConcurrentHashMap<>();
    private boolean enabled;
    private long flushDelayTicks;

    public PlayerLoginMessageServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "player-login-messages");
    }

    /** Message priority within a login session. Lower values are delivered first. */
    public enum Priority {
        WELCOME_CUSTOM(0),
        WELCOME(10),
        AWAY_SUMMARY(20),
        OTHER(30);

        private final int order;

        Priority(int order) {
            this.order = order;
        }
    }

    @Override
    public void onEnable() {
        reloadSettings();

        api.events().register(PlayerJoinEvent.class, event -> open(event.getPlayer()));
        api.events().register(PlayerQuitEvent.class, event -> close(event.getPlayer().getUniqueId()));
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
        if (!enabled) {
            cancelAll();
            return;
        }

        for (Map.Entry<UUID, LoginSession> entry : sessions.entrySet()) {
            schedule(entry.getKey(), entry.getValue(), flushDelayTicks);
        }
    }

    @Override
    public void onDisable() {
        cancelAll();
        sessions.clear();
    }

    /**
     * Opens a login session using this service's configured delay.
     *
     * @return the effective delay in ticks, or {@code 0} when disabled
     */
    public long open(@NotNull Player player) {
        return open(player, flushDelayTicks);
    }

    /**
     * Opens or updates a login session.
     *
     * @param player the joining player
     * @param requestedDelayTicks delay before delivery
     * @return the effective delay in ticks
     */
    public long open(@NotNull Player player, long requestedDelayTicks) {
        long requested = Math.max(0L, requestedDelayTicks);
        if (!enabled) {
            return requested;
        }

        UUID playerId = player.getUniqueId();
        LoginSession session = sessions.computeIfAbsent(playerId, ignored -> new LoginSession());
        session.delayTicks = effectiveDelay(requested);
        schedule(playerId, session, session.delayTicks);
        return session.delayTicks;
    }

    /**
     * Returns the current effective delay for a player, opening a default
     * session when the join event was not observed by this service.
     */
    public long delayTicks(@NotNull Player player) {
        if (!enabled) {
            return 0L;
        }
        LoginSession session = sessions.get(player.getUniqueId());
        return session == null ? open(player) : session.delayTicks;
    }

    /**
     * Queues one message category for delivery after the login session opens.
     * A later message in the same category replaces an earlier one only when
     * it has a higher priority (or the same priority).
     */
    public void queue(@NotNull Player player, @NotNull Priority priority,
                      @NotNull String category, @NotNull Consumer<Player> action) {
        if (!enabled) {
            if (player.isOnline()) {
                action.accept(player);
            }
            return;
        }

        UUID playerId = player.getUniqueId();
        LoginSession session = sessions.computeIfAbsent(playerId, ignored -> new LoginSession());
        long sequence = session.nextSequence++;
        QueuedMessage previous = session.messages.get(category);
        if (previous == null || priority.order <= previous.priority.order) {
            session.messages.put(category, new QueuedMessage(priority, sequence, action));
        }
        if (!api.tasks().exists(taskId(playerId))) {
            schedule(playerId, session, session.delayTicks > 0L ? session.delayTicks : flushDelayTicks);
        }
    }

    private void reloadSettings() {
        enabled = getConfigSection().getBoolean("enabled", true);
        long delaySeconds;
        try {
            delaySeconds = TimeUtil.parseDuration(getConfigSection().getString("flush-delay", "5s"));
        } catch (IllegalArgumentException ignored) {
            delaySeconds = 5L;
        }
        flushDelayTicks = effectiveDelay(Math.clamp(delaySeconds * 20L, 0L, 3600L * 20L));
    }

    private long effectiveDelay(long requestedDelayTicks) {
        return Math.max(MINIMUM_FLUSH_DELAY_TICKS, requestedDelayTicks);
    }

    private void schedule(UUID playerId, LoginSession session, long delayTicks) {
        api.tasks().runOnceDelay(taskId(playerId), Math.max(MINIMUM_FLUSH_DELAY_TICKS, delayTicks),
            () -> flush(playerId));
    }

    private void flush(UUID playerId) {
        LoginSession session = sessions.remove(playerId);
        if (session == null) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        List<QueuedMessage> messages = new ArrayList<>(session.messages.values());
        messages.sort(Comparator
            .comparingInt((QueuedMessage message) -> message.priority.order)
            .thenComparingLong(QueuedMessage::sequence));
        for (QueuedMessage message : messages) {
            try {
                message.action.accept(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not deliver a login message to " + player.getName() + ": "
                    + exception.getMessage());
            }
        }
    }

    private void close(UUID playerId) {
        sessions.remove(playerId);
        api.tasks().cancel(taskId(playerId));
    }

    private void cancelAll() {
        for (UUID playerId : sessions.keySet()) {
            api.tasks().cancel(taskId(playerId));
        }
        sessions.clear();
    }

    private String taskId(UUID playerId) {
        return TASK_PREFIX + playerId;
    }

    static List<String> orderedCategories(@NotNull Map<String, Priority> categories) {
        Map<String, Integer> sequence = new LinkedHashMap<>();
        int next = 0;
        for (String category : categories.keySet()) {
            sequence.put(category, next++);
        }
        return categories.entrySet().stream()
            .sorted(Comparator
                .comparingInt((Map.Entry<String, Priority> entry) -> entry.getValue().order)
                .thenComparingInt(entry -> sequence.get(entry.getKey())))
            .map(Map.Entry::getKey)
            .toList();
    }

    private static final class LoginSession {
        private final Map<String, QueuedMessage> messages = new LinkedHashMap<>();
        private long nextSequence;
        private long delayTicks;
    }

    private record QueuedMessage(Priority priority, long sequence, Consumer<Player> action) {
    }
}
