package dev.stemcraft.service.resourcepack.generators;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.json.JsonFile;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.exception.ResourcePackGeneratorException;

import java.io.File;

/**
 * Generates the pack.mcmeta file for a Minecraft resource pack.
 */
public class PackMetaGenerator extends ResourcePackGenerator {
    private static final int DEFAULT_PACK_FORMAT = 64;

    /**
     * Constructs a PackMetaGenerator with the given STEMCraftAPI instance.
     *
     * @param api The STEMCraftAPI instance.
     */
    public PackMetaGenerator(STEMCraftAPI api, ResourcePackService service) {
        super(api, service);
    }

    /**
     * Generates the pack.mcmeta file for the resource pack.
     *
     * @param manifest       The manifest configuration section (not used in this generator).
     * @param resourcePackDir  The resource pack directory where pack.mcmeta will be created.
     */
    @Override
    public void buildStart(ConfigSection manifest, File resourcePackDir) {
        ConfigSectionView config = service.getConfig();

        JsonFile json = new JsonFile(resourcePackDir, "pack.mcmeta");
        json.root().putObject("pack")
                .put("pack_format", config.getInt("pack_format", DEFAULT_PACK_FORMAT))
                .put("description", config.getString("description", "A STEMCraft Resource Pack"));
        try {
            json.save();
        } catch (Exception e) {
            throw new ResourcePackGeneratorException("Failed to write pack.mcmeta", e);
        }
    }
}
