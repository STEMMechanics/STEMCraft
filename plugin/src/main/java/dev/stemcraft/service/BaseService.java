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

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.StringUtil;

/**
 * Base class for STEMCraft services.
 */
public abstract class BaseService {
    protected final STEMCraft plugin;
    protected final STEMCraftAPI api;
    private ConfigSection configSection;
    private ConfigSection rootConfigSection;
    private String configKey;


    /**
     * Constructor for BaseService.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
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
     *
     * @return The feature id.
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
     *
     * @param configKey The configuration key.
     */
    public void setConfigKey(String configKey) {
        this.configKey = StringUtil.camelToKebab(configKey);
    }

    /**
     * Get the root configuration section of the plugin's config.
     *
     * @return The root ConfigSection.
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
     *
     * @return The ConfigSection for this feature.
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