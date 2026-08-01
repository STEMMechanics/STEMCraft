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

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Feature for first-time, returning, and anniversary welcome messages.
 */
public class PlayerWelcome extends BaseFeature {
    private static final String TABLE_NAME = "player_welcome_state";
    private static final String MIGRATION_NAME = "player-welcome";
    private static final int MIGRATION_VERSION = 1;
    private static final long JOIN_MESSAGE_DELAY_TICKS = 20L;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private boolean enabled;
    private List<String> firstTimeMessage = List.of();
    private List<String> returningMessage = List.of();
    private Map<Integer, List<String>> anniversaryMessages = Map.of();

    public enum MessageKind {
        FIRST_TIME("first-time", "first-time"),
        RETURNING("returning", "returning"),
        ANNIVERSARY("anniversary", "anniversaries");

        private final String displayName;
        private final String configRoot;

        MessageKind(String displayName, String configRoot) {
            this.displayName = displayName;
            this.configRoot = configRoot;
        }

        public String displayName() {
            return displayName;
        }
    }

    public PlayerWelcome(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        reloadSettings();
        ensureStorage();

        api.events().register(PlayerJoinEvent.class, event -> {
            if (!enabled) {
                return;
            }

            UUID playerId = event.getPlayer().getUniqueId();
            api.tasks().runLater(JOIN_MESSAGE_DELAY_TICKS, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    return;
                }
                handleJoin(player);
            });
        });
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
    }

    private void reloadSettings() {
        enabled = isEnabled();
        firstTimeMessage = readMessageList("first-time");
        returningMessage = readMessageList("returning");
        anniversaryMessages = readAnniversaryMessages();
    }

    private void ensureStorage() {
        if (api.database().migrationVersion(MIGRATION_NAME) >= MIGRATION_VERSION) {
            return;
        }

        boolean created = api.database().execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "player_uuid TEXT PRIMARY KEY," +
                "first_joined_at INTEGER NOT NULL," +
                "last_anniversary_year INTEGER NOT NULL," +
                "last_name TEXT NOT NULL" +
            ");"
        );
        if (!created) {
            api.messages().error("Failed to create " + TABLE_NAME + " table.");
            return;
        }

        api.database().setMigrationVersion(MIGRATION_NAME, MIGRATION_VERSION);
    }

    private void handleJoin(@NotNull Player player) {
        PlayerWelcomeState state = loadState(player.getUniqueId());
        boolean firstTime = state == null && !player.hasPlayedBefore();
        long now = System.currentTimeMillis();

        long firstJoinedAt = resolveFirstJoinedAt(player, state, now);
        int lastAnniversaryYear = state == null ? 0 : state.lastAnniversaryYear();

        upsertState(player, firstJoinedAt, lastAnniversaryYear);

        if (firstTime) {
            sendMessage(player, firstTimeMessage, firstJoinedAt, 0);
        } else {
            sendMessage(player, returningMessage, firstJoinedAt, 0);
        }

        Integer anniversaryYear = selectAnniversaryYear(
            anniversaryMessages.keySet(),
            completedAnniversaryYears(firstJoinedAt, now, ZoneId.systemDefault()),
            lastAnniversaryYear
        );
        if (anniversaryYear == null) {
            return;
        }

        List<String> message = anniversaryMessages.get(anniversaryYear);
        if (!hasUsableMessage(message)) {
            return;
        }

        broadcastMessage(player, message, firstJoinedAt, anniversaryYear);
        upsertState(player, firstJoinedAt, anniversaryYear);
    }

    private long resolveFirstJoinedAt(@NotNull OfflinePlayer player, @Nullable PlayerWelcomeState state, long now) {
        if (state != null) {
            return state.firstJoinedAt();
        }

        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed > 0L) {
            return firstPlayed;
        }

        return now;
    }

    private @Nullable PlayerWelcomeState loadState(@NotNull UUID playerId) {
        return api.database().querySingleMapped(
            "SELECT first_joined_at, last_anniversary_year FROM " + TABLE_NAME + " WHERE player_uuid = ?",
            ps -> ps.setString(1, playerId.toString().toLowerCase(Locale.ROOT)),
            rs -> new PlayerWelcomeState(
                rs.getLong("first_joined_at"),
                rs.getInt("last_anniversary_year")
            )
        );
    }

    private void upsertState(@NotNull Player player, long firstJoinedAt, int lastAnniversaryYear) {
        api.database().update(
            "INSERT INTO " + TABLE_NAME + " (player_uuid, first_joined_at, last_anniversary_year, last_name) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET " +
                "first_joined_at = excluded.first_joined_at, " +
                "last_anniversary_year = excluded.last_anniversary_year, " +
                "last_name = excluded.last_name",
            ps -> {
                ps.setString(1, player.getUniqueId().toString().toLowerCase(Locale.ROOT));
                ps.setLong(2, firstJoinedAt);
                ps.setInt(3, lastAnniversaryYear);
                ps.setString(4, player.getName());
            }
        );
    }

    private void sendMessage(@NotNull Player player, @Nullable List<String> messageLines, long firstJoinedAt, int years) {
        if (!hasUsableMessage(messageLines)) {
            return;
        }

        for (Component line : renderComponents(player, messageLines, firstJoinedAt, years)) {
            player.sendMessage(line);
        }
    }

    private void broadcastMessage(@NotNull Player player, @NotNull List<String> messageLines, long firstJoinedAt, int years) {
        List<Component> rendered = renderComponents(player, messageLines, firstJoinedAt, years);
        if (rendered.isEmpty()) {
            return;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            for (Component line : rendered) {
                online.sendMessage(line);
            }
        }
    }

    private @NotNull List<String> renderLines(@NotNull Player player, @NotNull List<String> messageLines, long firstJoinedAt, int years) {
        List<String> rendered = new ArrayList<>(messageLines.size());
        for (String line : messageLines) {
            String value = renderLine(player, line, firstJoinedAt, years);
            rendered.add(value == null ? "" : value);
        }
        return rendered;
    }

    private @NotNull List<Component> renderComponents(@NotNull Player player,
                                                      @NotNull List<String> messageLines,
                                                      long firstJoinedAt,
                                                      int years) {
        List<String> renderedLines = renderLines(player, messageLines, firstJoinedAt, years);
        List<Component> rendered = new ArrayList<>(renderedLines.size());
        for (String line : renderedLines) {
            rendered.add(renderComponentLine(line));
        }
        return List.copyOf(rendered);
    }

    private @Nullable String renderLine(@NotNull Player player, @Nullable String line, long firstJoinedAt, int years) {
        if (line == null) {
            return "";
        }

        LocalDate firstJoinDate = Instant.ofEpochMilli(firstJoinedAt).atZone(ZoneId.systemDefault()).toLocalDate();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("display_name", PlainTextComponentSerializer.plainText().serialize(player.displayName()));
        placeholders.put("world", player.getWorld().getName());
        placeholders.put("years", Integer.toString(years));
        placeholders.put("year", Integer.toString(years));
        placeholders.put("ordinal_year", ordinal(years));
        placeholders.put("first_join_date", DATE_FORMAT.format(firstJoinDate));
        placeholders.put("first_joined_at", Long.toString(firstJoinedAt));

        String rendered = PlaceholderUtil.apply(line, placeholders);
        return api.placeholders().apply(player, rendered);
    }

    private @NotNull List<String> readMessageList(@NotNull String path) {
        List<String> list = getConfigSection().getStringList(path);
        if (list.isEmpty()) {
            return List.of();
        }
        return List.copyOf(list);
    }

    private @NotNull Map<Integer, List<String>> readAnniversaryMessages() {
        ConfigSection section = getConfigSection().getSection("anniversaries", false);
        if (section == null) {
            return Map.of();
        }

        Map<Integer, List<String>> configured = new LinkedHashMap<>();
        section.getKeys(false).stream()
            .sorted(Comparator.comparingInt(PlayerWelcome::parsePositiveInt))
            .forEach(key -> {
                int year = parsePositiveInt(key);
                if (year <= 0) {
                    return;
                }

                List<String> lines = section.getStringList(key);
                if (!lines.isEmpty()) {
                    configured.put(year, List.copyOf(lines));
                }
            });

        return Map.copyOf(configured);
    }

    public @NotNull List<String> getConfiguredLines(@NotNull MessageKind kind, @Nullable Integer anniversaryYear) {
        List<String> list = getConfigSection().getStringList(messagePath(kind, anniversaryYear));
        if (list.isEmpty()) {
            return List.of();
        }
        return List.copyOf(list);
    }

    public void clearConfiguredLines(@NotNull MessageKind kind, @Nullable Integer anniversaryYear) {
        getConfigSection().remove(messagePath(kind, anniversaryYear));
        saveAndReloadConfigState();
    }

    public void addConfiguredLine(@NotNull MessageKind kind, @Nullable Integer anniversaryYear, @NotNull String line) {
        List<String> lines = new ArrayList<>(getConfiguredLines(kind, anniversaryYear));
        lines.add(line);
        saveConfiguredLines(kind, anniversaryYear, lines);
    }

    public void insertConfiguredLine(@NotNull MessageKind kind, @Nullable Integer anniversaryYear, int oneBasedLineNumber, @NotNull String line) {
        List<String> lines = new ArrayList<>(getConfiguredLines(kind, anniversaryYear));
        lines.add(normalizeInsertIndex(oneBasedLineNumber, lines.size()), line);
        saveConfiguredLines(kind, anniversaryYear, lines);
    }

    public void setConfiguredLine(@NotNull MessageKind kind, @Nullable Integer anniversaryYear, int oneBasedLineNumber, @NotNull String line) {
        List<String> lines = new ArrayList<>(getConfiguredLines(kind, anniversaryYear));
        lines.set(normalizeExistingLineIndex(oneBasedLineNumber, lines.size()), line);
        saveConfiguredLines(kind, anniversaryYear, lines);
    }

    public @NotNull String removeConfiguredLine(@NotNull MessageKind kind, @Nullable Integer anniversaryYear, int oneBasedLineNumber) {
        List<String> lines = new ArrayList<>(getConfiguredLines(kind, anniversaryYear));
        String removed = lines.remove(normalizeExistingLineIndex(oneBasedLineNumber, lines.size()));
        saveConfiguredLines(kind, anniversaryYear, lines);
        return removed;
    }

    public @NotNull List<Component> previewComponents(@NotNull Player player, @NotNull MessageKind kind, @Nullable Integer anniversaryYear) {
        List<String> lines = getConfiguredLines(kind, anniversaryYear);
        if (!hasUsableMessage(lines)) {
            return List.of();
        }

        int years = kind == MessageKind.ANNIVERSARY ? anniversaryYear == null ? 0 : anniversaryYear : 0;
        return renderComponents(player, lines, previewFirstJoinedAt(player), years);
    }

    static @NotNull String messagePath(@NotNull MessageKind kind, @Nullable Integer anniversaryYear) {
        if (kind == MessageKind.ANNIVERSARY) {
            if (anniversaryYear == null || anniversaryYear < 1) {
                throw new IllegalArgumentException("Anniversary welcome messages require a positive year.");
            }
            return kind.configRoot + "." + anniversaryYear;
        }

        return kind.configRoot;
    }

    static int normalizeInsertIndex(int oneBasedLineNumber, int currentSize) {
        if (oneBasedLineNumber < 1 || oneBasedLineNumber > currentSize + 1) {
            throw new IllegalArgumentException("Line number must be between 1 and " + (currentSize + 1) + ".");
        }
        return oneBasedLineNumber - 1;
    }

    static int normalizeExistingLineIndex(int oneBasedLineNumber, int currentSize) {
        if (oneBasedLineNumber < 1 || oneBasedLineNumber > currentSize) {
            throw new IllegalArgumentException("Line number must be between 1 and " + currentSize + ".");
        }
        return oneBasedLineNumber - 1;
    }

    static @NotNull String displayLine(@Nullable String line) {
        return line == null || line.isEmpty() ? "<blank>" : line;
    }

    static @NotNull Component renderComponentLine(@Nullable String line) {
        return TextUtil.colourise(line == null ? "" : line);
    }

    private void saveConfiguredLines(@NotNull MessageKind kind, @Nullable Integer anniversaryYear, @NotNull List<String> lines) {
        getConfigSection().set(messagePath(kind, anniversaryYear), new ArrayList<>(lines));
        saveAndReloadConfigState();
    }

    private void saveAndReloadConfigState() {
        getConfigSection().save();
        onReload();
    }

    static boolean hasUsableMessage(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }

        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                return true;
            }
        }

        return false;
    }

    static int completedAnniversaryYears(long firstJoinedAt, long now, @NotNull ZoneId zoneId) {
        if (firstJoinedAt <= 0L || now < firstJoinedAt) {
            return 0;
        }

        LocalDate first = Instant.ofEpochMilli(firstJoinedAt).atZone(zoneId).toLocalDate();
        LocalDate current = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate();
        return Math.max(0, Period.between(first, current).getYears());
    }

    static @Nullable Integer selectAnniversaryYear(@NotNull Iterable<Integer> configuredYears, int completedYears, int lastAnniversaryYear) {
        Integer best = null;
        for (Integer year : configuredYears) {
            if (year == null || year <= 0) {
                continue;
            }
            if (year > completedYears || year <= lastAnniversaryYear) {
                continue;
            }
            if (best == null || year > best) {
                best = year;
            }
        }
        return best;
    }

    static @NotNull String ordinal(int value) {
        int abs = Math.abs(value);
        int mod100 = abs % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return value + "th";
        }

        return switch (abs % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }

    private static int parsePositiveInt(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private long previewFirstJoinedAt(@NotNull Player player) {
        long firstPlayed = player.getFirstPlayed();
        return firstPlayed > 0L ? firstPlayed : System.currentTimeMillis();
    }

    private record PlayerWelcomeState(long firstJoinedAt, int lastAnniversaryYear) {
    }
}
