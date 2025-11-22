/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.api.services;

import dev.stemcraft.api.factories.ChunkGeneratorFactory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.generator.ChunkGenerator;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface WorldService extends STEMCraftService {
    boolean worldExists(String worldName);

    boolean isWorldLoaded(String worldName);

    World loadWorld(String worldName);

    boolean unloadWorld(String worldName, boolean save);

    World createWorld(String worldName);

    World createWorld(String worldName, ChunkGenerator generator);

    World createWorld(String worldName, String generatorName, String generatorOptions);

    void deleteWorld(String worldName) throws Exception;

    void renameWorld(String oldName, String newName) throws Exception;

    void duplicateWorld(String sourceWorldName, String targetWorldName) throws Exception;

    List<String> listWorlds();

    Path getWorldFolder(String worldName);

    void registerGenerator(String name, ChunkGeneratorFactory factory);

    Optional<ChunkGeneratorFactory> findGenerator(String name);

    /**
     * Returns true if block/entity changes in this world are currently being captured.
     */
    boolean isCapturing(World world);

    /**
     * Begin capturing all changes in the given world.
     */
    void captureStart(World world);

    /**
     * Stop capturing and discard all captured changes.
     */
    void captureStop(World world);

    /**
     * Roll back all captured changes in the given world and stop capturing.
     */
    void captureRollback(World world);

    /**
     * Clear all captured changes without stopping capture.
     */
    void captureReset(World world);

    /**
     * Capture the current state of this block for later rollback.
     */
    void capture(BlockState state);

    /**
     * Capture the entity for later rollback.
     */
    void capture(Entity entity);

    default void capture(Block block) { capture(block.getState()); }
}