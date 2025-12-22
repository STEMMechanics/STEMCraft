package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class Tab implements STEMCraftFeature {
    private STEMCraftAPI api;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private Chat vaultChat;

    private long updateTicks;
    private List<String> headerLines;
    private List<String> footerLines;
    private String nameFormat;
    private int maxNameLen;
    private int goodMax;
    private int warnMax;

    public void onEnable(STEMCraftAPI api) {
        this.api = api;
        hookVault();
        reload();
        start();
    }

    private void hookVault() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return;
        var rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
        if (rsp != null) vaultChat = rsp.getProvider();
    }

    public void reload() {
        String base = getConfigBase();

        updateTicks = api.config().getLong(base + ".update_ticks", 40L);
        headerLines = api.config().getStringList(base + ".header");
        footerLines = api.config().getStringList(base + ".footer");
        nameFormat = api.config().getString(base + ".name_format", "{prefix}{player}");
        maxNameLen = api.config().getInt(base + ".max_name_len", 48);

        goodMax = api.config().getInt(base + ".ping.good_max", 80);
        warnMax = api.config().getInt(base + ".ping.warn_max", 160);
    }

    public void start() {
        stop();
        api.tasks().repeating("tab-update", 1L, Math.max(1L, updateTicks), this::tick);
    }

    public void stop() {
        api.tasks().cancel("tab-update");
    }

    private void tick() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        for (Player p : Bukkit.getOnlinePlayers()) {
            String prefix = getVaultPrefix(p);
            String suffix = getVaultSuffix(p);

            String header = joinLines(headerLines, "\n");
            String footer = joinLines(footerLines, "\n");

            header = applyPlaceholders(header, p, prefix, suffix, online, max);
            footer = applyPlaceholders(footer, p, prefix, suffix, online, max);

            // header/footer: MiniMessage
            p.sendPlayerListHeaderAndFooter(mm.deserialize(header), mm.deserialize(footer));

            // tab name: build as legacy (Vault is legacy), then deserialize once
            String nameLine = applyPlaceholders(nameFormat, p, prefix, suffix, online, max);
            nameLine = trimVisible(nameLine, maxNameLen);

            Component tabName = legacyToComponent(nameLine);
            p.playerListName(tabName);
        }
    }

    private String applyPlaceholders(String s, Player p, String prefix, String suffix, int online, int max) {
        if (s == null) return "";

        String pingColour;
        int ping = p.getPing();
        if (ping <= goodMax) {
            pingColour = "green";
        } else if (ping <= warnMax) {
            pingColour = "yellow";
        } else {
            pingColour = "red";
        }

        String out = s
                .replace("{player}", p.getName())
                .replace("{world}", p.getWorld().getName())
                .replace("{ping}", String.valueOf(ping))
                .replace("{ping-colour}", "<" + pingColour + ">")
                .replace("{/ping-colour}", "</" + pingColour + ">")
                .replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max))
                .replace("{prefix}", prefix == null ? "" : prefix)
                .replace("{suffix}", suffix == null ? "" : suffix);

        // glyph bindings (turn :roles\stemcraft: into the char)
        out = api.locale().processBindings(out);

        return out;
    }

    private String getVaultPrefix(Player p) {
        if (vaultChat == null) return "";
        String raw = vaultChat.getPlayerPrefix(p);
        return raw == null ? "" : raw.replace('&', '§');
    }

    private String getVaultSuffix(Player p) {
        if (vaultChat == null) return "";
        String raw = vaultChat.getPlayerSuffix(p);
        return raw == null ? "" : raw.replace('&', '§');
    }

    private Component legacyToComponent(String legacy) {
        if (legacy == null || legacy.isBlank()) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    private static String joinLines(List<String> lines, String sep) {
        if (lines == null || lines.isEmpty()) return "";
        return String.join(sep, lines);
    }

    // Conservative: this limits raw string length, not true visible length.
    // If you want exact, we can strip legacy codes before measuring.
    private static String trimVisible(String s, int maxLen) {
        if (s == null) return "";
        if (maxLen <= 0) return s;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
