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

package dev.stemcraft.api.service.world;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for managing a session that tracks changes in the world.
 */
public interface WorldChangeSession {

    /**
     * Starts recording changes in the world.
     */
    void start();

    /**
     * Stops recording changes in the world.
     */
    void stop();

    /**
     * Checks if the session is currently recording changes.
     */
    boolean isRecording();

    /**
     * Clears all recorded changes.
     */
    void clear();

    /**
     * Rolls back all recorded changes.
     *
     * @param applyPhysics Whether to apply physics during the rollback.
     */
    void rollback(boolean applyPhysics);
    default void rollback() { rollback(false); }

    /**
     * Captures the block.
     *
     * @param block The block to capture.
     * @param overwriteExisting Whether to overwrite existing captured state.
     */
    default void captureBlock(@NotNull Block block, boolean overwriteExisting) { captureBlockState(block.getState(), overwriteExisting); }
    default void captureBlock(@NotNull Block block) { captureBlock(block, false); }

    /**
     * Captures the current state of a block.
     *
     * @param state The block state to capture.
     * @param overwriteExisting Whether to overwrite existing captured state.
     */
    void captureBlockState(@NotNull BlockState state, boolean overwriteExisting);
    default void captureBlockState(@NotNull BlockState state) { captureBlockState(state, false); }

    /**
     * Captures the current state of an entity.
     *
     * @param entity The entity to capture.
     */
    void captureEntity(@NotNull Entity entity);

    /**
     * Loads the session state from the database.
     */
    void load();

    /**
     * Saves the session state to the database.
     */
    void save();
}
