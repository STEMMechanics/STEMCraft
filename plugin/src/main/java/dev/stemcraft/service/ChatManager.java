package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.util.SCPlayer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ChatManager {
    STEMCraft plugin;
    String chatFormat;
    private Chat vaultChat;

    private String filterCommand;

    private final List<Pattern> blacklist = new ArrayList<>();
    private final List<Pattern> whitelist = new ArrayList<>();
    private final List<String> blacklistRaw = new ArrayList<>();
    private final List<String> whitelistRaw = new ArrayList<>();

    private final List<Pattern> warninglist = new ArrayList<>();
    private final List<String> warninglistRaw = new ArrayList<>();

    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> warningHits = new ConcurrentHashMap<>();

    private long spamCooldownMs;
    private long warningCooldownMs = 10 * 60 * 1000L; // 10 minutes
    private int warningThreshold = 3;

    private String spamMessage;
    private String warningMessage;

    private boolean muted;

    public ChatManager(STEMCraft plugin) {
        this.plugin = plugin;

        chatFormat = plugin.config().getString("chat.format", "{prefix}{player}: {message}{suffix}");
        filterCommand = plugin.config().getString("chat.filter_command", "");

        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            var rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
            if (rsp != null) {
                vaultChat = rsp.getProvider();
            }
        }

        spamCooldownMs = (long)(plugin.config().getDouble("chat.spam_cooldown", 1.5) * 1000L);
        spamMessage = plugin.config().getString("chat.spam_message", "Please do not spam the chat");

        warningThreshold = plugin.config().getInt("chat.warning_threshold", 3);
        warningCooldownMs = (long)(plugin.config().getDouble("chat.warning_cooldown", 10 * 60) * 1000L);
        warningMessage = plugin.config().getString("chat.warning_message", "You have been warned for bad chat. If you continue you will be banned.");

        loadChatFilterConfig();
    }

    private void loadChatFilterConfig() {
        blacklist.clear();
        whitelist.clear();
        blacklistRaw.clear();
        whitelistRaw.clear();
        warninglist.clear();
        warninglistRaw.clear();

        try {
            File file = new File(plugin.getDataFolder(), "chat-filter.yml");

            // Ensure data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Copy default chat-filter.yml from plugin resources if missing
            if (!file.exists()) {
                try (InputStream in = plugin.getResource("chat-filter.yml")) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    } else {
                        // Fallback to empty file if resource is missing
                        file.createNewFile();
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to create default chat-filter.yml: " + ex.getMessage());
                }
            }

            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            List<String> bl = cfg.getStringList("blacklist");
            List<String> wl = cfg.getStringList("whitelist");
            List<String> warn = cfg.getStringList("warninglist");
            if (warn == null || warn.isEmpty()) {
                // allow alternate key name
                warn = cfg.getStringList("warnings");
            }

            if (bl != null) blacklistRaw.addAll(bl);
            if (wl != null) whitelistRaw.addAll(wl);
            if (warn != null) warninglistRaw.addAll(warn);

            for (String s : blacklistRaw) {
                if (s == null || s.isBlank()) continue;
                try {
                    blacklist.add(Pattern.compile(normalize(s), Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException ex) {
                    plugin.getLogger().warning("Invalid blacklist regex in chat-filter.yml: " + s);
                }
            }

            for (String s : whitelistRaw) {
                if (s == null || s.isBlank()) continue;
                try {
                    whitelist.add(Pattern.compile(normalize(s), Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException ex) {
                    plugin.getLogger().warning("Invalid whitelist regex in chat-filter.yml: " + s);
                }
            }

            for (String s : warninglistRaw) {
                if (s == null || s.isBlank()) continue;
                try {
                    warninglist.add(Pattern.compile(normalize(s), Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException ex) {
                    plugin.getLogger().warning("Invalid warninglist regex in chat-filter.yml: " + s);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load chat-filter.yml: " + ex.getMessage());
        }
    }

    public void onEnable() {
        plugin.registerCommand("muteall")
            .permission("stemcraft.command.muteall")
            .usage("/muteall <on|off>")
            .description("Mute or unmute all chat")
            .tabCompletion("on", "off")
            .executor((api, cmd, ctx) -> {
                ctx.checkArgsSizeAtLeast(1);
                String arg = ctx.getArg(1);

                if (arg.equalsIgnoreCase("on")) {
                    muted = true;
                    ctx.returnInfo("Chat has been muted.");
                } else if (arg.equalsIgnoreCase("off")) {
                    muted = false;
                    ctx.returnInfo("Chat has been unmuted.");
                }
                ctx.returnError(cmd.getUsage());
            });

        plugin.registerEvent(AsyncChatEvent.class, (event) -> {
            if (muted) {
                event.setCancelled(true);
                plugin.messengerService().warn(event.getPlayer(), "Chat is currently muted.");
            }
        }, EventPriority.HIGH, true);

        plugin.registerCommand("chatfilter")
            .permission("stemcraft.command.chatfilter")
            .usage("/chatfilter reload")
            .description("Reload the chat filter configuration")
            .tabCompletion("reload")
            .executor((api, cmd, ctx) -> {
                ctx.checkArgsSizeAtLeast(1);
                String subcmd = ctx.getArg(1);

                if(subcmd.equalsIgnoreCase("reload")) {
                    loadChatFilterConfig();
                    ctx.returnInfo("Chat filter configuration reloaded.");
                } else {
                    ctx.returnError(cmd.getUsage());
                }
            });

        plugin.registerEvent(AsyncChatEvent.class, (event) -> {
            long now = System.currentTimeMillis();
            UUID uuid = event.getPlayer().getUniqueId();

            if(!api.gatekeeper().isWhiteListed(event.getPlayer())) { return; }

            String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

            if (spamCooldownMs > 0) {
                Long last = lastChatAt.put(uuid, now);
                if (last != null && (now - last) < spamCooldownMs) {
                    event.setCancelled(true);

                    // AsyncChatEvent runs async: notify player on main thread
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.messengerService().warn(event.getPlayer(), spamMessage)
                    );
                    return;
                }
            }

            boolean whitelisted = false;
            for (Pattern p : whitelist) {
                if (p.matcher(plain).find()) {
                    whitelisted = true;
                    break;
                }
            }

            if (!whitelisted) {
                String matched = null;
                for (int i = 0; i < blacklist.size(); i++) {
                    Pattern p = blacklist.get(i);
                    if (p.matcher(plain).find()) {
                        matched = (i < blacklistRaw.size() ? blacklistRaw.get(i) : p.pattern());
                        break;
                    }
                }

                if (matched != null) {
                    event.setCancelled(true);

                    plugin.warn("Blocked chat from " + event.getPlayer().getName() + " (matched blacklist: " + matched + "): " + plain);

                    if (filterCommand != null && !filterCommand.isBlank()) {
                        String cmd = filterCommand.replace("{player}", event.getPlayer().getName());

                        // AsyncChatEvent runs async: dispatch command on main thread
                        ConsoleCommandSender console = org.bukkit.Bukkit.getConsoleSender();
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> org.bukkit.Bukkit.dispatchCommand(console, cmd));
                    } else {
                        plugin.messengerService().error(event.getPlayer(), "Your message was blocked by the chat filter.");
                    }

                    return;
                }
            }

            if (!whitelisted) {
                // Warninglist: does not block, but warns player + logs. Whitelist overrides.
                String warnMatched = null;
                for (int i = 0; i < warninglist.size(); i++) {
                    Pattern p = warninglist.get(i);
                    if (p.matcher(plain).find()) {
                        warnMatched = (i < warninglistRaw.size() ? warninglistRaw.get(i) : p.pattern());
                        break;
                    }
                }

                if (warnMatched != null) {
                    plugin.warn("Chat warning for " + event.getPlayer().getName() + " (matched warninglist: " + warnMatched + "): " + plain);

                    // AsyncChatEvent runs async: notify player on main thread
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.messengerService().warn(event.getPlayer(), warningMessage)
                    );

                    Deque<Long> hits = warningHits.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                    synchronized (hits) {
                        // purge old
                        while (!hits.isEmpty() && (now - hits.peekFirst()) > warningCooldownMs) {
                            hits.removeFirst();
                        }
                        hits.addLast(now);

                        if (warningThreshold > 0 && hits.size() >= warningThreshold) {
                            // trigger filter command after N warnings in window
                            if (filterCommand != null && !filterCommand.isBlank()) {
                                String cmd = filterCommand.replace("{player}", event.getPlayer().getName());

                                ConsoleCommandSender console = org.bukkit.Bukkit.getConsoleSender();
                                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> org.bukkit.Bukkit.dispatchCommand(console, cmd));
                            }

                            // reset after triggering to avoid repeated triggers on every message
                            hits.clear();
                        }
                    }
                }
            }

            String pfx = vaultChat != null ? vaultChat.getPlayerPrefix(event.getPlayer()) : "";
            String sfx = vaultChat != null ? vaultChat.getPlayerSuffix(event.getPlayer()) : "";

            pfx = plugin.locale().processBindings(pfx.replace('&', '§'));
            sfx = plugin.locale().processBindings(sfx.replace('&', '§'));

            // Convert component message to legacy text so colours are embedded in the string
            String msgLegacy = LegacyComponentSerializer.legacySection().serialize(event.message());

            String line = chatFormat
                    .replace("{prefix}", pfx == null ? "" : pfx)
                    .replace("{suffix}", sfx == null ? "" : sfx)
                    .replace("{player}", event.getPlayer().getName())
                    .replace("{message}", msgLegacy.replace("§", ""));

            Component rendered = LegacyComponentSerializer.legacySection().deserialize(line);

            event.renderer((source, sourceDisplayName, message, viewer) -> rendered);
        });
    }

    private String normalize(String raw) {
        if (raw.matches(".*[\\\\^$\\[\\]().*?+].*")) return raw;
        return "\\b" + raw + "\\b";
    }
}
