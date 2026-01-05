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

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;

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
     */
    void rollback(boolean applyPhysics);

    /**
     * Captures the block.
     */
    default void captureBlock(Block block) { captureBlockState(block.getState()); }

    /**
     * Captures the current state of a block.
     */
    void captureBlockState(BlockState state);

    /**
     * Captures the current state of an entity.
     */
    void captureEntity(Entity entity);

    /**
     * Loads the session state from the database.
     */
    void load();

    /**
     * Saves the session state to the database.
     */
    void save();
}
