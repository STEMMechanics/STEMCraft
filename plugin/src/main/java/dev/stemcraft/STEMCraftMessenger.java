package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.command.CommandSender;

public class STEMCraftMessenger {
    public void log(CommandSender sender, String message, Throwable ex, String... placeholders) {
        STEMCraftAPI.api().messenger().log(sender, message, ex, placeholders);
    }

    public void info(CommandSender sender, String message, Throwable ex, String... placeholders) {
        STEMCraftAPI.api().messenger().info(sender, message, ex, placeholders);
    }

    public void warn(CommandSender sender, String message, Throwable ex, String... placeholders) {
        STEMCraftAPI.api().messenger().warn(sender, message, ex, placeholders);
    }

    public void error(CommandSender sender, String message, Throwable ex, String... placeholders) {
        STEMCraftAPI.api().messenger().error(sender, message, ex, placeholders);
    }

    public void success(CommandSender sender, String message, Throwable ex, String... placeholders)  {
        STEMCraftAPI.api().messenger().success(sender, message, ex, placeholders);
    }

}
