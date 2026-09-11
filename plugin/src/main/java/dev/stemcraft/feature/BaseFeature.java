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

package dev.stemcraft.feature;

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
 * Base class for all features in the STEMCraft plugin.
 */
public abstract class BaseFeature {
    protected final STEMCraftAPI api;
    private ConfigSection configSection;
    private ConfigFile rootConfigSection;
    private String resolvedConfigPath;

    /**
     * Constructor for BaseFeature.
     *
     * @param api The STEMCraft API instance.
     */
    public BaseFeature(STEMCraftAPI api) {
        this.api = api;
    }

    /**
     * Called when the feature is being enabled.
     */
    public void onEnable() { }

    /**
     * Called when the feature should refresh its configuration-backed runtime state.
     */
    public void onReload() {
        resetConfigCache();
    }

    /** Called when all pending feature state should be persisted without disabling it. */
    public void onSave() { }

    /**
     * Called when the feature is being disabled.
     */
    public void onDisable() { }

    /**
     * Get the name of this feature.
     *
     * @return The feature name.
     */
    public String id() {
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
     * Returns the config path candidates for this feature in priority order.
     *
     * @return Candidate config paths.
     */
    protected List<String> getConfigPathCandidates() {
        String kebab = StringUtil.camelToKebab(id());
        String snake = kebab.replace('-', '_');

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(kebab);
        candidates.add(snake);
        candidates.add("features." + kebab);
        candidates.add("features." + snake);

        return new ArrayList<>(candidates);
    }

    /**
     * Returns the config path actually resolved for this feature.
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
     * Get the configuration section for this feature.
     *
     * @return The feature's configuration section.
     */
    protected ConfigSection getConfigSection() {
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
                resolvedConfigPath = BundledConfigDefaults.restoreMissingSection(STEMCraft.getPlugin(), root, candidates);
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
     * Get the root configuration section of the plugin's config.
     *
     * @return The root configuration section.
     */
    protected ConfigFile getRootConfigSection() {
        if(rootConfigSection == null) {
            rootConfigSection = api.config().load("config.yml");
            if (rootConfigSection == null) {
                throw new IllegalStateException("Could not load config.yml");
            }
        }

        return rootConfigSection;
    }

    /**
     * Clears cached config handles so future lookups see updated disk state.
     */
    protected void resetConfigCache() {
        configSection = null;
        rootConfigSection = null;
        resolvedConfigPath = null;
    }

    /**
     * Check if the feature is enabled.
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean isEnabled() {
        return getConfigSection().getBoolean("enabled", true);
    }
}
