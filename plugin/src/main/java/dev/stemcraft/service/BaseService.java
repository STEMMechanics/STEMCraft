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
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.config.BundledConfigDefaults;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Base class for STEMCraft services.
 */
public abstract class BaseService {
    protected final STEMCraft plugin;
    protected final STEMCraftAPI api;
    private ConfigSection configSection;
    private ConfigFile rootConfigSection;
    private String configKey;
    private String resolvedConfigPath;


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
     * Constructor for BaseService.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     * @param configKey The service config key.
     */
    protected BaseService(STEMCraft plugin, STEMCraftAPI api, String configKey) {
        this.plugin = plugin;
        this.api = api;
        setConfigKey(configKey);
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
     * Called when the service should refresh configuration-backed runtime state.
     */
    public void onReload() {
        resetConfigCache();
    }

    /**
     * Get the id of this feature.
     *
     * @return The feature id.
     */
    public String getId() {
        String name = getClass().getSimpleName();

        if (name.endsWith("Impl")) {
            name = name.substring(0, name.length() - "Impl".length());
        }
        if (name.endsWith("Service")) {
            name = name.substring(0, name.length() - "Service".length());
        }

        return name;
    }

    /**
     * Set the configuration key for this service.
     *
     * @param configKey The configuration key.
     */
    protected final void setConfigKey(String configKey) {
        this.configKey = StringUtil.camelToKebab(configKey);
    }

    /**
     * Returns the config path candidates for this service in priority order.
     *
     * @return Candidate config paths.
     */
    protected List<String> getConfigPathCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        if (configKey != null && !configKey.isBlank()) {
            addConfigPathVariants(candidates, configKey);
        } else {
            String kebab = StringUtil.camelToKebab(getId());
            String snake = kebab.replace('-', '_');

            candidates.add(kebab);
            candidates.add(snake);
            candidates.add(StringUtil.toPlural(kebab));
            candidates.add(StringUtil.toPlural(snake));
        }

        // Backward compatibility for legacy auto-generated *-services paths.
        String legacyName = getClass().getSimpleName();
        if (legacyName.endsWith("Impl")) {
            legacyName = legacyName.substring(0, legacyName.length() - "Impl".length());
        }

        String legacyKebab = StringUtil.camelToKebab(legacyName);
        String legacySnake = legacyKebab.replace('-', '_');
        candidates.add(StringUtil.toPlural(legacyKebab));
        candidates.add(StringUtil.toPlural(legacySnake));

        return new ArrayList<>(candidates);
    }

    private void addConfigPathVariants(LinkedHashSet<String> candidates, String path) {
        candidates.add(path);

        if (path.contains(".")) {
            return;
        }

        candidates.add(path.replace('_', '-'));
        candidates.add(path.replace('-', '_'));
    }

    /**
     * Returns the config path actually resolved for this service.
     *
     * @return The resolved config path.
     */
    public String getResolvedConfigPath() {
        if (resolvedConfigPath == null) {
            resolvedConfigPath = getConfigPathCandidates().getFirst();
        }
        return resolvedConfigPath;
    }

    /**
     * Get the root configuration section of the plugin's config.
     *
     * @return The root ConfigSection.
     */
    protected final ConfigFile getRootConfigSection() {
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
            ConfigFile root = getRootConfigSection();
            List<String> candidates = getConfigPathCandidates();
            for (String path : candidates) {
                if (!root.isSection(path)) {
                    continue;
                }
                configSection = root.getSection(path, false);
                resolvedConfigPath = path;
                break;
            }

            if (configSection == null) {
                resolvedConfigPath = BundledConfigDefaults.restoreMissingSection(plugin, root, candidates);
            }

            if (configSection == null && resolvedConfigPath != null && root.isSection(resolvedConfigPath)) {
                configSection = root.getSection(resolvedConfigPath, false);
            }

            if (configSection == null) {
                resolvedConfigPath = candidates.getFirst();
                configSection = root.getSection(resolvedConfigPath);
            }
        }

        return configSection;
    }

    /**
     * Clears cached config handles so future lookups see updated disk state.
     */
    protected final void resetConfigCache() {
        configSection = null;
        rootConfigSection = null;
        resolvedConfigPath = null;
    }

    /**
     * Save the configuration section for this feature.
     */
    public void saveConfig() {
        getConfigSection().save();
    }
}
