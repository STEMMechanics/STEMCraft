package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.exception.ResourcePackGeneratorException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
     * @param manifest The manifest configuration section (not used in this generator).
     * @param resourcePackDir The resource pack directory where pack.mcmeta will be created.
     */
    @Override
    public void buildStart(ConfigSection manifest, File resourcePackDir) {
        ConfigSectionView config = service.getConfig();

        try {
            JsonObject root = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", config.getInt("pack_format", DEFAULT_PACK_FORMAT));
            pack.addProperty("description", config.getString("description", "A STEMCraft Resource Pack"));
            root.add("pack", pack);

            Path out = new File(resourcePackDir, "pack.mcmeta").toPath();
            Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            throw new ResourcePackGeneratorException("Failed to write pack.mcmeta", e);
        }
    }
}
