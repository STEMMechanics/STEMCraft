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

package dev.stemcraft.config;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import java.util.Set;

import java.io.File;
import java.io.IOException;

public class ConfigFileImpl extends ConfigSectionImpl implements ConfigFile {
    @Getter
    private String name;
    @Getter
    private boolean dirty;
    private File file;
    private YamlConfiguration config;
    @Getter
    @Setter
    private boolean autoSave = false;
    @Setter
    private boolean saveDefaults = true;

    /**
     * Loads the configuration file with the given name.
     */
    public boolean load(String name, boolean createIfNotExist) {
        if (!name.contains(".")) {
            name += ".yml";
        }

        this.name = name;
        this.dirty = false;

        file = new File(STEMCraftAPI.api().getDataFolder(), name);

        if (!file.exists()) {
            if (!createIfNotExist) {
                this.file = null;
                return false;
            }

            try {
                if (!file.createNewFile()) {
                    this.file = null;
                    return false;
                }
            } catch (IOException e) {
                this.file = null;
                return false;
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        super.setConfigFile(this);
        // Root section should be the loaded YAML itself (not the defaults section)
        super.setSection(this.config);
        return true;
    }

    /**
     * Checks if the configuration file exists.
     */
    public boolean exists() {
        return file != null && file.exists();
    }

    /**
     * Marks the configuration file as dirty (modified).
     */
    public void setDirty() {
        this.dirty = true;
    }

    /**
     * Saves the configuration file.
     */
    public void save(boolean pruneEmptySections) {
        if (file == null || !dirty) return;

        try {
            if(pruneEmptySections) {
                pruneEmptySections(config);
            }

            config.save(file);
            dirty = false;
        } catch (IOException e) {
            STEMCraftAPI.api().error("Could not save config file: " + name, e);
        }
    }

    /**
     * Saves the configuration file under a new name.
     */
    public ConfigFile saveAs(String name) {
        if (file == null) return this;

        File newFile = new File(STEMCraftAPI.api().getDataFolder(), name);
        try {
            config.save(newFile);
            return this;
        } catch (IOException e) {
            STEMCraftAPI.api().error("Could not save config file as: " + name, e);
            return this;
        }
    }

    /**
     * Gets whether the configuration file should save default values.
     */
    @Override
    public boolean getSaveDefaults() {
        return saveDefaults;
    }

    private static void pruneEmptySections(ConfigurationSection section) {
        Set<String> keys = section.getKeys(false);
        for (String key : keys) {
            if (!section.isConfigurationSection(key)) {
                continue;
            }

            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }

            pruneEmptySections(child);

            // After pruning children, remove this section if it's now empty
            if (child.getKeys(false).isEmpty() && child.getValues(false).isEmpty()) {
                section.set(key, null);
            }
        }
    }
}
