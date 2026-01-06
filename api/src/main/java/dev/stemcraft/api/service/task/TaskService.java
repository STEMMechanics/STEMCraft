/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.api.service.task;

import java.util.List;

/**
 * Service for scheduling and managing tasks.
 */
public interface TaskService {

    enum RetryResult {
        SUCCESS, // The task completed successfully
        EXHAUSTED // All retry attempts were exhausted without success
    }

    /**
     * Register a persistent callback for a specific type.
     *
     * @param persistentType The type of persistent task.
     * @param callback       The callback to be invoked.
     */
    void registerPersistentCallback(String persistentType, TaskCallback callback);

    /**
     * Schedule a persistent task to run after a delay.
     *
     * @param persistentType The type of persistent task.
     * @param id The identifier for the task.
     * @param data Additional data associated with the task.
     * @param delay The delay in ticks before execution.
     */
    void runLaterPersistent(String persistentType, String id, String data, long delay);

    /**
     * Get or set persistent data associated with a specific ID.
     *
     * @param id The identifier for the persistent data.
     * @return The persistent data associated with the ID.
     */
    String getPersistentData(String id);

    /**
     * Set persistent data associated with a specific ID.
     *
     * @param id The identifier for the persistent data.
     * @param data The persistent data to be set.
     */
    void setPersistentData(String id, String data);

    /**
     * List all persistent timers of a specific type.
     *
     * @param type The type of persistent timers.
     * @return A list of persistent timer IDs.
     */
    List<String> listPersistentTimers(String type);

    /**
     * Schedule task to run after a delay.
     *
     * @param delay The delay in ticks before execution.
     * @param task  The task to be executed.
     */
    void runLater(long delay, Runnable task);

    /**
     * Schedule a task to run on the next tick.
     *
     * @param task The task to be executed.
     */
    default void nextTick(Runnable task) {
        runLater(1L, task);
    }

    /**
     * Run an asynchronous task immediately
     *
     * @param task The task to be executed.
     */
    void runAsync(Runnable task);

    /**
     * Run a synchronous task immediately.
     *
     * @param task The task to be executed.
     */
    void runSync(Runnable task);

    /**
     * Schedule a task to run once after a delay with a specific ID.
     * Repeated calls with the same ID will cancel and overwrite the previous task.
     *
     * @param id The unique identifier for the task.
     * @param delay The delay in ticks before execution.
     * @param task The task to be executed.
     */
    void runOnceDelay(String id, long delay, Runnable task);

    /**
     * Cancel a scheduled task by its ID.
     *
     * @param id The unique identifier of the task to cancel.
     */
    void cancel(String id);

    /**
     * Check if a scheduled task exists by its ID.
     *
     * @param id The unique identifier of the task.
     * @return True if the task exists, false otherwise.
     */
    boolean exists(String id);

    /**
     * Get the remaining time in ticks for a scheduled task by its ID.
     *
     * @param id The unique identifier of the task.
     * @return The remaining time in ticks, or -1 if the task does not exist
     */
    long remaining(String id);

    /**
     * Schedule a repeating task with a fixed period.
     *
     * @param id The unique identifier for the task.
     * @param delay The initial delay in ticks before the first execution.
     * @param period The period in ticks between successive executions.
     * @param task The task to be executed.
     */
    void repeating(String id, long delay, long period, Runnable task);
    default void repeating(String id, long period, Runnable task) { repeating(id, period, period, task); }
    default void repeating(long period, Runnable task) { repeating("", period, period, task); }

    /**
     * Retry a task up to a maximum number of retries with a delay between attempts.
     *
     * @param maxRetries   The maximum number of retry attempts.
     * @param task         The task to be retried.
     * @param callback     The callback to be invoked with the result.
     * @param intervalDelay The delay in ticks between retry attempts.
     * @param startDelay   The initial delay in ticks before the first attempt.
     */
    void retry(int maxRetries, TaskRetryable task, TaskRetryCallback callback, long intervalDelay, long startDelay);
    default void retry(int maxRetries, TaskRetryable task, TaskRetryCallback callback) { retry(5, task, callback, 1L, 1L); }
    default void retry(TaskRetryable task, TaskRetryCallback callback) { retry(5, task, callback, 1L, 1L); }
}
