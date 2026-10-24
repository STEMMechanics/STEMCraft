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
import dev.stemcraft.api.service.debug.DebugContext;
import dev.stemcraft.api.service.debug.DebugRegistration;
import dev.stemcraft.api.service.workshop.Workshop;
import dev.stemcraft.api.service.workshop.WorkshopService;
import dev.stemcraft.api.service.workshop.WorkshopSource;
import dev.stemcraft.api.service.workshop.WorkshopSyncStatus;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Maintains website and manually created STEMMechanics workshops. */
public class WorkshopsFeature extends BaseFeature implements WorkshopService {
    static final String REFRESH_TASK = "feature:workshops-refresh";
    static final String MIGRATION_NAME = "workshops";
    static final int MIGRATION_VERSION = 2;
    static final String DEFAULT_INSTRUCTIONS_PERMISSION = "stemcraft.workshops.instructions";
    static final String ACCESS_PROMPT_TASK_PREFIX = "workshops:access-prompt:";
    static final long MAX_FEED_BYTES = 1_048_576L;
    static final int MAX_ACCESS_INSTRUCTION_LINES = 20;
    static final int MAX_ACCESS_INSTRUCTION_LENGTH = 1024;
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Australia/Brisbane");
    static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<String> DEFAULT_DISPLAY_HEADER = List.of(
            "<aqua><bold>STEMMechanics Upcoming Workshops</bold></aqua>");
    private static final List<String> DEFAULT_DISPLAY_ENTRY = List.of(
            "<yellow>{index}. {title}</yellow>",
            "<white>{date} at {time}</white>",
            "<gray>{location}</gray>",
            "<click:open_url:'{booking_url}'><aqua>[Click to view details]</aqua></click>");
    private static final List<String> DEFAULT_DISPLAY_NO_BOOKING = List.of(
            "<dark_gray>Details: /workshops info {id}</dark_gray>");
    private static final List<String> DEFAULT_DISPLAY_FOOTER = List.of(
            "<gray>Visit stemmechanics.com.au for more workshops.</gray>");
    private static final List<String> DEFAULT_INSTRUCTIONS_PROMPT = List.of(
            "<yellow>Update the workshop custom command for <white>{workshop_title}</white>, starting in {workshop_countdown}.</yellow>",
            "<gray>Players will use that custom command to access this workshop.</gray>");

    private final Object storageLock = new Object();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean();
    private final Map<String, Workshop> cache = new LinkedHashMap<>();
    private final Map<String, List<String>> accessOverrides = new LinkedHashMap<>();
    private final Map<UUID, String> accessPromptTasks = new LinkedHashMap<>();

    private volatile boolean enabled;
    private boolean websiteEnabled;
    private boolean syncEnabled;
    private boolean manualEnabled;
    private boolean cachePersistence;
    private String feedUrl;
    private long refreshIntervalSeconds;
    private long connectTimeoutSeconds;
    private long readTimeoutSeconds;
    private int maximumDisplayed;
    private ZoneId defaultTimezone;
    private List<String> displayLocations = List.of();
    private boolean instructionsPromptEnabled;
    private long instructionsPromptWindowSeconds;
    private long instructionsPromptDelaySeconds;
    private String instructionsPermission;
    private List<String> instructionsPromptMessage = List.of();
    private List<String> displayHeader = DEFAULT_DISPLAY_HEADER;
    private List<String> displayEntry = DEFAULT_DISPLAY_ENTRY;
    private List<String> displayNoBooking = DEFAULT_DISPLAY_NO_BOOKING;
    private List<String> displayFooter = DEFAULT_DISPLAY_FOOTER;
    private volatile WorkshopSyncStatus syncStatus = new WorkshopSyncStatus(false, false, null, null, "", 0);
    private Command command;
    private boolean eventsRegistered;
    private DebugRegistration workshopStatusDebug;
    private DebugRegistration workshopUpcomingDebug;
    private DebugRegistration workshopAccessPromptDebug;

