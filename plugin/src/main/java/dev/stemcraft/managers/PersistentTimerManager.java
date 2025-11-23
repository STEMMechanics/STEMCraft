package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.persistenttimer.PersistentTimerCallback;
import dev.stemcraft.api.services.persistenttimer.PersistentTimerService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class PersistentTimerManager implements PersistentTimerService {
    private final STEMCraft plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;
    private final Map<String, PersistentTimerCallback> typeCallbacks = new HashMap<>();

    public PersistentTimerManager(STEMCraft plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "timerdata.yml");

        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.error("Could not create timer data file.", e);
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void onEnable() {
        loadTimers();
    }

    public void onDisable() {
        save();
    }

    public void registerType(String type, PersistentTimerCallback callback) {
        typeCallbacks.put(type, callback);
    }

    /**
     * Schedule a timer:
     *  - type: which callback type to invoke
     *  - id:   unique id within that type
     *  - data: serialized data for that timer
     */
    public void schedule(String type, String id, String data, Duration delay) {
        long runAt = System.currentTimeMillis() + delay.toMillis();

        String path = "timers." + type + "." + id;
        dataConfig.set(path + ".runAt", runAt);
        dataConfig.set(path + ".data", data);
        save();

        scheduleInRuntime(type, id, runAt, data);
    }

    public void cancel(String type, String id) {
        String path = "timers." + type + "." + id;
        dataConfig.set(path, null);
        save();
    }

    /**
     * Call onEnable() after registering all callback types.
     */
    private void loadTimers() {
        ConfigurationSection timersSection = dataConfig.getConfigurationSection("timers");
        if (timersSection == null) return;

        for (String type : timersSection.getKeys(false)) {
            ConfigurationSection typeSection = timersSection.getConfigurationSection(type);
            if (typeSection == null) continue;

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
        }

        save();
    }

    private void scheduleInRuntime(String type, String id, long runAt, String data) {
        PersistentTimerCallback callback = typeCallbacks.get(type);
        if (callback == null) {
            // no callback for this type (maybe plugin changed)
            return;
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

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> runAndClear(type, id, data),
                ticks);
    }

    private void runAndClear(String type, String id, String data) {
        PersistentTimerCallback callback = typeCallbacks.get(type);
        if (callback != null) {
            try {
                callback.run(id, data);
            } catch (Throwable t) {
                t.printStackTrace();
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
            plugin.error("Could not save timer data file.", e);
        }
    }
}