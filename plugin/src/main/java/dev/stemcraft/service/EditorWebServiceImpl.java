/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.editor.EditorWebContext;
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.editor.EditorWebService;
import dev.stemcraft.api.service.web.WebServiceRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Shared private web editor hub and module registry. */
public final class EditorWebServiceImpl extends BaseService implements EditorWebService {
    private static final String EDITOR_PATH = "/editor";
    private static final long SESSION_LIFETIME_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private final Map<String, EditorWebModule> modules = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private String editorHtml;

    public EditorWebServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "editor-web");
    }

    @Override
    public void onEnable() {
        editorHtml = loadEditorHtml();
        api.web().registerEndpointHandler(EDITOR_PATH, this::handleRequest);
        api.commands().create("editor")
            .description("Open the STEMCraft editor")
            .usage("/editor")
            .executor((unused, command, context) -> openEditor(context))
            .register(plugin);
    }

    @Override
    public void onDisable() {
        sessions.clear();
        modules.clear();
    }

    @Override
    public void registerModule(@NotNull EditorWebModule module) {
        String id = normalizeModuleId(module.id());
        modules.put(id, module);
    }

    @Override
    public @NotNull String createSession(@NotNull CommandSender sender) {
        cleanupSessions();
        List<String> visible = modules.values().stream()
            .filter(module -> hasPermission(sender, module.readPermission()))
            .map(module -> normalizeModuleId(module.id()))
            .sorted()
            .toList();
        if (visible.isEmpty()) return "";
        Set<String> writable = modules.values().stream()
            .filter(module -> visible.contains(normalizeModuleId(module.id())))
            .filter(module -> hasPermission(sender, module.writePermission()))
            .map(module -> normalizeModuleId(module.id()))
            .collect(Collectors.toUnmodifiableSet());
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(System.currentTimeMillis() + SESSION_LIFETIME_MILLIS,
            Set.copyOf(visible), writable));
        return api.web().getPublicUrl() + EDITOR_PATH + "/" + token;
    }

    private void openEditor(CommandContext context) {
        String url = createSession(context.getSender());
        if (url.isBlank()) {
            context.returnError("No web editors are available for your permissions.");
            return;
        }
        context.getSender().sendMessage(Component.text("STEMCraft Workshop: ", NamedTextColor.YELLOW)
            .append(Component.text(url, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Open the STEMCraft editor")))));
        context.getSender().sendMessage(Component.text(
            "This private link expires in 24 hours or when the server restarts.", NamedTextColor.GRAY));
    }

    private Object handleRequest(WebServiceRequest request) {
        String path = request.path();
        if (path.equals(EDITOR_PATH)) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Open a private editor link."));
        }
        if (!path.startsWith(EDITOR_PATH + "/")) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Not found"));
        }
        String tail = path.substring((EDITOR_PATH + "/").length());
        String[] parts = tail.split("/", -1);
        if (parts.length == 0 || parts[0].isBlank()) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Open a private editor link."));
        }
        Session session = validSession(parts[0]);
        if (session == null) return response(new EditorWebResponse(403, "text/plain; charset=utf-8", "Not permitted"));
        if (parts.length == 1 || (parts.length == 2 && parts[1].isBlank())) {
            return "GET".equalsIgnoreCase(request.method())
                ? response(new EditorWebResponse(200, "text/html; charset=utf-8", editorHtml))
                : response(new EditorWebResponse(405, "text/plain; charset=utf-8", "Method not allowed"));
        }
        if (!"api".equalsIgnoreCase(parts[1])) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Not found"));
        }
        if (parts.length == 3 && "modules".equalsIgnoreCase(parts[2])) {
            return response(jsonResponse(200, visibleModules(session)));
        }
        if (parts.length < 4) return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Module required"));
        String moduleId = normalizeModuleId(parts[2]);
        if (!session.modules().contains(moduleId))
            return response(new EditorWebResponse(403, "text/plain; charset=utf-8", "This editor is not available in this link"));
        EditorWebModule module = modules.get(moduleId);
        if (module == null) return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Editor module not found"));
        String relativePath = "/" + String.join("/", java.util.Arrays.copyOfRange(parts, 3, parts.length));
        EditorWebContext context = new EditorWebContext(parts[0], session.writable().contains(moduleId));
        try {
            EditorWebResponse result = onServerThread(() -> module.handle(context, relativePath, request));
            return response(result);
        } catch (Exception exception) {
            plugin.getLogger().warning("[editor-web] Module '" + moduleId + "' failed: " + exception.getMessage());
            return response(new EditorWebResponse(500, "text/plain; charset=utf-8", "The editor could not complete that action."));
        }
    }

    private List<Map<String, Object>> visibleModules(Session session) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : session.modules()) {
            EditorWebModule module = modules.get(id);
            if (module == null) continue;
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("title", module.title());
            value.put("icon", module.icon());
            value.put("description", module.description());
            value.put("writable", session.writable().contains(id));
            result.add(value);
        }
        result.sort(java.util.Comparator.comparing(value -> String.valueOf(value.get("title")), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private Session validSession(String token) {
        cleanupSessions();
        Session session = sessions.get(token);
        return session != null && session.expiresAt() >= System.currentTimeMillis() ? session : null;
    }

    private void cleanupSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private static boolean hasPermission(CommandSender sender, String permission) {
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    private static String normalizeModuleId(String id) {
        if (id == null || !id.trim().matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("Invalid editor module ID");
        return id.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private static Map<String, Object> response(EditorWebResponse result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responseCode", result.status());
        response.put("contentType", result.contentType());
        response.put("body", result.body());
        response.put("headers", Map.of("Cache-Control", "no-store",
            "X-Content-Type-Options", "nosniff",
            "Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:"));
        return response;
    }

    private static EditorWebResponse jsonResponse(int status, Object value) {
        return new EditorWebResponse(status, "application/json; charset=utf-8", GSON.toJson(value));
    }

    private String loadEditorHtml() {
        try (InputStream stream = plugin.getResource("www/editor.html")) {
            if (stream == null) throw new IOException("www/editor.html is missing from the plugin jar");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not load the editor hub: " + exception.getMessage());
            return "<!doctype html><html><body><h1>STEMCraft Workshop</h1><p>The editor could not be loaded.</p></body></html>";
        }
    }

    private <T> T onServerThread(Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) return task.call();
        return Bukkit.getScheduler().callSyncMethod(plugin, task).get(10, TimeUnit.SECONDS);
    }

    private record Session(long expiresAt, Set<String> modules, Set<String> writable) { }
}
