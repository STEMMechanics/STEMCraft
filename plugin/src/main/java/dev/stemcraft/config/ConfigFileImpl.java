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

package dev.stemcraft.config;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.service.config.ConfigChangeEvent;
import dev.stemcraft.api.service.config.ConfigChangeOrigin;
import dev.stemcraft.api.service.config.ConfigSaveException;
import dev.stemcraft.api.service.config.ConfigSaveResult;
import dev.stemcraft.api.service.config.ConfigSubscription;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Conflict-aware YAML-backed ConfigFile implementation.
 *
 * <p>The in-memory configuration, the last accepted disk snapshot and the
 * locally dirty paths are deliberately kept separate. Every save reads disk
 * again and performs a three-way merge before an atomic replacement.</p>
 */
public class ConfigFileImpl extends ConfigSectionImpl implements ConfigFile {
    /** Serialises saves from multiple ConfigFileImpl instances in this JVM. */
    private static final Object SAVE_LOCK = new Object();

    @Getter
    private String name;
    @Getter
    private boolean dirty;
    private File file;
    private YamlConfiguration config;
    @Getter
    private boolean autoSave = false;
    private boolean saveDefaults = true;

    private final Object stateLock = new Object();
    private final Set<String> dirtyPaths = new LinkedHashSet<>();
    private boolean fullSaveRequired = false;

    /** Last accepted or successfully written tree. */
    private Map<String, Object> baseline = new LinkedHashMap<>();
    /** Revision of the actual disk bytes most recently observed. */
    private String diskRevision = "";
    private String observedRevision = "";
    private FileStamp observedStamp = FileStamp.missing();
    private final Map<String, Map<String, Object>> revisionSnapshots = new LinkedHashMap<>();
    private Consumer<Runnable> changeDispatcher = Runnable::run;
    private final CopyOnWriteArrayList<ListenerRegistration> listeners = new CopyOnWriteArrayList<>();

    /** Loads the configuration file with the given name. */
    public boolean load(String name, boolean createIfNotExist) {
        return load(STEMCraftAPI.api().getDataFolder(), name, createIfNotExist);
    }

    public boolean load(File file, boolean createIfNotExist) {
        File parent = file == null ? null : file.getAbsoluteFile().getParentFile();
        return load(parent, file == null ? "" : file.getName(), createIfNotExist);
    }

    public boolean load(File parent, String name, boolean createIfNotExist) {
        String resolvedName = name.contains(".") ? name : name + ".yml";
        File resolvedParent = parent == null ? new File(".") : parent;
        if (!resolvedParent.exists() && !resolvedParent.mkdirs()) return false;

        File targetFile = new File(resolvedParent, resolvedName);
        if (!targetFile.exists()) {
            if (!createIfNotExist) return false;
            try {
                if (!targetFile.createNewFile()) return false;
            } catch (IOException exception) {
                return false;
            }
        }

        DiskSnapshot loaded;
        try {
            loaded = readDisk(targetFile);
        } catch (IOException | InvalidConfigurationException exception) {
            return false;
        }

        synchronized (stateLock) {
            this.name = resolvedName;
            this.file = targetFile;
            this.config = loaded.configuration();
            this.baseline = loaded.tree();
            this.diskRevision = loaded.revision();
            this.observedRevision = loaded.revision();
            this.observedStamp = loaded.stamp();
            rememberRevision(loaded.revision(), loaded.tree());
            clearDirtyState();
            setConfigFile(this);
            setSection(this.config);
        }
        return true;
    }

    @Override
    public boolean exists() {
        synchronized (stateLock) {
            return file != null && file.exists();
        }
    }

    /** Returns the backing file for the shared ConfigService watcher. */
    public File getFile() {
        synchronized (stateLock) {
            return file;
        }
    }

    @Override
    public boolean reload() {
        synchronized (stateLock) {
            if (file == null) return false;

            DiskSnapshot loaded;
            try {
                loaded = readDisk(file);
            } catch (IOException | InvalidConfigurationException exception) {
                logError("Could not reload config file: " + name, exception);
                return false;
            }

            Map<String, Object> before = ConfigTree.snapshot(config);
            replaceActiveConfiguration(loaded.tree());
            baseline = loaded.tree();
            diskRevision = loaded.revision();
            observedRevision = loaded.revision();
            observedStamp = loaded.stamp();
            rememberRevision(loaded.revision(), loaded.tree());
            clearDirtyState();

            ConfigChangeEvent event = createEvent(before, loaded.tree(), ConfigChangeOrigin.RELOAD, loaded.revision());
            if (event != null) publish(event);
            return true;
        }
    }

