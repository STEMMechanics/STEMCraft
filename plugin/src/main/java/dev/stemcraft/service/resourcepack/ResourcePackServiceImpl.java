/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.service.resourcepack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.ResourcePackHost;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.api.util.FileUtil;
import dev.stemcraft.service.BaseService;
import dev.stemcraft.service.resourcepack.generators.GlyphGenerator;
import dev.stemcraft.service.resourcepack.generators.MinecraftPackGenerator;
import dev.stemcraft.service.resourcepack.generators.PackMetaGenerator;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.annotation.Nullable;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages the STEMCraft resource pack, including generation, hosting, and
 * sending to players.
 */
public class ResourcePackServiceImpl extends BaseService implements ResourcePackService {
    private record BedrockGlyphAsset(Path image, int javaHeight, int bedrockHeight, boolean autoScale, double scale, int yOffset) {}

    private File dataPacksDir;

    private ResourcePackHostImpl host;

    private final List<ResourcePackGenerator> generators = new ArrayList<>();
    private final Set<String> appliedManifestTokens = new HashSet<>();
    private String resourcePackHash = "";

    /**
     * Constructor for ResourcePackServiceImpl.
     *
     * @param plugin The main STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public ResourcePackServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Called when the service is enabled.
     */
    public void onEnable() {
        dataPacksDir = new File(plugin.getDataFolder(), "data-packs");
        // Bundled starter packs and the runtime working directory now use the
        // same path name for consistency. Existing files are not overwritten.
        plugin.exportBundledDirectory("data-packs");

        host = new ResourcePackHostImpl(api, this);
        host.onEnable(getConfigSection());

        ResourcePackCommand command = new ResourcePackCommand(api, this);
        command.onEnable();

        ResourcePackEvents events = new ResourcePackEvents(api, this);
        events.onEnable();

        registerGenerator(new PackMetaGenerator(api, this));
        registerGenerator(new GlyphGenerator(api, this));
        registerGenerator(new MinecraftPackGenerator(api, this));

        applyManifestTokensFromDisk();
    }

    @Override
    public void onReload() {
        super.onReload();
        applyManifestTokensFromDisk();
    }

    /**
     * Gets the resource pack configuration section.
     *
     * @return The resource pack configuration section.
     */
    public @NotNull ConfigSectionView getConfig() {
        return getConfigSection();
    }

    /**
     * Registers a resource pack generator.
     *
     * @param generator The resource pack generator to register.
     */
    public void registerGenerator(@NotNull ResourcePackGenerator generator) {
        if(generator.onLoad(getConfig())) {
            generators.add(generator);
        }
    }

    /**
     * Gets the resource pack file.
     *
     * @return The resource pack file, or null if not found.
     */
    public @Nullable File getResourcePack() {
        File rpFile = new File(plugin.getDataFolder(), "resource-pack.zip");
        if (rpFile.exists() && rpFile.isFile()) {
            return rpFile;
        }

        return null;
    }

    /**
     * Gets the SHA-1 hash of the resource pack file in hexadecimal format.
     *
     * @return The resource pack hash, or null if the pack does not exist.
     */
    public @NotNull String getResourcePackHash() {
        if(resourcePackHash == null || resourcePackHash.isEmpty()) {
            File resourcePack = getResourcePack();
            if (resourcePack != null && resourcePack.exists()) {
                resourcePackHash = FileUtil.sha1Hex(resourcePack);
            }
        }

        return resourcePackHash == null ? "" : resourcePackHash;
    }


    /**
     * Gets the ResourcePackHost instance.
     *
     * @return The ResourcePackHost.
     */
    public @NotNull ResourcePackHost host() {
        return host;
    }

