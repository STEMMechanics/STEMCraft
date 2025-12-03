package dev.stemcraft.api.services.persistenttimer;

import dev.stemcraft.api.services.STEMCraftService;

import java.time.Duration;
import java.util.List;

public interface PersistentTimerService extends STEMCraftService {
    void registerType(String type, PersistentTimerCallback callback);
    void schedule(String type, String id, String data, Duration delay);
    void cancel(String type, String id);
    boolean exists(String type, String id);
    List<String> list(String type);
    String get(String type, String id);
    long remaining(String type, String id);
}
