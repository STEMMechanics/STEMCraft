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

    private final String prefixLog;
    private final String prefixInfo;
    private final String prefixWarn;
    private final String prefixError;
    private final String prefixSuccess;
    private final String prefixBroadcast;

    public MessengerManager(STEMCraft plugin) {
        this.plugin = plugin;

        this.prefixLog = plugin.config().getString("logging.prefixes.log", "&7[STEM]&r ");
        this.prefixInfo = plugin.config().getString("logging.prefixes.info", "&9[INFO]&r ");
        this.prefixWarn = plugin.config().getString("logging.prefixes.warn", "&e[WARN]&r ");
        this.prefixError = plugin.config().getString("logging.prefixes.error", "&c[ERROR]&r ");
        this.prefixSuccess = plugin.config().getString("logging.prefixes.success", "&a[SUCCESS]&r ");
        this.prefixBroadcast = plugin.config().getString("logging.prefixes.broadcast", "&e[SERVER] ");
    }

    @Override
    public void debug(String message, Throwable ex, Object... placeholders) {
        if(plugin.debugging()) {
            message = locale(null, message, placeholders);
            Component component = SCString.colourise("[DEBUG] " + message);

            if (ex != null) {
                plugin.getComponentLogger().debug(component, ex);
            } else {
                plugin.getComponentLogger().debug(component);
            }
        }
    }

    @Override
    public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);

        Component senderComponent = null;
        if (sender instanceof Player) {
            senderComponent = SCString.colourise(bindings(this.prefixLog) + message);
        } else {
            senderComponent = SCString.colourise(message);
        }

        Component logComponent = SCString.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    public void plain(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);

        Component senderComponent = SCString.colourise(message);
        Component logComponent = SCString.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    @Override
    public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);

        Component senderComponent = null;
        if (sender instanceof Player) {
            senderComponent = SCString.colourise(bindings(this.prefixInfo) + message);
        } else {
            senderComponent = SCString.colourise(message);
        }

        Component logComponent = SCString.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    @Override
    public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);

        Component senderComponent = null;
        if (sender instanceof Player) {
            senderComponent = SCString.colourise(bindings(this.prefixWarn) + message);
        } else {
            senderComponent = SCString.colourise(message);
        }

        Component logComponent = SCString.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().warn(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().warn(logComponent);
        }
    }

    @Override
    public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);

        Component senderComponent = null;
        if (sender instanceof Player) {
            senderComponent = SCString.colourise(bindings(this.prefixError) + message);
        } else {
            senderComponent = SCString.colourise(message);
        }

        Component logComponent = SCString.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().error(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().error(logComponent);
        }
    }

    @Override
    public void success(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = locale(sender, message, placeholders);

        Component senderComponent = null;
        if (sender instanceof Player) {
            senderComponent = SCString.colourise(bindings(this.prefixSuccess) + message);
        } else {
            senderComponent = SCString.colourise(message);
        }

        Component logComponent = SCString.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    @Override
    public void broadcast(String message, List<Player> exclude, Object... placeholders) {
        String serverMessage = locale(null, message, placeholders);
        Component serverComponent = SCString.colourise(bindings(this.prefixBroadcast) + serverMessage);
        plugin.getComponentLogger().info(serverComponent);

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        onlinePlayers.forEach(player -> {
            if(exclude != null && !exclude.contains(player)) {
                String playerMessage = locale(player, message, placeholders);
                Component playerComponent = SCString.colourise(bindings(this.prefixBroadcast) + playerMessage);

                player.sendMessage(playerComponent);
            }
        });
    }

    private String locale(CommandSender sender, String message, Object... placeholders) {
        return plugin.localeService().get(sender, message, placeholders);
    }

    private String bindings(String message) {
        return plugin.localeService().processBindings(message);
    }
}