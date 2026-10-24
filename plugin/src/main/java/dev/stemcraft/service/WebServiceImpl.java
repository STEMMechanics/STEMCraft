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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import dev.stemcraft.api.service.mailbox.MailSendResult;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.web.WebServiceEndpointHandler;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of the WebService for serving HTTP requests.
 */
public class WebServiceImpl extends BaseService implements WebService {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String MAINTENANCE_MESSAGE = "Server is under maintenance. Please try again later.";
    private static final String SURVIVAL_MAIL_PATH = "/api/v1/survival/mail";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_HTTP_REQUEST_BODY_BYTES = 2 * 1024 * 1024;
    private static final int DEFAULT_MAIL_API_BODY_BYTES = 32 * 1024;
    private static final int DEFAULT_MAIL_API_MAX_ITEMS = 26;
    private static final int DEFAULT_MAIL_API_MAX_ITEM_AMOUNT = 1024;
    private static final long DEFAULT_MAIL_API_MAX_DELAY_SECONDS = 7L * 24L * 60L * 60L;
    private static final int MAIL_API_KEY_BYTES = 32;
    private static final int MAIL_API_MAX_MESSAGE_LENGTH = 2048;
    private static final int MAIL_API_MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAIL_API_MAX_IDEMPOTENCY_ENTRIES = 1024;
    private static final long MAIL_API_IDEMPOTENCY_RETENTION_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long MAIL_API_SERVER_THREAD_TIMEOUT_SECONDS = 5L;
    private File wwwRoot;
    private HttpServer httpServer;
    private volatile Map<String, WebServiceEndpointHandler> endpointHandlers = Map.of();
    private volatile List<String> onlinePlayerNames = List.of();
    private final Object mailApiIdempotencyLock = new Object();
    private final Map<String, MailApiIdempotencyEntry> mailApiIdempotency = new LinkedHashMap<>();
    private String ip;
    private int port;
    private volatile boolean publicPlayersEnabled;
    private volatile boolean mailApiEnabled;
    private volatile String mailApiKey = "";
    private volatile String mailApiSender = "Discord Rewards";
    private volatile int mailApiMaxBodyBytes = DEFAULT_MAIL_API_BODY_BYTES;
    private volatile int mailApiMaxItems = DEFAULT_MAIL_API_MAX_ITEMS;
    private volatile int mailApiMaxItemAmount = DEFAULT_MAIL_API_MAX_ITEM_AMOUNT;
    private volatile long mailApiMaxDelaySeconds = DEFAULT_MAIL_API_MAX_DELAY_SECONDS;
    private boolean playerSnapshotListenersRegistered;
    private boolean playerSnapshotRefreshScheduled;

    /**
     * Constructor for the WebServiceImpl.
     * 
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public WebServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "web-server");
    }

    /**
     * Enable the web service if configured to do so.
     */
    public void onEnable() {
        loadSettings();

        if(getConfigSection().getBoolean("enabled", false)) {
            start();
        }

        api.commands().create("webserver")
                .description("WEB_SERVER_DESCRIPTION")
                .permission("stemcraft.command.webserver")
                .tabCompletion("start")
                .tabCompletion("stop")
                .tabCompletion("enable")
                .tabCompletion("disable")
                .tabCompletion("status")
                .usage("WEB_SERVER_USAGE")
                .executor((api, cmd, ctx) -> {
                String action = ctx.args().isEmpty() ? "status" : ctx.args().getFirst().toLowerCase(Locale.ROOT);
                switch (action) {
                    case "start" -> {
                        if(isRunning()) {
                            api.messages().error(ctx.getSender(), "WEB_SERVER_ALREADY_RUNNING");
                        } else {
                            start();
                            if (isRunning()) {
                                api.messages().success(ctx.getSender(), "WEB_SERVER_STARTED", "url", getPublicUrl());
                            } else {
                                api.messages().error(ctx.getSender(), "WEB_SERVER_START_FAILED_PLAYER");
                            }
                        }
                    }
                    case "stop" -> {
                        if(isRunning()) {
                            stop();
                            api.messages().success(ctx.getSender(), "WEB_SERVER_STOPPED");
                        } else {
                            api.messages().error(ctx.getSender(), "WEB_SERVER_NOT_RUNNING");
                        }
                    }
                    case "enable" -> {
                        getConfigSection().set("enabled", true);
                        saveConfig();

                        api.messages().info(ctx.getSender(), "WEB_SERVER_ENABLED", "state", isRunning() ? "{WEB_SERVER_STATE_RUNNING}" : "{WEB_SERVER_STATE_NOT_RUNNING}");
                    }
                    case "disable" -> {
                        getConfigSection().set("enabled", false);
                        saveConfig();

                        api.messages().info(ctx.getSender(), "WEB_SERVER_DISABLED", "state", isRunning() ? "{WEB_SERVER_STATE_RUNNING}" : "{WEB_SERVER_STATE_NOT_RUNNING}");
                    }
                    case "", "status" -> {
                        String publicUrl = getPublicUrl();
                        api.messages().info(ctx.getSender(), "WEB_SERVER_STATUS",
                            "enabled_disabled",
                            getConfigSection().getBoolean("enabled", false) ? "{WEB_SERVER_STATE_ENABLED}" : "{WEB_SERVER_STATE_DISABLED}",
                            "running_not",
                            isRunning() ? "{WEB_SERVER_STATE_RUNNING}" : "{WEB_SERVER_STATE_NOT_RUNNING}",
                            "ip", isRunning() ? httpServer.getAddress().getHostString() : ip,
                            "port", isRunning() ? httpServer.getAddress().getPort() : port,
                            "url", publicUrl);
                    }
                    default -> api.messages().info(ctx.getSender(), cmd.getUsage());
                }
            })
            .register(plugin);
    }

