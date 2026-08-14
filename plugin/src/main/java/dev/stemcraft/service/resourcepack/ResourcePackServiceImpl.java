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
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildTarget;
import dev.stemcraft.api.service.resourcepack.ResourcePackHost;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.service.resourcepack.ResourcePackWriter;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import dev.stemcraft.api.util.FileUtil;
import dev.stemcraft.exception.ResourcePackGeneratorException;
import dev.stemcraft.service.BaseService;
import dev.stemcraft.service.resourcepack.generators.GlyphGenerator;
import dev.stemcraft.service.resourcepack.generators.MinecraftPackGenerator;
import dev.stemcraft.service.resourcepack.generators.PackMetaGenerator;
import dev.stemcraft.service.resourcepack.generators.CustomItemGenerator;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages the STEMCraft resource pack, including generation, hosting, and
 * sending to players.
 */
public class ResourcePackServiceImpl extends BaseService implements ResourcePackService {
    private int minSupportedVersion;
    private int maxSupportedVersion;
    private final int currentMinecraftFormatVersion;
    private PackFormatRange baseSupportedRange;
    private List<PlannedBuildSegment> plannedBuildSegments = List.of();
    private record BedrockGlyphAsset(Path image, int javaHeight, int bedrockHeight, boolean autoScale, double scale, int yOffset) {}
    private record BedrockCustomItemPackEntry(
        @NotNull String javaItemId,
        @NotNull String itemModelId,
        @NotNull String bedrockIdentifier,
        @NotNull String icon,
        @NotNull String displayName,
        @NotNull Path textureSource
    ) {}
    private record ResourcePackFormatVersion(int[] minecraftVersion, int formatVersion) {}
    public record OverlayBuildPlanEntry(@NotNull String directory, @NotNull PackFormatRange supportedRange) {}
    private record PlannedBuildSegment(
        @NotNull ResourcePackBuildTarget target,
        @NotNull PackFormatRange supportedRange,
        @Nullable String overlayDirectory,
        @NotNull List<ResourcePackGenerator> generators
    ) {
        private boolean overlay() {
            return overlayDirectory != null && !overlayDirectory.isBlank();
        }
    }

    private static final List<ResourcePackFormatVersion> RESOURCE_PACK_FORMAT_VERSIONS = List.of(
        new ResourcePackFormatVersion(new int[] {1, 20, 5}, 32),
        new ResourcePackFormatVersion(new int[] {1, 21, 0}, 34),
        new ResourcePackFormatVersion(new int[] {1, 21, 2}, 42),
        new ResourcePackFormatVersion(new int[] {1, 21, 4}, 46),
        new ResourcePackFormatVersion(new int[] {1, 21, 5}, 55),
        new ResourcePackFormatVersion(new int[] {1, 21, 6}, 63),
        new ResourcePackFormatVersion(new int[] {1, 21, 7}, 64),
        new ResourcePackFormatVersion(new int[] {1, 21, 9}, 69),
        new ResourcePackFormatVersion(new int[] {1, 21, 11}, 75),
        new ResourcePackFormatVersion(new int[] {26, 1, 0}, 84),
        new ResourcePackFormatVersion(new int[] {26, 2, 0}, 88)
    );

    private File dataPacksDir;

    private ResourcePackHostImpl host;

    private final List<ResourcePackGenerator> generators = new ArrayList<>();
    private final Map<String, ResourcePackGenerator> generatorsById = new LinkedHashMap<>();
    private final Map<Class<? extends ResourcePackGenerator>, ResourcePackGenerator> generatorsByType = new LinkedHashMap<>();
    private final Map<String, ResourcePackGenerator> pendingGenerators = new LinkedHashMap<>();
    private final Set<String> appliedManifestTokens = new HashSet<>();
    private final Set<String> emittedSupportedRangeWarnings = new HashSet<>();
    private String resourcePackHash = "";
    private boolean buildInProgress;

