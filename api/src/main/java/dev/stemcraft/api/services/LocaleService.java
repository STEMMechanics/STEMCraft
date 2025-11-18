package dev.stemcraft.api.services;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface LocaleService {
    Component get(CommandSender sender, String key, String... args);
    Component get(CommandSender sender, String key);
    Component get(Player player, String key, String... args);
    Component get(Player player, String key);
    Component get(String key, String... args);
    Component get(String key);

    /**
     * Reload locale files from disk.
     */
    void reload();
}