    /**
     * Check if the web server is currently running.
     *
     * @return true if the web server is running, false otherwise.
     */
    public boolean isRunning() {
        return httpServer != null;
    }

    /**
     * Start the web server.
     */
    public void start() {
        if (httpServer != null) {
            return;
        }

        loadSettings();
        refreshOnlinePlayerSnapshot();

        String wwwPath = getConfigSection().getString("path", "www");
        wwwPath = wwwPath
                .replace("\\", "/")
                .replaceAll("^/+", "")   // remove leading slashes
                .replaceAll("/+$", "")   // remove trailing slashes
                .replace("../", "")
                .replace("..", "");

        wwwRoot = new File(plugin.getDataFolder(), wwwPath);

        if (!wwwRoot.exists()) {
            if (!wwwRoot.mkdirs()) {
                api.messages().error("FAILED_CREATE_DIR");
                wwwRoot = null;
                return;
            }

        }

        if(!wwwRoot.isDirectory()) {
            api.messages().error("WEB_SERVER_PATH_NOT_DIR");
            wwwRoot = null;
            return;
        }

        port = getConfigSection().getInt("port", 8950);
        ip = getConfigSection().getString("ip", "127.0.0.1");

        try {
            httpServer = HttpServer.create(new InetSocketAddress(ip, port), 0);
            httpServer.createContext("/", new WebServiceHandler());
            httpServer.setExecutor(null);
            registerEndpointHandler("/status", this::handleStatusEndpoint);
            registerEndpointHandler(SURVIVAL_MAIL_PATH, this::handleSurvivalMailEndpoint);
            httpServer.start();
            registerPlayerSnapshotListeners();
            api.messages().info("WEB_SERVER_STARTED_ON", "ip", ip, "port", String.valueOf(httpServer.getAddress().getPort()));
        } catch (IOException | IllegalArgumentException e) {
            if (httpServer != null) {
                httpServer.stop(0);
            }
            httpServer = null;
            api.messages().error("WEB_SERVER_START_FAILED", e, "error", e.getMessage());
        }
    }

    /**
     * Stop the web server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            api.messages().info("WEB_SERVER_STOPPED");
        }
    }

    @Override
    public void onDisable() {
        stop();
        playerSnapshotRefreshScheduled = false;
        onlinePlayerNames = List.of();
    }

    /**
     * Register a custom endpoint handler.
     */
    public synchronized void registerEndpointHandler(@NotNull String path, @NotNull WebServiceEndpointHandler handler) {
        Map<String, WebServiceEndpointHandler> updated = new LinkedHashMap<>(endpointHandlers);
        updated.put(path, handler);
        endpointHandlers = Collections.unmodifiableMap(updated);
        api.messages().debug("WEB_SERVER_REGISTERED_ENDPOINT", "path", path);
    }

