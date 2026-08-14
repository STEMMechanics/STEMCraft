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
import dev.stemcraft.api.service.message.MessageType;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.service.BaseService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the MessageService interface.
 */
public class MessageServiceImpl extends BaseService implements MessageService {
    private static final Pattern LEADING_DIRECTIVE = Pattern.compile("^\\s*/?([a-zA-Z][a-zA-Z0-9_.-]*)/\\s*");
    private TokenProcessorImpl tokens;
    private MessagePrefixes prefixes;
    private Map<String, MessageContext> contexts = Map.of();

    /**
     * Constructor for MessageServiceImpl.
     *
     * @param plugin The STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public MessageServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        prefixes = MessagePrefixes.from(getRootConfigSection().getSection("logging.prefixes"));
        contexts = loadContexts(getRootConfigSection().getSection("logging.contexts", false));
        tokens = new TokenProcessorImpl();
        tokens.fromConfig(getRootConfigSection());
    }

    /**
     * Get the token processor for adding/removing global tokens.
     *
     * @return The TokenProcessor instance.
     */
    @Override
    public @NonNull TokenProcessor tokens() {
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
    public void debug(@NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
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
    public void log(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        deliver(sender, MessageType.LOG, null, message, ex, placeholders);
    }

    /**
     * Send a message to the sender or console if null.
     *
     * @param sender The command sender, or null for console.
     * @param message The message to send.
     * @param ex An optional exception to log.
     * @param placeholders Optional placeholders for the message.
     */
    public void send(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        deliver(sender, MessageType.PLAIN, null, message, ex, placeholders);
    }

    @Override
    public void send(@Nullable CommandSender sender,
                     @NotNull MessageType type,
                     @Nullable String context,
                     @NotNull String message,
                     @Nullable Throwable ex,
                     @NotNull Object... placeholders) {
        deliver(sender, type, context, message, ex, placeholders);
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
    public void info(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        deliver(sender, MessageType.INFO, null, message, ex, placeholders);
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
    public void warn(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        deliver(sender, MessageType.WARNING, null, message, ex, placeholders);
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
    public void error(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        deliver(sender, MessageType.ERROR, null, message, ex, placeholders);
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
    public void success(@Nullable CommandSender sender, @NotNull String message, @Nullable Throwable ex, @NotNull Object... placeholders) {
        deliver(sender, MessageType.SUCCESS, null, message, ex, placeholders);
    }

    private void deliver(@Nullable CommandSender sender,
                         @NotNull MessageType defaultType,
                         @Nullable String defaultContext,
                         @NotNull String key,
                         @Nullable Throwable ex,
                         @NotNull Object... placeholders) {
        String rendered = render(sender, key, placeholders);
        RoutedMessage routed = parseDirectives(rendered, defaultType, defaultContext, contexts.keySet());
        String contextPrefix = visibleContextPrefix(sender, routed.context());
        String typePrefix = sender instanceof Player ? prefixFor(routed.type()) : "";
        Component senderComponent = TextUtil.colourise(tokens.apply(contextPrefix + typePrefix) + routed.message());
        Component logComponent = TextUtil.colourise(routed.message());

        if (sender != null) {
            sender.sendMessage(senderComponent);
        }

        if (ex != null) {
            if (routed.type() == MessageType.WARNING) {
                plugin.getComponentLogger().warn(logComponent, ex);
            } else if (routed.type() == MessageType.ERROR) {
                plugin.getComponentLogger().error(logComponent, ex);
            } else {
                plugin.getComponentLogger().info(logComponent, ex);
            }
        } else if (sender == null) {
            if (routed.type() == MessageType.WARNING) {
                plugin.getComponentLogger().warn(logComponent);
            } else if (routed.type() == MessageType.ERROR) {
                plugin.getComponentLogger().error(logComponent);
            } else {
                plugin.getComponentLogger().info(logComponent);
            }
        }
    }

    private @NotNull String prefixFor(@NotNull MessageType type) {
        return switch (type) {
            case PLAIN -> "";
            case LOG -> prefixes.log();
            case INFO -> prefixes.info();
            case WARNING -> prefixes.warn();
            case ERROR -> prefixes.error();
            case SUCCESS -> prefixes.success();
            case BROADCAST -> prefixes.broadcast();
        };
    }

    private @NotNull String visibleContextPrefix(@Nullable CommandSender sender, @Nullable String contextId) {
        if (!(sender instanceof Player player) || contextId == null) {
            return "";
        }
        MessageContext context = contexts.get(contextId.toLowerCase(Locale.ROOT));
        return context != null && context.visibleTo(player) ? context.prefix() : "";
    }

    private static @NotNull Map<String, MessageContext> loadContexts(@Nullable ConfigSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, MessageContext> loaded = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigSection contextSection = section.getSection(key, false);
            if (contextSection == null) {
                loaded.put(key.toLowerCase(Locale.ROOT), new MessageContext(
                    section.getString(key, ""), List.of(), List.of(), List.of(), List.of(), null));
                continue;
            }
            Boolean explicitDefault = null;
            if (contextSection.contains("default")) {
                String rawDefault = contextSection.getString("default", "show").trim();
                explicitDefault = !rawDefault.equalsIgnoreCase("hide") && contextSection.getBoolean("default", true);
            }
            loaded.put(key.toLowerCase(Locale.ROOT), new MessageContext(
                contextSection.getString("prefix", ""),
                contextSection.getStringList("show-when.worlds"),
                contextSection.getStringList("show-when.permissions"),
                contextSection.getStringList("hide-when.worlds"),
                contextSection.getStringList("hide-when.permissions"),
                explicitDefault
            ));
        }
        return Map.copyOf(loaded);
    }

    static @NotNull RoutedMessage parseDirectives(@NotNull String message,
                                                   @NotNull MessageType defaultType,
                                                   @Nullable String defaultContext,
                                                   @NotNull Set<String> contexts) {
        if (!message.startsWith("/")) {
            return new RoutedMessage(defaultType, normalizeContext(defaultContext, contexts), message);
        }

        MessageType type = defaultType;
        String context = normalizeContext(defaultContext, contexts);
        String remaining = message;
        boolean parsedAny = false;
        while (true) {
            Matcher matcher = LEADING_DIRECTIVE.matcher(remaining);
            if (!matcher.find()) {
                break;
            }
            String identifier = matcher.group(1).toLowerCase(Locale.ROOT);
            MessageType parsedType = parseType(identifier);
            if (parsedType != null) {
                type = parsedType;
            } else if (contexts.contains(identifier)) {
                context = identifier;
            } else {
                break;
            }
            parsedAny = true;
            remaining = remaining.substring(matcher.end());
        }
        return parsedAny
            ? new RoutedMessage(type, context, remaining)
            : new RoutedMessage(defaultType, normalizeContext(defaultContext, contexts), message);
    }

    private static @Nullable MessageType parseType(@NotNull String identifier) {
        return switch (identifier) {
            case "plain", "send" -> MessageType.PLAIN;
            case "log" -> MessageType.LOG;
            case "info" -> MessageType.INFO;
            case "warn", "warning" -> MessageType.WARNING;
            case "error" -> MessageType.ERROR;
            case "success" -> MessageType.SUCCESS;
            case "broadcast" -> MessageType.BROADCAST;
            default -> null;
        };
    }

    private static @Nullable String normalizeContext(@Nullable String context, @NotNull Set<String> contexts) {
        if (context == null) {
            return null;
        }
        String normalized = context.trim().toLowerCase(Locale.ROOT);
        return contexts.contains(normalized) ? normalized : null;
    }

    record RoutedMessage(@NotNull MessageType type, @Nullable String context, @NotNull String message) {
    }

    record MessageContext(@NotNull String prefix,
                          @NotNull List<String> showWorlds,
                          @NotNull List<String> showPermissions,
                          @NotNull List<String> hideWorlds,
                          @NotNull List<String> hidePermissions,
                          @Nullable Boolean explicitDefault) {
        boolean visibleTo(@NotNull Player player) {
            return visibleTo(player.getWorld().getName(), player::hasPermission);
        }

        boolean visibleTo(@NotNull String worldName, @NotNull Predicate<String> hasPermission) {
            boolean showConfigured = !showWorlds.isEmpty() || !showPermissions.isEmpty();
            boolean hideConfigured = !hideWorlds.isEmpty() || !hidePermissions.isEmpty();
            boolean showMatches = matchesWorld(showWorlds, worldName)
                || showPermissions.stream().anyMatch(hasPermission);
            if (showMatches) {
                return true;
            }
            boolean hideMatches = matchesWorld(hideWorlds, worldName)
                || hidePermissions.stream().anyMatch(hasPermission);
            if (hideMatches) {
                return false;
            }
            if (explicitDefault != null) {
                return explicitDefault;
            }
            return !showConfigured || hideConfigured;
        }

        private static boolean matchesWorld(@NotNull List<String> patterns, @NotNull String worldName) {
            return patterns.stream().anyMatch(pattern -> globMatches(pattern, worldName));
        }

        static boolean globMatches(@NotNull String glob, @NotNull String value) {
            StringBuilder regex = new StringBuilder("^");
            int literalStart = 0;
            for (int i = 0; i < glob.length(); i++) {
                if (glob.charAt(i) != '*') {
                    continue;
                }
                regex.append(Pattern.quote(glob.substring(literalStart, i))).append(".*");
                literalStart = i + 1;
            }
            regex.append(Pattern.quote(glob.substring(literalStart))).append('$');
            return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(value).matches();
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
    public void broadcast(@NotNull String message, @Nullable List<Player> exclude, @NotNull Object... placeholders) {
        String serverMessage = render(null, message, placeholders);
        RoutedMessage serverRoute = parseDirectives(serverMessage, MessageType.BROADCAST, null, contexts.keySet());
        Component serverComponent = formatBroadcastConsoleMessage(serverRoute.message());
        plugin.getComponentLogger().info(serverComponent);

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        onlinePlayers.forEach(player -> {
            if (exclude == null || !exclude.contains(player)) {
                String playerMessage = render(player, message, placeholders);
                RoutedMessage route = parseDirectives(playerMessage, MessageType.BROADCAST, null, contexts.keySet());
                String contextPrefix = visibleContextPrefix(player, route.context());
                Component playerComponent = TextUtil.colourise(
                    tokens.apply(contextPrefix + prefixFor(route.type())) + route.message());

                player.sendMessage(playerComponent);
            }
        });
    }

    static @NotNull Component formatBroadcastConsoleMessage(@NotNull String message) {
        return TextUtil.colourise("[broadcast] " + message);
    }

    private @NotNull Component formatBroadcastPlayerMessage(@NotNull String message) {
        return formatBroadcastPlayerMessage(tokens.apply(prefixes.broadcast()), message);
    }

    static @NotNull Component formatBroadcastPlayerMessage(@NotNull String prefix, @NotNull String message) {
        return TextUtil.colourise(prefix + message);
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
    public @NotNull String text(@Nullable CommandSender sender, @NotNull String key, @NotNull Object... placeholders) {
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
        if (sender instanceof Player player) {
            str = PlaceholderUtil.apply(str,
                "player", player.getName(),
                "uuid", player.getUniqueId().toString());
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
