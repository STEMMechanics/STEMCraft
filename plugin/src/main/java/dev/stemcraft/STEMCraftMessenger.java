package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class STEMCraftMessenger {
    public void debug(String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messenger().debug(message, ex, placeholders);
    }

    public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messenger().log(sender, message, ex, placeholders);
    }

    public void plain(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messenger().plain(sender, message, ex, placeholders);
    }

    public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messenger().info(sender, message, ex, placeholders);
    }

    public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messenger().warn(sender, message, ex, placeholders);
    }

    public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messenger().error(sender, message, ex, placeholders);
    }

    public void success(CommandSender sender, String message, Throwable ex, Object... placeholders)  {
        STEMCraftAPI.api().messenger().success(sender, message, ex, placeholders);
    }

    public void broadcast(String message, List<Player> exclude, Object... placeholders)  {
        STEMCraftAPI.api().messenger().broadcast(message, exclude, placeholders);
    }
}
