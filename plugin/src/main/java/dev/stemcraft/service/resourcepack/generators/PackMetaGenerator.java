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
        final int[] supportedVersions = service.supportedVersion();
        final int minPackFormat = supportedVersions.length > 0 ? supportedVersions[0] : 0;
        final int maxPackFormat = supportedVersions.length > 1 ? supportedVersions[1] : minPackFormat;
        if (maxPackFormat >= 65) {
            packJson.addProperty("min_format", minPackFormat);
            packJson.addProperty("max_format", maxPackFormat);

            if (minPackFormat < 65) {
                addLegacyPackVersionToMetadata(packJson, minPackFormat, 64);
            }
        } else {
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
        packJson.addProperty("pack_format", maxPackFormat);

        final JsonArray supportedFormats = new JsonArray();
        supportedFormats.add(minPackFormat);
        supportedFormats.add(maxPackFormat);
        packJson.add("supported_formats", supportedFormats);
    }
}
