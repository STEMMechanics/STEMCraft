package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.StringUtil;

public abstract class BaseService {
    protected final STEMCraft plugin;
    protected final STEMCraftAPI api;
    private ConfigSection configSection;
    private ConfigSection rootConfigSection;
    private String configKey;


    /**
     * Constructor for BaseService.
     */
    protected BaseService(STEMCraft plugin, STEMCraftAPI api) {
        this.plugin = plugin;
        this.api = api;
    }

    /**
     * Called when the service is enabled.
     */
    public void onEnable() {}

    /**
     * Called when the service is disabled.
     */
    public void onDisable() {}

    /**
     * Get the id of this feature.
     */
    public String getId() {
        String name = getClass().getSimpleName();

        if (name.endsWith("Impl")) {
            return name.substring(0, name.length() - "Impl".length());
        }
        if (name.endsWith("Service")) {
            return name.substring(0, name.length() - "Service".length());
        }

        return name;
    }

    /**
     * Set the configuration key for this service.
     */
    public void setConfigKey(String configKey) {
        this.configKey = StringUtil.camelToKebab(configKey);
    }

    /**
     * Get the root configuration section of the plugin's config.
     */
    public ConfigSection getRootConfigSection() {
        if(rootConfigSection == null) {
            rootConfigSection = api.config().load("config.yml");
            if (rootConfigSection == null) {
                throw new IllegalStateException("Could not load config.yml");
            }
        }

        return rootConfigSection;
    }

    /**
     * Get the configuration section for this service from the plugin's config.
     */
    public ConfigSection getConfigSection() {
        if(configSection == null) {
            ConfigSection root = getRootConfigSection();

            if(configKey == null) {
                configKey = StringUtil.camelToKebab(getId());
                configKey = StringUtil.toPlural(configKey);
            }

            configSection = root.getSection(configKey);
        }

        return configSection;
    }

    /**
     * Save the configuration section for this feature.
     */
    public void saveConfig() {
        getConfigSection().save();
    }
}