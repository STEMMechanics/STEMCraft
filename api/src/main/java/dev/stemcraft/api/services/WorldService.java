package dev.stemcraft.api.services;

import dev.stemcraft.api.ChunkGeneratorFactory;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface WorldService extends STEMCraftService {
    boolean worldExists(String  worldName);
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
}