    Object handleStatusEndpoint(@NotNull WebServiceRequest request) {
        if (!"/status".equals(request.path())) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        boolean maintenance = plugin.isMaintenanceMode();
        List<String> currentPlayerNames = onlinePlayerNames;
        payload.put("online", true);
        payload.put("players_online", currentPlayerNames.size());
        payload.put("max_players", plugin.getServer().getMaxPlayers());
        payload.put("version", plugin.getServer().getMinecraftVersion());
        payload.put("maintenance", maintenance);
        payload.put("message", maintenance ? MAINTENANCE_MESSAGE : null);
        if (publicPlayersEnabled) {
            payload.put("players", currentPlayerNames);
        }
        payload.put("checked_at", OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responseCode", 200);
        response.put("contentType", "application/json; charset=utf-8");
        response.put("body", GSON.toJson(payload));
        return response;
    }

    Object handleSurvivalMailEndpoint(@NotNull WebServiceRequest request) {
        if (!SURVIVAL_MAIL_PATH.equals(request.path())) {
            return null;
        }

        loadSettings();
        if (!mailApiEnabled || mailApiKey.isBlank()) {
            return mailApiError(404, "Not found").asEndpointResponse();
        }

        if (!isMailApiAuthorised(request)) {
            return new MailApiResponse(
                401,
                GSON.toJson(Map.of("error", "Missing or invalid API key")),
                Map.of("WWW-Authenticate", "Bearer")
            ).asEndpointResponse();
        }

        if (!"POST".equalsIgnoreCase(request.method())) {
            return new MailApiResponse(
                405,
                GSON.toJson(Map.of("error", "Only POST is supported")),
                Map.of("Allow", "POST")
            ).asEndpointResponse();
        }

        String contentType = request.firstHeader("Content-Type");
        if (contentType != null && !contentType.isBlank()) {
            int separator = contentType.indexOf(';');
            String mediaType = (separator < 0 ? contentType : contentType.substring(0, separator)).trim();
            if (!"application/json".equalsIgnoreCase(mediaType)) {
                return mailApiError(415, "Content-Type must be application/json").asEndpointResponse();
            }
        }

        byte[] body = request.body();
        if (body.length > mailApiMaxBodyBytes) {
            return mailApiError(413, "Request body is too large").asEndpointResponse();
        }

        MailApiRequest mailRequest;
        try {
            mailRequest = parseMailApiRequest(body);
        } catch (MailApiValidationException exception) {
            return mailApiError(400, exception.getMessage()).asEndpointResponse();
        }

        String idempotencyKey = request.firstHeader("Idempotency-Key");
        if (idempotencyKey != null) {
            idempotencyKey = idempotencyKey.trim();
            if (idempotencyKey.isBlank() || idempotencyKey.length() > MAIL_API_MAX_IDEMPOTENCY_KEY_LENGTH) {
                return mailApiError(400, "Idempotency-Key must contain 1 to "
                    + MAIL_API_MAX_IDEMPOTENCY_KEY_LENGTH + " characters").asEndpointResponse();
            }
        }

        if (idempotencyKey == null) {
            return executeMailApiRequest(mailRequest).asEndpointResponse();
        }

        return executeIdempotentMailApiRequest(idempotencyKey, body, mailRequest);
    }

    private boolean isMailApiAuthorised(@NotNull WebServiceRequest request) {
        String authorization = request.firstHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }

        String providedKey = authorization.substring(BEARER_PREFIX.length()).trim();
        if (providedKey.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
            mailApiKey.getBytes(StandardCharsets.UTF_8),
            providedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Object executeIdempotentMailApiRequest(@NotNull String idempotencyKey,
                                                   byte @NotNull [] body,
                                                   @NotNull MailApiRequest mailRequest) {
        String fingerprint = hashBytes(body);
        MailApiIdempotencyEntry entry;
        boolean owner = false;

        synchronized (mailApiIdempotencyLock) {
            pruneMailApiIdempotency();
            entry = mailApiIdempotency.get(idempotencyKey);
            if (entry != null && !entry.fingerprint().equals(fingerprint)) {
                return mailApiError(409, "Idempotency-Key was already used for a different request")
                    .asEndpointResponse();
            }

            if (entry == null) {
                removeOldestCompletedMailApiIdempotencyEntry();
                if (mailApiIdempotency.size() >= MAIL_API_MAX_IDEMPOTENCY_ENTRIES) {
                    return mailApiError(503, "Too many requests are being processed")
                        .asEndpointResponse();
                }

                entry = new MailApiIdempotencyEntry(fingerprint);
                mailApiIdempotency.put(idempotencyKey, entry);
                owner = true;
            }
        }

        if (!owner) {
            try {
                return entry.result().get(MAIL_API_SERVER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .asEndpointResponse();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return mailApiError(503, "The original request is still being processed")
                    .asEndpointResponse();
            } catch (ExecutionException | TimeoutException exception) {
                return mailApiError(503, "The original request is still being processed")
                    .asEndpointResponse();
            }
        }

        try {
            MailApiResponse response = executeMailApiRequest(mailRequest);
            entry.result().complete(response);
            if (response.responseCode() != 202) {
                synchronized (mailApiIdempotencyLock) {
                    mailApiIdempotency.remove(idempotencyKey, entry);
                }
            }
            return response.asEndpointResponse();
        } catch (RuntimeException exception) {
            entry.result().completeExceptionally(exception);
            synchronized (mailApiIdempotencyLock) {
                mailApiIdempotency.remove(idempotencyKey, entry);
            }
            throw exception;
        }
    }

    private void pruneMailApiIdempotency() {
        long cutoff = System.currentTimeMillis() - MAIL_API_IDEMPOTENCY_RETENTION_MILLIS;
        Iterator<Map.Entry<String, MailApiIdempotencyEntry>> iterator = mailApiIdempotency.entrySet().iterator();
        while (iterator.hasNext()) {
            MailApiIdempotencyEntry entry = iterator.next().getValue();
            if (entry.createdAt() < cutoff && entry.result().isDone()) {
                iterator.remove();
            }
        }
    }

    private void removeOldestCompletedMailApiIdempotencyEntry() {
        if (mailApiIdempotency.size() < MAIL_API_MAX_IDEMPOTENCY_ENTRIES) {
            return;
        }

        Iterator<Map.Entry<String, MailApiIdempotencyEntry>> iterator = mailApiIdempotency.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().result().isDone()) {
                iterator.remove();
                return;
            }
        }
    }

    private MailApiResponse executeMailApiRequest(@NotNull MailApiRequest mailRequest) {
        if (Bukkit.isPrimaryThread()) {
            return processMailApiRequest(mailRequest);
        }

        try {
            return plugin.getServer().getScheduler()
                .callSyncMethod(plugin, () -> processMailApiRequest(mailRequest))
                .get(MAIL_API_SERVER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return mailApiError(503, "The server did not process the request in time");
        } catch (TimeoutException exception) {
            return mailApiError(503, "The server did not process the request in time");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return mailApiError(503, "The server could not process the request");
        }
    }

    private MailApiResponse processMailApiRequest(@NotNull MailApiRequest mailRequest) {
        if (api.mailboxes() == null || api.players() == null || api.items() == null) {
            return mailApiError(503, "The mailbox service is unavailable");
        }

        PlayerService.ResolvedPlayer recipient = api.players().resolveIdentity(mailRequest.player());
        if (recipient == null) {
            return mailApiError(404, "Player not found: " + mailRequest.player());
        }

        List<ItemStack> items = new ArrayList<>(mailRequest.items().size());
        try {
            for (MailApiItem item : mailRequest.items()) {
                items.add(createMailApiItem(item));
            }
        } catch (MailApiValidationException exception) {
            return mailApiError(400, exception.getMessage());
        }

        MailSendRequest sendRequest;
        if (mailRequest.delaySeconds() == null) {
            sendRequest = new MailSendRequest(mailApiSender, recipient.uuid(), mailRequest.message(), items);
        } else {
            long delayTicks;
            try {
                delayTicks = Math.multiplyExact(mailRequest.delaySeconds(), 20L);
            } catch (ArithmeticException exception) {
                return mailApiError(400, "delay_seconds is too large");
            }
            sendRequest = new MailSendRequest(
                mailApiSender, recipient.uuid(), mailRequest.message(), items, delayTicks);
        }

        MailSendResult result = api.mailboxes().send(sendRequest);
        if (!result.queued()) {
            String failureMessage = Objects.requireNonNullElse(result.message(), "");
            return mailApiError(422, failureMessage.isBlank() ? "Mail could not be queued" : failureMessage);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("queued", true);
        response.put("player", recipient.name());
        response.put("sender", mailApiSender);
        response.put("delay_seconds", mailRequest.delaySeconds());
        return mailApiJsonResponse(202, response);
    }

    private ItemStack createMailApiItem(@NotNull MailApiItem item) {
        ItemStack customItem;
        try {
            customItem = api.items().createCustomItem(item.id(), item.amount());
        } catch (IllegalArgumentException exception) {
            throw new MailApiValidationException("Invalid item '" + item.id() + "': " + exception.getMessage());
        }
        if (customItem != null) {
            return customItem;
        }

        String materialId = item.id();
        if (materialId.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            materialId = materialId.substring("minecraft:".length());
        }
        Material material = Material.matchMaterial(materialId.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            throw new MailApiValidationException("Unknown item: " + item.id());
        }
        return new ItemStack(material, item.amount());
    }

    private MailApiRequest parseMailApiRequest(byte @NotNull [] body) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new MailApiValidationException("Request body is not valid JSON");
        }
        if (!parsed.isJsonObject()) {
            throw new MailApiValidationException("Request body must be a JSON object");
        }

        JsonObject root = parsed.getAsJsonObject();
        String player = requiredText(root, "player");
        if (player.isBlank()) {
            throw new MailApiValidationException("player is required");
        }
        if (player.length() > 100) {
            throw new MailApiValidationException("player is too long");
        }

        String message = optionalText(root, "message");
        if (message.codePointCount(0, message.length()) > MAIL_API_MAX_MESSAGE_LENGTH) {
            throw new MailApiValidationException("message must be at most " + MAIL_API_MAX_MESSAGE_LENGTH + " characters");
        }

        List<MailApiItem> items = new ArrayList<>();
        JsonElement itemsElement = root.get("items");
        if (itemsElement != null && !itemsElement.isJsonNull()) {
            if (!itemsElement.isJsonArray()) {
                throw new MailApiValidationException("items must be an array");
            }
            JsonArray itemArray = itemsElement.getAsJsonArray();
            if (itemArray.size() > mailApiMaxItems) {
                throw new MailApiValidationException("items may contain at most " + mailApiMaxItems + " entries");
            }
            for (JsonElement itemElement : itemArray) {
                if (!itemElement.isJsonObject()) {
                    throw new MailApiValidationException("Each item must be an object");
                }
                JsonObject itemObject = itemElement.getAsJsonObject();
                String id = requiredText(itemObject, "id").trim();
                if (id.isBlank()) {
                    throw new MailApiValidationException("Each item requires an id");
                }
                if (id.length() > 256) {
                    throw new MailApiValidationException("Item id is too long");
                }
                long amount = requiredLong(itemObject, "amount");
                if (amount < 1 || amount > mailApiMaxItemAmount || amount > Integer.MAX_VALUE) {
                    throw new MailApiValidationException("Item amount must be between 1 and " + mailApiMaxItemAmount);
                }
                items.add(new MailApiItem(id, (int) amount));
            }
        }

        Long delaySeconds = null;
        JsonElement delayElement = root.get("delay_seconds");
        if (delayElement != null && !delayElement.isJsonNull()) {
            long value = requiredLong(root, "delay_seconds");
            if (value < 0 || value > mailApiMaxDelaySeconds) {
                throw new MailApiValidationException(
                    "delay_seconds must be between 0 and " + mailApiMaxDelaySeconds);
            }
            delaySeconds = value;
        }

        return new MailApiRequest(player.trim(), message, List.copyOf(items), delaySeconds);
    }

    private static String requiredText(@NotNull JsonObject object, @NotNull String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()
            || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new MailApiValidationException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static String optionalText(@NotNull JsonObject object, @NotNull String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return "";
        }
        return requiredText(object, field);
    }

    private static long requiredLong(@NotNull JsonObject object, @NotNull String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()
            || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new MailApiValidationException(field + " must be an integer");
        }
        try {
            return Long.parseLong(value.getAsString());
        } catch (NumberFormatException exception) {
            throw new MailApiValidationException(field + " must be an integer");
        }
    }

