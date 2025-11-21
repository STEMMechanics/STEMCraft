package dev.stemcraft.api.utils;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.command.CommandSender;

public class STEMCraftUtil {
    public void onLoad() { }

    static void log(CommandSender sender, String message, Throwable ex, String... placeholders) { STEMCraftAPI.api().messenger().log(sender, message, ex, placeholders); }
    static void info(CommandSender sender, String message, Throwable ex, String... placeholders) { STEMCraftAPI.api().messenger().info(sender, message, ex, placeholders); }
    static void warn(CommandSender sender, String message, Throwable ex, String... placeholders) { STEMCraftAPI.api().messenger().warn(sender, message, ex, placeholders); }
    static void error(CommandSender sender, String message, Throwable ex, String... placeholders) { STEMCraftAPI.api().messenger().error(sender, message, ex, placeholders); }
    static void success(CommandSender sender, String message, Throwable ex, String... placeholders) { STEMCraftAPI.api().messenger().log(sender, message, ex, placeholders); }

    static void log(String message, String... placeholders) { log(null, message, null, placeholders); }
    static void log(String message, Throwable ex, String... placeholders) { log(null, message, ex, placeholders); }
    static void log(CommandSender sender, String message, String... placeholders) { log(sender, message, null, placeholders); }
    static void info(String message, String... placeholders) { info(null, message, null, placeholders); }
    static void info(String message, Throwable ex, String... placeholders) { info(null, message, ex, placeholders); }
    static void info(CommandSender sender, String message, String... placeholders) { info(sender, message, null, placeholders); }
    static void warn(String message, String... placeholders) { warn(null, message, null, placeholders); }
    static void warn(String message, Throwable ex, String... placeholders) { warn(null, message, ex, placeholders); }
    static void warn(CommandSender sender, String message, String... placeholders) { warn(sender, message, null, placeholders); }
    static void error(String message, String... placeholders) { error(null, message, null, placeholders); }
    static void error(String message, Throwable ex, String... placeholders) { error(null, message, ex, placeholders); }
    static void error(CommandSender sender, String message, String... placeholders) { error(sender, message, null, placeholders); }
    static void success(String message, String... placeholders) { success(null, message, null, placeholders); }
    static void success(String message, Throwable ex, String... placeholders) { success(null, message, ex, placeholders); }
    static void success(CommandSender sender, String message, String... placeholders) { success(sender, message, null, placeholders); }
}
