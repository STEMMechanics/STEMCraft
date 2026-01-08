/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
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

package dev.stemcraft.service.message;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.message.TokenProcessor;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.service.BaseService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * Implementation of the MessageService interface.
 */
public class MessageServiceImpl extends BaseService implements MessageService {
    private final TokenProcessorImpl tokens;
    private final MessagePrefixes prefixes;

    /**
     * Constructor for MessageServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public MessageServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);

        this.prefixes = MessagePrefixes.from(getRootConfigSection().getSection("logging.prefixes"));

        tokens = new TokenProcessorImpl(getRootConfigSection());
    }

    /**
     * Get the token processor for adding/removing global tokens.
     *
     * @return The TokenProcessor instance.
     */
    @Override
    public TokenProcessor tokens() {
        return tokens;
    }

    /**
     * Log a debug message to the console.
     *
     * @param message The message to log.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    @Override
    public void debug(String message, Throwable ex, Object... placeholders) {
        if(plugin.debugging()) {
            message = render(null, message, placeholders);
            Component component = TextUtil.colourise("[DEBUG] " + message);

            if (ex != null) {
                plugin.getComponentLogger().debug(component, ex);
            } else {
                plugin.getComponentLogger().debug(component);
            }
        }
    }

    /**
     * Send a message to the sender or console if null.
     *
     * @param sender The command sender, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    @Override
    public void log(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = render(sender, message, placeholders);

        Component senderComponent;
        if (sender instanceof Player) {
            senderComponent = TextUtil.colourise(tokens.apply(prefixes.log()) + message);
        } else {
            senderComponent = TextUtil.colourise(message);
        }

        Component logComponent = TextUtil.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    /**
     * Send a message to the sender or console if null.
     *
     * @param sender The command sender, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    public void send(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = render(sender, message, placeholders);

        Component senderComponent = TextUtil.colourise(message);
        Component logComponent = TextUtil.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    /**
     * Send an info message to the sender or console if null.
     *
     * @param sender The command sender, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    @Override
    public void info(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = render(sender, message, placeholders);

        Component senderComponent;
        if (sender instanceof Player) {
            senderComponent = TextUtil.colourise(tokens.apply(prefixes.info()) + message);
        } else {
            senderComponent = TextUtil.colourise(message);
        }

        Component logComponent = TextUtil.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    /**
     * Send a warning message to the sender or console if null.
     *
     * @param sender The command sender, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    @Override
    public void warn(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = render(sender, message, placeholders);

        Component senderComponent;
        if (sender instanceof Player) {
            senderComponent = TextUtil.colourise(tokens.apply(prefixes.warn()) + message);
        } else {
            senderComponent = TextUtil.colourise(message);
        }

        Component logComponent = TextUtil.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().warn(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().warn(logComponent);
        }
    }

    /**
     * Send an error message to the sender or console if null.
     *
     * @param sender The command sender, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    @Override
    public void error(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = render(sender, message, placeholders);

        Component senderComponent;
        if (sender instanceof Player) {
            senderComponent = TextUtil.colourise(tokens.apply(prefixes.error()) + message);
        } else {
            senderComponent = TextUtil.colourise(message);
        }

        Component logComponent = TextUtil.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().error(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().error(logComponent);
        }
    }

    /**
     * Send a success message to the sender or console if null.
     *
     * @param sender The command sender, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    @Override
    public void success(CommandSender sender, String message, Throwable ex, Object... placeholders) {
        message = render(sender, message, placeholders);

        Component senderComponent;
        if (sender instanceof Player) {
            senderComponent = TextUtil.colourise(tokens.apply(prefixes.success()) + message);
        } else {
            senderComponent = TextUtil.colourise(message);
        }

        Component logComponent = TextUtil.colourise(message);

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            plugin.getComponentLogger().info(logComponent, ex);
        } else if (sender == null) {
            plugin.getComponentLogger().info(logComponent);
        }
    }

    /**
     * Broadcast a message to all online players, excluding those in the exclude list.
     *
     * @param message The message to broadcast.
     * @param exclude A list of players to exclude from the broadcast.
     * @param placeholders Optional placeholders for the message.
     */
    @Override
    public void broadcast(String message, List<Player> exclude, Object... placeholders) {
        String serverMessage = render(null, message, placeholders);
        Component serverComponent = TextUtil.colourise(tokens.apply(prefixes.broadcast()) + serverMessage);
        plugin.getComponentLogger().info(serverComponent);

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        onlinePlayers.forEach(player -> {
            if (exclude == null || !exclude.contains(player)) {
                String playerMessage = render(player, message, placeholders);
                Component playerComponent = TextUtil.colourise(tokens.apply(prefixes.broadcast()) + playerMessage);

                player.sendMessage(playerComponent);
            }
        });
    }

    /**
     * Get the localized text for a key with optional placeholders.
     *
     * @param sender The command sender for localization context.
     * @param key The localization key.
     * @param placeholders Optional placeholders for the message.
     * @return The localized and processed text.
     */
    @Override
    public String text(CommandSender sender, String key, Object... placeholders) {
        return render(sender, key, placeholders);
    }


    /**
     * Render a localized message with bindings and placeholders applied.
     *
     * @param sender The command sender for localization context.
     * @param key The localization key.
     * @param placeholders Optional placeholders for the message.
     * @return The rendered message.
     */
    private String render(CommandSender sender, String key, Object... placeholders) {
        String base = api.locales().resolve(sender, key);

        base = tokens.apply(base);
        base = applyPlaceholders(sender, base, placeholders);
        return base;
    }

    /**
     * Apply placeholders to a string.
     *
     * @param sender The command sender for localization context.
     * @param str The string to apply placeholders to.
     * @param placeholders The placeholders to apply.
     * @return The string with placeholders applied.
     */
    private String applyPlaceholders(CommandSender sender, String str, Object... placeholders) {
        if (str == null) {
            return null;
        }
        if (placeholders == null || placeholders.length <= 1) {
            return str;
        }

        String[] processed = StringUtil.toStrings(placeholders);

        // Preserve existing behaviour: placeholder values that are locale keys get translated.
        for (int i = 1; i < processed.length; i += 2) {
            String value = processed[i];
            if (value != null) {
                processed[i] = api.locales().resolve(sender, value);
            }
        }

        return PlaceholderUtil.apply(str, processed);
    }
}
