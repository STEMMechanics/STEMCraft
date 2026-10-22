/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.workshop.Workshop;
import dev.stemcraft.api.service.workshop.WorkshopService;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Delivers scheduled, join, manual, and workshop-backed announcements. */
public class AnnouncementsFeature extends BaseFeature {
    static final String SCHEDULE_TASK = "feature:announcements-schedule";
    static final String MIGRATION_NAME = "announcements";
    static final int MIGRATION_VERSION = 1;
    static final String WORKSHOP_TYPE = "workshops";
    static final String LEGACY_WORKSHOP_PROMOTION_ID = "workshop-promotion";
    static final long NO_PLAYER_RETRY_SECONDS = 30L;
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Australia/Brisbane");
    static final DateTimeFormatter WORKSHOP_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy");
    static final DateTimeFormatter WORKSHOP_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private final Map<String, AnnouncementDefinition> configuredAnnouncements = new LinkedHashMap<>();
    private final Map<String, AnnouncementDefinition> manualAnnouncements = new LinkedHashMap<>();
    private final Map<String, AnnouncementDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Instant> nextDue = new LinkedHashMap<>();
    private final Map<UUID, String> joinTasks = new ConcurrentHashMap<>();

    private boolean enabled;
    private boolean scheduledEnabled;
    private boolean joinEnabled;
    private boolean announceOnStartup;
    private long defaultIntervalSeconds;
    private long minimumGapSeconds;
    private long joinDelaySeconds;
    private String prefix;
    private List<String> firstTimeJoinMessage = List.of();
    private List<String> returningJoinMessage = List.of();
    private ZoneId defaultTimezone = DEFAULT_ZONE;
    private Instant lastScheduledBroadcastAt;
    private Command command;

