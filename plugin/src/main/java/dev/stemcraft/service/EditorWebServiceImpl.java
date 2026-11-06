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
import dev.stemcraft.api.service.config.ConfigChangeEvent;
import dev.stemcraft.api.service.config.ConfigSubscription;
import dev.stemcraft.api.service.editor.EditorWebContext;
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.editor.EditorWebService;
import dev.stemcraft.api.service.web.WebServiceRequest;
import dev.stemcraft.api.service.web.WebServiceStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Shared private web editor hub and module registry. */
public final class EditorWebServiceImpl extends BaseService implements EditorWebService {
    private static final String EDITOR_PATH = "/editor";
    private static final String SESSION_COOKIE_NAME = "stemcraft_editor_session";
    private static final long SESSION_COOKIE_MAX_AGE_SECONDS = TimeUnit.DAYS.toSeconds(30);
    private static final String SESSION_FILE_NAME = "editor-sessions.yml";
    private static final long SESSION_LIFETIME_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private final Map<String, EditorWebModule> modules = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, PersistentSession> playerSessions = new ConcurrentHashMap<>();
    private final Set<EditorEventClient> configEventClients = ConcurrentHashMap.newKeySet();
    private final File sessionFile;
    private String editorHtml;
    private ConfigSubscription configChangeSubscription;

