package dev.stemcraft.api.services.task;

@FunctionalInterface
public interface TaskRetryable {
    boolean run();
}
