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

public interface TaskService {
    enum RetryResult {
        SUCCESS,
        EXHAUSTED
    }

    /**
     * Register a persistent callback for a specific type.
     */
    void registerPersistentCallback(String persistentType, TaskCallback callback);

    /**
     * Schedule a persistent task to run after a delay.
     */
    void runLaterPersistent(String persistentType, String id, String data, long delay);

    /**
     * Get or set persistent data associated with a specific ID.
     */
    String getPersistentData(String id);

    /**
     * Set persistent data associated with a specific ID.
     */
    void setPersistentData(String id, String data);

    /**
     * List all persistent timers of a specific type.
     */
    List<String> listPersistentTimers(String type);

    /**
     * Schedule tasks.
     */
    void runLater(long delay, Runnable task);

    /**
     * Schedule a task to run on the next tick.
     */
    default void nextTick(Runnable task) {
        runLater(1L, task);
    }

    /**
     * Run an asynchronous task immediately.
     */
    void runAsync(Runnable task);

    /**
     * Run a synchronous task immediately.
     */
    void runSync(Runnable task);

    /**
     * Schedule a task to run once after a delay with a specific ID.
     * Repeated calls with the same ID will cancel and overwrite the previous task.
     */
    void runOnceDelay(String id, long delay, Runnable task);

    /**
     * Cancel a scheduled task by its ID.
     */
    void cancel(String id);

    /**
     * Check if a scheduled task exists by its ID.
     */
    boolean exists(String id);

    /**
     * Get the remaining time in ticks for a scheduled task by its ID.
     */
    long remaining(String id);

    /**
     * Schedule a repeating task with a fixed period.
     */
    void repeating(String id, long delay, long period, Runnable task);
    default void repeating(String id, long period, Runnable task) { repeating(id, period, period, task); }
    default void repeating(long period, Runnable task) { repeating("", period, period, task); }

    /**
     * Retry a task up to a maximum number of retries with a delay between attempts.
     */
    void retry(int maxRetries, TaskRetryable task, TaskRetryCallback callback, long intervalDelay, long startDelay);
    default void retry(int maxRetries, TaskRetryable task, TaskRetryCallback callback) { retry(5, task, callback, 1L, 1L); }
    default void retry(TaskRetryable task, TaskRetryCallback callback) { retry(5, task, callback, 1L, 1L); }
}
