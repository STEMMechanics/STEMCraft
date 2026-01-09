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

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages the STEMCraft resource pack, including generation, hosting, and
 * sending to players.
 */
public class ResourcePackServiceImpl extends BaseService implements ResourcePackService {
    private File dataPacksDir;

    private ResourcePackHostImpl host;

    private final List<ResourcePackGenerator> generators = new ArrayList<>();
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
        plugin.exportBundledDirectory("data-packs");
        dataPacksDir = new File(plugin.getDataFolder(), "data-packs");

        host = new ResourcePackHostImpl(api, this);
        host.onEnable(getConfigSection());

        ResourcePackCommand command = new ResourcePackCommand(api, this);
        command.onEnable();

        ResourcePackEvents events = new ResourcePackEvents(api, this);
        events.onEnable();

        registerGenerator(new PackMetaGenerator(api, this));
        registerGenerator(new GlyphGenerator(api, this));
        registerGenerator(new MinecraftPackGenerator(api, this));
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
        if(resourcePackHash.isEmpty()) {
            File resourcePack = getResourcePack();
            if (resourcePack != null && resourcePack.exists()) {
                resourcePackHash = FileUtil.sha1Hex(resourcePack);
            }
        }

        return resourcePackHash;
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
        if(!plugin.getDataFolder().toPath().resolve("resource-pack.zip").toFile().delete()) {
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
            File configsDir = new File(dataPackDir, "configs");
            if (!configsDir.exists() || !configsDir.isDirectory()) {
                continue;
            }

            File contentsDir = new File(dataPackDir, "contents");
            if (!contentsDir.exists() || !contentsDir.isDirectory()) {
                continue;
            }

            // buildFromDataPack
            for(ResourcePackGenerator generator : generators) {
                generator.buildFromDataPack(contentsDir, manifest, tempPackDir);
            }


            // iterate each YAML config file under configsDir
            File[] files = configsDir.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml")
            );

            if (files == null) {
                return;
            }

            for(File file : files) {
                ConfigSection config = api.config().load(file);
                if(config == null) {
                    continue;
                }

                String namespace = config.getString("namespace");

                if(namespace.isEmpty()) {
                    return;
                }

                File namespaceDir = new File(contentsDir, namespace);
                if(!namespaceDir.exists() || !namespaceDir.isDirectory()) {
                    return;
                }

                // buildFromDataPackConfig
                for(ResourcePackGenerator generator : generators) {
                    generator.buildFromDataPackConfig(namespace, config, namespaceDir, manifest, tempPackDir);
                }
            }
        }

        // buildEnd
        for(ResourcePackGenerator generator : generators) {
            generator.buildEnd(manifest, tempPackDir);
        }

        // zip up the resource pack
        try {
            File zipFile = plugin.getDataFolder().toPath().resolve("resource-pack.zip").toFile();

            if (statusCallback != null) { statusCallback.accept("compressing"); }
            zipFolder(tempPackDir, zipFile);

            resourcePackHash = null;
        } catch (IOException ex) {
            api.messages().error("Failed to create resource pack zip", ex);
            if (statusCallback != null) { statusCallback.accept("error"); }
            return;
        }

        // delete temporary resource pack folder
        if(!tempPackDir.delete()) {
            api.messages().error("Failed to delete temporary resource pack directory: " + tempPackDir.getAbsolutePath());
        }

        if(statusCallback != null) { statusCallback.accept("complete"); }
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
}
