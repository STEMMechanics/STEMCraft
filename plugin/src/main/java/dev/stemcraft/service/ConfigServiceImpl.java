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

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.service.config.ConfigChangeEvent;
import dev.stemcraft.api.service.config.ConfigSaveResult;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.service.config.ConfigSubscription;
import dev.stemcraft.config.ConfigFileImpl;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Shared configuration service. One WatchService is used for all opted-in
 * files; individual services only register subscriptions with this service.
 */
public class ConfigServiceImpl extends BaseService implements ConfigService {

    private static final long AUTO_SAVE_INTERVAL = 20 * 60 * 5; // 5 minutes
    private static final long RECONCILE_INTERVAL = 20 * 5; // 5 seconds
    private static final String RECONCILE_TASK_ID = "config-service-reconcile";

    private final Map<String, ConfigFileImpl> files = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ConfigChangeEvent>> globalListeners = new CopyOnWriteArrayList<>();
    private final Map<String, ConfigSubscription> globalBindings = new LinkedHashMap<>();
    private final Set<String> watchedFiles = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<WatchKey, Path> watchDirectories = new LinkedHashMap<>();
    private final Object watcherLock = new Object();
    private WatchService watchService;
    private Thread watcherThread;
    private volatile boolean enabled;

    public ConfigServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        enabled = true;
        startWatcher();
        if (api != null && api.tasks() != null) {
            api.tasks().repeating(AUTO_SAVE_INTERVAL, this::onSave);
            api.tasks().repeating(RECONCILE_TASK_ID, RECONCILE_INTERVAL, RECONCILE_INTERVAL,
                this::reconcileWatchedFiles);
        }
    }

    @Override
    public void onDisable() {
        enabled = false;
        if (api != null && api.tasks() != null) {
            api.tasks().cancel(RECONCILE_TASK_ID);
        }

        synchronized (watcherLock) {
            for (ConfigFileImpl file : files.values()) file.clearSubscriptions();
            globalListeners.clear();
            globalBindings.clear();
            watchedFiles.clear();
            watchDirectories.clear();
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException ignored) {
                    // Shutdown is best effort; the thread is interrupted below.
                }
                watchService = null;
            }
            if (watcherThread != null) watcherThread.interrupt();
            watcherThread = null;
            files.clear();
        }
    }

    @Override
    public void onSave() throws IllegalStateException {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, ConfigFileImpl> entry : snapshotFiles().entrySet()) {
            ConfigFileImpl file = entry.getValue();
            if (!file.isAutoSave() || !file.isDirty()) continue;

            ConfigSaveResult result = file.saveChecked();
            if (!result.success() || file.isDirty()) {
                failures.add(entry.getKey() + (result.message().isBlank() ? "" : ": " + result.message()));
            }
        }
        if (!failures.isEmpty()) throw new IllegalStateException(
            "Config files remain unsaved: " + String.join(", ", failures));
    }

    @Override
    public @Nullable ConfigFile load(@NotNull String name, boolean createIfNotExist) {
        File expected = resolveDataFile(name);
        String key = key(expected);
        ConfigFileImpl existing = files.get(key);
        if (existing != null) return existing;

        ConfigFileImpl configFile = new ConfigFileImpl();
        if (configFile.load(name, createIfNotExist)) return register(key(configFile.getFile()), configFile);
        return null;
    }

    @Override
    public @Nullable ConfigFile load(@NotNull File parent,
                                     @NotNull String name,
                                     boolean createIfNotExist) {
        if (!parent.exists() || !parent.isDirectory()) {
            throw new IllegalArgumentException("Parent must be an existing directory");
        }

        File expected = new File(parent, name.contains(".") ? name : name + ".yml");
        String key = key(expected);
        ConfigFileImpl existing = files.get(key);
        if (existing != null) return existing;

        ConfigFileImpl configFile = new ConfigFileImpl();
        if (configFile.load(parent, name, createIfNotExist)) return register(key(configFile.getFile()), configFile);
        return null;
    }

    @Override
    public @Nullable ConfigFile load(@NotNull File file, boolean createIfNotExist) {
        if (file.exists() && file.isDirectory()) throw new IllegalArgumentException("File must not be a directory");

        String key = key(file);
        ConfigFileImpl existing = files.get(key);
        if (existing != null) return existing;

        ConfigFileImpl configFile = new ConfigFileImpl();
        if (configFile.load(file, createIfNotExist)) return register(key(configFile.getFile()), configFile);
        return null;
    }

    @Override
    public void watch(@NotNull String name) {
        ConfigFile file = load(name, false);
        if (file instanceof ConfigFileImpl configFile) watch(configFile);
    }

    @Override
    public void watch(@NotNull File file) {
        ConfigFile loaded = load(file, false);
        if (loaded instanceof ConfigFileImpl configFile) watch(configFile);
    }

    @Override
    public void watch(@NotNull ConfigFile file) {
        if (file instanceof ConfigFileImpl configFile) watch(configFile);
        else watch(file.getName());
    }

    @Override
    public ConfigSubscription onChange(@NotNull String name,
                                       @NotNull Consumer<ConfigChangeEvent> listener) {
        ConfigFile file = load(name, false);
        if (!(file instanceof ConfigFileImpl configFile)) return ConfigSubscription.noop();
        watch(configFile);
        return configFile.onChange(listener);
    }

    @Override
    public ConfigSubscription onChange(@NotNull String name,
                                       @NotNull String path,
                                       @NotNull Consumer<ConfigChangeEvent> listener) {
        ConfigFile file = load(name, false);
        if (!(file instanceof ConfigFileImpl configFile)) return ConfigSubscription.noop();
        watch(configFile);
        return configFile.onChange(path, listener);
    }

    @Override
    public ConfigSubscription onAnyChange(@NotNull Consumer<ConfigChangeEvent> listener) {
        globalListeners.add(listener);
        synchronized (watcherLock) {
            for (Map.Entry<String, ConfigFileImpl> entry : files.entrySet()) bindGlobal(entry.getKey(), entry.getValue());
        }
        return new ConfigSubscription() {
            private boolean closed;

            @Override
            public void close() {
                if (closed) return;
                closed = true;
                globalListeners.remove(listener);
            }

            @Override
            public boolean isClosed() {
                return closed;
            }
        };
    }

    @Override
    public ConfigSubscription onChange(@NotNull File file,
                                       @NotNull Consumer<ConfigChangeEvent> listener) {
        ConfigFile loaded = load(file, false);
        if (!(loaded instanceof ConfigFileImpl configFile)) return ConfigSubscription.noop();
        watch(configFile);
        return configFile.onChange(listener);
    }

    @Override
    public ConfigSubscription onChange(@NotNull ConfigFile file,
                                       @NotNull Consumer<ConfigChangeEvent> listener) {
        if (!(file instanceof ConfigFileImpl configFile)) return onChange(file.getName(), listener);
        watch(configFile);
        return configFile.onChange(listener);
    }

    @Override
    public ConfigSubscription onChange(@NotNull File file,
                                       @NotNull String path,
                                       @NotNull Consumer<ConfigChangeEvent> listener) {
        ConfigFile loaded = load(file, false);
        if (!(loaded instanceof ConfigFileImpl configFile)) return ConfigSubscription.noop();
        watch(configFile);
        return configFile.onChange(path, listener);
    }

    @Override
    public ConfigSubscription onChange(@NotNull ConfigFile file,
                                       @NotNull String path,
                                       @NotNull Consumer<ConfigChangeEvent> listener) {
        if (!(file instanceof ConfigFileImpl configFile)) return onChange(file.getName(), path, listener);
        watch(configFile);
        return configFile.onChange(path, listener);
    }

    private ConfigFileImpl register(String path, ConfigFileImpl configFile) {
        configFile.setChangeDispatcher(this::dispatchOnMain);
        files.put(path, configFile);
        synchronized (watcherLock) {
            bindGlobal(path, configFile);
        }
        return configFile;
    }

    private void bindGlobal(String path, ConfigFileImpl configFile) {
        if (globalListeners.isEmpty() || globalBindings.containsKey(path)) return;
        globalBindings.put(path, configFile.onChange(event -> {
            for (Consumer<ConfigChangeEvent> listener : globalListeners) {
                try {
                    listener.accept(event);
                } catch (Throwable throwable) {
                    log("Global configuration listener failed for " + event.fileName(), throwable);
                }
            }
        }));
    }

    private void watch(ConfigFileImpl configFile) {
        File file = configFile.getFile();
        if (file == null) return;
        String fileKey = key(file);
        synchronized (watcherLock) {
            watchedFiles.add(fileKey);
            if (!enabled) return;
            try {
                ensureWatchService();
                registerDirectory(file.getAbsoluteFile().toPath().getParent());
            } catch (IOException exception) {
                log("Could not monitor configuration directory " + file.getParent(), exception);
            }
        }
    }

    private void startWatcher() {
        synchronized (watcherLock) {
            try {
                ensureWatchService();
            } catch (IOException exception) {
                log("Could not start configuration file monitoring", exception);
                return;
            }

            try {
                for (String watched : watchedFiles) {
                    ConfigFileImpl file = files.get(watched);
                    if (file != null) registerDirectory(file.getFile().getAbsoluteFile().toPath().getParent());
                }
            } catch (IOException exception) {
                log("Could not register configuration directories", exception);
            }

            if (watcherThread != null && watcherThread.isAlive()) return;
            watcherThread = new Thread(this::watchLoop, "STEMCraft-ConfigWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
        }
    }

    private void registerDirectory(Path directory) throws IOException {
        if (directory == null) return;
        boolean registered = watchDirectories.values().stream().anyMatch(directory::equals);
        if (registered) return;
        WatchKey watchKey = directory.register(watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);
        watchDirectories.put(watchKey, directory);
    }

    private void ensureWatchService() throws IOException {
        if (watchService == null) watchService = FileSystems.getDefault().newWatchService();
    }

    private void watchLoop() {
        while (enabled) {
            WatchService service;
            synchronized (watcherLock) {
                service = watchService;
            }
            if (service == null) return;

            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException exception) {
                return;
            }

            Path directory;
            synchronized (watcherLock) {
                directory = watchDirectories.get(key);
            }
            if (directory != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    Object context = event.context();
                    if (!(context instanceof Path changed)) continue;
                    Path changedFile = directory.resolve(changed).toAbsolutePath().normalize();
                    if (watchedFiles.contains(key(changedFile.toFile()))) scheduleReconcile(changedFile);
                }
            }

            if (!key.reset()) {
                synchronized (watcherLock) {
                    watchDirectories.remove(key);
                }
            }
        }
    }

    private void scheduleReconcile(Path path) {
        dispatchOnMain(() -> {
            ConfigFileImpl file = files.get(key(path.toFile()));
            if (file != null) file.reconcileExternal(true);
        });
    }

    private void reconcileWatchedFiles() {
        if (!enabled) return;
        Set<String> snapshot;
        synchronized (watcherLock) {
            snapshot = new LinkedHashSet<>(watchedFiles);
        }
        for (String watched : snapshot) {
            ConfigFileImpl file = files.get(watched);
            if (file != null) file.reconcileExternal();
        }
    }

    private void dispatchOnMain(Runnable runnable) {
        if (api == null || api.tasks() == null) {
            runnable.run();
            return;
        }
        try {
            api.tasks().runSync(runnable);
        } catch (Throwable throwable) {
            // A mocked/minimal API may not provide a scheduler; preserve the
            // callback rather than losing a validated configuration change.
            runnable.run();
        }
    }

    private Map<String, ConfigFileImpl> snapshotFiles() {
        synchronized (watcherLock) {
            return new LinkedHashMap<>(files);
        }
    }

    private File resolveDataFile(String name) {
        String resolved = name.contains(".") ? name : name + ".yml";
        File dataFolder = api == null ? new File(".") : api.getDataFolder();
        return new File(dataFolder, resolved);
    }

    private static String key(File file) {
        try {
            return file.getCanonicalFile().toPath().normalize().toString();
        } catch (IOException exception) {
            return file.getAbsoluteFile().toPath().normalize().toString();
        }
    }

    private void log(String message, Throwable throwable) {
        try {
            if (api != null && api.messages() != null) api.messages().error(message, throwable);
        } catch (Throwable ignored) {
            // Logging must not prevent configuration monitoring from continuing.
        }
    }
}
