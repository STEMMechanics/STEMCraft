/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.StringUtil;

public abstract class BaseFeature {

    protected final STEMCraftAPI api;
    private ConfigSection configSection;
    private ConfigSection rootConfigSection;

    /**
     * Constructor for BaseFeature.
     */
    public BaseFeature(STEMCraftAPI api) {
        this.api = api;
    }

    /**
     * Called when the feature is being enabled.
     */
    public void onEnable() { }

    /**
     * Called when the feature is being disabled.
     */
    public void onDisable() { }

    /**
     * Get the name of this feature.
     */
    public String getId() {
        String name = getClass().getSimpleName();

        if (name.endsWith("Feature")) {
            return name.substring(0, name.length() - "Feature".length());
        }
        if (name.endsWith("Command")) {
            return name.substring(0, name.length() - "Command".length());
        }

        return name;
    }

    /**
     * Get the configuration section for this feature.
     */
    public ConfigSection getConfigSection() {
        if(configSection == null) {
            ConfigFile config = api.config().load("config.yml");
            if(config == null) {
                throw new IllegalStateException("Could not load config.yml");
            }

            configSection = config.getSection(StringUtil.camelToKebab(getId()));
        }

        return configSection;
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
}
