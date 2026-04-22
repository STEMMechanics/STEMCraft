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

package dev.stemcraft.chunkgen;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * Chunk generator that creates an ocean-style world with a flat seabed.
 * <p>
 * Configuration string format: "<waterLevel>[:<seabedY>]"
 * Example: "80" creates a water world with the surface at Y=80.
 * Example: "80:48" creates a water world with the water surface at Y=80 and the seabed at Y=48.
 */
public class WaterGenerator extends ChunkGenerator {
    private static final int DEFAULT_WATER_LEVEL = 63;
    private static final int DEFAULT_OCEAN_DEPTH = 32;

    private final int waterLevel;
    private final int seabedY;

    private WaterGenerator(int waterLevel, int seabedY) {
        this.waterLevel = Math.max(2, waterLevel);
        this.seabedY = Math.clamp(seabedY, 1, this.waterLevel - 1);
    }

    /**
     * Create a WaterGenerator from a configuration string.
     *
     * @param cfg configuration string.
     * @return WaterGenerator instance.
     */
    public static WaterGenerator fromOptions(String cfg) {
        if (cfg == null || cfg.isBlank()) {
            return new WaterGenerator(DEFAULT_WATER_LEVEL, DEFAULT_WATER_LEVEL - DEFAULT_OCEAN_DEPTH);
        }

        String[] parts = cfg.trim().split(":", 2);
        int waterLevel = Integer.parseInt(parts[0].trim());
        int seabedY = parts.length == 2
                ? Integer.parseInt(parts[1].trim())
                : waterLevel - DEFAULT_OCEAN_DEPTH;

        return new WaterGenerator(waterLevel, seabedY);
    }

    public static @NotNull List<String> tabCompleteOptions(String cfg) {
        if (cfg == null || cfg.isBlank()) {
            return List.of("63", "80", "63:31", "80:48");
        }

        if (!cfg.contains(":")) {
            try {
                int waterLevel = Integer.parseInt(cfg.trim());
                int defaultSeabed = Math.max(1, waterLevel - DEFAULT_OCEAN_DEPTH);
                return List.of(String.valueOf(waterLevel), waterLevel + ":" + defaultSeabed);
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }

        String[] parts = cfg.trim().split(":", 2);
        try {
            int waterLevel = Integer.parseInt(parts[0].trim());
            int defaultSeabed = Math.max(1, waterLevel - DEFAULT_OCEAN_DEPTH);
            return List.of(waterLevel + ":" + defaultSeabed);
        } catch (NumberFormatException ignored) {
            return List.of();
        }
    }

    /**
     * Generate an ocean-style chunk with a simple seabed and a fixed water surface.
     *
     * @param info world information.
     * @param rnd random number generator.
     * @param chunkX chunk X coordinate.
     * @param chunkZ chunk Z coordinate.
     * @param data chunk data to modify.
     */
    @Override
    public void generateSurface(
            @NotNull WorldInfo info,
            @NotNull Random rnd,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData data
    ) {
        int dirtStartY = Math.max(1, seabedY - 3);

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                data.setBlock(bx, 0, bz, Material.BEDROCK);

                for (int y = 1; y < dirtStartY; y++) {
                    data.setBlock(bx, y, bz, Material.STONE);
                }
                for (int y = dirtStartY; y < seabedY; y++) {
                    data.setBlock(bx, y, bz, Material.DIRT);
                }

                data.setBlock(bx, seabedY, bz, Material.SAND);

                for (int y = seabedY + 1; y <= waterLevel; y++) {
                    data.setBlock(bx, y, bz, Material.WATER);
                }
            }
        }
    }
}