    /**
     * Ask a single player to download the resource pack.
     *
     * @param player the player to send the pack to.
     */
    public void sendPack(@NotNull Player player) {
        File resPack = getResourcePack();
        if (resPack == null || !resPack.exists()) { return; }

        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                .uri(URI.create(host.getUrl()))
                .hash(getResourcePackHash())
                .build();

        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(true)
                .prompt(Component.text("This server requires the STEMCraft resource pack"))
                .build();

        player.sendResourcePacks(request);
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
     * Generates the resource pack.
     *
     * @param statusCallback Optional callback to receive status updates.
     */
    public void generatePack(@Nullable Consumer<String> statusCallback) {
        // clear previous resource pack
        File existingZip = plugin.getDataFolder().toPath().resolve("resource-pack.zip").toFile();
        if(existingZip.exists() && !existingZip.delete()) {
            api.messages().error("Failed to delete old resource-pack.zip");
            if(statusCallback != null) { statusCallback.accept("error"); }
            return;
        }

        // create resource pack manifest
        ConfigSection manifest = api.config().load("resource-pack-manifest.yml");
        if(manifest == null) {
            api.messages().error("Failed to load resource pack manifest");
            if(statusCallback != null) { statusCallback.accept("error"); }
            return;
        }

        manifest = manifest.getSection("manifest");
        manifest.removeAll();

        // create temporary resource pack folder
        File tempPackDir;
        try {
            Path tempDir = Files.createTempDirectory("resource-pack-");
            tempPackDir = tempDir.toFile();
        } catch(IOException e) {
            api.messages().error("Failed to create temporary resource pack directory", e);
            if(statusCallback != null) { statusCallback.accept("error"); }
            return;
        }

        // iterate each data pack directory under dataPacksDir and process resource-pack configs
        File[] dataPackDirs = dataPacksDir.listFiles(File::isDirectory);
        if (dataPackDirs == null) {
            if(statusCallback != null) { statusCallback.accept("error"); }
            return;
        }

        // buildStart
        for(ResourcePackGenerator generator : generators) {
            generator.buildStart(manifest, tempPackDir);
        }

        // iterate each data pack directory
        if(statusCallback != null) { statusCallback.accept("generating"); }
        for (File dataPackDir : dataPackDirs) {
            int tokensBefore = countTokens(manifest);
            int filesBefore = countFiles(tempPackDir);
            File contentsDir = new File(dataPackDir, "contents");
            if (contentsDir.exists() && contentsDir.isDirectory()) {
                // buildFromDataPack should run even if there is no configs/ directory.
                for(ResourcePackGenerator generator : generators) {
                    generator.buildFromDataPack(contentsDir, manifest, tempPackDir);
                }
            } else {
                plugin.getLogger().info(
                    "[resource-pack] Pack '" + dataPackDir.getName() + "': no contents/ directory " +
                    "(" + new File(dataPackDir, "contents").getAbsolutePath() + ")"
                );
            }

            // iterate YAML configs from the data-pack location
            List<File> configFiles = collectPackConfigFiles(dataPackDir);

            if (configFiles.isEmpty()) {
                int tokensAfter = countTokens(manifest);
                int filesAfter = countFiles(tempPackDir);
                plugin.getLogger().info(
                    "[resource-pack] Pack '" + dataPackDir.getName() + "': +" +
                    (filesAfter - filesBefore) + " files, +" + (tokensAfter - tokensBefore) +
                    " glyph tokens (no config files)"
                );
                continue;
            }

            File[] namespaceDirs = contentsDir.exists() ? contentsDir.listFiles(file ->
                file.isDirectory() && !file.getName().startsWith(".")
            ) : null;

            for(File file : configFiles) {
                ConfigSection config = api.config().load(file);
                if(config == null) {
                    continue;
                }

                String namespace = config.getString("namespace");

                if(!namespace.isEmpty()) {
                    File namespaceDir = new File(contentsDir, namespace);
                    if(!namespaceDir.exists() || !namespaceDir.isDirectory()) {
                        continue;
                    }

                    for(ResourcePackGenerator generator : generators) {
                        generator.buildFromDataPackConfig(namespace, config, namespaceDir, manifest, tempPackDir);
                    }
                    continue;
                }

                if (namespaceDirs == null) {
                    continue;
                }

                for (File namespaceDir : namespaceDirs) {
                    for(ResourcePackGenerator generator : generators) {
                        generator.buildFromDataPackConfig(namespaceDir.getName(), config, namespaceDir, manifest, tempPackDir);
                    }
                }
            }

            int tokensAfter = countTokens(manifest);
            int filesAfter = countFiles(tempPackDir);
            plugin.getLogger().info(
                "[resource-pack] Pack '" + dataPackDir.getName() + "': +" +
                (filesAfter - filesBefore) + " files, +" + (tokensAfter - tokensBefore) + " glyph tokens"
            );
        }

        // buildEnd
        for(ResourcePackGenerator generator : generators) {
            generator.buildEnd(manifest, tempPackDir);
        }

        copyJavaPackIcon(tempPackDir);

        manifest.save();
        applyManifest(manifest);

        // zip up the resource pack
        try {
            File zipFile = plugin.getDataFolder().toPath().resolve("resource-pack.zip").toFile();

            if (statusCallback != null) { statusCallback.accept("compressing"); }
            zipFolder(tempPackDir, zipFile);

            resourcePackHash = "";
        } catch (IOException ex) {
            api.messages().error("Failed to create resource pack zip", ex);
            if (statusCallback != null) { statusCallback.accept("error"); }
            return;
        }

        generateBedrockPack(tempPackDir, manifest);
        reloadGeyserAfterPackBuild();

        // delete temporary resource pack folder
        if(!deleteDirectory(tempPackDir.toPath())) {
            api.messages().error("Failed to delete temporary resource pack directory: " + tempPackDir.getAbsolutePath());
        }

        if(statusCallback != null) { statusCallback.accept("complete"); }
    }

    private void reloadGeyserAfterPackBuild() {
        ConfigSectionView config = getConfig();
        if (!config.getBoolean("bedrock.reload_geyser_on_zip", true)) {
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return;
        }

        String command = config.getString("bedrock.reload_command", "geyser reload").trim();
        if (command.isBlank()) {
            return;
        }

        String commandLine = command.startsWith("/") ? command.substring(1) : command;
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine);
        if (!dispatched) {
            plugin.getLogger().warning("[resource-pack] Failed to dispatch bedrock reload command: " + commandLine);
        } else {
            plugin.getLogger().info("[resource-pack] Dispatched bedrock reload command: " + commandLine);
        }
    }