    @Override
    public String revision() {
        synchronized (stateLock) {
            return diskRevision;
        }
    }

    /** Marks the entire active tree as locally managed and dirty. */
    @Override
    public void setDirty() {
        synchronized (stateLock) {
            dirty = true;
            fullSaveRequired = true;
        }
    }

    /** Marks a path dirty without emitting an event. Used by legacy callers. */
    void setDirty(String path) {
        synchronized (stateLock) {
            String normalized = path == null ? "" : ConfigTree.normalize(path);
            if (normalized.isEmpty()) fullSaveRequired = true;
            else dirtyPaths.add(normalized);
            dirty = true;
        }
    }

    /** Captures a stable value for ConfigSectionImpl before it mutates Bukkit. */
    Object snapshotValue(String path) {
        synchronized (stateLock) {
            return ConfigTree.cloneValue(ConfigTree.valueAt(ConfigTree.snapshot(config), path));
        }
    }

    /** Records a mutation made through ConfigSectionImpl and emits INTERNAL events. */
    void recordLocalMutation(String path, Object beforeValue) {
        synchronized (stateLock) {
            if (config == null) return;
            String normalized = ConfigTree.normalize(path);
            Map<String, Object> current = ConfigTree.snapshot(config);
            Object afterValue = ConfigTree.valueAt(current, normalized);

            if (ConfigTree.equal(beforeValue, afterValue)) {
                recalculateDirtyState(current);
                return;
            }

            Object baselineValue = ConfigTree.valueAt(baseline, normalized);
            if (ConfigTree.equal(afterValue, baselineValue)) dirtyPaths.remove(normalized);
            else dirtyPaths.add(normalized);
            dirty = fullSaveRequired || !dirtyPaths.isEmpty();

            Map<String, Object> before = new LinkedHashMap<>(current);
            ConfigTree.applyPath(before, normalized, beforeValue);
            ConfigChangeEvent event = createEvent(before, current, ConfigChangeOrigin.INTERNAL, diskRevision);
            if (event != null) publish(event);
        }
    }

    /** Sets the listener dispatcher used by ConfigService to marshal callbacks. */
    public void setChangeDispatcher(Consumer<Runnable> dispatcher) {
        synchronized (stateLock) {
            changeDispatcher = dispatcher == null ? Runnable::run : dispatcher;
        }
    }

    @Override
    public ConfigSubscription onChange(@NotNull Consumer<ConfigChangeEvent> listener) {
        return onChange(null, listener);
    }

    /** Registers a listener filtered by a path. */
    public ConfigSubscription onChange(String path, @NotNull Consumer<ConfigChangeEvent> listener) {
        ListenerRegistration registration = new ListenerRegistration(
            path == null ? null : ConfigTree.normalize(path), listener);
        listeners.add(registration);
        return registration;
    }

    /** Removes all listeners during ConfigService shutdown. */
    public void clearSubscriptions() {
        for (ListenerRegistration listener : listeners) listener.close();
        listeners.clear();
    }

    /** Reconciles an external edit. */
    public boolean reconcileExternal() {
        return reconcileExternal(false);
    }

