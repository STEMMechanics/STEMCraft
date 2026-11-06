/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package dev.stemcraft.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.contentpack.ContentPackInfo;
import dev.stemcraft.api.service.contentpack.ContentPackService;
import dev.stemcraft.api.service.contentpack.ContentPackValidation;
import dev.stemcraft.api.service.config.ConfigChangeOrigin;
import dev.stemcraft.api.service.config.ConfigSaveResult;
import dev.stemcraft.api.service.config.ConfigSubscription;
import dev.stemcraft.api.service.editor.EditorWebContext;
import dev.stemcraft.api.service.editor.EditorWebAction;
import dev.stemcraft.api.service.editor.EditorWebBadge;
import dev.stemcraft.api.service.editor.EditorWebComponent;
import dev.stemcraft.api.service.editor.EditorWebDialog;
import dev.stemcraft.api.service.editor.EditorWebField;
import dev.stemcraft.api.service.editor.EditorWebItem;
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebOption;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.editor.EditorWebView;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Central discovery and lifecycle service for STEMCraft-owned content packs. */
public final class ContentPackServiceImpl extends BaseService implements ContentPackService {
    private static final String PACK_ROOT = "data-packs";
    private static final String STATE_FILE = "content-pack-state.yml";
    private static final String TRASH_DIRECTORY = ".content-pack-trash";
    private static final Gson EDITOR_GSON = new GsonBuilder().serializeNulls().create();
    private final Map<String, Pack> packs = new LinkedHashMap<>();
    private final Map<String, Boolean> state = new LinkedHashMap<>();
    private final Map<String, Boolean> resourcePackChanges = new HashMap<>();
    private final Map<String, Handler> handlers = new LinkedHashMap<>();
    private final Map<String, ConfigSubscription> configSubscriptions = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> pendingExternalChanges = new LinkedHashMap<>();
    private ConfigFile stateConfig;
    private File packRoot;
    private boolean reloadQueued;

