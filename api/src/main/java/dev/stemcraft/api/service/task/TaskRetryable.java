package dev.stemcraft.api.service.task;

@FunctionalInterface
public interface TaskRetryable {
    boolean run();
}
