package dev.stemcraft.service.resourcepack.generators;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.api.util.FileUtil;
import dev.stemcraft.exception.ResourcePackGeneratorException;

import java.io.File;

/**
 * Copies the Minecraft resource pack files.
 */
public class MinecraftPackGenerator extends ResourcePackGenerator {

    /**
     * Creates a new MinecraftPackGenerator.
     *
     * @param api The STEMCraft API instance.
     * @param service The ResourcePackService instance.
     */
    public MinecraftPackGenerator(STEMCraftAPI api, ResourcePackService service) {
        super(api, service);
    }

    /**
     * Builds the resource pack by copying Minecraft resource pack files from the data pack directory.
     *
     * @param dataPackDir The data pack directory.
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The resource pack directory to build into.
     */
    @Override
    public void buildFromDataPack(File dataPackDir, ConfigSection manifest, File resourcePackDir) {
        File file = new File(dataPackDir, "minecraft");
        if (file.exists() && file.isDirectory()) {
            try {
                FileUtil.copyDirectory(file.toPath(), resourcePackDir.toPath(), true);
            } catch (Exception e) {
                throw new ResourcePackGeneratorException("Failed to copy Minecraft resource pack files from data pack", e);
            }
        }
    }
}