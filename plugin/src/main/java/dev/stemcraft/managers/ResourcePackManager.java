package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.utils.chatmenu.SCChatMenu;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackManager {
    private static final String CONFIG_FILE_NAME = "resource-pack.yml";
    private static final String CONFIG_DIR_NAME = "resource-pack";
    private static final int DEFAULT_PACK_FORMAT = 64;
    private static final String DEFAULT_PACK_URL = "/resource-pack.zip";
    private final STEMCraft plugin;

    @Getter
    private File packFolder;

    @Getter
    private File packZip;
    @Getter
    private String packUrl;
    private String packDescription;
    @Getter
    private String packNamespace = "stemcraft";
    @Getter
    private byte[] packHash;

    // near other fields like plugin, packZip, etc.
    private final File cacheDir;
    private final File cachePackDir;
    private final File cacheMetaFile;

    private final List<String> bindings = new ArrayList<>();


    public ResourcePackManager(STEMCraft plugin) {
        this.plugin = plugin;

        this.cacheDir = plugin.getCacheDir();
        this.cachePackDir = new File(cacheDir, "resource-pack");
        if (!this.cachePackDir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            this.cachePackDir.mkdirs();
        }
        this.cacheMetaFile = new File(this.cachePackDir, "cache.yml");
    }

    public void onEnable() {
        plugin.exportBundledDirectory("resource-pack");

        ensureDefaultConfig();
        loadConfig();

        packUrl = plugin.webService().getPublicUrl() + DEFAULT_PACK_URL;

        plugin.webService().registerEndpointHandler(DEFAULT_PACK_URL, (method, uri, queryParams) -> {
            if (!"GET".equalsIgnoreCase(method)) {
                return Map.of(
                        "responseCode", 405,
                        "body", "Method Not Allowed"
                );
            }

            if(!uri.equals(DEFAULT_PACK_URL)) {
                return Map.of(
                        "responseCode", 404,
                        "body", "File not found"
                );
            }

            if (packZip == null || !packZip.exists()) {
                return Map.of(
                        "responseCode", 503,
                        "body", "Resource pack not available"
                );
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("contentType", "application/zip");
            resp.put("file", packZip);
            return resp;
        });

        try {
            generatePack();
            plugin.info("Generated resource pack at URL " + packUrl);
            embedPack();
        } catch (IOException e) {
            plugin.error("Failed to generate resource pack", e);
        }

        plugin.registerEvent(PlayerJoinEvent.class, event -> {
            if (packZip != null && packZip.exists()) {
                sendPack(event.getPlayer());
            }
        }, EventPriority.MONITOR, false);

        plugin.registerCommand("resourcepack")
            .setAlias("respack")
            .setUsage("/resourcepack [send] [player] | /resourcepack zip")
            .setDescription("Manage the STEMCraft resource pack")
            .addTabCompletion("{player}")
            .addTabCompletion("send", "{player}")
                .addTabCompletion("sendall")
            .addTabCompletion("zip")
            .setPermission("stemcraft.command.resourcepack")
            .setExecutor((api, cmd, ctx) -> {
                String subCommand = ctx.getArgLower(1);
                Player target = null;

                if(Objects.equals(subCommand, "debug")) {
                    cmd.info("Resource Pack Debug Info:");
                    cmd.info(" - Pack URL: " + packUrl);
                    cmd.info(" - Pack Hash: " + (packHash != null ? Arrays.toString(packHash) : "null"));
                    cmd.info(" - Pack Zip: " + (packZip != null ? packZip.getAbsolutePath() : "null"));
                    return;
                }

                if(Objects.equals(subCommand, "bindings")) {
                    SCChatMenu.render(
                            ctx.getSender(),
                            "Resource Pack Bindings",
                            "resourcepack bindings",
                            ctx.getArgAsInt(3, 1),
                            bindings.size(),
                            (start, count, isPlayer) -> {
                                List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
                                bindings.forEach(key -> {
                                    // list key and the locale binding
                                    Component line = Component.text(key + " ", NamedTextColor.YELLOW)
                                            .append(Component.text(plugin.localeService().processBindings(key), NamedTextColor.WHITE))
                                            .hoverEvent(HoverEvent.showText(
                                                    Component.text("Click to copy", NamedTextColor.GRAY)
                                            ))
                                            .clickEvent(ClickEvent.copyToClipboard(":" + key + ":"));
                                });

                                return lines;
                            },
                            "No bindings present."
                    );

                    return;
                }

                if(!Objects.equals(subCommand, "send") && !Objects.equals(subCommand, "zip") && !Objects.equals(subCommand, "sendall") ) {
                    subCommand = "send";
                }

                if(subCommand.equals("send")) {
                    if(ctx.numArgs() > 1) {
                        ctx.checkArgIsPlayer(-1);
                        target = ctx.getArgAsPlayer(-1);
                    } else {
                        ctx.checkNotConsole();
                        target = ctx.getSenderAsPlayer();
                    }
                }

                if ("zip".equals(subCommand)) {
                    try {
                        generatePack(true);
                        embedPack();
                    } catch (IOException e) {
                        ctx.returnError("Failed to regenerate resource pack. See console for details.");
                    }

                    sendPackToAll();
                    ctx.returnInfo("Regenerated resource pack and sending to all players...");
                }

                if (packZip == null || !packZip.exists()) {
                    ctx.returnError("Resource pack is not currently available.");
                }

                if(target == null) {
                    sendPackToAll();
                    ctx.returnInfo("Sending resource pack to all online players...");
                } else {
                    sendPack(target);
                    ctx.returnInfo("Requesting resource pack download for " + target.getName() + "...");
                }
            })
            .register(plugin);
    }

    private void ensureDefaultConfig() {
        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);
        if (!cfgDir.exists() && !cfgDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create resource pack config directory: " + cfgDir);
        }

        File cfgFile = new File(cfgDir, CONFIG_FILE_NAME);
        if (!cfgFile.exists()) {
            // Expect a default at resource-pack/resource-pack.yml in the plugin JAR
            plugin.saveResource(CONFIG_DIR_NAME + "/" + CONFIG_FILE_NAME, false);
        }
    }

    private void loadConfig() {
        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);
        File parentCfg = new File(cfgDir, CONFIG_FILE_NAME);
        YamlConfiguration parent = YamlConfiguration.loadConfiguration(parentCfg);

        packDescription = parent.getString("pack.description", "STEMCraft Pack");
        packNamespace = parent.getString("info.namespace", "stemcraft");
    }

    private List<YamlConfiguration> loadAllPackConfigs(File cfgDir) {
        List<YamlConfiguration> result = new ArrayList<>();
        List<File> yamlFiles = new ArrayList<>();
        collectChildYamlFiles(cfgDir, yamlFiles);

        for (File f : yamlFiles) {
            result.add(YamlConfiguration.loadConfiguration(f));
        }
        return result;
    }

    private void collectChildYamlFiles(File dir, List<File> out) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (!child.isDirectory()) continue;

            File[] yamlFiles = child.listFiles((d, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml")
            );

            if (yamlFiles == null) continue;
            Collections.addAll(out, yamlFiles);
        }
    }

    private void collectYamlFiles(File dir, String filesEndingWith, List<File> out) {
        if (dir == null || !dir.exists()) return;

        File[] yamlFiles = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".yml")) return false;

            return lower.endsWith(filesEndingWith.toLowerCase(Locale.ROOT) + ".yml")
                    || lower.endsWith(filesEndingWith.toLowerCase(Locale.ROOT));
        });

        Collections.addAll(out, yamlFiles);
    }

    private void generatePack(boolean force) throws IOException {
        // Ensure packZip points at the expected zip location so we can reuse it if unchanged
        if (packZip == null) {
            packZip = new File(cachePackDir, "stemcraft-pack.zip");
        }

        long sourceMtime = computeSourceLastModified();
        long lastMtime = 0L;

        if (cacheMetaFile.exists()) {
            YamlConfiguration meta = YamlConfiguration.loadConfiguration(cacheMetaFile);
            lastMtime = meta.getLong("lastSourceModified", 0L);
        }

        // If nothing changed and we are not forcing, reuse the existing pack.
        if (!force && packZip != null && packZip.exists() && lastMtime == sourceMtime) {
            return;
        }

        plugin.info("Generating resource pack... This may take a moment.");
        packFolder = new File(cachePackDir, "generated-pack");
        if (packFolder.exists()) {
            deleteRecursive(packFolder);
        }
        if (!packFolder.mkdirs()) {
            throw new IOException("Failed to create pack folder: " + packFolder);
        }

        // Java edition pack
        generatePackMeta();
        generateFontConfigs();
        generateFontJson();
        mergePackContents();

        // Zip Java pack
        packZip = new File(cachePackDir, "stemcraft-pack.zip");
        if (packZip.exists() && !packZip.delete()) {
            throw new IOException("Failed to delete old pack zip: " + packZip);
        }
        zipFolder(packFolder, packZip);

        // Compute SHA1 hash for setResourcePack
        packHash = computeSha1(packZip);

        // Optionally generate Bedrock pack for Geyser (does not affect Java pack if it fails)
//        generateBedrockPackIfAvailable();

        // After successful generation, update metadata. The sourceMtime is our version.
        YamlConfiguration meta = new YamlConfiguration();
        meta.set("lastSourceModified", sourceMtime);
        try {
            meta.save(cacheMetaFile);
        } catch (IOException ex) {
            plugin.error("Failed to save resource-pack cache metadata", ex);
        }
    }

    private void generatePack() throws IOException {
        generatePack(false);
    }

    private void generateBedrockPackIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return;
        }
        try {
            generateBedrockPack();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to generate Bedrock resource pack: " + e.getMessage());
        }
    }

    private void generateBedrockPack() throws IOException {
        plugin.info("Generating Bedrock resource pack for Geyser...");

        File bedrockDir = new File(cachePackDir, "generated-bedrock-pack");
        if (bedrockDir.exists()) {
            deleteRecursive(bedrockDir);
        }
        if (!bedrockDir.mkdirs()) {
            throw new IOException("Failed to create Bedrock pack folder: " + bedrockDir);
        }

        // 1) manifest.json
        if (!writeBedrockManifest(bedrockDir)) {
            plugin.getLogger().warning("Bedrock manifest.json not found; skipping Bedrock pack generation.");
            return;
        }

        // 2) Build Bedrock bitmap font from font_images
        buildBedrockBitmapFont(bedrockDir);

        // 3) Zip into .mcpack
        File mcpack = new File(plugin.getDataFolder(), "stemcraft-bedrock.mcpack");
        if (mcpack.exists() && !mcpack.delete()) {
            throw new IOException("Failed to delete old Bedrock pack: " + mcpack);
        }
        zipFolder(bedrockDir, mcpack);

        // 4) Copy into Geyser packs folder
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File geyserPacksDir = new File(new File(pluginsDir, "Geyser-Spigot"), "packs");
        if (!geyserPacksDir.exists() && !geyserPacksDir.mkdirs()) {
            throw new IOException("Failed to create Geyser packs directory: " + geyserPacksDir);
        }

        Files.copy(
                mcpack.toPath(),
                new File(geyserPacksDir, mcpack.getName()).toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        plugin.info("Generated Bedrock resource pack for Geyser at " +
                new File(geyserPacksDir, mcpack.getName()).getPath());
    }

    /**
     * Write Bedrock manifest.json into the Bedrock pack directory.
     * If plugins/STEMCraft/resource-pack/manifest.json exists, it is copied.
     * Otherwise, returns false to indicate no manifest is available.
     */
    private boolean writeBedrockManifest(File bedrockDir) throws IOException {
        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);
        File manifestSource = new File(cfgDir, "manifest.json");
        if (!manifestSource.exists() || !manifestSource.isFile()) {
            return false;
        }

        File manifestTarget = new File(bedrockDir, "manifest.json");
        Files.copy(
                manifestSource.toPath(),
                manifestTarget.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
        return true;
    }

    private void buildBedrockBitmapFont(File bedrockDir) throws IOException {
        List<BedrockGlyph> glyphs = collectBedrockGlyphs();
        if (glyphs.isEmpty()) {
            plugin.getLogger().info("No font_images found for Bedrock pack; skipping Bedrock font generation.");
            return;
        }

        final int cellSize = 8;   // 8x8 cells
        final int cols = 16;      // 16 cells per row
        int rows = (int) Math.ceil(glyphs.size() / (double) cols);

        int sheetWidth = cols * cellSize;
        int sheetHeight = rows * cellSize;

        BufferedImage sheet = new BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setComposite(AlphaComposite.Src);

        // clear to transparent
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, sheetWidth, sheetHeight);

        for (int i = 0; i < glyphs.size(); i++) {
            BedrockGlyph glyph = glyphs.get(i);
            BufferedImage src = ImageIO.read(glyph.sourceFile);

            int col = i % cols;
            int row = i / cols;

            int x = col * cellSize;
            int y = row * cellSize;

            g.drawImage(src, x, y, cellSize, cellSize, null);
        }

        g.dispose();

        File fontDir = new File(bedrockDir, "font");
        if (!fontDir.mkdirs() && !fontDir.exists()) {
            throw new IOException("Failed to create Bedrock font dir: " + fontDir);
        }

        File sheetFile = new File(fontDir, "glyph_E1.png");
        ImageIO.write(sheet, "png", sheetFile);

        writeBedrockFontJson(fontDir, glyphs, cellSize);
    }

    private List<BedrockGlyph> collectBedrockGlyphs() {
        List<BedrockGlyph> result = new ArrayList<>();

        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);
        List<File> yamlFiles = new ArrayList<>();
        collectChildYamlFiles(cfgDir, yamlFiles);

        for (File yamlFile : yamlFiles) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(yamlFile);
            ConfigurationSection fontImages = cfg.getConfigurationSection("font_images");
            if (fontImages == null) continue;

            String cfgNamespace = cfg.getString("info.namespace", packNamespace);
            if (cfgNamespace == null || cfgNamespace.isEmpty()) {
                cfgNamespace = packNamespace;
            }

            File baseDir = yamlFile.getParentFile();

            for (String key : fontImages.getKeys(false)) {
                String charStr = fontImages.getString(key + ".char");
                String filePath = fontImages.getString(key + ".file");
                if (filePath == null || filePath.isEmpty()) {
                    filePath = fontImages.getString(key + ".from");
                }

                if (charStr == null || charStr.isEmpty() || filePath == null || filePath.isEmpty()) {
                    continue;
                }

                File src = resolveTexturePath(baseDir, cfgNamespace, filePath);
                if (src == null || !src.isFile()) {
                    plugin.getLogger().warning("Bedrock font: missing texture for " + key + " (" + filePath + ")");
                    continue;
                }

                BedrockGlyph g = new BedrockGlyph();
                g.key = key;
                g.charStr = charStr;
                g.sourceFile = src;
                result.add(g);
            }
        }

        // Stable ordering
        result.sort(Comparator.comparing(g -> g.key));
        return result;
    }

    private void writeBedrockFontJson(File fontDir, List<BedrockGlyph> glyphs, int cellSize) throws IOException {
        StringBuilder charsArray = new StringBuilder();
        charsArray.append("[\n");
        for (int i = 0; i < glyphs.size(); i++) {
            BedrockGlyph g = glyphs.get(i);
            String escaped = escapeJson(g.charStr);
            charsArray.append("    \"").append(escaped).append("\"");
            if (i + 1 < glyphs.size()) charsArray.append(",");
            charsArray.append("\n");
        }
        charsArray.append("  ]");

        String json = """
            {
              "glyph_E1": {
                "type": "bitmap",
                "file": "font/glyph_E1.png",
                "height": %d,
                "ascent": %d,
                "chars": %s
              }
            }
            """.formatted(
                cellSize,
                cellSize,
                charsArray.toString()
        );

        File jsonFile = new File(fontDir, "glyph_E1.json");
        try (FileWriter fw = new FileWriter(jsonFile, false)) {
            fw.write(json);
        }
    }

    private void generatePackMeta() throws IOException {
        File meta = new File(packFolder, "pack.mcmeta");
        String json = """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s"
                  }
                }
                """.formatted(DEFAULT_PACK_FORMAT, escapeJson(packDescription));
        try (FileWriter fw = new FileWriter(meta, false)) {
            fw.write(json);
        }
    }

    private void generateFontJson() throws IOException {
        // Ensure font directory exists
        File fontDir = new File(packFolder, "assets/minecraft/font");
        if (!fontDir.mkdirs() && !fontDir.exists()) {
            throw new IOException("Failed to create font dir: " + fontDir);
        }

        // Icon textures are placed per-config under assets/<namespace>/textures/<filePath>

        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);
        List<File> yamlFiles = new ArrayList<>();
        collectChildYamlFiles(cfgDir, yamlFiles);
        collectYamlFiles(cachePackDir, "-generated", yamlFiles); // include generated configs

        List<String> providers = new ArrayList<>();

        // Load font-image bindings from all YAMLs under resource-pack/
        for (File yamlFile : yamlFiles) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(yamlFile);
            ConfigurationSection fontImages = cfg.getConfigurationSection("font_images");
            if (fontImages == null) continue;

            // Namespace for this config: fall back to global packNamespace if not provided
            String cfgNamespace = cfg.getString("info.namespace", packNamespace);
            if (cfgNamespace == null || cfgNamespace.isEmpty()) {
                cfgNamespace = packNamespace;
            }

            // For generated configs stored in the cache, we may have an explicit origin base directory
            String originBaseDir = cfg.getString("generate_font_images.origin_base_dir");
            File baseDir = (originBaseDir != null && !originBaseDir.isEmpty())
                    ? new File(originBaseDir)
                    : yamlFile.getParentFile();

            // Directory where we will place textures for this config
            // (we will write into subpaths like textures/font/roles/stemcraft.png)
            File texturesDir = new File(packFolder, "assets/" + cfgNamespace + "/textures");
            if (!texturesDir.mkdirs() && !texturesDir.exists()) {
                throw new IOException("Failed to create textures dir: " + texturesDir);
            }

            for (String key : fontImages.getKeys(false)) {
                String charStr = fontImages.getString(key + ".char");
                // Allow either `file` or legacy `from` as the source path relative to the YAML file dir
                String filePath = fontImages.getString(key + ".file");
                if (filePath == null || filePath.isEmpty()) {
                    filePath = fontImages.getString(key + ".from");
                }
                int ascent = fontImages.getInt(key + ".ascent", 8);
                int height = fontImages.getInt(key + ".height", 8);

                if (charStr == null || charStr.isEmpty()) {
                    plugin.getLogger().warning("Glyph " + key + " has no 'char' defined in " + yamlFile.getName() + ", skipping");
                    continue;
                }
                if (filePath == null || filePath.isEmpty()) {
                    plugin.getLogger().warning("Glyph " + key + " has no 'file'/'from' path defined in " + yamlFile.getName() + ", skipping");
                    continue;
                }

                // Copy the local texture into our pack under assets/<namespace>/textures/<filePath>
                // NOTE: key may contain backslashes for locale binding names; do NOT use it as a filename.
                if (!copyLocalTexture(baseDir, cfgNamespace, filePath, filePath, texturesDir)) {
                    plugin.getLogger().warning("Glyph " + key + " could not copy local texture from '" + filePath + "' (base " + baseDir.getPath() + "), skipping");
                    continue;
                }

                // Provider points at the configured namespace texture (<namespace>:<filePath>)
                String providerJson = """
                    {
                      "type": "bitmap",
                      "file": "%s:%s",
                      "height": %d,
                      "ascent": %d,
                      "chars": ["%s"]
                    }
                    """.formatted(
                        escapeJson(cfgNamespace),
                        escapeJson(filePath.replace("\\", "/")),
                        height,
                        ascent,
                        escapeJson(charStr)
                );

                providers.add(providerJson.trim());
            }
        }

        String json;
        if (providers.isEmpty()) {
            // minimal valid json; no providers
            json = """
                {
                  "providers": []
                }
                """;
        } else {
            String joined = String.join(",\n        ", providers);
            json = """
                {
                  "providers": [
                    %s
                  ]
                }
                """.formatted(joined);
        }

        File fontFile = new File(fontDir, "default.json");
        try (FileWriter fw = new FileWriter(fontFile, false)) {
            fw.write(json);
        }
    }

    /**
     * Processes all resource-pack YAML files and, for any that contain a
     * `generate_font_images` section with `enabled: true`, generates a corresponding
     * `*-generated.yml` file in the same directory containing font image definitions
     * for each PNG in the configured icons directory.
     */
    private void generateFontConfigs() {
        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);
        List<File> yamlFiles = new ArrayList<>();
        collectChildYamlFiles(cfgDir, yamlFiles);

        for (File yamlFile : yamlFiles) {
            String name = yamlFile.getName();

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(yamlFile);
            ConfigurationSection gen = cfg.getConfigurationSection("generate_font_images");
            if (gen == null) continue;

            // Namespace for this config: fall back to global packNamespace if not provided
            String cfgNamespace = cfg.getString("info.namespace", packNamespace);
            if (cfgNamespace == null || cfgNamespace.isEmpty()) {
                cfgNamespace = packNamespace;
            }

            boolean enabled = gen.getBoolean("enabled", false);
            if (!enabled) continue;

            String namePrefix = gen.getString("name_prefix", "");
            String texturesPath = gen.getString("textures_path", "font");
            String startingChar = gen.getString("starting_char", "\\uE200");
            int defaultAscent = gen.getInt("ascent", 8);
            int defaultHeight = gen.getInt("height", 8);

            int codePoint = parseStartingCodePoint(startingChar);

            File baseDir = yamlFile.getParentFile();
            File texturesDir = new File(baseDir, texturesPath);
            if (!texturesDir.exists() || !texturesDir.isDirectory()) {
                // Fallback: look under contents/<namespace>/textures/<texturesPath>
                File contentsRoot = new File(baseDir, "contents");
                File namespaceRoot = new File(contentsRoot, cfgNamespace);
                File texturesRoot = new File(namespaceRoot, "textures");
                File altDir = new File(texturesRoot, texturesPath);
                if (altDir.exists() && altDir.isDirectory()) {
                    texturesDir = altDir;
                } else {
                    plugin.getLogger().warning("generate_font_images: textures_path " + texturesDir.getAbsolutePath() +
                            " and " + altDir.getAbsolutePath() + " do not exist or are not directories for " + yamlFile.getName());
                    continue;
                }
            }

            List<File> pngFiles = new ArrayList<>();
            Path root = texturesDir.toPath();

            try {
                Files.walk(root)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .forEach(p -> pngFiles.add(p.toFile()));
            } catch (IOException ex) {
                plugin.getLogger().warning("generate_font_images: failed to scan png files under " + texturesDir.getAbsolutePath()
                        + " for " + yamlFile.getName() + ": " + ex.getMessage());
                continue;
            }

            if (pngFiles.isEmpty()) {
                plugin.getLogger().warning("generate_font_images: no .png files found in " + texturesDir.getAbsolutePath()
                        + " for " + yamlFile.getName());
                continue;
            }

            // stable ordering by relative path
            File finalTexturesDir = texturesDir;
            pngFiles.sort(Comparator.comparing(f -> rootRelativePath(finalTexturesDir, f)));

//            Arrays.sort(pngFiles, Comparator.comparing(File::getName));

            // Preserve namespace and original base directory so we can resolve textures later
            YamlConfiguration outCfg = new YamlConfiguration();
            outCfg.set("info.namespace", cfgNamespace);
            outCfg.set("generate_font_images.origin_base_dir", baseDir.getAbsolutePath());

            for (File png : pngFiles) {
                String rel = rootRelativePath(texturesDir, png); // e.g. "roles/stemcraft.png"

                String relNoExt = rel.substring(0, rel.length() - 4); // strip .png
                String key = namePrefix + relNoExt.replace("/", "\\"); // roles\stemcraft

                String yamlBase = "font_images." + key;

                String charStr = new String(Character.toChars(codePoint));

                outCfg.set(yamlBase + ".char", charStr);
                outCfg.set(yamlBase + ".file", texturesPath + "/" + rel); // keep forward slashes in file path
                outCfg.set(yamlBase + ".ascent", defaultAscent);
                outCfg.set(yamlBase + ".height", defaultHeight);

                codePoint++;
            }

            String parentName = yamlFile.getParentFile().getName();
            String baseName = yamlFile.getName().replaceFirst("\\.yml$", "");
            String generatedName = parentName + "-" + baseName + "-generated.yml";
            File outFile = new File(cachePackDir, generatedName);

            try {
                outCfg.save(outFile);
                plugin.getLogger().info("Generated font image config: " + outFile.getAbsolutePath());
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to save generated font image config " + outFile.getAbsolutePath() + ": " + ex.getMessage());
            }
        }
    }

    // Merge contents folders from each top-level directory under resource-pack into the generated pack,
    // placing all contents under the pack's assets directory (i.e., treating the contents tree as the root of assets/)
    private void mergePackContents() throws IOException {
        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);
        File[] children = cfgDir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (!child.isDirectory()) continue;

            File contentsDir = new File(child, "contents");
            if (!contentsDir.exists() || !contentsDir.isDirectory()) continue;

            // Merge contents into the pack's assets directory so that
            // contents/<namespace>/textures/... ends up under assets/<namespace>/textures/...
            File assetsRoot = new File(packFolder, "assets");
            mergeDirectory(contentsDir, assetsRoot);
        }
    }

    private static void mergeDirectory(File sourceDir, File targetDir) throws IOException {
        String basePath = sourceDir.getCanonicalPath();
        mergeDirectoryRecursive(sourceDir, basePath, targetDir);
    }

    private static void mergeDirectoryRecursive(File current, String basePath, File targetDir) throws IOException {
        String currentPath = current.getCanonicalPath();
        String relativePath;
        if (currentPath.equals(basePath)) {
            relativePath = "";
        } else {
            relativePath = currentPath.substring(basePath.length() + 1).replace("\\", "/");
        }

        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children == null) return;
            for (File child : children) {
                mergeDirectoryRecursive(child, basePath, targetDir);
            }
        } else {
            File dest = relativePath.isEmpty() ? targetDir : new File(targetDir, relativePath);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Failed to create directory " + parent);
            }
            Files.copy(current.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int parseStartingCodePoint(String startingChar) {
        if (startingChar == null || startingChar.isEmpty()) {
            return 0xE200; // sensible default in private use area
        }

        // If it's a single character, just use its code point
        if (startingChar.length() == 1) {
            return startingChar.codePointAt(0);
        }

        String s = startingChar.trim();

        if (s.startsWith("\\u") || s.startsWith("\\U")) {
            s = s.substring(2);
        } else if (s.startsWith("U+")) {
            s = s.substring(2);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }

        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Could not parse starting_char '" + startingChar + "', falling back to U+E200");
            return 0xE200;
        }
    }

    private static void zipFolder(File sourceDir, File outFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile))) {
            String basePath = sourceDir.getCanonicalPath();
            zipDirectoryRecursive(zos, sourceDir, basePath);
        }
    }

    private static void zipDirectoryRecursive(ZipOutputStream zos, File file, String basePath) throws IOException {
        String filePath = file.getCanonicalPath();
        String relativePath;
        if (filePath.equals(basePath)) {
            // Root directory, no relative path
            relativePath = "";
        } else {
            relativePath = filePath.substring(basePath.length() + 1).replace("\\", "/");
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            if (!relativePath.isEmpty()) {
                if (!relativePath.endsWith("/")) {
                    relativePath = relativePath + "/";
                }
                zos.putNextEntry(new ZipEntry(relativePath));
                zos.closeEntry();
            }
            for (File child : children) {
                zipDirectoryRecursive(zos, child, basePath);
            }
        } else {
            zos.putNextEntry(new ZipEntry(relativePath));
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(zos);
            }
            zos.closeEntry();
        }
    }

    private static void deleteRecursive(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        if (!f.delete()) {
            throw new IOException("Failed to delete " + f);
        }
    }

    private static byte[] computeSha1(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream is = Files.newInputStream(file.toPath())) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = is.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
            }
            return md.digest();
        } catch (Exception e) {
            throw new IOException("Failed to compute SHA-1 for " + file, e);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Ask a single player to download the resource pack.
     * NOTE: you must host packZip at packUrl yourself.
     */
    public void sendPack(Player player) {
        if (packHash != null) {
            player.setResourcePack(addCacheBuster(packUrl), packHash);
        } else {
            player.setResourcePack(addCacheBuster(packUrl));
        }
    }

    /**
     * Broadcast the pack to all online players.
     */
    public void sendPackToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendPack(player);
        }
    }

    /**
     * Loads the resource pack data and embeds it into the plugin.
     */
    public void embedPack() {
        File cfgDir = new File(plugin.getDataFolder(), CONFIG_DIR_NAME);

        // Remove previous bindings
        plugin.localeService().removeBindings(bindings);
        bindings.clear();

        // Load font-image bindings from all YAMLs under resource-pack/
        List<YamlConfiguration> configs = loadAllPackConfigs(cfgDir);

        // Include generated configs as well
        List<File> generatedFiles = new ArrayList<>();
        collectYamlFiles(cachePackDir, "-generated", generatedFiles);
        for (File f : generatedFiles) {
            configs.add(YamlConfiguration.loadConfiguration(f));
        }

        for (YamlConfiguration cfg : configs) {
            ConfigurationSection fontImages = cfg.getConfigurationSection("font_images");
            if (fontImages == null) continue;

            for (String key : fontImages.getKeys(false)) {
                String charStr = fontImages.getString(key + ".char");
                if (charStr == null || charStr.isEmpty()) {
                    plugin.getLogger().warning("Glyph " + key + " has no 'char' defined in one of the resource-pack YAML files");
                    continue;
                }
                // this will allow :key: replacements
                plugin.localeService().addBinding(key, charStr);
                bindings.add(key);
            }
        }
    }

    /**
     * Resolves and copies a local texture referenced by a font image's `file` or `from` field into the
     * generated resource pack under assets/<namespace>/textures/<destRelPath>.
     *
     * Resolution order:
     *  1) <baseDir>/<relPath>
     *  2) <baseDir>/contents/<namespace>/textures/<relPath>
     *
     * This allows packs to either ship loose files in the package root (e.g. `icons/accept.png`) or
     * pre-structured files inside the `contents` merge tree (e.g. `contents/stemcraft-ui/textures/font/accept.png`).
     *
     * @param baseDir  the directory containing the YAML file (the resource-pack package root)
     * @param namespace the namespace used for this config (e.g. "stemcraft-ui")
     * @param relPath  the relative path as defined in the YAML (e.g. "font/accept.png")
     * @param destRelPath  the path under textures/ to place the copied file (may include subfolders)
     * @param texturesDir the directory inside the generated pack to place textures into
     * @return true if the texture was found and copied, false otherwise
     */
    private boolean copyLocalTexture(File baseDir, String namespace, String relPath, String destRelPath, File texturesDir) {
        File source = resolveTexturePath(baseDir, namespace, relPath);

        if (source == null) {
            plugin.getLogger().warning("Local texture for glyph " + destRelPath + " not found for path '" + relPath + "' under " + baseDir.getAbsolutePath());
            return false;
        }

        String outRel = destRelPath.replace("\\", "/");
        File outFile = new File(texturesDir, outRel);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Error copying local texture: failed to create directory " + parent.getAbsolutePath());
            return false;
        }
        try {
            Files.copy(source.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Error copying local texture for glyph " + destRelPath + " from " + source.getAbsolutePath() + ": " + ex.getMessage());
            return false;
        }
    }

    /**
     * Helper to resolve a texture file for a font image using the two-step search:
     *  1) STEMCraft/resource-pack/<package>/<file>
     *  2) STEMCraft/resource-pack/<package>/contents/<namespace>/textures/<file>
     *
     * Returns the resolved File if found, or null if not.
     */
    private File resolveTexturePath(File baseDir, String namespace, String relPath) {
        if (relPath == null || relPath.isEmpty()) return null;

        // 1) Directly under the package directory
        File direct = new File(baseDir, relPath);
        if (direct.exists() && direct.isFile()) {
            return direct;
        }

        // 2) Under contents/<namespace>/textures/<relPath>
        File contentsRoot = new File(baseDir, "contents");
        File namespaceRoot = new File(contentsRoot, namespace);
        File texturesRoot = new File(namespaceRoot, "textures");
        File fromContents = new File(texturesRoot, relPath);
        if (fromContents.exists() && fromContents.isFile()) {
            return fromContents;
        }

        return null;
    }

    private static class BedrockGlyph {
        String key;
        String charStr;
        File sourceFile;
    }

    /**
     * Computes a last-modified fingerprint for all files under the resource-pack
     * configuration tree. This is used to decide whether we need to regenerate
     * the pack or can reuse the cached one.
     */
    private long computeSourceLastModified() {
        File root = new File(plugin.getDataFolder(), "resource-pack");
        if (!root.exists()) {
            return 0L;
        }
        return getLatestModified(root);
    }

    private long getLatestModified(File file) {
        long latest = file.lastModified();
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    latest = Math.max(latest, getLatestModified(child));
                }
            }
        }
        return latest;
    }

    private String addCacheBuster(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return baseUrl;
        }

        long version = 0L;

        if (cacheMetaFile.exists()) {
            YamlConfiguration meta = YamlConfiguration.loadConfiguration(cacheMetaFile);
            version = meta.getLong("lastSourceModified", 0L);
        }

        // Fallback: compute directly if no meta is present yet
        if (version <= 0L) {
            version = computeSourceLastModified();
        }

        if (version <= 0L) {
            return baseUrl;
        }

        String v = Long.toString(version);
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "v=" + v;
    }

    private static String rootRelativePath(File rootDir, File file) {
        Path root = rootDir.toPath().toAbsolutePath().normalize();
        Path p = file.toPath().toAbsolutePath().normalize();
        return root.relativize(p).toString().replace("\\", "/");
    }
}