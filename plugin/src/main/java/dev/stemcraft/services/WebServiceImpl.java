package dev.stemcraft.services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.web.WebService;
import dev.stemcraft.api.services.web.WebServiceEndpointHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebServiceImpl implements WebService {
    private STEMCraft plugin;
    private File wwwRoot;
    private HttpServer httpServer;
    private final Map<String, WebServiceEndpointHandler> endpointHandlers = new LinkedHashMap<>();

    public WebServiceImpl(STEMCraft plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        if(plugin.getConfig().getBoolean("web_server.enabled", false)) {
            start();
        }
    }

    public void onDisable() { }

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
                STEMCraft.error("Failed to create web server directory");
                wwwRoot = null;
                return;
            }

            if(!wwwRoot.isDirectory()) {
                STEMCraft.error("Web server path is not a directory");
                wwwRoot = null;
                return;
            }
        }

        int port = plugin.getConfig().getInt("web_server.port", 8950);
        String ip = plugin.getConfig().getString("web_server.ip", "127.0.0.1");

        try {
            httpServer = HttpServer.create(new InetSocketAddress(ip, port), 0);
            httpServer.createContext("/", new WebServiceHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            STEMCraft.info("Web server started on http://" + ip + ":" + port);
        } catch (IOException e) {
            STEMCraft.error("Failed to start web server: " + e.getMessage());
            httpServer = null;
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            STEMCraft.info("Web server stopped");
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
