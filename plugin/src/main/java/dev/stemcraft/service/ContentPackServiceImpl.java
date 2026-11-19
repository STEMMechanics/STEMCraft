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
import dev.stemcraft.api.service.editor.EditorWebModule;
import dev.stemcraft.api.service.editor.EditorWebResponse;
import dev.stemcraft.api.service.editor.WebView;
import dev.stemcraft.api.service.editor.WebViewBadge;
import dev.stemcraft.api.service.editor.WebViewButton;
import dev.stemcraft.api.service.editor.WebViewCards;
import dev.stemcraft.api.service.editor.WebViewComponent;
import dev.stemcraft.api.service.editor.WebViewDialog;
import dev.stemcraft.api.service.editor.WebViewDataEditor;
import dev.stemcraft.api.service.editor.WebViewEmpty;
import dev.stemcraft.api.service.editor.WebViewField;
import dev.stemcraft.api.service.editor.WebViewForm;
import dev.stemcraft.api.service.editor.WebViewItem;
import dev.stemcraft.api.service.editor.WebViewList;
import dev.stemcraft.api.service.editor.WebViewMetadata;
import dev.stemcraft.api.service.editor.WebViewOption;
import dev.stemcraft.api.service.editor.WebViewPanel;
import dev.stemcraft.api.service.editor.WebViewNotice;
import dev.stemcraft.api.service.editor.WebViewSubview;
import dev.stemcraft.api.service.editor.WebViewValidation;
import dev.stemcraft.api.service.web.WebServiceRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.file.YamlConfiguration;
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
    private static final int MAX_EDITOR_DATA_DEPTH = 32;
    private static final int MAX_EDITOR_DATA_NODES = 20_000;
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

    /** Return identity and revision metadata for one pack configuration. */
    DocumentSnapshot editorDocument(@NotNull String packId, @NotNull String relativePath) {
        ContentPackDocument document = findDocument(packId, relativePath);
        if (document == null || !document.file().isFile()) return null;
        return new DocumentSnapshot(document.packId(), documentPath(document), document.config().revision());
    }

    /** Return typed YAML data for a component-based editor view. */
    EditorDataSnapshot editorData(@NotNull String packId, @NotNull String relativePath) {
        ContentPackDocument document = findDocument(packId, relativePath);
        if (document == null || !document.file().isFile()) return null;
        try {
            return new EditorDataSnapshot(document.packId(), documentPath(document),
                sectionData(document.config(), 0, new int[]{0}), document.config().revision());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[content-packs] Could not read editor data for '"
                + document.file() + "': " + exception.getMessage());
            return null;
        }
    }

    /** Save structured YAML values with revision checks and the normal pack validation lifecycle. */
    DocumentSaveResult saveEditorData(@NotNull String packId, @NotNull String relativePath,
                                      @NotNull Map<?, ?> data, String expectedRevision) {
        ContentPackDocument document = findDocument(packId, relativePath);
        if (document == null || !document.file().isFile())
            return new DocumentSaveResult(false, "Configuration file not found.", null);
        String currentRevision = document.config().revision();
        if (expectedRevision == null || expectedRevision.isBlank())
            return new DocumentSaveResult(false, "Reload the configuration before saving so its revision can be checked.", currentRevision);

        String previous;
        try {
            previous = Files.readString(document.file().toPath());
        } catch (IOException exception) {
            return new DocumentSaveResult(false, "Could not read the current configuration: " + exception.getMessage(), currentRevision);
        }

        try {
            Object normalized = copyEditorValue(data, 0, new int[]{0});
            if (!(normalized instanceof Map<?, ?> root))
                return new DocumentSaveResult(false, "A configuration file must contain named fields at the top level.", currentRevision);
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<?, ?> entry : root.entrySet()) {
                yaml.set(String.valueOf(entry.getKey()), entry.getValue());
            }
            ConfigSaveResult saveResult = document.config().saveYaml(yaml.saveToString(), expectedRevision);
            if (!saveResult.success())
                return new DocumentSaveResult(false, saveResult.message(), saveResult.currentDiskRevision());
        } catch (IllegalArgumentException exception) {
            return new DocumentSaveResult(false, exception.getMessage(), currentRevision);
        }

        if (!reload(packId)) {
            ConfigSaveResult restoreResult = document.config().saveYaml(previous, "");
            if (!restoreResult.success()) plugin.getLogger().warning("[content-packs] Could not restore '"
                + document.file() + "' after a rejected structured web save: " + restoreResult.message());
            reload(packId);
            return new DocumentSaveResult(false, "The change failed pack validation; the previous working configuration was restored.",
                document.config().revision());
        }
        EditorDataSnapshot saved = editorData(packId, relativePath);
        return new DocumentSaveResult(true, "Saved and validated " + documentPath(document) + ".",
            saved == null ? document.config().revision() : saved.revision());
    }

    private static Object encodeUnicodeEscapes(Object value) {
        if (value instanceof String text) return encodeUnicodeText(text);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> result.put(encodeUnicodeText(String.valueOf(key)), encodeUnicodeEscapes(child)));
            return result;
        }
        if (value instanceof Collection<?> collection)
            return collection.stream().map(ContentPackServiceImpl::encodeUnicodeEscapes).toList();
        return value;
    }

    private static Map<String, Object> stringKeyMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if (key != null) result.put(String.valueOf(key), child);
            });
        }
        return result;
    }

    private static Object decodeUnicodeEscapes(Object value) {
        if (value instanceof String text) return decodeUnicodeText(text);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> result.put(decodeUnicodeText(String.valueOf(key)), decodeUnicodeEscapes(child)));
            return result;
        }
        if (value instanceof Collection<?> collection)
            return collection.stream().map(ContentPackServiceImpl::decodeUnicodeEscapes).toList();
        return value;
    }

    private static String encodeUnicodeText(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\\') result.append("\\\\");
            else if (character > 0x7e) result.append(String.format(Locale.ROOT, "\\u%04X", (int) character));
            else result.append(character);
        }
        return result.toString();
    }

    private static String decodeUnicodeText(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length();) {
            if (text.charAt(index) != '\\' || index + 1 >= text.length()) {
                result.append(text.charAt(index++));
            } else if (text.charAt(index + 1) == '\\') {
                result.append('\\');
                index += 2;
            } else if (text.charAt(index + 1) == 'u' && index + 6 <= text.length()) {
                try {
                    result.append((char) Integer.parseInt(text.substring(index + 2, index + 6), 16));
                    index += 6;
                } catch (NumberFormatException exception) {
                    result.append(text.charAt(index++));
                }
            } else {
                result.append(text.charAt(index++));
            }
        }
        return result.toString();
    }

    private static Map<String, Object> sectionData(ConfigSection section, int depth, int[] nodes) {
        if (depth > MAX_EDITOR_DATA_DEPTH) throw new IllegalArgumentException("Configuration nesting is too deep to edit safely.");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (++nodes[0] > MAX_EDITOR_DATA_NODES) throw new IllegalArgumentException("Configuration has too many values to edit in one view.");
            if (section.isSection(key)) {
                ConfigSection child = section.getSection(key, false);
                result.put(key, child == null ? Map.of() : sectionData(child, depth + 1, nodes));
            } else {
                result.put(key, copyEditorValue(section.get(key), depth + 1, nodes));
            }
        }
        return result;
    }

    private static Map<String, Object> sectionData(ConfigurationSection section, int depth, int[] nodes) {
        if (depth > MAX_EDITOR_DATA_DEPTH) throw new IllegalArgumentException("Configuration nesting is too deep to edit safely.");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (++nodes[0] > MAX_EDITOR_DATA_NODES) throw new IllegalArgumentException("Configuration has too many values to edit in one view.");
            ConfigurationSection child = section.getConfigurationSection(key);
            result.put(key, child == null ? copyEditorValue(section.get(key), depth + 1, nodes)
                : sectionData(child, depth + 1, nodes));
        }
        return result;
    }

    private static Object copyEditorValue(Object value, int depth, int[] nodes) {
        if (depth > MAX_EDITOR_DATA_DEPTH) throw new IllegalArgumentException("Configuration nesting is too deep to edit safely.");
        if (++nodes[0] > MAX_EDITOR_DATA_NODES) throw new IllegalArgumentException("Configuration has too many values to edit in one view.");
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isFinite(decimal)) throw new IllegalArgumentException("Configuration values must use finite numbers.");
            if (decimal == Math.rint(decimal) && decimal >= Long.MIN_VALUE && decimal <= Long.MAX_VALUE) {
                long integral = (long) decimal;
                return integral >= Integer.MIN_VALUE && integral <= Integer.MAX_VALUE ? (int) integral : integral;
            }
            return decimal;
        }
        if (value instanceof Character character) return character.toString();
        if (value instanceof ConfigurationSection section) return sectionData(section, depth, nodes);
        if (value instanceof ConfigurationSerializable serializable) return copyEditorValue(serializable.serialize(), depth + 1, nodes);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Configuration map keys must be text.");
                result.put(key, copyEditorValue(entry.getValue(), depth + 1, nodes));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) result.add(copyEditorValue(item, depth + 1, nodes));
            return result;
        }
        throw new IllegalArgumentException("This configuration contains a value the structured editor cannot safely change: "
            + value.getClass().getSimpleName());
    }

    /** Return a hierarchical plain map; ConfigSection.getMap() uses Bukkit's flattened/shallow views. */
    static Map<String, Object> configurationMap(ConfigSection section, String path) {
        if (section == null) return Map.of();
        if (path == null || path.isBlank()) return configurationMap(section, 0);
        if (section.isSection(path)) {
            ConfigSection child = section.getSection(path, false);
            return child == null ? Map.of() : configurationMap(child, 0);
        }
        Object value = normalizeConfigValue(section.get(path), 0);
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> configurationMap(ConfigSection section, int depth) {
        if (depth > MAX_EDITOR_DATA_DEPTH) throw new IllegalArgumentException("Configuration nesting is too deep.");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (section.isSection(key)) {
                ConfigSection child = section.getSection(key, false);
                result.put(key, child == null ? Map.of() : configurationMap(child, depth + 1));
            } else {
                result.put(key, normalizeConfigValue(section.get(key), depth + 1));
            }
        }
        return result;
    }

    private static Map<String, Object> configurationMap(ConfigurationSection section, int depth) {
        if (depth > MAX_EDITOR_DATA_DEPTH) throw new IllegalArgumentException("Configuration nesting is too deep.");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            result.put(key, child == null ? normalizeConfigValue(section.get(key), depth + 1)
                : configurationMap(child, depth + 1));
        }
        return result;
    }

    private static Object normalizeConfigValue(Object value, int depth) {
        if (depth > MAX_EDITOR_DATA_DEPTH) throw new IllegalArgumentException("Configuration nesting is too deep.");
        if (value instanceof ConfigurationSection section) return configurationMap(section, depth);
        if (value instanceof ConfigurationSerializable serializable) return normalizeConfigValue(serializable.serialize(), depth + 1);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) result.put(key, normalizeConfigValue(entry.getValue(), depth + 1));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) result.add(normalizeConfigValue(item, depth + 1));
            return result;
        }
        return value;
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
            if ("/data".equals(path) && "PUT".equalsIgnoreCase(request.method())) {
                if (!context.writable()) return text(403, "This link is read-only.");
                Map<?, ?> body = EDITOR_GSON.fromJson(request.bodyAsString(), Map.class);
                if (body == null || !(body.get("data") instanceof Map<?, ?> data))
                    return text(400, "The editor did not submit configuration fields.");
                String pack = body.get("pack") == null ? "" : String.valueOf(body.get("pack"));
                String file = body.get("file") == null ? "" : String.valueOf(body.get("file"));
                String revision = body.get("revision") == null ? "" : String.valueOf(body.get("revision"));
                Map<?, ?> submittedData = Boolean.TRUE.equals(body.get("unicodeEscapes"))
                    ? (Map<?, ?>) decodeUnicodeEscapes(data) : data;
                DocumentSaveResult result = saveEditorData(pack, file, submittedData, revision);
                return json(result.success() ? 200 : 409, result);
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
        @Override public @NotNull String icon() { return "package"; }
        @Override public @NotNull String description() { return "Manage content packs and their files."; }
        @Override public @NotNull String readPermission() { return "stemcraft.command.stemcraft.pack"; }
        @Override public @NotNull String writePermission() { return "stemcraft.command.stemcraft.pack"; }

        @Override public @NotNull WebView view(@NotNull EditorWebContext context,
                                                      @NotNull WebServiceRequest request) {
            return contentPackEditorView(context, request);
        }

        @Override public @NotNull EditorWebResponse handle(@NotNull EditorWebContext context, @NotNull String path,
                                                            @NotNull WebServiceRequest request) {
            return handleEditorRequest(context, path, request);
        }
    }

    private @NotNull WebView contentPackEditorView(@NotNull EditorWebContext context,
                                                          @NotNull WebServiceRequest request) {
        String selectedId = request.queryParam("pack");
        if (selectedId == null || selectedId.isBlank()) {
            List<WebViewItem> items = packs().stream()
                .map(info -> new WebViewItem(info.id(), info.id(),
                    info.description().isBlank() ? "Content pack" : info.description(),
                    packBadges(info),
                    WebViewButton.navigate("open-" + info.id(), "Open", Map.of("pack", info.id()))))
                .toList();
            List<WebViewButton> actions = context.writable()
                ? List.of(createPackAction()) : List.of();
            return new WebView("packs", "Content packs", "", context.writable(), List.of(
                new WebViewPanel("Content packs", 1, List.of(new WebViewCards(items, 3)), List.of(),
                    packs().size() + " pack" + (packs().size() == 1 ? "" : "s"))
            )).withTopbarActions(actions);
        }

        ContentPackInfo info = find(selectedId);
        if (info == null) {
            return new WebView("packs", "Content packs", "", context.writable(), List.of(
                new WebViewSubview(selectedId, WebViewButton.navigate("back", "Back", Map.of())),
                new WebViewEmpty("Content pack not found.")
            ));
        }

        String selectedFile = request.queryParam("file");
        if (selectedFile != null && !selectedFile.isBlank()) {
            return contentPackDataView(context, info, selectedFile, Map.of("pack", info.id()));
        }

        List<WebViewButton> actions = context.writable() ? packActions(info) : List.of();
        List<WebViewItem> documents = editorDocuments().stream()
            .filter(document -> info.id().equals(document.get("pack")))
            .map(document -> {
                String file = String.valueOf(document.get("file"));
                WebViewButton open = Boolean.TRUE.equals(document.get("hasItems"))
                    ? WebViewButton.moduleNavigate("edit-items-" + file.hashCode(), "Edit items", "items", Map.of("pack", info.id()))
                    : Boolean.TRUE.equals(document.get("hasLoot"))
                        ? WebViewButton.moduleNavigate("edit-drops-" + file.hashCode(), "Edit drops", "loot", Map.of("pack", info.id()))
                        : file.endsWith("agriculture.yml")
                            ? WebViewButton.moduleNavigate("edit-agriculture-" + file.hashCode(), "Edit farming and gathering",
                                "agriculture", Map.of("pack", info.id(), "file", file))
                            : WebViewButton.navigate("open-file-" + file.hashCode(), "Open", Map.of("pack", info.id(), "file", file));
                return new WebViewItem(file, documentTitle(file), documentDescription(file), List.of(), open);
            })
            .toList();
        List<WebViewComponent> components = List.of(
            new WebViewSubview(info.id(), WebViewButton.navigate("back", "Back", Map.of())),
            new WebViewPanel(info.id(), info.description().isBlank() ? "Content pack" : info.description(), 1,
                List.of(new WebViewValidation(info.validationErrors(), "No validation issues found."),
                    new WebViewMetadata(info.dependencies(), info.resourcePackRebuildRequired())), actions, ""),
            new WebViewPanel("Configuration files", 1,
                List.of(new WebViewList("", documents, true, "Search files", "This pack has no configuration files.")))
        );
        return new WebView("packs", info.id(), "", context.writable(), components);
    }

    private WebView contentPackDataView(EditorWebContext context, ContentPackInfo pack, String file,
                                        Map<String, String> backQuery) {
        EditorDataSnapshot snapshot = editorData(pack.id(), file);
        if (snapshot == null) return new WebView("packs", pack.id(), "", context.writable(), List.of(
            new WebViewSubview(file, WebViewButton.navigate("back-to-pack", "Back", backQuery)),
            new WebViewEmpty("That configuration file could not be opened.")));

        WebViewButton save = new WebViewButton("request", "save-config", "Save changes", "primary",
            "data", "PUT", Map.of(), Map.of(), null, Map.of());
        String title = documentTitle(file);
        String description = documentDescription(file);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("pack", snapshot.pack());
        state.put("file", snapshot.file());
        state.put("revision", snapshot.revision());

        if (file.substring(file.lastIndexOf('/') + 1).equalsIgnoreCase("config.yml")) {
            Map<String, Object> settings = new LinkedHashMap<>(snapshot.data());
            Map<String, Object> packSettings = stringKeyMap(settings.get("pack"));
            if (!(packSettings.get("namespace") instanceof String namespace) || namespace.isBlank())
                packSettings.put("namespace", pack.namespace());
            if (!(packSettings.get("description") instanceof String))
                packSettings.put("description", pack.description());
            if (!(packSettings.get("dependencies") instanceof Collection<?>))
                packSettings.put("dependencies", pack.dependencies());
            settings.put("pack", packSettings);
            state.put("data", settings);

            Map<String, String> dependencies = new LinkedHashMap<>();
            packs().stream().filter(info -> !info.id().equalsIgnoreCase(pack.id()))
                .forEach(info -> dependencies.put(info.id(), info.id()));
            pack.dependencies().stream().filter(id -> !dependencies.containsKey(id))
                .forEach(id -> dependencies.put(id, id + " (missing pack)"));
            List<WebViewOption> dependencyOptions = dependencies.entrySet().stream()
                .map(entry -> new WebViewOption(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(WebViewOption::label, String.CASE_INSENSITIVE_ORDER)).toList();
            List<WebViewField> fields = List.of(
                new WebViewField("text", "data.pack.namespace", "Item namespace", String.valueOf(packSettings.get("namespace")),
                    "For example: stemcraft_farming", true, "[a-z0-9._-]+", List.of(), Map.of(), false)
                    .withHelp("This lowercase prefix is used for custom item and resource IDs in this pack."),
                WebViewField.textarea("data.pack.description", "Pack description",
                    String.valueOf(packSettings.get("description")), true)
                    .withHelp("A short explanation shown to people choosing a pack."),
                new WebViewField("multiselect", "data.pack.dependencies", "Packs this pack needs", "", "", false,
                    null, dependencyOptions, Map.of(), true)
                    .withHelp("Choose other packs that must be enabled for this pack to work. Leave empty when it stands alone.")
            );
            WebViewForm form = new WebViewForm("content-pack-settings-editor", fields, List.of(),
                context.writable() ? List.of(save) : List.of(), 2, "", true, "");
            return new WebView("packs", "Pack details", description, context.writable(), List.of(
                new WebViewSubview(title, WebViewButton.navigate("back-to-pack", "Back to pack", backQuery)),
                new WebViewPanel("Pack details", "These settings tell STEMCraft how to identify and describe this pack.", 1,
                    List.of(form)),
                new WebViewNotice("Use the Enable or Disable button on the pack page to control whether players can use this pack.")),
                state);
        }

        state.put("data", encodeUnicodeEscapes(snapshot.data()));
        WebViewButton unicodeSave = new WebViewButton("request", "save-config", "Save changes", "primary",
            "data", "PUT", Map.of("unicodeEscapes", true), Map.of(), null, Map.of());
        WebViewForm form = new WebViewForm("content-pack-data-editor", List.of(),
            List.of(new WebViewDataEditor("data", "Pack values", 2)),
            context.writable() ? List.of(unicodeSave) : List.of(), 1, "", true, "");
        return new WebView("packs", title, description, context.writable(), List.of(
            new WebViewSubview(title, WebViewButton.navigate("back-to-pack", "Back to pack", backQuery)),
            new WebViewPanel(title, snapshot.pack() + " / " + snapshot.file(), 1, List.of(
                new WebViewNotice(documentEditHelp(file)),
                new WebViewNotice("Minecraft text symbols are shown as \\u codes here. STEMCraft turns them back into the correct text when you save."),
                form))), state);
    }

    private static String documentEditHelp(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.equals("recipes.yml"))
            return "Each recipe name is an entry players can make. Result is the item it creates; ingredients are the items needed. Shaped recipes use a crafting-table pattern, while furnace recipes use an input item and cooking time.";
        if (name.equals("dimension-portals.yml"))
            return "These settings describe portal blocks and the Minecraft dimensions players can travel between. Check the destination and frame blocks before saving.";
        if (name.equals("sleeping-bag.yml"))
            return "These settings control the sleeping bag item and its crafting recipe. The item name and recipe result should refer to the same custom item.";
        return "The groups below match settings saved in this pack file. Field names are shown in readable words; their original names are kept in the editable name boxes.";
    }

    private static String documentTitle(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.equals("agriculture.yml")) return "Farming and gathering";
        if (name.equals("custom-items.yml") || name.equals("items.yml")) return "Custom items";
        if (name.equals("loot.yml")) return "Mob and block drops";
        if (name.equals("recipes.yml")) return "Crafting recipes";
        if (name.equals("config.yml")) return "Pack settings";
        return name.replaceFirst("(?i)\\.ya?ml$", "").replace('-', ' ').replace('_', ' ');
    }

    private static String documentDescription(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.equals("agriculture.yml")) return "Choose crops players can grow and items they can find by breaking blocks.";
        if (name.equals("custom-items.yml") || name.equals("items.yml")) return "Add and edit items players can hold, use, craft or receive as rewards.";
        if (name.equals("loot.yml")) return "Choose what blocks and mobs can drop when players explore.";
        if (name.equals("recipes.yml")) return "Set the ingredients and result for crafting, furnace, smoker and brewing recipes.";
        if (name.equals("config.yml")) return "Choose the pack name, namespace and dependencies.";
        return "Edit the settings used by this content pack.";
    }

    private @NotNull WebViewButton createPackAction() {
        WebViewDialog dialog = new WebViewDialog("New content pack", "Create pack", List.of(
            WebViewField.text("id", "Pack ID", "my-pack", true, "[a-z0-9][a-z0-9_-]*"),
            WebViewField.select("template", "Template", List.of(
                new WebViewOption("", "Blank pack"),
                new WebViewOption("workshop", "Workshop example")))
        ));
        return WebViewButton.dialog("create", "New pack", "primary", "action",
            Map.of("action", "create"), dialog);
    }

    private @NotNull List<WebViewButton> packActions(@NotNull ContentPackInfo info) {
        List<WebViewButton> actions = new ArrayList<>();
        actions.add(WebViewButton.request("validate", "Validate", "default", "action",
            Map.of("action", "validate", "id", info.id())));
        actions.add(WebViewButton.request("reload", "Reload pack", "default", "action",
            Map.of("action", "reload", "id", info.id())));
        actions.add(WebViewButton.request(info.enabled() ? "disable" : "enable",
            info.enabled() ? "Disable" : "Enable", info.enabled() ? "warn" : "good", "action",
            Map.of("action", info.enabled() ? "disable" : "enable", "id", info.id())));
        actions.add(WebViewButton.request("delete", "Delete", "danger", "action",
            Map.of("action", "delete", "id", info.id())).confirmation("Delete content pack '" + info.id() + "'?"));
        return List.copyOf(actions);
    }

    private @NotNull List<WebViewBadge> packBadges(@NotNull ContentPackInfo info) {
        List<WebViewBadge> badges = new ArrayList<>();
        if (!info.valid()) badges.add(new WebViewBadge("Needs attention", "bad"));
        else if (info.enabled()) badges.add(new WebViewBadge("Enabled", "good"));
        else badges.add(new WebViewBadge("Disabled", "default"));
        if (!info.validationErrors().isEmpty()) {
            int count = info.validationErrors().size();
            badges.add(new WebViewBadge(count + " validation issue" + (count == 1 ? "" : "s"), "default"));
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
                value.put("hasItems", !configurationMap(document.config(), "custom-items").isEmpty());
                value.put("hasLoot", !configurationMap(document.config(), "loot.tables").isEmpty());
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
            errors.addAll(validateItems(configurationMap(document.config(), "custom-items"),
                source + ".custom-items", document, knownPotions));
            errors.addAll(validatePotionDefinitions(configurationMap(document.config(), "potions"),
                source + ".potions"));
            if (document.config().isSection("loot")) {
                errors.addAll(validateLootConditions(configurationMap(document.config(), "loot.tables"),
                    source + ".loot.tables"));
            }
            errors.addAll(validateDifficultyProfiles(configurationMap(document.config(), "difficulty-profiles"),
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
            Map<String, Object> recipes = configurationMap(document.config(), "recipes");
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

            Map<String, Object> tables = configurationMap(document.config(), "loot.tables");
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
        if (root != null) known.addAll(configurationMap(root, "custom-items").keySet().stream()
            .map(ContentPackServiceImpl::normalizeReference).filter(value -> !value.isBlank()).toList());
        for (Pack pack : packs.values()) {
            if (pack != target && !pack.enabled) continue;
            for (ContentPackDocument document : pack.documents) {
                known.addAll(configurationMap(document.config(), "custom-items").keySet().stream()
                    .map(ContentPackServiceImpl::normalizeReference).filter(value -> !value.isBlank()).toList());
            }
        }
        return known;
    }

    private Set<String> knownPotionDefinitions(Pack target) {
        Set<String> known = new HashSet<>();
        ConfigFile root = api.config().load("config.yml");
        if (root != null) known.addAll(configurationMap(root, "potions").keySet().stream()
            .map(ContentPackServiceImpl::normalizeReference).filter(value -> !value.isBlank()).toList());
        for (Pack pack : packs.values()) {
            if (pack != target && !pack.enabled) continue;
            for (ContentPackDocument document : pack.documents) {
                known.addAll(configurationMap(document.config(), "potions").keySet().stream()
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
                for (String id : configurationMap(document.config(), "custom-items").keySet()) {
                    duplicate(itemOwners, normalizeReference(id), pack.id + "/" + document.file().getName(), target,
                        "custom item").ifPresent(errors::add);
                }
                for (String id : configurationMap(document.config(), "potions").keySet()) {
                    duplicate(potionOwners, normalizeReference(id), pack.id + "/" + document.file().getName(), target,
                        "potion definition").ifPresent(errors::add);
                }
                for (String id : configurationMap(document.config(), "loot.tables").keySet()) {
                    duplicate(tableOwners, normalizeReference(id), pack.id + "/" + document.file().getName(), target,
                        "loot table").ifPresent(errors::add);
                }
                for (String section : List.of("shaped", "shapeless", "furnace", "smoker", "blast_furnace",
                    "campfire", "brewing", "smithing_transform", "smithing_trim")) {
                    for (String id : configurationMap(document.config(), "recipes." + section).keySet()) {
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

    record DocumentSnapshot(String pack, String file, String revision) { }

    record EditorDataSnapshot(String pack, String file, Map<String, Object> data, String revision) { }

    record DocumentSaveResult(boolean success, String message, String revision) { }
}
