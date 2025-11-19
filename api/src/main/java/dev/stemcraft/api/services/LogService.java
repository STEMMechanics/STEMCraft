package dev.stemcraft.api.services;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface LogService extends STEMCraftService {

    // console only

    void log(String message);
    void log(Component message);
    void log(String message, Throwable ex);

    void info(String message);
    void info(Component message);

    void warn(String message);
    void warn(Component message);
    void warn(String message, Throwable ex);

    void error(String message);
    void error(Component message);
    void error(String message, Throwable ex);

    void success(String message);
    void success(Component message);

    // to any CommandSender (player, console, command block, etc.)

    void log(CommandSender sender, String message);
    void log(CommandSender sender, Component message);

    void info(CommandSender sender, String message);
    void info(CommandSender sender, Component message);

    void warn(CommandSender sender, String message);
    void warn(CommandSender sender, Component message);

    void error(CommandSender sender, String message);
    void error(CommandSender sender, Component message);

    void success(CommandSender sender, String message);
    void success(CommandSender sender, Component message);

    // convenience overloads for Player

    default void log(Player player, String message) {
        log((CommandSender) player, message);
    }
    default void log(Player player, Component message) {
        log((CommandSender) player, message);
    }

    default void info(Player player, String message) {
        info((CommandSender) player, message);
    }
    default void info(Player player, Component message) { info((CommandSender) player, message); }

    default void warn(Player player, String message) {
        warn((CommandSender) player, message);
    }
    default void warn(Player player, Component message) { warn((CommandSender) player, message); }

    default void error(Player player, String message) {
        error((CommandSender) player, message);
    }
    default void error(Player player, Component message) { error((CommandSender) player, message); }

    default void success(Player player, String message) {
        success((CommandSender) player, message);
    }
    default void success(Player player, Component message) { success((CommandSender) player, message); }
}