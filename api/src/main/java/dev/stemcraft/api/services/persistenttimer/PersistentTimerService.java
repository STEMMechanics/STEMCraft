package dev.stemcraft.api.services.persistenttimer;

import dev.stemcraft.api.services.STEMCraftService;

import java.time.Duration;

public interface PersistentTimerService extends STEMCraftService {
    public void registerType(String type, PersistentTimerCallback callback);
    public void schedule(String type, String id, String data, Duration delay);
    public void cancel(String type, String id);
}
