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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.web.WebServiceEndpointHandler;
import dev.stemcraft.api.service.web.WebServiceRequest;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of the WebService for serving HTTP requests.
 */
public class WebServiceImpl extends BaseService implements WebService {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final String MAINTENANCE_MESSAGE = "Server is under maintenance. Please try again later.";
    private File wwwRoot;
    private HttpServer httpServer;
    private final Map<String, WebServiceEndpointHandler> endpointHandlers = new LinkedHashMap<>();
    private String ip;
    private int port;

    /**
     * Constructor for the WebServiceImpl.
     * 
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public WebServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "web_server");
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
                .tabCompletion("enable")
                .tabCompletion("disable")
                .tabCompletion("enable")
                .tabCompletion("status")
                .usage("WEB_SERVER_USAGE")
                .executor((api, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        api.messages().info(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                switch (ctx.args().getFirst().toLowerCase(Locale.ROOT)) {
                    case "start" -> {
                        if(isRunning()) {
                            api.messages().error(ctx.getSender(), "WEB_SERVER_ALREADY_RUNNING");
                        } else {
                            start();
                        }
                    }
                    case "stop" -> {
                        if(isRunning()) {
                            stop();
                        } else {
                            api.messages().error(ctx.getSender(), "WEB_SERVER_NOT_RUNNING");
                        }
                    }
                    case "enable" -> {
                        getConfigSection().set("enabled", true);
                        saveConfig();

                        api.messages().info(ctx.getSender(), "WEB_SERVER_ENABLED", "state", isRunning() ? "WEB_SERVER_STATE_RUNNING" : "WEB_SERVER_STATE_NOT_RUNNING");
                    }
                    case "disable" -> {
                        getConfigSection().set("enabled", false);
                        saveConfig();

                        api.messages().info(ctx.getSender(), "WEB_SERVER_DISABLED", "state", isRunning() ? "WEB_SERVER_STATE_RUNNING" : "WEB_SERVER_STATE_NOT_RUNNING");
                    }
                    case "", "status" -> api.messages().info(ctx.getSender(), "WEB_SERVER_STATUS",
                            "enabled_disabled",
                            getConfigSection().getBoolean("enabled", false) ? "WEB_SERVER_STATE_ENABLED" : "WEB_SERVER_STATE_DISABLED",
                            "running_not",
                            isRunning() ? "WEB_SERVER_STATE_RUNNING" : "WEB_SERVER_STATE_NOT_RUNNING");
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

            if(!wwwRoot.isDirectory()) {
                api.messages().error("WEB_SERVER_PATH_NOT_DIR");
                wwwRoot = null;
                return;
            }
        }

        port = getConfigSection().getInt("port", 8950);
        ip = getConfigSection().getString("ip", "127.0.0.1");

        try {
            httpServer = HttpServer.create(new InetSocketAddress(ip, port), 0);
            httpServer.createContext("/", new WebServiceHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            api.messages().info("WEB_SERVER_STARTED_ON", "ip", ip, "port", String.valueOf(port));

            registerEndpointHandler("/status", this::handleStatusEndpoint);
        } catch (IOException e) {
            api.messages().error("WEB_SERVER_START_FAILED", "error", e.getMessage());
            httpServer = null;
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

    /**
     * Register a custom endpoint handler.
     */
    public void registerEndpointHandler(@NotNull String path, @NotNull WebServiceEndpointHandler handler) {
        this.endpointHandlers.put(path, handler);
        api.messages().debug("WEB_SERVER_REGISTERED_ENDPOINT", "path", path);
    }

    Object handleStatusEndpoint(@NotNull WebServiceRequest request) {
        if (!"/status".equals(request.path())) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        boolean maintenance = plugin.isMaintenanceMode();
        payload.put("online", true);
        payload.put("players_online", plugin.getServer().getOnlinePlayers().size());
        payload.put("max_players", plugin.getServer().getMaxPlayers());
        payload.put("version", plugin.getServer().getMinecraftVersion());
        payload.put("maintenance", maintenance);
        payload.put("message", maintenance ? MAINTENANCE_MESSAGE : null);
        payload.put("checked_at", OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responseCode", 200);
        response.put("contentType", "application/json; charset=utf-8");
        response.put("body", GSON.toJson(payload));
        return response;
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

        String host = ip;

        if ("0.0.0.0".equals(host) || "::".equals(host)) {
            host = findBestLocalAddress();
        }

        return "http://" + host + ":" + port;
    }

    private void loadSettings() {
        port = getConfigSection().getInt("port", 8950);
        ip = getConfigSection().getString("ip", "127.0.0.1");
    }

    /**
     * Internal HTTP handler for processing requests.
     */
    class WebServiceHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String uri = exchange.getRequestURI().toString();

            // Decode once so %2e%2e etc can’t bypass checks
            String decodedUri = URLDecoder.decode(path, StandardCharsets.UTF_8);

            // Strip leading "/" so resolve treats it as relative
            String rel = decodedUri.startsWith("/") ? decodedUri.substring(1) : decodedUri;

            Map<String, String> queryParams = getQueryParams(exchange);
            Map<String, List<String>> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((key, values) -> headers.put(key, new ArrayList<>(values)));
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            WebServiceRequest request = new WebServiceRequest(
                    exchange.getRequestMethod(),
                    uri,
                    path,
                    queryParams,
                    headers,
                    requestBody,
                    exchange.getRemoteAddress().toString()
            );

            api.messages().info("WEB_SERVER_REQUEST",
                    "method", request.method(),
                    "path", request.path(),
                    "ip", exchange.getRemoteAddress().toString()
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
            exchange.sendResponseHeaders(200, size);

            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(requested, os);
            }
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
                exchange.sendResponseHeaders(responseCode, responseFile.length());
                try (OutputStream os = exchange.getResponseBody();
                     FileInputStream fis = new FileInputStream(responseFile)) {
                    fis.transferTo(os);
                }
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(responseCode, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        }

        private static @NonNull Map<String, String> getQueryParams(HttpExchange exchange) {
            Map<String, String> queryParams = new LinkedHashMap<>();
            String query = exchange.getRequestURI().getQuery();
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
            exchange.sendResponseHeaders(statusCode, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
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
