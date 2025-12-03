/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */
package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.MessengerService;
import dev.stemcraft.api.utils.SCString;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

public class MessengerManager implements MessengerService {

    private final STEMCraft plugin;

    private final Component prefixLog;
    private final Component prefixInfo;
    private final Component prefixWarn;
    private final Component prefixError;
    private final Component prefixSuccess;
    private final Component prefixBroadcast;

    public MessengerManager(STEMCraft plugin) {
        this.plugin = plugin;

        this.prefixLog = SCString.colourise(plugin.config().getString("logging.prefixes.log", "&7[STEM]&r "));
        this.prefixInfo = SCString.colourise(plugin.config().getString("logging.prefixes.info", "&9[INFO]&r "));
        this.prefixWarn = SCString.colourise(plugin.config().getString("logging.prefixes.warn", "&e[WARN]&r "));
        this.prefixError = SCString.colourise(plugin.config().getString("logging.prefixes.error", "&c[ERROR]&r "));
        this.prefixSuccess = SCString.colourise(plugin.config().getString("logging.prefixes.success", "&a[SUCCESS]&r "));
        this.prefixBroadcast = SCString.colourise(plugin.config().getString("logging.prefixes.broadcast", "&e[SERVER] "));
    }

    @Override
    public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCString.colourise(message);

        if(sender != null) {
            sender.sendMessage(this.prefixLog.append(component));
        }

        if(ex != null) {
            plugin.getComponentLogger().info(component, ex);
        } else if(sender == null) {
            plugin.getComponentLogger().info(component);
        }
    }

    @Override
    public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCString.colourise(message);

        if(sender != null) {
            sender.sendMessage(this.prefixInfo.append(component));
        }

        if(ex != null) {
            plugin.getComponentLogger().info(component, ex);
        } else if(sender == null) {
            plugin.getComponentLogger().info(component);
        }
    }

    @Override
    public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCString.colourise(message);

        if(sender != null) {
            sender.sendMessage(this.prefixWarn.append(component));
        }

        if(ex != null) {
            plugin.getComponentLogger().warn(component, ex);
        } else if(sender == null) {
            plugin.getComponentLogger().warn(component);
        }
    }

    @Override
    public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCString.colourise(message);

        if(sender != null) {
            sender.sendMessage(this.prefixError.append(component));
        }

        if(ex != null) {
            plugin.getComponentLogger().error(component, ex);
        } else if(sender == null) {
            plugin.getComponentLogger().error(component);
        }
    }

    @Override
    public void success(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCString.colourise(message);

        if(sender != null) {
            sender.sendMessage(this.prefixSuccess.append(component));
        }

        if(ex != null) {
            plugin.getComponentLogger().info(component, ex);
        } else if(sender == null) {
            plugin.getComponentLogger().info(component);
        }
    }

    @Override
    public void broadcast(String message, List<Player> exclude, Object... placeholders) {
        String serverMessage = locale(null, message, placeholders);
        Component serverComponent = SCString.colourise(serverMessage);
        plugin.getComponentLogger().info(this.prefixBroadcast.append(serverComponent));

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        onlinePlayers.forEach(player -> {
            if(exclude != null && !exclude.contains(player)) {
                String playerMessage = locale(player, message, placeholders);
                Component playerComponent = SCString.colourise(playerMessage);

                player.sendMessage(this.prefixBroadcast.append(playerComponent));
            }
        });
    }

    private String locale(CommandSender sender, String message, Object... placeholders) {
        return plugin.localeService().get(sender, message, placeholders);
    }
}