    public AnnouncementsFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        loadSettings();
        ensureStorage();
        loadManualAnnouncements();
        registerCommand();
        registerEvents();
        rebuildDefinitions(true);
    }

    @Override
    public void onReload() {
        super.onReload();
        boolean wasEnabled = enabled;
        loadSettings();
        loadManualAnnouncements();
        if (!joinEnabled) cancelJoinTasks();
        if (!enabled) {
            api.tasks().cancel(SCHEDULE_TASK);
            cancelJoinTasks();
            if (command != null) {
                command.unregister();
                command = null;
            }
            return;
        }
        if (!wasEnabled && command == null) {
            registerCommand();
        }
        rebuildDefinitions(false);
    }

    @Override
    public void onDisable() {
        enabled = false;
        api.tasks().cancel(SCHEDULE_TASK);
        cancelJoinTasks();
        if (command != null) {
            command.unregister();
            command = null;
        }
    }

    private void loadSettings() {
        ConfigSection section = getConfigSection();
        enabled = isEnabled();
        scheduledEnabled = section.getBoolean("scheduled-enabled", true);
        joinEnabled = section.getBoolean("join-enabled", true);
        announceOnStartup = section.getBoolean("announce-on-startup", false);
        defaultIntervalSeconds = parseDuration(section.getString("default-interval", "15m"), 900L, 15L, 604800L);
        minimumGapSeconds = parseDuration(section.getString("minimum-gap", "30s"), 30L, 0L, 86400L);
        joinDelaySeconds = parseDuration(section.getString("join-delay", "5s"), 5L, 0L, 3600L);
        prefix = section.getString("prefix", "");
        defaultTimezone = parseZone(section.getString("timezone", DEFAULT_ZONE.getId()), DEFAULT_ZONE);
        firstTimeJoinMessage = readLines(section, "join.first-time");
        returningJoinMessage = readLines(section, "join.returning");
        configuredAnnouncements.clear();
        readConfiguredAnnouncements(section.getSection("scheduled", false));
        if (configuredAnnouncements.values().stream().noneMatch(AnnouncementDefinition::workshopPromotion)) {
            readLegacyWorkshopPromotion(section.getSection("workshop-promotions", false));
        }
    }

    private void readConfiguredAnnouncements(@Nullable ConfigSection section) {
        if (section == null) return;
        for (String id : section.getKeys(false).stream().sorted().toList()) {
            ConfigSection entry = section.getSection(id, false);
            if (entry == null) continue;
            List<String> lines = readLines(entry, "message");
            if (lines.isEmpty()) continue;
            String type = entry.getString("type", "message").trim().toLowerCase(Locale.ROOT);
            configuredAnnouncements.put(id, new AnnouncementDefinition(
                    id,
                    lines,
                    entry.getBoolean("enabled", true),
                    parseDuration(entry.getString("interval", defaultIntervalSeconds + "s"), defaultIntervalSeconds, 15L, 604800L),
                    parseDate(entry.getString("start", ""), false),
                    parseDate(entry.getString("end", ""), true),
                    type,
                    WORKSHOP_TYPE.equals(type) ? readWorkshopFilter(entry.getSection("filters", false)) : WorkshopFilter.NONE,
                    false
            ));
        }
    }

    private void readLegacyWorkshopPromotion(@Nullable ConfigSection section) {
        if (section == null) return;
        List<String> lines = readLines(section, "message");
        if (lines.isEmpty()) return;
        configuredAnnouncements.put(LEGACY_WORKSHOP_PROMOTION_ID, new AnnouncementDefinition(
                LEGACY_WORKSHOP_PROMOTION_ID,
                lines,
                section.getBoolean("enabled", true),
                parseDuration(section.getString("interval", defaultIntervalSeconds + "s"), defaultIntervalSeconds, 15L, 604800L),
                parseDate(section.getString("start", ""), false),
                parseDate(section.getString("end", ""), true),
                WORKSHOP_TYPE,
                WorkshopFilter.NONE,
                false
        ));
    }

    private @NotNull WorkshopFilter readWorkshopFilter(@Nullable ConfigSection section) {
        if (section == null) return WorkshopFilter.NONE;
        ConfigSection days = section.getSection("days-until", false);
        Double greaterThan = days == null ? null : optionalNonNegativeDouble(days, "greater-than");
        Double lessThan = days == null ? null : optionalNonNegativeDouble(days, "less-than");
        if (greaterThan != null && lessThan != null && greaterThan >= lessThan) {
            STEMCraft.getPlugin().getLogger().warning("[announcements] Ignoring invalid workshop day filter: greater-than must be less than less-than.");
            greaterThan = null;
            lessThan = null;
        }
        List<String> locations = section.getStringList("locations").stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return new WorkshopFilter(greaterThan, lessThan, locations);
    }

    private @Nullable Double optionalNonNegativeDouble(@NotNull ConfigSection section, @NotNull String path) {
        if (!section.contains(path)) return null;
        double value = section.getDouble(path, -1.0);
        if (!Double.isFinite(value) || value < 0.0) {
            STEMCraft.getPlugin().getLogger().warning("[announcements] Ignoring invalid workshop filter value for " + path + ".");
            return null;
        }
        return value;
    }

    private void ensureStorage() {
        if (api.database().migrationVersion(MIGRATION_NAME) >= MIGRATION_VERSION) return;
        boolean created = api.database().execute("""
                CREATE TABLE IF NOT EXISTS announcements (
                    id TEXT PRIMARY KEY,
                    message TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    interval_seconds INTEGER NOT NULL,
                    start_at INTEGER,
                    end_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        if (!created) {
            STEMCraft.getPlugin().getLogger().warning("[announcements] Could not create the announcements table.");
            return;
        }
        api.database().setMigrationVersion(MIGRATION_NAME, MIGRATION_VERSION);
    }

    private void loadManualAnnouncements() {
        manualAnnouncements.clear();
        try {
            api.database().queryEach(
                    "SELECT id, message, enabled, interval_seconds, start_at, end_at FROM announcements ORDER BY id",
                    null,
                    result -> {
                        AnnouncementDefinition definition = readManualAnnouncement(result);
                        if (definition != null) manualAnnouncements.put(definition.id(), definition);
                    }
            );
        } catch (RuntimeException exception) {
            STEMCraft.getPlugin().getLogger().warning("[announcements] Could not load saved announcements: " + exception.getMessage());
        }
    }

    private @Nullable AnnouncementDefinition readManualAnnouncement(ResultSet result) {
        try {
            long start = result.getLong("start_at");
            boolean hasStart = !result.wasNull();
            long end = result.getLong("end_at");
            boolean hasEnd = !result.wasNull();
            List<String> lines = splitLines(result.getString("message"));
            if (lines.isEmpty()) return null;
            return new AnnouncementDefinition(
                    result.getString("id"),
                    lines,
                    result.getInt("enabled") != 0,
                    Math.max(15L, result.getLong("interval_seconds")),
                    hasStart ? Instant.ofEpochMilli(start) : null,
                    hasEnd ? Instant.ofEpochMilli(end) : null,
                    "message",
                    WorkshopFilter.NONE,
                    true
            );
        } catch (Exception exception) {
            STEMCraft.getPlugin().getLogger().warning("[announcements] Skipped an invalid saved announcement: " + exception.getMessage());
            return null;
        }
    }

    private void rebuildDefinitions(boolean startup) {
        Map<String, Instant> previousDue = new LinkedHashMap<>(nextDue);
        definitions.clear();
        definitions.putAll(configuredAnnouncements);
        definitions.putAll(manualAnnouncements);
        nextDue.clear();
        Instant now = Instant.now();
        for (AnnouncementDefinition definition : definitions.values()) {
            Instant due = previousDue.get(definition.id());
            if (due == null) {
                due = announceOnStartup && startup ? now : now.plusSeconds(definition.intervalSeconds());
            }
            nextDue.put(definition.id(), due);
        }
        if (!enabled || !scheduledEnabled) {
            api.tasks().cancel(SCHEDULE_TASK);
            return;
        }
        scheduleNext();
    }

    private void scheduleNext() {
        api.tasks().cancel(SCHEDULE_TASK);
        if (!enabled || !scheduledEnabled || definitions.isEmpty()) return;
        Instant now = Instant.now();
        Instant next = definitions.values().stream()
                .filter(definition -> isPotentiallyActive(definition, now))
                .map(definition -> effectiveDue(definition, now))
                .min(Comparator.naturalOrder())
                .orElse(now.plusSeconds(NO_PLAYER_RETRY_SECONDS));
        long delay = Math.max(1L, (next.toEpochMilli() - now.toEpochMilli() + 49L) / 50L);
        api.tasks().runOnceDelay(SCHEDULE_TASK, delay, this::processScheduled);
    }

    private void processScheduled() {
        if (!enabled || !scheduledEnabled) return;
        Instant now = Instant.now();
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            api.tasks().runOnceDelay(SCHEDULE_TASK, NO_PLAYER_RETRY_SECONDS * 20L, this::processScheduled);
            return;
        }
        List<AnnouncementDefinition> due = definitions.values().stream()
                .filter(definition -> isActive(definition, now))
                .filter(definition -> !effectiveDue(definition, now).isAfter(now))
                .sorted(Comparator.comparing((AnnouncementDefinition definition) -> effectiveDue(definition, now))
                        .thenComparing(AnnouncementDefinition::id))
                .toList();
        if (due.isEmpty()) {
            scheduleNext();
            return;
        }
        if (lastScheduledBroadcastAt != null && now.isBefore(lastScheduledBroadcastAt.plusSeconds(minimumGapSeconds))) {
            long delay = Math.max(1L, (lastScheduledBroadcastAt.plusSeconds(minimumGapSeconds).toEpochMilli() - now.toEpochMilli() + 49L) / 50L);
            api.tasks().runOnceDelay(SCHEDULE_TASK, delay, this::processScheduled);
            return;
        }

        AnnouncementDefinition selected = due.getFirst();
        if (broadcast(selected, null)) {
            lastScheduledBroadcastAt = now;
            nextDue.put(selected.id(), now.plusSeconds(selected.intervalSeconds()));
        } else {
            nextDue.put(selected.id(), now.plusSeconds(NO_PLAYER_RETRY_SECONDS));
        }
        scheduleNext();
    }

    private boolean isPotentiallyActive(AnnouncementDefinition definition, Instant now) {
        if (!definition.enabled() || definition.lines().isEmpty()) return false;
        if (definition.endAt() != null && !now.isBefore(definition.endAt())) return false;
        if (definition.startAt() != null && definition.startAt().isAfter(now)) return true;
        return !definition.workshopPromotion() || findAnnouncementWorkshop(definition.workshopFilter(), now) != null;
    }

    private boolean isActive(AnnouncementDefinition definition, Instant now) {
        if (!isPotentiallyActive(definition, now)) return false;
        return definition.startAt() == null || !definition.startAt().isAfter(now);
    }

    private Instant effectiveDue(AnnouncementDefinition definition, Instant now) {
        Instant due = nextDue.getOrDefault(definition.id(), now.plusSeconds(definition.intervalSeconds()));
        if (definition.startAt() != null && due.isBefore(definition.startAt())) return definition.startAt();
        return due;
    }

    private boolean broadcast(@NotNull AnnouncementDefinition definition, @Nullable Player recipient) {
        Workshop workshop = definition.workshopPromotion()
                ? findAnnouncementWorkshop(definition.workshopFilter(), Instant.now())
                : null;
        if (definition.workshopPromotion() && workshop == null) return false;
        int online = Bukkit.getOnlinePlayers().size();
        for (String line : definition.lines()) {
            String rendered = render(prefix + line, recipient, workshop, online);
            if (rendered.isBlank()) continue;
            if (recipient == null) api.messages().broadcast(rendered);
            else api.messages().send(recipient, rendered);
        }
        return true;
    }

    private @Nullable Workshop findAnnouncementWorkshop(@NotNull WorkshopFilter filter, @NotNull Instant now) {
        WorkshopService service = api.workshops();
        if (service == null || !service.isAvailable() || !service.isEnabled()) return null;
        return service.upcoming().stream()
                .filter(Workshop::announcementEligible)
                .filter(workshop -> filter.matches(workshop, now))
                .findFirst()
                .orElse(null);
    }

    private @NotNull String render(@NotNull String template, @Nullable Player player, @Nullable Workshop workshop, int online) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player == null ? "" : escapeLiteral(player.getName()));
        values.put("online_players", Integer.toString(online));
        values.put("next_workshop_title", "");
        values.put("next_workshop_date", "");
        values.put("next_workshop_time", "");
        values.put("next_workshop_url", "");
        values.put("next_workshop_countdown", "");
        if (workshop != null) {
            ZonedDateTime local = workshop.startAt().withZoneSameInstant(defaultTimezone);
            long countdown = Math.max(0L, (workshop.startAt().toInstant().toEpochMilli() - System.currentTimeMillis()) / 1000L);
            values.put("next_workshop_title", escapeLiteral(workshop.title()));
            values.put("next_workshop_date", escapeLiteral(WORKSHOP_DATE_FORMAT.format(local)));
            values.put("next_workshop_time", escapeLiteral(WORKSHOP_TIME_FORMAT.format(local)));
            values.put("next_workshop_url", escapeLiteral(workshop.bookingUrl()));
            values.put("next_workshop_countdown", escapeLiteral(countdown == 0L ? "now" : TimeUtil.formatLongDuration(countdown)));
        }
        String rendered = PlaceholderUtil.apply(template, values);
        String withPlaceholders = api.placeholders().apply(player, rendered);
        return withPlaceholders == null ? "" : withPlaceholders;
    }

    private void registerEvents() {
        api.events().register(PlayerJoinEvent.class, event -> {
            if (!enabled || !joinEnabled) return;
            Player player = event.getPlayer();
            String taskId = "announcements:join:" + player.getUniqueId();
            joinTasks.put(player.getUniqueId(), taskId);
            api.tasks().runOnceDelay(taskId, joinDelaySeconds * 20L, () -> {
                joinTasks.remove(player.getUniqueId(), taskId);
                if (!enabled || !joinEnabled || !player.isOnline()) return;
                broadcast(new AnnouncementDefinition("join", player.hasPlayedBefore() ? returningJoinMessage : firstTimeJoinMessage,
                        true, 0L, null, null, "message", WorkshopFilter.NONE, false), player);
            });
        });
        api.events().register(PlayerQuitEvent.class, event -> cancelJoinTask(event.getPlayer().getUniqueId()));
    }

    private void cancelJoinTask(UUID playerId) {
        String taskId = joinTasks.remove(playerId);
        if (taskId != null) api.tasks().cancel(taskId);
    }

    private void cancelJoinTasks() {
        for (String taskId : joinTasks.values()) api.tasks().cancel(taskId);
        joinTasks.clear();
    }

    private void registerCommand() {
        if (command != null) return;
        command = api.commands().create("announcements")
                .description("ANNOUNCEMENTS_DESCRIPTION")
                .usage("ANNOUNCEMENTS_USAGE")
                .tabCompletion("help")
                .tabCompletion("list")
                .tabCompletion("add")
                .tabCompletion("edit", "{announcement-id}")
                .tabCompletion("remove", "{announcement-id}")
                .tabCompletion("enable", "{announcement-id}")
                .tabCompletion("disable", "{announcement-id}")
                .tabCompletion("broadcast", "{announcement-id}")
                .tabCompletion("reload")
                .executor((unused, cmd, ctx) -> handleCommand(ctx))
                .register(STEMCraft.getPlugin());
        api.tabComplete().register("announcement-id", (player, args) -> definitions.keySet().stream().sorted().toList());
    }

    private void handleCommand(CommandContext ctx) {
        String subcommand = ctx.getArgLower(0);
        if (subcommand == null || subcommand.equals("help")) {
            showHelp(ctx);
            return;
        }
        switch (subcommand) {
            case "list" -> list(ctx);
            case "add" -> add(ctx);
            case "edit" -> edit(ctx);
            case "remove" -> remove(ctx);
            case "enable" -> setEnabled(ctx, true);
            case "disable" -> setEnabled(ctx, false);
            case "broadcast" -> broadcastNow(ctx);
            case "reload" -> reload(ctx);
            default -> ctx.returnUsage();
        }
    }

    private void showHelp(CommandContext ctx) {
        ctx.info("/announcements list - list configured announcements");
        ctx.info("/announcements broadcast <id> - broadcast an announcement now");
        if (isAdmin(ctx)) {
            ctx.info("/announcements add <message>");
            ctx.info("/announcements edit <id> <message>");
            ctx.info("/announcements remove <id>");
            ctx.info("/announcements enable|disable <id>");
            ctx.info("/announcements reload");
        }
    }

    private void list(CommandContext ctx) {
        requireAdmin(ctx);
        if (definitions.isEmpty()) {
            ctx.info("No announcements are configured.");
            return;
        }
        for (AnnouncementDefinition definition : definitions.values()) {
            String preview = definition.lines().getFirst().replace('\n', ' ');
            if (preview.length() > 80) preview = preview.substring(0, 77) + "...";
            ctx.info("{id}: {state} ({source}) - {preview}",
                    "id", definition.id(),
                    "state", definition.enabled() ? "enabled" : "disabled",
                    "source", definition.manual() ? "manual" : "config",
                    "preview", preview);
        }
    }

    private void add(CommandContext ctx) {
        requireAdmin(ctx);
        String message = rawMessage(ctx, 1);
        if (message.isBlank()) ctx.returnError("Provide an announcement message.");
        String id = nextManualId();
        AnnouncementDefinition definition = new AnnouncementDefinition(id, splitLines(message), true,
                defaultIntervalSeconds, null, null, "message", WorkshopFilter.NONE, true);
        if (!persistManual(definition, null)) ctx.returnError("Could not save the announcement.");
        manualAnnouncements.put(id, definition);
        rebuildDefinitions(false);
        ctx.returnSuccess("Added announcement {id}.", "id", id);
    }

    private void edit(CommandContext ctx) {
        requireAdmin(ctx);
        if (ctx.numArgs() < 3) ctx.returnUsage();
        AnnouncementDefinition existing = manualAnnouncements.get(ctx.getArg(1));
        if (existing == null) {
            ctx.returnError("Manual announcement not found.");
            return;
        }
        String message = rawMessage(ctx, 2);
        if (message.isBlank()) ctx.returnError("Provide an announcement message.");
        AnnouncementDefinition replacement = new AnnouncementDefinition(existing.id(), splitLines(message), existing.enabled(),
                existing.intervalSeconds(), existing.startAt(), existing.endAt(), "message", WorkshopFilter.NONE, true);
        if (!persistManual(replacement, existing)) ctx.returnError("Could not update the announcement.");
        manualAnnouncements.put(replacement.id(), replacement);
        rebuildDefinitions(false);
        ctx.returnSuccess("Updated announcement {id}.", "id", replacement.id());
    }

    private void remove(CommandContext ctx) {
        requireAdmin(ctx);
        if (ctx.numArgs() < 2) ctx.returnUsage();
        String id = ctx.getArg(1);
        if (!manualAnnouncements.containsKey(id)) {
            ctx.returnError("Manual announcement not found.");
            return;
        }
        int changed = api.database().update("DELETE FROM announcements WHERE id = ?", statement -> statement.setString(1, id));
        if (changed != 1) ctx.returnError("Could not remove the announcement.");
        manualAnnouncements.remove(id);
        rebuildDefinitions(false);
        ctx.returnSuccess("Removed announcement {id}.", "id", id);
    }

    private void setEnabled(CommandContext ctx, boolean enabledState) {
        requireAdmin(ctx);
        if (ctx.numArgs() < 2) ctx.returnUsage();
        String id = ctx.getArg(1);
        AnnouncementDefinition existing = manualAnnouncements.get(id);
        if (existing == null) {
            ctx.returnError("Manual announcement not found.");
            return;
        }
        AnnouncementDefinition replacement = new AnnouncementDefinition(existing.id(), existing.lines(), enabledState,
                existing.intervalSeconds(), existing.startAt(), existing.endAt(), "message", WorkshopFilter.NONE, true);
        if (api.database().update("UPDATE announcements SET enabled = ?, updated_at = ? WHERE id = ?", statement -> {
            statement.setInt(1, enabledState ? 1 : 0);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, id);
        }) != 1) ctx.returnError("Could not update the announcement.");
        manualAnnouncements.put(id, replacement);
        rebuildDefinitions(false);
        ctx.returnSuccess("Announcement {id} is now {state}.", "id", id, "state", enabledState ? "enabled" : "disabled");
    }

    private void broadcastNow(CommandContext ctx) {
        if (!ctx.hasPermission("stemcraft.announcements.broadcast") && !isAdmin(ctx)) {
            ctx.returnError("COMMAND_NO_PERMISSION");
        }
        if (ctx.numArgs() < 2) ctx.returnUsage();
        AnnouncementDefinition definition = definitions.get(ctx.getArg(1));
        if (definition == null) {
            ctx.returnError("Announcement not found.");
            return;
        }
        if (definition.workshopPromotion() && findAnnouncementWorkshop(definition.workshopFilter(), Instant.now()) == null) {
            ctx.returnInfo("There is no upcoming workshop to announce.");
        }
        if (!broadcast(definition, null)) ctx.returnInfo("The announcement was skipped.");
        else ctx.returnSuccess("Broadcast announcement {id}.", "id", definition.id());
    }

    private void reload(CommandContext ctx) {
        requireAdmin(ctx);
        STEMCraft.getPlugin().reloadStemCraft(false);
        ctx.returnSuccess("Announcements reloaded.");
    }

    private @NotNull String rawMessage(CommandContext ctx, int start) {
        if (ctx.rawArgs().size() <= start) return "";
        return String.join(" ", ctx.rawArgs().subList(start, ctx.rawArgs().size())).trim();
    }

    private boolean persistManual(AnnouncementDefinition definition, @Nullable AnnouncementDefinition existing) {
        Long start = definition.startAt() == null ? null : definition.startAt().toEpochMilli();
        Long end = definition.endAt() == null ? null : definition.endAt().toEpochMilli();
        long now = System.currentTimeMillis();
        int changed = api.database().update("""
                INSERT INTO announcements (id, message, enabled, interval_seconds, start_at, end_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET message = excluded.message, enabled = excluded.enabled,
                    interval_seconds = excluded.interval_seconds, start_at = excluded.start_at, end_at = excluded.end_at,
                    updated_at = excluded.updated_at
                """, statement -> {
            statement.setString(1, definition.id());
            statement.setString(2, String.join("\n", definition.lines()));
            statement.setInt(3, definition.enabled() ? 1 : 0);
            statement.setLong(4, definition.intervalSeconds());
            if (start == null) statement.setObject(5, null); else statement.setLong(5, start);
            if (end == null) statement.setObject(6, null); else statement.setLong(6, end);
            statement.setLong(7, existing == null ? now : now);
            statement.setLong(8, now);
        });
        return changed == 1;
    }

    private String nextManualId() {
        String id;
        do {
            id = "manual-" + UUID.randomUUID().toString().substring(0, 8);
        } while (definitions.containsKey(id));
        return id;
    }

    private boolean isAdmin(CommandContext ctx) {
        return ctx.hasPermission("stemcraft.announcements.admin");
    }

    private void requireAdmin(CommandContext ctx) {
        if (!isAdmin(ctx)) ctx.returnError("COMMAND_NO_PERMISSION");
    }

    private @NotNull List<String> readLines(@NotNull ConfigSection section, @NotNull String path) {
        Object value = section.get(path);
        if (value instanceof String string) return splitLines(string);
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            for (Object item : list) if (item != null) lines.addAll(splitLines(String.valueOf(item)));
            return List.copyOf(lines);
        }
        return List.of();
    }

    private static @NotNull List<String> splitLines(@Nullable String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R", -1)) if (!line.isBlank()) lines.add(line);
        return List.copyOf(lines);
    }

    static @NotNull String renderTemplate(@NotNull String template, @NotNull Map<String, String> values) {
        return PlaceholderUtil.apply(template, values);
    }

    static @NotNull List<String> orderedDueIds(@NotNull Map<String, Instant> due, @NotNull Instant now) {
        return due.entrySet().stream().filter(entry -> !entry.getValue().isAfter(now))
                .sorted(Map.Entry.<String, Instant>comparingByValue().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
    }

    static @NotNull String escapeLiteral(@Nullable String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }

    private static long parseDuration(String value, long fallback, long min, long max) {
        try {
            return Math.clamp(TimeUtil.parseDuration(value), min, max);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private @Nullable Instant parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        ZonedDateTime parsed = WorkshopsFeature.parseDateTime(value, defaultTimezone, endOfDay);
        return parsed == null ? null : parsed.toInstant();
    }

    private static ZoneId parseZone(String value, ZoneId fallback) {
        try { return ZoneId.of(value); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private record AnnouncementDefinition(String id, List<String> lines, boolean enabled, long intervalSeconds,
                                          Instant startAt, Instant endAt, String type, WorkshopFilter workshopFilter,
                                          boolean manual) {
        private AnnouncementDefinition {
            lines = lines == null ? List.of() : List.copyOf(lines);
            intervalSeconds = Math.max(15L, intervalSeconds);
            type = type == null || type.isBlank() ? "message" : type.trim().toLowerCase(Locale.ROOT);
            workshopFilter = workshopFilter == null ? WorkshopFilter.NONE : workshopFilter;
        }

        private boolean workshopPromotion() {
            return WORKSHOP_TYPE.equals(type);
        }
    }

    static record WorkshopFilter(@Nullable Double greaterThanDays, @Nullable Double lessThanDays,
                                 @NotNull List<String> locations) {
        static final WorkshopFilter NONE = new WorkshopFilter(null, null, List.of());

        WorkshopFilter {
            locations = locations == null ? List.of() : locations.stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }

        boolean matches(@NotNull Workshop workshop, @NotNull Instant now) {
            if (!locations.isEmpty() && locations.stream().noneMatch(location ->
                    location.equalsIgnoreCase(workshop.location().trim()))) {
                return false;
            }
            double daysUntil = (workshop.startAt().toInstant().toEpochMilli() - now.toEpochMilli()) / 86_400_000.0;
            return (greaterThanDays == null || daysUntil > greaterThanDays)
                    && (lessThanDays == null || daysUntil < lessThanDays);
        }
    }
}
