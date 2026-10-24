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
        double minPackFormat = supportedRange.minFormat();
        double maxPackFormat = supportedRange.maxFormat();
        if (maxPackFormat >= MIN_MAX_PACK_METADATA_FORMAT) {
            addFormat(packJson, "min_format", minPackFormat);
            addFormat(packJson, "max_format", maxPackFormat);

            if (minPackFormat < MIN_MAX_PACK_METADATA_FORMAT) {
                addLegacyPackVersionToMetadata(packJson, minPackFormat, LEGACY_PACK_METADATA_MAX_FORMAT);
            }
        } else {
            addLegacyPackVersionToMetadata(packJson, minPackFormat, maxPackFormat);
        }
    }

    private void addLegacyPackVersionToMetadata(@NotNull JsonObject packJson,
                                                double minPackFormat,
                                                double maxPackFormat) {
        addFormat(packJson, "pack_format", maxPackFormat);

        JsonArray supportedFormats = new JsonArray();
        addFormat(supportedFormats, minPackFormat);
        addFormat(supportedFormats, maxPackFormat);
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
        double minPackFormat = supportedRange.minFormat();
        double maxPackFormat = supportedRange.maxFormat();

        if (maxPackFormat >= MIN_MAX_PACK_METADATA_FORMAT) {
            addFormat(overlayJson, "min_format", minPackFormat);
            addFormat(overlayJson, "max_format", maxPackFormat);

            if (minPackFormat < MIN_MAX_PACK_METADATA_FORMAT) {
                addLegacyOverlayFormats(overlayJson, minPackFormat, LEGACY_PACK_METADATA_MAX_FORMAT);
            }
            return;
        }

        addLegacyOverlayFormats(overlayJson, minPackFormat, maxPackFormat);
    }

    private void addLegacyOverlayFormats(@NotNull JsonObject overlayJson,
                                         double minPackFormat,
                                         double maxPackFormat) {
        JsonArray legacyFormats = new JsonArray();
        addFormat(legacyFormats, minPackFormat);
        addFormat(legacyFormats, maxPackFormat);
        overlayJson.add("formats", legacyFormats);
    }

    private static void addFormat(@NotNull JsonObject object, @NotNull String name, double format) {
        if (format == Math.rint(format) && format <= Long.MAX_VALUE) {
            object.addProperty(name, (long) format);
        } else {
            object.addProperty(name, format);
        }
    }

    private static void addFormat(@NotNull JsonArray array, double format) {
        if (format == Math.rint(format) && format <= Long.MAX_VALUE) {
            array.add((long) format);
        } else {
            array.add(format);
        }
    }
}
