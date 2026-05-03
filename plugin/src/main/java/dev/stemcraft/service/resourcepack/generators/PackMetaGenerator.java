package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.exception.ResourcePackGeneratorException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the pack.mcmeta file for a Minecraft resource pack.
 */
public class PackMetaGenerator extends ResourcePackGenerator {
    private static final int DEFAULT_MIN_PACK_VERSION = 64;
    private static final int DEFAULT_MAX_PACK_VERSION = 84;

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
            addPackVersionToMetadata(config, pack);
            pack.addProperty("description", config.getString("description", "A STEMCraft Resource Pack"));
            root.add("pack", pack);

            Path out = new File(resourcePackDir, "pack.mcmeta").toPath();
            Path parent = out.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            throw new ResourcePackGeneratorException("Failed to write pack.mcmeta", e);
        }
    }

    /**
     * <p>Adds Minecraft Pack Version information to a JSON Object.</p>
     *
     * @param config The configuration file being used to generate the resource pack.
     * @param packJson The Pack JSON Object to add the version information to.
     *
     * @author ProjectHSI
     */
    private void addPackVersionToMetadata(@NotNull final ConfigSectionView config, @NotNull final JsonObject packJson) {
        final int minPackFormat = config.getInt("min_pack_format", DEFAULT_MIN_PACK_VERSION);
        final int maxPackFormat = config.getInt("max_pack_format", DEFAULT_MAX_PACK_VERSION);
        packJson.addProperty("min_format", minPackFormat);
        packJson.addProperty("max_format", maxPackFormat);

        if (minPackFormat < 65) {
            addLegacyPackVersionToMetadata(packJson, minPackFormat, maxPackFormat);
        }
    }

    /**
     * <p>Adds Minecraft Pack Version information to a JSON Object.</p>
     *
     * @param packJson The Pack JSON Object to add the version information to.
     * @param minPackFormat The minimum pack format of the resource pack being generated.
     * @param maxPackFormat The maximum pack format of the resource pack being generated.
     *
     * @author ProjectHSI
     */
    private void addLegacyPackVersionToMetadata(@NotNull final JsonObject packJson,
                                                final int minPackFormat, final int maxPackFormat) {
        packJson.addProperty("pack_format", minPackFormat);

        final JsonArray supportedFormats = new JsonArray();
        supportedFormats.add(minPackFormat);
        supportedFormats.add(maxPackFormat);
        packJson.add("supported_formats", supportedFormats);
    }
}