    public ContentPackServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "content-packs");
    }

    @Override
    public void onEnable() {
        plugin.exportBundledDirectory(PACK_ROOT);
        packRoot = new File(plugin.getDataFolder(), PACK_ROOT);
        loadState();
        discover();
        if (api.editorWeb() != null) api.editorWeb().registerModule(new ContentPackEditorModule());
    }

    @Override
    public void onReload() {
        super.onReload();
        Map<String, Pack> previous = new LinkedHashMap<>(packs);
        Map<String, String> previousContents = snapshotPackContents(previous.values());
        discover();
        Map<String, Pack> proposed = new LinkedHashMap<>(packs);
        Set<String> ids = new LinkedHashSet<>(previous.keySet());
        ids.addAll(proposed.keySet());
        for (String id : ids) {
            Pack oldPack = previous.get(id);
            Pack newPack = proposed.get(id);
            if (newPack == null) {
                rollbackPendingChanges(oldPack);
                if (oldPack != null && oldPack.enabled) unloadPack(oldPack);
                continue;
            }
            if (!newPack.enabled) {
                clearPendingChanges(newPack);
                if (oldPack != null && oldPack.enabled) unloadPack(oldPack);
                continue;
            }
            ContentPackValidation validation = fullValidation(newPack);
            if (!validation.valid()) {
                if (!rollbackPendingChanges(newPack)) restorePackContents(newPack, previousContents);
                if (oldPack != null && oldPack.enabled) {
                    // Keep both the old metadata and old specialist registrations.
                    packs.put(id, oldPack);
                }
                continue;
            }
            if (!applyPackReload(newPack)) {
                if (!rollbackPendingChanges(newPack)) restorePackContents(newPack, previousContents);
                if (oldPack != null && oldPack.enabled) {
                    packs.put(id, oldPack);
                    applyPackReload(oldPack);
                } else {
                    unloadPack(newPack);
                }
            } else {
                clearPendingChanges(newPack);
            }
        }
    }

    @Override
    public void onDisable() {
        configSubscriptions.values().forEach(ConfigSubscription::close);
        configSubscriptions.clear();
        pendingExternalChanges.clear();
        saveState();
        packs.clear();
        resourcePackChanges.clear();
        handlers.clear();
    }

    @Override
    public @NotNull List<ContentPackInfo> packs() {
        return packs.values().stream().map(Pack::info).toList();
    }

    @Override
    public @Nullable ContentPackInfo find(@NotNull String id) {
        Pack pack = packs.get(normalizeId(id));
        return pack == null ? null : pack.info();
    }

    @Override
    public @NotNull ContentPackValidation validate(@NotNull String id) {
        Pack pack = packs.get(normalizeId(id));
        if (pack == null) {
            return new ContentPackValidation(id, false, List.of("Pack not found: " + id), List.of());
        }
        return fullValidation(pack);
    }

    /** Validate every discovered pack and return the errors keyed by pack ID. */
    public @NotNull Map<String, ContentPackValidation> validateAll() {
        Map<String, ContentPackValidation> result = new LinkedHashMap<>();
        for (Pack pack : packs.values()) result.put(pack.id, fullValidation(pack));
        return result;
    }

    @Override
    public boolean reload(@NotNull String id) {
        String normalized = normalizeId(id);
        Pack existing = packs.get(normalized);
        if (existing == null) return false;
        Map<String, String> previousContents = snapshotPackContents(List.of(existing));
        Pack candidate = readPack(existing.directory);
        packs.put(normalized, candidate);
        ContentPackValidation validation = fullValidation(candidate);
        if (!validation.valid()) {
            restorePackContents(candidate, previousContents);
            packs.put(normalized, existing);
            plugin.getLogger().warning("[content-packs] Refusing to reload '" + normalized
                + "'; previous working state retained: " + String.join(" | ", validation.errors()));
            return false;
        }
        if (applyPackReload(candidate)) {
            clearPendingChanges(candidate);
            return true;
        }
        restorePackContents(candidate, previousContents);
        packs.put(normalized, existing);
        applyPackReload(existing);
        return false;
    }

    @Override
    public boolean enable(@NotNull String id) {
        String normalized = normalizeId(id);
        Pack pack = packs.get(normalized);
        if (pack == null) return false;
        boolean wasEnabled = pack.enabled;
        Boolean previousState = state.get(normalized);
        state.put(normalized, true);
        saveState();
        pack.enabled = true;
        ContentPackValidation validation = fullValidation(pack);
        if (!validation.valid()) {
            pack.enabled = wasEnabled;
            if (previousState == null) state.remove(normalized);
            else state.put(normalized, previousState);
            saveState();
            return false;
        }
        if (wasEnabled && pack.valid) return true;
        if (applyPackReload(pack)) return true;
        // Specialist loaders may have partially reconciled before reporting a
        // failure. Remove that partial state and leave the pack disabled.
        unloadPack(pack);
        pack.enabled = wasEnabled;
        if (previousState == null) state.remove(normalized);
        else state.put(normalized, previousState);
        saveState();
        return false;
    }

    @Override
    public boolean disable(@NotNull String id) {
        String normalized = normalizeId(id);
        Pack pack = packs.get(normalized);
        if (pack == null) return false;
        for (Pack other : packs.values()) {
            if (other == pack || !other.enabled) continue;
            if (other.dependencies.stream().anyMatch(dependency -> {
                try {
                    return normalizeId(dependency).equals(pack.id);
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            })) {
                plugin.getLogger().warning("[content-packs] Cannot disable '" + normalized
                    + "' while enabled pack '" + other.id + "' depends on it.");
                return false;
            }
        }
        pack.enabled = false;
        state.put(normalized, false);
        saveState();
        return unloadPack(pack);
    }

    @Override
    public boolean delete(@NotNull String id) {
        String normalized = normalizeId(id);
        Pack pack = packs.get(normalized);
        if (pack == null || !pack.directory.isDirectory()) return false;
        if (pack.enabled && !disable(normalized)) return false;
        Path trashRoot = new File(plugin.getDataFolder(), TRASH_DIRECTORY).toPath();
        try {
            Files.createDirectories(trashRoot);
            Path destination = trashRoot.resolve(normalized + "-" + System.currentTimeMillis());
            Files.move(pack.directory.toPath(), destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            try {
                Path destination = trashRoot.resolve(normalized + "-" + System.currentTimeMillis());
                Files.move(pack.directory.toPath(), destination);
            } catch (IOException failure) {
                plugin.getLogger().warning("[content-packs] Could not move '" + normalized
                    + "' to recoverable trash: " + failure.getMessage());
                return false;
            }
        }
        packs.remove(normalized);
        state.remove(normalized);
        saveState();
        return true;
    }

    @Override
    public boolean create(@NotNull String id, @Nullable String template) {
        String normalized = normalizeId(id);
        if (packs.containsKey(normalized) || packRoot == null) return false;
        File directory = new File(packRoot, normalized);
        if (directory.exists()) return false;
        try {
            Files.createDirectories(directory.toPath().resolve("configs"));
            Files.createDirectories(directory.toPath().resolve("textures"));
            String normalizedTemplate = template == null ? "" : template.trim().toLowerCase(Locale.ROOT);
            if (Set.of("workshop", "example", "stemcraft-workshop").contains(normalizedTemplate)) {
                File bundled = new File(packRoot, "stemcraft-workshop");
                if (bundled.isDirectory()) copyDirectory(bundled.toPath(), directory.toPath());
            }
            String namespace = normalized.replace('-', '_');
            String content = "pack:\n"
                + "  enabled: false\n"
                + "  namespace: " + namespace + "\n"
                + "  description: \"Workshop content pack\"\n"
                + "  dependencies: []\n\n"
                + "# Add custom-items, recipes, loot, and abilities under configs/*.yml.\n";
            Files.writeString(directory.toPath().resolve("config.yml"), content);
        } catch (IOException exception) {
            return false;
        }
        state.put(normalized, false);
        saveState();
        discover();
        return true;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path directory,
                                                                    java.nio.file.attribute.BasicFileAttributes attributes)
                throws IOException {
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                                                            java.nio.file.attribute.BasicFileAttributes attributes)
                throws IOException {
                Path destination = target.resolve(source.relativize(file));
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public @NotNull List<String> texturePaths(@NotNull String id) {
        Pack pack = packs.get(normalizeId(id));
        if (pack == null) return List.of();
        File textures = new File(pack.directory, "textures");
        if (!textures.isDirectory()) return List.of();
        List<String> result = new ArrayList<>();
        collectTextures(textures, textures, result);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(result);
    }

    /** Return a safe snapshot of one pack document for the shared web editor. */
    DocumentSnapshot editorDocument(@NotNull String packId, @NotNull String relativePath) {
        ContentPackDocument document = findDocument(packId, relativePath);
        if (document == null || !document.file().isFile()) return null;
        try {
            return new DocumentSnapshot(document.packId(), documentPath(document),
                Files.readString(document.file().toPath()), document.config().revision());
        } catch (IOException exception) {
            return null;
        }
    }

    /** Save one pack document, then validate and reload the owning pack. */
    DocumentSaveResult saveEditorDocument(@NotNull String packId, @NotNull String relativePath,
                                          @NotNull String content, String expectedRevision) {
        ContentPackDocument document = findDocument(packId, relativePath);
        if (document == null || !document.file().isFile()) return new DocumentSaveResult(false, "Document not found.", null);
        String currentRevision = document.config().revision();
        if (expectedRevision != null && !expectedRevision.isBlank() && !expectedRevision.equals(currentRevision))
            return new DocumentSaveResult(false, "This document changed elsewhere. Reload it before saving.", currentRevision);

        String previous;
        try {
            previous = Files.readString(document.file().toPath());
        } catch (IOException exception) {
            return new DocumentSaveResult(false, "Could not write the document: " + exception.getMessage(), currentRevision);
        }

        ConfigSaveResult saveResult = document.config().saveYaml(content, expectedRevision);
        if (!saveResult.success()) {
            return new DocumentSaveResult(false, saveResult.message(), saveResult.currentDiskRevision());
        }
        if (!reload(packId)) {
            ConfigSaveResult restoreResult = document.config().saveYaml(previous, "");
            if (!restoreResult.success()) plugin.getLogger().warning("[content-packs] Could not restore '"
                + document.file() + "' after a failed web save: " + restoreResult.message());
            reload(packId);
            return new DocumentSaveResult(false, "The document was rejected; the previous working version was restored.",
                document.config().revision());
        }
        return new DocumentSaveResult(true, "Saved and reloaded " + documentPath(document) + ".", document.config().revision());
    }

    /** Return a mutable configuration value for a structured editor operation. */
    Object editorValue(@NotNull String packId, @NotNull String relativePath, @NotNull String path) {
        ContentPackDocument document = findDocument(packId, relativePath);
        return document == null ? null : document.config().get(path);
    }

    /** Create a new config document inside a pack, then rediscover it safely. */
    DocumentSnapshot createEditorDocument(@NotNull String packId, @NotNull String relativePath,
                                          @NotNull String content) {
        Pack pack;
        try {
            pack = packs.get(normalizeId(packId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (pack == null || relativePath.isBlank()) return null;
        String normalizedPath = relativePath.replace('\\', '/');
        if (!normalizedPath.startsWith("configs/") || normalizedPath.contains("../")
                || normalizedPath.contains("..\\") || !normalizedPath.endsWith(".yml")) return null;
        Path root = pack.directory.toPath().toAbsolutePath().normalize();
        Path target = root.resolve(normalizedPath).normalize();
        if (!target.startsWith(root) || Files.exists(target)) return null;
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
        boolean wasEnabled = pack.enabled;
        discover();
        if (wasEnabled && !reload(pack.id)) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            discover();
            if (wasEnabled) reload(pack.id);
            return null;
        }
        return editorDocument(pack.id, normalizedPath);
    }

    /** Update one structured configuration value, with the same revision guard as document saves. */
    DocumentSaveResult saveEditorValue(@NotNull String packId, @NotNull String relativePath,
                                       @NotNull String path, @NotNull Object value,
                                       String expectedRevision) {
        ContentPackDocument document = findDocument(packId, relativePath);
        if (document == null || !document.file().isFile()) return new DocumentSaveResult(false, "Document not found.", null);
        String currentRevision = document.config().revision();
        if (expectedRevision != null && !expectedRevision.isBlank() && !expectedRevision.equals(currentRevision))
            return new DocumentSaveResult(false, "This document changed elsewhere. Reload it before saving.", currentRevision);
        String previous;
        try {
            previous = Files.readString(document.file().toPath());
            document.config().set(path, value);
        } catch (IOException | RuntimeException exception) {
            return new DocumentSaveResult(false, "Could not write the document: " + exception.getMessage(), currentRevision);
        }
        ConfigSaveResult saveResult = document.config().saveChecked();
        if (!saveResult.success()) {
            document.config().saveYaml(previous, "");
            return new DocumentSaveResult(false, saveResult.message(), saveResult.currentDiskRevision());
        }
        if (!reload(packId)) {
            ConfigSaveResult restoreResult = document.config().saveYaml(previous, "");
            if (!restoreResult.success()) plugin.getLogger().warning("[content-packs] Could not restore '"
                + document.file() + "' after a failed structured save: " + restoreResult.message());
            reload(packId);
            return new DocumentSaveResult(false, "The change was rejected; the previous working version was restored.",
                document.config().revision());
        }
        return new DocumentSaveResult(true, "Saved and reloaded " + documentPath(document) + ".", document.config().revision());
    }

    private EditorWebResponse handleEditorRequest(EditorWebContext context, String path, WebServiceRequest request) {
        try {
            if ("/data".equals(path) && "GET".equalsIgnoreCase(request.method())) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("kind", "packs");
                data.put("packs", packs().stream().map(info -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", info.id());
                    value.put("namespace", info.namespace());
                    value.put("description", info.description());
                    value.put("enabled", info.enabled());
                    value.put("bundled", info.bundled());
                    value.put("dependencies", info.dependencies());
                    value.put("valid", info.valid());
                    value.put("errors", info.validationErrors());
                    value.put("resourcePackRebuildRequired", info.resourcePackRebuildRequired());
                    return value;
                }).toList());
                data.put("documents", editorDocuments());
                return json(200, data);
            }
            if ("/document".equals(path)) {
                String pack = request.queryParam("pack");
                String file = request.queryParam("file");
                if ("GET".equalsIgnoreCase(request.method())) {
                    DocumentSnapshot snapshot = editorDocument(pack, file);
                    if (snapshot == null) return text(404, "Document not found.");
                    return json(200, snapshot);
                }
                if ("PUT".equalsIgnoreCase(request.method())) {
                    if (!context.writable()) return text(403, "This link is read-only.");
                    Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                    String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
                    String revision = body.get("revision") == null ? "" : String.valueOf(body.get("revision"));
                    DocumentSaveResult result = saveEditorDocument(pack, file, content, revision);
                    return json(result.success() ? 200 : 409, result);
                }
            }
            if ("/action".equals(path) && "POST".equalsIgnoreCase(request.method())) {
                if (!context.writable()) return text(403, "This link is read-only.");
                Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                String action = body.get("action") == null ? "" : String.valueOf(body.get("action"));
                String id = body.get("id") == null ? "" : String.valueOf(body.get("id"));
                boolean success = switch (action.toLowerCase(Locale.ROOT)) {
                    case "create" -> create(id, body.get("template") == null ? null : String.valueOf(body.get("template")));
                    case "enable" -> enable(id);
                    case "disable" -> disable(id);
                    case "delete" -> delete(id);
                    case "reload" -> reload(id);
                    case "validate" -> true;
                    default -> false;
                };
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", success);
                result.put("action", action);
                result.put("id", id);
                if ("validate".equalsIgnoreCase(action)) {
                    ContentPackValidation validation = validate(id);
                    result.put("success", validation.valid());
                    result.put("errors", validation.errors());
                    result.put("warnings", validation.warnings());
                }
                result.put("message", success ? "Done!" : "That pack action could not be completed.");
                return json(success ? 200 : 400, result);
            }
            return text(405, "Method not allowed.");
        } catch (IllegalArgumentException exception) {
            return text(400, exception.getMessage() == null ? "Invalid request." : exception.getMessage());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[content-packs] Web editor request failed: " + exception.getMessage());
            return text(500, "The pack editor could not complete that action.");
        }
    }

    /** Service-owned module metadata; the shared editor renders the result. */
    private final class ContentPackEditorModule implements EditorWebModule {
        @Override public @NotNull String id() { return "packs"; }
        @Override public @NotNull String title() { return "Packs"; }
        @Override public @NotNull String icon() { return "P"; }
        @Override public @NotNull String description() { return "Manage content packs and their files."; }
        @Override public @NotNull String readPermission() { return "stemcraft.command.stemcraft.pack"; }
        @Override public @NotNull String writePermission() { return "stemcraft.command.stemcraft.pack"; }

        @Override public @NotNull EditorWebView view(@NotNull EditorWebContext context,
                                                      @NotNull WebServiceRequest request) {
            return contentPackEditorView(context, request);
        }

        @Override public @NotNull EditorWebResponse handle(@NotNull EditorWebContext context, @NotNull String path,
                                                            @NotNull WebServiceRequest request) {
            return handleEditorRequest(context, path, request);
        }
    }

    private @NotNull EditorWebView contentPackEditorView(@NotNull EditorWebContext context,
                                                          @NotNull WebServiceRequest request) {
        String selectedId = request.queryParam("pack");
        if (selectedId == null || selectedId.isBlank()) {
            List<EditorWebItem> items = packs().stream()
                .map(info -> new EditorWebItem(info.id(), info.id(),
                    info.description().isBlank() ? "Content pack" : info.description(),
                    packBadges(info),
                    EditorWebAction.navigate("open-" + info.id(), "Open", Map.of("pack", info.id()))))
                .toList();
            List<EditorWebAction> actions = context.writable()
                ? List.of(createPackAction()) : List.of();
            return new EditorWebView("packs", "Content packs", "", context.writable(), List.of(
                EditorWebComponent.of("toolbar", Map.of(
                    "actions", actions,
                    "meta", packs().size() + " pack" + (packs().size() == 1 ? "" : "s"))),
                EditorWebComponent.of("cards", Map.of("items", items))
            ));
        }

        ContentPackInfo info = find(selectedId);
        if (info == null) {
            return new EditorWebView("packs", "Content packs", "", context.writable(), List.of(
                EditorWebComponent.of("subview", Map.of(
                    "title", selectedId,
                    "back", EditorWebAction.navigate("back", "Back", Map.of()))),
                EditorWebComponent.of("empty", Map.of("text", "Content pack not found."))
            ));
        }

        List<EditorWebAction> actions = context.writable() ? packActions(info) : List.of();
        List<EditorWebOption> documents = editorDocuments().stream()
            .filter(document -> info.id().equals(document.get("pack")))
            .map(document -> new EditorWebOption(String.valueOf(document.get("file")),
                String.valueOf(document.get("label"))))
            .toList();
        List<EditorWebComponent> components = new ArrayList<>();
        components.add(EditorWebComponent.of("subview", Map.of(
            "title", info.id(),
            "back", EditorWebAction.navigate("back", "Back", Map.of()))));
        components.add(EditorWebComponent.of("card", Map.of(
            "title", info.id(),
            "description", info.description().isBlank() ? "Content pack" : info.description(),
            "badges", packBadges(info),
            "actions", actions,
            "children", List.of(
                EditorWebComponent.of("validation", Map.of(
                    "errors", info.validationErrors(),
                    "empty", "No validation issues found.")),
                EditorWebComponent.of("meta", Map.of(
                    "dependencies", info.dependencies(),
                    "resourcePackRebuildRequired", info.resourcePackRebuildRequired()))
            ))));
        components.add(EditorWebComponent.of("card", Map.of(
            "title", "Pack files",
            "description", "Open a configuration file in its own editor view.",
            "children", List.of(EditorWebComponent.of("document-picker", Map.of(
                "module", "packs",
                "pack", info.id(),
                "options", documents,
                "empty", "No editable files"))))));
        return new EditorWebView("packs", info.id(), "", context.writable(), components);
    }

    private @NotNull EditorWebAction createPackAction() {
        EditorWebDialog dialog = new EditorWebDialog("New content pack", "Create pack", List.of(
            EditorWebField.text("id", "Pack ID", "my-pack", true, "[a-z0-9][a-z0-9_-]*"),
            EditorWebField.select("template", "Template", List.of(
                new EditorWebOption("", "Blank pack"),
                new EditorWebOption("workshop", "Workshop example")))
        ));
        return EditorWebAction.dialog("create", "New pack", "primary", "action",
            Map.of("action", "create"), dialog);
    }

    private @NotNull List<EditorWebAction> packActions(@NotNull ContentPackInfo info) {
        List<EditorWebAction> actions = new ArrayList<>();
        actions.add(EditorWebAction.request("validate", "Validate", "default", "action",
            Map.of("action", "validate", "id", info.id())));
        actions.add(EditorWebAction.request("reload", "Reload pack", "default", "action",
            Map.of("action", "reload", "id", info.id())));
        actions.add(EditorWebAction.request(info.enabled() ? "disable" : "enable",
            info.enabled() ? "Disable" : "Enable", info.enabled() ? "warn" : "good", "action",
            Map.of("action", info.enabled() ? "disable" : "enable", "id", info.id())));
        actions.add(EditorWebAction.request("delete", "Delete", "danger", "action",
            Map.of("action", "delete", "id", info.id())));
        return List.copyOf(actions);
    }

    private @NotNull List<EditorWebBadge> packBadges(@NotNull ContentPackInfo info) {
        List<EditorWebBadge> badges = new ArrayList<>();
        if (!info.valid()) badges.add(new EditorWebBadge("Needs attention", "bad"));
        else if (info.enabled()) badges.add(new EditorWebBadge("Enabled", "good"));
        else badges.add(new EditorWebBadge("Disabled", "default"));
        if (!info.validationErrors().isEmpty()) {
            int count = info.validationErrors().size();
            badges.add(new EditorWebBadge(count + " validation issue" + (count == 1 ? "" : "s"), "default"));
        }
        return List.copyOf(badges);
    }

    private List<Map<String, Object>> editorDocuments() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Pack pack : packs.values()) {
            for (ContentPackDocument document : pack.documents) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("pack", pack.id);
                value.put("file", documentPath(document));
                value.put("label", pack.id + " / " + documentPath(document));
                value.put("hasItems", !document.config().getMap("custom-items", false).isEmpty());
                value.put("hasLoot", !document.config().getMap("loot.tables", false).isEmpty());
                result.add(value);
            }
        }
        return result;
    }

    private ContentPackDocument findDocument(String packId, String relativePath) {
        if (packId == null || relativePath == null || relativePath.isBlank()) return null;
        try {
            String normalizedPack = normalizeId(packId);
            String normalizedPath = relativePath.replace('\\', '/');
            if (normalizedPath.startsWith("/") || normalizedPath.contains("../") || normalizedPath.contains("..\\")) return null;
            return packs.values().stream().filter(pack -> pack.id.equals(normalizedPack))
                .flatMap(pack -> pack.documents.stream())
                .filter(document -> documentPath(document).equals(normalizedPath))
                .findFirst().orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String documentPath(ContentPackDocument document) {
        return document.packDirectory().toPath().relativize(document.file().toPath()).toString()
            .replace(java.io.File.separatorChar, '/');
    }

    private static EditorWebResponse json(int status, Object value) {
        return new EditorWebResponse(status, "application/json; charset=utf-8", EDITOR_GSON.toJson(value));
    }

    private static EditorWebResponse text(int status, String value) {
        return new EditorWebResponse(status, "text/plain; charset=utf-8", value == null ? "" : value);
    }

    public void markResourcePackChange(@NotNull String id) {
        resourcePackChanges.put(normalizeId(id), true);
    }

    /** Register a specialist loader without coupling this service to its schema. */
    public void registerHandler(@NotNull String id, @NotNull Function<String, Boolean> reload,
                                @NotNull Consumer<String> unload) {
        registerHandler(id, reload, unload, ignored -> List.of());
    }

    /** Register a loader and its side-effect-free proposed-state validator. */
    public void registerHandler(@NotNull String id, @NotNull Function<String, Boolean> reload,
                                @NotNull Consumer<String> unload,
                                @NotNull Function<String, List<String>> validate) {
        handlers.put(normalizeId(id), new Handler(reload, unload, validate));
    }

    /** Documents from all currently enabled packs, in deterministic order. */
    public @NotNull List<ContentPackDocument> enabledDocuments() {
        return packs.values().stream()
            .filter(pack -> pack.enabled && pack.valid)
            .flatMap(pack -> pack.documents.stream())
            .toList();
    }

    public @NotNull List<ContentPackDocument> documents(@NotNull String id) {
        Pack pack = packs.get(normalizeId(id));
        return pack == null ? List.of() : List.copyOf(pack.documents);
    }

    /** Revalidate and activate all enabled packs after optional features register providers. */
    public void activateEnabledPacks() {
        for (Pack pack : packs.values()) {
            if (!pack.enabled) continue;
            ContentPackValidation validation = fullValidation(pack);
            if (!validation.valid()) {
                plugin.getLogger().warning("[content-packs] Pack '" + pack.id + "' remains inactive: "
                    + String.join(" | ", validation.errors()));
                continue;
            }
            if (!applyPackReload(pack)) {
                plugin.getLogger().warning("[content-packs] Pack '" + pack.id
                    + "' could not activate; specialist registration failed.");
            }
        }
    }

    private void discover() {
        if (packRoot == null) return;
        File[] directories = packRoot.listFiles(File::isDirectory);
        Map<String, Pack> discovered = new LinkedHashMap<>();
        if (directories != null) {
            java.util.Arrays.sort(directories, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File directory : directories) {
                if (directory.getName().startsWith(".")) continue;
                String id;
                try { id = normalizeId(directory.getName()); }
                catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("[content-packs] Ignoring invalid directory '" + directory.getName() + "'.");
                    continue;
                }
                discovered.put(id, readPack(directory));
            }
        }
        packs.clear();
        packs.putAll(discovered);
        reconcileConfigSubscriptions();
        reportValidation(validateAll());
    }

    /**
     * Content packs opt into the common ConfigService monitor. The service
     * still owns validation and reconciliation; this callback only schedules
     * the existing transactional pack lifecycle on the next server tick.
     */
    private void reconcileConfigSubscriptions() {
        Set<String> desired = new LinkedHashSet<>();
        for (Pack pack : packs.values()) {
            for (ContentPackDocument document : pack.documents) {
                String key = configFileKey(document.file());
                desired.add(key);
                if (configSubscriptions.containsKey(key)) continue;
                api.config().watch(document.file());
                ConfigSubscription subscription = api.config().onChange(document.file(), event -> {
                    if (event.origin() != ConfigChangeOrigin.EXTERNAL) return;
                    rememberExternalChange(document, event);
                    schedulePackReload();
                });
                configSubscriptions.put(key, subscription);
            }
        }

        configSubscriptions.entrySet().removeIf(entry -> {
            if (desired.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
    }

    private void schedulePackReload() {
        if (reloadQueued) return;
        reloadQueued = true;
        Runnable reload = () -> {
            reloadQueued = false;
            onReload();
        };
        if (api.tasks() == null) reload.run();
        else api.tasks().nextTick(reload);
    }

    private static String configFileKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException exception) {
            return file.getAbsolutePath();
        }
    }

    private void rememberExternalChange(ContentPackDocument document,
                                        dev.stemcraft.api.service.config.ConfigChangeEvent event) {
        String key = configFileKey(document.file());
        Map<String, Object> oldValues = pendingExternalChanges.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        event.oldValues().forEach(oldValues::putIfAbsent);
    }

    /** Restore a semantically rejected YAML change through ConfigService. */
    private boolean rollbackPendingChanges(Pack pack) {
        if (pack == null) return false;
        boolean restored = false;
        for (ContentPackDocument document : pack.documents) {
            String key = configFileKey(document.file());
            Map<String, Object> oldValues = pendingExternalChanges.remove(key);
            if (oldValues == null || oldValues.isEmpty()) continue;
            restored = true;

            for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
                if (entry.getValue() == null) document.config().remove(entry.getKey());
                else document.config().set(entry.getKey(), entry.getValue());
            }
            ConfigSaveResult result = document.config().saveChecked();
            if (!result.success()) {
                plugin.getLogger().warning("[content-packs] Could not restore rejected change in "
                    + document.file() + ": " + result.message());
            }
        }
        return restored;
    }

    private void clearPendingChanges(Pack pack) {
        if (pack == null) return;
        for (ContentPackDocument document : pack.documents) {
            pendingExternalChanges.remove(configFileKey(document.file()));
        }
    }

    private Map<String, String> snapshotPackContents(Collection<Pack> source) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Pack pack : source) {
            for (ContentPackDocument document : pack.documents) {
                try {
                    result.put(configFileKey(document.file()), Files.readString(document.file().toPath()));
                } catch (IOException ignored) {
                    // A missing document is handled by the normal discovery validation.
                }
            }
        }
        return result;
    }

    private void restorePackContents(Pack pack, Map<String, String> previousContents) {
        if (pack == null) return;
        for (ContentPackDocument document : pack.documents) {
            String previous = previousContents.get(configFileKey(document.file()));
            if (previous == null) continue;
            ConfigSaveResult result = document.config().saveYaml(previous, "");
            if (!result.success()) {
                plugin.getLogger().warning("[content-packs] Could not restore rejected configuration '"
                    + document.file() + "': " + result.message());
            }
        }
    }

    private void reportValidation(Map<String, ContentPackValidation> validations) {
        validations.values().stream()
            .filter(validation -> !validation.valid())
            .forEach(validation -> plugin.getLogger().warning("[content-packs] " + validation.packId()
                + " is inactive: " + String.join(" | ", validation.errors())));
    }

    private Pack readPack(File directory) {
        String id = normalizeId(directory.getName());
        List<String> discoveryErrors = new ArrayList<>();
        File rootFile = new File(directory, "config.yml");
        ConfigFile root = null;
        if (rootFile.isFile()) {
            ConfigFile loaded = api.config().load(rootFile);
            if (loaded != null && loaded.reload()) {
                root = loaded;
            } else {
                discoveryErrors.add(id + "/config.yml: invalid YAML or could not be loaded. Fix the file syntax and reload the pack.");
            }
        } else {
            discoveryErrors.add(id + "/config.yml: missing pack metadata file. Create it with a pack section.");
        }
        String namespace = root == null ? id.replace('-', '_')
            : root.getString("pack.namespace", id.replace('-', '_')).trim();
        if (namespace.isBlank()) namespace = id.replace('-', '_');
        String description = root == null ? "" : root.getString("pack.description", "");
        boolean configuredEnabled = root == null || root.getBoolean("pack.enabled", true);
        boolean enabled = state.getOrDefault(id, configuredEnabled);
        List<String> dependencies = root == null ? List.of() : root.getStringList("pack.dependencies");
        List<ContentPackDocument> documents = new ArrayList<>();
        if (root != null) documents.add(new ContentPackDocument(id, namespace, directory, rootFile, root));
        File configs = new File(directory, "configs");
        File[] files = configs.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                ConfigFile config = api.config().load(file);
                if (config != null && config.reload()) {
                    documents.add(new ContentPackDocument(id, namespace, directory, file, config));
                } else {
                    discoveryErrors.add(id + "/" + file.getName()
                        + ": invalid YAML or could not be loaded. Fix the file syntax and reload the pack.");
                }
            }
        }
        return new Pack(id, namespace, description, directory, enabled, root != null, dependencies, documents,
            discoveryErrors);
    }

    private ContentPackValidation validatePack(Pack pack, boolean includeInactive) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        errors.addAll(pack.discoveryErrors);
        if (!includeInactive && !pack.enabled) return new ContentPackValidation(pack.id, true, errors, warnings);
        if (pack.namespace.isBlank()) {
            errors.add(pack.id + ": pack.namespace must not be blank.");
        } else if (!pack.namespace.matches("[a-z0-9][a-z0-9._-]*")) {
            errors.add(pack.id + ": pack.namespace must use lowercase letters, numbers, dots, hyphens, or underscores.");
        }
        for (String dependency : pack.dependencies) {
            String normalized;
            try { normalized = normalizeId(dependency); }
            catch (IllegalArgumentException exception) {
                errors.add(pack.id + ": invalid dependency '" + dependency + "'.");
                continue;
            }
            Pack dependencyPack = packs.get(normalized);
            if (dependencyPack == null) errors.add(pack.id + ": missing dependency '" + normalized + "'.");
            else if (!dependencyPack.enabled) errors.add(pack.id + ": dependency '" + normalized + "' is disabled.");
        }
        Set<String> knownPotions = knownPotionDefinitions(pack);
        for (ContentPackDocument document : pack.documents) {
            String source = pack.id + "/" + document.file().getName();
            errors.addAll(validateItems(document.config().getMap("custom-items", false),
                source + ".custom-items", document, knownPotions));
            errors.addAll(validatePotionDefinitions(document.config().getMap("potions", false),
                source + ".potions"));
            if (document.config().isSection("loot")) {
                errors.addAll(validateLootConditions(document.config().getMap("loot.tables", false),
                    source + ".loot.tables"));
            }
            errors.addAll(validateDifficultyProfiles(document.config().getMap("difficulty-profiles", false),
                source + ".difficulty-profiles"));
        }
        pack.valid = errors.isEmpty();
        return new ContentPackValidation(pack.id, errors.isEmpty(), errors, warnings);
    }

    private List<String> validateItems(Map<String, Object> items, String path, ContentPackDocument document,
                                       Set<String> knownPotions) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> entry : items.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> item)) {
                errors.add(path + "." + entry.getKey() + " must be a mapping.");
                continue;
            }
            String material = String.valueOf(item.containsKey("material") ? item.get("material") : "");
            if (Material.matchMaterial(material.trim().toUpperCase(Locale.ROOT)) == null)
                errors.add(path + "." + entry.getKey() + ".material is not a valid item material: " + material);
            String placement = String.valueOf(item.containsKey("placement") ? item.get("placement") : "DENY");
            if (!Set.of("DENY", "VANILLA", "ALLOW").contains(placement.toUpperCase(Locale.ROOT)))
                errors.add(path + "." + entry.getKey() + ".placement must be DENY, VANILLA, or ALLOW.");
            Object maxDamage = item.get("max-damage");
            if (maxDamage != null && (!(maxDamage instanceof Number number) || number.intValue() <= 0))
                errors.add(path + "." + entry.getKey() + ".max-damage must be a positive number.");
            Object stats = item.get("stats");
            if (stats instanceof Map<?, ?> statMap) {
                for (String field : List.of("attack-damage", "attack-speed", "armor", "toughness")) {
                    Object value = statMap.get(field);
                    if (value != null && (!(value instanceof Number number)
                        || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0D)) {
                        errors.add(path + "." + entry.getKey() + ".stats." + field
                            + " must be a finite non-negative final total.");
                    }
                }
            } else if (stats != null) {
                errors.add(path + "." + entry.getKey() + ".stats must be a mapping.");
            }
            Object colour = item.containsKey("color") ? item.get("color") : item.get("colour");
            if (colour != null && !String.valueOf(colour).replace("#", "").matches("[0-9a-fA-F]{6}"))
                errors.add(path + "." + entry.getKey() + ".color must be #RRGGBB.");
            Object texture = item.get("texture");
            if (texture != null && !String.valueOf(texture).isBlank()) {
                errors.addAll(validateTextureReference(document, String.valueOf(texture),
                    path + "." + entry.getKey() + ".texture"));
            }
            Object enchantments = item.get("enchantments");
            if (enchantments instanceof Map<?, ?> map) {
                for (Object rawId : map.keySet()) {
                    String id = String.valueOf(rawId);
                    NamespacedKey key = NamespacedKey.fromString(id.contains(":") ? id : "minecraft:" + id);
                    if (key == null || RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key) == null)
                        errors.add(path + "." + entry.getKey() + ".enchantments." + id + " is unknown.");
                }
            }
            Object potion = item.get("potion");
            if (potion instanceof Map<?, ?> potionMap && potionMap.get("effects") instanceof List<?> effects) {
                for (int index = 0; index < effects.size(); index++) {
                    if (!(effects.get(index) instanceof Map<?, ?> effect)) continue;
                    String type = String.valueOf(effect.containsKey("type") ? effect.get("type") : "");
                    if (Registry.MOB_EFFECT.get(NamespacedKey.minecraft(type.toLowerCase(Locale.ROOT))) == null)
                        errors.add(path + "." + entry.getKey() + ".potion.effects[" + index + "].type is unknown.");
                }
            } else if (potion instanceof String reference && !reference.isBlank()
                && !knownPotions.contains(normalizeReference(reference))) {
                errors.add(path + "." + entry.getKey() + ".potion references unknown potion definition '"
                    + reference + "'. Define it under potions or use an inline effects mapping.");
            } else if (potion != null && !(potion instanceof Map<?, ?>) && !(potion instanceof String)) {
                errors.add(path + "." + entry.getKey() + ".potion must be a definition ID or mapping.");
            }
        }
        return errors;
    }

    private List<String> validatePotionDefinitions(Map<String, Object> definitions, String path) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            String itemPath = path + "." + entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> definition)) {
                errors.add(itemPath + " must be a mapping.");
                continue;
            }
            Object color = definition.containsKey("color") ? definition.get("color") : definition.get("colour");
            if (color != null && !String.valueOf(color).replace("#", "").matches("[0-9a-fA-F]{6}"))
                errors.add(itemPath + ".color must be #RRGGBB.");
            Object effects = definition.get("effects");
            if (!(effects instanceof List<?> list) || list.isEmpty()) {
                errors.add(itemPath + ".effects must contain at least one effect mapping.");
                continue;
            }
            for (int index = 0; index < list.size(); index++) {
                if (!(list.get(index) instanceof Map<?, ?> effect)) {
                    errors.add(itemPath + ".effects[" + index + "] must be a mapping.");
                    continue;
                }
                String type = String.valueOf(effect.containsKey("type") ? effect.get("type") : "");
                if (Registry.MOB_EFFECT.get(NamespacedKey.minecraft(type.toLowerCase(Locale.ROOT))) == null)
                    errors.add(itemPath + ".effects[" + index + "].type is unknown.");
                Object duration = effect.get("duration-seconds");
                if (duration != null && (!(duration instanceof Number number) || number.doubleValue() <= 0D))
                    errors.add(itemPath + ".effects[" + index + "].duration-seconds must be positive.");
                Object level = effect.get("level");
                if (level != null && (!(level instanceof Number number) || number.intValue() < 1))
                    errors.add(itemPath + ".effects[" + index + "].level must be at least 1.");
            }
        }
        return errors;
    }

    private List<String> validateTextureReference(ContentPackDocument document, String reference, String path) {
        String value = reference.trim().replace('\\', '/');
        int separator = value.indexOf(':');
        String namespace = separator >= 0 ? value.substring(0, separator) : document.namespace();
        String texture = separator >= 0 ? value.substring(separator + 1) : value;
        if (texture.startsWith("textures/")) texture = texture.substring("textures/".length());
        if (texture.toLowerCase(Locale.ROOT).endsWith(".png")) {
            texture = texture.substring(0, texture.length() - 4);
        }
        if (!namespace.matches("[a-z0-9][a-z0-9._-]*") || texture.isBlank()
            || texture.startsWith("/") || texture.contains("../") || texture.contains("\\..")) {
            return List.of(path + " must be a valid namespaced texture path (namespace:path).");
        }
        // Minecraft assets and existing STEMCraft packs may provide a texture
        // outside the new user-facing textures/ directory.
        if ("minecraft".equals(namespace)) return List.of();
        File modern = new File(document.packDirectory(), "textures/" + texture + ".png");
        File legacy = new File(document.packDirectory(),
            "contents/" + namespace + "/textures/" + texture + ".png");
        if (!modern.isFile() && !legacy.isFile()) {
            return List.of(path + " references missing PNG '" + value
                + "'. Add it under textures/ or contents/" + namespace + "/textures/.");
        }
        return List.of();
    }

    private ContentPackValidation fullValidation(Pack pack) {
        ContentPackValidation base = validatePack(pack, true);
        List<String> errors = new ArrayList<>(base.errors());
        if (pack.enabled) errors.addAll(duplicateErrors(pack));
        errors.addAll(referenceErrors(pack));
        for (Map.Entry<String, Handler> entry : handlers.entrySet()) {
            try {
                List<String> specialistErrors = entry.getValue().validate().apply(pack.id);
                if (specialistErrors != null) errors.addAll(specialistErrors);
            } catch (RuntimeException exception) {
                errors.add(pack.id + ": specialist validator '" + entry.getKey()
                    + "' failed: " + (exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage()));
            }
        }
        pack.valid = errors.isEmpty();
        return new ContentPackValidation(pack.id, errors.isEmpty(), errors, base.warnings());
    }

    private List<String> referenceErrors(Pack target) {
        Set<String> knownItems = knownCustomItems(target);
        List<String> errors = new ArrayList<>();
        for (ContentPackDocument document : target.documents) {
            String source = target.id + "/" + document.file().getName();
            Map<String, Object> recipes = document.config().getMap("recipes", false);
            for (String type : List.of("shaped", "shapeless", "furnace", "smoker", "blast_furnace", "campfire", "brewing")) {
                Object raw = recipes.get(type);
                if (!(raw instanceof Map<?, ?> definitions)) continue;
                for (Map.Entry<?, ?> definition : definitions.entrySet()) {
                    if (!(definition.getValue() instanceof Map<?, ?> values)) continue;
                    String path = source + ".recipes." + type + "." + definition.getKey();
                    validateReference(values.get("result"), knownItems, errors, path + ".result");
                    validateReference(values.get("input"), knownItems, errors,
                        path + ".input", "AWKWARD", "effect:");
                    validateReference(values.get("ingredient"), knownItems, errors, path + ".ingredient");
                    Object ingredients = values.get("ingredients");
                    if (ingredients instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> ingredient : map.entrySet()) {
                            validateReference(ingredient.getValue(), knownItems, errors,
                                path + ".ingredients." + ingredient.getKey());
                        }
                    } else if (ingredients instanceof Collection<?> collection) {
                        int index = 0;
                        for (Object ingredient : collection) {
                            validateReference(ingredient, knownItems, errors, path + ".ingredients[" + index++ + "]");
                        }
                    }
                }
            }

            Map<String, Object> tables = document.config().getMap("loot.tables", false);
            for (Map.Entry<String, Object> table : tables.entrySet()) {
                if (!(table.getValue() instanceof Map<?, ?> definition)) continue;
                Object rules = definition.get("rules");
                if (!(rules instanceof Collection<?> collection)) continue;
                int ruleIndex = 0;
                for (Object rawRule : collection) {
                    if (!(rawRule instanceof Map<?, ?> rule)) { ruleIndex++; continue; }
                    String path = source + ".loot.tables." + table.getKey() + ".rules[" + ruleIndex++ + "]";
                    validateReference(rule.get("match"), knownItems, errors, path + ".match");
                    Object entries = rule.get("entries");
                    if (entries instanceof Collection<?> entryCollection) {
                        int entryIndex = 0;
                        for (Object rawEntry : entryCollection) {
                            if (rawEntry instanceof Map<?, ?> entry) {
                                validateReference(entry.get("item"), knownItems, errors,
                                    path + ".entries[" + entryIndex + "].item");
                            }
                            entryIndex++;
                        }
                    }
                }
            }
        }
        return errors;
    }

    private Set<String> knownCustomItems(Pack target) {
        Set<String> known = new HashSet<>();
        ConfigFile root = api.config().load("config.yml");
        if (root != null) known.addAll(root.getMap("custom-items", false).keySet().stream()
            .map(ContentPackServiceImpl::normalizeReference).filter(value -> !value.isBlank()).toList());
        for (Pack pack : packs.values()) {
            if (pack != target && !pack.enabled) continue;
            for (ContentPackDocument document : pack.documents) {
                known.addAll(document.config().getMap("custom-items", false).keySet().stream()
                    .map(ContentPackServiceImpl::normalizeReference).filter(value -> !value.isBlank()).toList());
            }
        }
        return known;
    }

    private Set<String> knownPotionDefinitions(Pack target) {
        Set<String> known = new HashSet<>();
        ConfigFile root = api.config().load("config.yml");
        if (root != null) known.addAll(root.getMap("potions", false).keySet().stream()
            .map(ContentPackServiceImpl::normalizeReference).filter(value -> !value.isBlank()).toList());
        for (Pack pack : packs.values()) {
            if (pack != target && !pack.enabled) continue;
            for (ContentPackDocument document : pack.documents) {
                known.addAll(document.config().getMap("potions", false).keySet().stream()
                    .map(ContentPackServiceImpl::normalizeReference).filter(value -> !value.isBlank()).toList());
            }
        }
        return known;
    }

    private void validateReference(Object raw, Set<String> knownItems, List<String> errors, String path,
                                   String... allowedValues) {
        if (raw == null) return;
        String value = String.valueOf(raw).trim();
        if (value.isBlank() || List.of(allowedValues).stream().anyMatch(value::equalsIgnoreCase)
            || List.of(allowedValues).stream().anyMatch(prefix -> value.regionMatches(true, 0, prefix, 0, prefix.length()))) return;
        String normalized = normalizeReference(value);
        String material = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
        if (knownItems.contains(normalized)
            || Material.matchMaterial(material.trim().toUpperCase(Locale.ROOT)) != null) return;
        errors.add(path + " references unknown item or material '" + value + "'.");
    }

    private List<String> duplicateErrors(Pack target) {
        List<String> errors = new ArrayList<>();
        Map<String, String> itemOwners = new HashMap<>();
        Map<String, String> tableOwners = new HashMap<>();
        Map<String, String> recipeOwners = new HashMap<>();
        Map<String, String> potionOwners = new HashMap<>();
        for (Pack pack : packs.values()) {
            if (!pack.enabled) continue;
            for (ContentPackDocument document : pack.documents) {
                for (String id : document.config().getMap("custom-items", false).keySet()) {
                    duplicate(itemOwners, normalizeReference(id), pack.id + "/" + document.file().getName(), target,
                        "custom item").ifPresent(errors::add);
                }
                for (String id : document.config().getMap("potions", false).keySet()) {
                    duplicate(potionOwners, normalizeReference(id), pack.id + "/" + document.file().getName(), target,
                        "potion definition").ifPresent(errors::add);
                }
                for (String id : document.config().getMap("loot.tables", false).keySet()) {
                    duplicate(tableOwners, normalizeReference(id), pack.id + "/" + document.file().getName(), target,
                        "loot table").ifPresent(errors::add);
                }
                for (String section : List.of("shaped", "shapeless", "furnace", "smoker", "blast_furnace",
                    "campfire", "brewing", "smithing_transform", "smithing_trim")) {
                    for (String id : document.config().getMap("recipes." + section, false).keySet()) {
                        duplicate(recipeOwners, normalizeReference(id), pack.id + "/" + document.file().getName(), target,
                            "recipe").ifPresent(errors::add);
                    }
                }
            }
        }
        return errors;
    }

    private java.util.Optional<String> duplicate(Map<String, String> owners, String id, String source,
                                                  Pack target, String kind) {
        if (id.isBlank()) return java.util.Optional.empty();
        String previous = owners.putIfAbsent(id, source);
        if (previous == null || (!source.startsWith(target.id + "/") && !previous.startsWith(target.id + "/")))
            return java.util.Optional.empty();
        return java.util.Optional.of(target.id + ": duplicate " + kind + " ID '" + id
            + "' in " + previous + " and " + source + ". Rename one definition.");
    }

    private static String normalizeReference(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return (colon >= 0 ? id.substring(colon + 1) : id).trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private List<String> validateLootConditions(Map<String, Object> tables, String path) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> tableEntry : tables.entrySet()) {
            if (!(tableEntry.getValue() instanceof Map<?, ?> table)) {
                errors.add(path + "." + tableEntry.getKey() + " must be a mapping.");
                continue;
            }
            Object rulesValue = table.get("rules");
            if (!(rulesValue instanceof List<?> rules)) continue;
            for (int index = 0; index < rules.size(); index++) {
                Object rawRule = rules.get(index);
                if (!(rawRule instanceof Map<?, ?> rule)) {
                    errors.add(path + "." + tableEntry.getKey() + ".rules[" + index + "] must be a mapping.");
                    continue;
                }
                Object conditions = rule.get("conditions");
                if (!(conditions instanceof Map<?, ?> conditionMap)) continue;
                Map<String, Object> typed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> condition : conditionMap.entrySet()) {
                    if (condition.getKey() != null) typed.put(condition.getKey().toString(), condition.getValue());
                }
                errors.addAll(api.conditions().validate(typed,
                    path + "." + tableEntry.getKey() + ".rules[" + index + "].conditions"));
            }
        }
        return errors;
    }

    private List<String> validateDifficultyProfiles(Map<String, Object> profiles, String path) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> entry : profiles.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> profile)) {
                errors.add(path + "." + entry.getKey() + " must be a mapping.");
                continue;
            }
            Object range = profile.get("range");
            if (range != null && (!(range instanceof Number number) || number.doubleValue() <= 0D)) {
                errors.add(path + "." + entry.getKey() + ".range must be greater than zero.");
            }
            Object multiplier = profile.get("multiplier");
            if (multiplier != null && (!(multiplier instanceof Number number) || number.doubleValue() < 0D)) {
                errors.add(path + "." + entry.getKey() + ".multiplier must not be negative.");
            }
        }
        return errors;
    }

    private boolean applyPackReload(Pack pack) {
        // Specialist services own their registrations. This hook keeps the
        // central lifecycle transactional while allowing them to reconcile.
        boolean success = true;
        for (Map.Entry<String, Handler> entry : handlers.entrySet()) {
            try {
                success &= Boolean.TRUE.equals(entry.getValue().reload().apply(pack.id));
            } catch (RuntimeException exception) {
                success = false;
                plugin.getLogger().warning("[content-packs] Loader '" + entry.getKey()
                    + "' failed for '" + pack.id + "': " + exception.getMessage());
            }
        }
        return success;
    }

    private boolean unloadPack(Pack pack) {
        for (Handler handler : handlers.values()) {
            try { handler.unload().accept(pack.id); }
            catch (RuntimeException exception) {
                plugin.getLogger().warning("[content-packs] Loader unload failed for '" + pack.id
                    + "': " + exception.getMessage());
            }
        }
        return true;
    }

    private void loadState() {
        state.clear();
        stateConfig = api.config().load(STATE_FILE);
        if (stateConfig == null) return;
        ConfigSection enabled = stateConfig.getSection("enabled", false);
        if (enabled != null) for (String id : enabled.getKeys()) state.put(normalizeId(id), enabled.getBoolean(id, true));
    }

    private void saveState() {
        if (stateConfig == null) stateConfig = api.config().load(STATE_FILE);
        if (stateConfig == null) return;
        stateConfig.getSection("enabled").removeAll();
        for (Map.Entry<String, Boolean> entry : state.entrySet()) stateConfig.set("enabled." + entry.getKey(), entry.getValue());
        stateConfig.save();
    }

    private static void collectTextures(File root, File current, List<String> output) {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectTextures(root, child, output);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".png"))
                output.add(root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/'));
        }
    }

    private static String normalizeId(String id) {
        if (id == null) throw new IllegalArgumentException("Pack ID must not be blank");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]*"))
            throw new IllegalArgumentException("Pack IDs must use lowercase letters, numbers, hyphens, or underscores.");
        return normalized;
    }

    private final class Pack {
        private final String id;
        private final String namespace;
        private final String description;
        private final File directory;
        private boolean enabled;
        private final boolean bundled;
        private final List<String> dependencies;
        private final List<ContentPackDocument> documents;
        private final List<String> discoveryErrors;
        private boolean valid = true;

        private Pack(String id, String namespace, String description, File directory, boolean enabled,
                     boolean bundled, List<String> dependencies, List<ContentPackDocument> documents,
                     List<String> discoveryErrors) {
            this.id = id; this.namespace = namespace; this.description = description; this.directory = directory;
            this.enabled = enabled; this.bundled = bundled; this.dependencies = List.copyOf(dependencies);
            this.documents = List.copyOf(documents);
            this.discoveryErrors = List.copyOf(discoveryErrors);
        }

        private ContentPackInfo info() {
            ContentPackValidation validation = fullValidation(this);
            valid = validation.valid();
            return new ContentPackInfo(id, namespace, description, directory.getAbsolutePath(), enabled, bundled,
                dependencies, validation.errors(), resourcePackChanges.getOrDefault(id, false));
        }
    }

    private record Handler(Function<String, Boolean> reload, Consumer<String> unload,
                           Function<String, List<String>> validate) { }

    record DocumentSnapshot(String pack, String file, String content, String revision) { }

    record DocumentSaveResult(boolean success, String message, String revision) { }
}