    private static MailApiResponse mailApiJsonResponse(int responseCode, @NotNull Map<String, ?> payload) {
        return new MailApiResponse(responseCode, GSON.toJson(payload), Map.of());
    }

    private static MailApiResponse mailApiError(int responseCode, @NotNull String message) {
        return mailApiJsonResponse(responseCode, Map.of("error", message));
    }

    private static String hashBytes(byte @NotNull [] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record MailApiRequest(String player,
                                  String message,
                                  List<MailApiItem> items,
                                  Long delaySeconds) {}

    private record MailApiItem(String id, int amount) {}

    private static final class MailApiValidationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private MailApiValidationException(@NotNull String message) {
            super(message);
        }
    }

    private static final class MailApiIdempotencyEntry {
        private final String fingerprint;
        private final long createdAt = System.currentTimeMillis();
        private final CompletableFuture<MailApiResponse> result = new CompletableFuture<>();

        private MailApiIdempotencyEntry(@NotNull String fingerprint) {
            this.fingerprint = fingerprint;
        }

        private String fingerprint() {
            return fingerprint;
        }

        private long createdAt() {
            return createdAt;
        }

        private CompletableFuture<MailApiResponse> result() {
            return result;
        }
    }

    private record MailApiResponse(int responseCode,
                                   String body,
                                   Map<String, String> headers) {
        private MailApiResponse {
            headers = Map.copyOf(headers);
        }

        private Map<String, Object> asEndpointResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("responseCode", responseCode);
            response.put("contentType", "application/json; charset=utf-8");
            if (!headers.isEmpty()) {
                response.put("headers", headers);
            }
            response.put("body", body);
            return response;
        }
    }

    /**
     * Get the public URL of the web server.
     */
    public @NotNull String getPublicUrl() {
        loadSettings();

        String publicUrl = getConfigSection().getString("public-url", "").trim();
        if(!publicUrl.isEmpty()) {
            if(!publicUrl.startsWith("http://") && !publicUrl.startsWith("https://")) {
                publicUrl = "http://" + publicUrl;
            }

            if(publicUrl.endsWith("/")) {
                publicUrl = publicUrl.substring(0, publicUrl.length() - 1);
            }

            return publicUrl;
        }

        String host = isRunning() ? httpServer.getAddress().getHostString() : ip;

        if ("0.0.0.0".equals(host) || "::".equals(host)) {
            host = findBestLocalAddress();
        }

        if (host.contains(":")) host = "[" + host + "]";
        int listeningPort = isRunning() ? httpServer.getAddress().getPort() : port;
        return "http://" + host + ":" + listeningPort;
    }

    void loadSettings() {
        port = getConfigSection().getInt("port", 8950);
        ip = getConfigSection().getString("ip", "127.0.0.1");
        publicPlayersEnabled = getConfigSection().getBoolean("status.players.enabled", false);
        mailApiEnabled = getConfigSection().getBoolean("mail-api.enabled", false);
        String configuredMailApiKey = Objects.requireNonNullElse(
            getConfigSection().getString("mail-api.key", ""), "").trim();
        if (!configuredMailApiKey.isBlank()) {
            mailApiKey = configuredMailApiKey;
        } else if (mailApiKey.isBlank()) {
            mailApiKey = generateMailApiKey();
            getConfigSection().set("mail-api.key", mailApiKey);
            saveConfig();
            java.util.logging.Logger logger = plugin.getLogger();
            if (logger != null) {
                logger.info(
                    "[web-server] Generated a new mail-api key and saved it to config.yml. Keep this key secret.");
            }
        }
        mailApiSender = Objects.requireNonNullElse(
            getConfigSection().getString("mail-api.sender", "Discord Rewards"), "Discord Rewards").trim();
        if (mailApiSender.isBlank()) {
            mailApiSender = "Discord Rewards";
        }

        int configuredBodyBytes = getConfigSection().getInt("mail-api.max-body-bytes", DEFAULT_MAIL_API_BODY_BYTES);
        mailApiMaxBodyBytes = configuredBodyBytes > 0 ? configuredBodyBytes : DEFAULT_MAIL_API_BODY_BYTES;

        int configuredMaxItems = getConfigSection().getInt("mail-api.max-items", DEFAULT_MAIL_API_MAX_ITEMS);
        mailApiMaxItems = configuredMaxItems > 0 ? configuredMaxItems : DEFAULT_MAIL_API_MAX_ITEMS;

        int configuredMaxItemAmount = getConfigSection().getInt(
            "mail-api.max-item-amount", DEFAULT_MAIL_API_MAX_ITEM_AMOUNT);
        mailApiMaxItemAmount = configuredMaxItemAmount > 0
            ? configuredMaxItemAmount : DEFAULT_MAIL_API_MAX_ITEM_AMOUNT;

        long configuredMaxDelaySeconds = getConfigSection().getLong(
            "mail-api.max-delay-seconds", DEFAULT_MAIL_API_MAX_DELAY_SECONDS);
        mailApiMaxDelaySeconds = configuredMaxDelaySeconds >= 0
            ? configuredMaxDelaySeconds : DEFAULT_MAIL_API_MAX_DELAY_SECONDS;
    }

    private static String generateMailApiKey() {
        byte[] bytes = new byte[MAIL_API_KEY_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    void registerPlayerSnapshotListeners() {
        if (playerSnapshotListenersRegistered) {
            return;
        }

        api.events().register(PlayerJoinEvent.class, event -> scheduleOnlinePlayerSnapshotRefresh());
        api.events().register(PlayerQuitEvent.class, event -> scheduleOnlinePlayerSnapshotRefresh());
        api.events().register(PlayerKickEvent.class, event -> scheduleOnlinePlayerSnapshotRefresh());
        playerSnapshotListenersRegistered = true;
    }

    private void scheduleOnlinePlayerSnapshotRefresh() {
        if (playerSnapshotRefreshScheduled) {
            return;
        }

        playerSnapshotRefreshScheduled = true;
        api.tasks().nextTick(() -> {
            playerSnapshotRefreshScheduled = false;
            refreshOnlinePlayerSnapshot();
        });
    }

    void refreshOnlinePlayerSnapshot() {
        List<String> names = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT)))
                .toList();
        onlinePlayerNames = List.copyOf(names);
    }

    /**
     * Internal HTTP handler for processing requests.
     */
    class WebServiceHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            try {
                // Log before parsing so a bad request cannot fail silently.
                api.messages().info("WEB_SERVER_REQUEST",
                    "method", exchange.getRequestMethod(),
                    "path", redactSensitivePath(path),
                    "ip", exchange.getRemoteAddress().toString());
                handleRequest(exchange, path);
            } catch (IOException | RuntimeException | LinkageError failure) {
                api.messages().error("WEB_SERVER_REQUEST_FAILED", failure,
                    "method", exchange.getRequestMethod(),
                    "path", redactSensitivePath(path),
                    "ip", exchange.getRemoteAddress().toString());
                if (exchange.getResponseCode() < 0) {
                    sendErrorResponse(exchange, 500, "Internal server error");
                }
            } finally {
                exchange.close();
            }
        }

        private void handleRequest(HttpExchange exchange, String path) throws IOException {
            String uri = exchange.getRequestURI().toString();

            // URI.getPath() has already decoded percent escapes. A second decode
            // corrupts literal '%' and '+' characters and can throw before logging.
            // Strip leading "/" so resolve treats it as relative
            String rel = path.startsWith("/") ? path.substring(1) : path;

            Map<String, String> queryParams;
            try {
                queryParams = getQueryParams(exchange);
            } catch (IllegalArgumentException malformedQuery) {
                sendErrorResponse(exchange, 400, "Invalid query parameters");
                return;
            }
            Map<String, List<String>> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((key, values) -> headers.put(key, new ArrayList<>(values)));
            byte[] requestBody = exchange.getRequestBody().readNBytes(MAX_HTTP_REQUEST_BODY_BYTES + 1);
            if (requestBody.length > MAX_HTTP_REQUEST_BODY_BYTES) {
                sendErrorResponse(exchange, 413, "Request body is too large");
                return;
            }
            WebServiceRequest request = new WebServiceRequest(
                    exchange.getRequestMethod(),
                    uri,
                    path,
                    queryParams,
                    headers,
                    requestBody,
                    exchange.getRemoteAddress().toString()
            );

            // Use canonical/real paths + normalization to prevent traversal
            Path root = wwwRoot.toPath().toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path requested = root.resolve(rel).normalize();

            if (!requested.startsWith(root)) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            for (var e : endpointHandlers.entrySet()) {
                if (request.path().startsWith(e.getKey())) {
                    Object result = e.getValue().handle(request);
                    if (result == null) continue;
                    writeEndpointResponse(exchange, result);
                    return;
                }
            }

            if (Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
                sendErrorResponse(exchange, 403, "Directory listing not permitted");
                return;
            }

            if (!Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
                sendErrorResponse(exchange, 404, "File not found");
                return;
            }

            // Serve the requested file (streaming, not readAllBytes)
            String contentType = Files.probeContentType(requested);
            if (contentType == null) contentType = "application/octet-stream";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            long size = Files.size(requested);
            if (!beginResponse(exchange, 200, size)) return;

            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(requested, os);
            }
        }

        private static String redactSensitivePath(String path) {
            if (!path.startsWith("/quests/editor/")) return path;
            String remainder = path.substring("/quests/editor/".length());
            int slash = remainder.indexOf('/');
            return "/quests/editor/[redacted]" + (slash < 0 ? "" : remainder.substring(slash));
        }

        private void writeEndpointResponse(HttpExchange exchange, Object result) throws IOException {
            int responseCode = 200;
            byte[] bodyBytes = new byte[0];
            File responseFile = null;
            String contentType = "text/plain; charset=utf-8";
            Map<String, String> responseHeaders = new LinkedHashMap<>();

            if (result instanceof Map<?, ?> map) {
                Object codeObj = map.get("responseCode");
                if (codeObj == null) {
                    codeObj = map.get("code");
                }
                if (codeObj instanceof Number) responseCode = ((Number) codeObj).intValue();

                Object ctObj = map.get("contentType");
                if (ctObj instanceof String) contentType = (String) ctObj;

                Object headersObj = map.get("headers");
                if (headersObj instanceof Map<?, ?> mapHeaders) {
                    for (Map.Entry<?, ?> headerEntry : mapHeaders.entrySet()) {
                        if (headerEntry.getKey() == null || headerEntry.getValue() == null) {
                            continue;
                        }
                        responseHeaders.put(headerEntry.getKey().toString(), headerEntry.getValue().toString());
                    }
                }

                Object fileObj = map.get("file");
                if (fileObj instanceof File f) {
                    responseFile = f;
                } else {
                    Object bodyObj = map.get("body");
                    String body = (bodyObj != null) ? bodyObj.toString() : "";
                    bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                }
            } else if (result != null) {
                bodyBytes = result.toString().getBytes(StandardCharsets.UTF_8);
            }

            responseHeaders.forEach((header, value) -> exchange.getResponseHeaders().set(header, value));

            if (responseFile != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
                if (!beginResponse(exchange, responseCode, responseFile.length())) return;
                try (OutputStream os = exchange.getResponseBody();
                     FileInputStream fis = new FileInputStream(responseFile)) {
                    fis.transferTo(os);
                }
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", contentType);
            if (!beginResponse(exchange, responseCode, bodyBytes.length)) return;
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        }

        private static @NonNull Map<String, String> getQueryParams(HttpExchange exchange) {
            Map<String, String> queryParams = new LinkedHashMap<>();
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    if (keyValue.length == 2) {
                        queryParams.put(key, URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
                    } else if (keyValue.length == 1) {
                        queryParams.put(key, "");
                    }
                }
            }
            return queryParams;
        }

        private void sendErrorResponse(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
            byte[] errorBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            if (!beginResponse(exchange, statusCode, errorBytes.length)) return;
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
        }

        private boolean beginResponse(HttpExchange exchange, int statusCode, long length) throws IOException {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Length", Long.toString(length));
                exchange.sendResponseHeaders(statusCode, -1);
                return false;
            }
            exchange.sendResponseHeaders(statusCode, length == 0 ? -1 : length);
            return length > 0;
        }
    }

    /**
     * Attempt to find the best local IP address for the server.
     *
     * @return The best local IP address as a string.
     */
    private String findBestLocalAddress() {
        try {
            for (var networkInterface : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) continue;

                for (var addr : java.util.Collections.list(networkInterface.getInetAddresses())) {
                    if (addr.isLoopbackAddress()) continue;
                    if (addr.isLinkLocalAddress()) continue;
                    if (addr.isAnyLocalAddress()) continue;

                    // Prefer IPv4 LAN addresses like 192.168.x.x / 10.x.x.x
                    if (addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback
        return "127.0.0.1";
    }
}
