package dev.stemcraft.integration;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread skin request coordination with persistent, per-source retry backoff. */
public final class SkinRequests<T> {
    private final Map<String, T> resolved = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, List<Consumer<T>>> pending = new java.util.concurrent.ConcurrentHashMap<>();

    public void request(STEMCraftAPI api, Plugin plugin, String configName, String source,
                        Supplier<T> convert, Consumer<T> ready) {
        String key = configName + "|" + source;
        if (resolved.containsKey(key)) {
            deliver(plugin, ready, resolved.get(key));
            return;
        }
        if (pending.containsKey(key)) {
            pending.get(key).add(ready);
            return;
        }
        String path = "skin-request-retries." + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        ConfigFile config = api.config().load(configName, false);
        if (config == null) {
            plugin.getLogger().warning("Cannot load " + configName + " for skin retry state; skipping skin request");
            return;
        }
        long now = System.currentTimeMillis();
        if (config.getLong(path + ".retry-after", 0) > now) return;
        int attempt = Math.clamp(config.getInt(path + ".attempts", 0), 0, 7) + 1;
        long delay = retryDelayMillis(attempt);
        // Record before starting: an interrupted request must not reset backoff on restart.
        config.set(path + ".attempts", attempt);
        config.set(path + ".retry-after", now + delay);
        config.save();
        pending.put(key, new ArrayList<>(List.of(ready)));
        CompletableFuture.supplyAsync(convert).orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).whenComplete((value, error) -> {
            if (!plugin.isEnabled()) {
                pending.remove(key);
                return;
            }
            api.tasks().nextTick(() -> {
                List<Consumer<T>> callbacks = pending.remove(key);
                if (!plugin.isEnabled() || callbacks == null) return;
                if (error != null || value == null) {
                    // Measure backoff from failure too, in case conversion took a long time.
                    config.set(path + ".retry-after", System.currentTimeMillis() + delay);
                    config.save();
                    plugin.getLogger().warning("Skin request failed (" + configName + ", "
                        + path.substring(path.lastIndexOf('.') + 1) + "): "
                        + failureSummary(error) + "; retry allowed in " + delay / 60000 + " minutes");
                    return;
                }
                resolved.put(key, value);
                config.set(path, null);
                config.save();
                for (Consumer<T> callback : callbacks) {
                    deliver(plugin, callback, value);
                }
            });
        });
    }

    private static <T> void deliver(Plugin plugin, Consumer<T> callback, T value) {
        try { callback.accept(value); }
        catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not apply resolved skin: " + failureSummary(ex));
        }
    }

    static long retryDelayMillis(int attempt) {
        return Math.min(6 * 60 * 60_000L, 5 * 60_000L * (1L << Math.clamp(attempt - 1, 0, 7)));
    }

    static String failureSummary(Throwable error) {
        if (error == null) return "skin service returned no texture";
        while (error.getCause() != null && error.getCause() != error) error = error.getCause();
        // Avoid logging URLs, tokens or repeated stack traces from external services.
        return error.getClass().getSimpleName();
    }
}
