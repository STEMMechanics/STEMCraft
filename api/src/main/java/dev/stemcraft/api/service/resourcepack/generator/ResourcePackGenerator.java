package dev.stemcraft.api.service.resourcepack.generator;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;

import java.io.File;

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
     * @param manifest         The manifest configuration section.
     * @param resourcePackDir  The output directory for resource pack files.
     */
    public void buildStart(ConfigSection manifest, File resourcePackDir) { }

    /**
     * Called at the end of the build process.
     *
     * @param manifest         The manifest configuration section.
     * @param resourcePackDir  The output directory for resource pack files.
     */
    @SuppressWarnings("EmptyMethod")
    public void buildEnd(ConfigSection manifest, File resourcePackDir) { }

    /**
     * Called to build the resource pack from existing data pack files.
     *
     * @param dataPackDir      The output directory for data pack files.
     * @param resourcePackDir  The output directory for resource pack files.
     * @param manifest         The manifest configuration section.
     */
    public void buildFromDataPack(File dataPackDir, ConfigSection manifest, File resourcePackDir) { }

    /**
     * Called to build the resource pack based on data pack configuration.
     *
     * @param dataPackDir      The output directory for data pack files.
     * @param resourcePackDir  The output directory for resource pack files.
     * @param namespace        The namespace to use for generated assets.
     * @param config           The configuration section for generator settings.
     * @param manifest         The manifest configuration section.
     */
    public void buildFromDataPackConfig(String namespace, ConfigSection config, File dataPackDir, ConfigSection manifest, File resourcePackDir) { }

    /**
     * Applies additional modifications to the resource pack based on the manifest.
     *
     * @param namespace The namespace to use for generated assets.
     * @param manifest  The manifest configuration section.
     */
    public void apply(String namespace, ConfigSectionView manifest) { }
}
