package dev.stemcraft.api.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;

public interface WorldBaseSetting {

    /**
     * Returns the unique key for this setting.
     */
    String key();

    /**
     * Called when the setting is enabled.
     */
    void onEnable(STEMCraftAPI api, WorldService service);

    /**
     * Called when the setting is disabled.
     */
    default void onDisable() {}

    /**
     * Called when a world is loaded.
     */
    default void onWorldLoad(World world, ConfigSection config) {}

    /**
     * Called when a world is unloaded.
     */
    default void onWorldUnload(World world, ConfigSection config) {}

    /**
     * Called when a world is deleted.
     */
    default void onWorldDeleted(String worldName, ConfigSection config) {}

    /**
     * Returns a list of tab completions for this setting.
     */
    List<String[]> tabCompletions();

    /**
     * Called when the command for this setting is executed.
     */
    void onCommand(CommandContext ctx, ConfigSection config, World world);

    /**
     * Return the value of this setting for the given world from the config.
     */
    default String get(World world, ConfigSection config) {
        return config.getString(key(), "unset").toLowerCase(Locale.ROOT);
    }

    /**
     * Set the value of this setting for the given world in the config.
     */
    void set(World world, ConfigSection config, String value);
}
