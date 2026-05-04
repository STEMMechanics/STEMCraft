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

package dev.stemcraft.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.punishment.PunishmentRecord;
import dev.stemcraft.api.service.web.WebServiceRequest;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TimeUtil;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Integrates STEMCraft with the website webhook flow without requiring a second plugin jar.
 */
public final class WebhookBridgeServiceImpl extends BaseService {
    private static final Gson GSON = new Gson();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final long[] STARTUP_SYNC_BACKOFF_SECONDS = new long[]{0L, 5L, 15L, 30L, 30L, 30L, 60L, 60L, 60L};
    private static final long STARTUP_SYNC_REPEAT_SECONDS = 300L;
    private static final int HISTORY_LIMIT = 10;
    private static final String ACCOUNT_STATE_FILE = "webhook-accounts.yml";
    private static final String PLATFORM_JAVA = "java";
    private static final String PLATFORM_BEDROCK = "bedrock";

    private final ConcurrentMap<String, AccountRecord> accountsByUsername = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AccountRecord> accountsByUuid = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BlacklistRecord> blacklistByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PenaltyStateRecord> penaltiesByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeSessionsByUuid = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> recentInboundDeliveries = new ConcurrentHashMap<>();
    private final List<QueuedInboundEvent> pendingSyncManagedEvents = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final AtomicBoolean syncRetryScheduled = new AtomicBoolean(false);
    private final AtomicBoolean initialSyncComplete = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean endpointRegistered = new AtomicBoolean(false);
    private final AtomicInteger syncAttemptCounter = new AtomicInteger(0);
    private final Deque<WebhookHistoryEntry> recentOutboundHistory = new ArrayDeque<>();
    private final Deque<WebhookHistoryEntry> recentInboundHistory = new ArrayDeque<>();
    private final Object historyLock = new Object();

    private ConfigFile accountStateConfig;
    private HttpClient httpClient;
    private volatile Instant syncStateCutoff;
    private volatile Instant lastSyncAt;
    private volatile String lastSyncReason = "never";
    private volatile SyncResult lastSyncResult = SyncResult.failure("Never run.");