    /**
     * Constructor for ResourcePackServiceImpl.
     *
     * @param plugin The main STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public ResourcePackServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);

        currentMinecraftFormatVersion = resolveResourcePackFormat(STEMCraft.getMinecraftVersion());
        int[] supportedRange = resolveSupportedVersionRange(STEMCraft.getMinecraftVersion());
        minSupportedVersion = supportedRange[0];
        maxSupportedVersion = supportedRange[1];
        baseSupportedRange = new PackFormatRange(minSupportedVersion, maxSupportedVersion);

        if (isMinecraftVersionBeyondKnownRange(STEMCraft.getMinecraftVersion())) {
            plugin.getLogger().warning(
                "[resource-pack] Minecraft "
                    + Bukkit.getMinecraftVersion()
                    + " is newer than the latest known resource-pack format mapping. "
                    + "Using pack format "
                    + maxSupportedVersion
                    + " until the mapping table is updated."
            );
        }
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
        recalculateSupportedVersionRange();

        ResourcePackCommand command = new ResourcePackCommand(api, this);
        command.onEnable();

        ResourcePackEvents events = new ResourcePackEvents(api, this);
        events.onEnable();

        registerGenerator(new PackMetaGenerator(this));
        registerGenerator(new GlyphGenerator(this));
        registerGenerator(new MinecraftPackGenerator(this));
        registerGenerator(new CustomItemGenerator());
        attemptPendingGeneratorRegistrations();
        logPendingGenerators();

        applyManifestTokensFromDisk();
    }

    @Override
    public void onDisable() {
        for (ResourcePackGenerator generator : generators) {
            generator.onUnload();
        }
        generators.clear();
        generatorsById.clear();
        generatorsByType.clear();
        pendingGenerators.clear();
    }

    @Override
    public void onReload() {
        super.onReload();
        emittedSupportedRangeWarnings.clear();
        reloadActiveGenerators();
        attemptPendingGeneratorRegistrations();
        recalculateSupportedVersionRange();
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

    public @NotNull File[] dataPackDirectories() {
        File[] packDirs = dataPacksDir == null ? null : dataPacksDir.listFiles(File::isDirectory);
        return packDirs == null ? new File[0] : packDirs;
    }

    public @NotNull List<File> collectPackConfigFiles(@NotNull File packDir) {
        LinkedHashSet<File> configFiles = new LinkedHashSet<>();
        addPackConfigFiles(configFiles, packDir);
        return new ArrayList<>(configFiles);
    }

    public @Nullable ConfigSection loadPackConfig(@NotNull File file) {
        return api.config().load(file);
    }

    public @NotNull List<OverlayBuildPlanEntry> overlayBuildPlan() {
        return plannedBuildSegments.stream()
            .filter(PlannedBuildSegment::overlay)
            .map(segment -> new OverlayBuildPlanEntry(
                Objects.requireNonNull(segment.overlayDirectory()),
                segment.supportedRange()
            ))
            .toList();
    }

    private void reloadActiveGenerators() {
        for (ResourcePackGenerator generator : generators) {
            generator.onUnload();
            generator.onLoad(generatorConfig(generator.id()));
        }
    }

    private void activateGenerator(@NotNull ResourcePackGenerator generator) {
        generator.onLoad(generatorConfig(generator.id()));
        generators.add(generator);
        generatorsById.put(generator.id(), generator);
        generatorsByType.put(generator.getClass(), generator);

        if (!supportsCurrentMinecraftFormat(generator)) {
            plugin.getLogger().info(
                "[resource-pack] Generator " + generator.id()
                    + " does not support the current server pack format "
                    + currentMinecraftFormatVersion
                    + " and will only run for compatible targets."
            );
        }
    }

    private @NotNull ConfigSectionView generatorConfig(@NotNull String generatorId) {
        ConfigSectionView generatorsSection = getConfig().getSection("generators");
        return generatorsSection.getSection(generatorId);
    }

    private @NotNull String requireGeneratorId(@NotNull ResourcePackGenerator generator) {
        String id = Objects.requireNonNull(generator.id(), "generator.id()");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Resource-pack generator id must not be blank");
        }
        return id;
    }

    /**
     * Registers a resource pack generator.
     *
     * @param generator The resource pack generator to register.
     */
    @Override
    public void registerGenerator(@NotNull ResourcePackGenerator generator) {
        if (buildInProgress) {
            throw new IllegalStateException("Cannot register resource-pack generators during an active build");
        }

        String generatorId = requireGeneratorId(generator);
        if (generatorsById.containsKey(generatorId) || pendingGenerators.containsKey(generatorId)) {
            throw new IllegalArgumentException("Duplicate resource-pack generator id: " + generatorId);
        }

        List<String> missingGenerators = missingRequiredGenerators(generator);
        if (!missingGenerators.isEmpty()) {
            plugin.getLogger().warning(
                "[resource-pack] Delaying generator "
                    + generatorId
                    + " until required generators are loaded: "
                    + String.join(", ", missingGenerators)
                    + "."
            );
            pendingGenerators.put(generatorId, generator);
            return;
        }

        activateGenerator(generator);
        attemptPendingGeneratorRegistrations();
    }

