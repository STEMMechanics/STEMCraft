package dev.stemcraft.config;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.YamlConfiguration;

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
        this.name = name;
        this.dirty = false;

        file = new File(STEMCraftAPI.api().getDataFolder(), name);
        if (!file.exists()) {
            if (createIfNotExist) {
                try {
                    if (file.createNewFile()) {
                        this.config = YamlConfiguration.loadConfiguration(file);
                        super.setConfigFile(this);
                        super.setSection(this.config.getDefaultSection());

                        return true;
                    }
                } catch (IOException e) {
                    return false;
                }
            }
        }

        this.file = null;
        return false;
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
    public void save() {
        if (file == null || !dirty) return;

        try {
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
}