    public WebhookBridgeServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        setConfigKey("webhook_bridge");
    }

    @Override
    public void onEnable() {
        configureDefaults();

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config().getLong("connect_timeout_millis", 5000L)))
            .build();

        File accountStateFile = new File(plugin.getDataFolder(), ACCOUNT_STATE_FILE);
        this.accountStateConfig = accountStateFile.exists() ? api.config().load(accountStateFile, false) : null;
        ensureStateStorage();
        migrateLegacyStateIfNeeded();
        loadAccountsFromConfig();
        loadBlacklistFromConfig();
        loadPenaltiesFromConfig();
        restoreLocalAccessState();

        registerEvents();
        registerCommands();
        registerPunishmentObservers();

        if (!isBridgeEnabled()) {
            return;
        }

        activateBridge("startup");
    }

    @Override
    public void onDisable() {
        if (isBridgeEnabled()) {
            shuttingDown.set(true);
            sendShutdownLifecycleEvents();
        }
        saveAccountState();
        saveBlacklistState();
        savePenaltyState();
    }

    private void registerEvents() {
        api.events().register(AsyncPlayerPreLoginEvent.class, this::onAsyncPlayerPreLogin, EventPriority.HIGHEST, false);
        registerLegacyPlayerLoginHandler();
        api.events().register(PlayerJoinEvent.class, this::onPlayerJoin);
        api.events().register(PlayerQuitEvent.class, this::onPlayerQuit);
        api.events().register(AsyncChatEvent.class, this::onAsyncChat);
    }

    @SuppressWarnings("deprecation")
    private void registerLegacyPlayerLoginHandler() {
        api.events().register(PlayerLoginEvent.class, this::onPlayerLogin, EventPriority.HIGHEST, false);
    }

    private void registerCommands() {
        api.commands().create("webhook")
            .description("WEBHOOK_COMMAND_DESCRIPTION")
            .usage("WEBHOOK_COMMAND_USAGE")
            .permission("stemcraft.command.webhook")
            .tabCompletion("status")
            .tabCompletion("info")
            .tabCompletion("enable")
            .tabCompletion("disable")
            .tabCompletion("sync")
            .tabCompletion("syncstats")
            .tabCompletion("history")
            .tabCompletion("history", "send")
            .tabCompletion("history", "receive")
            .tabCompletion("history", "all")
            .executor((unused, cmd, ctx) -> {
                String action = Objects.requireNonNullElse(ctx.getArgLower(0), "status");
                switch (action) {
                    case "status" -> sendWebhookStatus(ctx, false);
                    case "info" -> sendWebhookStatus(ctx, true);
                    case "history" -> sendWebhookHistory(ctx);
                    case "enable" -> enableBridgeCommand(ctx);
                    case "disable" -> disableBridgeCommand(ctx);
                    case "sync" -> runManualSyncCommand(ctx);
                    case "syncstats" -> runManualStatsSyncCommand(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(plugin);

        api.commands().create("webhooksync")
            .description("WEBHOOK_SYNC_COMMAND_DESCRIPTION")
            .usage("WEBHOOK_SYNC_COMMAND_USAGE")
            .permission("stemcraft.command.webhooksync")
            .executor((unused, cmd, ctx) -> runManualSyncCommand(ctx))
            .register(plugin);
    }

    private void registerPunishmentObservers() {
        plugin.punishments().registerObserver(this::reportPunishmentCreated);
    }

    private void runManualSyncCommand(dev.stemcraft.api.command.CommandContext ctx) {
        if (!isBridgeEnabled()) {
            ctx.returnError("WEBHOOK_BRIDGE_DISABLED");
        }
        if (syncInProgress.get()) {
            ctx.returnError("WEBHOOK_SYNC_ALREADY_RUNNING");
        }

        ctx.info("WEBHOOK_SYNC_STARTING");
        runSyncRequest(result -> {
            if (result.ok) {
                ctx.success("WEBHOOK_SYNC_COMPLETE");
            } else {
                ctx.error("WEBHOOK_SYNC_FAILED", "reason", result.errorSummary);
            }
        });
    }

    private void runManualStatsSyncCommand(dev.stemcraft.api.command.CommandContext ctx) {
        if (!isBridgeEnabled()) {
            ctx.returnError("WEBHOOK_BRIDGE_DISABLED");
        }
        if (syncInProgress.get()) {
            ctx.returnError("WEBHOOK_SYNC_ALREADY_RUNNING");
        }

        ctx.info("WEBHOOK_STATS_SYNC_STARTING");
        runPlayerStatsSyncRequest(result -> {
            if (result.ok) {
                ctx.success("WEBHOOK_STATS_SYNC_COMPLETE");
            } else {
                ctx.error("WEBHOOK_STATS_SYNC_FAILED", "reason", result.errorSummary);
            }
        });
    }

    private void enableBridgeCommand(dev.stemcraft.api.command.CommandContext ctx) {
        if (isBridgeEnabled()) {
            ctx.returnInfo("WEBHOOK_BRIDGE_ALREADY_ENABLED");
        }

        config().set("enabled", true);
        saveConfig();
        shuttingDown.set(false);
        initialSyncComplete.set(false);
        syncAttemptCounter.set(0);
        activateBridge("manual-enable");

        if (getSharedSecret().isBlank()) {
            ctx.returnWarn("WEBHOOK_BRIDGE_ENABLED_NO_SECRET");
        }
        ctx.returnSuccess("WEBHOOK_BRIDGE_ENABLED");
    }

    private void disableBridgeCommand(dev.stemcraft.api.command.CommandContext ctx) {
        if (!isBridgeEnabled()) {
            ctx.returnInfo("WEBHOOK_BRIDGE_ALREADY_DISABLED");
        }

        config().set("enabled", false);
        saveConfig();
        initialSyncComplete.set(false);
        syncAttemptCounter.set(0);

        ctx.returnSuccess("WEBHOOK_BRIDGE_DISABLED_RUNTIME");
    }

    private void sendWebhookStatus(dev.stemcraft.api.command.CommandContext ctx, boolean includeHistory) {
        ctx.info("WEBHOOK_STATUS_HEADER");
        ctx.info("WEBHOOK_STATUS_ENABLED", "value", yesNo(isBridgeEnabled()));
        ctx.info("WEBHOOK_STATUS_WEB_SERVER", "value", yesNo(plugin.web() != null && plugin.web().isRunning()));
        ctx.info("WEBHOOK_STATUS_ENDPOINT", "value", yesNo(endpointRegistered.get()));
        ctx.info("WEBHOOK_STATUS_LISTEN_PATH", "value", config().getString("listen_path", "/stemcraft/webhook"));
        ctx.info("WEBHOOK_STATUS_PUBLIC_URL", "value", buildBridgeUrl());
        String siteWebhookUrl = config().getString("site_webhook_url", "").trim();
        ctx.info("WEBHOOK_STATUS_SITE_URL", "value", siteWebhookUrl.isBlank() ? "<unset>" : siteWebhookUrl);
        ctx.info("WEBHOOK_STATUS_SHARED_SECRET", "value", yesNo(!getSharedSecret().isBlank()));
        ctx.info("WEBHOOK_STATUS_WHITELIST", "value", yesNo(isWhitelistEnforcementActive()));
        ctx.info("WEBHOOK_STATUS_INITIAL_SYNC", "value", yesNo(initialSyncComplete.get()));
        ctx.info("WEBHOOK_STATUS_SYNC_IN_PROGRESS", "value", yesNo(syncInProgress.get()));
        ctx.info("WEBHOOK_STATUS_PENDING_EVENTS", "value", pendingQueuedEvents());
        ctx.info("WEBHOOK_STATUS_ACCOUNTS", "value", allAccountRecords().size());
        ctx.info("WEBHOOK_STATUS_PENALTIES", "value", penaltiesByKey.size());
        ctx.info("WEBHOOK_STATUS_BLACKLIST", "value", blacklistByKey.size());
        ctx.info("WEBHOOK_STATUS_LAST_SYNC", "value", describeLastSync());

        if (includeHistory) {
            sendHistorySection(ctx, "WEBHOOK_HISTORY_LAST_OUTBOUND", recentHistory(recentOutboundHistory, 3));
            sendHistorySection(ctx, "WEBHOOK_HISTORY_LAST_INBOUND", recentHistory(recentInboundHistory, 3));
        }
    }

    private void sendWebhookHistory(dev.stemcraft.api.command.CommandContext ctx) {
        String scope = Objects.requireNonNullElse(ctx.getArgLower(1), "all");
        int count = Math.clamp(ctx.getArgAsInt(2, 3, 1, 10), 1, 10);

        switch (scope) {
            case "send", "out", "outbound" -> sendHistorySection(ctx, "WEBHOOK_HISTORY_OUTBOUND", recentHistory(recentOutboundHistory, count));
            case "receive", "in", "inbound" -> sendHistorySection(ctx, "WEBHOOK_HISTORY_INBOUND", recentHistory(recentInboundHistory, count));
            case "all" -> {
                sendHistorySection(ctx, "WEBHOOK_HISTORY_OUTBOUND", recentHistory(recentOutboundHistory, count));
                sendHistorySection(ctx, "WEBHOOK_HISTORY_INBOUND", recentHistory(recentInboundHistory, count));
            }
            default -> ctx.returnError("WEBHOOK_HISTORY_SCOPE_INVALID", "scope", scope);
        }
    }

    private void sendHistorySection(dev.stemcraft.api.command.CommandContext ctx, String titleKey, List<WebhookHistoryEntry> history) {
        ctx.info(titleKey);
        if (history.isEmpty()) {
            ctx.info("WEBHOOK_HISTORY_EMPTY");
            return;
        }
        for (WebhookHistoryEntry entry : history) {
            sendHistoryEntry(ctx, entry);
        }
    }

    private void sendHistoryEntry(dev.stemcraft.api.command.CommandContext ctx, WebhookHistoryEntry entry) {
        String status = entry.status() > 0 ? String.valueOf(entry.status()) : "-";
        if (entry.detail() != null && !entry.detail().isBlank()) {
            ctx.info("WEBHOOK_HISTORY_ENTRY_DETAIL",
                "timestamp", entry.occurredAt(),
                "event", entry.event(),
                "result", entry.result(),
                "status", status,
                "delivery", entry.deliveryId(),
                "target", entry.target(),
                "detail", entry.detail());
            return;
        }

        ctx.info("WEBHOOK_HISTORY_ENTRY",
            "timestamp", entry.occurredAt(),
            "event", entry.event(),
            "result", entry.result(),
            "status", status,
            "delivery", entry.deliveryId(),
            "target", entry.target());
    }

    private void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!isBridgeEnabled()) {
            return;
        }

        String loginPlatform = platformForLogin(event.getUniqueId(), event.getName());

        PenaltyStateRecord activeBan = findActivePenalty(event.getUniqueId(), event.getName(), "ban");
        if (activeBan != null) {
            disallowAsyncLogin(event, AsyncPlayerPreLoginEvent.Result.KICK_BANNED, messageForPenalty(activeBan, "You are banned from this server."));
            return;
        }

        BlacklistRecord blacklist = findBlacklist(event.getUniqueId(), event.getName());
        if (blacklist != null && blacklist.isActive()) {
            disallowAsyncLogin(event, AsyncPlayerPreLoginEvent.Result.KICK_BANNED, messageForBlacklist(event.getUniqueId(), event.getName()));
            return;
        }

        if (!isWhitelistedByAccountState(event.getUniqueId(), event.getName(), loginPlatform)) {
            disallowAsyncLogin(event, AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, getWhitelistKickMessage());
            return;
        }

        // Ensure webhook whitelist authority can clear prior whitelist disallow state
        // (e.g. vanilla/bukkit whitelist or other early checks), while bans/blacklist
        // above still return before this point.
        event.allow();
    }

    private void onPlayerJoin(PlayerJoinEvent event) {
        if (!isBridgeEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        PenaltyStateRecord activeBan = findActivePenalty(player.getUniqueId(), player.getName(), "ban");
        if (activeBan != null) {
            api.tasks().nextTick(() -> kickPlayer(player, messageForPenalty(activeBan, "You are banned from this server.")));
            return;
        }

        if (isBlacklisted(player.getUniqueId(), player.getName())) {
            api.tasks().nextTick(() -> kickPlayer(player, messageForBlacklist(player.getUniqueId(), player.getName())));
            return;
        }

        String loginPlatform = platformForLogin(player.getUniqueId(), player.getName());
        if (isDeniedByAccountStateStrict(player.getUniqueId(), player.getName(), loginPlatform)) {
            api.tasks().nextTick(() -> kickPlayer(player, getWhitelistKickMessage()));
            return;
        }

        syncObservedPlayer(player);
        sendPlayerLogin(player);
    }

    @SuppressWarnings("deprecation")
    private void onPlayerLogin(PlayerLoginEvent event) {
        if (!isBridgeEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        String loginPlatform = platformForLogin(player.getUniqueId(), player.getName());

        PenaltyStateRecord activeBan = findActivePenalty(player.getUniqueId(), player.getName(), "ban");
        if (activeBan != null) {
            disallowPlayerLogin(event, PlayerLoginEvent.Result.KICK_BANNED, messageForPenalty(activeBan, "You are banned from this server."));
            return;
        }

        BlacklistRecord blacklist = findBlacklist(player.getUniqueId(), player.getName());
        if (blacklist != null && blacklist.isActive()) {
            disallowPlayerLogin(event, PlayerLoginEvent.Result.KICK_BANNED, messageForBlacklist(player.getUniqueId(), player.getName()));
            return;
        }

        if (isDeniedByAccountStateStrict(player.getUniqueId(), player.getName(), loginPlatform)) {
            disallowPlayerLogin(event, PlayerLoginEvent.Result.KICK_WHITELIST, getWhitelistKickMessage());
            return;
        }

        event.allow();
    }

    private boolean isDeniedByAccountStateStrict(UUID uuid, String username, String platform) {
        if (!config().getBoolean("enforce_account_whitelist", true)) {
            return !isWhitelistedByAccountState(uuid, username, platform);
        }

        String normalizedPlatform = normalizePlatform(platform);
        String normalizedUuid = uuid == null ? null : normalize(uuid);
        String normalizedUsername = webhookInboundUsername(username, normalizedPlatform);

        Boolean byPrimary = queryAccountWhitelist(normalizedUuid, normalizedUsername, normalizedPlatform);
        if (byPrimary != null) {
            return !byPrimary;
        }

        return true;
    }

    private @Nullable Boolean queryAccountWhitelist(@Nullable String uuid, @Nullable String username, String platform) {
        if (uuid != null && !uuid.isBlank()) {
            Integer byUuid = api.database().querySingleMapped(
                "SELECT is_whitelisted FROM webhook_accounts WHERE lower(platform)=lower(?) AND lower(uuid)=lower(?) " +
                    "ORDER BY COALESCE(occurred_at, '') DESC LIMIT 1",
                ps -> {
                    ps.setString(1, platform);
                    ps.setString(2, uuid);
                },
                rs -> rs.getInt(1)
            );
            if (byUuid != null) {
                return byUuid == 1;
            }
        }

        if (username != null && !username.isBlank()) {
            Integer byUsername = api.database().querySingleMapped(
                "SELECT is_whitelisted FROM webhook_accounts WHERE lower(platform)=lower(?) AND lower(username)=lower(?) " +
                    "ORDER BY COALESCE(occurred_at, '') DESC LIMIT 1",
                ps -> {
                    ps.setString(1, platform);
                    ps.setString(2, username);
                },
                rs -> rs.getInt(1)
            );
            if (byUsername != null) {
                return byUsername == 1;
            }
        }

        return null;
    }

    private void onPlayerQuit(PlayerQuitEvent event) {
        if (!isBridgeEnabled()) {
            return;
        }
        if (shuttingDown.get()) {
            return;
        }

        sendPlayerLogout(event.getPlayer());
    }

    private void onAsyncChat(AsyncChatEvent event) {
        if (!isBridgeEnabled()) {
            return;
        }

        PenaltyStateRecord activeMute = findActivePenalty(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "mute");
        if (activeMute == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendPlainMessage(messageForPenalty(activeMute, "You are muted and cannot chat right now."));
    }

    private void reportPunishmentCreated(PunishmentRecord record) {
        if (!isBridgeEnabled()) {
            return;
        }
        if (record.durationSeconds() != null && record.durationSeconds() == -1L) {
            reportPunishmentLifted(record);
            return;
        }

        rememberLocalPenalty(record);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "player.penalty.created");
        payload.put("uuid", normalize(record.targetUuid()));
        payload.put("username", record.targetName());
        payload.put("type", record.type());
        payload.put("reason", record.reason());
        payload.put("started_at", record.createdAt().toString());
        payload.put("occurred_at", record.createdAt().toString());
        payload.put("updated_at", record.createdAt().toString());
        payload.put("is_permanent", record.permanent());
        if (record.durationSeconds() != null && record.durationSeconds() > 0L) {
            payload.put("duration_seconds", record.durationSeconds());
        }
        if (record.actorUuid() != null) {
            payload.put("by_uuid", normalize(record.actorUuid()));
        }
        if (record.actorName() != null && !record.actorName().isBlank()) {
            payload.put("by_username", record.actorName());
        }

        sendWebhook(payload);
    }

    private void reportPunishmentLifted(PunishmentRecord record) {
        Instant occurredAt = record.createdAt();
        List<PenaltyStateRecord> penaltiesToLift = findActivePenalties(record.targetUuid(), record.targetName(), record.type());
        if (penaltiesToLift.isEmpty()) {
            PunishmentRecord priorRecord = plugin.punishments().findLatestPriorPunishment(record.targetUuid(), record.type(), occurredAt);
            if (priorRecord != null) {
                penaltiesToLift = List.of(penaltyStateRecord(null, priorRecord));
            }
        }

        if (penaltiesToLift.isEmpty()) {
            sendWebhook(buildPenaltyLiftPayload(record, null, occurredAt));
            return;
        }

        boolean stateChanged = false;
        for (PenaltyStateRecord existing : penaltiesToLift) {
            PenaltyStateRecord liftedRecord = new PenaltyStateRecord(
                existing.externalId,
                existing.username,
                existing.uuid,
                existing.type,
                existing.reason,
                existing.createdAt,
                existing.endsAt,
                existing.permanent,
                occurredAt
            );
            if (liftedRecord.key() != null) {
                penaltiesByKey.put(liftedRecord.key(), liftedRecord);
                stateChanged = true;
            }

            sendWebhook(buildPenaltyLiftPayload(record, existing, occurredAt));
        }

        if (stateChanged) {
            savePenaltyState();
        }
    }

    private Map<String, Object> buildPenaltyLiftPayload(@NotNull PunishmentRecord record,
                                                        @Nullable PenaltyStateRecord existing,
                                                        @NotNull Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "player.penalty.updated");
        payload.put("uuid", normalize(record.targetUuid()));
        payload.put("username", existing != null && existing.username != null ? existing.username : record.targetName());
        payload.put("type", record.type());
        if (existing != null && existing.createdAt != null) {
            payload.put("started_at", existing.createdAt.toString());
        }
        payload.put("occurred_at", occurredAt.toString());
        payload.put("updated_at", occurredAt.toString());
        payload.put("lifted_at", occurredAt.toString());
        if (existing != null && existing.externalId != null && !existing.externalId.isBlank()) {
            payload.put("penalty_key", existing.externalId);
        }
        if (existing != null && existing.reason != null && !existing.reason.isBlank()) {
            payload.put("reason", existing.reason);
        }
        if (existing != null) {
            payload.put("is_permanent", existing.permanent);
            Long durationSeconds = durationSeconds(existing);
            if (durationSeconds != null && durationSeconds > 0L) {
                payload.put("duration_seconds", durationSeconds);
            }
        }
        if (record.actorUuid() != null) {
            payload.put("lifted_by_uuid", normalize(record.actorUuid()));
        }
        if (record.actorName() != null && !record.actorName().isBlank()) {
            payload.put("lifted_by_username", record.actorName());
        }
        if (record.reason() != null && !record.reason().isBlank()) {
            payload.put("lift_reason", record.reason());
        }
        return payload;
    }

    private void rememberLocalPenalty(@NotNull PunishmentRecord record) {
        PenaltyStateRecord localState = penaltyStateRecord(findPenalty(record.targetUuid(), record.targetName(), record.type()), record);
        String key = localState.key();
        if (key == null) {
            return;
        }

        penaltiesByKey.put(key, localState);
        savePenaltyState();
    }

    private @NotNull PenaltyStateRecord penaltyStateRecord(@Nullable PenaltyStateRecord existing,
                                                           @NotNull PunishmentRecord record) {
        Instant endsAt = record.permanent() ? null : record.expiresAt();
        return new PenaltyStateRecord(
            existing == null ? null : existing.externalId,
            record.targetName(),
            normalize(record.targetUuid()),
            record.type(),
            record.reason(),
            record.createdAt(),
            endsAt,
            record.permanent(),
            null
        );
    }

    private static @Nullable Long durationSeconds(@NotNull PenaltyStateRecord record) {
        if (record.permanent || record.createdAt == null || record.endsAt == null) {
            return null;
        }

        long seconds = Duration.between(record.createdAt, record.endsAt).getSeconds();
        return Math.max(seconds, 0L);
    }

    private void configureDefaults() {
        ConfigSection cfg = config();
        boolean changed = false;

        changed |= setDefault(cfg, "enabled", false);
        changed |= setDefault(cfg, "site_webhook_url", "http://127.0.0.1:8080/webhooks/minecraft/server");
        changed |= setDefault(cfg, "shared_secret", "");
        changed |= setDefault(cfg, "server_name", plugin.getServer().getName());
        changed |= setDefault(cfg, "listen_path", "/stemcraft/webhook");
        changed |= setDefault(cfg, "connect_timeout_millis", 5000L);
        changed |= setDefault(cfg, "request_timeout_millis", 10000L);
        changed |= setDefault(cfg, "replay_window_seconds", 300L);
        changed |= setDefault(cfg, "debug_logging", false);
        changed |= setDefault(cfg, "enforce_account_whitelist", true);
        changed |= setDefault(cfg, "bedrock_username_prefix", ".");
        ConfigSection rootCfg = getRootConfigSection();
        if (!rootCfg.contains("whitelist_message")) {
            String legacyMessage = cfg.getString("whitelist_kick_message", "");
            if (!legacyMessage.isBlank()) {
                rootCfg.set("whitelist_message", legacyMessage);
            } else {
                rootCfg.set("whitelist_message", "You are not whitelisted on this server.");
            }
            changed = true;
        }
        if (cfg.contains("whitelist_kick_message")) {
            cfg.set("whitelist_kick_message", null);
            changed = true;
        }
        changed |= setDefault(cfg, "drop_stale_sync_managed_events", true);
        changed |= setDefault(cfg, "drop_sync_managed_events_missing_occurred_at", true);
        changed |= setDefault(cfg, "allow_status_requests", true);
        changed |= setDefault(cfg, "allow_player_stats_requests", true);
        changed |= setDefault(cfg, "player_stats_sync_periods", List.of("day", "week", "month", "all"));
        changed |= setDefault(cfg, "allow_remote_commands", false);
        changed |= setDefault(cfg, "remote_command_max_output_chars", 12000);

        if (changed) {
            rootCfg.save();
            saveConfig();
        }
    }

    private String getWhitelistKickMessage() {
        String message = getRootConfigSection().getString("whitelist_message", "You are not whitelisted on this server.");
        return message.isBlank() ? "You are not whitelisted on this server." : message;
    }

    private boolean setDefault(ConfigSection cfg, String path, Object value) {
        if (cfg.contains(path)) {
            return false;
        }
        cfg.set(path, value);
        return true;
    }

    private void registerWebhookEndpoint() {
        String secret = getSharedSecret();
        if (secret.isBlank()) {
            plugin.getLogger().warning("Webhook bridge shared_secret is empty. Listener will not start.");
            return;
        }

        String listenPath = config().getString("listen_path", "/stemcraft/webhook");
        if (endpointRegistered.compareAndSet(false, true)) {
            api.web().registerEndpointHandler(listenPath, this::handleWebhookRequest);
        }
        plugin.getLogger().info("Webhook bridge listening on " + api.web().getPublicUrl() + listenPath);
    }

    private void activateBridge(String reason) {
        api.web().start();
        registerWebhookEndpoint();
        if (getSharedSecret().isBlank()) {
            return;
        }
        if ("startup".equalsIgnoreCase(reason)) {
            scheduleSyncRetry();
            return;
        }
        api.tasks().runAsync(() -> attemptSync(reason));
    }

    private void scheduleSyncRetry() {
        if (!isBridgeEnabled()) {
            return;
        }

        if (!syncRetryScheduled.compareAndSet(false, true)) {
            return;
        }

        long delayTicks = startupSyncDelaySecondsForAttempt(syncAttemptCounter.get()) * 20L;
        api.tasks().runLater(delayTicks, () -> {
            syncRetryScheduled.set(false);
            api.tasks().runAsync(() -> attemptSync("startup"));
        });
    }

    private void triggerImmediateSync() {
        if (!isBridgeEnabled() || initialSyncComplete.get()) {
            return;
        }

        api.tasks().runAsync(() -> attemptSync("webhook-recovery"));
    }

    private void attemptSync(String reason) {
        if (!plugin.isEnabled() || !isBridgeEnabled()) {
            return;
        }

        if (!syncInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            SyncResult result = executeSyncRequest(reason);
            recordSyncResult(reason, result);
            if (result.ok) {
                initialSyncComplete.set(true);
                syncAttemptCounter.set(0);
                drainPendingSyncManagedEvents();
                return;
            }

            int attemptNumber = syncAttemptCounter.incrementAndGet();
            plugin.getLogger().warning("Webhook bridge sync attempt " + attemptNumber + " failed: " + result.errorSummary);
            scheduleSyncRetry();
        } finally {
            syncInProgress.set(false);
        }
    }

    private static long startupSyncDelaySecondsForAttempt(int attemptIndex) {
        if (attemptIndex < 0) {
            throw new IllegalArgumentException("Invalid startup sync attempt index " + attemptIndex);
        }
        if (attemptIndex >= STARTUP_SYNC_BACKOFF_SECONDS.length) {
            return STARTUP_SYNC_REPEAT_SECONDS;
        }
        return STARTUP_SYNC_BACKOFF_SECONDS[attemptIndex];
    }

    private void runSyncRequest(java.util.function.Consumer<SyncResult> callback) {
        if (!syncInProgress.compareAndSet(false, true)) {
            api.tasks().runSync(() -> callback.accept(SyncResult.failure("Sync already in progress.")));
            return;
        }
        api.tasks().runAsync(() -> {
            SyncResult result;
            try {
                result = executeSyncRequest("manual");
                recordSyncResult("manual", result);
                if (result.ok) {
                    initialSyncComplete.set(true);
                    syncAttemptCounter.set(0);
                    drainPendingSyncManagedEvents();
                }
            } finally {
                syncInProgress.set(false);
            }
            SyncResult finalResult = result;
            api.tasks().runSync(() -> callback.accept(finalResult));
        });
    }

    private void drainPendingSyncManagedEvents() {
        List<QueuedInboundEvent> queued;
        synchronized (pendingSyncManagedEvents) {
            if (pendingSyncManagedEvents.isEmpty()) {
                return;
            }
            queued = new ArrayList<>(pendingSyncManagedEvents);
            pendingSyncManagedEvents.clear();
        }

        try {
            Future<?> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                for (QueuedInboundEvent queuedEvent : queued) {
                    try {
                        if (shouldDropStaleSyncManagedEvent(queuedEvent.payload())) {
                            continue;
                        }
                        handleInboundEvent(queuedEvent.payload());
                    } catch (Exception exception) {
                        plugin.getLogger().warning("Failed to apply queued webhook event " + queuedEvent.eventName() + ": " + exception.getMessage());
                    }
                }
                return null;
            });
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to drain queued webhook events after sync: " + exception.getMessage());
        }
    }

    private SyncResult executeSyncRequest(String reason) {
        String siteWebhookUrl = config().getString("site_webhook_url", "").trim();
        String secret = getSharedSecret();
        if (siteWebhookUrl.isBlank() || secret.isBlank()) {
            return SyncResult.failure("Missing site_webhook_url or shared_secret.");
        }

        try {
            Map<String, Object> pingPayload = new LinkedHashMap<>();
            pingPayload.put("event", "server.health.ping");
            pingPayload.put("server_name", config().getString("server_name", plugin.getServer().getName()));
            pingPayload.put("plugin_version", pluginVersion());
            JsonObject pingResponse = sendSyncEvent(siteWebhookUrl, secret, pingPayload);
            if (pingResponse == null || !booleanValue(pingResponse, "ok", false)) {
                return SyncResult.failure("server.health.ping failed");
            }

            Map<String, Object> playersPayload = new LinkedHashMap<>();
            playersPayload.put("event", "server.sync.players");
            playersPayload.put("server_name", config().getString("server_name", plugin.getServer().getName()));
            playersPayload.put("reason", reason);
            playersPayload.put("plugin_version", pluginVersion());
            playersPayload.put("players", buildPlayersSyncPayload());
            JsonObject playersResponse = sendSyncEvent(siteWebhookUrl, secret, playersPayload);
            if (playersResponse == null || !booleanValue(playersResponse, "ok", false)) {
                return SyncResult.failure("server.sync.players failed");
            }

            Map<String, Object> penaltiesPayload = new LinkedHashMap<>();
            penaltiesPayload.put("event", "server.sync.penalties");
            penaltiesPayload.put("server_name", config().getString("server_name", plugin.getServer().getName()));
            penaltiesPayload.put("reason", reason);
            penaltiesPayload.put("plugin_version", pluginVersion());
            penaltiesPayload.put("penalties", buildPenaltiesSyncPayload());
            JsonObject penaltiesResponse = sendSyncEvent(siteWebhookUrl, secret, penaltiesPayload);
            if (penaltiesResponse == null || !booleanValue(penaltiesResponse, "ok", false)) {
                return SyncResult.failure("server.sync.penalties failed");
            }

            SyncSnapshot snapshot = parseStartupSyncSnapshot(playersResponse, penaltiesResponse);
            if (snapshot == null) {
                return SyncResult.failure("Invalid sync responses");
            }
            Future<?> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                applySnapshot(snapshot);
                return null;
            });
            future.get(10, TimeUnit.SECONDS);
            sendPlayerStatsSync();
            return SyncResult.success();
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return SyncResult.failure(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private JsonObject sendSyncEvent(String siteWebhookUrl, String secret, Map<String, Object> payload) {
        String body = GSON.toJson(payload);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String deliveryId = UUID.randomUUID().toString();
        String signature = sign(body, timestamp, secret);
        String event = Objects.toString(payload.get("event"), "<unknown>");
        HttpRequest request = HttpRequest.newBuilder(URI.create(siteWebhookUrl))
            .timeout(Duration.ofMillis(config().getLong("request_timeout_millis", 10000L)))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Minecraft-Timestamp", timestamp)
            .header("X-Minecraft-Signature", signature)
            .header("X-Minecraft-Delivery-Id", deliveryId)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "failed", preview(response.body()));
                plugin.getLogger().warning("Sync event " + payload.get("event") + " failed: HTTP " + response.statusCode() + " body=" + preview(response.body()));
                return null;
            }
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "invalid_response", preview(response.body()));
                return null;
            }
            recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "ok", null);
            return parsed.getAsJsonObject();
        } catch (Exception exception) {
            recordOutboundHistory(event, deliveryId, siteWebhookUrl, 0, "exception", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            plugin.getLogger().warning("Sync event " + payload.get("event") + " failed: " + exception.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> buildPlayersSyncPayload() {
        List<Map<String, Object>> players = new ArrayList<>();
        for (AccountRecord account : allAccountRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", account.uuid);
            row.put("username", webhookOutboundUsername(account.username, account.platform));
            row.put("platform", normalizePlatform(account.platform));
            row.put("is_whitelisted", account.whitelisted);
            row.put("updated_at", account.occurredAt == null ? null : account.occurredAt.toString());
            players.add(row);
        }
        return players;
    }

    private List<Map<String, Object>> buildPenaltiesSyncPayload() {
        List<Map<String, Object>> penalties = new ArrayList<>();
        for (PenaltyStateRecord penalty : penaltiesByKey.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", penalty.uuid);
            row.put("username", penalty.username);
            row.put("type", penalty.type);
            row.put("started_at", penalty.createdAt == null ? null : penalty.createdAt.toString());
            row.put("updated_at", penalty.liftedAt != null
                ? penalty.liftedAt.toString()
                : (penalty.createdAt == null ? Instant.now().toString() : penalty.createdAt.toString()));
            row.put("reason", penalty.reason);
            row.put("is_permanent", penalty.permanent);
            if (penalty.endsAt != null && penalty.createdAt != null) {
                row.put("duration_seconds", Math.max(0L, Duration.between(penalty.createdAt, penalty.endsAt).getSeconds()));
            }
            if (penalty.liftedAt != null) {
                row.put("deleted_at", penalty.liftedAt.toString());
            }
            penalties.add(row);
        }
        return penalties;
    }

    private SyncSnapshot parseStartupSyncSnapshot(JsonObject playersResponse, JsonObject penaltiesResponse) {
        try {
            JsonObject playersSync = requiredSyncObject(playersResponse);
            JsonObject penaltiesSync = requiredSyncObject(penaltiesResponse);
            List<AccountSnapshot> accounts = parseAccounts(requiredArray(playersSync, "players"));
            List<PenaltySnapshot> penalties = parsePenalties(requiredArray(penaltiesSync, "penalties"));
            return new SyncSnapshot("replace", Instant.now(), accounts, penalties, List.of());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void sendPlayerLogin(Player player) {
        Map<String, Object> payload = playerPayload("player.login", player);
        String sessionId = UUID.randomUUID().toString();
        activeSessionsByUuid.put(normalize(player.getUniqueId()), sessionId);
        payload.put("session_uuid", sessionId);
        sendWebhook(payload);
        sendPlayerProfileUpdated(player);
    }

    private void sendPlayerLogout(Player player) {
        Map<String, Object> payload = playerPayload("player.logout", player);
        String sessionId = activeSessionsByUuid.remove(normalize(player.getUniqueId()));
        if (sessionId != null) {
            payload.put("session_uuid", sessionId);
        }
        sendWebhook(payload);
    }

    private void sendShutdownLifecycleEvents() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Object> payload = playerPayload("player.logout", player);
            String sessionId = activeSessionsByUuid.remove(normalize(player.getUniqueId()));
            if (sessionId != null) {
                payload.put("session_uuid", sessionId);
            }
            sendWebhookSync(payload);
        }

        Map<String, Object> shutdownPayload = new LinkedHashMap<>();
        shutdownPayload.put("event", "server.shutdown");
        shutdownPayload.put("server_name", config().getString("server_name", plugin.getServer().getName()));
        shutdownPayload.put("plugin_version", pluginVersion());
        shutdownPayload.put("occurred_at", Instant.now().toString());
        shutdownPayload.put("online_players", onlinePlayers);
        sendWebhookSync(shutdownPayload);
    }

    private void sendPlayerProfileUpdated(Player player) {
        sendWebhook(playerPayload("player.profile.updated", player));
    }

    private void sendPlayerStatsSync() {
        Map<String, Object> baseSnapshot = api.playerStats().buildWebhookStatsResponse(null, null, null, "all");
        List<Map<String, Object>> periodSnapshots = new ArrayList<>();
        for (String period : configuredStatsSyncPeriods()) {
            Map<String, Object> snapshot = api.playerStats().buildWebhookStatsResponse(null, null, null, period);
            Map<String, Object> periodPayload = new LinkedHashMap<>();
            periodPayload.put("period", snapshot.get("period"));
            periodPayload.put("period_days", snapshot.get("period_days"));
            periodPayload.put("timestamp", snapshot.get("timestamp"));
            periodPayload.put("players", normalizeStatsPlayers(snapshot.get("players")));
            periodSnapshots.add(periodPayload);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "server.sync.players.stats");
        payload.put("timestamp", Instant.now().toString());
        payload.put("stats", baseSnapshot.get("stats"));
        payload.put("periods", periodSnapshots);
        sendWebhook(payload);
    }

    private void runPlayerStatsSyncRequest(java.util.function.Consumer<SyncResult> callback) {
        if (!syncInProgress.compareAndSet(false, true)) {
            api.tasks().runSync(() -> callback.accept(SyncResult.failure("Sync already in progress.")));
            return;
        }
        api.tasks().runAsync(() -> {
            SyncResult result;
            try {
                result = executePlayerStatsSyncRequest();
                recordSyncResult("manual-stats", result);
            } finally {
                syncInProgress.set(false);
            }
            SyncResult finalResult = result;
            api.tasks().runSync(() -> callback.accept(finalResult));
        });
    }

    private SyncResult executePlayerStatsSyncRequest() {
        String siteWebhookUrl = config().getString("site_webhook_url", "").trim();
        String secret = getSharedSecret();
        if (siteWebhookUrl.isBlank() || secret.isBlank()) {
            return SyncResult.failure("Missing site_webhook_url or shared_secret.");
        }

        Map<String, Object> baseSnapshot = api.playerStats().buildWebhookStatsResponse(null, null, null, "all");
        List<Map<String, Object>> periodSnapshots = new ArrayList<>();
        for (String period : configuredStatsSyncPeriods()) {
            Map<String, Object> snapshot = api.playerStats().buildWebhookStatsResponse(null, null, null, period);
            Map<String, Object> periodPayload = new LinkedHashMap<>();
            periodPayload.put("period", snapshot.get("period"));
            periodPayload.put("period_days", snapshot.get("period_days"));
            periodPayload.put("timestamp", snapshot.get("timestamp"));
            periodPayload.put("players", normalizeStatsPlayers(snapshot.get("players")));
            periodSnapshots.add(periodPayload);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "server.sync.players.stats");
        payload.put("timestamp", Instant.now().toString());
        payload.put("stats", baseSnapshot.get("stats"));
        payload.put("periods", periodSnapshots);

        JsonObject response = sendSyncEvent(siteWebhookUrl, secret, payload);
        if (response == null || !booleanValue(response, "ok", false)) {
            return SyncResult.failure("server.sync.players.stats failed");
        }
        return SyncResult.success();
    }

    private List<Map<String, Object>> normalizeStatsPlayers(Object playersPayload) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (!(playersPayload instanceof Iterable<?> iterable)) {
            return normalized;
        }

        for (Object row : iterable) {
            if (!(row instanceof Map<?, ?> source)) {
                continue;
            }

            Map<String, Object> player = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                player.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            String uuidText = objectString(player.get("uuid"));
            String username = objectString(player.get("username"));
            UUID uuid = parseUuid(uuidText);
            String platform = statsPlatformFor(uuid, username);

            player.put("platform", platform);
            player.put("username", webhookOutboundUsername(username == null ? "" : username, platform));
            normalized.add(player);
        }

        return normalized;
    }

    private String statsPlatformFor(@Nullable UUID uuid, @Nullable String username) {
        String inferred = inferPlatform(uuid, username);
        AccountRecord stored = findAccount(uuid, username, inferred);
        if (stored != null && stored.platform != null && !stored.platform.isBlank()) {
            return normalizePlatform(stored.platform);
        }
        return inferred;
    }

    private String inferPlatform(@Nullable UUID uuid, @Nullable String username) {
        if (PlayerUtil.isBedrock(uuid)) {
            return PLATFORM_BEDROCK;
        }
        if (looksLikePrefixedBedrockUsername(username)) {
            return PLATFORM_BEDROCK;
        }
        return PLATFORM_JAVA;
    }

    private static @Nullable String objectString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> playerPayload(String event, Player player) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String platform = PlayerUtil.isBedrock(player) ? PLATFORM_BEDROCK : PLATFORM_JAVA;
        payload.put("event", event);
        payload.put("uuid", normalize(player.getUniqueId()));
        payload.put("username", webhookOutboundUsername(player.getName(), platform));
        payload.put("platform", platform);
        payload.put("occurred_at", Instant.now().toString());
        payload.put("server_name", config().getString("server_name", plugin.getServer().getName()));
        return payload;
    }

    private String webhookOutboundUsername(String username, String platform) {
        if (username == null) {
            return "";
        }
        if (!PLATFORM_BEDROCK.equalsIgnoreCase(normalizePlatform(platform))) {
            return username;
        }

        String prefix = bedrockUsernamePrefix();
        if (!prefix.isEmpty() && username.startsWith(prefix)) {
            return username.substring(prefix.length());
        }
        return username;
    }

    private String webhookInboundUsername(String username, String platform) {
        if (username == null || username.isBlank()) {
            return username;
        }
        if (!PLATFORM_BEDROCK.equalsIgnoreCase(normalizePlatform(platform))) {
            return username;
        }

        String prefix = bedrockUsernamePrefix();
        if (!prefix.isEmpty() && username.startsWith(prefix)) {
            return username.substring(prefix.length());
        }
        return username;
    }

    private String bedrockUsernamePrefix() {
        return config().getString("bedrock_username_prefix", ".");
    }

    private void sendWebhook(Map<String, Object> payload) {
        if (!isBridgeEnabled()) {
            return;
        }

        String body = GSON.toJson(payload);
        dispatchWebhook(body, UUID.randomUUID().toString());
    }

    private void sendWebhookSync(Map<String, Object> payload) {
        if (!isBridgeEnabled()) {
            return;
        }

        String body = GSON.toJson(payload);
        String siteWebhookUrl = config().getString("site_webhook_url", "").trim();
        String secret = getSharedSecret();
        String event = eventNameFromBody(body);
        String deliveryId = UUID.randomUUID().toString();
        if (siteWebhookUrl.isBlank() || secret.isBlank()) {
            recordOutboundHistory(event, deliveryId, siteWebhookUrl, 0, "skipped", "missing site_webhook_url or shared_secret");
            plugin.getLogger().warning("Skipping outbound webhook event: missing site_webhook_url or shared_secret.");
            return;
        }

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = sign(body, timestamp, secret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(siteWebhookUrl))
            .timeout(Duration.ofMillis(config().getLong("request_timeout_millis", 10000L)))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Minecraft-Timestamp", timestamp)
            .header("X-Minecraft-Signature", signature)
            .header("X-Minecraft-Delivery-Id", deliveryId)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "failed", preview(response.body()));
                plugin.getLogger().warning("[webhook-outbound] event=" + event
                    + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
                    + " status=" + response.statusCode()
                    + " result=failed body=" + preview(response.body()));
            } else if (config().getBoolean("debug_logging", false)) {
                recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "ok", null);
                plugin.getLogger().info("[webhook-outbound] event=" + event
                    + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
                    + " status=" + response.statusCode()
                    + " result=ok");
            } else {
                recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "ok", null);
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordOutboundHistory(event, deliveryId, siteWebhookUrl, 0, "exception", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            plugin.getLogger().warning("[webhook-outbound] event=" + event
                + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
                + " result=exception error=" + exception.getMessage());
        }
    }

    private Object handleInboundEvent(JsonObject payload) {
        String event = stringValue(payload, "event");
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("Missing event");
        }

        return switch (event) {
            case "player.profile.created" -> {
                handleAccountSync(objectOrSelf(payload, "player"));
                yield null;
            }
            case "player.profile.deleted" -> {
                handleAccountRemove(objectOrSelf(payload, "player"));
                yield null;
            }
            case "account.sync" -> {
                handleAccountSync(objectOrSelf(payload, "account"));
                yield null;
            }
            case "account.remove" -> {
                handleAccountRemove(objectOrSelf(payload, "account"));
                yield null;
            }
            case "player.penalty.updated" -> {
                if (isPenaltyLiftPayload(payload)) {
                    handlePenaltyLifted(payload);
                } else {
                    handlePenaltyCreated(payload);
                }
                yield null;
            }
            case "player.penalty.deleted", "player.penalty.lifted" -> {
                handlePenaltyLifted(payload);
                yield null;
            }
            case "blacklist.sync" -> {
                handleBlacklistSync(objectOrSelf(payload, "blacklist"));
                yield null;
            }
            case "blacklist.remove" -> {
                handleBlacklistRemove(objectOrSelf(payload, "blacklist"));
                yield null;
            }
            case "player.penalty.created" -> {
                handlePenaltyCreated(payload);
                yield null;
            }
            case "server.status.request" -> buildServerStatusResponse();
            case "server.player-stats.request" -> buildPlayerStatsResponse(payload);
            case "server.command.request" -> executeServerCommandRequest(payload);
            default -> throw new IllegalArgumentException("Unsupported event: " + event);
        };
    }

    private boolean shouldQueueSyncManagedEvent(JsonObject payload) {
        String event = stringValue(payload, "event");
        return !initialSyncComplete.get() && isSyncManagedEvent(event);
    }

    private void queueSyncManagedEvent(JsonObject payload, String deliveryId) {
        String event = Objects.requireNonNullElse(stringValue(payload, "event"), "<missing>");
        synchronized (pendingSyncManagedEvents) {
            pendingSyncManagedEvents.add(new QueuedInboundEvent(deliveryId, event, payload.deepCopy()));
        }
    }

    private void handleAccountSync(JsonObject accountJson) {
        Instant occurredAt = requireOccurredAt(accountJson);
        String platform = normalizePlatform(stringValue(accountJson, "platform"));
        String username = webhookInboundUsername(requireString(accountJson, "username"), platform);
        String uuidText = stringValue(accountJson, "uuid");
        boolean whitelisted = booleanValue(accountJson, "is_whitelisted", true);
        AccountRecord existing = findStoredAccount(uuidText, username, platform);
        if (existing != null && existing.occurredAt != null && !occurredAt.isAfter(existing.occurredAt)) {
            return;
        }

        AccountRecord record = mergeAccountRecord(existing, username, uuidText, platform, whitelisted, occurredAt);
        putAccount(record);

        if (whitelisted) {
            applyWhitelist(record, true);
            kickIfBlacklisted(record);
        } else {
            handleAccountRemove(accountJson);
        }
    }

    private void handleAccountRemove(JsonObject accountJson) {
        Instant occurredAt = requireOccurredAt(accountJson);
        String platform = normalizePlatform(stringValue(accountJson, "platform"));
        String username = webhookInboundUsername(stringValue(accountJson, "username"), platform);
        String uuidText = stringValue(accountJson, "uuid");

        AccountRecord existing = findStoredAccount(uuidText, username, platform);
        if (existing != null && existing.occurredAt != null && !occurredAt.isAfter(existing.occurredAt)) {
            return;
        }

        AccountRecord removedAccount = mergeAccountRecord(existing, username, uuidText, platform, false, occurredAt);
        putAccount(removedAccount);
        applyWhitelist(removedAccount, false);
        kickIfNoLongerWhitelisted(removedAccount);
    }

    private void handleBlacklistSync(JsonObject blacklistJson) {
        Instant occurredAt = requireOccurredAt(blacklistJson);
        BlacklistRecord existing = findStoredBlacklist(stringValue(blacklistJson, "uuid"), stringValue(blacklistJson, "username"));
        if (existing != null && existing.occurredAt != null && !occurredAt.isAfter(existing.occurredAt)) {
            return;
        }

        BlacklistRecord record = new BlacklistRecord(
            stringValue(blacklistJson, "username"),
            stringValue(blacklistJson, "uuid"),
            stringValue(blacklistJson, "reason"),
            parseInstant(stringValue(blacklistJson, "starts_at")),
            parseInstant(stringValue(blacklistJson, "ends_at")),
            booleanValue(blacklistJson, "is_permanent", false),
            occurredAt
        );

        if (record.key() == null) {
            throw new IllegalArgumentException("Blacklist sync requires uuid or username");
        }

        putBlacklist(record);
        kickIfBlacklisted(record);
    }

    private void handleBlacklistRemove(JsonObject blacklistJson) {
        String uuidText = stringValue(blacklistJson, "uuid");
        String username = stringValue(blacklistJson, "username");
        Instant occurredAt = requireOccurredAt(blacklistJson);
        BlacklistRecord existing = findStoredBlacklist(uuidText, username);
        if (existing != null && existing.occurredAt != null && !occurredAt.isAfter(existing.occurredAt)) {
            return;
        }

        BlacklistRecord tombstone = new BlacklistRecord(
            username != null && !username.isBlank() ? username : existing == null ? null : existing.username,
            uuidText != null && !uuidText.isBlank() ? uuidText : existing == null ? null : existing.uuid,
            existing == null ? null : existing.reason,
            existing == null ? null : existing.startsAt,
            occurredAt,
            false,
            occurredAt
        );
        if (tombstone.key() != null) {
            putBlacklist(tombstone);
        }
    }

    private void handlePenaltyCreated(JsonObject payload) {
        if (isPenaltyLiftPayload(payload)) {
            handlePenaltyLifted(payload);
            return;
        }

        String type = requireString(payload, "type").toLowerCase();
        String key = penaltyKey(payload, type);
        if (key == null) {
            throw new IllegalArgumentException("Penalty create requires penalty_key, uuid, or username.");
        }

        Instant occurredAt = parseInstant(stringValue(payload, "started_at"));
        if (occurredAt == null) {
            occurredAt = Objects.requireNonNullElse(parseInstant(stringValue(payload, "occurred_at")), Instant.now());
        }
        long durationSeconds = durationSecondsValue(payload);
        boolean isPermanent = booleanValue(payload, "is_permanent", false);
        Instant explicitEndsAt = parseInstant(stringValue(payload, "ends_at"));
        Instant endsAt = "kick".equals(type)
            ? occurredAt
            : (explicitEndsAt != null ? explicitEndsAt : (isPermanent || durationSeconds <= 0L ? null : occurredAt.plusSeconds(durationSeconds)));

        PenaltyStateRecord existing = penaltiesByKey.get(key);
        if (existing != null) {
            if (existing.liftedAt != null && !occurredAt.isAfter(existing.liftedAt)) {
                return;
            }
            if (existing.createdAt != null && occurredAt.isBefore(existing.createdAt)) {
                return;
            }
        }

        PenaltyStateRecord record = new PenaltyStateRecord(
            penaltyExternalId(payload),
            stringValue(payload, "username"),
            stringValue(payload, "uuid"),
            type,
            stringValue(payload, "reason"),
            occurredAt,
            endsAt,
            isPermanent,
            null
        );
        penaltiesByKey.put(key, record);
        savePenaltyState();

        if ("kick".equals(type)) {
            kickMatchingPlayers(record, messageForPenalty(record, "You have been kicked from this server."));
            return;
        }
        if ("ban".equals(type)) {
            kickMatchingPlayers(record, messageForPenalty(record, "You are banned from this server."));
        }
    }

    private void handlePenaltyLifted(JsonObject payload) {
        String type = stringValue(payload, "type");
        String normalizedType = type == null ? "ban" : type.toLowerCase();
        String key = penaltyKey(payload, normalizedType);
        if (key == null) {
            throw new IllegalArgumentException("Penalty lift requires penalty_key, uuid, or type+username.");
        }

        Instant occurredAt = penaltyLiftedAt(payload);
        if (occurredAt == null) {
            occurredAt = Objects.requireNonNullElse(parseInstant(stringValue(payload, "occurred_at")), Instant.now());
        }
        PenaltyStateRecord existing = penaltiesByKey.get(key);
        if (existing != null && existing.liftedAt != null && !occurredAt.isAfter(existing.liftedAt)) {
            return;
        }

        PenaltyStateRecord record = existing == null
            ? new PenaltyStateRecord(
                penaltyExternalId(payload),
                stringValue(payload, "username"),
                stringValue(payload, "uuid"),
                normalizedType,
                null,
                null,
                null,
                false,
                occurredAt
            )
            : new PenaltyStateRecord(
                existing.externalId,
                existing.username,
                existing.uuid,
                existing.type,
                existing.reason,
                existing.createdAt,
                existing.endsAt,
                existing.permanent,
                occurredAt
            );

        penaltiesByKey.put(key, record);
        savePenaltyState();

        if ("ban".equals(normalizedType)) {
            pardonLocalBan(record.uuid, record.username);
            UUID liftedUuid = parseUuid(record.uuid);
            if (liftedUuid != null) {
                plugin.punishments().syncLiftBan(liftedUuid, stringValue(payload, "reason"));
            }
        }
    }

    private static boolean isPenaltyLiftPayload(JsonObject payload) {
        return penaltyLiftedAt(payload) != null;
    }

    private static Instant penaltyLiftedAt(JsonObject payload) {
        Instant liftedAt = parseInstant(stringValue(payload, "lifted_at"));
        if (liftedAt != null) {
            return liftedAt;
        }
        return parseInstant(stringValue(payload, "deleted_at"));
    }

    private Map<String, Object> buildServerStatusResponse() {
        if (!config().getBoolean("allow_status_requests", true)) {
            throw new SecurityException("Status requests are disabled.");
        }

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeWithinAllocated = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeWithinAllocated;
        long freeMemory = Math.max(0L, maxMemory - usedMemory);

        long loadedChunks = Bukkit.getWorlds().stream()
            .mapToLong(World::getChunkCount)
            .sum();

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("free_bytes", freeMemory);
        memory.put("used_bytes", usedMemory);
        memory.put("allocated_bytes", allocatedMemory);
        memory.put("max_bytes", maxMemory);

        Map<String, Object> players = new LinkedHashMap<>();
        players.put("online", Bukkit.getOnlinePlayers().size());
        players.put("max", Bukkit.getMaxPlayers());

        double[] tpsSamples = Bukkit.getTPS();
        Map<String, Object> tps = new LinkedHashMap<>();
        tps.put("one_minute", sanitizeTps(tpsSamples, 0));
        tps.put("five_minute", sanitizeTps(tpsSamples, 1));
        tps.put("fifteen_minute", sanitizeTps(tpsSamples, 2));

        List<Map<String, Object>> worlds = Bukkit.getWorlds().stream()
            .map(world -> {
                Map<String, Object> worldData = new LinkedHashMap<>();
                worldData.put("name", world.getName());
                worldData.put("players", world.getPlayers().size());
                worldData.put("loaded_chunks", world.getChunkCount());
                return worldData;
            })
            .collect(Collectors.toList());

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("server_name", config().getString("server_name", plugin.getServer().getName()));
        status.put("bukkit_name", Bukkit.getName());
        status.put("bukkit_version", Bukkit.getVersion());
        status.put("minecraft_version", Bukkit.getMinecraftVersion());
        status.put("plugin_version", plugin.getPluginMeta().getVersion());
        status.put("online_mode", Bukkit.getOnlineMode());
        status.put("players", players);
        status.put("memory", memory);
        status.put("loaded_chunks", loadedChunks);
        status.put("worlds", worlds);
        status.put("tps", tps);
        status.put("timestamp", Instant.now().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("status", status);
        return response;
    }

    private Map<String, Object> buildPlayerStatsResponse(JsonObject payload) {
        if (!config().getBoolean("allow_player_stats_requests", true)) {
            throw new SecurityException("Player stats requests are disabled.");
        }

        String uuid = nullableString(payload, "uuid");
        String username = nullableString(payload, "username");
        String statKey = nullableString(payload, "stat_key");
        String period = nullableString(payload, "period");
        if (period == null || period.isBlank()) {
            period = nullableString(payload, "range");
        }
        Map<String, Object> response = new LinkedHashMap<>(api.playerStats().buildWebhookStatsResponse(uuid, username, statKey, period));
        response.put("players", normalizeStatsPlayers(response.get("players")));
        return response;
    }

    private Map<String, Object> executeServerCommandRequest(JsonObject payload) {
        if (!config().getBoolean("allow_remote_commands", false)) {
            throw new SecurityException("Remote commands are disabled.");
        }

        String command = requireString(payload, "command").trim();
        if (command.isBlank()) {
            throw new IllegalArgumentException("Missing command");
        }

        String commandLine = command.startsWith("/") ? command.substring(1) : command;
        StringBuilder outputBuffer = new StringBuilder();
        CommandSender sender = Bukkit.getServer().createCommandSender(component -> {
            if (!outputBuffer.isEmpty()) {
                outputBuffer.append('\n');
            }
            outputBuffer.append(PLAIN_TEXT.serialize(component));
        });
        long startedAt = System.currentTimeMillis();
        boolean success;
        try {
            success = Bukkit.dispatchCommand(sender, commandLine);
        } catch (CommandException exception) {
            if (!outputBuffer.isEmpty()) {
                outputBuffer.append('\n');
            }
            outputBuffer.append(exception.getMessage());
            success = false;
        }
        long durationMillis = Math.max(0L, System.currentTimeMillis() - startedAt);

        String output = outputBuffer.toString();
        int maxOutputChars = Math.max(0, config().getInt("remote_command_max_output_chars", 12000));
        boolean truncated = false;
        if (maxOutputChars > 0 && output.length() > maxOutputChars) {
            output = output.substring(0, maxOutputChars);
            truncated = true;
        }

        Map<String, Object> commandResult = new LinkedHashMap<>();
        commandResult.put("command", commandLine);
        commandResult.put("success", success);
        commandResult.put("output", output);
        commandResult.put("truncated", truncated);
        commandResult.put("duration_millis", durationMillis);
        commandResult.put("timestamp", Instant.now().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("result", commandResult);
        return response;
    }

    private void applySnapshot(SyncSnapshot snapshot) {
        List<PenaltyStateRecord> previousPenalties = new ArrayList<>(penaltiesByKey.values());
        Map<String, AccountRecord> previousAccountsByKey = new LinkedHashMap<>();
        Map<String, String> previousUuidByPlatformUsername = new LinkedHashMap<>();
        for (AccountRecord existing : allAccountRecords()) {
            String key = accountKey(existing.uuid, existing.username, existing.platform);
            if (key != null) {
                previousAccountsByKey.put(key, existing);
            }
            if (existing.username != null && !existing.username.isBlank()
                && existing.uuid != null && !existing.uuid.isBlank()) {
                previousUuidByPlatformUsername.put(
                    accountUsernameKey(existing.username, existing.platform),
                    existing.uuid
                );
            }
        }

        accountsByUsername.clear();
        accountsByUuid.clear();
        for (AccountSnapshot account : snapshot.accounts) {
            String accountPlatform = normalizePlatform(account.platform);
            String accountUsername = webhookInboundUsername(account.username, accountPlatform);
            String accountUuid = account.uuid;
            if ((accountUuid == null || accountUuid.isBlank()) && accountUsername != null && !accountUsername.isBlank()) {
                String preservedUuid = previousUuidByPlatformUsername.get(
                    accountUsernameKey(accountUsername, accountPlatform)
                );
                if (preservedUuid != null && !preservedUuid.isBlank()) {
                    accountUuid = preservedUuid;
                }
            }

            Instant occurredAt = (account.occurredAt == null ? snapshot.asOf : account.occurredAt);
            putAccount(new AccountRecord(
                accountUsername,
                accountUuid,
                accountPlatform,
                account.whitelisted,
                occurredAt
            ), false);
            if (config().getBoolean("debug_logging", false)) {
                plugin.getLogger().info("[webhook-sync] account uuid=" + Objects.requireNonNullElse(accountUuid, "<null>")
                    + " username=" + Objects.requireNonNullElse(accountUsername, "<null>")
                    + " platform=" + accountPlatform
                    + " whitelisted=" + account.whitelisted
                    + " occurred_at=" + Objects.requireNonNullElse(
                        occurredAt,
                        Instant.now()
                    ));
            }
        }

        Set<String> appliedAccounts = new LinkedHashSet<>();
        for (AccountRecord account : allAccountRecords()) {
            String key = accountKey(account.uuid, account.username, account.platform);
            if (key != null) {
                appliedAccounts.add(key);
                applyWhitelist(account, account.whitelisted);
                if (!account.whitelisted) {
                    kickIfNoLongerWhitelisted(account);
                }
            }
        }

        for (Map.Entry<String, AccountRecord> previousEntry : previousAccountsByKey.entrySet()) {
            if (appliedAccounts.contains(previousEntry.getKey())) {
                continue;
            }
            applyWhitelist(previousEntry.getValue(), false);
            kickIfNoLongerWhitelisted(previousEntry.getValue());
        }
        saveAccountState();

        penaltiesByKey.clear();
        Set<UUID> snapshotBanUuids = new LinkedHashSet<>();
        for (PenaltySnapshot penalty : snapshot.penalties) {
            String key = penaltyKey(penalty.externalId, penalty.uuid, penalty.username, penalty.type);
            if (key == null) {
                continue;
            }
            if ("ban".equalsIgnoreCase(penalty.type) && isSnapshotPenaltyActive(penalty)) {
                UUID banUuid = parseUuid(penalty.uuid);
                if (banUuid != null) {
                    snapshotBanUuids.add(banUuid);
                }
            }
            penaltiesByKey.put(key, new PenaltyStateRecord(
                penalty.externalId,
                penalty.username,
                penalty.uuid,
                penalty.type,
                penalty.reason,
                penalty.startedAt,
                penalty.endsAt,
                penalty.permanent,
                penalty.liftedAt
            ));
        }
        savePenaltyState();

        for (PenaltyStateRecord previousPenalty : previousPenalties) {
            if (!"ban".equalsIgnoreCase(previousPenalty.type)) {
                continue;
            }

            PenaltyStateRecord currentPenalty = previousPenalty.key() == null ? null : penaltiesByKey.get(previousPenalty.key());
            if (currentPenalty == null || currentPenalty.isInactive()) {
                pardonLocalBan(previousPenalty.uuid, previousPenalty.username);
                UUID previousUuid = parseUuid(previousPenalty.uuid);
                if (previousUuid != null && !snapshotBanUuids.contains(previousUuid)) {
                    plugin.punishments().syncLiftBan(previousUuid, "Lifted by website sync");
                }
            }
        }
        plugin.punishments().syncReconcileActiveBans(snapshotBanUuids);

        blacklistByKey.clear();
        for (BlacklistSnapshot blacklist : snapshot.legacyBlacklist) {
            BlacklistRecord record = new BlacklistRecord(
                blacklist.username,
                blacklist.uuid,
                blacklist.reason,
                blacklist.startsAt,
                blacklist.endsAt,
                blacklist.permanent,
                blacklist.occurredAt == null ? snapshot.asOf : blacklist.occurredAt
            );
            String key = record.key();
            if (key != null) {
                blacklistByKey.put(key, record);
            }
        }
        saveBlacklistState();

        for (PenaltyStateRecord penalty : penaltiesByKey.values()) {
            if (penalty.isInactive()) {
                continue;
            }
            if ("ban".equalsIgnoreCase(penalty.type)) {
                kickMatchingPlayers(penalty, messageForPenalty(penalty, "You are banned from this server."));
            } else if ("kick".equalsIgnoreCase(penalty.type)) {
                kickMatchingPlayers(penalty, messageForPenalty(penalty, "You have been kicked from this server."));
            }
        }
        for (BlacklistRecord blacklist : blacklistByKey.values()) {
            kickIfBlacklisted(blacklist);
        }

        this.syncStateCutoff = snapshot.asOf == null ? Instant.now() : snapshot.asOf;
    }

    private static boolean isSnapshotPenaltyActive(PenaltySnapshot penalty) {
        if (penalty == null || !"ban".equalsIgnoreCase(penalty.type)) {
            return false;
        }
        if (penalty.liftedAt != null) {
            return false;
        }
        if (penalty.permanent) {
            return true;
        }
        return penalty.endsAt != null && penalty.endsAt.isAfter(Instant.now());
    }

    private void kickIfNoLongerWhitelisted(AccountRecord record) {
        String message = "You are no longer whitelisted on this server.";
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            boolean sameUuid = record.uuid != null && record.uuid.equalsIgnoreCase(normalize(onlinePlayer.getUniqueId()));
            boolean sameUsername = record.username != null && record.username.equalsIgnoreCase(onlinePlayer.getName());
            if (sameUuid || sameUsername) {
                kickPlayer(onlinePlayer, message);
            }
        }
    }

    @SuppressWarnings("EmptyMethod")
    private void applyWhitelist(AccountRecord record, boolean whitelisted) {
        // Webhook bridge whitelist authority is in-memory/database account state only.
        // Do not mirror whitelist state into Bukkit or Gatekeeper allowlists.
    }

    private void pardonLocalBan(String uuidText, String username) {
        UUID uuid = parseUuid(uuidText);
        String resolvedName = username;
        if ((resolvedName == null || resolvedName.isBlank()) && uuid != null) {
            resolvedName = PlayerUtil.name(uuid);
        }

        if (uuid == null && (resolvedName == null || resolvedName.isBlank())) {
            return;
        }

        PlayerProfile profile = Bukkit.createProfile(uuid, resolvedName);
        BanList<PlayerProfile> banList = Bukkit.getBanList(BanListType.PROFILE);
        if (banList.isBanned(profile)) {
            banList.pardon(profile);
        }
    }

    private void kickIfBlacklisted(AccountRecord record) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            boolean sameUuid = record.uuid != null && record.uuid.equalsIgnoreCase(normalize(onlinePlayer.getUniqueId()));
            boolean sameUsername = record.username != null && record.username.equalsIgnoreCase(onlinePlayer.getName());
            if ((sameUuid || sameUsername) && isBlacklisted(onlinePlayer.getUniqueId(), onlinePlayer.getName())) {
                kickPlayer(onlinePlayer, messageForBlacklist(onlinePlayer.getUniqueId(), onlinePlayer.getName()));
            }
        }
    }

    private void kickIfBlacklisted(BlacklistRecord record) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (matchesBlacklist(record, player.getUniqueId(), player.getName())) {
                kickPlayer(player, messageForBlacklist(player.getUniqueId(), player.getName()));
            }
        }
    }

    private void kickMatchingPlayers(PenaltyStateRecord record, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (matchesPenalty(record, player.getUniqueId(), player.getName())) {
                kickPlayer(player, message);
            }
        }
    }

    private void syncObservedPlayer(Player player) {
        AccountRecord existing = findStoredAccount(normalize(player.getUniqueId()), player.getName(), PlayerUtil.isBedrock(player) ? PLATFORM_BEDROCK : PLATFORM_JAVA);
        putAccount(new AccountRecord(
            player.getName(),
            normalize(player.getUniqueId()),
            PlayerUtil.isBedrock(player) ? PLATFORM_BEDROCK : PLATFORM_JAVA,
            existing != null && existing.whitelisted,
            existing == null ? Instant.now() : existing.occurredAt
        ));
    }

    private void putAccount(AccountRecord record) {
        putAccount(record, true);
    }

    private void putAccount(AccountRecord record, boolean persist) {
        AccountRecord existing = findStoredAccount(record.uuid, record.username, record.platform);
        if (existing != null) {
            if (existing.username != null && !existing.username.isBlank()) {
                accountsByUsername.remove(accountUsernameKey(existing.username, existing.platform));
            }
            if (existing.uuid != null && !existing.uuid.isBlank()) {
                accountsByUuid.remove(accountUuidKey(existing.uuid, existing.platform));
            }
        }
        if (record.username != null && !record.username.isBlank()) {
            accountsByUsername.put(accountUsernameKey(record.username, record.platform), record);
        }
        if (record.uuid != null && !record.uuid.isBlank()) {
            accountsByUuid.put(accountUuidKey(record.uuid, record.platform), record);
        }
        if (persist) {
            saveAccountState();
        }
    }

    private void dispatchWebhook(String body, String deliveryId) {
        String siteWebhookUrl = config().getString("site_webhook_url", "").trim();
        String secret = getSharedSecret();
        String event = eventNameFromBody(body);
        if (siteWebhookUrl.isBlank() || secret.isBlank()) {
            recordOutboundHistory(event, deliveryId, siteWebhookUrl, 0, "skipped", "missing site_webhook_url or shared_secret");
            plugin.getLogger().warning("Skipping outbound webhook event: missing site_webhook_url or shared_secret.");
            return;
        }

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = sign(body, timestamp, secret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(siteWebhookUrl))
            .timeout(Duration.ofMillis(config().getLong("request_timeout_millis", 10000L)))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Minecraft-Timestamp", timestamp)
            .header("X-Minecraft-Signature", signature)
            .header("X-Minecraft-Delivery-Id", deliveryId)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        plugin.getLogger().info("[webhook-outbound] event=" + event
            + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
            + " url=" + siteWebhookUrl);

        api.tasks().runAsync(() -> {
            try {
                HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() > 299) {
                    recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "failed", preview(response.body()));
                    plugin.getLogger().warning("[webhook-outbound] event=" + event
                        + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
                        + " status=" + response.statusCode()
                        + " result=failed body=" + preview(response.body()));
                } else if (config().getBoolean("debug_logging", false)) {
                    recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "ok", null);
                    plugin.getLogger().info("[webhook-outbound] event=" + event
                        + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
                        + " status=" + response.statusCode()
                        + " result=ok");
                } else {
                    recordOutboundHistory(event, deliveryId, siteWebhookUrl, response.statusCode(), "ok", null);
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                recordOutboundHistory(event, deliveryId, siteWebhookUrl, 0, "exception", exception.getClass().getSimpleName() + ": " + exception.getMessage());
                plugin.getLogger().warning("[webhook-outbound] event=" + event
                    + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
                    + " result=exception error=" + exception.getMessage());
            }
        });
    }

    private AccountRecord findStoredAccount(String uuidText, String username, String platform) {
        AccountRecord byUuid = null;
        String normalizedPlatform = normalizePlatform(platform);
        if (uuidText != null && !uuidText.isBlank()) {
            AccountRecord candidate = accountsByUuid.get(accountUuidKey(uuidText, normalizedPlatform));
            if (candidate != null && platformMatches(candidate.platform, platform)) {
                byUuid = candidate;
            }
        }

        AccountRecord byUsername = null;
        if (username != null && !username.isBlank()) {
            AccountRecord candidate = accountsByUsername.get(accountUsernameKey(username, normalizedPlatform));
            if (candidate != null && platformMatches(candidate.platform, platform)) {
                byUsername = candidate;
            }
        }

        if (byUuid == null) {
            return byUsername;
        }
        if (byUsername == null) {
            return byUuid;
        }
        if (byUuid.occurredAt == null) {
            return byUsername;
        }
        if (byUsername.occurredAt == null) {
            return byUuid;
        }
        return byUuid.occurredAt.isAfter(byUsername.occurredAt) ? byUuid : byUsername;
    }

    private AccountRecord mergeAccountRecord(@Nullable AccountRecord existing, String username, String uuidText, String platform, boolean whitelisted, Instant occurredAt) {
        return new AccountRecord(
            username != null && !username.isBlank() ? username : existing == null ? null : existing.username,
            uuidText != null && !uuidText.isBlank() ? uuidText : existing == null ? null : existing.uuid,
            platform,
            whitelisted,
            occurredAt
        );
    }

    private void putBlacklist(BlacklistRecord record) {
        BlacklistRecord existing = findStoredBlacklist(record.uuid, record.username);
        if (existing != null && existing.key() != null) {
            blacklistByKey.remove(existing.key());
        }
        blacklistByKey.put(record.key(), record);
        saveBlacklistState();
    }

    private BlacklistRecord findStoredBlacklist(String uuidText, String username) {
        BlacklistRecord byUuid = null;
        if (uuidText != null && !uuidText.isBlank()) {
            byUuid = blacklistByKey.get("uuid:" + uuidText.toLowerCase());
        }

        BlacklistRecord byUsername = null;
        if (username != null && !username.isBlank()) {
            byUsername = blacklistByKey.get("username:" + username.toLowerCase());
        }

        if (byUuid == null) {
            return byUsername;
        }
        if (byUsername == null) {
            return byUuid;
        }
        if (byUuid.occurredAt == null) {
            return byUsername;
        }
        if (byUsername.occurredAt == null) {
            return byUuid;
        }
        return byUuid.occurredAt.isAfter(byUsername.occurredAt) ? byUuid : byUsername;
    }

    private BlacklistRecord findBlacklist(UUID uuid, String username) {
        String normalizedUuid = uuid == null ? null : normalize(uuid);
        if (normalizedUuid != null) {
            BlacklistRecord byUuid = blacklistByKey.get("uuid:" + normalizedUuid);
            if (byUuid != null && byUuid.isActive()) {
                return byUuid;
            }
        }

        if (username != null && !username.isBlank()) {
            BlacklistRecord byUsername = blacklistByKey.get("username:" + username.toLowerCase());
            if (byUsername != null && byUsername.isActive()) {
                return byUsername;
            }
        }

        return null;
    }

    private PenaltyStateRecord findActivePenalty(UUID uuid, String username, String type) {
        List<PenaltyStateRecord> activePenalties = findActivePenalties(uuid, username, type);
        return activePenalties.isEmpty() ? null : activePenalties.getFirst();
    }

    boolean hasActiveBan(UUID uuid, @Nullable String username) {
        return findActivePenalty(uuid, username, "ban") != null;
    }

    private List<PenaltyStateRecord> findActivePenalties(UUID uuid, String username, String type) {
        List<PenaltyStateRecord> matches = new ArrayList<>();
        for (PenaltyStateRecord record : penaltiesByKey.values()) {
            if (record.isInactive() || !record.type.equalsIgnoreCase(type)) {
                continue;
            }
            if (matchesPenalty(record, uuid, username)) {
                matches.add(record);
            }
        }

        matches.sort((left, right) -> {
            Instant leftCreatedAt = left.createdAt == null ? Instant.EPOCH : left.createdAt;
            Instant rightCreatedAt = right.createdAt == null ? Instant.EPOCH : right.createdAt;
            int createdAtCompare = rightCreatedAt.compareTo(leftCreatedAt);
            if (createdAtCompare != 0) {
                return createdAtCompare;
            }
            return Objects.requireNonNullElse(left.key(), "").compareTo(Objects.requireNonNullElse(right.key(), ""));
        });
        return matches;
    }

    private PenaltyStateRecord findPenalty(UUID uuid, String username, String type) {
        PenaltyStateRecord bestMatch = null;
        for (PenaltyStateRecord record : penaltiesByKey.values()) {
            if (!record.type.equalsIgnoreCase(type)) {
                continue;
            }
            if (!matchesPenalty(record, uuid, username)) {
                continue;
            }
            if (bestMatch == null) {
                bestMatch = record;
                continue;
            }
            if (record.createdAt != null && (bestMatch.createdAt == null || record.createdAt.isAfter(bestMatch.createdAt))) {
                bestMatch = record;
            }
        }
        return bestMatch;
    }

    private boolean isBlacklisted(UUID uuid, String username) {
        return findBlacklist(uuid, username) != null;
    }

    private boolean isWhitelistedByAccountState(UUID uuid, String username, String platform) {
        if (!config().getBoolean("enforce_account_whitelist", true)) {
            return PlayerUtil.isWhitelistedVanilla(uuid, username);
        }

        AccountRecord account = findAccount(uuid, username, platform);
        if (account != null) {
            return account.whitelisted;
        }

        return false;
    }

    boolean isWhitelisted(@Nullable UUID uuid, @Nullable String username, @Nullable String platform) {
        return isWhitelistedByAccountState(uuid, username, platform);
    }

    private AccountRecord findAccount(UUID uuid, String username, String platform) {
        String normalizedPlatform = normalizePlatform(platform);
        String normalizedUuid = uuid == null ? null : normalize(uuid);
        if (normalizedUuid != null) {
            AccountRecord byUuid = accountsByUuid.get(accountUuidKey(normalizedUuid, normalizedPlatform));
            if (byUuid != null && platformMatches(byUuid.platform, platform)) {
                return byUuid;
            }

            // Pre-login platform detection can be ambiguous for Floodgate players.
            // If direct platform lookup misses, try the alternate platform key.
            String altPlatform = PLATFORM_BEDROCK.equalsIgnoreCase(normalizedPlatform) ? PLATFORM_JAVA : PLATFORM_BEDROCK;
            AccountRecord byUuidAlt = accountsByUuid.get(accountUuidKey(normalizedUuid, altPlatform));
            if (byUuidAlt != null) {
                return byUuidAlt;
            }
        }

        if (username == null || username.isBlank()) {
            return null;
        }
        AccountRecord byUsername = accountsByUsername.get(accountUsernameKey(username, normalizedPlatform));
        if (byUsername != null && platformMatches(byUsername.platform, platform)) {
            return byUsername;
        }

        // Fallback for Floodgate prefixed names where login-time platform resolution may not
        // yet be definitive in AsyncPlayerPreLoginEvent.
        String altPlatform = PLATFORM_BEDROCK.equalsIgnoreCase(normalizedPlatform) ? PLATFORM_JAVA : PLATFORM_BEDROCK;
        AccountRecord byUsernameAlt = accountsByUsername.get(accountUsernameKey(username, altPlatform));
        if (byUsernameAlt != null) {
            return byUsernameAlt;
        }

        if (looksLikePrefixedBedrockUsername(username)) {
            return accountsByUsername.get(accountUsernameKey(username, PLATFORM_BEDROCK));
        }

        return null;
    }

    private boolean matchesPenalty(PenaltyStateRecord record, UUID uuid, String username) {
        if (record.uuid != null) {
            if (uuid == null) {
                return false;
            }
            return record.uuid.equalsIgnoreCase(normalize(uuid));
        }
        return record.username != null && record.username.equalsIgnoreCase(username);
    }

    private boolean matchesBlacklist(BlacklistRecord record, UUID uuid, String username) {
        if (!record.isActive()) {
            return false;
        }
        if (record.uuid != null) {
            if (uuid == null) {
                return false;
            }
            return record.uuid.equalsIgnoreCase(normalize(uuid));
        }
        return record.username != null && record.username.equalsIgnoreCase(username);
    }

    private String messageForBlacklist(UUID uuid, String username) {
        BlacklistRecord blacklist = findBlacklist(uuid, username);
        if (blacklist == null || blacklist.reason == null || blacklist.reason.isBlank()) {
            return "You are blacklisted from this server.";
        }
        return blacklist.reason;
    }

    private String messageForPenalty(PenaltyStateRecord penalty, String fallback) {
        StringBuilder message = new StringBuilder();
        message.append(fallback);

        if (penalty.reason != null && !penalty.reason.isBlank()) {
            message.append("\nReason: ").append(penalty.reason);
        }

        if (!penalty.permanent && penalty.endsAt != null) {
            long remainingSeconds = Duration.between(Instant.now(), penalty.endsAt).getSeconds();
            if (remainingSeconds > 0L) {
                message.append("\nRemaining: ").append(formatRemainingDuration(remainingSeconds));
            }
        }

        return message.toString();
    }

    private String formatRemainingDuration(long remainingSeconds) {
        return TimeUtil.formatDuration(remainingSeconds);
    }

    private UUID parseUuid(String uuidText) {
        if (uuidText == null || uuidText.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuidText);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void loadAccountsFromConfig() {
        accountsByUsername.clear();
        accountsByUuid.clear();
        api.database().queryEach(
            "SELECT username, uuid, platform, is_whitelisted, occurred_at FROM webhook_accounts",
            null,
            rs -> putAccount(
                new AccountRecord(
                    rs.getString("username"),
                    rs.getString("uuid"),
                    normalizePlatform(rs.getString("platform")),
                    rs.getInt("is_whitelisted") == 1,
                    parseInstant(rs.getString("occurred_at"))
                ),
                false
            )
        );
    }

    private void saveAccountState() {
        api.database().update("DELETE FROM webhook_accounts", null);
        for (AccountRecord record : allAccountRecords()) {
            api.database().update(
                "INSERT INTO webhook_accounts (record_key, username, uuid, platform, is_whitelisted, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, Objects.requireNonNullElse(accountKey(record.uuid, record.username, record.platform), UUID.randomUUID().toString()));
                    ps.setString(2, record.username);
                    ps.setString(3, record.uuid);
                    ps.setString(4, record.platform);
                    ps.setInt(5, record.whitelisted ? 1 : 0);
                    ps.setString(6, record.occurredAt == null ? null : record.occurredAt.toString());
                }
            );
        }
    }

    private void loadBlacklistFromConfig() {
        blacklistByKey.clear();
        api.database().queryEach(
            "SELECT username, uuid, reason, starts_at, ends_at, is_permanent, occurred_at FROM webhook_blacklist",
            null,
            rs -> {
            BlacklistRecord record = new BlacklistRecord(
                rs.getString("username"),
                rs.getString("uuid"),
                rs.getString("reason"),
                parseInstant(rs.getString("starts_at")),
                parseInstant(rs.getString("ends_at")),
                rs.getInt("is_permanent") == 1,
                parseInstant(rs.getString("occurred_at"))
            );

            if (record.key() != null) {
                blacklistByKey.put(record.key(), record);
            }
            }
        );
    }

    private void saveBlacklistState() {
        api.database().update("DELETE FROM webhook_blacklist", null);
        for (BlacklistRecord record : blacklistByKey.values()) {
            api.database().update(
                "INSERT INTO webhook_blacklist (record_key, username, uuid, reason, starts_at, ends_at, is_permanent, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, Objects.requireNonNullElse(record.key(), UUID.randomUUID().toString()));
                    ps.setString(2, record.username);
                    ps.setString(3, record.uuid);
                    ps.setString(4, record.reason);
                    ps.setString(5, record.startsAt == null ? null : record.startsAt.toString());
                    ps.setString(6, record.endsAt == null ? null : record.endsAt.toString());
                    ps.setInt(7, record.permanent ? 1 : 0);
                    ps.setString(8, record.occurredAt == null ? null : record.occurredAt.toString());
                }
            );
        }
    }

    private void loadPenaltiesFromConfig() {
        penaltiesByKey.clear();
        api.database().queryEach(
            "SELECT external_id, username, uuid, type, reason, created_at, ends_at, is_permanent, lifted_at FROM webhook_penalties",
            null,
            rs -> {
            PenaltyStateRecord record = new PenaltyStateRecord(
                rs.getString("external_id"),
                rs.getString("username"),
                rs.getString("uuid"),
                Objects.requireNonNullElse(rs.getString("type"), "ban"),
                rs.getString("reason"),
                parseInstant(rs.getString("created_at")),
                parseInstant(rs.getString("ends_at")),
                rs.getInt("is_permanent") == 1,
                parseInstant(rs.getString("lifted_at"))
            );

            if (record.key() != null) {
                penaltiesByKey.put(record.key(), record);
            }
            }
        );
    }

    private void savePenaltyState() {
        api.database().update("DELETE FROM webhook_penalties", null);
        for (PenaltyStateRecord record : penaltiesByKey.values()) {
            api.database().update(
                "INSERT INTO webhook_penalties (record_key, external_id, username, uuid, type, reason, created_at, ends_at, is_permanent, lifted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, Objects.requireNonNullElse(record.key(), UUID.randomUUID().toString()));
                    ps.setString(2, record.externalId);
                    ps.setString(3, record.username);
                    ps.setString(4, record.uuid);
                    ps.setString(5, record.type);
                    ps.setString(6, record.reason);
                    ps.setString(7, record.createdAt == null ? null : record.createdAt.toString());
                    ps.setString(8, record.endsAt == null ? null : record.endsAt.toString());
                    ps.setInt(9, record.permanent ? 1 : 0);
                    ps.setString(10, record.liftedAt == null ? null : record.liftedAt.toString());
                }
            );
        }
    }

    private void restoreLocalAccessState() {
        Set<String> appliedAccounts = new LinkedHashSet<>();
        for (AccountRecord account : allAccountRecords()) {
            String key = accountKey(account.uuid, account.username, account.platform);
            if (key != null && appliedAccounts.add(key)) {
                applyWhitelist(account, account.whitelisted);
            }
        }
    }

    private boolean isBridgeEnabled() {
        return config().getBoolean("enabled", false);
    }

    boolean isWhitelistEnforcementActive() {
        return isBridgeEnabled() && config().getBoolean("enforce_account_whitelist", true);
    }

    private void ensureStateStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS webhook_accounts (" +
            "record_key TEXT PRIMARY KEY," +
            "username TEXT," +
            "uuid TEXT," +
            "platform TEXT," +
            "is_whitelisted INTEGER NOT NULL DEFAULT 0," +
            "occurred_at TEXT" +
            ");"
        );
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS webhook_blacklist (" +
            "record_key TEXT PRIMARY KEY," +
            "username TEXT," +
            "uuid TEXT," +
            "reason TEXT," +
            "starts_at TEXT," +
            "ends_at TEXT," +
            "is_permanent INTEGER NOT NULL DEFAULT 0," +
            "occurred_at TEXT" +
            ");"
        );
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS webhook_penalties (" +
            "record_key TEXT PRIMARY KEY," +
            "external_id TEXT," +
            "username TEXT," +
            "uuid TEXT," +
            "type TEXT," +
            "reason TEXT," +
            "created_at TEXT," +
            "ends_at TEXT," +
            "is_permanent INTEGER NOT NULL DEFAULT 0," +
            "lifted_at TEXT" +
            ");"
        );
    }

    private void migrateLegacyStateIfNeeded() {
        if (api.database().migrationVersion("webhook-bridge-state") >= 1) {
            return;
        }

        accountsByUsername.clear();
        accountsByUuid.clear();
        blacklistByKey.clear();
        penaltiesByKey.clear();

        if (this.accountStateConfig != null && this.accountStateConfig.isSection("accounts")) {
            ConfigSection section = this.accountStateConfig.getSection("accounts", false);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigSection accountSection = section.getSection(key, false);
                    if (accountSection == null) continue;
                    putAccount(new AccountRecord(
                        accountSection.getString("username"),
                        accountSection.getString("uuid"),
                        normalizePlatform(accountSection.getString("platform")),
                        accountSection.getBoolean("is_whitelisted", false),
                        parseInstant(accountSection.getString("occurred_at"))
                    ), false);
                }
            }
        }

        if (config().isSection("state.accounts")) {
            ConfigSection section = config().getSection("state.accounts", false);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigSection accountSection = section.getSection(key, false);
                    if (accountSection == null) continue;
                    putAccount(new AccountRecord(
                        accountSection.getString("username"),
                        accountSection.getString("uuid"),
                        normalizePlatform(accountSection.getString("platform")),
                        accountSection.getBoolean("is_whitelisted", false),
                        parseInstant(accountSection.getString("occurred_at"))
                    ), false);
                }
            }
        }

        if (config().isSection("state.blacklist")) {
            ConfigSection section = config().getSection("state.blacklist", false);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigSection blacklistSection = section.getSection(key, false);
                    if (blacklistSection == null) continue;
                    BlacklistRecord record = new BlacklistRecord(
                        blacklistSection.getString("username"),
                        blacklistSection.getString("uuid"),
                        blacklistSection.getString("reason"),
                        parseInstant(blacklistSection.getString("starts_at")),
                        parseInstant(blacklistSection.getString("ends_at")),
                        blacklistSection.getBoolean("is_permanent", false),
                        parseInstant(blacklistSection.getString("occurred_at"))
                    );
                    if (record.key() != null) {
                        blacklistByKey.put(record.key(), record);
                    }
                }
            }
        }

        if (config().isSection("state.penalties")) {
            ConfigSection section = config().getSection("state.penalties", false);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigSection penaltySection = section.getSection(key, false);
                    if (penaltySection == null) continue;
                    PenaltyStateRecord record = new PenaltyStateRecord(
                        penaltySection.getString("external_id"),
                        penaltySection.getString("username"),
                        penaltySection.getString("uuid"),
                        Objects.requireNonNullElse(penaltySection.getString("type"), "ban"),
                        penaltySection.getString("reason"),
                        parseInstant(penaltySection.getString("created_at")),
                        parseInstant(penaltySection.getString("ends_at")),
                        penaltySection.getBoolean("is_permanent", false),
                        parseInstant(penaltySection.getString("lifted_at"))
                    );
                    if (record.key() != null) {
                        penaltiesByKey.put(record.key(), record);
                    }
                }
            }
        }

        saveAccountState();
        saveBlacklistState();
        savePenaltyState();

        if (this.accountStateConfig != null) {
            this.accountStateConfig.remove("accounts");
            this.accountStateConfig.save();
        }
        config().remove("state.accounts");
        config().remove("state.blacklist");
        config().remove("state.penalties");
        saveConfig();

        api.database().setMigrationVersion("webhook-bridge-state", 1);
    }

    private String getSharedSecret() {
        return config().getString("shared_secret", "").trim();
    }

    private String preview(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }

    private boolean isTimestampFresh(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }

        try {
            long timestampSeconds = Long.parseLong(timestamp);
            long maxSkew = Math.max(30L, config().getLong("replay_window_seconds", 300L));
            return Math.abs(Instant.now().getEpochSecond() - timestampSeconds) <= maxSkew;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }

        return MessageDigest.isEqual(
            left.getBytes(StandardCharsets.UTF_8),
            right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean registerInboundDelivery(String deliveryId) {
        pruneRecentInboundDeliveries();
        if (deliveryId == null || deliveryId.isBlank()) {
            return false;
        }

        Instant expiresAt = Instant.now().plusSeconds(Math.max(30L, config().getLong("replay_window_seconds", 300L)));
        return recentInboundDeliveries.putIfAbsent(deliveryId, expiresAt) == null;
    }

    private void pruneRecentInboundDeliveries() {
        Instant now = Instant.now();
        recentInboundDeliveries.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().isAfter(now));
    }

    private boolean shouldDropStaleSyncManagedEvent(JsonObject payload) {
        if (!config().getBoolean("drop_stale_sync_managed_events", true)) {
            return false;
        }

        Instant cutoff = this.syncStateCutoff;
        if (cutoff == null) {
            return false;
        }

        String event = stringValue(payload, "event");
        if (!isSyncManagedEvent(event)) {
            return false;
        }

        Instant occurredAt = parseInboundOccurredAt(payload);
        if (occurredAt == null) {
            return config().getBoolean("drop_sync_managed_events_missing_occurred_at", false);
        }

        return occurredAt.isBefore(cutoff);
    }

    private static boolean isSyncManagedEvent(String event) {
        return "player.profile.created".equals(event)
            || "player.profile.deleted".equals(event)
            || "player.penalty.created".equals(event)
            || "player.penalty.updated".equals(event)
            || "player.penalty.deleted".equals(event)
            || "account.sync".equals(event)
            || "account.remove".equals(event)
            || "blacklist.sync".equals(event)
            || "blacklist.remove".equals(event)
            || "player.penalty.lifted".equals(event);
    }

    private static Instant parseInboundOccurredAt(JsonObject payload) {
        Instant topLevel = parseInstant(nullableString(payload, "occurred_at"));
        if (topLevel != null) {
            return topLevel;
        }

        JsonObject account = objectValue(payload, "account");
        if (account != null) {
            Instant accountOccurredAt = parseInstant(nullableString(account, "occurred_at"));
            if (accountOccurredAt != null) {
                return accountOccurredAt;
            }
        }

        JsonObject blacklist = objectValue(payload, "blacklist");
        if (blacklist != null) {
            return parseInstant(nullableString(blacklist, "occurred_at"));
        }

        return null;
    }

    private static Instant requireOccurredAt(JsonObject payload) {
        Instant occurredAt = parseInboundOccurredAt(payload);
        if (occurredAt == null) {
            throw new IllegalArgumentException("Missing or invalid occurred_at");
        }
        return occurredAt;
    }

    private static SyncSnapshot parseSyncSnapshot(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!booleanValue(root, "ok", false)) {
                return null;
            }

            JsonObject sync = requiredSyncObject(root);
            return new SyncSnapshot(
                requireString(sync, "mode"),
                parseInstant(nullableString(sync, "as_of")),
                parseAccounts(requiredArray(sync, "accounts")),
                parsePenalties(requiredArray(sync, "penalties")),
                parseLegacyBlacklist(requiredArray(sync, "legacy_blacklist"))
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<AccountSnapshot> parseAccounts(Iterable<JsonElement> elements) {
        List<AccountSnapshot> accounts = new ArrayList<>();
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            accounts.add(new AccountSnapshot(
                nullableString(item, "uuid"),
                requireString(item, "username"),
                nullableString(item, "platform"),
                booleanValue(item, "is_whitelisted", false),
                parseInstant(nullableString(item, "occurred_at"))
            ));
        }
        return accounts;
    }

    private static List<PenaltySnapshot> parsePenalties(Iterable<JsonElement> elements) {
        List<PenaltySnapshot> penalties = new ArrayList<>();
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String externalId = nullableString(item, "penalty_key");
            if (externalId == null) {
                externalId = nullableString(item, "external_id");
            }
            Instant startedAt = parseInstant(nullableString(item, "started_at"));
            if (startedAt == null) {
                startedAt = parseInstant(nullableString(item, "occurred_at"));
            }
            Instant liftedAt = parseInstant(nullableString(item, "lifted_at"));
            if (liftedAt == null) {
                liftedAt = parseInstant(nullableString(item, "deleted_at"));
            }
            penalties.add(new PenaltySnapshot(
                externalId,
                nullableString(item, "uuid"),
                nullableString(item, "username"),
                requireString(item, "type").toLowerCase(),
                nullableString(item, "reason"),
                startedAt,
                parseInstant(nullableString(item, "ends_at")),
                booleanValue(item, "is_permanent", false),
                liftedAt
            ));
        }
        return penalties;
    }

    private static List<BlacklistSnapshot> parseLegacyBlacklist(Iterable<JsonElement> elements) {
        List<BlacklistSnapshot> legacyBlacklist = new ArrayList<>();
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            legacyBlacklist.add(new BlacklistSnapshot(
                nullableString(item, "uuid"),
                nullableString(item, "username"),
                nullableString(item, "reason"),
                parseInstant(nullableString(item, "starts_at")),
                parseInstant(nullableString(item, "ends_at")),
                booleanValue(item, "is_permanent", false),
                parseInstant(nullableString(item, "occurred_at"))
            ));
        }
        return legacyBlacklist;
    }

    private static JsonObject requiredSyncObject(JsonObject payload) {
        JsonElement element = payload.get("sync");
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Missing object: sync");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject objectOrSelf(JsonObject payload, String name) {
        JsonElement element = payload.get(name);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return payload;
    }

    private static Iterable<JsonElement> requiredArray(JsonObject payload, String name) {
        JsonElement element = payload.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Missing array: " + name);
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject payload, String name) {
        String value = stringValue(payload, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing string: " + name);
        }
        return value;
    }

    private static String stringValue(JsonObject payload, String name) {
        JsonElement element = payload.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static String nullableString(JsonObject payload, String name) {
        String value = stringValue(payload, name);
        return value == null || value.isBlank() ? null : value;
    }

    private static JsonObject objectValue(JsonObject payload, String name) {
        JsonElement element = payload.get(name);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static boolean booleanValue(JsonObject payload, String name, boolean fallback) {
        JsonElement element = payload.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static long durationSecondsValue(JsonObject payload) {
        JsonElement element = payload.get("duration_seconds");
        if (element == null || element.isJsonNull()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static double sanitizeTps(double[] samples, int index) {
        if (samples == null || index < 0 || index >= samples.length) {
            return 0.0D;
        }
        double value = samples[index];
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return PLATFORM_JAVA;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("bedrock")
            || normalized.equals("floodgate")
            || normalized.equals("geyser")
        ) {
            return PLATFORM_BEDROCK;
        }
        return PLATFORM_JAVA;
    }

    private static boolean platformMatches(String expectedPlatform, String actualPlatform) {
        return normalizePlatform(expectedPlatform).equalsIgnoreCase(normalizePlatform(actualPlatform));
    }

    private static String normalize(UUID uuid) {
        return uuid.toString().toLowerCase();
    }

    private static String accountKey(String uuid, String username, String platform) {
        String platformPrefix = normalizePlatform(platform).toLowerCase(Locale.ROOT) + ":";
        if (uuid != null && !uuid.isBlank()) {
            return platformPrefix + "uuid:" + uuid.toLowerCase(Locale.ROOT);
        }
        String normalizedUsername = normalizeAccountUsername(username, platform);
        if (normalizedUsername != null) {
            return platformPrefix + "username:" + normalizedUsername;
        }
        return null;
    }

    private static String accountUuidKey(String uuid, String platform) {
        return normalizePlatform(platform).toLowerCase(Locale.ROOT) + ":uuid:" + uuid.toLowerCase(Locale.ROOT);
    }

    private static String accountUsernameKey(String username, String platform) {
        String normalizedUsername = normalizeAccountUsername(username, platform);
        if (normalizedUsername == null) {
            return normalizePlatform(platform).toLowerCase(Locale.ROOT) + ":username:";
        }
        return normalizePlatform(platform).toLowerCase(Locale.ROOT) + ":username:" + normalizedUsername;
    }

    private Collection<AccountRecord> allAccountRecords() {
        Map<String, AccountRecord> unique = new LinkedHashMap<>();
        for (AccountRecord record : accountsByUsername.values()) {
            unique.put(accountIdentityKey(record), record);
        }
        for (AccountRecord record : accountsByUuid.values()) {
            unique.putIfAbsent(accountIdentityKey(record), record);
        }
        return unique.values();
    }

    private static String accountIdentityKey(AccountRecord record) {
        String platform = normalizePlatform(record.platform).toLowerCase(Locale.ROOT);
        if (record.uuid != null && !record.uuid.isBlank()) {
            return platform + ":uuid:" + record.uuid.toLowerCase(Locale.ROOT);
        }
        String normalizedUsername = normalizeAccountUsername(record.username, record.platform);
        if (normalizedUsername != null) {
            return platform + ":username:" + normalizedUsername;
        }
        return platform + ":anonymous";
    }

    private static String normalizeAccountUsername(String username, String platform) {
        if (username == null) {
            return null;
        }

        String out = username.trim().toLowerCase(Locale.ROOT);
        if (out.isBlank()) {
            return null;
        }

        int idx = 0;
        while (idx < out.length()) {
            char c = out.charAt(idx);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                break;
            }
            idx++;
        }
        if (idx > 0 && idx < out.length()) {
            out = out.substring(idx);
        }

        return out.isBlank() ? null : out;
    }

    private static String penaltyKey(JsonObject payload, String type) {
        String externalId = stringValue(payload, "penalty_key");
        if (externalId == null || externalId.isBlank()) {
            externalId = stringValue(payload, "external_id");
        }
        return penaltyKey(externalId, stringValue(payload, "uuid"), stringValue(payload, "username"), type);
    }

    private static String penaltyExternalId(JsonObject payload) {
        String value = stringValue(payload, "penalty_key");
        if (value == null || value.isBlank()) {
            value = stringValue(payload, "external_id");
        }
        return value;
    }

    private static String penaltyKey(String externalId, String uuid, String username, String type) {
        if (externalId != null && !externalId.isBlank()) {
            return "external:" + externalId;
        }
        if (uuid != null && !uuid.isBlank()) {
            return "type:" + type + ":uuid:" + uuid.toLowerCase();
        }
        if (username != null && !username.isBlank()) {
            return "type:" + type + ":username:" + username.toLowerCase();
        }
        return null;
    }

    private String platformFor(UUID uuid) {
        return PlayerUtil.isBedrock(uuid) ? PLATFORM_BEDROCK : PLATFORM_JAVA;
    }

    private String platformForLogin(UUID uuid, String username) {
        if (PlayerUtil.isBedrock(uuid)) {
            return PLATFORM_BEDROCK;
        }

        if (looksLikePrefixedBedrockUsername(username)) {
            return PLATFORM_BEDROCK;
        }

        return PLATFORM_JAVA;
    }

    private static boolean looksLikePrefixedBedrockUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String trimmed = username.trim();
        if (trimmed.isBlank()) {
            return false;
        }
        char first = trimmed.charAt(0);
        return !((first >= 'a' && first <= 'z')
            || (first >= 'A' && first <= 'Z')
            || (first >= '0' && first <= '9')
            || first == '_');
    }

    private static String sign(String body, String timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "\n" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign webhook payload", exception);
        }
    }

    private ConfigSection config() {
        return getConfigSection();
    }

    private Object handleWebhookRequest(WebServiceRequest request) {
        String deliveryId = request.firstHeader("X-Minecraft-Delivery-Id");
        String event = "<unknown>";
        try {
            if (!isBridgeEnabled()) {
                return logInboundAndJsonResponse(request, event, deliveryId, 503, "disabled", "{\"ok\":false,\"error\":\"bridge_disabled\"}");
            }
            if (getSharedSecret().isBlank()) {
                return logInboundAndJsonResponse(request, event, deliveryId, 503, "disabled", "{\"ok\":false,\"error\":\"missing_shared_secret\"}");
            }
            if (!"POST".equalsIgnoreCase(request.method())) {
                plugin.getLogger().warning("Rejected webhook request: method " + request.method() + " is not allowed for " + request.path());
                return logInboundAndJsonResponse(request, event, deliveryId, 405, "rejected", "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            }

            String body = request.bodyAsString();
            String timestamp = request.firstHeader("X-Minecraft-Timestamp");
            String signature = request.firstHeader("X-Minecraft-Signature");
            event = eventNameFromBody(body);

            if (!isTimestampFresh(timestamp)) {
                plugin.getLogger().warning("Rejected webhook request: stale or missing timestamp for delivery " + Objects.requireNonNullElse(deliveryId, "<missing>"));
                return logInboundAndJsonResponse(request, event, deliveryId, 403, "rejected", "{\"ok\":false,\"error\":\"stale_timestamp\"}");
            }

            String expected = sign(body, timestamp, getSharedSecret());
            if (signature == null || !secureEquals(expected, signature)) {
                plugin.getLogger().warning("Rejected webhook request: invalid signature for delivery " + Objects.requireNonNullElse(deliveryId, "<missing>"));
                return logInboundAndJsonResponse(request, event, deliveryId, 403, "rejected", "{\"ok\":false,\"error\":\"forbidden\"}");
            }
            if (!registerInboundDelivery(deliveryId)) {
                plugin.getLogger().warning("Rejected webhook request: replay detected for delivery " + Objects.requireNonNullElse(deliveryId, "<missing>"));
                return logInboundAndJsonResponse(request, event, deliveryId, 409, "rejected", "{\"ok\":false,\"error\":\"replay_detected\"}");
            }

            JsonObject payload = JsonParser.parseString(body).getAsJsonObject();
            event = Objects.requireNonNullElse(stringValue(payload, "event"), "<missing>");
            if (parseInboundOccurredAt(payload) == null) {
                plugin.getLogger().warning("Rejected webhook request: missing or invalid occurred_at for delivery " + Objects.requireNonNullElse(deliveryId, "<missing>"));
                return logInboundAndJsonResponse(request, event, deliveryId, 422, "rejected", "{\"ok\":false,\"error\":\"missing_occurred_at\"}");
            }
            if (shouldQueueSyncManagedEvent(payload)) {
                queueSyncManagedEvent(payload, deliveryId);
                triggerImmediateSync();
                return logInboundAndJsonResponse(request, event, deliveryId, 200, "queued", "{\"ok\":true,\"queued\":\"pending_sync\"}");
            }
            if (shouldDropStaleSyncManagedEvent(payload)) {
                return logInboundAndJsonResponse(request, event, deliveryId, 200, "ignored", "{\"ok\":true,\"ignored\":\"stale\"}");
            }

            Future<Object> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> handleInboundEvent(payload));
            Object response = future.get(10, TimeUnit.SECONDS);

            if (response instanceof Map<?, ?> responseMap) {
                return logInboundAndJsonResponse(request, event, deliveryId, 200, "accepted", GSON.toJson(responseMap));
            }
            return logInboundAndJsonResponse(request, event, deliveryId, 200, "accepted", "{\"ok\":true}");
        } catch (SecurityException exception) {
            plugin.getLogger().warning("Rejected webhook request: " + exception.getMessage());
            return logInboundAndJsonResponse(request, event, deliveryId, 403, "rejected", "{\"ok\":false,\"error\":\"forbidden\",\"message\":" + GSON.toJson(exception.getMessage()) + "}");
        } catch (JsonParseException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Rejected webhook request: invalid payload - " + exception.getMessage());
            return logInboundAndJsonResponse(request, event, deliveryId, 422, "rejected", "{\"ok\":false,\"error\":\"invalid_json\"}");
        } catch (Exception exception) {
            plugin.getLogger().warning("Webhook bridge inbound handling failed: " + exception.getMessage());
            return logInboundAndJsonResponse(request, event, deliveryId, 500, "failed", "{\"ok\":false,\"error\":\"server_error\"}");
        }
    }

    private Object logInboundAndJsonResponse(WebServiceRequest request, String event, String deliveryId, int status, String result, String body) {
        plugin.getLogger().info("[webhook-inbound] method=" + request.method()
            + " path=" + request.path()
            + " event=" + Objects.requireNonNullElse(event, "<unknown>")
            + " delivery=" + Objects.requireNonNullElse(deliveryId, "<missing>")
            + " status=" + status
            + " result=" + result);
        recordInboundHistory(request, event, deliveryId, status, result);
        return jsonResponse(status, body);
    }

    private String eventNameFromBody(String body) {
        if (body == null || body.isBlank()) {
            return "<missing>";
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return "<missing>";
            }
            String event = stringValue(root.getAsJsonObject(), "event");
            return event == null || event.isBlank() ? "<missing>" : event;
        } catch (JsonParseException ignored) {
            return "<missing>";
        }
    }

    private Map<String, Object> jsonResponse(int status, String body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responseCode", status);
        response.put("contentType", "application/json; charset=utf-8");
        response.put("body", body);
        return response;
    }

    private void recordSyncResult(String reason, SyncResult result) {
        this.lastSyncAt = Instant.now();
        this.lastSyncReason = reason == null || reason.isBlank() ? "unknown" : reason;
        this.lastSyncResult = result == null ? SyncResult.failure("Unknown result.") : result;
    }

    private void recordOutboundHistory(String event, String deliveryId, String target, int status, String result, @Nullable String detail) {
        WebhookHistoryEntry entry = new WebhookHistoryEntry(
            Instant.now(),
            "outbound",
            Objects.requireNonNullElse(event, "<unknown>"),
            Objects.requireNonNullElse(deliveryId, "<missing>"),
            Objects.requireNonNullElse(target, "<unset>"),
            status,
            Objects.requireNonNullElse(result, "unknown"),
            detail == null || detail.isBlank() ? null : preview(detail)
        );
        pushHistory(recentOutboundHistory, entry);
    }

    private void recordInboundHistory(WebServiceRequest request, String event, String deliveryId, int status, String result) {
        WebhookHistoryEntry entry = new WebhookHistoryEntry(
            Instant.now(),
            "inbound",
            Objects.requireNonNullElse(event, "<unknown>"),
            Objects.requireNonNullElse(deliveryId, "<missing>"),
            request.method() + " " + request.path() + " @ " + request.remoteAddress(),
            status,
            Objects.requireNonNullElse(result, "unknown"),
            null
        );
        pushHistory(recentInboundHistory, entry);
    }

    private void pushHistory(Deque<WebhookHistoryEntry> history, WebhookHistoryEntry entry) {
        synchronized (historyLock) {
            history.addFirst(entry);
            while (history.size() > HISTORY_LIMIT) {
                history.removeLast();
            }
        }
    }

    private List<WebhookHistoryEntry> recentHistory(Deque<WebhookHistoryEntry> history, int limit) {
        synchronized (historyLock) {
            List<WebhookHistoryEntry> entries = new ArrayList<>();
            int remaining = Math.max(0, limit);
            for (WebhookHistoryEntry entry : history) {
                if (remaining-- <= 0) {
                    break;
                }
                entries.add(entry);
            }
            return entries;
        }
    }

    private String describeLastSync() {
        if (lastSyncAt == null) {
            return "never";
        }
        return lastSyncAt + " [" + lastSyncReason + "] " + (lastSyncResult.ok ? "ok" : "failed: " + lastSyncResult.errorSummary);
    }

    private int pendingQueuedEvents() {
        synchronized (pendingSyncManagedEvents) {
            return pendingSyncManagedEvents.size();
        }
    }

    private String buildBridgeUrl() {
        if (plugin.web() == null) {
            return "<web service unavailable>";
        }
        return plugin.web().getPublicUrl() + config().getString("listen_path", "/stemcraft/webhook");
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private @NotNull List<String> configuredStatsSyncPeriods() {
        List<String> periods = new ArrayList<>();
        for (String value : config().getStringList("player_stats_sync_periods")) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String period = value.trim().toLowerCase(Locale.ROOT);
            if (!period.equals("day") && !period.equals("week") && !period.equals("month") && !period.equals("all")) {
                continue;
            }
            if (!periods.contains(period)) {
                periods.add(period);
            }
        }
        return periods.isEmpty() ? List.of("day", "week", "month", "all") : periods;
    }

    private @NotNull String pluginVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    private static void kickPlayer(@NotNull Player player, @NotNull String message) {
        player.kick(Component.text(message));
    }

    @SuppressWarnings("deprecation")
    private static void disallowAsyncLogin(
        @NotNull AsyncPlayerPreLoginEvent event,
        @NotNull AsyncPlayerPreLoginEvent.Result result,
        @NotNull String message
    ) {
        event.disallow(result, message);
    }

    @SuppressWarnings("deprecation")
    private static void disallowPlayerLogin(
        @NotNull PlayerLoginEvent event,
        @NotNull PlayerLoginEvent.Result result,
        @NotNull String message
    ) {
        event.disallow(result, message);
    }

    private String formatHistoryEntry(WebhookHistoryEntry entry) {
        StringBuilder out = new StringBuilder();
        out.append(entry.occurredAt())
            .append(" ")
            .append(entry.event())
            .append(" ")
            .append(entry.result());
        if (entry.status() > 0) {
            out.append(" status=").append(entry.status());
        }
        out.append(" delivery=").append(entry.deliveryId());
        out.append(" target=").append(entry.target());
        if (entry.detail() != null && !entry.detail().isBlank()) {
            out.append(" detail=").append(entry.detail());
        }
        return out.toString();
    }

    private record SyncResult(boolean ok, String errorSummary) {
        private static SyncResult success() {
            return new SyncResult(true, "");
        }

        private static SyncResult failure(String errorSummary) {
            return new SyncResult(false, errorSummary == null ? "unknown_error" : errorSummary);
        }
    }

    private record QueuedInboundEvent(String deliveryId, String eventName, JsonObject payload) {
    }

    private record WebhookHistoryEntry(
        Instant occurredAt,
        String direction,
        String event,
        String deliveryId,
        String target,
        int status,
        String result,
        @Nullable String detail
    ) {}

    private record SyncSnapshot(
        String mode,
        Instant asOf,
        List<AccountSnapshot> accounts,
        List<PenaltySnapshot> penalties,
        List<BlacklistSnapshot> legacyBlacklist
    ) {}

    private record AccountSnapshot(String uuid, String username, String platform, boolean whitelisted, Instant occurredAt) {}

    private record PenaltySnapshot(
        String externalId,
        String uuid,
        String username,
        String type,
        String reason,
        Instant startedAt,
        Instant endsAt,
        boolean permanent,
        Instant liftedAt
    ) {}

    private record BlacklistSnapshot(
        String uuid,
        String username,
        String reason,
        Instant startsAt,
        Instant endsAt,
        boolean permanent,
        Instant occurredAt
    ) {}

    private record AccountRecord(String username, String uuid, String platform, boolean whitelisted, Instant occurredAt) {
        private AccountRecord {
            uuid = uuid == null || uuid.isBlank() ? null : uuid.toLowerCase();
            platform = normalizePlatform(platform);
        }
    }

    private record BlacklistRecord(String username, String uuid, String reason, Instant startsAt, Instant endsAt,
                                   boolean permanent, Instant occurredAt) {
            private BlacklistRecord(String username, String uuid, String reason, Instant startsAt, Instant endsAt, boolean permanent, Instant occurredAt) {
                this.username = username == null || username.isBlank() ? null : username;
                this.uuid = uuid == null || uuid.isBlank() ? null : uuid.toLowerCase();
                this.reason = reason;
                this.startsAt = startsAt;
                this.endsAt = endsAt;
                this.permanent = permanent;
                this.occurredAt = occurredAt;
            }

            private String key() {
                if (this.uuid != null) {
                    return "uuid:" + this.uuid;
                }
                if (this.username != null) {
                    return "username:" + this.username.toLowerCase();
                }
                return null;
            }

            private boolean isActive() {
                Instant now = Instant.now();
                if (this.startsAt != null && this.startsAt.isAfter(now)) {
                    return false;
                }
                if (this.permanent) {
                    return true;
                }
                return this.endsAt == null || this.endsAt.isAfter(now);
            }
        }

    private record PenaltyStateRecord(String externalId, String username, String uuid, String type, String reason,
                                      Instant createdAt, Instant endsAt, boolean permanent, Instant liftedAt) {
            private PenaltyStateRecord(String externalId, String username, String uuid, String type, String reason, Instant createdAt, Instant endsAt, boolean permanent, Instant liftedAt) {
                this.externalId = externalId == null || externalId.isBlank() ? null : externalId;
                this.username = username == null || username.isBlank() ? null : username;
                this.uuid = uuid == null || uuid.isBlank() ? null : uuid.toLowerCase();
                this.type = type == null || type.isBlank() ? "ban" : type.toLowerCase();
                this.reason = reason;
                this.createdAt = createdAt;
                this.endsAt = endsAt;
                this.permanent = permanent;
                this.liftedAt = liftedAt;
            }

            private String key() {
                if (this.externalId != null) {
                    return "external:" + this.externalId;
                }
                if (this.uuid != null) {
                    return "type:" + this.type + ":uuid:" + this.uuid;
                }
                if (this.username != null) {
                    return "type:" + this.type + ":username:" + this.username.toLowerCase();
                }
                return null;
            }

            private boolean isInactive() {
                if ("kick".equals(this.type)) {
                    return true;
                }
                if (this.liftedAt != null) {
                    return true;
                }
                if (this.permanent) {
                    return false;
                }
                return this.endsAt == null || !this.endsAt.isAfter(Instant.now());
            }
        }
}
