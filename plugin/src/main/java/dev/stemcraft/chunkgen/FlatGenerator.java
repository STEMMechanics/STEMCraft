package dev.stemcraft.chunkgen;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

// flat: parse "grass_block;dirt:3;bedrock"
public class FlatGenerator extends ChunkGenerator {
    private record Layer(Material mat, int height) {}
    private final List<Layer> layers;

    private FlatGenerator(List<Layer> layers) {
        this.layers = layers;
    }

    /** Parse config string like "grass_block;dirt:3;bedrock" */
    public static FlatGenerator fromOptions(String cfg) {
        if (cfg == null || cfg.isBlank()) {
            // default = bedrock, 3 dirt, grass
            return new FlatGenerator(List.of(
                    new Layer(Material.BEDROCK, 1),
                    new Layer(Material.DIRT, 3),
                    new Layer(Material.GRASS_BLOCK, 1)
            ));
        }

        List<Layer> parsed = new ArrayList<>();
        for (String token : cfg.split(";")) {
            if (token.isBlank()) continue;
            String[] parts = token.split(":", 2);
            String matStr = parts[0].trim().toUpperCase(Locale.ROOT);
            Material mat = Material.matchMaterial(matStr);
            if (mat == null) {
                throw new IllegalArgumentException("Unknown material: " + matStr);
            }
            int h = (parts.length == 2) ? Integer.parseInt(parts[1].trim()) : 1;
            parsed.add(new Layer(mat, Math.max(1, h)));
        }
        return new FlatGenerator(parsed);
    }

    @Override
    public void generateSurface(
            @NotNull WorldInfo info,
            @NotNull Random rnd,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData data
    ) {
        int y = 0;
        for (Layer layer : layers) {
            for (int yy = y; yy < y + layer.height; yy++) {
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        data.setBlock(bx, yy, bz, layer.mat);
                    }
                }
            }
            y += layer.height;
        }
    }
}