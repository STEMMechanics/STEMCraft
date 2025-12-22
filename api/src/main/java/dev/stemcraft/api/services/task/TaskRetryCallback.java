package dev.stemcraft.api.services.task;

@FunctionalInterface
public interface TaskRetryCallback {
    void done(TaskService.RetryResult result);
}