    public EditorWebServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "editor-web");
        sessionFile = new File(plugin.getDataFolder(), SESSION_FILE_NAME);
    }

    @Override
    public void onEnable() {
        editorHtml = loadEditorHtml();
        loadPersistentSessions();
        registerComponentsModule();
        api.web().registerEndpointHandler(EDITOR_PATH, this::handleRequest);
        api.web().registerStreamingEndpointHandler(EDITOR_PATH, this::handleConfigStream);
        configChangeSubscription = api.config().onAnyChange(this::publishConfigChange);
        api.commands().create("editor")
            .description("Open the STEMCraft editor")
            .usage("/editor [logout]")
            .tabCompletion("logout")
            .executor((unused, command, context) -> {
                if (!context.args().isEmpty()) {
                    if (context.args().size() == 1 && "logout".equalsIgnoreCase(context.args().getFirst())) {
                        logoutEditor(context);
                    } else {
                        context.returnError("Usage: /editor [logout]");
                    }
                    return;
                }
                openEditor(context);
            })
            .register(plugin);
    }

    @Override
    public void onDisable() {
        sessions.clear();
        playerSessions.clear();
        modules.clear();
        if (configChangeSubscription != null) configChangeSubscription.close();
        configChangeSubscription = null;
        configEventClients.clear();
    }

    @Override
    public void registerModule(@NotNull EditorWebModule module) {
        String id = normalizeModuleId(module.id());
        modules.put(id, module);
    }

    private void registerComponentsModule() {
        registerModule(new EditorWebModule() {
            @Override public @NotNull String id() { return "components"; }
            @Override public @NotNull String title() { return "Components"; }
            @Override public @NotNull String icon() { return "C"; }
            @Override public @NotNull String description() { return "Review the shared editor building blocks."; }
            @Override public @NotNull String readPermission() { return ""; }
            @Override public @NotNull EditorWebResponse handle(@NotNull EditorWebContext context, @NotNull String path,
                                                                 @NotNull WebServiceRequest request) {
                if (!"/data".equals(path) || !"GET".equalsIgnoreCase(request.method())) {
                    return new EditorWebResponse(405, "text/plain; charset=utf-8", "Method not allowed");
                }
                return jsonResponse(200, Map.of("kind", "components"));
            }
        });
    }

    @Override
    public @NotNull String createSession(@NotNull CommandSender sender) {
        cleanupSessions();
        List<String> visible = modules.values().stream()
            .filter(module -> hasPermission(sender, module.readPermission()))
            .map(module -> normalizeModuleId(module.id()))
            .sorted()
            .toList();
        if (visible.isEmpty()) {
            if (sender instanceof Player player) revokePlayerSession(player.getUniqueId());
            return "";
        }
        Set<String> writable = modules.values().stream()
            .filter(module -> visible.contains(normalizeModuleId(module.id())))
            .filter(module -> hasPermission(sender, module.writePermission()))
            .map(module -> normalizeModuleId(module.id()))
            .collect(Collectors.toUnmodifiableSet());
        if (sender instanceof Player player) {
            return createPlayerSession(player.getUniqueId(), Set.copyOf(visible), writable);
        }
        String token = newToken();
        sessions.put(token, new Session(System.currentTimeMillis() + SESSION_LIFETIME_MILLIS,
            null, Set.copyOf(visible), writable));
        return editorUrl(token);
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
        if (context.getSender() instanceof Player) {
            context.getSender().sendMessage(Component.text(
                "This link stays logged in until you use /editor logout.", NamedTextColor.GRAY));
        } else {
            context.getSender().sendMessage(Component.text(
                "This private link expires in 24 hours or when the server restarts.", NamedTextColor.GRAY));
        }
    }

    private void logoutEditor(CommandContext context) {
        if (!(context.getSender() instanceof Player player)) {
            context.returnError("Only players have persistent editor sessions.");
            return;
        }
        if (!revokePlayerSession(player.getUniqueId())) {
            context.returnInfo("You do not have a persistent editor session.");
            return;
        }
        context.returnSuccess("Your editor session has been logged out. Use /editor to create a new link.");
    }

    private Object handleRequest(WebServiceRequest request) {
        String path = request.path();
        if (path.equals(EDITOR_PATH) || path.equals(EDITOR_PATH + "/")) {
            Session session = validSession(cookieToken(request));
            if (session == null) {
                return response(new EditorWebResponse(403, "text/plain; charset=utf-8",
                    "Open your private editor link to sign in."));
            }
            return "GET".equalsIgnoreCase(request.method())
                ? response(new EditorWebResponse(200, "text/html; charset=utf-8", editorHtml))
                : response(new EditorWebResponse(405, "text/plain; charset=utf-8", "Method not allowed"));
        }
        if (!path.startsWith(EDITOR_PATH + "/")) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Not found"));
        }
        String tail = path.substring((EDITOR_PATH + "/").length());
        String[] parts = tail.split("/", -1);
        if (parts.length == 0 || parts[0].isBlank()) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Open a private editor link."));
        }

        if (!"api".equalsIgnoreCase(parts[0])) {
            if (parts.length > 1 && !(parts.length == 2 && parts[1].isBlank())) {
                return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Not found"));
            }
            Session loginSession = validSession(parts[0]);
            if (loginSession == null) return response(new EditorWebResponse(403, "text/plain; charset=utf-8", "Not permitted"));
            return "GET".equalsIgnoreCase(request.method())
                ? redirectToCleanEditor(request, parts[0])
                : response(new EditorWebResponse(405, "text/plain; charset=utf-8", "Method not allowed"));
        }

        String token = cookieToken(request);
        Session session = validSession(token);
        if (session == null) return response(new EditorWebResponse(403, "text/plain; charset=utf-8", "Not permitted"));

        if (parts.length == 0 || !"api".equalsIgnoreCase(parts[0])) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Not found"));
        }

        int moduleIndex = 1;
        if (parts.length == moduleIndex + 1 && "modules".equalsIgnoreCase(parts[moduleIndex])) {
            return response(jsonResponse(200, visibleModules(session)));
        }

        int relativePathIndex = moduleIndex + 1;
        if (parts.length <= relativePathIndex) {
            return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Module required"));
        }
        String moduleId = normalizeModuleId(parts[moduleIndex]);
        if (!session.modules().contains(moduleId))
            return response(new EditorWebResponse(403, "text/plain; charset=utf-8", "This editor is not available in this link"));
        EditorWebModule module = modules.get(moduleId);
        if (module == null) return response(new EditorWebResponse(404, "text/plain; charset=utf-8", "Editor module not found"));
        String relativePath = "/" + String.join("/", java.util.Arrays.copyOfRange(parts, relativePathIndex, parts.length));
        EditorWebContext context = new EditorWebContext(token, session.writable().contains(moduleId));
        try {
            EditorWebResponse result = onServerThread(() -> module.handle(context, relativePath, request));
            return response(result);
        } catch (Exception exception) {
            plugin.getLogger().warning("[editor-web] Module '" + moduleId + "' failed: " + exception.getMessage());
            return response(new EditorWebResponse(500, "text/plain; charset=utf-8", "The editor could not complete that action."));
        }
    }

    /** Streams ConfigService events to authenticated editor tabs without owning a watcher. */
    private boolean handleConfigStream(WebServiceRequest request, WebServiceStream stream) throws IOException {
        if (!"GET".equalsIgnoreCase(request.method())) return false;
        String path = request.path();
        if (!path.startsWith(EDITOR_PATH + "/")) return false;
        String[] parts = path.substring((EDITOR_PATH + "/").length()).split("/", -1);
        if (parts.length != 2 || !"api".equalsIgnoreCase(parts[0]) || !"events".equalsIgnoreCase(parts[1])) return false;
        String token = cookieToken(request);
        if (validSession(token) == null) return false;

        EditorEventClient client = new EditorEventClient();
        configEventClients.add(client);
        try {
            stream.send("ready", "{}");
            while (stream.isOpen() && validSession(token) != null) {
                String payload = client.events.poll(20L, TimeUnit.SECONDS);
                if (payload == null) stream.send("keepalive", "{}");
                else stream.send("config.change", payload);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            configEventClients.remove(client);
        }
        return true;
    }

    private void publishConfigChange(ConfigChangeEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file", event.fileName());
        data.put("origin", event.origin().name());
        data.put("paths", event.paths());
        data.put("oldValues", event.oldValues());
        data.put("newValues", event.newValues());
        data.put("revision", event.revision());
        String payload = GSON.toJson(data);
        for (EditorEventClient client : configEventClients) client.offer(payload);
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

    private String createPlayerSession(UUID playerId, Set<String> visible, Set<String> writable) {
        PersistentSession existing = playerSessions.get(playerId);
        String token = existing == null ? newToken() : existing.token();
        PersistentSession updated = new PersistentSession(token, Set.copyOf(visible), Set.copyOf(writable));
        playerSessions.put(playerId, updated);
        sessions.put(token, new Session(Long.MAX_VALUE, playerId, updated.modules(), updated.writable()));
        savePersistentSessions();
        return editorUrl(token);
    }

    private boolean revokePlayerSession(UUID playerId) {
        PersistentSession removed = playerSessions.remove(playerId);
        if (removed == null) return false;
        sessions.remove(removed.token());
        savePersistentSessions();
        return true;
    }

    private String newToken() {
        String token;
        do {
            byte[] bytes = new byte[24];
            SECURE_RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (sessions.containsKey(token));
        return token;
    }

    private String editorUrl(String token) {
        return api.web().getPublicUrl() + EDITOR_PATH + "/" + token;
    }

    private void loadPersistentSessions() {
        if (!sessionFile.isFile()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(sessionFile);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return;
        for (String rawId : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(rawId);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String path = "players." + rawId;
            String token = config.getString(path + ".token", "");
            if (!isToken(token)) continue;
            Set<String> storedVisible = storedModules(config.getStringList(path + ".modules"));
            Set<String> writable = storedModules(config.getStringList(path + ".writable"));
            if (storedVisible.isEmpty()) continue;
            Set<String> visible = new java.util.LinkedHashSet<>(storedVisible);
            visible.add("components");
            PersistentSession persistent = new PersistentSession(token, Set.copyOf(visible), writable);
            playerSessions.put(playerId, persistent);
            sessions.put(token, new Session(Long.MAX_VALUE, playerId, persistent.modules(), writable));
        }
    }

    private void savePersistentSessions() {
        YamlConfiguration config = new YamlConfiguration();
        playerSessions.forEach((playerId, session) -> {
            String path = "players." + playerId;
            config.set(path + ".token", session.token());
            config.set(path + ".modules", session.modules().stream().sorted().toList());
            config.set(path + ".writable", session.writable().stream().sorted().toList());
        });
        try {
            File parent = sessionFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the editor session directory.");
                return;
            }
            config.save(sessionFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save editor sessions: " + exception.getMessage());
        }
    }

    private static Set<String> storedModules(List<String> values) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String value : values) {
            try {
                result.add(normalizeModuleId(value));
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid module IDs from a manually edited session file.
            }
        }
        return Set.copyOf(result);
    }

    private static boolean isToken(String token) {
        return token != null && token.length() >= 24 && token.matches("[A-Za-z0-9_-]+");
    }

    private static final class EditorEventClient {
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>(64);

        private void offer(String payload) {
            if (!events.offer(payload)) {
                events.poll();
                events.offer(payload);
            }
        }
    }

    private static boolean hasPermission(CommandSender sender, String permission) {
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    private static String normalizeModuleId(String id) {
        if (id == null || !id.trim().matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("Invalid editor module ID");
        return id.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private Map<String, Object> redirectToCleanEditor(WebServiceRequest request, String token) {
        return response(new EditorWebResponse(303, "text/plain; charset=utf-8", "Editor session established."),
            Map.of(
                "Location", EDITOR_PATH,
                "Set-Cookie", sessionCookie(request, token)
            ));
    }

    private String sessionCookie(WebServiceRequest request, String token) {
        StringBuilder cookie = new StringBuilder(SESSION_COOKIE_NAME)
            .append('=').append(token)
            .append("; Path=").append(EDITOR_PATH)
            .append("; Max-Age=").append(SESSION_COOKIE_MAX_AGE_SECONDS)
            .append("; HttpOnly; SameSite=Lax");
        if (isSecureRequest(request)) cookie.append("; Secure");
        return cookie.toString();
    }

    private boolean isSecureRequest(WebServiceRequest request) {
        String forwarded = request.firstHeader("X-Forwarded-Proto");
        if (forwarded != null && forwarded.toLowerCase(java.util.Locale.ROOT).startsWith("https")) return true;
        if (request.uri().toLowerCase(java.util.Locale.ROOT).startsWith("https://")) return true;
        try {
            return api.web().getPublicUrl().toLowerCase(java.util.Locale.ROOT).startsWith("https://");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String cookieToken(WebServiceRequest request) {
        String cookieHeader = request.firstHeader("Cookie");
        if (cookieHeader == null) return "";
        for (String part : cookieHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && SESSION_COOKIE_NAME.equals(pair[0].trim())) {
                String token = pair[1].trim();
                return isToken(token) ? token : "";
            }
        }
        return "";
    }

    private static Map<String, Object> response(EditorWebResponse result) {
        return response(result, Map.of());
    }

    private static Map<String, Object> response(EditorWebResponse result, Map<String, String> additionalHeaders) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responseCode", result.status());
        response.put("contentType", result.contentType());
        response.put("body", result.body());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:");
        headers.putAll(additionalHeaders);
        response.put("headers", headers);
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

    private record Session(long expiresAt, UUID playerId, Set<String> modules, Set<String> writable) { }
    private record PersistentSession(String token, Set<String> modules, Set<String> writable) { }
}
