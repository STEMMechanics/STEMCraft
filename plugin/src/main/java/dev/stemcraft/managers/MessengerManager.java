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
import dev.stemcraft.api.utils.SCText;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public class MessengerManager implements MessengerService {

    private final STEMCraft plugin;

    private final Component prefixLog;
    private final Component prefixInfo;
    private final Component prefixWarn;
    private final Component prefixError;
    private final Component prefixSuccess;

    public MessengerManager(STEMCraft plugin) {
        this.plugin = plugin;

        this.prefixLog = SCText.colourise(plugin.config().getString("logging.prefixes.log", "&7[STEM]&r "));
        this.prefixInfo = SCText.colourise(plugin.config().getString("logging.prefixes.info", "&9[INFO]&r "));
        this.prefixWarn = SCText.colourise(plugin.config().getString("logging.prefixes.warn", "&e[WARN]&r "));
        this.prefixError = SCText.colourise(plugin.config().getString("logging.prefixes.error", "&c[ERROR]&r "));
        this.prefixSuccess = SCText.colourise(plugin.config().getString("logging.prefixes.success", "&a[SUCCESS]&r "));
    }

    @Override
    public void log(CommandSender sender, String message, Throwable ex, String... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCText.colourise(message);

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
    public void info(CommandSender sender, String message, Throwable ex, String... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCText.colourise(message);

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
    public void warn(CommandSender sender, String message, Throwable ex, String... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCText.colourise(message);

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
    public void error(CommandSender sender, String message, Throwable ex, String... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCText.colourise(message);

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
    public void success(CommandSender sender, String message, Throwable ex, String... placeholders) {
        message = locale(sender, message, placeholders);
        Component component = SCText.colourise(message);

        if(sender != null) {
            sender.sendMessage(this.prefixSuccess.append(component));
        }

        if(ex != null) {
            plugin.getComponentLogger().info(component, ex);
        } else if(sender == null) {
            plugin.getComponentLogger().info(component);
        }
    }

    private String locale(CommandSender sender, String message, String... placeholders) {
        return plugin.localeService().get(sender, message, placeholders);
    }
}