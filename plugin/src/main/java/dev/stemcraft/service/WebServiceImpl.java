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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.web.WebService;
import dev.stemcraft.api.service.web.WebServiceEndpointHandler;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of the WebService for serving HTTP requests.
 */
public class WebServiceImpl extends BaseService implements WebService {
    private File wwwRoot;
    private HttpServer httpServer;
    private final Map<String, WebServiceEndpointHandler> endpointHandlers = new LinkedHashMap<>();
    private String ip;
    private int port;

    /**
     * Constructor for the WebServiceImpl.
     * 
     * @param plugin The STEMCraft plugin instance.
     * @param api    The STEMCraft API instance.
     */
    public WebServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        setConfigKey("web-server");
    }

    /**
     * Enable the web service if configured to do so.
     */
    public void onEnable() {
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
        String wwwPath = plugin.getConfig().getString("path", "www");
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
    public void registerEndpointHandler(String path, WebServiceEndpointHandler handler) {
        this.endpointHandlers.put(path, handler);
        api.messages().debug("WEB_SERVER_REGISTERED_ENDPOINT", "path", path);
    }

    /**
     * Get the public URL of the web server.
     */
    public String getPublicUrl() {
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

    /**
     * Internal HTTP handler for processing requests.
     */
    class WebServiceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uri = exchange.getRequestURI().getPath();
            Map<String, String> queryParams = getStringStringMap(exchange);

            api.messages().info("WEB_SERVER_REQUEST", "method", exchange.getRequestMethod(), "path", uri, "ip", exchange.getRemoteAddress().toString());

            File file = new File(wwwRoot, uri.substring(1));

            if (!file.getAbsolutePath().startsWith(wwwRoot.getAbsolutePath())) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            for (var e : endpointHandlers.entrySet()) {
                if (uri.startsWith(e.getKey())) {
                    Object result = e.getValue().handle("GET", uri, queryParams);

                    int responseCode = 200;
                    byte[] bodyBytes = new byte[0];
                    File responseFile = null;
                    String contentType = "text/html; charset=utf-8";

                    if (result instanceof Map<?,?> map) {
                        Object codeObj = map.get("responseCode");
                        if (codeObj instanceof Number) {
                            responseCode = ((Number) codeObj).intValue();
                        }

                        Object ctObj = map.get("contentType");
                        if (ctObj instanceof String) {
                            contentType = (String) ctObj;
                        }

                        Object fileObj = map.get("file");
                        if (fileObj instanceof File f) {
                            responseFile = f;
                        } else {
                            Object bodyObj = map.get("body");
                            String body = (bodyObj != null) ? bodyObj.toString() : "";
                            bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } else if (result != null) {
                        bodyBytes = result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }

                    // send response
                    if (responseFile != null) {
                        exchange.getResponseHeaders().add("Content-Type", contentType);
                        exchange.sendResponseHeaders(responseCode, responseFile.length());
                        try (OutputStream os = exchange.getResponseBody();
                             FileInputStream fis = new FileInputStream(responseFile)) {
                            fis.transferTo(os);
                        }
                    } else {
                        exchange.getResponseHeaders().add("Content-Type", contentType);
                        exchange.sendResponseHeaders(responseCode, bodyBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bodyBytes);
                        }
                    }

                    return;                }
            }

            if (file.isDirectory()) {
                sendErrorResponse(exchange, 403, "Directory listing not permitted");
                return;
            }

            if (!file.exists()) {
                sendErrorResponse(exchange, 404, "File not found");
                return;
            }

            // Serve the requested file
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }

        private static @NonNull Map<String, String> getStringStringMap(HttpExchange exchange) {
            Map<String, String> queryParams = new LinkedHashMap<>();
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        queryParams.put(keyValue[0], keyValue[1]);
                    } else if (keyValue.length == 1) {
                        queryParams.put(keyValue[0], "");
                    }
                }
            }
            return queryParams;
        }

        private void sendErrorResponse(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
            exchange.sendResponseHeaders(statusCode, errorMessage.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorMessage.getBytes());
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
            for (var iface : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                for (var addr : java.util.Collections.list(iface.getInetAddresses())) {
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
