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

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.service.world.generation.*;
import dev.stemcraft.chunkgen.generator.*;
import dev.stemcraft.chunkgen.terrain.TerrainModel;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import java.util.function.Function;

/** Built-in composition root. Adding a model requires no changes to the world service. */
public final class BuiltInGenerators {
    private BuiltInGenerators() {}
    public static void register(Plugin owner, STEMCraftAPI api) {
        ConfigFile config = api.config().load("config.yml");
        register(owner, api, config, "wasteland", "Wasteland", "Eroded plains, dry rivers and regional impact craters.",
                GeneratorCategory.HOSTILE, WastelandGenerator::new);
        register(owner, api, config, "skylands", "Skylands", "Floating islands and continents across three vertical bands.",
                GeneratorCategory.FLOATING, SkylandsGenerator::new);
        register(owner, api, config, "deep", "The Deep", "Enormous connected caverns, pillars and discrete underground lakes.",
                GeneratorCategory.CAVE, DeepGenerator::new, new dev.stemcraft.chunkgen.feature.CavernLightFeature());
        register(owner, api, config, "faraway", "Faraway Lands", "Distorted walls, cavities, spires and folded terrain.",
                GeneratorCategory.EXPERIMENTAL, FarawayLandsGenerator::new);
    }
    private static void register(Plugin owner, STEMCraftAPI api, ConfigFile config, String id, String name,
                                 String description, GeneratorCategory category,
                                 Function<GenerationContext, TerrainModel> factory,
                                 dev.stemcraft.chunkgen.feature.GenerationFeature... details) {
        if (config != null && !config.getBoolean("world-generation." + id + ".enabled", true)) return;
        GeneratorDefinition definition = new GeneratorDefinition(new NamespacedKey("stemcraft", id), name,
                description, category, true, 1);
        api.worlds().generator().registerGenerator(owner, definition, options -> {
            if (!options.isBlank()) throw new IllegalArgumentException("Generator " + id + " accepts no terrain options");
            return new StemChunkGenerator(definition, factory, java.util.List.of(details));
        });
    }
}