    private void applyManifestTokensFromDisk() {
        ConfigSection manifestRoot = api.config().load("resource-pack-manifest.yml");
        if (manifestRoot == null) {
            return;
        }
        applyManifest(manifestRoot.getSection("manifest"));
    }

    private void applyManifest(ConfigSectionView manifest) {
        if (manifest == null) {
            return;
        }

        if (!appliedManifestTokens.isEmpty()) {
            api.messages().tokens().remove(appliedManifestTokens);
            appliedManifestTokens.clear();
        }

        for (ResourcePackGenerator generator : generators) {
            generator.apply("", manifest);
        }

        ConfigSectionView tokenSection = manifest.getSection("tokens");
        if (tokenSection == null) {
            return;
        }

        appliedManifestTokens.addAll(tokenSection.getKeys());
    }

    /**
     * Zips the contents of a folder into a zip file.
     *
     * @param sourceDir the folder to zip.
     * @param outFile the output zip file.
     * @throws IOException if an I/O error occurs.
     */
    private static void zipFolder(File sourceDir, File outFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile))) {
            String basePath = sourceDir.getCanonicalPath();
            zipDirectoryRecursive(zos, sourceDir, basePath);
        }
    }

    /**
     * Recursively zips a directory.
     *
     * @param zos the zip output stream.
     * @param file the current file or directory.
     * @param basePath the base path to determine relative paths.
     * @throws IOException if an I/O error occurs.
     */
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

    private static boolean deleteDirectory(Path root) {
        if (root == null || !Files.exists(root)) {
            return true;
        }

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static int countTokens(ConfigSectionView manifest) {
        if (manifest == null) {
            return 0;
        }

        ConfigSectionView tokens = manifest.getSection("tokens");
        if (tokens == null) {
            return 0;
        }

        return tokens.getKeys().size();
    }

    private static int countFiles(File root) {
        if (root == null || !root.exists()) {
            return 0;
        }

        if (root.isFile()) {
            return 1;
        }

        File[] children = root.listFiles();
        if (children == null) {
            return 0;
        }

        int total = 0;
        for (File child : children) {
            total += countFiles(child);
        }
        return total;
    }

    private void generateBedrockPack(File javaPackDir, ConfigSectionView manifest) {
        ConfigSectionView config = getConfig();
        if (!config.getBoolean("bedrock.enabled", true)) {
            return;
        }

        Path geyserPacksDir = resolveBedrockPacksDir(config);
        String packName = config.getString("bedrock.pack_name", "stemcraft-bedrock");
        Path bedrockPackDir = geyserPacksDir.resolve(packName);
        Path bedrockZip = geyserPacksDir.resolve(packName + ".zip");

        if (!deleteDirectory(bedrockPackDir)) {
            plugin.getLogger().warning("[resource-pack] Failed to clear old bedrock pack at " + bedrockPackDir);
            return;
        }
        try {
            Files.deleteIfExists(bedrockZip);
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed to clear old bedrock zip at " + bedrockZip + ": " + e.getMessage());
        }

        try {
            Files.createDirectories(bedrockPackDir);
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed to create bedrock pack directory " + bedrockPackDir + ": " + e.getMessage());
            return;
        }

        ConfigSectionView tokens = manifest.getSection("tokens");
        ConfigSectionView tokenMeta = manifest.getSection("token-meta");

        if (tokens == null || tokenMeta == null) {
            plugin.getLogger().info("[resource-pack] Bedrock pack '" + packName + "': no glyph metadata available");
            String iconSignature = copyBedrockPackIcon(bedrockPackDir);
            writeBedrockManifest(bedrockPackDir, packName, 0, "empty|icon=" + iconSignature);
            writeBedrockZip(bedrockPackDir, bedrockZip);
            return;
        }

        int copied = 0;
        String buildNonce = UUID.randomUUID().toString();
        JsonObject textureRoot = new JsonObject();
        textureRoot.addProperty("resource_pack_name", packName);
        textureRoot.addProperty("texture_name", "atlas.items");
        JsonObject textureData = new JsonObject();
        textureRoot.add("texture_data", textureData);
        StringBuilder signatureBuilder = new StringBuilder("bedrock|pack=").append(packName).append("|build=").append(buildNonce);

        JsonObject glyphMap = new JsonObject();
        Map<Integer, BedrockGlyphAsset> glyphImagesByCodepoint = new HashMap<>();
        Map<Integer, String> codepointOwners = new HashMap<>();

        List<String> sortedTokens = new ArrayList<>(tokenMeta.getKeys());
        sortedTokens.sort(String.CASE_INSENSITIVE_ORDER);
        for (String token : sortedTokens) {
            ConfigSectionView tokenSection = tokenMeta.getSection(token);
            if (tokenSection == null) {
                continue;
            }

            String namespace = tokenSection.getString("namespace", "").trim();
            String file = tokenSection.getString("file", "").trim();
            if (namespace.isEmpty() || file.isEmpty()) {
                continue;
            }

            Path src = javaPackDir.toPath().resolve("assets").resolve(namespace).resolve("textures").resolve(file);
            if (!Files.exists(src) || !Files.isRegularFile(src)) {
                continue;
            }

            String safeToken = sanitizeToken(token);
            Path dest = bedrockPackDir
                .resolve("textures")
                .resolve("stemcraft")
                .resolve("glyphs")
                .resolve(safeToken + ".png");

            try {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException e) {
                plugin.getLogger().warning("[resource-pack] Failed to copy bedrock glyph '" + token + "': " + e.getMessage());
                continue;
            }

            JsonObject texDef = new JsonObject();
            texDef.addProperty("textures", "textures/stemcraft/glyphs/" + safeToken);
            textureData.add(safeToken, texDef);

            JsonObject mapDef = new JsonObject();
            mapDef.addProperty("token", ":" + token + ":");
            String unicode = tokens.getString(token, "");
            mapDef.addProperty("unicode", unicode);
            mapDef.addProperty("texture", "textures/stemcraft/glyphs/" + safeToken + ".png");
            glyphMap.add(safeToken, mapDef);
            signatureBuilder
                .append("|token=").append(token)
                .append("|unicode=").append(unicode)
                .append("|namespace=").append(namespace)
                .append("|file=").append(file)
                .append("|hash=").append(FileUtil.sha1Hex(dest.toFile()))
                .append("|javaHeight=").append(tokenSection.getInt("height", 8))
                .append("|bedrockHeight=").append(tokenSection.getInt("bedrock.height", tokenSection.getInt("height", 8)))
                .append("|autoScale=").append(tokenSection.getBoolean("bedrock.auto_scale", true))
                .append("|scale=").append(tokenSection.getDouble("bedrock.scale", 1.0d))
                .append("|yOffset=").append(tokenSection.getInt("bedrock.y_offset", 0));

            int codepoint = parseUnicodeCodepoint(unicode, -1);
            if (codepoint >= 0 && codepoint <= 0xFFFF) {
                String previousOwner = codepointOwners.putIfAbsent(codepoint, token);
                if (previousOwner != null) {
                    plugin.getLogger().warning(
                        "[resource-pack] Duplicate glyph codepoint U+"
                            + String.format(Locale.ROOT, "%04X", codepoint)
                            + " for tokens '" + previousOwner + "' and '" + token
                            + "'. Keeping first token mapping."
                    );
                } else {
                    glyphImagesByCodepoint.put(codepoint, new BedrockGlyphAsset(
                        dest,
                        tokenSection.getInt("height", 8),
                        tokenSection.getInt("bedrock.height", tokenSection.getInt("height", 8)),
                        tokenSection.getBoolean("bedrock.auto_scale", true),
                        tokenSection.getDouble("bedrock.scale", 1.0d),
                        tokenSection.getInt("bedrock.y_offset", 0)
                    ));
                }
            }
            copied++;
        }

        try {
            Files.createDirectories(bedrockPackDir.resolve("textures"));
            Files.writeString(
                bedrockPackDir.resolve("textures").resolve("item_texture.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(textureRoot),
                StandardCharsets.UTF_8
            );

            Files.writeString(
                bedrockPackDir.resolve("glyph-map.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(glyphMap),
                StandardCharsets.UTF_8
            );

            String iconSignature = copyBedrockPackIcon(bedrockPackDir);
            String signature = signatureBuilder.append("|icon=").append(iconSignature).toString();
            writeBedrockManifest(bedrockPackDir, packName, copied, signature);
            writeBedrockFontPages(bedrockPackDir, glyphImagesByCodepoint);
            writeBedrockZip(bedrockPackDir, bedrockZip);
            plugin.getLogger().info("[resource-pack] Bedrock pack '" + packName + "': " + copied + " mapped glyph textures -> " + bedrockPackDir);
            plugin.getLogger().info("[resource-pack] Bedrock pack zip: " + bedrockZip);
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed writing bedrock pack metadata: " + e.getMessage());
        }
    }

    private Path resolveBedrockPacksDir(ConfigSectionView config) {
        String raw = config.getString("bedrock.packs_dir", "../Geyser-Spigot/packs").trim();
        Path base = plugin.getDataFolder().toPath();
        Path resolved = Path.of(raw);
        if (!resolved.isAbsolute()) {
            resolved = base.resolve(raw).normalize();
        }
        return resolved;
    }

    private void copyJavaPackIcon(File javaPackDir) {
        Path source = resolvePackIconFromDataPacks();
        if (source == null) {
            return;
        }

        Path destination = javaPackDir.toPath().resolve("pack.png");
        try {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed to copy Java pack icon from " + source + ": " + e.getMessage());
        }
    }

    private String copyBedrockPackIcon(Path bedrockPackDir) {
        Path source = resolvePackIconFromDataPacks();
        if (source == null) {
            return "none";
        }

        Path destination = bedrockPackDir.resolve("pack_icon.png");
        try {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed to copy Bedrock pack icon from " + source + ": " + e.getMessage());
            return "copy-error";
        }

        String hash = FileUtil.sha1Hex(destination.toFile());
        return hash == null || hash.isBlank() ? "present" : hash;
    }

    private @Nullable Path resolvePackIconFromDataPacks() {
        if (dataPacksDir == null || !dataPacksDir.exists() || !dataPacksDir.isDirectory()) {
            return null;
        }

        File[] packDirs = dataPacksDir.listFiles(File::isDirectory);
        if (packDirs == null || packDirs.length == 0) {
            return null;
        }

        Arrays.sort(packDirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        Path selected = null;
        List<String> matchingPacks = new ArrayList<>();
        for (File packDir : packDirs) {
            Path candidate = packDir.toPath().resolve("pack.png");
            if (Files.isRegularFile(candidate)) {
                matchingPacks.add(packDir.getName());
                if (selected == null) {
                    selected = candidate;
                }
            }
        }

        if (selected == null) {
            return null;
        }

        if (matchingPacks.size() > 1) {
            plugin.getLogger().warning(
                "[resource-pack] Multiple data-pack icons found (" + matchingPacks
                    + "). Using first pack icon from '" + matchingPacks.get(0) + "'."
            );
        }

        return selected;
    }

    private void writeBedrockManifest(Path bedrockPackDir, String packName, int glyphCount, String signature) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", 2);
        JsonObject header = new JsonObject();
        header.addProperty("name", packName);
        header.addProperty("description", "STEMCraft Bedrock glyph pack (" + glyphCount + " textures)");
        String stableSignature = signature == null ? "" : signature;
        header.addProperty("uuid", UUID.nameUUIDFromBytes(("stemcraft-bedrock-header-" + packName + "-" + stableSignature).getBytes(StandardCharsets.UTF_8)).toString());
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(0);
        version.add(0);
        header.add("version", version);
        JsonArray minEngine = new JsonArray();
        minEngine.add(1);
        minEngine.add(20);
        minEngine.add(0);
        header.add("min_engine_version", minEngine);
        root.add("header", header);

        JsonArray modules = new JsonArray();
        JsonObject resourcesModule = new JsonObject();
        resourcesModule.addProperty("type", "resources");
        resourcesModule.addProperty("uuid", UUID.nameUUIDFromBytes(("stemcraft-bedrock-module-" + packName + "-" + stableSignature).getBytes(StandardCharsets.UTF_8)).toString());
        resourcesModule.add("version", version.deepCopy());
        modules.add(resourcesModule);
        root.add("modules", modules);

        try {
            Files.writeString(
                bedrockPackDir.resolve("manifest.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(root),
                StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed to write bedrock manifest.json: " + e.getMessage());
        }
    }

    private void writeBedrockZip(Path bedrockPackDir, Path bedrockZip) {
        try {
            File zipFile = bedrockZip.toFile();
            zipFolder(bedrockPackDir.toFile(), zipFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed to create bedrock zip " + bedrockZip + ": " + e.getMessage());
        }
    }

    private String sanitizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "glyph";
        }

        String out = token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        out = out.replaceAll("_+", "_");
        if (out.isBlank()) {
            out = "glyph";
        }
        return out;
    }

    private int parseUnicodeCodepoint(String value, int fallback) {
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

    private void writeBedrockFontPages(Path bedrockPackDir, Map<Integer, BedrockGlyphAsset> glyphImagesByCodepoint) {
        if (glyphImagesByCodepoint.isEmpty()) {
            return;
        }

        // Bedrock glyph pages are 16x16 cells; each page represents 0xXX00-0xXXFF code points.
        final int cellSize = 16;
        final int pageSize = 16 * cellSize;
        Map<Integer, BufferedImage> pages = new HashMap<>();

        for (Map.Entry<Integer, BedrockGlyphAsset> entry : glyphImagesByCodepoint.entrySet()) {
            int codepoint = entry.getKey();
            if (codepoint < 0 || codepoint > 0xFFFF) {
                continue;
            }

            int page = (codepoint >> 8) & 0xFF;
            int low = codepoint & 0xFF;
            int row = (low >> 4) & 0x0F;
            int col = low & 0x0F;

            BufferedImage pageImage = pages.computeIfAbsent(page, ignored ->
                new BufferedImage(pageSize, pageSize, BufferedImage.TYPE_INT_ARGB)
            );

            BufferedImage src;
            try {
                src = ImageIO.read(entry.getValue().image().toFile());
            } catch (IOException e) {
                plugin.getLogger().warning("[resource-pack] Failed to read glyph image " + entry.getValue().image() + ": " + e.getMessage());
                continue;
            }
            if (src == null) {
                continue;
            }

            int drawW = src.getWidth();
            int drawH = src.getHeight();
            if (entry.getValue().autoScale()) {
                int targetHeight = Math.max(1, Math.min(cellSize, entry.getValue().bedrockHeight() > 0
                    ? entry.getValue().bedrockHeight()
                    : entry.getValue().javaHeight()));
                double fit = targetHeight / (double) Math.max(1, src.getHeight());
                drawW = Math.max(1, (int) Math.round(src.getWidth() * fit));
                drawH = Math.max(1, (int) Math.round(src.getHeight() * fit));
            }

            double manualScale = entry.getValue().scale() <= 0.0d ? 1.0d : entry.getValue().scale();
            drawW = Math.max(1, (int) Math.round(drawW * manualScale));
            drawH = Math.max(1, (int) Math.round(drawH * manualScale));

            if (drawW > cellSize || drawH > cellSize) {
                double fit = Math.min(cellSize / (double) drawW, cellSize / (double) drawH);
                drawW = Math.max(1, (int) Math.round(drawW * fit));
                drawH = Math.max(1, (int) Math.round(drawH * fit));
            }

            int x = (col * cellSize) + Math.max(0, (cellSize - drawW) / 2);
            int y = (row * cellSize) + Math.max(0, (cellSize - drawH) / 2) + entry.getValue().yOffset();
            int minY = row * cellSize;
            int maxY = minY + (cellSize - drawH);
            y = Math.max(minY, Math.min(maxY, y));

            BufferedImage glyphImage = scaleBedrockGlyph(src, drawW, drawH);

            Graphics2D g = pageImage.createGraphics();
            try {
                g.drawImage(glyphImage, x, y, null);
            } finally {
                g.dispose();
            }
        }

        Path fontDir = bedrockPackDir.resolve("font");
        try {
            Files.createDirectories(fontDir);
        } catch (IOException e) {
            plugin.getLogger().warning("[resource-pack] Failed to create bedrock font dir: " + e.getMessage());
            return;
        }

        for (Map.Entry<Integer, BufferedImage> entry : pages.entrySet()) {
            String fileName = String.format(Locale.ROOT, "glyph_%02X.png", entry.getKey());
            Path out = fontDir.resolve(fileName);
            try {
                ImageIO.write(entry.getValue(), "png", out.toFile());
            } catch (IOException e) {
                plugin.getLogger().warning("[resource-pack] Failed to write bedrock font page " + fileName + ": " + e.getMessage());
            }
        }
    }

    private BufferedImage scaleBedrockGlyph(BufferedImage src, int targetWidth, int targetHeight) {
        int safeWidth = Math.max(1, targetWidth);
        int safeHeight = Math.max(1, targetHeight);

        if (src.getWidth() == safeWidth && src.getHeight() == safeHeight) {
            return src;
        }

        if (safeWidth < src.getWidth() || safeHeight < src.getHeight()) {
            return downscaleGlyphAreaAverage(src, safeWidth, safeHeight);
        }

        return scaleGlyphNearest(src, safeWidth, safeHeight);
    }

    private BufferedImage scaleGlyphNearest(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private BufferedImage downscaleGlyphAreaAverage(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

        double scaleX = src.getWidth() / (double) targetWidth;
        double scaleY = src.getHeight() / (double) targetHeight;

        for (int ty = 0; ty < targetHeight; ty++) {
            double srcY0 = ty * scaleY;
            double srcY1 = (ty + 1) * scaleY;

            int yStart = Math.max(0, (int) Math.floor(srcY0));
            int yEnd = Math.min(src.getHeight() - 1, (int) Math.ceil(srcY1) - 1);

            for (int tx = 0; tx < targetWidth; tx++) {
                double srcX0 = tx * scaleX;
                double srcX1 = (tx + 1) * scaleX;

                int xStart = Math.max(0, (int) Math.floor(srcX0));
                int xEnd = Math.min(src.getWidth() - 1, (int) Math.ceil(srcX1) - 1);

                double totalArea = 0.0d;
                double alphaArea = 0.0d;
                double redArea = 0.0d;
                double greenArea = 0.0d;
                double blueArea = 0.0d;

                for (int sy = yStart; sy <= yEnd; sy++) {
                    double overlapY = Math.min(srcY1, sy + 1.0d) - Math.max(srcY0, sy);
                    if (overlapY <= 0.0d) {
                        continue;
                    }

                    for (int sx = xStart; sx <= xEnd; sx++) {
                        double overlapX = Math.min(srcX1, sx + 1.0d) - Math.max(srcX0, sx);
                        if (overlapX <= 0.0d) {
                            continue;
                        }

                        double area = overlapX * overlapY;
                        int argb = src.getRGB(sx, sy);
                        double alpha = ((argb >>> 24) & 0xFF) / 255.0d;
                        double red = (argb >>> 16) & 0xFF;
                        double green = (argb >>> 8) & 0xFF;
                        double blue = argb & 0xFF;

                        totalArea += area;
                        alphaArea += alpha * area;
                        redArea += red * alpha * area;
                        greenArea += green * alpha * area;
                        blueArea += blue * alpha * area;
                    }
                }

                if (totalArea <= 0.0d || alphaArea <= 0.0d) {
                    out.setRGB(tx, ty, 0x00000000);
                    continue;
                }

                int alpha = clampColour((int) Math.round((alphaArea / totalArea) * 255.0d));
                int red = clampColour((int) Math.round(redArea / alphaArea));
                int green = clampColour((int) Math.round(greenArea / alphaArea));
                int blue = clampColour((int) Math.round(blueArea / alphaArea));
                out.setRGB(tx, ty, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        return out;
    }

    private int clampColour(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private List<File> collectPackConfigFiles(File packDir) {
        LinkedHashSet<File> configFiles = new LinkedHashSet<>();
        addPackConfigFiles(configFiles, packDir);
        return new ArrayList<>(configFiles);
    }

    private void addPackConfigFiles(Set<File> configFiles, File packDir) {
        if (packDir == null || !packDir.exists() || !packDir.isDirectory()) {
            return;
        }

        File rootConfig = new File(packDir, "config.yml");
        if (rootConfig.exists() && rootConfig.isFile()) {
            configFiles.add(rootConfig);
        }

        File configsDir = new File(packDir, "configs");
        if (!configsDir.exists() || !configsDir.isDirectory()) {
            return;
        }

        File[] files = configsDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(".yml")
        );

        if (files != null) {
            configFiles.addAll(Arrays.asList(files));
        }
    }
}