    /**
     * Reconciles an external edit. The shared ConfigService calls this after a
     * WatchService event and during periodic reconciliation.
     */
    public boolean reconcileExternal(boolean forceRead) {
        synchronized (stateLock) {
            if (file == null || config == null) return false;

            FileStamp stamp = FileStamp.from(file);
            if (!forceRead && stamp.equals(observedStamp)) return false;

            DiskSnapshot disk;
            try {
                disk = readDisk(file);
            } catch (IOException | InvalidConfigurationException exception) {
                observedStamp = stamp;
                observedRevision = revisionOf(file);
                logError("Ignoring invalid external config change: " + name, exception);
                return false;
            }

            observedStamp = disk.stamp();
            observedRevision = disk.revision();
            if (Objects.equals(disk.revision(), diskRevision)
                && ConfigTree.equal(disk.tree(), baseline)) return false;

            Map<String, Object> before = ConfigTree.snapshot(config);
            Set<String> localPaths = localChangePaths(before);
            if (localPaths.isEmpty()) {
                replaceActiveConfiguration(disk.tree());
                baseline = disk.tree();
                diskRevision = disk.revision();
                clearDirtyState();
                rememberRevision(disk.revision(), disk.tree());
                ConfigChangeEvent event = createEvent(before, disk.tree(), ConfigChangeOrigin.EXTERNAL, disk.revision());
                if (event != null) publish(event);
                return event != null;
            }

            Set<String> externalPaths = ConfigTree.diffPaths(baseline, disk.tree());
            if (externalPaths.isEmpty()) {
                diskRevision = disk.revision();
                return false;
            }

            Map<String, Object> active = castMap(ConfigTree.cloneValue(before));
            Map<String, Object> acceptedBaseline = castMap(ConfigTree.cloneValue(baseline));
            for (String externalPath : externalPaths) {
                if (externalPathConflicts(externalPath, localPaths, before, disk.tree())) continue;

                Object diskValue = ConfigTree.valueAt(disk.tree(), externalPath);
                ConfigTree.applyPath(active, externalPath, diskValue);
                ConfigTree.applyPath(acceptedBaseline, externalPath, diskValue);
            }

            replaceActiveConfiguration(active);
            baseline = acceptedBaseline;
            diskRevision = disk.revision();
            rememberRevision(disk.revision(), disk.tree());
            recalculateDirtyState(active);

            ConfigChangeEvent event = createEvent(before, active, ConfigChangeOrigin.EXTERNAL, disk.revision());
            if (event != null) publish(event);
            return event != null;
        }
    }

