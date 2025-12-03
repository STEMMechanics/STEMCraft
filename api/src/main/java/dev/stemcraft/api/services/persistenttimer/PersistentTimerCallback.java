package dev.stemcraft.api.services.persistenttimer;

@FunctionalInterface
public interface PersistentTimerCallback {
    void run(String type, String id, String data);
}
