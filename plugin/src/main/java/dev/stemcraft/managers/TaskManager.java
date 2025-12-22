package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.services.task.TaskCallback;
import dev.stemcraft.api.services.task.TaskRetryCallback;
import dev.stemcraft.api.services.task.TaskRetryable;
import dev.stemcraft.api.services.task.TaskService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TaskManager implements TaskService {
    private final static String DATA_FILE_NAME = "task-data.yml";
    private final STEMCraft plugin;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private final Map<String, TaskCallback> persistentCallbacks = new HashMap<>();
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, Long> tasksRunAt = new ConcurrentHashMap<>();

    /*  Constructor */
    public TaskManager(STEMCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);

        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.error("PERSISTENT_TIMER_CREATE_DATA_FILE_FAILED", e);
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    @Override
    public void onDisable() {
        save();
    }

    @Override
    public void registerPersistentCallback(String persistentType, TaskCallback callback) {
        persistentCallbacks.put(persistentType, callback);
        loadPersistentTimers(persistentType);
    }

    /**
     * Schedule a timer:
     *  - type: which callback type to invoke
     *  - id:   unique id within that type
     *  - data: serialized data for that timer
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

    @Override
    public String getPersistentData(String id) {
        String path = "tasks." + id + ".data";
        if (!dataConfig.contains(path)) {
            return null;
        }
        return dataConfig.getString(path);
    }

    @Override
    public void setPersistentData(String id, String data) {
        String path = "tasks." + id + ".data";
        dataConfig.set(path, data);
        save();
    }

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

    @Override
    public void runLater(long delay, Runnable task) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(InstanceHolder.plugin(), task, delay);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(InstanceHolder.plugin(), task);
    }

    @Override
    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(InstanceHolder.plugin(), task);
    }


    /**
     * Runs once after delay. Cancels previous task with same id.
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
     */
    @Override
    public boolean exists(String id) {
        ConfigurationSection tasksSection = dataConfig.getConfigurationSection("tasks");
        if (tasksSection != null && tasksSection.contains(id)) {
            return true;
        }

        return tasks.containsKey(id);
    }

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

    @Override
    public void repeating(String id, long delay, long period, Runnable task) {
        if(id == null || id.isEmpty()) {
            return;
        }

        if(exists(id)) {
            cancel(id);
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(
                InstanceHolder.plugin(),
                task,
                delay,
                period
        );

        tasks.put(id, bukkitTask);
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
     * Call onEnable() after registering all callback types.
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
            plugin.error("PERSISTENT_TIMER_SAVE_FAILED", e);
        }
    }
}