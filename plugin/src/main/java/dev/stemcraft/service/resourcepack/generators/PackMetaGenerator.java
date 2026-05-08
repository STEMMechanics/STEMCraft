package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.generator.AbstractResourcePackGenerator;
import dev.stemcraft.service.resourcepack.ResourcePackServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Generates the pack.mcmeta file for a Minecraft resource pack.
 */
public class PackMetaGenerator extends AbstractResourcePackGenerator {
    /**
     * Resource-pack metadata format introduced top-level {@code min_format}
     * and {@code max_format} fields in pack metadata and overlay metadata
     * starting at pack format 65.
     */
    private static final int MIN_MAX_PACK_METADATA_FORMAT = 65;

    /**
     * Legacy metadata uses {@code pack_format}/{@code supported_formats} up to
     * the format immediately before {@link #MIN_MAX_PACK_METADATA_FORMAT}.
     */
    private static final int LEGACY_PACK_METADATA_MAX_FORMAT = MIN_MAX_PACK_METADATA_FORMAT - 1;

    private final ResourcePackServiceImpl service;

    public PackMetaGenerator(@NotNull ResourcePackServiceImpl service) {
        super("pack-meta");
        this.service = service;
    }

    @Override
    public void generate(@NotNull ResourcePackBuildContext context) throws IOException {
        if (context.writer().overlay()) {
            return;
        }

        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        addPackVersionToMetadata(context.writer().supportedRange(), pack);
        pack.addProperty(
            "description",
            service.getConfig().getString("description", "A STEMCraft Resource Pack")
        );
        root.add("pack", pack);
        addOverlays(root);
        context.writer().writeString(
            "pack.mcmeta",
            new GsonBuilder().setPrettyPrinting().create().toJson(root)
        );
    }

    private void addPackVersionToMetadata(@NotNull PackFormatRange supportedRange, @NotNull JsonObject packJson) {
        int minPackFormat = supportedRange.minFormat();
        int maxPackFormat = supportedRange.maxFormat();
        if (maxPackFormat >= MIN_MAX_PACK_METADATA_FORMAT) {
            packJson.addProperty("min_format", minPackFormat);
            packJson.addProperty("max_format", maxPackFormat);

            if (minPackFormat < MIN_MAX_PACK_METADATA_FORMAT) {
                addLegacyPackVersionToMetadata(packJson, minPackFormat, LEGACY_PACK_METADATA_MAX_FORMAT);
            }
        } else {
            addLegacyPackVersionToMetadata(packJson, minPackFormat, maxPackFormat);
        }
    }

    private void addLegacyPackVersionToMetadata(@NotNull JsonObject packJson,
                                                int minPackFormat,
                                                int maxPackFormat) {
        packJson.addProperty("pack_format", maxPackFormat);

        JsonArray supportedFormats = new JsonArray();
        supportedFormats.add(minPackFormat);
        supportedFormats.add(maxPackFormat);
        packJson.add("supported_formats", supportedFormats);
    }

    private void addOverlays(@NotNull JsonObject root) {
        JsonArray overlayEntries = new JsonArray();

        for (ResourcePackServiceImpl.OverlayBuildPlanEntry overlay : service.overlayBuildPlan()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("directory", overlay.directory());
            addOverlayVersionMetadata(overlay.supportedRange(), entry);
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

        if (maxPackFormat >= MIN_MAX_PACK_METADATA_FORMAT) {
            overlayJson.addProperty("min_format", minPackFormat);
            overlayJson.addProperty("max_format", maxPackFormat);

            if (minPackFormat < MIN_MAX_PACK_METADATA_FORMAT) {
                addLegacyOverlayFormats(overlayJson, minPackFormat, LEGACY_PACK_METADATA_MAX_FORMAT);
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