    @Override
    public boolean hasGenerator(@NotNull String generatorId) {
        return generatorsById.containsKey(generatorId);
    }

    @Override
    public boolean hasGenerator(@NotNull Class<? extends ResourcePackGenerator> generatorType) {
        for (ResourcePackGenerator generator : generatorsByType.values()) {
            if (generatorType.isInstance(generator)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void unregisterGenerator(@NotNull String generatorId) {
        if (buildInProgress) {
            throw new IllegalStateException("Cannot unregister resource-pack generators during an active build");
        }

        ResourcePackGenerator pending = pendingGenerators.remove(generatorId);
        if (pending != null) {
            return;
        }

        ResourcePackGenerator active = generatorsById.remove(generatorId);
        if (active == null) {
            return;
        }

        generators.remove(active);
        generatorsByType.entrySet().removeIf(entry -> entry.getValue() == active);
        active.onUnload();
        recalculateSupportedVersionRange();
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
     * Ask an audience to download the resource pack.
     *
     * @param audience the audience to send the pack to.
     */
    public void sendPack(@NotNull Audience audience) {
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

        audience.sendResourcePacks(request);
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
        recalculateSupportedVersionRange();
        buildInProgress = true;
        try {
            generatePackInternal(statusCallback);
        } finally {
            buildInProgress = false;
        }
    }

    private void generatePackInternal(@Nullable Consumer<String> statusCallback) {
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

        if (plannedBuildSegments.isEmpty()) {
            api.messages().error("Failed to plan resource pack build segments");
            if (statusCallback != null) { statusCallback.accept("error"); }
            return;
        }

        if(statusCallback != null) { statusCallback.accept("generating"); }
        try {
            for (PlannedBuildSegment segment : plannedBuildSegments) {
                File segmentRoot = resolveSegmentRoot(tempPackDir, segment);
                buildSegment(segment, manifest, segmentRoot);
            }
        } catch (Exception e) {
            api.messages().error("Failed to generate resource pack", e);
            deleteDirectory(tempPackDir.toPath());
            if (statusCallback != null) { statusCallback.accept("error"); }
            return;
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

    private void buildSegment(@NotNull PlannedBuildSegment segment,
                              @NotNull ConfigSection manifest,
                              @NotNull File segmentRoot) {
        ResourcePackWriter writer = new SegmentResourcePackWriter(
            segmentRoot.toPath(),
            segment.supportedRange(),
            segment.overlayDirectory(),
            manifest
        );

        plugin.getLogger().info(
            "[resource-pack] Building segment '" + segmentLabel(segment) + "' with generators: "
                + segment.generators().stream().map(ResourcePackGenerator::id).toList()
        );

        for (ResourcePackGenerator generator : segment.generators()) {
            if (!generator.supports(segment.target())) {
                plugin.getLogger().info(
                    "[resource-pack] Skipping generator '" + generator.id()
                        + "' for unsupported target " + segment.target().minecraftVersion()
                        + " (pack format " + segment.target().packFormat() + ")."
                );
                continue;
            }

            try {
                generator.generate(new ResourcePackBuildContext(
                    segment.target(),
                    writer,
                    generatorConfig(generator.id())
                ));
            } catch (IOException e) {
                throw new ResourcePackGeneratorException(
                    "Failed generator '" + generator.id() + "' for segment '" + segmentLabel(segment) + "'",
                    e
                );
            }
        }
    }

    private @NotNull File resolveSegmentRoot(@NotNull File tempPackDir, @NotNull PlannedBuildSegment segment) {
        if (!segment.overlay()) {
            return tempPackDir;
        }

        File overlayRoot = tempPackDir.toPath()
            .resolve("overlays")
            .resolve(Objects.requireNonNull(segment.overlayDirectory()))
            .toFile();

        if (!overlayRoot.exists() && !overlayRoot.mkdirs() && !overlayRoot.exists()) {
            throw new ResourcePackGeneratorException("Failed to create overlay directory " + overlayRoot);
        }

        return overlayRoot;
    }

    private @NotNull String segmentLabel(@NotNull PlannedBuildSegment segment) {
        if (!segment.overlay()) {
            return "base:" + segment.supportedRange().minFormat() + "-" + segment.supportedRange().maxFormat();
        }

        return Objects.requireNonNull(segment.overlayDirectory()) + ":"
            + segment.supportedRange().minFormat() + "-" + segment.supportedRange().maxFormat();
    }

    /**
     * Returns the legacy service-level pack-format compatibility window as
     * {@code [minSupportedFormat, maxSupportedFormat]}.
     *
     * <p>This does not enumerate every explicit build target. Use
     * {@link #buildPlan()} for the full multi-target plan.</p>
     *
     * @return The min and max supported pack formats for the current service state.
     */
    @Override
    public int[] supportedVersion() {
        return new int[] { minSupportedVersion, maxSupportedVersion };
    }

    @Override
    public @NotNull PackFormatRange supportedRange() {
        return baseSupportedRange;
    }

    @Override
    public @NotNull List<ResourcePackBuildTarget> buildPlan() {
        return plannedBuildSegments.stream()
            .map(PlannedBuildSegment::target)
            .toList();
    }

    static int resolveResourcePackFormat(@Nullable int[] minecraftVersion) {
        int resolvedFormat = RESOURCE_PACK_FORMAT_VERSIONS.getFirst().formatVersion();
        if (minecraftVersion == null || minecraftVersion.length == 0) {
            return resolvedFormat;
        }

        for (ResourcePackFormatVersion version : RESOURCE_PACK_FORMAT_VERSIONS) {
            if (compareMinecraftVersions(minecraftVersion, version.minecraftVersion()) >= 0) {
                resolvedFormat = version.formatVersion();
            } else {
                break;
            }
        }

        return resolvedFormat;
    }

    static int[] resolveSupportedVersionRange(@Nullable int[] minecraftVersion) {
        return new int[] {
            RESOURCE_PACK_FORMAT_VERSIONS.getFirst().formatVersion(),
            resolveResourcePackFormat(minecraftVersion)
        };
    }

    static int[] resolveSupportedVersionRange(@Nullable int[] minecraftVersion,
                                              @Nullable ConfigSectionView config,
                                              @Nullable Consumer<String> warningCallback) {
        int[] defaultRange = resolveSupportedVersionRange(minecraftVersion);
        int minSupportedVersion = defaultRange[0];
        int maxSupportedVersion = defaultRange[1];

        if (config == null) {
            return defaultRange;
        }

        int configuredMinSupportedVersion = config.getInt("min_pack_format", minSupportedVersion);
        int configuredMaxSupportedVersion = config.getInt("max_pack_format", maxSupportedVersion);

        if (configuredMinSupportedVersion < RESOURCE_PACK_FORMAT_VERSIONS.getFirst().formatVersion()) {
            if (warningCallback != null) {
                warningCallback.accept(
                    "[resource-pack] Configured min_pack_format "
                        + configuredMinSupportedVersion
                        + " is below the earliest supported pack format "
                        + RESOURCE_PACK_FORMAT_VERSIONS.getFirst().formatVersion()
                        + ". Clamping to the earliest supported format."
                );
            }
            configuredMinSupportedVersion = RESOURCE_PACK_FORMAT_VERSIONS.getFirst().formatVersion();
        }

        if (configuredMinSupportedVersion > maxSupportedVersion) {
            if (warningCallback != null) {
                warningCallback.accept(
                    "[resource-pack] Configured min_pack_format "
                        + configuredMinSupportedVersion
                        + " is above the current Minecraft pack format "
                        + maxSupportedVersion
                        + ". Clamping to the current Minecraft pack format."
                );
            }
            configuredMinSupportedVersion = maxSupportedVersion;
        }

        if (configuredMaxSupportedVersion != maxSupportedVersion && warningCallback != null) {
            warningCallback.accept(
                "[resource-pack] Configured max_pack_format "
                    + configuredMaxSupportedVersion
                    + " does not match the current Minecraft pack format "
                    + maxSupportedVersion
                    + ". The service must support the running server version, so max_pack_format is clamped to "
                    + maxSupportedVersion
                    + "."
            );
        }

        return new int[] { configuredMinSupportedVersion, maxSupportedVersion };
    }

    private static boolean isMinecraftVersionBeyondKnownRange(@Nullable int[] minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.length == 0) {
            return false;
        }

        return compareMinecraftVersions(
            minecraftVersion,
            RESOURCE_PACK_FORMAT_VERSIONS.getLast().minecraftVersion()
        ) > 0;
    }

    private boolean supportsCurrentMinecraftFormat(@NotNull ResourcePackGenerator generator) {
        return generator.supports(currentBuildTarget());
    }

    private @Nullable PackFormatRange selectBaseRange(@NotNull ResourcePackGenerator generator,
                                                      @NotNull PackFormatRange currentBaseRange) {
        PackFormatRange supportedRange = generator.supportedFormats();
        if (!supportedRange.contains(currentMinecraftFormatVersion)) {
            return null;
        }

        return supportedRange.intersection(currentBaseRange);
    }

    private @NotNull List<String> missingRequiredGenerators(@NotNull ResourcePackGenerator generator) {
        List<String> requiredGenerators = generator.requiredGenerators();
        List<String> missingGenerators = new ArrayList<>();
        Set<String> loadedGeneratorIds = generators.stream()
            .map(ResourcePackGenerator::id)
            .collect(Collectors.toSet());

        for (String requiredGenerator : requiredGenerators) {
            if (!loadedGeneratorIds.contains(requiredGenerator)) {
                missingGenerators.add(requiredGenerator);
            }
        }
        return missingGenerators;
    }

    private void attemptPendingGeneratorRegistrations() {
        boolean progress;
        do {
            progress = false;
            Iterator<Map.Entry<String, ResourcePackGenerator>> iterator = pendingGenerators.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, ResourcePackGenerator> entry = iterator.next();
                ResourcePackGenerator generator = entry.getValue();
                if (!missingRequiredGenerators(generator).isEmpty()) {
                    continue;
                }

                iterator.remove();
                activateGenerator(generator);
                progress = true;
            }
        } while (progress);

        recalculateSupportedVersionRange();
    }

    private void logPendingGenerators() {
        for (ResourcePackGenerator generator : pendingGenerators.values()) {
            List<String> missingGenerators = missingRequiredGenerators(generator);

            if (!missingGenerators.isEmpty()) {
                plugin.getLogger().warning(
                    "[resource-pack] Generator "
                        + generator.id()
                        + " is still waiting on required generators: "
                        + String.join(", ", missingGenerators)
                        + "."
                );
            }
        }
    }

    private void recalculateSupportedVersionRange() {
        int[] supportedRange = resolveSupportedVersionRange(
            STEMCraft.getMinecraftVersion(),
            getConfig(),
            this::logSupportedRangeWarning
        );

        PackFormatRange nextBaseRange = new PackFormatRange(supportedRange[0], supportedRange[1]);

        for (ResourcePackGenerator generator : generators) {
            PackFormatRange generatorBaseRange = selectBaseRange(generator, nextBaseRange);
            if (generatorBaseRange == null) {
                plugin.getLogger().info(
                    "[resource-pack] Generator "
                        + generator.id()
                        + " does not support the current server pack format "
                        + currentMinecraftFormatVersion
                        + " and will be skipped for that target."
                        + "."
                );
                continue;
            }

            nextBaseRange = nextBaseRange.intersection(generatorBaseRange);
        }

        baseSupportedRange = nextBaseRange;
        minSupportedVersion = nextBaseRange.minFormat();
        maxSupportedVersion = nextBaseRange.maxFormat();
        plannedBuildSegments = planBuildSegments(nextBaseRange);
    }

    private void logSupportedRangeWarning(@NotNull String warning) {
        if (emittedSupportedRangeWarnings.add(warning)) {
            plugin.getLogger().warning(warning);
        }
    }

    private @NotNull List<PlannedBuildSegment> planBuildSegments(@NotNull PackFormatRange baseRange) {
        List<PlannedBuildSegment> segments = new ArrayList<>();
        segments.add(new PlannedBuildSegment(
            currentBuildTarget(),
            baseRange,
            null,
            resolveSegmentGenerators(baseRange)
        ));

        List<PackFormatRange> futureRanges = planFutureSegments(
            currentMinecraftFormatVersion,
            generators.stream().map(ResourcePackGenerator::supportedFormats).toList()
        );

        for (PackFormatRange futureRange : futureRanges) {
            List<ResourcePackGenerator> segmentGenerators = resolveSegmentGenerators(futureRange);
            if (segmentGenerators.isEmpty()) {
                continue;
            }

            segments.add(new PlannedBuildSegment(
                buildTarget(futureRange.maxFormat()),
                futureRange,
                overlayDirectoryName(futureRange),
                segmentGenerators
            ));
        }

        return List.copyOf(segments);
    }

    static @NotNull List<PackFormatRange> planFutureSegments(int currentFormat,
                                                             @NotNull List<PackFormatRange> generatorRanges) {
        SortedSet<Integer> boundaries = new TreeSet<>();

        for (PackFormatRange range : generatorRanges) {
            if (range.maxFormat() == Integer.MAX_VALUE) {
                continue;
            }

            int futureMin = Math.max(range.minFormat(), currentFormat + 1);
            if (futureMin > range.maxFormat()) {
                continue;
            }

            boundaries.add(futureMin);
            boundaries.add(range.maxFormat() + 1);
        }

        if (boundaries.size() < 2) {
            return List.of();
        }

        List<Integer> sortedBoundaries = new ArrayList<>(boundaries);
        List<PackFormatRange> ranges = new ArrayList<>();

        for (int i = 0; i < sortedBoundaries.size() - 1; i++) {
            int minFormat = sortedBoundaries.get(i);
            int maxFormat = sortedBoundaries.get(i + 1) - 1;
            if (minFormat > maxFormat) {
                continue;
            }

            PackFormatRange candidate = new PackFormatRange(minFormat, maxFormat);
            boolean covered = false;
            for (PackFormatRange generatorSupportedRange : generatorRanges) {
                if (supportsRange(generatorSupportedRange, candidate)) {
                    covered = true;
                    break;
                }
            }

            if (covered) {
                ranges.add(candidate);
            }
        }

        return List.copyOf(ranges);
    }

    private @NotNull List<ResourcePackGenerator> resolveSegmentGenerators(@NotNull PackFormatRange segmentRange) {
        List<ResourcePackGenerator> compatibleGenerators = new ArrayList<>();
        for (ResourcePackGenerator generator : generators) {
            if (supportsRange(generator.supportedFormats(), segmentRange)) {
                compatibleGenerators.add(generator);
            }
        }

        boolean changed;
        do {
            changed = false;
            Iterator<ResourcePackGenerator> iterator = compatibleGenerators.iterator();
            while (iterator.hasNext()) {
                ResourcePackGenerator generator = iterator.next();
                boolean dependenciesSatisfied = true;
                Set<String> compatibleIds = compatibleGenerators.stream()
                    .map(ResourcePackGenerator::id)
                    .collect(Collectors.toSet());
                for (String requiredGenerator : generator.requiredGenerators()) {
                    if (!compatibleIds.contains(requiredGenerator)) {
                        dependenciesSatisfied = false;
                        break;
                    }
                }

                if (!dependenciesSatisfied) {
                    iterator.remove();
                    changed = true;
                }
            }
        } while (changed);

        return List.copyOf(compatibleGenerators);
    }

    private static boolean supportsRange(@NotNull PackFormatRange supportedRange,
                                         @NotNull PackFormatRange targetRange) {
        return supportedRange.minFormat() <= targetRange.minFormat()
            && supportedRange.maxFormat() >= targetRange.maxFormat();
    }

    private static @NotNull String overlayDirectoryName(@NotNull PackFormatRange range) {
        return "overlay_" + range.minFormat() + "_" + range.maxFormat();
    }

    private @NotNull ResourcePackBuildTarget currentBuildTarget() {
        String minecraftVersion = Bukkit.getMinecraftVersion();
        if (minecraftVersion.isBlank()) {
            minecraftVersion = "pack-format-" + currentMinecraftFormatVersion;
        }
        return new ResourcePackBuildTarget(minecraftVersion, currentMinecraftFormatVersion);
    }

    private @NotNull ResourcePackBuildTarget buildTarget(int packFormat) {
        return new ResourcePackBuildTarget(resolveMinecraftVersionLabel(packFormat), packFormat);
    }

    private @NotNull String resolveMinecraftVersionLabel(int packFormat) {
        for (ResourcePackFormatVersion version : RESOURCE_PACK_FORMAT_VERSIONS) {
            if (version.formatVersion() == packFormat) {
                return formatMinecraftVersion(version.minecraftVersion());
            }
        }

        return "pack-format-" + packFormat;
    }

    private static @NotNull String formatMinecraftVersion(int @NotNull [] version) {
        return version[0] + "." + version[1] + "." + version[2];
    }

    private static int compareMinecraftVersions(int @NotNull [] left, int @NotNull [] right) {
        int maxLength = Math.max(left.length, right.length);
        for (int i = 0; i < maxLength; i++) {
            int leftValue = i < left.length ? left[i] : 0;
            int rightValue = i < right.length ? right[i] : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
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

        ConfigSectionView tokenSection = manifest.getSection("tokens");
        if (tokenSection == null) {
            return;
        }

        appliedManifestTokens.addAll(tokenSection.getKeys());
        for (String token : appliedManifestTokens) {
            String value = tokenSection.getString(token, "");
            if (!value.isBlank()) {
                api.messages().tokens().add(token, value);
            }
        }
        if (api.holograms() != null) {
            api.tasks().nextTick(() -> api.holograms().refreshDynamic());
        }
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted") // This function's output is reasonable, so why bother changing it?
    @Contract("null -> true")
    private static boolean deleteDirectory(Path root) {
        if (root == null || !Files.exists(root)) {
            return true;
        }

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
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

        List<BedrockCustomItemPackEntry> customItems = collectBedrockCustomItems(javaPackDir.toPath());
        ConfigSectionView tokens = manifest.getSection("tokens");
        ConfigSectionView tokenMeta = manifest.getSection("token-meta");
        boolean hasGlyphs = tokens != null && tokenMeta != null;

        if (!hasGlyphs && customItems.isEmpty()) {
            plugin.getLogger().info("[resource-pack] Bedrock pack '" + packName + "': no glyph or custom item metadata available");
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

        if (hasGlyphs) {
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
                    Path parent = dest.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
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

                int codepoint = parseUnicodeCodepoint(unicode);
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
        }

        for (BedrockCustomItemPackEntry customItem : customItems) {
            Path destination = bedrockPackDir.resolve("textures").resolve("items").resolve(customItem.icon() + ".png");
            try {
                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(customItem.textureSource(), destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException e) {
                plugin.getLogger().warning("[resource-pack] Failed to copy bedrock custom item '" + customItem.bedrockIdentifier() + "': " + e.getMessage());
                continue;
            }

            JsonObject texDef = new JsonObject();
            texDef.addProperty("textures", "textures/items/" + customItem.icon());
            textureData.add(customItem.icon(), texDef);
            signatureBuilder
                .append("|custom-item=").append(customItem.bedrockIdentifier())
                .append("|javaItem=").append(customItem.javaItemId())
                .append("|itemModel=").append(customItem.itemModelId())
                .append("|icon=").append(customItem.icon())
                .append("|hash=").append(FileUtil.sha1Hex(destination.toFile()));
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
            writeGeyserCustomItemMappings(config, customItems);
            writeBedrockZip(bedrockPackDir, bedrockZip);
            plugin.getLogger().info("[resource-pack] Bedrock pack '" + packName + "': " + copied + " mapped assets -> " + bedrockPackDir);
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

    private Path resolveBedrockCustomMappingsDir(ConfigSectionView config) {
        String raw = config.getString("bedrock.custom_items.mappings_dir", "../Geyser-Spigot/custom_mappings").trim();
        Path base = plugin.getDataFolder().toPath();
        Path resolved = Path.of(raw);
        if (!resolved.isAbsolute()) {
            resolved = base.resolve(raw).normalize();
        }
        return resolved;
    }

    private @NotNull List<BedrockCustomItemPackEntry> collectBedrockCustomItems(@NotNull Path javaPackRoot) {
        List<BedrockCustomItemPackEntry> entries = new ArrayList<>();
        for (dev.stemcraft.api.service.item.CustomItemDefinition definition : api.items().customItemDefinitions()) {
            if (definition.clients() == null || definition.clients().java() == null || definition.clients().bedrock() == null) {
                continue;
            }

            dev.stemcraft.api.service.item.JavaItemVisualDefinition java = definition.clients().java();
            dev.stemcraft.api.service.item.BedrockItemVisualDefinition bedrock = definition.clients().bedrock();
            String[] textureParts = splitNamespacedPath(bedrock.texturePath());
            Path source = javaPackRoot.resolve("assets").resolve(textureParts[0]).resolve("textures").resolve(textureParts[1] + ".png");
            if (!Files.exists(source) || !Files.isRegularFile(source)) {
                plugin.getLogger().warning("[resource-pack] Missing custom item texture for '" + definition.id() + "': " + source);
                continue;
            }

            entries.add(new BedrockCustomItemPackEntry(
                definition.template().getType().getKey().toString(),
                java.itemModelId(),
                bedrock.identifier(),
                bedrock.icon(),
                bedrock.displayName(),
                source
            ));
        }
        return entries;
    }

    private void writeGeyserCustomItemMappings(@NotNull ConfigSectionView config,
                                               @NotNull List<BedrockCustomItemPackEntry> customItems) throws IOException {
        if (customItems.isEmpty() || !config.getBoolean("bedrock.custom_items.enabled", true)) {
            return;
        }

        Path mappingsDir = resolveBedrockCustomMappingsDir(config);
        Files.createDirectories(mappingsDir);
        String fileName = config.getString("bedrock.custom_items.mappings_file", "stemcraft-custom-items.json").trim();
        if (fileName.isBlank()) {
            fileName = "stemcraft-custom-items.json";
        }

        JsonObject root = new JsonObject();
        root.addProperty("format_version", 2);
        JsonObject items = new JsonObject();
        root.add("items", items);

        Map<String, JsonArray> definitionsByJavaItem = new LinkedHashMap<>();
        for (BedrockCustomItemPackEntry entry : customItems) {
            JsonArray definitions = definitionsByJavaItem.computeIfAbsent(entry.javaItemId(), ignored -> new JsonArray());
            JsonObject definition = new JsonObject();
            definition.addProperty("type", "definition");
            definition.addProperty("model", entry.itemModelId());
            definition.addProperty("bedrock_identifier", entry.bedrockIdentifier());
            definition.addProperty("display_name", entry.displayName());
            JsonObject bedrockOptions = new JsonObject();
            bedrockOptions.addProperty("icon", entry.icon());
            definition.add("bedrock_options", bedrockOptions);
            definitions.add(definition);
        }

        for (Map.Entry<String, JsonArray> entry : definitionsByJavaItem.entrySet()) {
            items.add(entry.getKey(), entry.getValue());
        }

        Path target = mappingsDir.resolve(fileName);
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        plugin.getLogger().info("[resource-pack] Wrote Geyser custom item mappings: " + target);
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
        return hash.isBlank() ? "present" : hash;
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
                    + "). Using first pack icon from '" + matchingPacks.getFirst() + "'."
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

    private @NotNull String[] splitNamespacedPath(@NotNull String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Invalid namespaced path: " + value);
        }
        return new String[] {value.substring(0, separator), value.substring(separator + 1)};
    }

    private int parseUnicodeCodepoint(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }

        String normalized = value.trim();
        if (normalized.startsWith("\\u") && normalized.length() == 6) {
            try {
                return Integer.parseInt(normalized.substring(2), 16);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        if ((normalized.startsWith("u+") || normalized.startsWith("U+")) && normalized.length() > 2) {
            try {
                return Integer.parseInt(normalized.substring(2), 16);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        if (normalized.matches("^[0-9a-fA-F]{4,6}$")) {
            try {
                return Integer.parseInt(normalized, 16);
            } catch (NumberFormatException ignored) {
                return -1;
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
                int targetHeight = Math.clamp(
                    entry.getValue().bedrockHeight() > 0 ? entry.getValue().bedrockHeight() : entry.getValue().javaHeight(),
                    1,
                    cellSize
                );
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
            y = Math.clamp(y, minY, maxY);

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
        return Math.clamp(value, 0, 255);
    }

    private record SegmentResourcePackWriter(
        @NotNull Path root,
        @NotNull PackFormatRange supportedRange,
        @Nullable String overlayDirectory,
        @NotNull ConfigSection manifest
    ) implements ResourcePackWriter {
        @Override
        public boolean overlay() {
            return overlayDirectory != null && !overlayDirectory.isBlank();
        }

        @Override
        public @NotNull Path resolve(@NotNull String relativePath) {
            return root.resolve(relativePath);
        }

        @Override
        public void writeString(@NotNull String relativePath, @NotNull String content) throws IOException {
            Path target = resolve(relativePath);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }

        @Override
        public void copyFile(@NotNull Path source, @NotNull String relativePath) throws IOException {
            FileUtil.copyFile(source, resolve(relativePath));
        }

        @Override
        public void copyDirectory(@NotNull Path sourceDir, @NotNull String relativePath) throws IOException {
            FileUtil.copyDirectory(sourceDir, resolve(relativePath), true);
        }
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
