package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.api.util.FileUtil;
import dev.stemcraft.exception.ResourcePackGeneratorException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

        Set<Integer> reservedCodePoints = collectReservedCodePoints(manifest);
        Set<String> reservedFilePaths = new HashSet<>();
        ConfigSection glyphsSection = getGlyphSection(config);
        if (glyphsSection != null && !glyphsSection.getKeys().isEmpty()) {
            // Destination JSON (append providers rather than overwrite)
            Path fontJsonPath = new File(resourcePackDir, "assets/minecraft/font/default.json").toPath();
            JsonObject json = loadOrCreateDefaultFont(fontJsonPath);
            JsonArray providers = ensureProvidersArray(json);

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
                int glyphCodePoint = parseCodePoint(charString, -1);
                if (glyphCodePoint < 0 || glyphCodePoint > 0x10FFFF) {
                    continue;
                }
                reservedCodePoints.add(glyphCodePoint);
                String glyphChars = new String(Character.toChars(glyphCodePoint));

                String filePath = glyphConfig.getString("file");
                if (filePath.isBlank()) {
                    continue;
                }
                reservedFilePaths.add(filePath.replace("\\", "/").toLowerCase(Locale.ROOT));

                int ascent = glyphConfig.getInt("ascent", 8);
                int height = glyphConfig.getInt("height", 8);

                // Expected source layout for namespace dirs is <namespaceDir>/textures/<filePath>.
                File src = new File(new File(dataPackDir, "textures"), filePath);
                File dest = new File(texturesBase, filePath.replace("\\", "/"));

                try {
                    if (!src.exists()) {
                        continue;
                    }

                    FileUtil.copyFile(src.toPath(), dest.toPath());
                } catch (Exception e) {
                    throw new ResourcePackGeneratorException("Failed to copy glyph image for " + glyphName, e);
                }

                JsonObject provider = new JsonObject();
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", namespace + ":" + filePath.replace("\\", "/"));
                provider.addProperty("ascent", ascent);
                provider.addProperty("height", height);
                JsonArray chars = new JsonArray();
                chars.add(glyphChars);
                provider.add("chars", chars);
                providers.add(provider);

                String tokenPrefix = config.getString("name_prefix");
                String token = tokenPrefix + glyphName;
                writeTokenMeta(manifest, token, namespace, filePath.replace("\\", "/"), ascent, height, glyphConfig);
                manifest.set("tokens." + token, glyphChars);
            }

            try {
                Files.createDirectories(fontJsonPath.getParent());
                Files.writeString(fontJsonPath, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            } catch (Exception e) {
                throw new ResourcePackGeneratorException("Failed to write font JSON " + fontJsonPath, e);
            }
        }

        processGeneratedFontImages(namespace, config, dataPackDir, manifest, resourcePackDir, reservedCodePoints, reservedFilePaths);
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

    private JsonObject loadOrCreateDefaultFont(Path fontJsonPath) {
        try {
            if (!Files.exists(fontJsonPath)) {
                return new JsonObject();
            }

            String raw = Files.readString(fontJsonPath);
            if (raw.isBlank()) {
                return new JsonObject();
            }

            if (!JsonParser.parseString(raw).isJsonObject()) {
                return new JsonObject();
            }

            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            throw new ResourcePackGeneratorException("Failed to open font JSON " + fontJsonPath, e);
        }
    }

    private JsonArray ensureProvidersArray(JsonObject root) {
        if (!root.has("providers") || !root.get("providers").isJsonArray()) {
            JsonArray providers = new JsonArray();
            root.add("providers", providers);
            return providers;
        }
        return root.getAsJsonArray("providers");
    }

    private ConfigSection getGlyphSection(ConfigSection config) {
        if (config.contains("glyphs")) {
            return config.getSection("glyphs");
        }

        if (config.contains("font_images")) {
            return config.getSection("font_images");
        }

        return null;
    }

    private void processGeneratedFontImages(
        String namespace,
        ConfigSection config,
        File namespaceDir,
        ConfigSection manifest,
        File resourcePackDir,
        Set<Integer> reservedCodePoints,
        Set<String> reservedFilePaths
    ) {
        if (!config.contains("generate_font_images")) {
            return;
        }

        ConfigSection section = config.getSection("generate_font_images");
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        String texturesPath = section.getString("textures_path", "");
        String namePrefix = section.getString("name_prefix");
        String startCharValue = section.getString("starting_char", "\uE200");
        int ascent = section.getInt("ascent", 8);
        int height = section.getInt("height", 8);
        int nextCodePoint = parseCodePoint(startCharValue, 0xE200);

        File texturesRoot = new File(namespaceDir, "textures");
        // Allow packs that place assets directly under the namespace root.
        if (!texturesRoot.exists() || !texturesRoot.isDirectory()) {
            texturesRoot = namespaceDir;
        }

        File texturesDir = texturesPath.isBlank() ? texturesRoot : new File(texturesRoot, texturesPath);
        if (!texturesDir.exists() || !texturesDir.isDirectory()) {
            return;
        }

        List<File> pngFiles = collectPngFiles(texturesDir);
        if (pngFiles.isEmpty()) {
            return;
        }
        pngFiles.sort((left, right) -> left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath()));

        Path fontJsonPath = new File(resourcePackDir, "assets/minecraft/font/default.json").toPath();
        JsonObject json = loadOrCreateDefaultFont(fontJsonPath);
        JsonArray providers = ensureProvidersArray(json);

        for (File pngFile : pngFiles) {
            String relative = texturesRoot.toPath().relativize(pngFile.toPath()).toString().replace("\\", "/");
            if (reservedFilePaths.contains(relative.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String tokenName = deriveGeneratedTokenName(texturesDir, pngFile, namePrefix);

            while (reservedCodePoints.contains(nextCodePoint)) {
                nextCodePoint++;
            }

            JsonObject provider = new JsonObject();
            provider.addProperty("type", "bitmap");
            provider.addProperty("file", namespace + ":" + relative);
            provider.addProperty("ascent", ascent);
            provider.addProperty("height", height);
            JsonArray chars = new JsonArray();
            chars.add(new String(Character.toChars(nextCodePoint)));
            provider.add("chars", chars);
            providers.add(provider);

            writeTokenMeta(manifest, tokenName, namespace, relative, ascent, height, section);
            manifest.set("tokens." + tokenName, new String(Character.toChars(nextCodePoint)));
            reservedCodePoints.add(nextCodePoint);
            nextCodePoint++;
        }

        try {
            Files.createDirectories(fontJsonPath.getParent());
            Files.writeString(fontJsonPath, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (Exception e) {
            throw new ResourcePackGeneratorException("Failed to write generated font JSON " + fontJsonPath, e);
        }
    }

    private String deriveGeneratedTokenName(File tokenRoot, File pngFile, String namePrefix) {
        String relativeTokenPath = tokenRoot.toPath().relativize(pngFile.toPath()).toString().replace("\\", "/");
        if (relativeTokenPath.toLowerCase(Locale.ROOT).endsWith(".png")) {
            relativeTokenPath = relativeTokenPath.substring(0, relativeTokenPath.length() - 4);
        }

        relativeTokenPath = relativeTokenPath.replace(' ', '_').toLowerCase(Locale.ROOT);
        return (namePrefix + relativeTokenPath).toLowerCase(Locale.ROOT);
    }

    private List<File> collectPngFiles(File root) {
        List<File> files = new ArrayList<>();
        File[] children = root.listFiles();
        if (children == null) {
            return files;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(collectPngFiles(child));
                continue;
            }

            if (child.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                files.add(child);
            }
        }
        return files;
    }

    private void writeTokenMeta(
        ConfigSection manifest,
        String token,
        String namespace,
        String file,
        int ascent,
        int height,
        ConfigSectionView sourceConfig
    ) {
        String metaPath = "token-meta." + token;
        manifest.set(metaPath + ".namespace", namespace);
        manifest.set(metaPath + ".file", file);
        manifest.set(metaPath + ".ascent", ascent);
        manifest.set(metaPath + ".height", height);
        manifest.set(metaPath + ".bedrock.auto_scale", sourceConfig.getBoolean("bedrock.auto_scale", true));
        manifest.set(metaPath + ".bedrock.height", sourceConfig.getInt("bedrock.height", height));
        manifest.set(metaPath + ".bedrock.scale", sourceConfig.getDouble("bedrock.scale", 1.0d));
        manifest.set(metaPath + ".bedrock.y_offset", sourceConfig.getInt("bedrock.y_offset", 0));
    }

    private int parseCodePoint(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim();
        if (normalized.startsWith("\\u") && normalized.length() == 6) {
            try {
                return Integer.parseInt(normalized.substring(2), 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        if ((normalized.startsWith("u+") || normalized.startsWith("U+")) && normalized.length() > 2) {
            try {
                return Integer.parseInt(normalized.substring(2), 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        if (normalized.matches("^[0-9a-fA-F]{4,6}$")) {
            try {
                return Integer.parseInt(normalized, 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        return normalized.codePointAt(0);
    }

    private Set<Integer> collectReservedCodePoints(ConfigSection manifest) {
        Set<Integer> reserved = new HashSet<>();
        if (manifest == null) {
            return reserved;
        }

        ConfigSection tokens = manifest.getSection("tokens");
        if (tokens == null) {
            return reserved;
        }

        for (String key : tokens.getKeys()) {
            String value = tokens.getString(key, "");
            int codePoint = parseCodePoint(value, -1);
            if (codePoint >= 0) {
                reserved.add(codePoint);
            }
        }

        return reserved;
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
