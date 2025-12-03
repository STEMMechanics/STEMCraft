package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.persistenttimer.PersistentTimerCallback;
import dev.stemcraft.api.services.persistenttimer.PersistentTimerService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PersistentTimerManager implements PersistentTimerService {
    private final STEMCraft plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;
    private final Map<String, PersistentTimerCallback> typeCallbacks = new HashMap<>();
    private final Map<String, BukkitTask> tasks = new HashMap<>();

    public PersistentTimerManager(STEMCraft plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "timerdata.yml");

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

    public void onDisable() {
        save();
    }

    @Override
    public void registerType(String type, PersistentTimerCallback callback) {
        typeCallbacks.put(type, callback);
        loadTimers(type);
    }

    /**
     * Schedule a timer:
     *  - type: which callback type to invoke
     *  - id:   unique id within that type
     *  - data: serialized data for that timer
     */
    @Override
    public void schedule(String type, String id, String data, Duration delay) {
        long runAt = System.currentTimeMillis() + delay.toMillis();

        String path = "timers." + type + "." + id;
        dataConfig.set(path + ".runAt", runAt);
        dataConfig.set(path + ".data", data);
        save();

        scheduleInRuntime(type, id, runAt, data);
    }

    @Override
    public void cancel(String type, String id) {
        String path = "timers." + type + "." + id;
        dataConfig.set(path, null);
        save();

        String key = type + ":" + id;
        BukkitTask task = tasks.remove(key);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public boolean exists(String type, String id) {
        String base = "timers." + type + "." + id;
        if (!dataConfig.contains(base + ".runAt")) {
            return false;
        }

        long runAt = dataConfig.getLong(base + ".runAt", -1);
        if (runAt <= 0) {
            return false;
        }

        // treat expired timers as non existent
        return runAt > System.currentTimeMillis();
    }

    @Override
    public List<String> list(String type) {
        List<String> ids = new java.util.ArrayList<>();

        ConfigurationSection typeSection = dataConfig.getConfigurationSection("timers." + type);
        if (typeSection == null) {
            return ids;
        }

        long now = System.currentTimeMillis();

        for (String id : typeSection.getKeys(false)) {
            String base = "timers." + type + "." + id;
            long runAt = dataConfig.getLong(base + ".runAt", -1);
            if (runAt > now) {
                ids.add(id);
            }
        }

        return ids;
    }

    @Override
    public String get(String type, String id) {
        String path = "timers." + type + "." + id + ".data";
        if (!dataConfig.contains(path)) {
            return null;
        }
        return dataConfig.getString(path);
    }

    @Override
    public long remaining(String type, String id) {
        String base = "timers." + type + "." + id;
        if (!dataConfig.contains(base + ".runAt")) {
            return -1L; // not found
        }

        long runAt = dataConfig.getLong(base + ".runAt", -1);
        if (runAt <= 0) {
            return -1L;
        }

        long remaining = runAt - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    /**
     * Call onEnable() after registering all callback types.
     */
    private void loadTimers(String type) {
        ConfigurationSection timersSection = dataConfig.getConfigurationSection("timers");
        if (timersSection == null) return;

        ConfigurationSection typeSection = timersSection.getConfigurationSection(type);
        if (typeSection == null) return;

        for (String id : typeSection.getKeys(false)) {
            String base = "timers." + type + "." + id;
            long runAt = dataConfig.getLong(base + ".runAt", -1);
            String data = dataConfig.getString(base + ".data", "");

            if (runAt <= 0) {
                dataConfig.set(base, null);
                continue;
            }

            scheduleInRuntime(type, id, runAt, data);
        }

        save();
    }

    private void scheduleInRuntime(String type, String id, long runAt, String data) {
        PersistentTimerCallback callback = typeCallbacks.get(type);
        if (callback == null) {
            // no callback for this type (maybe plugin changed)
            return;
        }

        String key = type + ":" + id;

        // cancel old task if present
        BukkitTask old = tasks.remove(key);
        if (old != null) {
            old.cancel();
        }

        long now = System.currentTimeMillis();
        long remainingMillis = runAt - now;

        if (remainingMillis <= 0) {
            // overdue: run immediately
            Bukkit.getScheduler().runTask(plugin, () -> runAndClear(type, id, data));
            return;
        }

        long ticks = Math.max(1, remainingMillis / 50L);
        if (ticks > Integer.MAX_VALUE) {
            ticks = Integer.MAX_VALUE;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> runAndClear(type, id, data),
                ticks);

        tasks.put(key, task);
    }

    private void runAndClear(String type, String id, String data) {
        String key = type + ":" + id;

        // remove task reference
        tasks.remove(key);

        PersistentTimerCallback callback = typeCallbacks.get(type);
        if (callback != null) {
            try {
                callback.run(type, id, data);
            } catch (Throwable t) {
                plugin.getComponentLogger().error("PERSISTENT_TIMER_RUN_FAILED", t);
            }
        }

        String path = "timers." + type + "." + id;
        dataConfig.set(path, null);
        save();
    }

    private void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.error("PERSISTENT_TIMER_SAVE_FAILED", e);
        }
    }
}