package dev.stemcraft.capability;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.capability.HasMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HasMessagesImpl implements HasMessages {
    public void debug(String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().debug(message, ex, placeholders);
    }

    public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().log(sender, message, ex, placeholders);
    }

    public void send(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().send(sender, message, ex, placeholders);
    }

    public void send(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().send(sender, message, ex, placeholders));
    }

    public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().info(sender, message, ex, placeholders);
    }

    public void info(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().info(sender, message, ex, placeholders));
    }

    public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().warn(sender, message, ex, placeholders);
    }

    public void warn(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().warn(sender, message, ex, placeholders));
    }

    public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        STEMCraftAPI.api().messages().error(sender, message, ex, placeholders);
    }

    public void error(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders) {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().error(sender, message, ex, placeholders));
    }

    public void success(CommandSender sender, String message, Throwable ex, Object... placeholders)  {
        STEMCraftAPI.api().messages().success(sender, message, ex, placeholders);
    }

    public void success(List<CommandSender> senderList, String message, Throwable ex, Object... placeholders)  {
        senderList.forEach(sender -> STEMCraftAPI.api().messages().success(sender, message, ex, placeholders));
    }

    public void broadcast(String message, List<Player> exclude, Object... placeholders)  {
        STEMCraftAPI.api().messages().broadcast(message, exclude, placeholders);
    }
}
