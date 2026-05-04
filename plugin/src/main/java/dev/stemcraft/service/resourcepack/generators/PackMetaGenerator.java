package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
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
    public void buildStart(ResourcePackBuildContext context, ConfigSection manifest, File resourcePackDir) {
        if (context.overlay()) {
            return;
        }

        ConfigSectionView config = service.getConfig();

        try {
            JsonObject root = new JsonObject();
            JsonObject pack = new JsonObject();
            addPackVersionToMetadata(service.supportedRange(), pack);
            pack.addProperty("description", config.getString("description", "A STEMCraft Resource Pack"));
            root.add("pack", pack);
            addOverlays(root);

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
     * @param supportedRange The Pack Format Range supported by the resource pack being generated.
     * @param packJson The Pack JSON Object to add the version information to.
     *
     * @author ProjectHSI
     */
    private void addPackVersionToMetadata(@NotNull final PackFormatRange supportedRange, @NotNull final JsonObject packJson) {
        final int minPackFormat = supportedRange.minFormat();
        final int maxPackFormat = supportedRange.maxFormat();
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

    private void addOverlays(@NotNull JsonObject root) {
        JsonArray overlayEntries = new JsonArray();

        for (ResourcePackBuildContext context : service.buildPlan()) {
            if (!context.overlay()) {
                continue;
            }

            JsonObject entry = new JsonObject();
            entry.addProperty("directory", context.overlayDirectory());
            addOverlayVersionMetadata(context.supportedRange(), entry);
            overlayEntries.add(entry);
        }

        if (overlayEntries.isEmpty()) {
            return;
        }

        JsonObject overlays = new JsonObject();
        overlays.add("entries", overlayEntries);
        root.add("overlays", overlays);
    }

    private void addOverlayVersionMetadata(@NotNull PackFormatRange supportedRange, @NotNull JsonObject overlayJson) {
        int minPackFormat = supportedRange.minFormat();
        int maxPackFormat = supportedRange.maxFormat();

        if (maxPackFormat >= 65) {
            overlayJson.addProperty("min_format", minPackFormat);
            overlayJson.addProperty("max_format", maxPackFormat);

            if (minPackFormat < 65) {
                addLegacyOverlayFormats(overlayJson, minPackFormat, 64);
            }
            return;
        }

        addLegacyOverlayFormats(overlayJson, minPackFormat, maxPackFormat);
    }

    private void addLegacyOverlayFormats(@NotNull JsonObject overlayJson,
                                         int minPackFormat,
                                         int maxPackFormat) {
        JsonArray legacyFormats = new JsonArray();
        legacyFormats.add(minPackFormat);
        legacyFormats.add(maxPackFormat);
        overlayJson.add("formats", legacyFormats);
    }
}