    public WorkshopsFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        loadSettings();
        if (!enabled) return;
        ensureStorage();
        loadStoredWorkshops();
        loadStoredAccessOverrides();
        registerTabCompletion();
        registerCommand();
        registerEvents();
        registerDebugTests();
        restartRefreshTask();
    }

    @Override
    public void onReload() {
        super.onReload();
        boolean wasEnabled = enabled;
        loadSettings();
        if (!enabled) {
            api.tasks().cancel(REFRESH_TASK);
            cancelAccessPromptTasks();
            if (command != null) {
                command.unregister();
                command = null;
            }
            unregisterDebugTests();
            return;
        }
        if (!wasEnabled) {
            ensureStorage();
            registerTabCompletion();
            registerCommand();
            registerEvents();
        }
        loadStoredWorkshops();
        loadStoredAccessOverrides();
        registerDebugTests();
        restartRefreshTask();
    }

    @Override
    public void onDisable() {
        enabled = false;
        api.tasks().cancel(REFRESH_TASK);
        cancelAccessPromptTasks();
        if (command != null) {
            command.unregister();
            command = null;
        }
        unregisterDebugTests();
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public @NotNull List<Workshop> upcoming() {
        Instant now = Instant.now();
        synchronized (cache) {
            return cache.values().stream()
                    .filter(this::isSourceEnabled)
                    .filter(workshop -> workshop.isUpcoming(now))
                    .sorted(workshopComparator())
                    .toList();
        }
    }

    @Override
    public @Nullable Workshop nextUpcoming() {
        return upcoming().stream().findFirst().orElse(null);
    }

    /** Return the next eligible workshop for promotional messages. */
    public @Nullable Workshop nextAnnouncementWorkshop() {
        return upcoming().stream()
                .filter(Workshop::announcementEligible)
                .findFirst()
                .orElse(null);
    }

    @Override
    public @Nullable Workshop find(@NotNull String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        synchronized (cache) {
            Workshop direct = cache.get(id);
            if (direct != null) return direct;
            return cache.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(id))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Override
    public @NotNull List<Workshop> within(@NotNull Instant from, @NotNull Instant to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        return upcoming().stream()
                .filter(workshop -> {
                    Instant start = workshop.startAt().toInstant();
                    Instant expiry = (workshop.endAt() == null ? workshop.startAt() : workshop.endAt()).toInstant();
                    return !start.isAfter(to) && !expiry.isBefore(from);
                })
                .toList();
    }

    @Override
    public void refresh() {
        if (!isWebsiteSyncEnabled()) {
            return;
        }
        api.tasks().runAsync(this::refreshWebsite);
    }

    @Override
    public @Nullable Workshop addManual(@NotNull Workshop workshop) {
        if (!enabled || !manualEnabled || workshop == null || workshop.source() != WorkshopSource.MANUAL) {
            return null;
        }
        if (find(workshop.id()) != null) {
            return null;
        }
        if (!isValidBookingUrl(workshop.bookingUrl())) {
            return null;
        }

        Workshop stored = new Workshop(
                workshop.id(), workshop.title(), workshop.description(), workshop.startAt(), workshop.endAt(),
                workshop.location(), workshop.bookingUrl(), WorkshopSource.MANUAL, Instant.now(), workshop.announcementEligible()
        );
        synchronized (storageLock) {
            int changed = persistWorkshop(stored);
            if (changed != 1) return null;
            synchronized (cache) {
                cache.put(stored.id(), stored);
            }
        }
        return stored;
    }

    @Override
    public boolean updateManual(@NotNull Workshop workshop) {
        if (!enabled || !manualEnabled || workshop == null || workshop.source() != WorkshopSource.MANUAL) {
            return false;
        }
        Workshop existing = find(workshop.id());
        if (existing == null || existing.source() != WorkshopSource.MANUAL || !isValidBookingUrl(workshop.bookingUrl())) {
            return false;
        }

        Workshop stored = new Workshop(
                existing.id(), workshop.title(), workshop.description(), workshop.startAt(), workshop.endAt(),
                workshop.location(), workshop.bookingUrl(), WorkshopSource.MANUAL, Instant.now(), workshop.announcementEligible()
        );
        synchronized (storageLock) {
            if (persistWorkshop(stored) != 1) return false;
            synchronized (cache) {
                cache.put(stored.id(), stored);
            }
        }
        return true;
    }

    @Override
    public boolean removeManual(@NotNull String id) {
        Workshop existing = find(id);
        if (!enabled || !manualEnabled || existing == null || existing.source() != WorkshopSource.MANUAL) {
            return false;
        }
        synchronized (storageLock) {
            int changed = api.database().update(
                    "DELETE FROM workshops WHERE id = ? AND source = 'MANUAL'",
                    statement -> statement.setString(1, existing.id())
            );
            if (changed != 1) return false;
            api.database().update(
                    "DELETE FROM workshop_access_instructions WHERE workshop_id = ?",
                    statement -> statement.setString(1, existing.id())
            );
            synchronized (accessOverrides) {
                accessOverrides.remove(normalizeKey(existing.id()));
            }
            synchronized (cache) {
                cache.remove(existing.id());
            }
        }
        return true;
    }

    @Override
    public @NotNull WorkshopSyncStatus syncStatus() {
        return syncStatus;
    }

    @Override
    public @NotNull List<String> accessInstructions(@NotNull Workshop workshop) {
        if (workshop == null) return List.of();
        Workshop target = nextInstructionWorkshop();
        if (target == null || !target.id().equalsIgnoreCase(workshop.id())) return List.of();
        synchronized (accessOverrides) {
            List<String> override = accessOverrides.get(normalizeKey(workshop.id()));
            return override == null ? List.of() : override;
        }
    }

    private @Nullable Workshop nextInstructionWorkshop() {
        return upcoming().stream()
                .filter(Workshop::announcementEligible)
                .findFirst()
                .orElse(null);
    }

    private boolean saveAccessInstructions(@NotNull Workshop workshop, @NotNull List<String> lines) {
        List<String> bounded = boundedAccessInstructions(lines);
        if (bounded.isEmpty()) return false;
        int changed = api.database().update("""
                INSERT INTO workshop_access_instructions (workshop_id, message, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(workshop_id) DO UPDATE SET message = excluded.message, updated_at = excluded.updated_at
                """, statement -> {
            statement.setString(1, workshop.id());
            statement.setString(2, String.join("\n", bounded));
            statement.setLong(3, System.currentTimeMillis());
        });
        if (changed != 1) return false;
        api.database().update(
                "DELETE FROM workshop_access_instructions WHERE workshop_id <> ?",
                statement -> statement.setString(1, workshop.id())
        );
        synchronized (accessOverrides) {
            accessOverrides.clear();
            accessOverrides.put(normalizeKey(workshop.id()), bounded);
        }
        return true;
    }

    private boolean clearAccessInstructions() {
        api.database().update("DELETE FROM workshop_access_instructions", null);
        synchronized (accessOverrides) {
            accessOverrides.clear();
        }
        return true;
    }

    ZoneId defaultTimezone() {
        return defaultTimezone;
    }

    int maximumDisplayed() {
        return maximumDisplayed;
    }

    private @NotNull List<Workshop> visibleUpcoming() {
        return upcoming().stream()
                .filter(workshop -> matchesLocationFilter(workshop.location(), displayLocations))
                .toList();
    }

    private boolean isSourceEnabled(@NotNull Workshop workshop) {
        return workshop.source() == WorkshopSource.WEBSITE ? websiteEnabled : manualEnabled;
    }

    private void loadSettings() {
        ConfigSection section = getConfigSection();
        enabled = super.isEnabled();
        websiteEnabled = section.getBoolean("website-enabled", true);
        syncEnabled = section.getBoolean("rss-sync-enabled", true);
        manualEnabled = section.getBoolean("manual-enabled", true);
        cachePersistence = section.getBoolean("cache-persistence", true);
        feedUrl = section.getString("rss-url", "").trim();
        refreshIntervalSeconds = parseDuration(section.getString("refresh-interval", "60m"), 3600L, 15L, 604800L);
        connectTimeoutSeconds = parseDuration(section.getString("connection-timeout", "10s"), 10L, 1L, 120L);
        readTimeoutSeconds = parseDuration(section.getString("read-timeout", "20s"), 20L, 1L, 300L);
        maximumDisplayed = Math.clamp(section.getInt("maximum-displayed", 3), 1, 20);
        defaultTimezone = parseZone(section.getString("timezone", DEFAULT_ZONE.getId()), DEFAULT_ZONE);
        displayLocations = section.getStringList("display-filter.locations").stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        displayHeader = configuredLines(section, "display.header", DEFAULT_DISPLAY_HEADER);
        displayEntry = configuredLines(section, "display.entry", DEFAULT_DISPLAY_ENTRY);
        displayNoBooking = configuredLines(section, "display.no-booking", DEFAULT_DISPLAY_NO_BOOKING);
        displayFooter = configuredLines(section, "display.footer", DEFAULT_DISPLAY_FOOTER);
        instructionsPromptEnabled = section.getBoolean("instructions.prompt-enabled", true);
        instructionsPromptWindowSeconds = parseDuration(
                section.getString("instructions.prompt-window", "1h"), 3600L, 60L, 604800L);
        instructionsPromptDelaySeconds = parseDuration(
                section.getString("instructions.prompt-delay", "5s"), 5L, 0L, 3600L);
        instructionsPermission = section.getString("instructions.permission", DEFAULT_INSTRUCTIONS_PERMISSION).trim();
        if (instructionsPermission.isBlank()) instructionsPermission = DEFAULT_INSTRUCTIONS_PERMISSION;
        instructionsPromptMessage = configuredLines(section, "instructions.prompt", DEFAULT_INSTRUCTIONS_PROMPT);
        syncStatus = new WorkshopSyncStatus(isWebsiteSyncEnabled(), false,
                syncStatus.lastAttemptAt(), syncStatus.lastSuccessAt(), syncStatus.lastError(), countWebsiteWorkshops());
    }

    private static @NotNull List<String> configuredLines(@NotNull ConfigSection section, @NotNull String path,
                                                          @NotNull List<String> fallback) {
        return section.contains(path) ? readLines(section.get(path)) : fallback;
    }

    private static @NotNull List<String> readLines(@Nullable Object value) {
        if (value == null) return List.of();
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) values.addAll(splitLines(String.valueOf(item)));
            }
        } else {
            values.addAll(splitLines(String.valueOf(value)));
        }
        return List.copyOf(values);
    }

    private static @NotNull List<String> splitLines(@Nullable String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            if (!line.isBlank()) lines.add(line);
        }
        return List.copyOf(lines);
    }

    private static @NotNull List<String> boundedAccessInstructions(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<String> bounded = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            bounded.add(boundedText(line, MAX_ACCESS_INSTRUCTION_LENGTH));
            if (bounded.size() >= MAX_ACCESS_INSTRUCTION_LINES) break;
        }
        return List.copyOf(bounded);
    }

    private static @NotNull String normalizeKey(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isWebsiteSyncEnabled() {
        return enabled && websiteEnabled && syncEnabled && !feedUrl.isBlank();
    }

    private void restartRefreshTask() {
        api.tasks().cancel(REFRESH_TASK);
        if (!isWebsiteSyncEnabled()) {
            return;
        }
        long periodTicks = Math.max(20L, refreshIntervalSeconds * 20L);
        api.tasks().repeating(REFRESH_TASK, periodTicks, periodTicks, this::refresh);
        refresh();
    }

    private void ensureStorage() {
        int version = api.database().migrationVersion(MIGRATION_NAME);
        if (version < 1) {
            boolean created = api.database().execute("""
                    CREATE TABLE IF NOT EXISTS workshops (
                        id TEXT PRIMARY KEY,
                        source TEXT NOT NULL,
                        source_key TEXT NOT NULL UNIQUE,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        start_at INTEGER NOT NULL,
                        end_at INTEGER,
                        timezone TEXT NOT NULL,
                        location TEXT NOT NULL,
                        booking_url TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        announcement_eligible INTEGER NOT NULL
                    )
                    """);
            if (!created) {
                pluginWarning("Could not create the workshops table.");
                return;
            }
            api.database().execute("CREATE INDEX IF NOT EXISTS workshops_start_idx ON workshops (start_at);");
            api.database().execute("CREATE INDEX IF NOT EXISTS workshops_source_idx ON workshops (source);");
            api.database().setMigrationVersion(MIGRATION_NAME, 1);
            version = 1;
        }
        if (version < 2) {
            boolean created = api.database().execute("""
                    CREATE TABLE IF NOT EXISTS workshop_access_instructions (
                        workshop_id TEXT PRIMARY KEY,
                        message TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            if (!created) {
                pluginWarning("Could not create the workshop access instructions table.");
                return;
            }
            api.database().setMigrationVersion(MIGRATION_NAME, 2);
        }
    }

    private void loadStoredWorkshops() {
        Map<String, Workshop> loaded = new LinkedHashMap<>();
        api.database().queryEach(
                "SELECT id, source, title, description, start_at, end_at, timezone, location, booking_url, updated_at, announcement_eligible " +
                        "FROM workshops WHERE source = 'MANUAL' OR ? = 1",
                statement -> statement.setInt(1, cachePersistence ? 1 : 0),
                result -> {
                    Workshop workshop = workshopFromRow(result);
                    if (workshop != null) loaded.put(workshop.id(), workshop);
                }
        );
        synchronized (cache) {
            cache.clear();
            cache.putAll(loaded);
        }
    }

    private void loadStoredAccessOverrides() {
        Workshop target = nextInstructionWorkshop();
        Map<String, List<String>> loaded = new LinkedHashMap<>();
        if (target == null) {
            api.database().update("DELETE FROM workshop_access_instructions", null);
        } else {
            api.database().update(
                    "DELETE FROM workshop_access_instructions WHERE workshop_id <> ?",
                    statement -> statement.setString(1, target.id())
            );
            api.database().queryEach(
                    "SELECT workshop_id, message FROM workshop_access_instructions WHERE workshop_id = ?",
                    statement -> statement.setString(1, target.id()),
                    result -> {
                        String id = result.getString("workshop_id");
                        List<String> lines = boundedAccessInstructions(splitLines(result.getString("message")));
                        if (!normalizeKey(id).isBlank() && !lines.isEmpty()) {
                            loaded.put(normalizeKey(id), lines);
                        }
                    }
            );
        }
        synchronized (accessOverrides) {
            accessOverrides.clear();
            accessOverrides.putAll(loaded);
        }
    }

    private @Nullable Workshop workshopFromRow(java.sql.ResultSet result) {
        try {
            WorkshopSource source = WorkshopSource.valueOf(result.getString("source"));
            ZoneId zone = parseZone(result.getString("timezone"), defaultTimezone == null ? DEFAULT_ZONE : defaultTimezone);
            ZonedDateTime start = Instant.ofEpochMilli(result.getLong("start_at")).atZone(zone);
            long endMillis = result.getLong("end_at");
            ZonedDateTime end = result.wasNull() ? null : Instant.ofEpochMilli(endMillis).atZone(zone);
            String bookingUrl = safeHttpUrl(result.getString("booking_url"));
            return new Workshop(
                    result.getString("id"), result.getString("title"), result.getString("description"), start, end,
                    result.getString("location"), bookingUrl, source,
                    Instant.ofEpochMilli(result.getLong("updated_at")), result.getInt("announcement_eligible") != 0
            );
        } catch (Exception exception) {
            pluginWarning("Skipped an invalid stored workshop: " + exception.getMessage());
            return null;
        }
    }

    private int persistWorkshop(Workshop workshop) {
        Long end = workshop.endAt() == null ? null : workshop.endAt().toInstant().toEpochMilli();
        return api.database().update("""
                INSERT INTO workshops
                    (id, source, source_key, title, description, start_at, end_at, timezone, location, booking_url, updated_at, announcement_eligible)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    source = excluded.source,
                    source_key = excluded.source_key,
                    title = excluded.title,
                    description = excluded.description,
                    start_at = excluded.start_at,
                    end_at = excluded.end_at,
                    timezone = excluded.timezone,
                    location = excluded.location,
                    booking_url = excluded.booking_url,
                    updated_at = excluded.updated_at,
                    announcement_eligible = excluded.announcement_eligible
                """, statement -> {
            statement.setString(1, workshop.id());
            statement.setString(2, workshop.source().name());
            statement.setString(3, workshop.source() == WorkshopSource.MANUAL ? workshop.id() : workshop.id());
            statement.setString(4, workshop.title());
            statement.setString(5, workshop.description());
            statement.setLong(6, workshop.startAt().toInstant().toEpochMilli());
            if (end == null) statement.setObject(7, null);
            else statement.setLong(7, end);
            statement.setString(8, workshop.startAt().getZone().getId());
            statement.setString(9, workshop.location());
            statement.setString(10, workshop.bookingUrl());
            statement.setLong(11, workshop.updatedAt().toEpochMilli());
            statement.setInt(12, workshop.announcementEligible() ? 1 : 0);
        });
    }

    private void refreshWebsite() {
        if (!isWebsiteSyncEnabled() || !refreshInProgress.compareAndSet(false, true)) {
            return;
        }
        Instant attemptedAt = Instant.now();
        syncStatus = new WorkshopSyncStatus(true, true, attemptedAt, syncStatus.lastSuccessAt(), "", countWebsiteWorkshops());
        try {
            byte[] body = fetchFeed();
            FeedParseResult parsed = parseFeed(body, defaultTimezone);
            applyWebsiteRecords(parsed.records());
            if (parsed.invalidEntries() > 0) {
                pluginWarning("Skipped " + parsed.invalidEntries() + " invalid workshop feed entr" +
                        (parsed.invalidEntries() == 1 ? "y." : "ies."));
            }
            syncStatus = new WorkshopSyncStatus(true, false, attemptedAt, Instant.now(), "", countWebsiteWorkshops());
        } catch (Exception exception) {
            syncStatus = new WorkshopSyncStatus(true, false, attemptedAt, syncStatus.lastSuccessAt(),
                    safeError(exception), countWebsiteWorkshops());
            pluginWarning("Workshop feed refresh failed: " + safeError(exception));
        } finally {
            refreshInProgress.set(false);
        }
    }

    private byte[] fetchFeed() throws Exception {
        URI uri = parseHttpUri(feedUrl);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(readTimeoutSeconds))
                .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9")
                .header("User-Agent", "STEMCraft workshop synchroniser/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, responseInfo -> {
            long contentLength = responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_FEED_BYTES) {
                throw new IllegalStateException("Feed exceeds the " + MAX_FEED_BYTES + " byte limit");
            }
            return HttpResponse.BodyHandlers.ofInputStream().apply(responseInfo);
        });
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        if (!isHttpUri(response.uri())) {
            throw new IllegalStateException("Feed redirect left HTTP(S)");
        }
        try (InputStream input = response.body()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(MAX_FEED_BYTES, 64 * 1024L));
            byte[] buffer = new byte[8192];
            int read;
            long total = 0L;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FEED_BYTES) {
                    throw new IllegalStateException("Feed exceeds the " + MAX_FEED_BYTES + " byte limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    static @NotNull FeedParseResult parseFeed(@NotNull byte[] body, @NotNull ZoneId defaultZone) throws XMLStreamException {
        String xml = normalizeXmlDeclaration(new String(body, StandardCharsets.UTF_8));
        XMLInputFactory factory = XMLInputFactory.newFactory();
        setXmlProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        setXmlProperty(factory, "javax.xml.stream.isSupportingExternalEntities", false);
        setXmlProperty(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setXmlProperty(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        List<ParsedFeedWorkshop> records = new ArrayList<>();
        int invalid = 0;
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && "item".equals(reader.getLocalName())) {
                    ParsedFeedWorkshop parsed = readItem(reader, defaultZone);
                    if (parsed == null) invalid++;
                    else records.add(parsed);
                }
            }
        } finally {
            reader.close();
        }
        return new FeedParseResult(List.copyOf(records), invalid);
    }

    private static @Nullable ParsedFeedWorkshop readItem(XMLStreamReader reader, ZoneId defaultZone) throws XMLStreamException {
        String title = "";
        String link = "";
        String guid = "";
        String description = "";
        String start = "";
        String end = "";
        String location = "";
        String status = "";

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "item".equals(reader.getLocalName())) {
                break;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;

            switch (reader.getLocalName()) {
                case "title" -> title = readElementText(reader);
                case "link" -> link = readElementText(reader);
                case "guid" -> guid = readElementText(reader);
                case "description" -> description = readElementText(reader);
                case "startDate" -> start = readElementText(reader);
                case "endDate" -> end = readElementText(reader);
                case "location" -> location = readElementText(reader);
                case "status" -> status = readElementText(reader);
                default -> { }
            }
        }

        String sourceKey = boundedText(guid.isBlank() ? link : guid, 2048);
        if (sourceKey.isBlank() || title.isBlank()) return null;
        ZonedDateTime startAt = parseDateTime(start, defaultZone, false);
        if (startAt == null) return null;
        ZonedDateTime endAt = parseDateTime(end, defaultZone, true);
        if (endAt != null && endAt.isBefore(startAt)) endAt = null;
        String safeLink = safeHttpUrl(link);
        boolean eligible = !status.equalsIgnoreCase("closed") && !status.equalsIgnoreCase("cancelled");
        return new ParsedFeedWorkshop(sourceKey, title, description, startAt, endAt, location, safeLink, eligible);
    }

    private static String readElementText(XMLStreamReader reader) throws XMLStreamException {
        return reader.getElementText().trim();
    }

    static @NotNull String normalizeXmlDeclaration(@NotNull String xml) {
        String normalized = xml.startsWith("\uFEFF") ? xml.substring(1) : xml;
        int start = 0;
        while (start < normalized.length() && Character.isWhitespace(normalized.charAt(start))) start++;
        if (!normalized.startsWith("<?xml", start)) return normalized;
        int end = normalized.indexOf("?>", start);
        if (end < 0) return normalized;
        String declaration = normalized.substring(start, end + 2).replace("&quot;", "\"");
        return normalized.substring(0, start) + declaration + normalized.substring(end + 2);
    }

    static @Nullable ZonedDateTime parseDateTime(@Nullable String value, @NotNull ZoneId defaultZone, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        try { return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toZonedDateTime(); }
        catch (DateTimeParseException ignored) { }
        try { return ZonedDateTime.parse(trimmed, DateTimeFormatter.ISO_ZONED_DATE_TIME); }
        catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(defaultZone); }
        catch (DateTimeParseException ignored) { }
        try {
            LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atTime(endOfDay ? java.time.LocalTime.MAX : java.time.LocalTime.MIDNIGHT).atZone(defaultZone);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    static @NotNull String safeHttpUrl(@Nullable String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = parseHttpUri(value);
            return uri.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    static boolean matchesLocationFilter(@Nullable String location, @NotNull List<String> allowedLocations) {
        if (allowedLocations.isEmpty()) return true;
        String value = location == null ? "" : location.trim();
        return allowedLocations.stream().anyMatch(allowed ->
                allowed != null && allowed.trim().equalsIgnoreCase(value));
    }

    private static URI parseHttpUri(String value) {
        if (value == null || value.length() > 2048) throw new IllegalArgumentException("URL is invalid");
        String trimmed = value.trim();
        if (trimmed.chars().anyMatch(character -> Character.isWhitespace(character) || character == '\'' ||
                character == '"' || character == '<' || character == '>' || character == '\\')) {
            throw new IllegalArgumentException("URL contains unsafe characters");
        }
        URI uri = URI.create(trimmed);
        if (!isHttpUri(uri) || uri.getUserInfo() != null || uri.getHost() == null) {
            throw new IllegalArgumentException("Only HTTP(S) URLs with a host are allowed");
        }
        return uri;
    }

    private static boolean isHttpUri(URI uri) {
        if (uri == null || uri.getScheme() == null) return false;
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private void applyWebsiteRecords(List<ParsedFeedWorkshop> records) {
        Map<String, ParsedFeedWorkshop> unique = deduplicateRecords(records);
        Instant now = Instant.now();
        Map<String, Workshop> website = new LinkedHashMap<>();
        for (ParsedFeedWorkshop record : unique.values()) {
            String id = stableWebsiteId(record.sourceKey());
            website.put(id, new Workshop(id, record.title(), record.description(), record.startAt(), record.endAt(),
                    record.location(), record.bookingUrl(), WorkshopSource.WEBSITE, now, record.announcementEligible()));
        }

        synchronized (storageLock) {
            if (cachePersistence) {
                Set<String> existingWebsiteIds = new HashSet<>();
                api.database().queryEach(
                        "SELECT id FROM workshops WHERE source = 'WEBSITE'",
                        null,
                        result -> existingWebsiteIds.add(result.getString("id"))
                );
                for (String oldId : existingWebsiteIds) {
                    if (!website.containsKey(oldId)) {
                        api.database().update("DELETE FROM workshops WHERE id = ? AND source = 'WEBSITE'",
                                statement -> statement.setString(1, oldId));
                    }
                }
                for (Workshop workshop : website.values()) {
                    persistWebsiteWorkshop(workshop, findSourceKey(unique, workshop.id()));
                }
            }

            synchronized (cache) {
                cache.entrySet().removeIf(entry -> entry.getValue().source() == WorkshopSource.WEBSITE);
                cache.putAll(website);
            }
        }
    }

    private void persistWebsiteWorkshop(Workshop workshop, String sourceKey) {
        Long end = workshop.endAt() == null ? null : workshop.endAt().toInstant().toEpochMilli();
        api.database().update("""
                INSERT INTO workshops
                    (id, source, source_key, title, description, start_at, end_at, timezone, location, booking_url, updated_at, announcement_eligible)
                VALUES (?, 'WEBSITE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    source_key = excluded.source_key, title = excluded.title, description = excluded.description,
                    start_at = excluded.start_at, end_at = excluded.end_at, timezone = excluded.timezone,
                    location = excluded.location, booking_url = excluded.booking_url, updated_at = excluded.updated_at,
                    announcement_eligible = excluded.announcement_eligible
                """, statement -> {
            statement.setString(1, workshop.id());
            statement.setString(2, sourceKey);
            statement.setString(3, workshop.title());
            statement.setString(4, workshop.description());
            statement.setLong(5, workshop.startAt().toInstant().toEpochMilli());
            if (end == null) statement.setObject(6, null); else statement.setLong(6, end);
            statement.setString(7, workshop.startAt().getZone().getId());
            statement.setString(8, workshop.location());
            statement.setString(9, workshop.bookingUrl());
            statement.setLong(10, workshop.updatedAt().toEpochMilli());
            statement.setInt(11, workshop.announcementEligible() ? 1 : 0);
        });
    }

    private static String findSourceKey(Map<String, ParsedFeedWorkshop> records, String id) {
        String hash = id.startsWith("web-") ? id.substring(4) : id;
        return records.values().stream()
                .filter(record -> shortHash(record.sourceKey()).equals(hash))
                .map(ParsedFeedWorkshop::sourceKey)
                .findFirst()
                .orElse(id);
    }

    private void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;
        api.events().register(PlayerJoinEvent.class, event -> scheduleAccessPrompt(event.getPlayer()));
        api.events().register(PlayerQuitEvent.class, event -> cancelAccessPrompt(event.getPlayer().getUniqueId()));
    }

    private void scheduleAccessPrompt(@NotNull Player player) {
        if (!enabled || !instructionsPromptEnabled || !canManageInstructions(player)) return;
        String taskId = ACCESS_PROMPT_TASK_PREFIX + player.getUniqueId();
        String previous = accessPromptTasks.put(player.getUniqueId(), taskId);
        if (previous != null) api.tasks().cancel(previous);
        api.tasks().runOnceDelay(taskId, instructionsPromptDelaySeconds * 20L, () -> {
            accessPromptTasks.remove(player.getUniqueId(), taskId);
            if (!enabled || !player.isOnline()) return;
            promptForAccessInstructions(player);
        });
    }

    private void cancelAccessPrompt(@NotNull UUID playerId) {
        String taskId = accessPromptTasks.remove(playerId);
        if (taskId != null) api.tasks().cancel(taskId);
    }

    private void cancelAccessPromptTasks() {
        for (String taskId : accessPromptTasks.values()) api.tasks().cancel(taskId);
        accessPromptTasks.clear();
    }

    private void registerDebugTests() {
        if (workshopStatusDebug != null) return;
        workshopStatusDebug = api.debug().register(
                "workshop-status",
                "Inspect workshop configuration, cache, and feed synchronisation state.",
                this::debugWorkshopStatus
        );
        workshopUpcomingDebug = api.debug().register(
                "workshop-upcoming",
                "Preview the player-facing upcoming workshops list.",
                this::debugWorkshopUpcoming
        );
        workshopAccessPromptDebug = api.debug().register(
                "workshop-access-prompt",
                "Preview and deliver the workshop access-instructions prompt.",
                this::debugWorkshopAccessPrompt
        );
    }

    private void unregisterDebugTests() {
        if (workshopStatusDebug != null) workshopStatusDebug.close();
        if (workshopUpcomingDebug != null) workshopUpcomingDebug.close();
        if (workshopAccessPromptDebug != null) workshopAccessPromptDebug.close();
        workshopStatusDebug = null;
        workshopUpcomingDebug = null;
        workshopAccessPromptDebug = null;
    }

    private void debugWorkshopStatus(@NotNull DebugContext context) {
        Player player = context.player();
        if (player == null) {
            context.error("This debug test requires a player target.");
            return;
        }

        WorkshopSyncStatus status = syncStatus;
        List<Workshop> upcoming = upcoming();
        List<Workshop> visible = visibleUpcoming();
        int cached;
        int manual;
        synchronized (cache) {
            cached = cache.size();
            manual = (int) cache.values().stream()
                    .filter(workshop -> workshop.source() == WorkshopSource.MANUAL)
                    .count();
        }

        context.info("<aqua>Debug test: workshop-status</aqua>");
        context.info("Player: " + player.getName());
        context.info("Workshops feature enabled: " + enabled);
        context.info("Website workshops enabled: " + websiteEnabled);
        context.info("Website feed synchronisation enabled: " + syncEnabled);
        context.info("Manual workshops enabled: " + manualEnabled);
        context.info("Cache persistence enabled: " + cachePersistence);
        context.info("Cached workshops: " + cached + " (website: " + countWebsiteWorkshops() + ", manual: " + manual + ")");
        context.info("Upcoming workshops: " + upcoming.size());
        context.info("Visible after location filters: " + visible.size() + " (maximum displayed: " + maximumDisplayed + ")");
        context.info("Synchronisation enabled: " + status.enabled());
        context.info("Synchronisation in progress: " + status.inProgress());
        context.info("Website workshops in last sync snapshot: " + status.websiteWorkshopCount());
        context.info("Last sync attempt: " + String.valueOf(status.lastAttemptAt()));
        context.info("Last successful sync: " + String.valueOf(status.lastSuccessAt()));
        if (!status.lastError().isBlank()) {
            context.warn("Last sync error: " + AnnouncementsFeature.escapeLiteral(status.lastError()));
        }

        Workshop next = nextUpcoming();
        if (next == null) {
            context.warn("There is no upcoming workshop in the current cache.");
        } else {
            context.info("Next workshop: " + debugWorkshopSummary(next));
        }
        Workshop instructionTarget = nextInstructionWorkshop();
        if (instructionTarget == null) {
            context.info("Access-instructions target: none");
        } else {
            context.info("Access-instructions target: " + debugWorkshopSummary(instructionTarget));
            context.info("Stored access-instruction lines: " + accessInstructions(instructionTarget).size());
        }
        context.success("Workshop status inspection completed.");
    }

    private void debugWorkshopUpcoming(@NotNull DebugContext context) {
        Player player = context.player();
        if (player == null) return;

        List<Workshop> liveUpcoming = visibleUpcoming();
        boolean simulated = liveUpcoming.isEmpty();
        List<Workshop> upcoming = simulated
                ? List.of(AnnouncementsFeature.sampleDebugWorkshop(Instant.now(), defaultTimezone, false))
                : liveUpcoming;

        int limit = Math.min(maximumDisplayed, upcoming.size());
        List<String> previewLines = new ArrayList<>();
        previewLines.addAll(sendDisplayLines(player, displayHeader, null, 0, upcoming.size(), limit));
        for (int i = 0; i < limit; i++) {
            Workshop workshop = upcoming.get(i);
            previewLines.addAll(sendDisplayLines(player, displayEntry, workshop, i + 1, upcoming.size(), limit));
            if (workshop.bookingUrl().isBlank()) {
                previewLines.addAll(sendDisplayLines(player, displayNoBooking, workshop, i + 1, upcoming.size(), limit));
            }
            previewLines.addAll(sendDisplayLines(player, accessInstructions(workshop), workshop,
                    i + 1, upcoming.size(), limit));
        }
        previewLines.addAll(sendDisplayLines(player, displayFooter, null, 0, upcoming.size(), limit));
        for (String line : previewLines) echoDebugPreviewLine(context, player, line);
    }

    private void debugWorkshopAccessPrompt(@NotNull DebugContext context) {
        Player player = context.player();
        if (player == null) return;

        Instant now = Instant.now();
        Workshop liveWorkshop = nextInstructionWorkshop();
        Workshop workshop = liveWorkshop;
        if (workshop == null) {
            workshop = upcoming().stream().findFirst()
                    .orElseGet(() -> AnnouncementsFeature.sampleDebugWorkshop(now, defaultTimezone, false));
        }

        if (instructionsPromptMessage.isEmpty()) return;

        for (String template : instructionsPromptMessage) {
            String message = renderInstructionsPrompt(template, workshop, player, now);
            if (message.isBlank()) continue;
            player.sendMessage(MINI_MESSAGE.deserialize(message));
            if (!context.sender().equals(player)) {
                context.sender().sendMessage(AnnouncementsFeature.playerVisibleText(message));
            }
        }
    }

    private void echoDebugPreviewLine(@NotNull DebugContext context, @NotNull Player player,
                                      @NotNull String message) {
        if (!context.sender().equals(player)) {
            context.sender().sendMessage(AnnouncementsFeature.playerVisibleText(message));
        }
    }

    private static @NotNull String debugWorkshopSummary(@NotNull Workshop workshop) {
        return workshop.id() + " (" + AnnouncementsFeature.escapeLiteral(workshop.title()) + ") at "
                + workshop.startAt();
    }

    private void promptForAccessInstructions(@NotNull Player player) {
        if (!instructionsPromptEnabled || !canManageInstructions(player)) return;
        Instant now = Instant.now();
        Workshop workshop = nextInstructionWorkshop();
        if (workshop == null || !withinPromptWindow(workshop, now) || !accessInstructions(workshop).isEmpty()) return;

        for (String template : instructionsPromptMessage) {
            String rendered = renderInstructionsPrompt(template, workshop, player, now);
            if (!rendered.isBlank()) player.sendMessage(MINI_MESSAGE.deserialize(rendered));
        }
    }

    static boolean withinPromptWindow(@NotNull Workshop workshop, @NotNull Instant now, long windowSeconds) {
        Instant start = workshop.startAt().toInstant();
        return !now.isBefore(start.minusSeconds(Math.max(0L, windowSeconds))) && now.isBefore(start);
    }

    private boolean withinPromptWindow(@NotNull Workshop workshop, @NotNull Instant now) {
        return withinPromptWindow(workshop, now, instructionsPromptWindowSeconds);
    }

    private @NotNull String renderInstructionsPrompt(@NotNull String template, @NotNull Workshop workshop,
                                                     @NotNull Player player, @NotNull Instant now) {
        Map<String, String> values = new LinkedHashMap<>(displayPlaceholders(workshop, 1, 1, 1, defaultTimezone));
        values.put("workshop_id", values.get("id"));
        values.put("workshop_title", values.get("title"));
        values.put("workshop_date", values.get("date"));
        values.put("workshop_time", values.get("time"));
        values.put("workshop_url", values.get("booking_url"));
        values.put("workshop_location", values.get("location"));
        long countdown = Math.max(0L, workshop.startAt().toEpochSecond() - now.getEpochSecond());
        values.put("workshop_countdown", AnnouncementsFeature.escapeLiteral(
                countdown == 0L ? "now" : TimeUtil.formatLongDuration(countdown)));
        String rendered = PlaceholderUtil.apply(
                WorkshopDateFormatter.apply(template, WorkshopDateFormatter.dateValues(workshop, defaultTimezone)),
                values);
        String expanded = api.placeholders().apply(player, rendered);
        return expanded == null ? "" : expanded;
    }

    static @NotNull Map<String, ParsedFeedWorkshop> deduplicateRecords(@NotNull List<ParsedFeedWorkshop> records) {
        Map<String, ParsedFeedWorkshop> unique = new LinkedHashMap<>();
        for (ParsedFeedWorkshop record : records) {
            if (record != null) unique.putIfAbsent(record.sourceKey(), record);
        }
        return unique;
    }

    static @NotNull String stableWebsiteId(@NotNull String sourceKey) {
        return "web-" + shortHash(sourceKey);
    }

    private void registerTabCompletion() {
        api.tabComplete().register("workshop-id", (player, args) -> {
            synchronized (cache) {
                return cache.keySet().stream().sorted().toList();
            }
        });
    }

    private void registerCommand() {
        command = api.commands().create("workshops")
                .description("WORKSHOPS_DESCRIPTION")
                .usage("WORKSHOPS_USAGE")
                .access((sender, args) -> {
                    String subcommand = args.isEmpty() ? "" : args.getFirst().toLowerCase(Locale.ROOT);
                    boolean publicAction = subcommand.isBlank() || subcommand.equals("help") ||
                            subcommand.equals("list") || subcommand.equals("info");
                    boolean instructionsAction = subcommand.equals("instructions") &&
                            (sender.hasPermission(instructionsPermission) || sender.hasPermission("stemcraft.workshops.admin"));
                    return publicAction || sender.hasPermission("stemcraft.workshops.admin") ||
                            sender.hasPermission("stemcraft.workshops.refresh") || instructionsAction;
                })
                .tabCompletion("help")
                .tabCompletion("list")
                .tabCompletion("add")
                .tabCompletion("edit", "{workshop-id}")
                .tabCompletion("remove", "{workshop-id}")
                .tabCompletion("instructions")
                .tabCompletion("instructions", "clear")
                .tabCompletion("refresh")
                .tabCompletion("info", "{workshop-id}")
                .executor((unused, cmd, ctx) -> handleCommand(ctx))
                .register(STEMCraft.getPlugin());
    }

    private void handleCommand(CommandContext ctx) {
        String subcommand = ctx.getArgLower(0);
        if (subcommand == null || subcommand.equals("list")) {
            showWorkshops(ctx);
            return;
        }
        switch (subcommand) {
            case "help" -> showHelp(ctx);
            case "add" -> addWorkshop(ctx);
            case "edit" -> editWorkshop(ctx);
            case "remove" -> removeWorkshop(ctx);
            case "instructions" -> setWorkshopInstructions(ctx);
            case "refresh" -> refreshWorkshopFeed(ctx);
            case "info" -> showWorkshopInfo(ctx);
            default -> ctx.returnUsage();
        }
    }

    private void showHelp(CommandContext ctx) {
        ctx.info("/workshops [list] - show upcoming workshops");
        ctx.info("/workshops info <id> - show workshop details");
        if (isAdmin(ctx) || canManageInstructions(ctx)) {
            if (isAdmin(ctx)) {
                ctx.info("/workshops add <title> | <start> | <end|-> | <location> | <url|-> | <description>");
                ctx.info("/workshops edit <id> <title> | <start> | <end|-> | <location> | <url|-> | <description>");
                ctx.info("/workshops remove <id>");
            }
            ctx.info("/workshops instructions <message> | <message> ...");
            ctx.info("/workshops instructions clear");
        }
        if (isAdmin(ctx)) {
            ctx.info("/workshops refresh");
        }
    }

    private void showWorkshops(CommandContext ctx) {
        List<Workshop> upcoming = visibleUpcoming();
        if (upcoming.isEmpty()) {
            ctx.info("<gray>No upcoming STEMMechanics workshops are scheduled.</gray>");
            return;
        }

        int limit = Math.min(maximumDisplayed, upcoming.size());
        List<Workshop> displayed = upcoming.subList(0, limit);
        sendDisplayLines(ctx.getSender(), displayHeader, null, 0, upcoming.size(), displayed.size());
        for (int i = 0; i < limit; i++) {
            Workshop workshop = upcoming.get(i);
            sendDisplayLines(ctx.getSender(), displayEntry, workshop, i + 1, upcoming.size(), displayed.size());
            if (workshop.bookingUrl().isBlank()) {
                sendDisplayLines(ctx.getSender(), displayNoBooking, workshop, i + 1, upcoming.size(), displayed.size());
            }
            sendDisplayLines(ctx.getSender(), accessInstructions(workshop), workshop,
                    i + 1, upcoming.size(), displayed.size());
            if (isAdmin(ctx)) {
                ctx.info("ID: {id} | Source: {source}",
                        "id", workshop.id(), "source", workshop.source().name());
            }
        }
        sendDisplayLines(ctx.getSender(), displayFooter, null, 0, upcoming.size(), displayed.size());
    }

    private @NotNull List<String> sendDisplayLines(@NotNull CommandSender sender, @NotNull List<String> templates,
                                                   @Nullable Workshop workshop, int index, int count, int shown) {
        List<String> renderedLines = new ArrayList<>();
        for (String template : templates) {
            if (skipOptionalDisplayLine(template, workshop)) continue;
            String rendered = renderDisplayLine(template, workshop, index, count, shown);
            if (!rendered.isBlank()) renderedLines.add(rendered);
        }
        for (String rendered : renderedLines) sender.sendMessage(MINI_MESSAGE.deserialize(rendered));
        return List.copyOf(renderedLines);
    }

    private static boolean skipOptionalDisplayLine(@NotNull String template, @Nullable Workshop workshop) {
        if (workshop == null) return false;
        String normalized = template.toLowerCase(Locale.ROOT);
        if (workshop.bookingUrl().isBlank() && (normalized.contains("{booking_url}") || normalized.contains("{url}"))) {
            return true;
        }
        if (workshop.location().isBlank() && normalized.contains("{location}")) return true;
        return workshop.description().isBlank() && normalized.contains("{description}");
    }

    private @NotNull String renderDisplayLine(@NotNull String template, @Nullable Workshop workshop,
                                               int index, int count, int shown) {
        String rendered = PlaceholderUtil.apply(
                WorkshopDateFormatter.apply(template, WorkshopDateFormatter.dateValues(workshop, defaultTimezone)),
                displayPlaceholders(workshop, index, count, shown, defaultTimezone));
        String expanded = api.placeholders().apply(rendered);
        return expanded == null ? "" : expanded;
    }

    static @NotNull Map<String, String> displayPlaceholders(@Nullable Workshop workshop, int index, int count,
                                                             int shown, @NotNull ZoneId timezone) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("index", Integer.toString(index));
        values.put("count", Integer.toString(count));
        values.put("shown", Integer.toString(shown));
        values.put("title", "");
        values.put("description", "");
        values.put("date", "");
        values.put("time", "");
        values.put("start_date", "");
        values.put("start_time", "");
        values.put("start", "");
        values.put("end_date", "");
        values.put("end_time", "");
        values.put("end", "");
        values.put("location", "");
        values.put("booking_url", "");
        values.put("url", "");
        values.put("id", "");
        values.put("source", "");
        values.put("next_workshop_title", "");
        values.put("next_workshop_date", "");
        values.put("next_workshop_time", "");
        values.put("next_workshop_url", "");
        values.put("next_workshop_location", "");
        values.put("next_workshop_end_date", "");
        values.put("next_workshop_end_time", "");
        values.put("active_workshop_title", "");
        values.put("active_workshop_date", "");
        values.put("active_workshop_time", "");
        values.put("active_workshop_url", "");
        values.put("active_workshop_location", "");
        values.put("active_workshop_end_date", "");
        values.put("active_workshop_end_time", "");
        if (workshop == null) return values;

        ZonedDateTime start = workshop.startAt().withZoneSameInstant(timezone);
        String date = DISPLAY_DATE_FORMAT.format(start);
        String time = DISPLAY_TIME_FORMAT.format(start);
        values.put("title", AnnouncementsFeature.escapeLiteral(workshop.title()));
        values.put("description", AnnouncementsFeature.escapeLiteral(workshop.description()));
        values.put("date", AnnouncementsFeature.escapeLiteral(date));
        values.put("time", AnnouncementsFeature.escapeLiteral(time));
        values.put("start_date", AnnouncementsFeature.escapeLiteral(date));
        values.put("start_time", AnnouncementsFeature.escapeLiteral(time));
        values.put("start", AnnouncementsFeature.escapeLiteral(date + " at " + time));
        values.put("location", AnnouncementsFeature.escapeLiteral(workshop.location()));
        values.put("booking_url", AnnouncementsFeature.escapeLiteral(workshop.bookingUrl()));
        values.put("url", AnnouncementsFeature.escapeLiteral(workshop.bookingUrl()));
        values.put("id", AnnouncementsFeature.escapeLiteral(workshop.id()));
        values.put("source", AnnouncementsFeature.escapeLiteral(workshop.source().name().toLowerCase(Locale.ROOT)));
        values.put("next_workshop_title", values.get("title"));
        values.put("next_workshop_date", values.get("date"));
        values.put("next_workshop_time", values.get("time"));
        values.put("next_workshop_url", values.get("booking_url"));
        values.put("next_workshop_location", values.get("location"));
        values.put("active_workshop_title", values.get("title"));
        values.put("active_workshop_date", values.get("date"));
        values.put("active_workshop_time", values.get("time"));
        values.put("active_workshop_url", values.get("booking_url"));
        values.put("active_workshop_location", values.get("location"));
        if (workshop.endAt() != null) {
            ZonedDateTime end = workshop.endAt().withZoneSameInstant(timezone);
            String endDate = DISPLAY_DATE_FORMAT.format(end);
            String endTime = DISPLAY_TIME_FORMAT.format(end);
            values.put("end_date", AnnouncementsFeature.escapeLiteral(endDate));
            values.put("end_time", AnnouncementsFeature.escapeLiteral(endTime));
            values.put("end", AnnouncementsFeature.escapeLiteral(endDate + " at " + endTime));
            values.put("next_workshop_end_date", values.get("end_date"));
            values.put("next_workshop_end_time", values.get("end_time"));
            values.put("active_workshop_end_date", values.get("end_date"));
            values.put("active_workshop_end_time", values.get("end_time"));
        }
        return values;
    }

    private Component renderWorkshopLine(int index, Workshop workshop) {
        ZonedDateTime start = workshop.startAt().withZoneSameInstant(defaultTimezone);
        Component line = Component.text(index + ". " + workshop.title(), NamedTextColor.YELLOW)
                .append(Component.text("\n   " + DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a").format(start), NamedTextColor.WHITE));
        if (!workshop.location().isBlank()) {
            line = line.append(Component.text("\n   " + workshop.location(), NamedTextColor.GRAY));
        }
        if (!workshop.bookingUrl().isBlank()) {
            line = line.append(Component.text("\n   "))
                    .append(Component.text("[Click to view details]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(workshop.bookingUrl())));
        } else {
            line = line.append(Component.text("\n   Details: /workshops info " + workshop.id(), NamedTextColor.DARK_GRAY));
        }
        return line;
    }

    private void showWorkshopInfo(CommandContext ctx) {
        if (ctx.numArgs() < 2) ctx.returnUsage();
        Workshop workshop = find(ctx.getArg(1));
        if (workshop == null) {
            ctx.returnError("Workshop not found.");
            return;
        }
        if (!isAdmin(ctx) && !matchesLocationFilter(workshop.location(), displayLocations)) {
            ctx.returnError("Workshop not found.");
            return;
        }
        ctx.getSender().sendMessage(Component.text(workshop.title(), NamedTextColor.AQUA));
        if (!workshop.description().isBlank()) {
            ctx.getSender().sendMessage(Component.text(workshop.description(), NamedTextColor.GRAY));
        }
        ctx.getSender().sendMessage(renderWorkshopLine(1, workshop));
        sendDisplayLines(ctx.getSender(), accessInstructions(workshop), workshop, 1, 1, 1);
        if (isAdmin(ctx)) ctx.info("ID: {id} | Source: {source}", "id", workshop.id(), "source", workshop.source().name());
    }

    private void setWorkshopInstructions(CommandContext ctx) {
        requireInstructionsAccess(ctx);
        if (ctx.numArgs() < 2) ctx.returnUsage();
        String raw = String.join(" ", ctx.rawArgs().subList(1, ctx.rawArgs().size())).trim();
        if (raw.equalsIgnoreCase("clear")) {
            if (!clearAccessInstructions()) ctx.returnError("Could not clear the workshop access instructions.");
            ctx.returnSuccess("Cleared the current workshop access instructions.");
            return;
        }
        Workshop workshop = nextInstructionWorkshop();
        if (workshop == null) {
            ctx.returnError("There is no upcoming eligible workshop to configure.");
            return;
        }
        List<String> lines = parseAccessInstructions(raw);
        if (lines.isEmpty()) ctx.returnError("Provide an access message, or use clear.");
        if (!saveAccessInstructions(workshop, lines)) {
            ctx.returnError("Could not save the workshop access instructions.");
            return;
        }
        ctx.returnSuccess("Saved access instructions for the next workshop.");
    }

    private void addWorkshop(CommandContext ctx) {
        requireAdmin(ctx);
        WorkshopInput input = parseWorkshopInput(ctx, 1);
        if (input == null) return;
        Workshop workshop = WorkshopService.manual(nextManualId(), input.title(), input.description(), input.startAt(), input.endAt(),
                input.location(), input.bookingUrl(), true);
        if (addManual(workshop) == null) ctx.returnError("Could not save the manual workshop.");
        ctx.returnSuccess("Added workshop {id}.", "id", workshop.id());
    }

    private void editWorkshop(CommandContext ctx) {
        requireAdmin(ctx);
        if (ctx.numArgs() < 3) ctx.returnUsage();
        Workshop existing = find(ctx.getArg(1));
        if (existing == null || existing.source() != WorkshopSource.MANUAL) ctx.returnError("Manual workshop not found.");
        WorkshopInput input = parseWorkshopInput(ctx, 2);
        if (input == null) return;
        Workshop replacement = WorkshopService.manual(existing.id(), input.title(), input.description(), input.startAt(), input.endAt(),
                input.location(), input.bookingUrl(), existing.announcementEligible());
        if (!updateManual(replacement)) ctx.returnError("Could not update the manual workshop.");
        ctx.returnSuccess("Updated workshop {id}.", "id", existing.id());
    }

    private void removeWorkshop(CommandContext ctx) {
        requireAdmin(ctx);
        if (ctx.numArgs() < 2) ctx.returnUsage();
        if (!removeManual(ctx.getArg(1))) ctx.returnError("Manual workshop not found.");
        ctx.returnSuccess("Removed workshop {id}.", "id", ctx.getArg(1));
    }

    private void refreshWorkshopFeed(CommandContext ctx) {
        if (!ctx.hasPermission("stemcraft.workshops.refresh") && !isAdmin(ctx)) {
            ctx.returnError("COMMAND_NO_PERMISSION");
        }
        refresh();
        ctx.returnInfo("Workshop feed refresh started in the background.");
    }

    private @Nullable WorkshopInput parseWorkshopInput(CommandContext ctx, int start) {
        List<String> raw = ctx.rawArgs();
        if (raw.size() <= start) {
            ctx.returnUsage();
        }
        String joined = String.join(" ", raw.subList(start, raw.size()));
        String[] fields = joined.split("\\s*\\|\\s*", -1);
        if (fields.length < 5) {
            ctx.returnError("Use: <title> | <start> | <end|-> | <location> | <url|-> | <description>");
        }
        String title = fields[0].trim();
        ZonedDateTime startAt = parseDateTime(fields[1].trim(), defaultTimezone, false);
        ZonedDateTime endAt = "-".equals(fields[2].trim()) ? null : parseDateTime(fields[2].trim(), defaultTimezone, true);
        String location = fields[3].trim();
        String url = "-".equals(fields[4].trim()) ? "" : safeHttpUrl(fields[4].trim());
        String description = fields.length >= 6 ? fields[5].trim() : "";
        if (title.isBlank() || startAt == null || (!fields[2].trim().equals("-") && endAt == null) || location.isBlank() ||
                (!fields[4].trim().equals("-") && url.isBlank())) {
            ctx.returnError("Invalid workshop. Use ISO dates and HTTP(S) booking URLs.");
        }
        if (endAt != null && endAt.isBefore(startAt)) ctx.returnError("Workshop end must not be before its start.");
        return new WorkshopInput(title, description, startAt, endAt, location, url);
    }

    static @NotNull List<String> parseAccessInstructions(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        for (String value : raw.split("\\s*\\|\\s*", -1)) {
            String line = boundedText(value, MAX_ACCESS_INSTRUCTION_LENGTH);
            if (line.isBlank()) continue;
            lines.add(line);
            if (lines.size() >= MAX_ACCESS_INSTRUCTION_LINES) break;
        }
        return List.copyOf(lines);
    }

    private String nextManualId() {
        String id;
        do {
            id = "manual-" + shortHash(java.util.UUID.randomUUID().toString()).substring(0, 8);
        } while (find(id) != null);
        return id;
    }

    private boolean requireAdmin(CommandContext ctx) {
        if (!isAdmin(ctx)) ctx.returnError("COMMAND_NO_PERMISSION");
        return true;
    }

    private boolean requireInstructionsAccess(CommandContext ctx) {
        if (!canManageInstructions(ctx)) ctx.returnError("COMMAND_NO_PERMISSION");
        return true;
    }

    private boolean canManageInstructions(CommandContext ctx) {
        return ctx.hasPermission(instructionsPermission) || isAdmin(ctx);
    }

    private boolean canManageInstructions(@NotNull CommandSender sender) {
        return sender.hasPermission(instructionsPermission) || sender.hasPermission("stemcraft.workshops.admin");
    }

    private boolean isAdmin(CommandContext ctx) {
        return ctx.hasPermission("stemcraft.workshops.admin");
    }

    private int countWebsiteWorkshops() {
        synchronized (cache) {
            return (int) cache.values().stream().filter(workshop -> workshop.source() == WorkshopSource.WEBSITE).count();
        }
    }

    private static Comparator<Workshop> workshopComparator() {
        return Comparator.comparing((Workshop workshop) -> workshop.startAt().toInstant())
                .thenComparing(Workshop::id);
    }

    private static long parseDuration(String value, long fallback, long min, long max) {
        try {
            return Math.clamp(TimeUtil.parseDuration(value), min, max);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static ZoneId parseZone(String value, ZoneId fallback) {
        try { return ZoneId.of(value); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String boundedText(String value, int maximum) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() > maximum ? text.substring(0, maximum) : text;
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item));
            return result.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static void setXmlProperty(XMLInputFactory factory, String key, Object value) {
        try { factory.setProperty(key, value); }
        catch (IllegalArgumentException ignored) { }
    }

    private static boolean isValidBookingUrl(String url) {
        return url == null || url.isBlank() || !safeHttpUrl(url).isBlank();
    }

    private void pluginWarning(String message) {
        STEMCraft.getPlugin().getLogger().warning("[workshops] " + message);
    }

    private static String safeError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : boundedText(message, 240);
    }

    record ParsedFeedWorkshop(String sourceKey, String title, String description, ZonedDateTime startAt,
                              ZonedDateTime endAt, String location, String bookingUrl, boolean announcementEligible) { }

    record FeedParseResult(List<ParsedFeedWorkshop> records, int invalidEntries) { }

    private record WorkshopInput(String title, String description, ZonedDateTime startAt, ZonedDateTime endAt,
                                 String location, String bookingUrl) { }
}
