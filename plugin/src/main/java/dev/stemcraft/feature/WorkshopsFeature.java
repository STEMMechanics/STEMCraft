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
import dev.stemcraft.api.service.workshop.WorkshopSource;
import dev.stemcraft.api.service.workshop.WorkshopSyncStatus;
import dev.stemcraft.api.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
import java.util.concurrent.atomic.AtomicBoolean;

/** Maintains website and manually created STEMMechanics workshops. */
public class WorkshopsFeature extends BaseFeature implements WorkshopService {
    static final String REFRESH_TASK = "feature:workshops-refresh";
    static final String MIGRATION_NAME = "workshops";
    static final int MIGRATION_VERSION = 1;
    static final long MAX_FEED_BYTES = 1_048_576L;
    static final String DEFAULT_FEED_URL = "https://www.stemmechanics.com.au/workshops/feed";
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Australia/Brisbane");

    private final Object storageLock = new Object();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean();
    private final Map<String, Workshop> cache = new LinkedHashMap<>();

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
    private volatile WorkshopSyncStatus syncStatus = new WorkshopSyncStatus(false, false, null, null, "", 0);
    private Command command;

    public WorkshopsFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        loadSettings();
        ensureStorage();
        loadStoredWorkshops();
        registerTabCompletion();
        registerCommand();
        restartRefreshTask();
    }

    @Override
    public void onReload() {
        super.onReload();
        boolean wasEnabled = enabled;
        loadSettings();
        if (!enabled) {
            api.tasks().cancel(REFRESH_TASK);
            if (command != null) {
                command.unregister();
                command = null;
            }
            return;
        }
        if (!wasEnabled) {
            registerTabCompletion();
            registerCommand();
        }
        loadStoredWorkshops();
        restartRefreshTask();
    }

    @Override
    public void onDisable() {
        enabled = false;
        api.tasks().cancel(REFRESH_TASK);
        if (command != null) {
            command.unregister();
            command = null;
        }
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
        if (!enabled || !websiteEnabled || !syncEnabled) {
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
        feedUrl = section.getString("rss-url", DEFAULT_FEED_URL).trim();
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
        syncStatus = new WorkshopSyncStatus(enabled && websiteEnabled && syncEnabled, false,
                syncStatus.lastAttemptAt(), syncStatus.lastSuccessAt(), syncStatus.lastError(), countWebsiteWorkshops());
    }

    private void restartRefreshTask() {
        api.tasks().cancel(REFRESH_TASK);
        if (!enabled || !websiteEnabled || !syncEnabled) {
            return;
        }
        long periodTicks = Math.max(20L, refreshIntervalSeconds * 20L);
        api.tasks().repeating(REFRESH_TASK, periodTicks, periodTicks, this::refresh);
        refresh();
    }

    private void ensureStorage() {
        if (api.database().migrationVersion(MIGRATION_NAME) >= MIGRATION_VERSION) {
            return;
        }

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
        api.database().setMigrationVersion(MIGRATION_NAME, MIGRATION_VERSION);
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
        if (!enabled || !websiteEnabled || !syncEnabled || !refreshInProgress.compareAndSet(false, true)) {
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
                    return publicAction || sender.hasPermission("stemcraft.workshops.admin") ||
                            sender.hasPermission("stemcraft.workshops.refresh");
                })
                .tabCompletion("help")
                .tabCompletion("list")
                .tabCompletion("add")
                .tabCompletion("edit", "{workshop-id}")
                .tabCompletion("remove", "{workshop-id}")
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
            case "refresh" -> refreshWorkshopFeed(ctx);
            case "info" -> showWorkshopInfo(ctx);
            default -> ctx.returnUsage();
        }
    }

    private void showHelp(CommandContext ctx) {
        ctx.info("/workshops [list] - show upcoming workshops");
        ctx.info("/workshops info <id> - show workshop details");
        if (isAdmin(ctx)) {
            ctx.info("/workshops add <title> | <start> | <end|-> | <location> | <url|-> | <description>");
            ctx.info("/workshops edit <id> <title> | <start> | <end|-> | <location> | <url|-> | <description>");
            ctx.info("/workshops remove <id>");
            ctx.info("/workshops refresh");
        }
    }

    private void showWorkshops(CommandContext ctx) {
        List<Workshop> upcoming = visibleUpcoming();
        if (upcoming.isEmpty()) {
            ctx.info("<gray>No upcoming STEMMechanics workshops are scheduled.</gray>");
            return;
        }

        ctx.info("<aqua><bold>STEMMechanics Upcoming Workshops</bold></aqua>");
        int limit = Math.min(maximumDisplayed, upcoming.size());
        for (int i = 0; i < limit; i++) {
            Workshop workshop = upcoming.get(i);
            ctx.getSender().sendMessage(renderWorkshopLine(i + 1, workshop));
        }
        ctx.info("<gray>Visit stemmechanics.com.au for more workshops.</gray>");
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
        if (isAdmin(ctx)) ctx.info("ID: {id} | Source: {source}", "id", workshop.id(), "source", workshop.source().name());
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
