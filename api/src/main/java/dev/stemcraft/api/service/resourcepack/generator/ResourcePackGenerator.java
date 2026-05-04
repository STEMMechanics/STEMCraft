package dev.stemcraft.api.service.resourcepack.generator;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;

import java.io.File;
import java.util.List;

public abstract class ResourcePackGenerator {
    protected final STEMCraftAPI api;
    protected final ResourcePackService service;

    /**
     * Constructor for ResourcePackGenerator.
     */
    public ResourcePackGenerator(STEMCraftAPI api, ResourcePackService service) {
        this.api = api;
        this.service = service;
    }

    /**
     * Called when the generator is loaded.
     *
     * @param config The configuration section for the generator.
     * @return true if the generator loaded successfully, false otherwise.
     */
    @SuppressWarnings("SameReturnValue")
    public boolean onLoad(ConfigSectionView config) { return true; }

    /**
     * Called at the start of the build process.
     *
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The output directory for resource pack files.
     */
    public void buildStart(ConfigSection manifest, File resourcePackDir) { }

    /**
     * Called at the start of one build segment.
     *
     * @param context The build-segment context.
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The output directory for segment files.
     */
    public void buildStart(ResourcePackBuildContext context, ConfigSection manifest, File resourcePackDir) {
        buildStart(manifest, resourcePackDir);
    }

    /**
     * Called at the end of the build process.
     *
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The output directory for resource pack files.
     */
    @SuppressWarnings("EmptyMethod")
    public void buildEnd(ConfigSection manifest, File resourcePackDir) { }

    /**
     * Called at the end of one build segment.
     *
     * @param context The build-segment context.
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The output directory for segment files.
     */
    public void buildEnd(ResourcePackBuildContext context, ConfigSection manifest, File resourcePackDir) {
        buildEnd(manifest, resourcePackDir);
    }

    /**
     * Called to build the resource pack from existing data pack files.
     *
     * @param dataPackDir The output directory for data pack files.
     * @param resourcePackDir The output directory for resource pack files.
     * @param manifest The manifest configuration section.
     */
    public void buildFromDataPack(File dataPackDir, ConfigSection manifest, File resourcePackDir) { }

    /**
     * Called to build one resource-pack segment from existing data pack files.
     *
     * @param context The build-segment context.
     * @param dataPackDir The output directory for data pack files.
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The output directory for segment files.
     */
    public void buildFromDataPack(ResourcePackBuildContext context,
                                  File dataPackDir,
                                  ConfigSection manifest,
                                  File resourcePackDir) {
        buildFromDataPack(dataPackDir, manifest, resourcePackDir);
    }

    /**
     * Called to build the resource pack based on data pack configuration.
     *
     * @param dataPackDir The output directory for data pack files.
     * @param resourcePackDir The output directory for resource pack files.
     * @param namespace The namespace to use for generated assets.
     * @param config The configuration section for generator settings.
     * @param manifest The manifest configuration section.
     */
    public void buildFromDataPackConfig(String namespace, ConfigSection config, File dataPackDir, ConfigSection manifest, File resourcePackDir) { }

    /**
     * Called to build one resource-pack segment based on data pack configuration.
     *
     * @param context The build-segment context.
     * @param namespace The namespace to use for generated assets.
     * @param config The configuration section for generator settings.
     * @param dataPackDir The output directory for data pack files.
     * @param manifest The manifest configuration section.
     * @param resourcePackDir The output directory for segment files.
     */
    public void buildFromDataPackConfig(ResourcePackBuildContext context,
                                        String namespace,
                                        ConfigSection config,
                                        File dataPackDir,
                                        ConfigSection manifest,
                                        File resourcePackDir) {
        buildFromDataPackConfig(namespace, config, dataPackDir, manifest, resourcePackDir);
    }

    /**
     * Applies additional modifications to the resource pack based on the manifest.
     *
     * @param namespace The namespace to use for generated assets.
     * @param manifest The manifest configuration section.
     */
    public void apply(String namespace, ConfigSectionView manifest) { }

    /**
     * Returns the supported Minecraft version range for this generator.
     *
     * @return The supported Minecraft version range.
     */
    public int[] supportedVersion() { return new int[] { 0, 0 }; }

    /**
     * Returns the supported resource-pack format ranges for this generator.
     *
     * @return The supported format ranges.
     */
    public List<PackFormatRange> supportedRanges() {
        int[] supportedVersion = supportedVersion();
        int[] currentSupportedVersion = currentSupportedVersion();

        int minFormat = supportedVersion.length > 0 && supportedVersion[0] > 0
            ? supportedVersion[0]
            : currentSupportedVersion[0];
        int maxFormat = supportedVersion.length > 1 && supportedVersion[1] > 0
            ? supportedVersion[1]
            : currentSupportedVersion[1];

        return List.of(new PackFormatRange(minFormat, maxFormat));
    }

    /**
     * Returns the generator classes that must already be loaded before this
     * generator can be enabled.
     *
     * @return The required generator classes.
     */
    public List<Class<? extends ResourcePackGenerator>> requiredGenerators() {
        return List.of();
    }

    /**
     * Returns the currently supported resource-pack format range for the service.
     *
     * @return The supported resource-pack format range.
     */
    protected final int[] currentSupportedVersion() {
        return service.supportedVersion();
    }

    /**
     * Checks whether another resource-pack generator is loaded.
     *
     * @param generatorType The generator type to check.
     * @return true if the generator is loaded, false otherwise.
     */
    protected final boolean hasGenerator(Class<? extends ResourcePackGenerator> generatorType) {
        return service.hasGenerator(generatorType);
    }
}
