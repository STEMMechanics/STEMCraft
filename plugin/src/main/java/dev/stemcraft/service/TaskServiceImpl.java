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
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.task.TaskCallback;
import dev.stemcraft.api.service.task.TaskRetryCallback;
import dev.stemcraft.api.service.task.TaskRetryable;
import dev.stemcraft.api.service.task.TaskService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the TaskService for managing scheduled tasks and persistent timers.
 */
public class TaskServiceImpl extends BaseService implements TaskService {
    private final static String DATA_FILE_NAME = "task-data.yml";
    private File dataFile;
    private YamlConfiguration dataConfig;
    private final Map<String, TaskCallback> persistentCallbacks = new HashMap<>();
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, Long> tasksRunAt = new ConcurrentHashMap<>();

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
        this.dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);

        if (!dataFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                dataFile.createNewFile();
            } catch (IOException e) {
                api.messages().error("PERSISTENT_TIMER_CREATE_DATA_FILE_FAILED", e);
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Called when the service is being disabled.
     */
    @Override
    public void onDisable() {
        save();
    }

    /**
     * Register a persistent timer callback type.
     *
     * @param persistentType The unique type name for this callback.
     * @param callback The callback to invoke when the timer runs.
     */
    @Override
    public void registerPersistentCallback(String persistentType, TaskCallback callback) {
        persistentCallbacks.put(persistentType, callback);
        loadPersistentTimers(persistentType);
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
    public void runLaterPersistent(String persistentType, String id, String data, long delay) {
        long runAt = System.currentTimeMillis() + (delay * 50L);

        cancel(id);
        String path = "tasks." + id;
        dataConfig.set(path + ".type", persistentType);
        dataConfig.set(path + ".runAt", runAt);
        dataConfig.set(path + ".data", data);
        save();

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
    public String getPersistentData(String id) {
        String path = "tasks." + id + ".data";
        if (!dataConfig.contains(path)) {
            return null;
        }
        return dataConfig.getString(path);
    }

    /**
     * Set the persistent data for a timer.
     *
     * @param id The unique timer id.
     * @param data The serialized data to store.
     */
    @Override
    public void setPersistentData(String id, String data) {
        String path = "tasks." + id + ".data";
        dataConfig.set(path, data);
        save();
    }

    /**
     * List all persistent timer ids of a given type.
     *
     * @param type The callback type.
     * @return A list of timer ids.
     */
    @Override
    public List<String> listPersistentTimers(String type) {
        List<String> ids = new java.util.ArrayList<>();

        ConfigurationSection typeSection = dataConfig.getConfigurationSection("tasks");
        if (typeSection == null) return ids;

        for (String id : typeSection.getKeys(false)) {
            String timerType = dataConfig.getString("tasks." + id + ".type", "");
            if (timerType.equals(type)) {
                ids.add(id);
            }
        }

        return ids;
    }

    /**
     * Runs a task later after a delay.
     *
     * @param delay The delay in ticks.
     * @param task The task to run.
     */
    @Override
    public void runLater(long delay, Runnable task) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(InstanceHolder.plugin(), task, delay);
    }

    /**
     * Runs a task asynchronously.
     *
     * @param task The task to run.
     */
    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(InstanceHolder.plugin(), task);
    }

    /** {@inheritDoc} */
    @Override
    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(InstanceHolder.plugin(), task);
    }


    /**
     * Runs once after delay. Cancels previous task with same id.
     *
     * @param id The unique task id.
     * @param delay The delay in ticks.
     * @param task The task to run.
     */
    @Override
    public void runOnceDelay(String id, long delay, Runnable task) {
        if(id == null || id.isEmpty()) {
            return;
        }

        if(exists(id)) {
            cancel(id);
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(
                InstanceHolder.plugin(),
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
     * Cancels a scheduled task by id.
     *
     * @param id The unique task id. Supports asterisk wildcards.
     */
    @Override
    public void cancel(String id) {
        Set<String> idList = new HashSet<>();
        ConfigurationSection tasksSection = dataConfig.getConfigurationSection("tasks");

        if(id.indexOf('*') != -1) {
            String prefix = id.substring(0, id.indexOf('*'));
            String suffix = id.substring(id.indexOf('*') + 1);

            if (tasksSection != null) {
                for (String item : tasksSection.getKeys(false)) {
                    if (item.startsWith(prefix) && item.endsWith(suffix)) {
                        idList.add(item);
                    }
                }
            }

            for (String item : tasks.keySet()) {
                if (item.startsWith(prefix) && item.endsWith(suffix)) {
                    idList.add(item);
                }
            }
        } else {
            idList.add(id);
        }

        boolean changed = false;

        for(String item : idList) {
            if (tasksSection != null && tasksSection.contains(item)) {
                tasksSection.set(item, null);
                changed = true;
            }

            BukkitTask t = tasks.remove(item);
            tasksRunAt.remove(item);

            if (t != null) t.cancel();
        }

        if(changed) {
            save();
        }
    }

    /**
     * Returns true if a task with this id is scheduled.
     *
     * @param id The unique task id.
     * @return true if the task exists, false otherwise.
     */
    @Override
    public boolean exists(String id) {
        ConfigurationSection tasksSection = dataConfig.getConfigurationSection("tasks");
        if (tasksSection != null && tasksSection.contains(id)) {
            return true;
        }

        return tasks.containsKey(id);
    }

    /**
     * Get the remaining time in ticks for a scheduled task.
     *
     * @param id The unique task id.
     * @return The remaining time in ticks, or -1 if not found.
     */
    @Override
    public long remaining(String id) {
        if(tasksRunAt.containsKey(id)) {
            long runAt = tasksRunAt.get(id);
            long now = System.currentTimeMillis();
            long remainingMillis = runAt - now;
            return Math.max(0, remainingMillis / 50L);
        }

        String path = "tasks." + id + ".runAt";
        if (!dataConfig.contains(path)) {
            return -1;
        }

        long runAt = dataConfig.getLong(path);
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
    public void repeating(String id, long delay, long period, Runnable task) {
        if(id != null && !id.isEmpty()) {
            if (exists(id)) {
                cancel(id);
            }
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(
                InstanceHolder.plugin(),
                task,
                delay,
                period
        );

        if(id != null && !id.isEmpty()) {
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
    public void retry(int maxRetries, TaskRetryable task, TaskRetryCallback callback, long intervalDelay, long startDelay) {
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

    /**
     * Load persistent timers of a given type from storage.
     *
     * @param persistentType The callback type to load.
     */
    private void loadPersistentTimers(String persistentType) {
        ConfigurationSection timersSection = dataConfig.getConfigurationSection("tasks");
        if (timersSection == null) return;

        for (String id : timersSection.getKeys(false)) {
            String type = dataConfig.getString("tasks." + id + ".type", "");
            if (!type.equals(persistentType)) {
                continue;
            }

            long runAt = dataConfig.getLong("tasks." + id + ".runAt", 0L);
            String data = dataConfig.getString("tasks." + id + ".data", "");

            schedulePersistentTimer(type, id);
        }

        save();
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

        long runAt = dataConfig.getLong("tasks." + id + ".runAt", 0L);
        long delay = runAt - System.currentTimeMillis();

        runOnceDelay( id, Math.max(0, delay), () -> {
            String data = getPersistentData(id);
            if (data != null) {
                callback.run(type, id, data);
            }
            cancel(id);
        });
    }

    private void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            api.messages().error("PERSISTENT_TIMER_SAVE_FAILED", e);
        }
    }
}