package dev.stemcraft.api;

import org.bukkit.generator.ChunkGenerator;

@FunctionalInterface
public interface ChunkGeneratorFactory {
    ChunkGenerator create(String options);
}
