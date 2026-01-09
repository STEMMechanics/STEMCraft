package dev.stemcraft.service.resourcepack.generators;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.json.JsonFile;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.api.util.FileUtil;
import dev.stemcraft.exception.ResourcePackGeneratorException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Generates glyphs for custom fonts in the resource pack based on data pack configuration.
 */
public class GlyphGenerator extends ResourcePackGenerator {

    /**
     * Constructs a GlyphGenerator with the given STEMCraftAPI and ResourcePackService.
     *
     * @param api The STEMCraftAPI instance.
     * @param service The ResourcePackService instance.
     */
    public GlyphGenerator(STEMCraftAPI api, ResourcePackService service) {
        super(api, service);
    }

    /**
     * Generates glyphs defined in the data pack configuration into the resource pack.
     *
     * @param namespace The namespace to use for generated assets.
     * @param config The configuration section for generator settings.
     * @param dataPackDir The output directory for data pack files.
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The output directory for resource pack files.
     */
    @Override
    public void buildFromDataPackConfig(String namespace, ConfigSection config, File dataPackDir, ConfigSection manifest, File resourcePackDir) {
        if (dataPackDir == null || resourcePackDir == null || namespace == null || config == null) {
            return;
        }

        ConfigSection glyphsSection = config.getSection("glyphs");
        if (glyphsSection == null) {
            return;
        }

        // Destination JSON (append providers rather than overwrite)
        Path fontJsonPath = new File(resourcePackDir, "assets/minecraft/font/default.json").toPath();
        JsonFile json;
        try {
            json = new JsonFile(fontJsonPath).load();
        } catch (Exception e) {
            throw new ResourcePackGeneratorException("Failed to open font JSON " + fontJsonPath, e);
        }

        // Ensure namespace textures base exists
        File texturesBase = new File(resourcePackDir, "assets/" + namespace + "/textures");
        if (!texturesBase.exists()) {
            //noinspection ResultOfMethodCallIgnored
            texturesBase.mkdirs();
        }

        for (String glyphName : glyphsSection.getKeys()) {
            ConfigSection glyphConfig = glyphsSection.getSection(glyphName);
            if (glyphConfig == null) {
                continue;
            }

            String charString = glyphConfig.getString("char");
            if (charString.isEmpty()) {
                continue;
            }
            char glyphChar = charString.charAt(0);

            String filePath = glyphConfig.getString("file");
            if (filePath.isBlank()) {
                continue;
            }

            int ascent = glyphConfig.getInt("ascent", 8);
            int height = glyphConfig.getInt("height", 8);

            // Copy the image from the data folder into the generated pack, under assets/<namespace>/textures/<filePath>
            // Expected source layout: <dataPackDir>/resource-pack/<filePath>
            File src = new File(new File(dataPackDir, "resource-pack"), filePath);
            File dest = new File(texturesBase, filePath.replace("\\", "/"));

            try {
                if (!src.exists()) {
                    throw new IOException("Glyph image not found: " + src.getAbsolutePath());
                }

                FileUtil.copyFile(src.toPath(), dest.toPath());
            } catch (Exception e) {
                throw new ResourcePackGeneratorException("Failed to copy glyph image for " + glyphName, e);
            }

            // Append provider into the existing providers array
            json.appendMap("/providers", Map.of(
                    "type", "bitmap",
                    "file", namespace + ":" + filePath.replace("\\", "/"),
                    "ascent", ascent,
                    "height", height,
                    "chars", List.of(String.valueOf(glyphChar))
            ));

            manifest.set("tokens.accept", String.valueOf(glyphChar));
        }

        try {
            json.save();
        } catch (Exception e) {
            throw new ResourcePackGeneratorException("Failed to write font JSON " + fontJsonPath, e);
        }
    }

    /**
     * Applies token mappings from the manifest to the resource pack service.
     *
     * @param namespace The namespace for the resource pack.
     * @param manifest The manifest configuration section.
     */
    @Override
    public void apply(String namespace, ConfigSectionView manifest) {
        for(String key : manifest.getSection("tokens").getKeys()) {
            String value = manifest.getSection("tokens").getString(key);
            if(!value.isEmpty()) {
                api.messages().tokens().add(key, value);
            }
        }
    }
}

/*
 namespace: stemcraft
 glyphs:
    accept:
        char: 
       file: font/accept.png
       ascent: 8
       height: 8
     cancel:
       char: 
       file: font/cancel.png
       ascent: 8
       height: 8
 */