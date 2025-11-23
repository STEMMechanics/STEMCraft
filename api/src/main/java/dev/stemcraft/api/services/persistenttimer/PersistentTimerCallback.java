package dev.stemcraft.api.services.persistenttimer;

@FunctionalInterface
public interface PersistentTimerCallback {
    void run(String id, String data);
}
