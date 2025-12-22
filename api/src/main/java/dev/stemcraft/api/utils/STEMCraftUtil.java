package dev.stemcraft.api.utils;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.command.CommandSender;

public class STEMCraftUtil {
    public void onLoad() { }

    static void log(CommandSender sender, String message, Throwable ex, Object... placeholders) { STEMCraftAPI.api().messenger().log(sender, message, ex, placeholders); }
    static void info(CommandSender sender, String message, Throwable ex, Object... placeholders) { STEMCraftAPI.api().messenger().info(sender, message, ex, placeholders); }
    static void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) { STEMCraftAPI.api().messenger().warn(sender, message, ex, placeholders); }
    static void error(CommandSender sender, String message, Throwable ex, Object... placeholders) { STEMCraftAPI.api().messenger().error(sender, message, ex, placeholders); }
    static void success(CommandSender sender, String message, Throwable ex, Object... placeholders) { STEMCraftAPI.api().messenger().log(sender, message, ex, placeholders); }

    static void log(String message, Object... placeholders) { log(null, message, null, placeholders); }
    static void log(String message, Throwable ex, Object... placeholders) { log(null, message, ex, placeholders); }
    static void log(CommandSender sender, String message, Object... placeholders) { log(sender, message, null, placeholders); }
    static void info(String message, Object... placeholders) { info(null, message, null, placeholders); }
    static void info(String message, Throwable ex, Object... placeholders) { info(null, message, ex, placeholders); }
    static void info(CommandSender sender, String message, Object... placeholders) { info(sender, message, null, placeholders); }
    static void warn(String message, Object... placeholders) { warn(null, message, null, placeholders); }
    static void warn(String message, Throwable ex, Object... placeholders) { warn(null, message, ex, placeholders); }
    static void warn(CommandSender sender, String message, Object... placeholders) { warn(sender, message, null, placeholders); }
    static void error(String message, Object... placeholders) { error(null, message, null, placeholders); }
    static void error(String message, Throwable ex, Object... placeholders) { error(null, message, ex, placeholders); }
    static void error(CommandSender sender, String message, Object... placeholders) { error(sender, message, null, placeholders); }
    static void success(String message, Object... placeholders) { success(null, message, null, placeholders); }
    static void success(String message, Throwable ex, Object... placeholders) { success(null, message, ex, placeholders); }
    static void success(CommandSender sender, String message, Object... placeholders) { success(sender, message, null, placeholders); }
}
