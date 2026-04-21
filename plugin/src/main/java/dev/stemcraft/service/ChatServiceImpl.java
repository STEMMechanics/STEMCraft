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

package dev.stemcraft.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.api.util.TimeUtil;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServiceImpl extends BaseService {
    private static final Gson GSON = new Gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String BOOK_TITLE_SEPARATOR = "\n\n---BOOK-TITLE---\n\n";
    private static final String BOOK_PAGE_SEPARATOR = "\n\n---BOOK-PAGE---\n\n";
    private static final List<ModerationActionRule> DEFAULT_MODERATION_ACTION_RULES = List.of(
        new ModerationActionRule(1, TimeUtil.parseDuration("5m"), "warn", 0L),
        new ModerationActionRule(2, TimeUtil.parseDuration("5m"), "warn", 0L),
        new ModerationActionRule(3, TimeUtil.parseDuration("10m"), "kick", 0L),
        new ModerationActionRule(4, TimeUtil.parseDuration("30m"), "ban", TimeUtil.parseDuration("7d"))
    );

    String chatFormat;
    private Chat vaultChat;

    private String filterCommand;

    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();

    private long spamCooldownMs;
    private String spamMessage;
    private boolean contentFilterEnabled;
    private long contentFilterTimeoutMillis;
    private String contentFilterBlockedMessage;
    private String contentFilterUnavailableMessage;
    private boolean contentFilterAllowFilteredMessage;
    private HttpClient httpClient;
    private final Map<UUID, Deque<Instant>> contentFilterViolations = new ConcurrentHashMap<>();
    private List<ModerationActionRule> moderationActionRules = List.of();
    private long moderationMaxWindowSeconds;

    private boolean muted;

    public ChatServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
        setConfigKey("chat");
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            var rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
            if (rsp != null) {
                vaultChat = rsp.getProvider();
            }
        }

        reloadSettings();

        api.commands().create("muteall")
            .permission("stemcraft.command.muteall")
            .usage("MUTEALL_USAGE")
            .description("MUTEALL_DESCRIPTION")
            .tabCompletion("on", "off")
            .executor((unused, cmd, ctx) -> {
                ctx.checkArgsSizeAtLeast(1);
                String arg = ctx.getArg(1);

                if (arg.equalsIgnoreCase("on")) {
                    muted = true;
                    ctx.returnInfo("MUTEALL_MUTED");
                } else if (arg.equalsIgnoreCase("off")) {
                    muted = false;
                    ctx.returnInfo("MUTEALL_UNMUTED");
                }
                ctx.returnUsage();
            })
            .register(plugin);

        api.events().register(AsyncChatEvent.class, event -> {
            if (muted) {
                event.setCancelled(true);
                api.messages().warn(event.getPlayer(), "CHAT_MUTED_WARNING");
            }
        }, EventPriority.HIGH, true);

        api.events().register(AsyncChatEvent.class, event -> {
            long now = System.currentTimeMillis();
            UUID uuid = event.getPlayer().getUniqueId();

            String plain = PLAIN.serialize(event.message());

            if (spamCooldownMs > 0) {
                Long last = lastChatAt.put(uuid, now);
                if (last != null && (now - last) < spamCooldownMs) {
                    event.setCancelled(true);
                    api.tasks().nextTick(() -> api.messages().warn(event.getPlayer(), spamMessage));
                    return;
                }
            }

            String effectiveMessage = plain;
            if (contentFilterEnabled) {
                ModerationDecision decision = moderatePlayerMessage(
                    event.getPlayer(),
                    "chat",
                    plain,
                    event.getPlayer().getLocation(),
                    Map.of("channel", "global")
                );

                if (decision.blocked()) {
                    event.setCancelled(true);
                    plugin.getLogger().warning("Blocked chat from " + event.getPlayer().getName() + ": " + plain + " (" + decision.reason() + ")");

                    applyModerationEnforcement(event.getPlayer(), "chat", decision);
                    return;
                }

                if (decision.filteredMessage() != null) {
                    effectiveMessage = decision.filteredMessage();
                    event.message(Component.text(effectiveMessage));
                }
            }

            String pfx = vaultChat != null ? vaultChat.getPlayerPrefix(event.getPlayer()) : "";
            String sfx = vaultChat != null ? vaultChat.getPlayerSuffix(event.getPlayer()) : "";

            pfx = api.messages().tokens().apply(pfx.replace('&', '§'));
            sfx = api.messages().tokens().apply(sfx.replace('&', '§'));

            String line = chatFormat
                .replace("{prefix}", pfx == null ? "" : pfx)
                .replace("{suffix}", sfx == null ? "" : sfx)
                .replace("{player}", event.getPlayer().getName())
                .replace("{message}", effectiveMessage.replace("§", ""));

            line = api.messages().tokens().apply(line);

            Component rendered = LegacyComponentSerializer.legacySection().deserialize(line);
            event.renderer((source, sourceDisplayName, message, viewer) -> rendered);
        });

        api.events().register(SignChangeEvent.class, event -> {
            if (!contentFilterEnabled) {
                return;
            }

            String content = event.lines().stream()
                .map(PLAIN::serialize)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            if (content.isBlank()) {
                return;
            }

            ModerationDecision decision = moderatePlayerMessage(
                event.getPlayer(),
                "sign",
                content,
                event.getBlock().getLocation(),
                Map.of(
                    "side", event.getSide().name().toLowerCase(Locale.ROOT),
                    "line_count", event.lines().size()
                )
            );

            if (decision.blocked()) {
                event.setCancelled(true);
                applyModerationEnforcement(event.getPlayer(), "sign", decision);
                return;
            }

            if (decision.filteredMessage() != null) {
                List<String> filteredLines = splitSignLines(decision.filteredMessage());
                for (int i = 0; i < 4; i++) {
                    // HACK: variable "content" should be `Component`s. It's not. - ProjectHSI
                    event.line(i, Component.text(filteredLines.get(i)));
                }
            }
        }, EventPriority.HIGH, true);

        api.events().register(PlayerEditBookEvent.class, event -> {
            if (!contentFilterEnabled) {
                return;
            }

            BookMeta meta = event.getNewBookMeta();
            EncodedBookContent encoded = encodeBookContent(meta);
            if (encoded.message().isBlank()) {
                return;
            }

            ModerationDecision decision = moderatePlayerMessage(
                event.getPlayer(),
                "book",
                encoded.message(),
                event.getPlayer().getLocation(),
                Map.of(
                    "title", encoded.title() == null ? "" : encoded.title(),
                    "page_count", encoded.pages().size(),
                    "signing", event.isSigning()
                )
            );

            if (decision.blocked()) {
                event.setCancelled(true);
                applyModerationEnforcement(event.getPlayer(), "book", decision);
                return;
            }

            if (decision.filteredMessage() != null) {
                BookMeta filteredMeta = applyFilteredBook(meta, encoded.title(), decision.filteredMessage(), event.isSigning());
                event.setNewBookMeta(filteredMeta);
            }
        }, EventPriority.HIGH, true);
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
    }

    private void reloadSettings() {
        chatFormat = TextUtil.colouriseToSection(
            getConfigSection().getString("format", "{prefix}{player}: {message}{suffix}")
        );
        filterCommand = getConfigSection().getString("filter_command", "");

        spamCooldownMs = (long) (getConfigSection().getDouble("spam_cooldown", 1.5) * 1000L);
        spamMessage = getConfigSection().getString("spam_message", "Please do not spam the chat");
        contentFilterEnabled = getConfigSection().getBoolean("content_filter.enabled", true);
        contentFilterTimeoutMillis = getConfigSection().getLong(
            "content_filter.timeout_millis",
            getRootConfigSection().getLong("webhook_bridge.request_timeout_millis", 10000L)
        );
        contentFilterBlockedMessage = getConfigSection().getString(
            "content_filter.blocked_message",
            "Your message was blocked by the content filter."
        );
        contentFilterUnavailableMessage = getConfigSection().getString(
            "content_filter.unavailable_message",
            "Chat is temporarily unavailable while the content filter service is offline."
        );
        contentFilterAllowFilteredMessage = getConfigSection().getBoolean("content_filter.allow_filtered_message", false);
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(getRootConfigSection().getLong("webhook_bridge.connect_timeout_millis", 5000L)))
            .build();
        loadModerationActionRules();
    }

    private ModerationDecision moderatePlayerMessage(Player player, String messageType, String message, Location location, Map<String, Object> context) {
        String endpointUrl = getRootConfigSection().getString("webhook_bridge.site_webhook_url", "").trim();
        String sharedSecret = getRootConfigSection().getString("webhook_bridge.shared_secret", "").trim();
        String serverName = getRootConfigSection().getString("webhook_bridge.server_name", plugin.getServer().getName());

        if (endpointUrl.isBlank() || sharedSecret.isBlank()) {
            return ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_unconfigured", null, false);
        }

        String deliveryId = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "player.message");
        payload.put("uuid", player.getUniqueId().toString());
        payload.put("username", player.getName());
        payload.put("platform", PlayerUtil.isBedrock(player) ? "bedrock" : "java");
        payload.put("message_type", messageType);
        payload.put("message", message);
        payload.put("server_name", serverName);
        payload.put("occurred_at", Instant.now().toString());

        if (location != null && location.getWorld() != null) {
            payload.put("world", location.getWorld().getName());
            payload.put("x", location.getX());
            payload.put("y", location.getY());
            payload.put("z", location.getZ());
            payload.put("yaw", location.getYaw());
            payload.put("pitch", location.getPitch());
        }

        if (context != null && !context.isEmpty()) {
            payload.put("context", context);
        }

        String body = GSON.toJson(payload);
        String signature = sign(body, timestamp, sharedSecret);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpointUrl))
            .timeout(Duration.ofMillis(contentFilterTimeoutMillis))
            .header("Content-Type", "application/json")
            .header("X-Minecraft-Timestamp", timestamp)
            .header("X-Minecraft-Delivery-Id", deliveryId)
            .header("X-Minecraft-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                return switch (response.statusCode()) {
                    case 403 -> ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_forbidden", null, false);
                    case 409 -> ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_replay_rejected", null, false);
                    case 422 -> ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_invalid_payload", null, false);
                    default -> ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_http_" + response.statusCode(), null, false);
                };
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("pass")) {
                return ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_invalid_response", null, false);
            }

            boolean pass = json.get("pass").getAsBoolean();
            String filteredMessage = nullableString(json, "filtered_message");
            String reason = nullableString(json, "reason");
            String reasonDetail = nullableString(json, "reason_detail");

            if (pass) {
                return ModerationDecision.allow();
            }
            if (filteredMessage != null && contentFilterAllowFilteredMessage) {
                return ModerationDecision.filtered(filteredMessage, reason, reasonDetail);
            }

            return ModerationDecision.deny(contentFilterBlockedMessage, reason == null ? "content_filter_rejected" : reason, reasonDetail, true);
        } catch (JsonParseException exception) {
            plugin.getLogger().warning("Content filter returned invalid JSON: " + exception.getMessage());
            return ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_invalid_json", null, false);
        } catch (Exception exception) {
            plugin.getLogger().warning("Content filter request failed: " + exception.getMessage());
            return ModerationDecision.deny(contentFilterUnavailableMessage, "content_filter_unavailable", null, false);
        }
    }

    private void loadModerationActionRules() {
        List<ModerationActionRule> loadedRules = new ArrayList<>();
        if (!getConfigSection().isSection("content_filter.actions")) {
            writeDefaultModerationActionRules();
        }

        if (getConfigSection().isSection("content_filter.actions")) {
            var actionsSection = getConfigSection().getSection("content_filter.actions", false);
            if (actionsSection != null) {
                List<String> keys = new ArrayList<>(actionsSection.getKeys(false));
                keys.sort(Comparator.comparingInt(this::sortKeyAsInt));
                for (String key : keys) {
                    var ruleSection = actionsSection.getSection(key, false);
                    if (ruleSection == null) {
                        continue;
                    }

                    int count = ruleSection.getInt("count", 0);
                    long windowSeconds = parseDurationSeconds(ruleSection.getString("within", "0s"), 0L);
                    String action = ruleSection.getString("action", "warn").trim().toLowerCase(Locale.ROOT);
                    long durationSeconds = parseDurationSeconds(ruleSection.getString("duration", ""), 0L);

                    if (count <= 0 || windowSeconds <= 0L || action.isBlank()) {
                        continue;
                    }

                    loadedRules.add(new ModerationActionRule(count, windowSeconds, action, durationSeconds));
                }
            }
        }

        if (loadedRules.isEmpty()) {
            loadedRules.addAll(DEFAULT_MODERATION_ACTION_RULES);
        }

        moderationActionRules = loadedRules;
        moderationMaxWindowSeconds = loadedRules.stream()
            .mapToLong(ModerationActionRule::windowSeconds)
            .max()
            .orElse(0L);
    }

    private void writeDefaultModerationActionRules() {
        var actionsSection = getConfigSection().createSection("content_filter.actions", true);
        int index = 1;
        for (ModerationActionRule rule : DEFAULT_MODERATION_ACTION_RULES) {
            String basePath = Integer.toString(index++);
            actionsSection.set(basePath + ".count", rule.count());
            actionsSection.set(basePath + ".within", TimeUtil.formatDuration(rule.windowSeconds()));
            actionsSection.set(basePath + ".action", rule.action());
            if (rule.durationSeconds() > 0L) {
                actionsSection.set(basePath + ".duration", TimeUtil.formatDuration(rule.durationSeconds()));
            } else {
                actionsSection.remove(basePath + ".duration");
            }
        }
        getRootConfigSection().save();
    }

    private int sortKeyAsInt(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private long parseDurationSeconds(String text, long defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return TimeUtil.parseDuration(text);
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }

    private void applyModerationEnforcement(Player player, String messageType, ModerationDecision decision) {
        if (decision.enforcePunishment()) {
            applyModerationViolation(player, messageType, decision);
            return;
        }

        api.tasks().nextTick(() -> api.messages().error(player, decision.userMessage()));
    }

    private void applyModerationViolation(Player player, String messageType, ModerationDecision decision) {
        if (filterCommand != null && !filterCommand.isBlank()) {
            String cmd = filterCommand.replace("{player}", player.getName());
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(console, cmd));
        }

        ModerationActionRule actionRule = recordViolation(player.getUniqueId());
        String reason = buildModerationReason(messageType, decision);

        if (actionRule == null) {
            api.tasks().nextTick(() -> api.messages().error(player, contentFilterBlockedMessage));
            return;
        }

        switch (actionRule.action()) {
            case "ban" -> applyContentFilterBan(player, actionRule, reason);
            case "kick" -> applyContentFilterKick(player, reason);
            case "warn" -> plugin.punishments().record(player.getUniqueId(), null, null, "warn", false, reason);
            default -> api.tasks().nextTick(() -> api.messages().error(player, contentFilterBlockedMessage));
        }
    }

    private ModerationActionRule recordViolation(UUID playerUuid) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = contentFilterViolations.computeIfAbsent(playerUuid, ignored -> new LinkedList<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
            if (moderationMaxWindowSeconds > 0L) {
                Instant cutoff = now.minusSeconds(moderationMaxWindowSeconds);
                while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                    timestamps.removeFirst();
                }
            }

            ModerationActionRule matchedRule = null;
            for (ModerationActionRule rule : moderationActionRules) {
                if (timestamps.size() < rule.count()) {
                    continue;
                }

                List<Instant> snapshot = new ArrayList<>(timestamps);
                Instant thresholdInstant = snapshot.get(snapshot.size() - rule.count());
                if (!thresholdInstant.isBefore(now.minusSeconds(rule.windowSeconds()))) {
                    matchedRule = rule;
                }
            }

            return matchedRule;
        }
    }

    private String buildModerationReason(String messageType, ModerationDecision decision) {
        StringBuilder reason = new StringBuilder("Blocked ");
        reason.append(messageType).append(" by content filter");

        if (decision.reason() != null && !decision.reason().isBlank()) {
            reason.append(": ").append(decision.reason());
        }
        if (decision.reasonDetail() != null && !decision.reasonDetail().isBlank()) {
            reason.append(" (").append(decision.reasonDetail()).append(')');
        }

        return reason.toString();
    }

    private void applyContentFilterKick(Player player, String reason) {
        plugin.punishments().record(player.getUniqueId(), null, null, "kick", true, reason);
        api.tasks().nextTick(() -> player.kick(Component.text("You have been kicked: " + reason)));
    }

    private void applyContentFilterBan(Player player, ModerationActionRule rule, String reason) {
        Duration duration = rule.durationSeconds() > 0L ? Duration.ofSeconds(rule.durationSeconds()) : null;
        plugin.punishments().record(player.getUniqueId(), null, duration, "ban", true, reason);

        PlayerProfile profile = Bukkit.createProfile(player.getUniqueId(), player.getName());
        var expires = duration == null ? null : java.util.Date.from(Instant.now().plus(duration));
        Bukkit.getBanList(BanListType.PROFILE).addBan(profile, reason, expires, "<server>");
        api.tasks().nextTick(() -> player.kick(plugin.punishments().formatBanMessage(plugin.punishments().findActiveBan(player.getUniqueId()))));
    }

    private BookMeta applyFilteredBook(BookMeta originalMeta, String originalTitle, String filteredMessage, boolean signing) {
        DecodedBookContent decoded = decodeBookContent(filteredMessage, originalTitle);
        BookMeta updatedMeta = originalMeta.clone();

        if (signing) {
            if (decoded.title() == null || decoded.title().isBlank()) {
                updatedMeta.title(null);
            } else {
                updatedMeta.title(Component.text(decoded.title()));
            }
        }
        
        updatedMeta = (BookMeta) updatedMeta.pages(decoded.pages().stream().map((bookString) -> (Component)Component.text(bookString)).toList());
        return updatedMeta;
    }

    private EncodedBookContent encodeBookContent(BookMeta meta) {
        String title = meta.hasTitle() && meta.getTitle() != null ? meta.getTitle() : "";
        List<String> pages = new ArrayList<>();
        for (Component page : meta.pages()) {
            pages.add(PLAIN.serialize(page));
        }

        String body = String.join(BOOK_PAGE_SEPARATOR, pages);
        String message = title + BOOK_TITLE_SEPARATOR + body;
        if (title.isBlank() && pages.stream().allMatch(String::isBlank)) {
            message = "";
        }

        return new EncodedBookContent(title, pages, message);
    }

    private DecodedBookContent decodeBookContent(String encoded, String fallbackTitle) {
        if (encoded == null) {
            return new DecodedBookContent(fallbackTitle, List.of(""));
        }

        String title = fallbackTitle;
        String pagesText = encoded;

        int titleSeparatorIndex = encoded.indexOf(BOOK_TITLE_SEPARATOR);
        if (titleSeparatorIndex >= 0) {
            title = encoded.substring(0, titleSeparatorIndex);
            pagesText = encoded.substring(titleSeparatorIndex + BOOK_TITLE_SEPARATOR.length());
        }

        List<String> pages = new ArrayList<>(List.of(pagesText.split(BOOK_PAGE_SEPARATOR, -1)));
        if (pages.isEmpty()) {
            pages = new ArrayList<>(List.of(""));
        }

        return new DecodedBookContent(title, pages);
    }

    private List<String> splitSignLines(String content) {
        String[] rawLines = content.split("\\R", -1);
        List<String> lines = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            if (i < rawLines.length) {
                lines.add(rawLines[i]);
            } else {
                lines.add("");
            }
        }

        if (rawLines.length > 4) {
            StringBuilder lastLine = new StringBuilder(lines.get(3));
            for (int i = 4; i < rawLines.length; i++) {
                if (!lastLine.isEmpty()) {
                    lastLine.append(' ');
                }
                lastLine.append(rawLines[i]);
            }
            lines.set(3, lastLine.toString());
        }

        return lines;
    }

    private String nullableString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        return json.get(key).getAsString();
    }

    private String sign(String body, String timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "\n" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign content filter payload", exception);
        }
    }

    private record ModerationDecision(boolean blocked, String filteredMessage, String userMessage, String reason, String reasonDetail, boolean enforcePunishment) {
        private static ModerationDecision allow() {
            return new ModerationDecision(false, null, null, "allowed", null, false);
        }

        private static ModerationDecision filtered(String filteredMessage, String reason, String reasonDetail) {
            return new ModerationDecision(false, filteredMessage, null, reason == null ? "filtered" : reason, reasonDetail, false);
        }

        private static ModerationDecision deny(String userMessage, String reason, String reasonDetail, boolean enforcePunishment) {
            return new ModerationDecision(true, null, userMessage, reason, reasonDetail, enforcePunishment);
        }
    }

    private record ModerationActionRule(int count, long windowSeconds, String action, long durationSeconds) {
    }

    private record EncodedBookContent(String title, List<String> pages, String message) {
    }

    private record DecodedBookContent(String title, List<String> pages) {
    }
}
