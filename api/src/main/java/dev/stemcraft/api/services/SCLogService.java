package dev.stemcraft.api.services;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface SCLogService {

    // console only

    void log(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    void success(String message);

    // to any CommandSender (player, console, command block, etc.)

    void log(CommandSender sender, String message);

    void info(CommandSender sender, String message);

    void warn(CommandSender sender, String message);

    void error(CommandSender sender, String message);

    void success(CommandSender sender, String message);

    // convenience overloads for Player

    default void log(Player player, String message) {
        log((CommandSender) player, message);
    }

    default void info(Player player, String message) {
        info((CommandSender) player, message);
    }

    default void warn(Player player, String message) {
        warn((CommandSender) player, message);
    }

    default void error(Player player, String message) {
        error((CommandSender) player, message);
    }

    default void success(Player player, String message) {
        success((CommandSender) player, message);
    }
}