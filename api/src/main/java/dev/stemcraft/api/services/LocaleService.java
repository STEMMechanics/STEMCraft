package dev.stemcraft.api.services;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface LocaleService extends STEMCraftService {
    Component getComponent(CommandSender sender, String key, String... args);
    String getString(CommandSender sender, String key, String... args);
    Component getComponent(CommandSender sender, String key);
    String getString(CommandSender sender, String key);
    Component getComponent(Player player, String key, String... args);
    String getString(Player player, String key, String... args);
    Component getComponent(Player player, String key);
    String getString(Player player, String key);
    Component getComponent(String key, String... args);
    String getString(String key, String... args);
    Component getComponent(String key);
    String getString(String key);

    /**
     * Reload locale files from disk.
     */
    void reload();
}