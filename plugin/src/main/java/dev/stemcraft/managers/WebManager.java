/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.managers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.web.WebService;
import dev.stemcraft.api.services.web.WebServiceEndpointHandler;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class WebManager implements WebService {
    private STEMCraft plugin;
    private File wwwRoot;
    private HttpServer httpServer;
    private final Map<String, WebServiceEndpointHandler> endpointHandlers = new LinkedHashMap<>();

    public WebManager(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        if(plugin.config().getBoolean("web_server.enabled", false)) {
            start();
        }

        plugin.registerCommand("webserver")
                .setUsage("webserver <start|stop|enable|disable>")
                .setExecutor((api, cmd, ctx) -> {
                    if (ctx.args().isEmpty()) {
                        api.info(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                switch (ctx.args().getFirst().toLowerCase(Locale.ROOT)) {
                    case "start" -> {
                        if(isRunning()) {
                            api.error(ctx.getSender(), "WEBSERVER_ALREADY_RUNNING");
                        } else {
                            start();
                        }
                    }
                    case "stop" -> {
                        if(isRunning()) {
                            stop();
                        } else {
                            api.error(ctx.getSender(), "WEBSERVER_NOT_RUNNING");
                        }
                    }
                    case "enable" -> {
                        plugin.config().set("web_server.enabled", true);
                        plugin.configSave();

                        api.error(ctx.getSender(), "WEBSERVER_ENABLED", "state", isRunning() ? "WEBSERVER_STATE_RUNNING" : "WEBSERVER_STATE_NOT_RUNNING");
                    }
                    case "disable" -> {
                        plugin.config().set("web_server.enabled", false);
                        plugin.configSave();

                        api.error(ctx.getSender(), "WEBSERVER_DISABLED", "state", isRunning() ? "WEBSERVER_STATE_RUNNING" : "WEBSERVER_STATE_NOT_RUNNING");
                    }
                    default -> api.info(ctx.getSender(), cmd.getUsage());
                }
            })
            .register(plugin);
    }

    public boolean isRunning() {
        return httpServer != null;
    }

    public void start() {
        String wwwPath = plugin.getConfig().getString("web_server.path", "www");
        wwwPath = wwwPath
                .replace("\\", "/")
                .replaceAll("^/+", "")   // remove leading slashes
                .replaceAll("/+$", "")   // remove trailing slashes
                .replace("../", "")
                .replace("..", "");

        wwwRoot = new File(plugin.getDataFolder(), wwwPath);

        if (!wwwRoot.exists()) {
            if (!wwwRoot.mkdirs()) {
                plugin.error("FAILED_CREATE_DIR");
                wwwRoot = null;
                return;
            }

            if(!wwwRoot.isDirectory()) {
                plugin.error("WEBSERVER_PATH_NOT_DIR");
                wwwRoot = null;
                return;
            }
        }

        int port = plugin.config().getInt("web_server.port", 8950);
        String ip = plugin.config().getString("web_server.ip", "127.0.0.1");

        try {
            httpServer = HttpServer.create(new InetSocketAddress(ip, port), 0);
            httpServer.createContext("/", new WebServiceHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            plugin.info("WEBSERVER_STARTED_ON", "ip", ip, "port", String.valueOf(port));
        } catch (IOException e) {
            plugin.error("WEBSERVER_START_FAILED", "error", e.getMessage());
            httpServer = null;
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            plugin.info("WEBSERVER_STOPPED");
        }
    }

    public void registerEndpointHandler(String path, WebServiceEndpointHandler handler) {
        this.endpointHandlers.put(path, handler);
    }


    class WebServiceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uri = exchange.getRequestURI().getPath();
            File file = new File(wwwRoot, uri.substring(1));

            if (!file.getAbsolutePath().startsWith(wwwRoot.getAbsolutePath())) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            for (var e : endpointHandlers.entrySet()) {
                if (uri.startsWith(e.getKey())) {
                    Object result = e.getValue().handle("GET", uri);

                    int code;
                    byte[] bodyBytes;

                    if (result instanceof Map<?,?> map) {
                        Object codeObj = map.get("code");
                        Object bodyObj = map.get("body");

                        code = (codeObj instanceof Number) ? ((Number) codeObj).intValue() : 200;
                        String body = (bodyObj != null) ? bodyObj.toString() : "";
                        bodyBytes = body.getBytes();
                    } else {
                        code = 200;
                        bodyBytes = result != null ? result.toString().getBytes() : new byte[0];
                    }

                    exchange.sendResponseHeaders(code, bodyBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bodyBytes);
                    }

                    return;
                }
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

        private void sendErrorResponse(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
            exchange.sendResponseHeaders(statusCode, errorMessage.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorMessage.getBytes());
            }
        }
    }
}