    /**
     * Performs the conflict-aware save. Legacy void save methods delegate here
     * and throw ConfigSaveException on failure.
     */
    @Override
    public ConfigSaveResult saveChecked(boolean pruneEmptySections) {
        synchronized (stateLock) {
            synchronized (SAVE_LOCK) {
            if (file == null) {
                return ConfigSaveResult.failure(diskRevision, "Configuration file is not loaded: " + name);
            }
            if (!dirty) return ConfigSaveResult.success(false, diskRevision, "Configuration is already saved.");

            DiskSnapshot disk;
            try {
                disk = readDisk(file);
            } catch (IOException | InvalidConfigurationException exception) {
                String revision = revisionOf(file);
                return ConfigSaveResult.failure(revision,
                    "Could not read the latest configuration before saving " + name + ": " + exception.getMessage());
            }

            observedStamp = disk.stamp();
            observedRevision = disk.revision();
            Map<String, Object> active = ConfigTree.snapshot(config);
            Set<String> localPaths = localChangePaths(active);

            // A caller can mark a file dirty without changing a value. Accept
            // external changes rather than rewriting them from stale memory.
            if (localPaths.isEmpty()) {
                ConfigChangeEvent event = null;
                if (!ConfigTree.equal(active, disk.tree())) {
                    replaceActiveConfiguration(disk.tree());
                    event = createEvent(active, disk.tree(), ConfigChangeOrigin.EXTERNAL, disk.revision());
                }
                baseline = disk.tree();
                diskRevision = disk.revision();
                clearDirtyState();
                rememberRevision(disk.revision(), disk.tree());
                if (event != null) publish(event);
                return ConfigSaveResult.success(false, disk.revision(), "No local configuration changes required writing.");
            }

            Set<String> externalPaths = ConfigTree.diffPaths(baseline, disk.tree());
            Set<String> conflicts = new LinkedHashSet<>();
            for (String localPath : localPaths) {
                Object localValue = ConfigTree.valueAt(active, localPath);
                Object diskValue = ConfigTree.valueAt(disk.tree(), localPath);
                if (ConfigTree.equal(localValue, diskValue)) continue;

                boolean overlapsExternal = externalPaths.stream()
                    .anyMatch(externalPath -> ConfigTree.overlaps(localPath, externalPath));
                if (overlapsExternal) conflicts.add(localPath.isEmpty() ? "<root>" : localPath);
            }

            if (!conflicts.isEmpty()) {
                return ConfigSaveResult.conflict(disk.revision(), conflicts,
                    "Configuration conflicts detected in " + name + ": " + String.join(", ", conflicts));
            }

            Map<String, Object> merged = castMap(ConfigTree.cloneValue(disk.tree()));
            Map<String, Object> externalOnly = castMap(ConfigTree.cloneValue(active));
            for (String localPath : localPaths) {
                Object localValue = ConfigTree.valueAt(active, localPath);
                ConfigTree.applyPath(merged, localPath, localValue);
            }
            for (String externalPath : externalPaths) {
                if (!localPaths.stream().anyMatch(localPath -> ConfigTree.overlaps(localPath, externalPath))) {
                    ConfigTree.applyPath(externalOnly, externalPath, ConfigTree.valueAt(disk.tree(), externalPath));
                }
            }

            YamlConfiguration saveTarget = toConfiguration(merged);
            if (pruneEmptySections) pruneEmptySections(saveTarget);

            String expectedRevision = disk.revision();
            String beforeMoveRevision = revisionOf(file);
            if (!Objects.equals(expectedRevision, beforeMoveRevision)) {
                return ConfigSaveResult.conflict(beforeMoveRevision, localPaths,
                    "Configuration changed on disk while preparing " + name + "; no changes were written.");
            }

            File temporary = null;
            try {
                File parent = file.getAbsoluteFile().getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return ConfigSaveResult.failure(expectedRevision,
                        "Could not create the configuration directory for " + name);
                }

                temporary = File.createTempFile(".stemcraft-" + file.getName() + "-", ".tmp", parent);
                saveTarget.save(temporary);
                String expectedWrittenRevision = revisionOf(temporary);

                String finalCheckRevision = revisionOf(file);
                if (!Objects.equals(expectedRevision, finalCheckRevision)) {
                    return ConfigSaveResult.conflict(finalCheckRevision, localPaths,
                        "Configuration changed on disk during save preparation; no changes were written.");
                }

                atomicReplace(temporary.toPath(), file.toPath());
                temporary = null;

                String writtenRevision = revisionOf(file);
                if (!Objects.equals(writtenRevision, expectedWrittenRevision)) {
                    return ConfigSaveResult.conflict(writtenRevision, localPaths,
                        "Configuration changed while the atomic save was being finalized; local changes remain dirty.");
                }

                Map<String, Object> beforeActive = ConfigTree.snapshot(config);
                replaceActiveConfiguration(saveTarget);
                Map<String, Object> savedTree = ConfigTree.snapshot(saveTarget);
                baseline = savedTree;
                diskRevision = writtenRevision;
                observedRevision = writtenRevision;
                observedStamp = FileStamp.from(file);
                rememberRevision(writtenRevision, savedTree);
                clearDirtyState();

                ConfigChangeEvent externalEvent = createEvent(beforeActive, externalOnly,
                    ConfigChangeOrigin.EXTERNAL, writtenRevision);
                if (externalEvent != null) publish(externalEvent);
                return ConfigSaveResult.success(true, writtenRevision, "Configuration saved.");
            } catch (IOException exception) {
                return ConfigSaveResult.failure(expectedRevision,
                    "Could not save config file: " + name + ": " + exception.getMessage());
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary.toPath());
                    } catch (IOException ignored) {
                        // Failed temporary cleanup cannot make the original unsafe.
                    }
                }
            }
        }
        }
    }

    /** Applies and saves a complete YAML document through the same merge engine. */
    @Override
    public ConfigSaveResult saveYaml(String yaml, String expectedRevision) {
        synchronized (stateLock) {
            synchronized (SAVE_LOCK) {
            if (file == null || config == null) {
                return ConfigSaveResult.failure(diskRevision, "Configuration file is not loaded: " + name);
            }

            DiskSnapshot disk;
            try {
                disk = readDisk(file);
            } catch (IOException | InvalidConfigurationException exception) {
                return ConfigSaveResult.failure(revisionOf(file),
                    "Could not read the latest configuration before the editor save: " + exception.getMessage());
            }
            observedStamp = disk.stamp();
            observedRevision = disk.revision();

            Map<String, Object> browserBaseline = expectedRevision == null || expectedRevision.isBlank()
                ? baseline
                : revisionSnapshots.get(expectedRevision);
            if (browserBaseline == null && Objects.equals(expectedRevision, diskRevision)) browserBaseline = baseline;
            if (browserBaseline == null) {
                return ConfigSaveResult.conflict(disk.revision(), Set.of("<root>"),
                    "The editor revision is no longer available; reload the document.");
            }
            browserBaseline = castMap(ConfigTree.cloneValue(browserBaseline));

            YamlConfiguration candidate = new YamlConfiguration();
            try {
                candidate.loadFromString(yaml == null ? "" : yaml);
            } catch (InvalidConfigurationException exception) {
                return ConfigSaveResult.failure(disk.revision(),
                    "The editor document contains invalid YAML: " + exception.getMessage());
            }

            Map<String, Object> before = ConfigTree.snapshot(config);
            Map<String, Object> after = ConfigTree.snapshot(candidate);
            Set<String> localPaths = ConfigTree.reducePaths(ConfigTree.diffPaths(browserBaseline, after));
            if (localPaths.isEmpty()) {
                if (!ConfigTree.equal(before, disk.tree())) {
                    replaceActiveConfiguration(disk.tree());
                    baseline = disk.tree();
                    diskRevision = disk.revision();
                    clearDirtyState();
                    rememberRevision(disk.revision(), disk.tree());
                    ConfigChangeEvent externalEvent = createEvent(before, disk.tree(),
                        ConfigChangeOrigin.EXTERNAL, disk.revision());
                    if (externalEvent != null) publish(externalEvent);
                }
                return ConfigSaveResult.success(false, disk.revision(), "The editor document was already current.");
            }

            Set<String> externalPaths = ConfigTree.diffPaths(browserBaseline, disk.tree());
            Set<String> conflicts = new LinkedHashSet<>();
            for (String localPath : localPaths) {
                Object localValue = ConfigTree.valueAt(after, localPath);
                Object diskValue = ConfigTree.valueAt(disk.tree(), localPath);
                if (ConfigTree.equal(localValue, diskValue)) continue;
                if (externalPaths.stream().anyMatch(externalPath -> ConfigTree.overlaps(localPath, externalPath))) {
                    conflicts.add(localPath.isEmpty() ? "<root>" : localPath);
                }
            }
            if (!conflicts.isEmpty()) {
                return ConfigSaveResult.conflict(disk.revision(), conflicts,
                    "The editor document conflicts with newer configuration values: "
                        + String.join(", ", conflicts));
            }

            Map<String, Object> merged = castMap(ConfigTree.cloneValue(disk.tree()));
            Map<String, Object> internalAfter = castMap(ConfigTree.cloneValue(before));
            Map<String, Object> externalAfter = castMap(ConfigTree.cloneValue(before));
            for (String localPath : localPaths) {
                Object localValue = ConfigTree.valueAt(after, localPath);
                ConfigTree.applyPath(merged, localPath, localValue);
                ConfigTree.applyPath(internalAfter, localPath, localValue);
            }
            for (String externalPath : externalPaths) {
                if (!localPaths.stream().anyMatch(localPath -> ConfigTree.overlaps(localPath, externalPath))) {
                    ConfigTree.applyPath(externalAfter, externalPath, ConfigTree.valueAt(disk.tree(), externalPath));
                }
            }

            YamlConfiguration saveTarget = toConfiguration(merged);
            File temporary = null;
            try {
                File parent = file.getAbsoluteFile().getParentFile();
                temporary = File.createTempFile(".stemcraft-" + file.getName() + "-", ".tmp", parent);
                saveTarget.save(temporary);
                String expectedWrittenRevision = revisionOf(temporary);
                String finalCheckRevision = revisionOf(file);
                if (!Objects.equals(disk.revision(), finalCheckRevision)) {
                    return ConfigSaveResult.conflict(finalCheckRevision, localPaths,
                        "Configuration changed while the editor save was being prepared.");
                }
                atomicReplace(temporary.toPath(), file.toPath());
                temporary = null;
                String writtenRevision = revisionOf(file);
                if (!Objects.equals(writtenRevision, expectedWrittenRevision)) {
                    return ConfigSaveResult.conflict(writtenRevision, localPaths,
                        "Configuration changed while the editor save was being finalized.");
                }

                replaceActiveConfiguration(saveTarget);
                Map<String, Object> savedTree = ConfigTree.snapshot(saveTarget);
                baseline = savedTree;
                diskRevision = writtenRevision;
                observedRevision = writtenRevision;
                observedStamp = FileStamp.from(file);
                rememberRevision(writtenRevision, savedTree);
                clearDirtyState();

                ConfigChangeEvent internalEvent = createEvent(before, internalAfter,
                    ConfigChangeOrigin.INTERNAL, writtenRevision);
                if (internalEvent != null) publish(internalEvent);
                ConfigChangeEvent externalEvent = createEvent(before, externalAfter,
                    ConfigChangeOrigin.EXTERNAL, writtenRevision);
                if (externalEvent != null) publish(externalEvent);
                return ConfigSaveResult.success(true, writtenRevision, "Configuration saved.");
            } catch (IOException exception) {
                return ConfigSaveResult.failure(disk.revision(),
                    "Could not save the editor document: " + exception.getMessage());
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary.toPath());
                    } catch (IOException ignored) { }
                }
            }
        }
        }
    }

    @Override
    public void save(boolean pruneEmptySections) {
        ConfigSaveResult result = saveChecked(pruneEmptySections);
        if (!result.success()) throw new ConfigSaveException(result);
    }

    @Override
    public void save() {
        save(true);
    }

    @Override
    public ConfigFile saveAs(String name) {
        synchronized (stateLock) {
            if (file == null || config == null) return this;
            File newFile = new File(STEMCraftAPI.api().getDataFolder(), name);
            try {
                File parent = newFile.getAbsoluteFile().getParentFile();
                if (parent != null) parent.mkdirs();
                config.save(newFile);
                return this;
            } catch (IOException exception) {
                logError("Could not save config file as: " + name, exception);
                return this;
            }
        }
    }

    @Override
    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    @Override
    public boolean getSaveDefaults() {
        return saveDefaults;
    }

    @Override
    public void setSaveDefaults(boolean saveDefaults) {
        this.saveDefaults = saveDefaults;
    }

    private Set<String> localChangePaths(Map<String, Object> active) {
        if (fullSaveRequired) return ConfigTree.equal(active, baseline) ? Set.of() : Set.of("");

        Set<String> effective = new LinkedHashSet<>();
        for (String path : dirtyPaths) {
            if (!ConfigTree.equal(ConfigTree.valueAt(active, path), ConfigTree.valueAt(baseline, path))) {
                effective.add(path);
            }
        }
        return ConfigTree.reducePaths(effective);
    }

    private boolean externalPathConflicts(String externalPath,
                                          Set<String> localPaths,
                                          Map<String, Object> active,
                                          Map<String, Object> disk) {
        for (String localPath : localPaths) {
            if (!ConfigTree.overlaps(localPath, externalPath)) continue;
            Object localValue = ConfigTree.valueAt(active, localPath);
            Object diskValue = ConfigTree.valueAt(disk, localPath);
            if (!ConfigTree.equal(localValue, diskValue)) return true;
        }
        return false;
    }

    private void recalculateDirtyState(Map<String, Object> active) {
        if (fullSaveRequired && ConfigTree.equal(active, baseline)) fullSaveRequired = false;
        dirtyPaths.removeIf(path -> ConfigTree.equal(
            ConfigTree.valueAt(active, path), ConfigTree.valueAt(baseline, path)));
        dirty = fullSaveRequired || !dirtyPaths.isEmpty();
    }

    private void clearDirtyState() {
        dirty = false;
        fullSaveRequired = false;
        dirtyPaths.clear();
    }

    private void rememberRevision(String revision, Map<String, Object> tree) {
        if (revision == null || revision.isBlank() || tree == null) return;
        revisionSnapshots.put(revision, castMap(ConfigTree.cloneValue(tree)));
        while (revisionSnapshots.size() > 32) {
            revisionSnapshots.remove(revisionSnapshots.keySet().iterator().next());
        }
    }

    private void replaceActiveConfiguration(Map<String, Object> tree) {
        replaceActiveConfiguration(toConfiguration(tree));
    }

    private void replaceActiveConfiguration(YamlConfiguration replacement) {
        if (config == replacement) return;
        replaceSectionContents(config, replacement);
        setConfigFile(this);
        setSection(config);
    }

    private static YamlConfiguration toConfiguration(Map<String, Object> tree) {
        YamlConfiguration configuration = new YamlConfiguration();
        writeTree(configuration, tree);
        return configuration;
    }

    private static void writeTree(ConfigurationSection target, Map<String, Object> tree) {
        for (Map.Entry<String, Object> entry : tree.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                ConfigurationSection child = target.createSection(entry.getKey());
                Map<String, Object> childTree = new LinkedHashMap<>();
                for (Map.Entry<?, ?> childEntry : map.entrySet()) {
                    if (childEntry.getKey() instanceof String key) childTree.put(key, childEntry.getValue());
                }
                writeTree(child, childTree);
            } else {
                target.set(entry.getKey(), ConfigTree.cloneValue(value));
            }
        }
    }

    private static void replaceSectionContents(ConfigurationSection target, ConfigurationSection source) {
        for (String key : new LinkedHashSet<>(target.getKeys(false))) {
            if (!source.contains(key) && !source.isConfigurationSection(key)) target.set(key, null);
        }

        for (String key : source.getKeys(false)) {
            ConfigurationSection sourceSection = source.getConfigurationSection(key);
            if (sourceSection != null) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    target.set(key, null);
                    targetSection = target.createSection(key);
                }
                replaceSectionContents(targetSection, sourceSection);
            } else {
                target.set(key, ConfigTree.cloneValue(source.get(key)));
            }
        }
    }

    private ConfigChangeEvent createEvent(Map<String, Object> before,
                                          Map<String, Object> after,
                                          ConfigChangeOrigin origin,
                                          String revision) {
        Set<String> paths = ConfigTree.diffPaths(before, after);
        if (paths.isEmpty()) return null;

        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        for (String path : paths) {
            oldValues.put(path, publicValue(ConfigTree.valueAt(before, path)));
            newValues.put(path, publicValue(ConfigTree.valueAt(after, path)));
        }
        return new ConfigChangeEvent(name, origin, paths, oldValues, newValues, revision);
    }

    private static Object publicValue(Object value) {
        return value == ConfigTree.MISSING ? null : ConfigTree.cloneValue(value);
    }

    private void publish(ConfigChangeEvent event) {
        if (event == null) return;
        for (ListenerRegistration registration : listeners) {
            if (registration.closed.get()
                || (registration.path != null && !event.affects(registration.path))) continue;
            Runnable callback = () -> {
                if (registration.closed.get()) return;
                try {
                    registration.listener.accept(event);
                } catch (Throwable throwable) {
                    logError("Configuration change listener failed for " + name, throwable);
                }
            };
            try {
                changeDispatcher.accept(callback);
            } catch (Throwable throwable) {
                logError("Could not dispatch configuration change for " + name, throwable);
            }
        }
    }

    private DiskSnapshot readDisk(File target) throws IOException, InvalidConfigurationException {
        boolean exists = target.exists();
        byte[] bytes = exists ? Files.readAllBytes(target.toPath()) : new byte[0];
        YamlConfiguration loaded = new YamlConfiguration();
        if (bytes.length > 0) loaded.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        return new DiskSnapshot(loaded, ConfigTree.snapshot(loaded), digest(bytes), FileStamp.from(target));
    }

    private static String revisionOf(File target) {
        if (target == null || !target.exists()) return digest(new byte[0]);
        try {
            return digest(Files.readAllBytes(target.toPath()));
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void atomicReplace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void pruneEmptySections(ConfigurationSection section) {
        Set<String> keys = new LinkedHashSet<>(section.getKeys(false));
        for (String key : keys) {
            if (!section.isConfigurationSection(key)) continue;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) continue;
            pruneEmptySections(child);
            if (child.getKeys(false).isEmpty() && child.getValues(false).isEmpty()) section.set(key, null);
        }
    }

    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static void logError(String message, Throwable throwable) {
        try {
            STEMCraftAPI api = STEMCraftAPI.api();
            if (api != null && api.messages() != null) api.messages().error(message, throwable);
        } catch (Throwable ignored) {
            // Config tests and early bootstrap may not have an API instance.
        }
    }

    private record DiskSnapshot(YamlConfiguration configuration,
                                Map<String, Object> tree,
                                String revision,
                                FileStamp stamp) {}

    private record FileStamp(boolean exists, long modified, long size) {
        static FileStamp from(File file) {
            if (file == null || !file.exists()) return missing();
            return new FileStamp(true, file.lastModified(), file.length());
        }

        static FileStamp missing() {
            return new FileStamp(false, 0L, 0L);
        }
    }

    private final class ListenerRegistration implements ConfigSubscription {
        private final String path;
        private final Consumer<ConfigChangeEvent> listener;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ListenerRegistration(String path, Consumer<ConfigChangeEvent> listener) {
            this.path = path;
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) listeners.remove(this);
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }
    }
}
