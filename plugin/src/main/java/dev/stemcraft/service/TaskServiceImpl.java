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
import dev.stemcraft.api.service.task.TaskCallback;
import dev.stemcraft.api.service.task.TaskRetryCallback;
import dev.stemcraft.api.service.task.TaskRetryable;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the TaskService for managing scheduled tasks and persistent timers.
 */
public class TaskServiceImpl extends BaseService implements TaskService {
    private final static String DATA_FILE_NAME = "task-data.yml";
    private static final String DEBOUNCE_TASK_PREFIX = "task-service:debounce:";
    private File dataFile;
    private final Map<String, TaskCallback> persistentCallbacks = new HashMap<>();
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, Long> tasksRunAt = new ConcurrentHashMap<>();
    private final Map<String, Long> debounceUntil = new ConcurrentHashMap<>();
    private boolean storageReady = false;

    /**
     * Constructor for TaskServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public TaskServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Called when the service is being enabled.
     */
    @Override
    public void onEnable() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logBootstrapError(new IOException("Unable to create data folder " + dataFolder.getPath()));
            return;
        }

        this.dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);
    }

    /**
     * Called when the service is being disabled.
     */
    @Override
    public void onDisable() {
        cancelAll();
    }

    /**
     * Register a persistent timer callback type.
     *
     * @param persistentType The unique type name for this callback.
     * @param callback The callback to invoke when the timer runs.
     */
    @Override
    public void registerPersistentCallback(@NotNull String persistentType, @NotNull TaskCallback callback) {
        persistentCallbacks.put(persistentType, callback);
        if (ensureStorageReady()) {
            loadPersistentTimers(persistentType);
        }
    }

    /**
     * Schedule a timer.
     *
     * @param persistentType The callback type.
     * @param id The unique timer id.
     * @param data The serialized data for the timer.
     * @param delay The delay in ticks before running.
     */
    @Override
    public void runLaterPersistent(@NotNull String persistentType, @NotNull String id, @NotNull String data, long delay) {
        long runAt = System.currentTimeMillis() + (delay * 50L);

        cancel(id);
        if (!ensureStorageReady()) {
            runOnceDelay(id, delay, () -> {
                TaskCallback callback = persistentCallbacks.get(persistentType);
                if (callback != null) {
                    callback.run(persistentType, id, data);
                }
                cancel(id);
            });
            return;
        }

        api.database().update(
            "INSERT INTO persistent_tasks (id, type, run_at, data) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(id) DO UPDATE SET type = excluded.type, run_at = excluded.run_at, data = excluded.data",
            ps -> {
                ps.setString(1, id);
                ps.setString(2, persistentType);
                ps.setLong(3, runAt);
                ps.setString(4, data);
            }
        );

        if(persistentCallbacks.containsKey(persistentType)) {
            schedulePersistentTimer(persistentType, id);
        }
    }

    /**
     * Get the persistent data for a timer.
     *
     * @param id The unique timer id.
     * @return The serialized data or null if not found.
     */
    @Override
    public @Nullable String getPersistentData(@NotNull String id) {
        if (!ensureStorageReady()) {
            return null;
        }
        return api.database().querySingleMapped(
            "SELECT data FROM persistent_tasks WHERE id = ?",
            ps -> ps.setString(1, id),
            rs -> rs.getString(1)
        );
    }

    /**
     * Set the persistent data for a timer.
     *
     * @param id The unique timer id.
     * @param data The serialized data to store.
     */
    @Override
    public void setPersistentData(@NotNull String id, @NotNull String data) {
        if (!ensureStorageReady()) {
            return;
        }
        api.database().update("UPDATE persistent_tasks SET data = ? WHERE id = ?", ps -> {
            ps.setString(1, data);
            ps.setString(2, id);
        });
    }

    /**
     * List all persistent timer ids of a given type.
     *
     * @param type The callback type.
     * @return A list of timer ids.
     */
    @Override
    public @NotNull List<String> listPersistentTimers(@NotNull String type) {
        List<String> ids = new ArrayList<>();
        if (!ensureStorageReady()) {
            return ids;
        }
        api.database().queryEach(
            "SELECT id FROM persistent_tasks WHERE type = ?",
            ps -> ps.setString(1, type),
            rs -> ids.add(rs.getString("id"))
        );
        return ids;
    }

    /**
     * Runs a task later after a delay.
     *
     * @param delay The delay in ticks.
     * @param task The task to run.
     */
    @Override
    public void runLater(long delay, @NotNull Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    /**
     * Runs a task asynchronously.
     *
     * @param task The task to run.
     */
    @Override
    public void runAsync(@NotNull Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    /** {@inheritDoc} */
    @Override
    public void runSync(@NotNull Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }


    /**
     * Runs once after delay. Cancels previous task with same id.
     *
     * @param id The unique task id.
     * @param delay The delay in ticks.
     * @param task The task to run.
     */
    @Override
    public void runOnceDelay(@NotNull String id, long delay, @NotNull Runnable task) {
        if(id.isEmpty()) {
            return;
        }

        if(exists(id)) {
            cancel(id);
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    try {
                        task.run();
                    } finally {
                        tasks.remove(id);
                        tasksRunAt.remove(id);
                    }
                },
                delay
        );

        tasks.put(id, bukkitTask);
        tasksRunAt.put(id, System.currentTimeMillis() + (delay * 50L));
    }

    /**
     * Runs a task immediately and suppresses repeated calls with the same id until
     * the debounce period has elapsed.
     *
     * @param id The unique debounce id.
     * @param period The debounce period in ticks.
     * @param task The task to run.
     */
    @Override
    public void debounce(@NotNull String id, long period, @NotNull Runnable task) {
        if (period <= 0L || id.isEmpty()) {
            task.run();
            return;
        }

        long now = System.currentTimeMillis();
        Long activeUntil = debounceUntil.get(id);
        if (activeUntil != null && activeUntil > now) {
            return;
        }

        long expiresAt = now + (period * 50L);
        debounceUntil.put(id, expiresAt);
        runOnceDelay(debounceTaskId(id), period, () -> clearDebounce(id, expiresAt));
        task.run();
    }


    /**
     * Cancels a scheduled task by id.
     *
     * @param id The unique task id. Supports asterisk wildcards.
     */
    @Override
    public void cancel(@NotNull String id) {
        Set<String> idList = new HashSet<>();

        if(id.indexOf('*') != -1) {
            String prefix = id.substring(0, id.indexOf('*'));
            String suffix = id.substring(id.indexOf('*') + 1);
            if (ensureStorageReady()) {
                api.database().queryEach(
                    "SELECT id FROM persistent_tasks",
                    null,
                    rs -> {
                        String item = rs.getString("id");
                        if (item.startsWith(prefix) && item.endsWith(suffix)) {
                            idList.add(item);
                        }
                    }
                );
            }

            for (String item : tasks.keySet()) {
                if (item.startsWith(prefix) && item.endsWith(suffix)) {
                    idList.add(item);
                }
            }

            for (String debounceId : debounceUntil.keySet()) {
                if (debounceId.startsWith(prefix) && debounceId.endsWith(suffix)) {
                    idList.add(debounceId);
                    idList.add(debounceTaskId(debounceId));
                }
            }
        } else {
            idList.add(id);
            if (debounceUntil.containsKey(id)) {
                idList.add(debounceTaskId(id));
            }
        }

        for(String item : idList) {
            if (ensureStorageReady()) {
                api.database().update("DELETE FROM persistent_tasks WHERE id = ?", ps -> ps.setString(1, item));
            }

            BukkitTask t = tasks.remove(item);
            tasksRunAt.remove(item);
            if (item.startsWith(DEBOUNCE_TASK_PREFIX)) {
                debounceUntil.remove(item.substring(DEBOUNCE_TASK_PREFIX.length()));
            } else {
                debounceUntil.remove(item);
            }

            if (t != null) t.cancel();
        }
    }

    /**
     * Returns true if a task with this id is scheduled.
     *
     * @param id The unique task id.
     * @return true if the task exists, false otherwise.
     */
    @Override
    public boolean exists(@NotNull String id) {
        if (!ensureStorageReady()) {
            return tasks.containsKey(id);
        }
        int count = api.database().querySingleMapped(
            "SELECT COUNT(*) FROM persistent_tasks WHERE id = ?",
            ps -> ps.setString(1, id),
            rs -> rs.getInt(1),
            0
        );
        return count > 0 || tasks.containsKey(id);
    }

    /**
     * Get the remaining time in ticks for a scheduled task.
     *
     * @param id The unique task id.
     * @return The remaining time in ticks, or -1 if not found.
     */
    @Override
    public long remaining(@NotNull String id) {
        if(tasksRunAt.containsKey(id)) {
            long runAt = tasksRunAt.get(id);
            long now = System.currentTimeMillis();
            long remainingMillis = runAt - now;
            return Math.max(0, remainingMillis / 50L);
        }
        if (!ensureStorageReady()) {
            return -1;
        }
        Long runAt = api.database().querySingleMapped(
            "SELECT run_at FROM persistent_tasks WHERE id = ?",
            ps -> ps.setString(1, id),
            rs -> rs.getLong(1)
        );
        if (runAt == null) {
            return -1;
        }
        long now = System.currentTimeMillis();
        long remainingMillis = runAt - now;
        return Math.max(0, remainingMillis / 50L);
    }

    /**
     * Runs a repeating task with given period. Cancels previous task with same id.
     *
     * @param id The unique task id.
     * @param delay The delay in ticks before first run.
     * @param period The period in ticks between runs.
     * @param task The task to run.
     */
    @Override
    public void repeating(@NotNull String id, long delay, long period, @NotNull Runnable task) {
        if(!id.isEmpty()) {
            if (exists(id)) {
                cancel(id);
            }
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                task,
                delay,
                period
        );

        if(!id.isEmpty()) {
            tasks.put(id, bukkitTask);
        }
    }

    /**
     * Cancel every tracked task. Use on plugin shutdown.
     */
    public void cancelAll() {
        for (BukkitTask task : tasks.values()) {
            if (task != null) task.cancel();
        }
        tasks.clear();
        tasksRunAt.clear();
        debounceUntil.clear();
    }

    /**
     * Retry a task multiple times with delay intervals.
     *
     * @param maxRetries The maximum number of retries.
     * @param task The task to retry.
     * @param callback The callback to invoke when done.
     * @param intervalDelay The delay in ticks between retries.
     * @param startDelay The initial delay in ticks before first attempt.
     */
    public void retry(int maxRetries, @NotNull TaskRetryable task, @NotNull TaskRetryCallback callback, long intervalDelay, long startDelay) {
        retry0(maxRetries, task, callback, intervalDelay, startDelay, true);
    }

    /*--------------------------------------------------
     * Private methods
     --------------------------------------------------*/
    private void retry0(int remaining, TaskRetryable task, TaskRetryCallback callback, long intervalDelay, long startDelay, boolean first) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (task.run()) {
                callback.done(RetryResult.SUCCESS);
                return;
            }

            if (remaining <= 1) {
                callback.done(RetryResult.EXHAUSTED);
                return;
            }

            retry0(remaining - 1, task, callback, intervalDelay, startDelay, false);
        }, first ? startDelay : intervalDelay);
    }

    private void clearDebounce(String id, long expectedExpiresAt) {
        debounceUntil.computeIfPresent(id, (key, currentExpiresAt) -> currentExpiresAt.equals(expectedExpiresAt) ? null : currentExpiresAt);
    }

    private String debounceTaskId(String id) {
        return DEBOUNCE_TASK_PREFIX + id;
    }

    /**
     * Load persistent timers of a given type from storage.
     *
     * @param persistentType The callback type to load.
     */
    private void loadPersistentTimers(String persistentType) {
        if (!ensureStorageReady()) {
            return;
        }
        api.database().queryEach(
            "SELECT id FROM persistent_tasks WHERE type = ?",
            ps -> ps.setString(1, persistentType),
            rs -> schedulePersistentTimer(persistentType, rs.getString("id"))
        );
    }

    /**
     * Schedule a persistent timer to run at its stored time.
     *
     * @param type The callback type.
     * @param id The unique timer id.
     */
    private void schedulePersistentTimer(String type, String id) {
        if(tasks.containsKey(id)) {
            // already scheduled
            return;
        }

        TaskCallback callback = persistentCallbacks.get(type);
        if (callback == null) {
            // no callback for this type (maybe plugin changed)
            return;
        }

        if (!ensureStorageReady()) {
            return;
        }
        Long runAtValue = api.database().querySingleMapped(
            "SELECT run_at FROM persistent_tasks WHERE id = ?",
            ps -> ps.setString(1, id),
            rs -> rs.getLong(1)
        );
        if (runAtValue == null) {
            return;
        }
        long runAt = runAtValue;
        long delay = runAt - System.currentTimeMillis();

        runOnceDelay( id, Math.max(0, delay), () -> {
            String data = getPersistentData(id);
            if (data != null) {
                callback.run(type, id, data);
            }
            cancel(id);
        });
    }

    private void ensureStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS persistent_tasks (" +
            "id TEXT PRIMARY KEY," +
            "type TEXT NOT NULL," +
            "run_at INTEGER NOT NULL," +
            "data TEXT NOT NULL" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS persistent_tasks_type ON persistent_tasks(type);");
        api.database().execute("CREATE INDEX IF NOT EXISTS persistent_tasks_run_at ON persistent_tasks(run_at);");
    }

    private void migrateLegacyDataFile() {
        if (api.database().migrationVersion("task-service-state") >= 1) {
            return;
        }

        if (dataFile == null || !dataFile.exists()) {
            api.database().setMigrationVersion("task-service-state", 1);
            return;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection tasksSection = legacy.getConfigurationSection("tasks");
        if (tasksSection != null) {
            for (String id : tasksSection.getKeys(false)) {
                String type = Objects.requireNonNull(legacy.getString("tasks." + id + ".type", ""));
                long runAt = legacy.getLong("tasks." + id + ".runAt", 0L);
                String data = legacy.getString("tasks." + id + ".data", "");
                if (type.isBlank()) {
                    continue;
                }
                api.database().update(
                    "INSERT INTO persistent_tasks (id, type, run_at, data) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(id) DO UPDATE SET type = excluded.type, run_at = excluded.run_at, data = excluded.data",
                    ps -> {
                        ps.setString(1, id);
                        ps.setString(2, type);
                        ps.setLong(3, runAt);
                        ps.setString(4, data);
                    }
                );
            }
        }

        api.database().setMigrationVersion("task-service-state", 1);
    }

    private boolean ensureStorageReady() {
        if (storageReady) {
            return true;
        }
        if (api.database() == null) {
            return false;
        }

        ensureStorage();
        migrateLegacyDataFile();
        storageReady = true;
        return true;
    }

    private void logBootstrapError(@NotNull Throwable error) {
        if (api.messages() != null) {
            api.messages().error("PERSISTENT_TIMER_CREATE_DATA_FILE_FAILED", error);
            return;
        }

        plugin.getLogger().severe("PERSISTENT_TIMER_CREATE_DATA_FILE_FAILED: " + error.getMessage());
    }
}
