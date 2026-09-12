package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.api.service.world.generation.GeneratorMapRenderer;
import net.pl3x.map.core.renderer.Renderer;
import net.pl3x.map.core.renderer.task.RegionScanTask;
import net.pl3x.map.core.util.Colors;
import net.pl3x.map.core.world.Chunk;
import net.pl3x.map.core.world.Region;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adapts generator map policies to Pl3xMap's saved-region render workers. */
public final class GeneratorRenderer extends Renderer {
    static final Map<String, GeneratorMapRenderer> POLICIES = new ConcurrentHashMap<>();
    private final GeneratorMapRenderer policy;

    /** Constructor signature required by Pl3xMap's renderer registry. */
    public GeneratorRenderer(RegionScanTask task, Builder builder) {
        super(task, builder);
        policy = POLICIES.get(task.getWorld().getName());
    }

    @Override public void scanBlock(Region region, Chunk chunk, Chunk.BlockData data, int x, int z) {
        if (policy == null) {
            getTileImage().setPixel(x, z, basicPixelColor(region, data, x, z));
            return;
        }
        var column = column(chunk, x, z);
        int y = policy.surfaceY(column);
        if (y < column.minY() || y >= column.maxY()) {
            getTileImage().setPixel(x, z, 0);
            return;
        }
        var state = chunk.getBlockState(x, y, z);
        var biome = region.getWorld().getBiomeManager().getBiome(region, x, y, z);
        int color = state.getBlock().isWater() ? Colors.getWaterColor(region, biome, x, z)
            : Colors.fixBlockColor(region, biome, state, x, z);
        getTileImage().setPixel(x, z, policy.color(column, y, Colors.setAlpha(255, color)));
    }

    static GeneratorMapRenderer.Column column(Chunk chunk, int x, int z) {
        return new GeneratorMapRenderer.Column() {
            @Override public int minY() { return chunk.getWorld().getMinBuildHeight(); }
            @Override public int maxY() { return chunk.getWorld().getMaxBuildHeight(); }
            @Override public boolean isAir(int y) { return chunk.getBlockState(x, y, z).getBlock().isAir(); }
            @Override public boolean isVisible(int y) {
                var block = chunk.getBlockState(x, y, z).getBlock();
                return block.isFluid() || block.color() > 0;
            }
        };
    }
}
