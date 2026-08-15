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
import com.google.gson.GsonBuilder;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.profanity.ProfanityFilterResult;
import dev.stemcraft.api.service.profanity.ProfanitySeverity;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public class ChatServiceImpl extends BaseService {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String BOOK_TITLE_SEPARATOR = "\n\n---BOOK-TITLE---\n\n";
    private static final String BOOK_PAGE_SEPARATOR = "\n\n---BOOK-PAGE---\n\n";
    private static final int MODERATION_DEFAULT_LIMIT = 20;
    private static final int MODERATION_MAX_LIMIT = 100;
    private static final List<ModerationActionRule> DEFAULT_MODERATION_ACTION_RULES = List.of(
        new ModerationActionRule(3, "warn", 0L),
        new ModerationActionRule(5, "kick", 0L),
        new ModerationActionRule(6, "ban", TimeUtil.parseDuration("1h")),
        new ModerationActionRule(10, "ban", TimeUtil.parseDuration("7d"))
    );
    private static final Map<ProfanitySeverity, Integer> DEFAULT_CONTENT_FILTER_SEVERITY_POINTS = Map.of(
        ProfanitySeverity.MILD, 1,
        ProfanitySeverity.MODERATE, 2,
        ProfanitySeverity.HIGH, 5,
        ProfanitySeverity.EXTREME, 10
    );

    String chatFormat;
    private Chat vaultChat;

    private String filterCommand;

    private final Map<UUID, DuplicateMessageState> duplicateMessages = new ConcurrentHashMap<>();
    private int duplicateMessageLimit;
    private String duplicateMessageWarning;
    private boolean contentFilterEnabled;
    private ProfanitySeverity contentFilterMinimumSeverity;
    private String contentFilterBlockedMessage;
    private String contentFilterWarnMessage;
    private String contentFilterKickReason;
    private String contentFilterBanReason;
    private boolean contentFilterAllowFilteredMessage;
    private boolean contentFilterStaffAlerts;
    private String contentFilterStaffAlertPermission;
    private final EnumMap<ProfanitySeverity, Integer> contentFilterSeverityPoints = new EnumMap<>(ProfanitySeverity.class);
    private int contentFilterScoreDecayAmount;
    private long contentFilterScoreDecaySeconds;
    private boolean reportsEnabled;
    private boolean reportsIncludeOnlinePlayerLocations;
    private String reportsStaffAlertPermission;
    private final Map<UUID, ViolationScoreState> contentFilterViolations = new ConcurrentHashMap<>();
    private List<ModerationActionRule> moderationActionRules = List.of();

    private boolean muted;

    public ChatServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "chat");
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
        ensureModerationStorage();
        ensureReportStorage();
        registerModerationCommands();
        registerReportCommands();

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
            if (plugin.firstJoin() != null && plugin.firstJoin().hasActiveSession(event.getPlayer().getUniqueId())) {
                return;
            }
            if (muted) {
                event.setCancelled(true);
                api.messages().warn(event.getPlayer(), "CHAT_MUTED_WARNING");
            }
        }, EventPriority.HIGH, true);

        api.events().register(AsyncChatEvent.class, event -> {
            if (plugin.firstJoin() != null && plugin.firstJoin().hasActiveSession(event.getPlayer().getUniqueId())) {
                return;
            }
            UUID uuid = event.getPlayer().getUniqueId();

            String plain = PLAIN.serialize(event.message());

            if (isDuplicateMessageBlocked(uuid, plain)) {
                event.setCancelled(true);
                recordCommunicationAudit("chat", event.getPlayer(), plain, plain, event.getPlayer().getLocation(), Map.of(
                    "result", "duplicate_blocked"
                ));
                api.tasks().nextTick(() -> api.messages().warn(event.getPlayer(), duplicateMessageWarning));
                return;
            }

            String effectiveMessage = plain;
            ModerationDecision decision = ModerationDecision.allow();
            if (contentFilterEnabled) {
                decision = moderatePlayerMessage(
                    event.getPlayer(),
                    "chat",
                    plain,
                    event.getPlayer().getLocation(),
                    Map.of("channel", "global")
                );

                if (decision.blocked()) {
                    event.setCancelled(true);
                    plugin.getLogger().warning("Blocked chat from " + event.getPlayer().getName() + ": " + plain + " (" + decision.reason() + ")");

                    ModerationOutcome outcome = applyModerationEnforcement(event.getPlayer(), "chat", decision);
                    recordCommunicationAudit("chat", event.getPlayer(), plain, null, event.getPlayer().getLocation(), buildModerationAuditDetails(decision, outcome));
                    recordModerationIncident(event.getPlayer(), "chat", plain, null, event.getPlayer().getLocation(), Map.of("channel", "global"), decision, outcome);
                    return;
                }

                if (decision.filteredMessage() != null) {
                    effectiveMessage = decision.filteredMessage();
                    event.message(Component.text(effectiveMessage));
                    ModerationOutcome outcome = ModerationOutcome.filtered();
                    recordCommunicationAudit("chat", event.getPlayer(), plain, effectiveMessage, event.getPlayer().getLocation(), buildModerationAuditDetails(decision, outcome));
                    recordModerationIncident(event.getPlayer(), "chat", plain, effectiveMessage, event.getPlayer().getLocation(), Map.of("channel", "global"), decision, outcome);
                }
            }

            if (decision.filteredMessage() == null) {
                recordCommunicationAudit("chat", event.getPlayer(), plain, effectiveMessage, event.getPlayer().getLocation(), Map.of("result", "allowed"));
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

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            if (reportsEnabled && hasReportsAlertPermission(player)) {
                alertPendingReports(player);
            }
        });

        api.events().register(SignChangeEvent.class, event -> {
            String content = event.lines().stream()
                .map(PLAIN::serialize)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            if (content.isBlank()) {
                return;
            }

            Map<String, Object> context = Map.of(
                "side", event.getSide().name().toLowerCase(Locale.ROOT),
                "line_count", event.lines().size()
            );
            ModerationDecision decision = contentFilterEnabled
                ? moderatePlayerMessage(event.getPlayer(), "sign", content, event.getBlock().getLocation(), context)
                : ModerationDecision.allow();

            if (decision.blocked()) {
                event.setCancelled(true);
                ModerationOutcome outcome = applyModerationEnforcement(event.getPlayer(), "sign", decision);
                recordCommunicationAudit("sign", event.getPlayer(), content, null, event.getBlock().getLocation(), buildModerationAuditDetails(decision, outcome));
                recordModerationIncident(event.getPlayer(), "sign", content, null, event.getBlock().getLocation(), context, decision, outcome);
                return;
            }

            String effectiveContent = content;
            if (decision.filteredMessage() != null) {
                effectiveContent = decision.filteredMessage();
                List<String> filteredLines = splitSignLines(decision.filteredMessage());
                for (int i = 0; i < 4; i++) {
                    event.line(i, Component.text(filteredLines.get(i)));
                }
                ModerationOutcome outcome = ModerationOutcome.filtered();
                recordCommunicationAudit("sign", event.getPlayer(), content, effectiveContent, event.getBlock().getLocation(), buildModerationAuditDetails(decision, outcome));
                recordModerationIncident(event.getPlayer(), "sign", content, effectiveContent, event.getBlock().getLocation(), context, decision, outcome);
            } else {
                recordCommunicationAudit("sign", event.getPlayer(), content, effectiveContent, event.getBlock().getLocation(), Map.of("result", "allowed"));
            }
        }, EventPriority.HIGH, true);

        api.events().register(PlayerQuitEvent.class,
            event -> duplicateMessages.remove(event.getPlayer().getUniqueId()));

        api.events().register(PlayerEditBookEvent.class, event -> {
            BookMeta meta = event.getNewBookMeta();
            EncodedBookContent encoded = encodeBookContent(meta);
            if (encoded.message().isBlank()) {
                return;
            }

            Map<String, Object> context = Map.of(
                "title", encoded.title() == null ? "" : encoded.title(),
                "page_count", encoded.pages().size(),
                "signing", event.isSigning()
            );
            ModerationDecision decision = contentFilterEnabled
                ? moderatePlayerMessage(event.getPlayer(), "book", encoded.message(), event.getPlayer().getLocation(), context)
                : ModerationDecision.allow();

            if (decision.blocked()) {
                event.setCancelled(true);
                ModerationOutcome outcome = applyModerationEnforcement(event.getPlayer(), "book", decision);
                recordCommunicationAudit("book", event.getPlayer(), encoded.message(), null, event.getPlayer().getLocation(), buildModerationAuditDetails(decision, outcome));
                recordModerationIncident(event.getPlayer(), "book", encoded.message(), null, event.getPlayer().getLocation(), context, decision, outcome);
                return;
            }

            if (decision.filteredMessage() != null) {
                BookMeta filteredMeta = applyFilteredBook(meta, encoded.title(), decision.filteredMessage(), event.isSigning());
                event.setNewBookMeta(filteredMeta);
                ModerationOutcome outcome = ModerationOutcome.filtered();
                recordCommunicationAudit("book", event.getPlayer(), encoded.message(), decision.filteredMessage(), event.getPlayer().getLocation(), buildModerationAuditDetails(decision, outcome));
                recordModerationIncident(event.getPlayer(), "book", encoded.message(), decision.filteredMessage(), event.getPlayer().getLocation(), context, decision, outcome);
            } else {
                recordCommunicationAudit("book", event.getPlayer(), encoded.message(), encoded.message(), event.getPlayer().getLocation(), Map.of("result", "allowed"));
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

        duplicateMessageLimit = Math.max(0, getConfigSection().getInt("duplicate_message_limit", 2));
        duplicateMessageWarning = getConfigSection().getString(
            "duplicate_message_warning", "Please do not repeat the same message");
        duplicateMessages.clear();
        contentFilterEnabled = getConfigSection().getBoolean("content_filter.enabled", true);
        contentFilterMinimumSeverity = ProfanitySeverity.fromString(
            getConfigSection().getString("content_filter.minimum_severity", "mild"),
            ProfanitySeverity.MILD
        );
        contentFilterBlockedMessage = getConfigSection().getString(
            "content_filter.blocked_message",
            "Your message was blocked. Please keep chat family friendly."
        );
        contentFilterWarnMessage = getConfigSection().getString(
            "content_filter.warn_message",
            "Your message was blocked. Please keep chat family friendly."
        );
        contentFilterKickReason = getConfigSection().getString(
            "content_filter.kick_reason",
            "Repeated use of inappropriate language."
        );
        contentFilterBanReason = getConfigSection().getString(
            "content_filter.ban_reason",
            "Severe or repeated use of inappropriate language."
        );
        contentFilterAllowFilteredMessage = getConfigSection().getBoolean("content_filter.allow_filtered_message", false);
        contentFilterStaffAlerts = getConfigSection().getBoolean("content_filter.staff_alerts", true);
        contentFilterStaffAlertPermission = getConfigSection().getString(
            "content_filter.staff_alert_permission",
            "stemcraft.moderation.alerts"
        );
        loadContentFilterScoreSettings();
        reportsEnabled = getConfigSection().getBoolean("reports.enabled", true);
        reportsIncludeOnlinePlayerLocations = getConfigSection().getBoolean("reports.include_online_player_locations", true);
        reportsStaffAlertPermission = getConfigSection().getString(
            "reports.staff_alert_permission",
            "stemcraft.moderation.alerts"
        );
        loadModerationActionRules();
    }

    boolean isDuplicateMessageBlocked(UUID playerId, String message) {
        if (duplicateMessageLimit <= 0) {
            return false;
        }
        String normalized = message.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        DuplicateMessageState previous = duplicateMessages.get(playerId);
        int count = previous != null && previous.message().equals(normalized) ? previous.count() + 1 : 1;
        duplicateMessages.put(playerId, new DuplicateMessageState(normalized, count));
        return count > duplicateMessageLimit;
    }

    void configureDuplicateMessageLimitForTest(int limit) {
        duplicateMessageLimit = Math.max(0, limit);
        duplicateMessages.clear();
    }

    private record DuplicateMessageState(String message, int count) {
    }

    private ModerationDecision moderatePlayerMessage(Player player, String messageType, String message, Location location, Map<String, Object> context) {
        if (!contentFilterEnabled || api.profanityFilter() == null || !api.profanityFilter().isEnabled()) {
            return ModerationDecision.allow();
        }

        ProfanityFilterResult result = api.profanityFilter().check(message, contentFilterMinimumSeverity);
        if (!result.offensive()) {
            return ModerationDecision.allow();
        }

        String reasonDetail = String.join(", ", result.matchedWords());
        if (contentFilterAllowFilteredMessage && !Objects.equals(result.cleanedText(), result.originalText())) {
            return ModerationDecision.filtered(result.cleanedText(), "content_filter_filtered", reasonDetail);
        }

        return ModerationDecision.deny(contentFilterBlockedMessage, "content_filter_rejected", reasonDetail, result.severity(), true);
    }

    private void ensureModerationStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS moderation_incidents (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "occurred_at INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "message_type TEXT NOT NULL," +
                "original_text TEXT NOT NULL," +
                "cleaned_text TEXT," +
                "matched_words TEXT," +
                "blocked INTEGER NOT NULL," +
                "action_taken TEXT NOT NULL," +
                "strike_count INTEGER NOT NULL," +
                "reason_code TEXT," +
                "reason_detail TEXT," +
                "world TEXT," +
                "x REAL," +
                "y REAL," +
                "z REAL," +
                "context_json TEXT," +
                "resolved INTEGER NOT NULL DEFAULT 0," +
                "resolved_at INTEGER," +
                "resolved_by_uuid TEXT," +
                "resolved_by_name TEXT," +
                "resolution_action TEXT," +
                "resolution_note TEXT" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS moderation_incidents_occurred_at_idx ON moderation_incidents(occurred_at);");
        api.database().execute("CREATE INDEX IF NOT EXISTS moderation_incidents_player_name_idx ON moderation_incidents(player_name);");
        api.database().execute("CREATE INDEX IF NOT EXISTS moderation_incidents_action_taken_idx ON moderation_incidents(action_taken);");
        api.database().execute("CREATE INDEX IF NOT EXISTS moderation_incidents_resolved_idx ON moderation_incidents(resolved);");
    }

    private void ensureReportStorage() {
        api.database().execute(
            "CREATE TABLE IF NOT EXISTS player_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "occurred_at INTEGER NOT NULL," +
                "reporter_uuid TEXT NOT NULL," +
                "reporter_name TEXT NOT NULL," +
                "message TEXT NOT NULL," +
                "world TEXT," +
                "x REAL," +
                "y REAL," +
                "z REAL," +
                "online_snapshot_json TEXT," +
                "alerted INTEGER NOT NULL DEFAULT 0," +
                "resolved INTEGER NOT NULL DEFAULT 0," +
                "resolved_at INTEGER," +
                "resolved_by_uuid TEXT," +
                "resolved_by_name TEXT," +
                "resolution_note TEXT" +
            ");"
        );
        api.database().execute("CREATE INDEX IF NOT EXISTS player_reports_occurred_at_idx ON player_reports(occurred_at);");
        api.database().execute("CREATE INDEX IF NOT EXISTS player_reports_reporter_name_idx ON player_reports(reporter_name);");
        api.database().execute("CREATE INDEX IF NOT EXISTS player_reports_resolved_idx ON player_reports(resolved);");
        api.database().execute("CREATE INDEX IF NOT EXISTS player_reports_alerted_idx ON player_reports(alerted);");
    }

    private void registerReportCommands() {
        api.commands().create("report")
            .description("Report something to moderators.")
            .usage("/report <message>")
            .executor((unused, cmd, ctx) -> {
                if (!reportsEnabled) {
                    ctx.returnError("Reporting is currently disabled.");
                    return;
                }

                Player reporter = ctx.asPlayer();
                if (reporter == null) {
                    ctx.returnError("This command must be run in-game.");
                    return;
                }

                String message = trimToNull(ctx.getArgsAsString(0, ""));
                if (message == null) {
                    ctx.returnError("Usage: /report <message>");
                    return;
                }

                PlayerReportRecord report = createPlayerReport(reporter, message);
                if (report == null) {
                    ctx.returnError("The report could not be recorded.");
                    return;
                }

                boolean alerted = notifyStaffOfReport(report);
                if (alerted) {
                    markReportAlerted(report.id());
                }

                ctx.returnSuccess("Report #" + report.id() + " recorded at " + formatModerationInstant(report.occurredAt()) + ".");
            })
            .register(plugin);

        api.commands().create("reports")
            .description("Review player reports.")
            .usage("/reports <list|show|resolve> [options]")
            .permission("stemcraft.command.reports")
            .tabCompletion("list")
            .tabCompletion("show", "{int}")
            .tabCompletion("resolve", "{int}")
            .executor((unused, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    ctx.returnUsage();
                    return;
                }

                switch (Objects.requireNonNullElse(ctx.getArgLower(0), "")) {
                    case "list" -> {
                        ctx.dropArg();
                        handleReportList(ctx);
                    }
                    case "show" -> {
                        ctx.dropArg();
                        handleReportShow(ctx);
                    }
                    case "resolve" -> {
                        ctx.dropArg();
                        handleReportResolve(ctx);
                    }
                    default -> ctx.returnUsage();
                }
            })
            .register(plugin);
    }

    private void handleReportList(dev.stemcraft.api.command.CommandContext ctx) {
        ModerationPlayerFilter playerFilter = resolveModerationPlayer(ctx.getOption("player"));
        String status = normalizeModerationText(ctx.getOption("status", "open"));
        Instant since = parseModerationTime(ctx.getOption("since"), Instant.now().minus(Duration.ofDays(7)));
        int page = parsePositiveInt(ctx.getOption("page"), 1);
        int limit = clamp(parsePositiveInt(ctx.getOption("limit"), MODERATION_DEFAULT_LIMIT), 1, MODERATION_MAX_LIMIT);

        Boolean resolved = switch (status) {
            case "all" -> null;
            case "resolved" -> true;
            default -> false;
        };

        List<PlayerReportRecord> reports = queryPlayerReports(
            playerFilter,
            resolved,
            since,
            null,
            limit,
            (page - 1) * limit,
            false
        );

        if (reports.isEmpty()) {
            ctx.returnInfo("No player reports matched the requested filters.");
            return;
        }

        ctx.info("Player reports:");
        for (PlayerReportRecord report : reports) {
            ctx.info(formatReportSummary(report));
        }
    }

    private void handleReportShow(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /reports show <id>");
            return;
        }

        long id = parseIncidentId(ctx, ctx.getArg(0), "Usage: /reports show <id>");
        PlayerReportRecord report = getPlayerReport(id);
        if (report == null) {
            ctx.returnError("Player report #" + id + " was not found.");
            return;
        }

        ctx.info("Report #" + report.id() + " " + formatModerationInstant(report.occurredAt()) + (report.resolved() ? " [resolved]" : " [open]"));
        ctx.info("Reporter: " + report.reporterName() + " [" + report.reporterUuid() + "]");
        if (report.world() != null) {
            ctx.info("Location: " + report.world() + " " + formatCoordinates(report.x(), report.y(), report.z()));
        }
        ctx.info("Message: " + report.message());
        if (report.onlineSnapshotJson() != null && !report.onlineSnapshotJson().isBlank()) {
            ctx.info("Online snapshot: " + report.onlineSnapshotJson());
        }
        if (report.resolved()) {
            ctx.info("Resolved: " + formatModerationInstant(Objects.requireNonNullElse(report.resolvedAt(), report.occurredAt())) +
                " by " + Objects.requireNonNullElse(report.resolvedByName(), "<unknown>") +
                formatSuffix(report.resolutionNote()));
        }
    }

    private void handleReportResolve(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /reports resolve <id> [note]");
            return;
        }

        long id = parseIncidentId(ctx, ctx.getArg(0), "Usage: /reports resolve <id> [note]");
        PlayerReportRecord report = getPlayerReport(id);
        if (report == null) {
            ctx.returnError("Player report #" + id + " was not found.");
            return;
        }

        String note = trimToNull(ctx.getArgsAsString(1, "Resolved"));
        resolvePlayerReport(id, ctx.asPlayer(), ctx.getSenderName(), note);
        ctx.returnSuccess("Resolved player report #" + id + ".");
    }

    private @Nullable PlayerReportRecord createPlayerReport(@NotNull Player reporter, @NotNull String message) {
        Location location = reporter.getLocation();
        String world = location.getWorld() != null ? location.getWorld().getName() : null;
        Double x = location.getX();
        Double y = location.getY();
        Double z = location.getZ();
        String snapshotJson = buildOnlineSnapshotJson();

        PlayerReportRecord report = api.database().querySingleMapped(
            "INSERT INTO player_reports (" +
                "occurred_at, reporter_uuid, reporter_name, message, world, x, y, z, online_snapshot_json, alerted, resolved" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0) " +
                "RETURNING id, occurred_at, reporter_uuid, reporter_name, message, world, x, y, z, online_snapshot_json, alerted, resolved, resolved_at, resolved_by_uuid, resolved_by_name, resolution_note",
            ps -> {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, reporter.getUniqueId().toString());
                ps.setString(3, reporter.getName());
                ps.setString(4, message);
                ps.setString(5, world);
                ps.setDouble(6, x);
                ps.setDouble(7, y);
                ps.setDouble(8, z);
                ps.setString(9, snapshotJson);
            },
            rs -> mapPlayerReport(rs)
        );

        if (report != null) {
            recordCommunicationAudit("report", reporter, message, message, location, Map.of("report_id", report.id()));
        }
        return report;
    }

    private @Nullable String buildOnlineSnapshotJson() {
        if (!reportsIncludeOnlinePlayerLocations) {
            List<Map<String, Object>> summary = Bukkit.getOnlinePlayers().stream()
                .map(player -> Map.<String, Object>of(
                    "uuid", player.getUniqueId().toString(),
                    "name", player.getName()
                ))
                .toList();
            return summary.isEmpty() ? null : GSON.toJson(summary);
        }

        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uuid", player.getUniqueId().toString());
            item.put("name", player.getName());
            item.put("world", location.getWorld() == null ? null : location.getWorld().getName());
            item.put("x", Math.round(location.getX()));
            item.put("y", Math.round(location.getY()));
            item.put("z", Math.round(location.getZ()));
            snapshot.add(item);
        }
        return snapshot.isEmpty() ? null : GSON.toJson(snapshot);
    }

    private boolean notifyStaffOfReport(@NotNull PlayerReportRecord report) {
        List<Player> alertedPlayers = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (hasReportsAlertPermission(player)) {
                alertedPlayers.add(player);
            }
        });
        if (alertedPlayers.isEmpty()) {
            plugin.getLogger().warning(formatReportAlert(report));
            return false;
        }

        String message = formatReportAlert(report);
        for (Player player : alertedPlayers) {
            api.messages().warn(player, message);
        }
        plugin.getLogger().warning(message);
        return true;
    }

    private void alertPendingReports(@NotNull Player player) {
        List<PlayerReportRecord> pending = queryPlayerReports(null, false, null, null, MODERATION_MAX_LIMIT, 0, true).stream()
            .filter(report -> !report.alerted())
            .toList();
        if (pending.isEmpty()) {
            return;
        }

        api.messages().warn(player, "There are " + pending.size() + " unresolved player report(s) needing review.");
        for (PlayerReportRecord report : pending) {
            api.messages().warn(player, formatReportAlert(report));
            markReportAlerted(report.id());
        }
    }

    private boolean hasReportsAlertPermission(@NotNull Player player) {
        String permission = trimToNull(reportsStaffAlertPermission);
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private void markReportAlerted(long id) {
        api.database().update("UPDATE player_reports SET alerted = 1 WHERE id = ?", ps -> ps.setLong(1, id));
    }

    private @Nullable PlayerReportRecord getPlayerReport(long id) {
        return api.database().querySingleMapped(
            "SELECT id, occurred_at, reporter_uuid, reporter_name, message, world, x, y, z, online_snapshot_json, alerted, resolved, resolved_at, resolved_by_uuid, resolved_by_name, resolution_note " +
                "FROM player_reports WHERE id = ?",
            ps -> ps.setLong(1, id),
            rs -> mapPlayerReport(rs)
        );
    }

    private List<PlayerReportRecord> queryPlayerReports(@Nullable ModerationPlayerFilter playerFilter,
                                                        @Nullable Boolean resolved,
                                                        @Nullable Instant since,
                                                        @Nullable Instant until,
                                                        int limit,
                                                        int offset,
                                                        boolean ascending) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, occurred_at, reporter_uuid, reporter_name, message, world, x, y, z, online_snapshot_json, alerted, resolved, resolved_at, resolved_by_uuid, resolved_by_name, resolution_note " +
                "FROM player_reports WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (playerFilter != null) {
            sql.append(" AND (");
            boolean wrote = false;
            if (playerFilter.uuid() != null) {
                sql.append("reporter_uuid = ?");
                params.add(playerFilter.uuid().toString());
                wrote = true;
            }
            if (playerFilter.name() != null) {
                if (wrote) {
                    sql.append(" OR ");
                }
                sql.append("LOWER(reporter_name) = ?");
                params.add(playerFilter.name().toLowerCase(Locale.ROOT));
            }
            sql.append(')');
        }
        if (resolved != null) {
            sql.append(" AND resolved = ?");
            params.add(resolved ? 1 : 0);
        }
        if (since != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(since.toEpochMilli());
        }
        if (until != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(until.toEpochMilli());
        }

        sql.append(" ORDER BY occurred_at ").append(ascending ? "ASC" : "DESC").append(", id ").append(ascending ? "ASC" : "DESC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<PlayerReportRecord> reports = new ArrayList<>();
        api.database().queryEach(sql.toString(), ps -> bindModerationParams(ps, params), rs -> reports.add(mapPlayerReport(rs)));
        return reports;
    }

    private PlayerReportRecord mapPlayerReport(java.sql.ResultSet rs) throws java.sql.SQLException {
        Double x = rs.getDouble("x");
        if (rs.wasNull()) {
            x = null;
        }
        Double y = rs.getDouble("y");
        if (rs.wasNull()) {
            y = null;
        }
        Double z = rs.getDouble("z");
        if (rs.wasNull()) {
            z = null;
        }

        long resolvedAtRaw = rs.getLong("resolved_at");
        Instant resolvedAt = rs.wasNull() ? null : Instant.ofEpochMilli(resolvedAtRaw);

        return new PlayerReportRecord(
            rs.getLong("id"),
            Instant.ofEpochMilli(rs.getLong("occurred_at")),
            UUID.fromString(rs.getString("reporter_uuid")),
            rs.getString("reporter_name"),
            rs.getString("message"),
            rs.getString("world"),
            x,
            y,
            z,
            rs.getString("online_snapshot_json"),
            rs.getInt("alerted") == 1,
            rs.getInt("resolved") == 1,
            resolvedAt,
            parseModerationUuid(rs.getString("resolved_by_uuid")),
            rs.getString("resolved_by_name"),
            rs.getString("resolution_note")
        );
    }

    private void resolvePlayerReport(long id, @Nullable Player actor, @NotNull String actorName, @Nullable String note) {
        api.database().update(
            "UPDATE player_reports SET resolved = 1, resolved_at = ?, resolved_by_uuid = ?, resolved_by_name = ?, resolution_note = ? WHERE id = ?",
            ps -> {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, actor != null ? actor.getUniqueId().toString() : null);
                ps.setString(3, actorName);
                ps.setString(4, note);
                ps.setLong(5, id);
            }
        );
    }

    private String formatReportSummary(PlayerReportRecord report) {
        return "#" + report.id() + " " + formatModerationInstant(report.occurredAt()) + " " + report.reporterName() +
            (report.resolved() ? " [resolved]" : " [open]") +
            " [" + clip(report.message().replace('\n', ' '), 72) + "]";
    }

    private String formatReportAlert(PlayerReportRecord report) {
        return "[Report] #" + report.id() + " " + report.reporterName() + " at " + formatModerationInstant(report.occurredAt()) +
            ": " + clip(report.message().replace('\n', ' '), 80);
    }

    private void registerModerationCommands() {
        api.commands().create("moderation")
            .description("Review and manage moderation incidents.")
            .usage("/moderation <list|show|context|resolve|undo|score|setscore|clearscore|strikes|clearstrikes> [options]")
            .permission("stemcraft.command.moderation")
            .tabCompletion("list")
            .tabCompletion("show", "{int}")
            .tabCompletion("context", "{int}")
            .tabCompletion("resolve", "{int}")
            .tabCompletion("undo", "{int}")
            .tabCompletion("score", "{player}")
            .tabCompletion("setscore", "{player}", "{int}")
            .tabCompletion("clearscore", "{player}")
            .tabCompletion("strikes", "{player}")
            .tabCompletion("clearstrikes", "{player}")
            .executor((unused, cmd, ctx) -> {
                if (ctx.args().isEmpty()) {
                    ctx.returnUsage();
                    return;
                }

                switch (Objects.requireNonNullElse(ctx.getArgLower(0), "")) {
                    case "list" -> {
                        ctx.dropArg();
                        handleModerationList(ctx);
                    }
                    case "show" -> {
                        ctx.dropArg();
                        handleModerationShow(ctx);
                    }
                    case "context" -> {
                        ctx.dropArg();
                        handleModerationContext(ctx);
                    }
                    case "resolve" -> {
                        ctx.dropArg();
                        handleModerationResolve(ctx);
                    }
                    case "undo" -> {
                        ctx.dropArg();
                        handleModerationUndo(ctx);
                    }
                    case "score", "strikes" -> {
                        ctx.dropArg();
                        handleModerationScore(ctx);
                    }
                    case "setscore" -> {
                        ctx.dropArg();
                        handleModerationSetScore(ctx);
                    }
                    case "clearscore", "clearstrikes" -> {
                        ctx.dropArg();
                        handleModerationClearScore(ctx);
                    }
                    default -> ctx.returnUsage();
                }
            })
            .register(plugin);
    }

    private void handleModerationList(dev.stemcraft.api.command.CommandContext ctx) {
        ModerationPlayerFilter playerFilter = resolveModerationPlayer(ctx.getOption("player"));
        String messageType = trimToNull(ctx.getOption("type"));
        String action = trimToNull(ctx.getOption("action"));
        String status = normalizeModerationText(ctx.getOption("status", "open"));
        Instant since = parseModerationTime(ctx.getOption("since"), Instant.now().minus(Duration.ofDays(7)));
        int page = parsePositiveInt(ctx.getOption("page"), 1);
        int limit = clamp(parsePositiveInt(ctx.getOption("limit"), MODERATION_DEFAULT_LIMIT), 1, MODERATION_MAX_LIMIT);

        Boolean resolved = switch (status) {
            case "all" -> null;
            case "resolved" -> true;
            default -> false;
        };

        List<ModerationIncidentRecord> incidents = queryModerationIncidents(
            playerFilter,
            messageType,
            action,
            resolved,
            since,
            null,
            limit,
            (page - 1) * limit,
            false
        );

        if (incidents.isEmpty()) {
            ctx.returnInfo("No moderation incidents matched the requested filters.");
            return;
        }

        ctx.info("Moderation incidents:");
        for (ModerationIncidentRecord incident : incidents) {
            ctx.info(formatModerationSummary(incident));
        }
    }

    private void handleModerationShow(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /moderation show <id>");
            return;
        }

        long id = parseIncidentId(ctx, ctx.getArg(0), "Usage: /moderation show <id>");
        ModerationIncidentRecord incident = getModerationIncident(id);
        if (incident == null) {
            ctx.returnError("Moderation incident #" + id + " was not found.");
            return;
        }

        ctx.info("Incident #" + incident.id() + " " + formatModerationInstant(incident.occurredAt()) + " " + incident.messageType() + " " + incident.actionTaken());
        ctx.info("Player: " + incident.playerName() + " [" + incident.playerUuid() + "]");
        ctx.info("Blocked: " + incident.blocked() + " | Score: " + incident.strikeCount());
        if (incident.reasonCode() != null) {
            ctx.info("Reason: " + incident.reasonCode() + formatSuffix(incident.reasonDetail()));
        }
        ctx.info("Original: " + incident.originalText());
        if (incident.cleanedText() != null && !Objects.equals(incident.cleanedText(), incident.originalText())) {
            ctx.info("Cleaned: " + incident.cleanedText());
        }
        if (incident.matchedWords() != null && !incident.matchedWords().isBlank()) {
            ctx.info("Matched: " + incident.matchedWords());
        }
        if (incident.world() != null) {
            ctx.info("Location: " + incident.world() + " " + formatCoordinates(incident.x(), incident.y(), incident.z()));
        }
        if (incident.contextJson() != null && !incident.contextJson().isBlank()) {
            ctx.info("Context: " + incident.contextJson());
        }
        if (incident.resolved()) {
            ctx.info("Resolved: " + formatModerationInstant(Objects.requireNonNullElse(incident.resolvedAt(), incident.occurredAt())) +
                " by " + Objects.requireNonNullElse(incident.resolvedByName(), "<unknown>") +
                " [" + Objects.requireNonNullElse(incident.resolutionAction(), "resolve") + "]" +
                formatSuffix(incident.resolutionNote()));
        }
    }

    private void handleModerationContext(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /moderation context <id> [window]");
            return;
        }

        long id = parseIncidentId(ctx, ctx.getArg(0), "Usage: /moderation context <id> [window]");
        ModerationIncidentRecord incident = getModerationIncident(id);
        if (incident == null) {
            ctx.returnError("Moderation incident #" + id + " was not found.");
            return;
        }

        long windowSeconds = parseDurationSeconds(ctx.getArg(1, "5m"));
        if (windowSeconds <= 0L) {
            windowSeconds = TimeUtil.parseDuration("5m");
        }

        List<AuditEventRecord> contextEvents = plugin.audit().findCommunicationContext(incident.occurredAt(), windowSeconds, null);
        if (contextEvents.isEmpty()) {
            ctx.returnInfo("No audit context was found for incident #" + incident.id() + ".");
            return;
        }

        ctx.info("Context around incident #" + incident.id() + ":");
        for (AuditEventRecord event : contextEvents) {
            ctx.info((event.occurredAt().equals(incident.occurredAt()) ? "* " : "  ") + formatAuditContextLine(event));
        }
    }

    private void handleModerationResolve(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /moderation resolve <id> [note]");
            return;
        }

        long id = parseIncidentId(ctx, ctx.getArg(0), "Usage: /moderation resolve <id> [note]");
        ModerationIncidentRecord incident = getModerationIncident(id);
        if (incident == null) {
            ctx.returnError("Moderation incident #" + id + " was not found.");
            return;
        }

        String note = trimToNull(ctx.getArgsAsString(1, ""));
        updateIncidentResolution(id, ctx.asPlayer(), ctx.getSenderName(), "resolve", note);
        ctx.returnSuccess("Marked moderation incident #" + id + " as resolved.");
    }

    private void handleModerationUndo(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /moderation undo <id> [reason]");
            return;
        }

        long id = parseIncidentId(ctx, ctx.getArg(0), "Usage: /moderation undo <id> [reason]");
        ModerationIncidentRecord incident = getModerationIncident(id);
        if (incident == null) {
            ctx.returnError("Moderation incident #" + id + " was not found.");
            return;
        }

        String reason = trimToNull(ctx.getArgsAsString(1, "Appeal accepted"));
        int clearedStrikes = clearViolations(incident.playerUuid());
        boolean unbanned = false;
        if ("ban".equalsIgnoreCase(incident.actionTaken())) {
            unbanned = plugin.punishments().pardonActiveBan(incident.playerUuid(), ctx.asPlayer(), reason, ctx.getSenderName());
        }

        String note = reason + " | cleared_score=" + clearedStrikes + " | ban_reversed=" + unbanned;
        updateIncidentResolution(id, ctx.asPlayer(), ctx.getSenderName(), "undo", note);
        ctx.returnSuccess("Undid moderation incident #" + id + ".");
    }

    private void handleModerationScore(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /moderation score <player>");
            return;
        }

        OfflinePlayer target = ctx.getArgAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null) {
            ctx.returnError("Player was not found.");
            return;
        }

        int count = getActiveViolationCount(target.getUniqueId());
        String playerName = Objects.requireNonNullElse(target.getName(), target.getUniqueId().toString());
        ctx.info("Active content-filter score for " + playerName + ": " + count);
        if (ctx.isPlayer()) {
            ctx.getSender().sendMessage(
                Component.text("[Set]", net.kyori.adventure.text.format.NamedTextColor.BLUE)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/moderation setscore " + playerName + " " + count))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Set this player's score")))
                    .append(Component.text(" ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(
                        Component.text("[Clear]", net.kyori.adventure.text.format.NamedTextColor.RED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/moderation clearscore " + playerName))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Clear this player's score")))
                    )
            );
        }
    }

    private void handleModerationSetScore(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().size() < 2) {
            ctx.returnError("Usage: /moderation setscore <player> <value>");
            return;
        }

        OfflinePlayer target = ctx.getArgAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null) {
            ctx.returnError("Player was not found.");
            return;
        }

        int score = ctx.getArgAsInt(1, Integer.MIN_VALUE, 0, null);
        if (score == Integer.MIN_VALUE) {
            ctx.returnError("Usage: /moderation setscore <player> <value>");
            return;
        }

        setViolationScore(target.getUniqueId(), score);
        ctx.returnSuccess("Set active content-filter score for " + Objects.requireNonNullElse(target.getName(), target.getUniqueId().toString()) + " to " + score + ".");
    }

    private void handleModerationClearScore(dev.stemcraft.api.command.CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.returnError("Usage: /moderation clearscore <player>");
            return;
        }

        OfflinePlayer target = ctx.getArgAsOfflinePlayer(0);
        if (target == null || target.getUniqueId() == null) {
            ctx.returnError("Player was not found.");
            return;
        }

        int cleared = clearViolations(target.getUniqueId());
        ctx.returnSuccess("Cleared " + cleared + " active content-filter score for " + Objects.requireNonNullElse(target.getName(), target.getUniqueId().toString()) + ".");
    }

    private void recordModerationIncident(Player player,
                                          String messageType,
                                          String originalText,
                                          @Nullable String cleanedText,
                                          @Nullable Location location,
                                          Map<String, Object> context,
                                          ModerationDecision decision,
                                          ModerationOutcome outcome) {
        String world = location != null && location.getWorld() != null ? location.getWorld().getName() : null;
        Double x = location != null ? location.getX() : null;
        Double y = location != null ? location.getY() : null;
        Double z = location != null ? location.getZ() : null;
        String contextJson = context.isEmpty() ? null : GSON.toJson(context);

        ModerationIncidentRecord incident = api.database().querySingleMapped(
            "INSERT INTO moderation_incidents (" +
                "occurred_at, player_uuid, player_name, message_type, original_text, cleaned_text, matched_words, blocked, action_taken, strike_count, " +
                "reason_code, reason_detail, world, x, y, z, context_json, resolved" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0) " +
                "RETURNING id, occurred_at, player_uuid, player_name, message_type, original_text, cleaned_text, matched_words, blocked, action_taken, strike_count, " +
                "reason_code, reason_detail, world, x, y, z, context_json, resolved, resolved_at, resolved_by_uuid, resolved_by_name, resolution_action, resolution_note",
            ps -> {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getName());
                ps.setString(4, messageType);
                ps.setString(5, originalText);
                ps.setString(6, cleanedText);
                ps.setString(7, trimToNull(decision.reasonDetail()));
                ps.setInt(8, decision.blocked() ? 1 : 0);
                ps.setString(9, outcome.actionTaken());
                ps.setInt(10, outcome.strikeCount());
                ps.setString(11, decision.reason());
                ps.setString(12, decision.reasonDetail());
                ps.setString(13, world);
                if (x == null) {
                    ps.setNull(14, java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(14, x);
                }
                if (y == null) {
                    ps.setNull(15, java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(15, y);
                }
                if (z == null) {
                    ps.setNull(16, java.sql.Types.DOUBLE);
                } else {
                    ps.setDouble(16, z);
                }
                ps.setString(17, contextJson);
            },
            rs -> mapModerationIncident(rs)
        );
        if (incident != null) {
            alertStaff(incident);
        }
    }

    private void recordCommunicationAudit(String category,
                                         Player player,
                                         String content,
                                         @Nullable String effectiveContent,
                                         @Nullable Location location,
                                         Map<String, Object> details) {
        if (plugin.audit() == null) {
            return;
        }
        plugin.audit().recordCommunication(category, player, content, effectiveContent, location, details);
    }

    private Map<String, Object> buildModerationAuditDetails(ModerationDecision decision, ModerationOutcome outcome) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", outcome.actionTaken());
        details.put("blocked", decision.blocked());
        if (decision.reason() != null) {
            details.put("reason", decision.reason());
        }
        if (decision.reasonDetail() != null) {
            details.put("matched_words", decision.reasonDetail());
        }
        details.put("score_total", outcome.strikeCount());
        details.put("score_points", outcome.pointsAdded());
        if (decision.severity() != null) {
            details.put("severity", decision.severity().name().toLowerCase(Locale.ROOT));
        }
        return details;
    }

    private void alertStaff(ModerationIncidentRecord incident) {
        if (!contentFilterStaffAlerts) {
            return;
        }

        String permission = trimToNull(contentFilterStaffAlertPermission);
        String message = "[Moderation] #" + incident.id() + " " + incident.playerName() + " " + incident.messageType() +
            " -> " + incident.actionTaken() + " [" + clip(incident.originalText().replace('\n', ' '), 64) + "]";

        Bukkit.getOnlinePlayers().forEach(player -> {
            if (permission == null || permission.isBlank() || player.hasPermission(permission)) {
                api.messages().warn(player, message);
            }
        });
        plugin.getLogger().warning(message);
    }

    private @Nullable ModerationIncidentRecord getModerationIncident(long id) {
        return api.database().querySingleMapped(
            "SELECT id, occurred_at, player_uuid, player_name, message_type, original_text, cleaned_text, matched_words, blocked, action_taken, strike_count, " +
                "reason_code, reason_detail, world, x, y, z, context_json, resolved, resolved_at, resolved_by_uuid, resolved_by_name, resolution_action, resolution_note " +
                "FROM moderation_incidents WHERE id = ?",
            ps -> ps.setLong(1, id),
            rs -> mapModerationIncident(rs)
        );
    }

    private List<ModerationIncidentRecord> queryModerationIncidents(@Nullable ModerationPlayerFilter playerFilter,
                                                                    @Nullable String messageType,
                                                                    @Nullable String action,
                                                                    @Nullable Boolean resolved,
                                                                    @Nullable Instant since,
                                                                    @Nullable Instant until,
                                                                    int limit,
                                                                    int offset,
                                                                    boolean ascending) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, occurred_at, player_uuid, player_name, message_type, original_text, cleaned_text, matched_words, blocked, action_taken, strike_count, " +
                "reason_code, reason_detail, world, x, y, z, context_json, resolved, resolved_at, resolved_by_uuid, resolved_by_name, resolution_action, resolution_note " +
                "FROM moderation_incidents WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (playerFilter != null) {
            sql.append(" AND (");
            boolean wrote = false;
            if (playerFilter.uuid() != null) {
                sql.append("player_uuid = ?");
                params.add(playerFilter.uuid().toString());
                wrote = true;
            }
            if (playerFilter.name() != null) {
                if (wrote) {
                    sql.append(" OR ");
                }
                sql.append("LOWER(player_name) = ?");
                params.add(playerFilter.name().toLowerCase(Locale.ROOT));
            }
            sql.append(')');
        }
        if (messageType != null) {
            sql.append(" AND message_type = ?");
            params.add(normalizeModerationText(messageType));
        }
        if (action != null) {
            sql.append(" AND action_taken = ?");
            params.add(normalizeModerationText(action));
        }
        if (resolved != null) {
            sql.append(" AND resolved = ?");
            params.add(resolved ? 1 : 0);
        }
        if (since != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(since.toEpochMilli());
        }
        if (until != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(until.toEpochMilli());
        }

        sql.append(" ORDER BY occurred_at ").append(ascending ? "ASC" : "DESC").append(", id ").append(ascending ? "ASC" : "DESC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<ModerationIncidentRecord> incidents = new ArrayList<>();
        api.database().queryEach(sql.toString(), ps -> bindModerationParams(ps, params), rs -> incidents.add(mapModerationIncident(rs)));
        return incidents;
    }

    private ModerationIncidentRecord mapModerationIncident(java.sql.ResultSet rs) throws java.sql.SQLException {
        Double x = rs.getDouble("x");
        if (rs.wasNull()) {
            x = null;
        }
        Double y = rs.getDouble("y");
        if (rs.wasNull()) {
            y = null;
        }
        Double z = rs.getDouble("z");
        if (rs.wasNull()) {
            z = null;
        }

        Long resolvedAt = rs.getLong("resolved_at");
        Instant resolvedInstant = rs.wasNull() ? null : Instant.ofEpochMilli(resolvedAt);

        return new ModerationIncidentRecord(
            rs.getLong("id"),
            Instant.ofEpochMilli(rs.getLong("occurred_at")),
            UUID.fromString(rs.getString("player_uuid")),
            rs.getString("player_name"),
            rs.getString("message_type"),
            rs.getString("original_text"),
            rs.getString("cleaned_text"),
            rs.getString("matched_words"),
            rs.getInt("blocked") == 1,
            rs.getString("action_taken"),
            rs.getInt("strike_count"),
            rs.getString("reason_code"),
            rs.getString("reason_detail"),
            rs.getString("world"),
            x,
            y,
            z,
            rs.getString("context_json"),
            rs.getInt("resolved") == 1,
            resolvedInstant,
            parseModerationUuid(rs.getString("resolved_by_uuid")),
            rs.getString("resolved_by_name"),
            rs.getString("resolution_action"),
            rs.getString("resolution_note")
        );
    }

    private void updateIncidentResolution(long id,
                                          @Nullable Player actor,
                                          String actorName,
                                          String resolutionAction,
                                          @Nullable String note) {
        api.database().update(
            "UPDATE moderation_incidents SET resolved = 1, resolved_at = ?, resolved_by_uuid = ?, resolved_by_name = ?, resolution_action = ?, resolution_note = ? WHERE id = ?",
            ps -> {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, actor != null ? actor.getUniqueId().toString() : null);
                ps.setString(3, actorName);
                ps.setString(4, normalizeModerationText(resolutionAction));
                ps.setString(5, note);
                ps.setLong(6, id);
            }
        );
    }

    private void bindModerationParams(java.sql.PreparedStatement ps, List<Object> params) throws java.sql.SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            int index = i + 1;
            if (value instanceof String stringValue) {
                ps.setString(index, stringValue);
            } else if (value instanceof Integer intValue) {
                ps.setInt(index, intValue);
            } else if (value instanceof Long longValue) {
                ps.setLong(index, longValue);
            } else {
                ps.setObject(index, value);
            }
        }
    }

    private @Nullable ModerationPlayerFilter resolveModerationPlayer(@Nullable String rawPlayer) {
        String name = trimToNull(rawPlayer);
        if (name == null) {
            return null;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        return new ModerationPlayerFilter(offlinePlayer != null ? offlinePlayer.getUniqueId() : null, name);
    }

    private long parseIncidentId(dev.stemcraft.api.command.CommandContext ctx, @Nullable String raw, String usage) {
        if (raw == null) {
            ctx.returnError(usage);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            ctx.returnError(usage);
            return -1L;
        }
    }

    private int parsePositiveInt(@Nullable String raw, int defaultValue) {
        String value = trimToNull(raw);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private @Nullable Instant parseModerationTime(@Nullable String raw, @Nullable Instant defaultValue) {
        String value = trimToNull(raw);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Instant.now().minusSeconds(TimeUtil.parseDuration(value));
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }

    private @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private @Nullable String normalizeModerationText(@Nullable String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private @Nullable UUID parseModerationUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String formatModerationSummary(ModerationIncidentRecord incident) {
        return "#" + incident.id() + " " + formatModerationInstant(incident.occurredAt()) + " " + incident.playerName() + " " +
            incident.messageType() + " -> " + incident.actionTaken() +
            " score=" + incident.strikeCount() +
            (incident.resolved() ? " [resolved]" : " [open]") +
            " [" + clip(incident.originalText().replace('\n', ' '), 64) + "]";
    }

    private void loadContentFilterScoreSettings() {
        contentFilterSeverityPoints.clear();
        for (ProfanitySeverity severity : ProfanitySeverity.values()) {
            int defaultPoints = DEFAULT_CONTENT_FILTER_SEVERITY_POINTS.getOrDefault(severity, Math.max(1, severity.ordinal() + 1));
            int configuredPoints = getConfigSection().getInt(
                "content_filter.severity_points." + severity.name().toLowerCase(Locale.ROOT),
                defaultPoints
            );
            contentFilterSeverityPoints.put(severity, Math.max(0, configuredPoints));
        }

        contentFilterScoreDecayAmount = Math.max(0, getConfigSection().getInt("content_filter.score_decay.amount", 1));
        contentFilterScoreDecaySeconds = parseDurationSeconds(getConfigSection().getString("content_filter.score_decay.every", "1h"));
        if (contentFilterScoreDecayAmount > 0 && contentFilterScoreDecaySeconds <= 0L) {
            contentFilterScoreDecaySeconds = TimeUtil.parseDuration("1h");
        }
    }

    void configureContentFilterScoringForTest(@NotNull Map<ProfanitySeverity, Integer> severityPoints, int decayAmount, long decaySeconds) {
        contentFilterSeverityPoints.clear();
        contentFilterSeverityPoints.putAll(severityPoints);
        contentFilterScoreDecayAmount = decayAmount;
        contentFilterScoreDecaySeconds = decaySeconds;
    }

    Map<ProfanitySeverity, Integer> contentFilterSeverityPoints() {
        return Map.copyOf(contentFilterSeverityPoints);
    }

    int contentFilterScoreDecayAmount() {
        return contentFilterScoreDecayAmount;
    }

    long contentFilterScoreDecaySeconds() {
        return contentFilterScoreDecaySeconds;
    }

    List<ModerationActionRule> moderationActionRules() {
        return List.copyOf(moderationActionRules);
    }

    void updateContentFilterSeverityPoints(@NotNull ProfanitySeverity severity, int points) {
        contentFilterSeverityPoints.put(severity, Math.max(0, points));
        getConfigSection().set("content_filter.severity_points." + severity.name().toLowerCase(Locale.ROOT), Math.max(0, points));
        getRootConfigSection().save();
    }

    void updateContentFilterScoreDecay(int amount, long decaySeconds) {
        contentFilterScoreDecayAmount = Math.max(0, amount);
        contentFilterScoreDecaySeconds = Math.max(0L, decaySeconds);
        getConfigSection().set("content_filter.score_decay.amount", contentFilterScoreDecayAmount);
        if (contentFilterScoreDecaySeconds <= 0L) {
            getConfigSection().set("content_filter.score_decay.every", "0s");
        } else {
            getConfigSection().set("content_filter.score_decay.every", TimeUtil.formatDuration(contentFilterScoreDecaySeconds));
        }
        getRootConfigSection().save();
    }

    void addModerationActionRule(int threshold, @NotNull String action, long durationSeconds) {
        List<ModerationActionRule> rules = new ArrayList<>(moderationActionRules);
        rules.add(new ModerationActionRule(Math.max(1, threshold), normalizeModerationText(action), Math.max(0L, durationSeconds)));
        saveModerationActionRules(rules);
    }

    void updateModerationActionRule(int index, int threshold, @NotNull String action, long durationSeconds) {
        List<ModerationActionRule> rules = new ArrayList<>(moderationActionRules);
        if (index < 1 || index > rules.size()) {
            throw new IllegalArgumentException("Unknown moderation action index: " + index);
        }
        rules.set(index - 1, new ModerationActionRule(Math.max(1, threshold), normalizeModerationText(action), Math.max(0L, durationSeconds)));
        saveModerationActionRules(rules);
    }

    void removeModerationActionRule(int index) {
        List<ModerationActionRule> rules = new ArrayList<>(moderationActionRules);
        if (index < 1 || index > rules.size()) {
            throw new IllegalArgumentException("Unknown moderation action index: " + index);
        }
        rules.remove(index - 1);
        saveModerationActionRules(rules);
    }

    void setViolationScore(@NotNull UUID playerUuid, int score) {
        int normalized = Math.max(0, score);
        if (normalized == 0) {
            contentFilterViolations.remove(playerUuid);
            return;
        }

        ViolationScoreState state = contentFilterViolations.computeIfAbsent(playerUuid, ignored -> new ViolationScoreState());
        synchronized (state) {
            state.score = normalized;
            state.updatedAt = Instant.now();
        }
    }

    private void saveModerationActionRules(@NotNull List<ModerationActionRule> rules) {
        rules.sort(Comparator.comparingInt(ModerationActionRule::threshold));
        var actionsSection = getConfigSection().createSection("content_filter.actions", true);
        int index = 1;
        for (ModerationActionRule rule : rules) {
            String basePath = Integer.toString(index++);
            actionsSection.set(basePath + ".threshold", rule.threshold());
            actionsSection.set(basePath + ".action", rule.action());
            if (rule.durationSeconds() > 0L) {
                actionsSection.set(basePath + ".duration", TimeUtil.formatDuration(rule.durationSeconds()));
            } else {
                actionsSection.remove(basePath + ".duration");
            }
        }
        getRootConfigSection().save();
        moderationActionRules = List.copyOf(rules);
    }

    private String formatModerationInstant(Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant);
    }

    private String formatSuffix(@Nullable String text) {
        return text == null || text.isBlank() ? "" : " (" + text + ")";
    }

    private String formatCoordinates(@Nullable Double x, @Nullable Double y, @Nullable Double z) {
        if (x == null || y == null || z == null) {
            return "(unknown)";
        }
        return Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z);
    }

    private String formatAuditContextLine(AuditEventRecord event) {
        return event.id() + " " + formatModerationInstant(event.occurredAt()) + " " + event.category() + " " + event.actorName() +
            (event.content() == null ? "" : " " + clip(event.content().replace('\n', ' '), 72));
    }

    private String clip(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
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

                    int threshold = ruleSection.getInt("threshold", Integer.MIN_VALUE);
                    if (threshold == Integer.MIN_VALUE) {
                        threshold = ruleSection.getInt("count", 0);
                    }
                    String action = ruleSection.getString("action", "warn").trim().toLowerCase(Locale.ROOT);
                    long durationSeconds = parseDurationSeconds(ruleSection.getString("duration", ""));

                    if (threshold <= 0 || action.isBlank()) {
                        continue;
                    }

                    loadedRules.add(new ModerationActionRule(threshold, action, durationSeconds));
                }
            }
        }

        if (loadedRules.isEmpty()) {
            loadedRules.addAll(DEFAULT_MODERATION_ACTION_RULES);
        }

        loadedRules.sort(Comparator.comparingInt(ModerationActionRule::threshold));
        moderationActionRules = loadedRules;
    }

    private void writeDefaultModerationActionRules() {
        var actionsSection = getConfigSection().createSection("content_filter.actions", true);
        int index = 1;
        for (ModerationActionRule rule : DEFAULT_MODERATION_ACTION_RULES) {
            String basePath = Integer.toString(index++);
            actionsSection.set(basePath + ".threshold", rule.threshold());
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

    private long parseDurationSeconds(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        try {
            return TimeUtil.parseDuration(text);
        } catch (IllegalArgumentException ignored) {
            return 0L;
        }
    }

    private ModerationOutcome applyModerationEnforcement(Player player, String messageType, ModerationDecision decision) {
        if (decision.enforcePunishment()) {
            return applyModerationViolation(player, messageType, decision);
        }

        api.tasks().nextTick(() -> api.messages().error(player, decision.userMessage()));
        return ModerationOutcome.blocked(0, 0);
    }

    private ModerationOutcome applyModerationViolation(Player player, String messageType, ModerationDecision decision) {
        if (filterCommand != null && !filterCommand.isBlank()) {
            String cmd = filterCommand.replace("{player}", player.getName());
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(console, cmd));
        }

        ViolationStatus status = recordViolation(player.getUniqueId(), decision.severity(), Instant.now());
        ModerationActionRule actionRule = status.actionRule();

        if (actionRule == null) {
            api.tasks().nextTick(() -> api.messages().error(player, contentFilterBlockedMessage));
            return ModerationOutcome.blocked(status.activeScore(), status.pointsAdded());
        }

        switch (actionRule.action()) {
            case "ban" -> {
                applyContentFilterBan(player, actionRule, buildContentFilterBanReason(messageType, decision));
                return ModerationOutcome.of("ban", status.activeScore(), status.pointsAdded());
            }
            case "kick" -> {
                applyContentFilterKick(player, buildContentFilterKickReason(messageType, decision));
                return ModerationOutcome.of("kick", status.activeScore(), status.pointsAdded());
            }
            case "warn" -> {
                plugin.punishments().record(player.getUniqueId(), null, null, "warn", false, buildContentFilterWarnReason(messageType, decision));
                api.tasks().nextTick(() -> api.messages().error(player, contentFilterWarnMessage));
                return ModerationOutcome.of("warn", status.activeScore(), status.pointsAdded());
            }
            default -> {
                api.tasks().nextTick(() -> api.messages().error(player, contentFilterBlockedMessage));
                return ModerationOutcome.blocked(status.activeScore(), status.pointsAdded());
            }
        }
    }

    ViolationStatus recordViolation(UUID playerUuid, @Nullable ProfanitySeverity severity, @NotNull Instant now) {
        int pointsAdded = scorePointsForSeverity(severity);
        ViolationScoreState state = contentFilterViolations.computeIfAbsent(playerUuid, ignored -> new ViolationScoreState());

        synchronized (state) {
            int activeScore = decayViolationScore(state, now);
            activeScore = Math.max(0, activeScore + pointsAdded);
            state.score = activeScore;
            state.updatedAt = now;

            if (activeScore <= 0) {
                contentFilterViolations.remove(playerUuid, state);
            }

            return new ViolationStatus(activeScore, pointsAdded, findMatchedRule(activeScore));
        }
    }

    private int clearViolations(UUID playerUuid) {
        ViolationScoreState state = contentFilterViolations.remove(playerUuid);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            int cleared = Math.max(0, state.score);
            state.score = 0;
            state.updatedAt = Instant.now();
            return cleared;
        }
    }

    private int getActiveViolationCount(UUID playerUuid) {
        return getActiveViolationCount(playerUuid, Instant.now());
    }

    int getActiveViolationCount(UUID playerUuid, @NotNull Instant now) {
        ViolationScoreState state = contentFilterViolations.get(playerUuid);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            int activeScore = decayViolationScore(state, now);
            if (activeScore <= 0) {
                contentFilterViolations.remove(playerUuid, state);
                return 0;
            }
            return activeScore;
        }
    }

    private int decayViolationScore(@NotNull ViolationScoreState state, @NotNull Instant now) {
        int currentScore = Math.max(0, state.score);
        if (currentScore == 0) {
            state.updatedAt = now;
            return 0;
        }
        if (contentFilterScoreDecayAmount <= 0 || contentFilterScoreDecaySeconds <= 0L) {
            return currentScore;
        }

        long elapsedSeconds = Math.max(0L, Duration.between(state.updatedAt, now).getSeconds());
        long completedPeriods = elapsedSeconds / contentFilterScoreDecaySeconds;
        if (completedPeriods <= 0L) {
            return currentScore;
        }

        long decayed = completedPeriods * (long) contentFilterScoreDecayAmount;
        int decayedScore = (int) Math.max(0L, currentScore - decayed);
        if (decayedScore < currentScore) {
            state.score = decayedScore;
            state.updatedAt = state.updatedAt.plusSeconds(completedPeriods * contentFilterScoreDecaySeconds);
        }
        return decayedScore;
    }

    private ModerationActionRule findMatchedRule(int activeScore) {
        ModerationActionRule matchedRule = null;
        for (ModerationActionRule rule : moderationActionRules) {
            if (activeScore >= rule.threshold()) {
                matchedRule = rule;
            }
        }
        return matchedRule;
    }

    private String buildContentFilterWarnReason(String messageType, ModerationDecision decision) {
        return buildContentFilterReason(contentFilterWarnMessage, messageType, decision);
    }

    private String buildContentFilterKickReason(String messageType, ModerationDecision decision) {
        return buildContentFilterReason(contentFilterKickReason, messageType, decision);
    }

    private String buildContentFilterBanReason(String messageType, ModerationDecision decision) {
        return buildContentFilterReason(contentFilterBanReason, messageType, decision);
    }

    private String buildContentFilterReason(String template, String messageType, ModerationDecision decision) {
        String severity = decision.severity() == null
            ? ""
            : decision.severity().name().toLowerCase(Locale.ROOT);
        return template
            .replace("{type}", messageType)
            .replace("{severity}", severity)
            .trim();
    }

    void configureContentFilterMessagesForTest(String blockedMessage, String warnMessage, String kickReason, String banReason) {
        this.contentFilterBlockedMessage = blockedMessage;
        this.contentFilterWarnMessage = warnMessage;
        this.contentFilterKickReason = kickReason;
        this.contentFilterBanReason = banReason;
    }

    String contentFilterKickReasonForTest(String messageType, @Nullable ProfanitySeverity severity) {
        return buildContentFilterKickReason(messageType, ModerationDecision.deny("", "content_filter_rejected", "ignored", severity, true));
    }

    String contentFilterBanReasonForTest(String messageType, @Nullable ProfanitySeverity severity) {
        return buildContentFilterBanReason(messageType, ModerationDecision.deny("", "content_filter_rejected", "ignored", severity, true));
    }

    private void applyContentFilterKick(Player player, String reason) {
        plugin.punishments().record(player.getUniqueId(), null, null, "kick", true, reason);
        api.tasks().nextTick(() -> player.kick(Component.text("You have been kicked from this server.\nReason: " + reason)));
    }

    private void applyContentFilterBan(Player player, ModerationActionRule rule, String reason) {
        Duration duration = rule.durationSeconds() > 0L ? Duration.ofSeconds(rule.durationSeconds()) : null;
        plugin.punishments().record(player.getUniqueId(), null, duration, "ban", true, reason);

        PlayerProfile profile = Bukkit.createProfile(player.getUniqueId(), player.getName());
        java.util.Date expires = duration == null ? null : java.util.Date.from(Instant.now().plus(duration));
        Bukkit.getBanList(BanListType.PROFILE).addBan(profile, reason, expires, "<server>");
        api.tasks().nextTick(() -> player.kick(plugin.punishments().formatBanMessage(plugin.punishments().findActiveBan(player.getUniqueId()))));
    }

    private BookMeta applyFilteredBook(BookMeta originalMeta, String originalTitle, String filteredMessage, boolean signing) {
        DecodedBookContent decoded = decodeBookContent(filteredMessage, originalTitle);
        BookMeta updatedMeta = originalMeta.clone();

        if (signing) {
            if (decoded.title() == null || decoded.title().isBlank()) {
                updatedMeta.setTitle(null);
            } else {
                updatedMeta.setTitle(decoded.title());
            }
        }

        for (int i = 0; i < decoded.pages().size(); i++) {
            updatedMeta.page(i + 1, Component.text(decoded.pages().get(i)));
        }
        return updatedMeta;
    }

    private EncodedBookContent encodeBookContent(BookMeta meta) {
        String title = meta.hasTitle() ? Objects.requireNonNullElse(meta.getTitle(), "") : "";
        List<String> pages = new ArrayList<>();
        for (Component page : meta.pages()) {
            pages.add(PLAIN.serialize(page));
        }

        String body = String.join(BOOK_PAGE_SEPARATOR, pages);
        String message = title + BOOK_TITLE_SEPARATOR + body;
        boolean allPagesBlank = true;
        for (String page : pages) {
            if (!page.isBlank()) {
                allPagesBlank = false;
                break;
            }
        }
        boolean titleBlank = title.isEmpty();
        if (titleBlank && allPagesBlank) {
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

    private int scorePointsForSeverity(@Nullable ProfanitySeverity severity) {
        if (severity == null) {
            return 0;
        }
        return Math.max(0, contentFilterSeverityPoints.getOrDefault(severity, DEFAULT_CONTENT_FILTER_SEVERITY_POINTS.getOrDefault(severity, 0)));
    }

    private record ModerationDecision(boolean blocked,
                                      String filteredMessage,
                                      String userMessage,
                                      String reason,
                                      String reasonDetail,
                                      @Nullable ProfanitySeverity severity,
                                      boolean enforcePunishment) {
        private static ModerationDecision allow() {
            return new ModerationDecision(false, null, null, "allowed", null, null, false);
        }

        private static ModerationDecision filtered(String filteredMessage, String reason, String reasonDetail) {
            return new ModerationDecision(false, filteredMessage, null, reason == null ? "filtered" : reason, reasonDetail, null, false);
        }

        private static ModerationDecision deny(String userMessage, String reason, String reasonDetail, @Nullable ProfanitySeverity severity, boolean enforcePunishment) {
            return new ModerationDecision(true, null, userMessage, reason, reasonDetail, severity, enforcePunishment);
        }
    }

    private record ModerationOutcome(String actionTaken, int strikeCount, int pointsAdded) {
        private static ModerationOutcome filtered() {
            return new ModerationOutcome("filtered", 0, 0);
        }

        private static ModerationOutcome blocked(int strikeCount, int pointsAdded) {
            return new ModerationOutcome("blocked", strikeCount, pointsAdded);
        }

        private static ModerationOutcome of(String actionTaken, int strikeCount, int pointsAdded) {
            return new ModerationOutcome(actionTaken, strikeCount, pointsAdded);
        }
    }

    record ModerationActionRule(int threshold, String action, long durationSeconds) {
    }

    record ViolationStatus(int activeScore, int pointsAdded, @Nullable ModerationActionRule actionRule) {
    }

    private static final class ViolationScoreState {
        private int score;
        private Instant updatedAt = Instant.now();
    }

    private record ModerationPlayerFilter(@Nullable UUID uuid, @Nullable String name) {
    }

    private record EncodedBookContent(String title, List<String> pages, String message) {
    }

    private record DecodedBookContent(String title, List<String> pages) {
    }
}
