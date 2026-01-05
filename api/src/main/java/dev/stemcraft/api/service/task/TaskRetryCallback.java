package dev.stemcraft.api.service.task;

@FunctionalInterface
public interface TaskRetryCallback {
    void done(TaskService.RetryResult result);
}
