package dev.stemcraft.minigame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shared renderer for fastest-time leaderboard style holograms used by minigames.
 */
public final class TimedRecordLeaderboard {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private TimedRecordLeaderboard() {
    }

    public static @NotNull List<String> render(
        @NotNull String defaultTitle,
        @NotNull List<String> data,
        @NotNull List<Entry> entries
    ) {
        Options options = parseOptions(defaultTitle, data);
        List<String> lines = new ArrayList<>();
        lines.add(options.title());

        List<Entry> sorted = entries.stream()
            .filter(entry -> entry != null && entry.timeMillis() > 0L && entry.playerName() != null && !entry.playerName().isBlank())
            .sorted(Comparator
                .comparingLong(Entry::timeMillis)
                .thenComparing(Entry::playerName, String.CASE_INSENSITIVE_ORDER))
            .limit(options.limit())
            .toList();

        if (sorted.isEmpty()) {
            lines.add("<gray>No recorded times yet.</gray>");
            return lines;
        }

        int rank = 1;
        for (Entry entry : sorted) {
            String template = rank == 1 && options.firstLine() != null
                ? options.firstLine()
                : options.line();
            String formattedTime = formatMillis(entry.timeMillis());
            lines.add(template
                .replace("{rank}", Integer.toString(rank))
                .replace("{player}", entry.playerName())
                .replace("{time}", formattedTime)
                .replace("{value}", formattedTime));
            rank++;
        }

        return lines;
    }

    public static @NotNull String formatMillis(long durationMillis) {
        if (durationMillis <= 0L) {
            return "-";
        }

        long totalSeconds = durationMillis / 1000L;
        if (totalSeconds < 60L) {
            long millis = durationMillis % 1000L;
            return String.format(Locale.ROOT, "%d.%03ds", totalSeconds, millis);
        }

        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %dm %ds", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
    }

    private static @NotNull Options parseOptions(@NotNull String defaultTitle, @NotNull List<String> data) {
        String title = defaultTitle;
        int limit = DEFAULT_LIMIT;
        String line = "<yellow>{rank}.</yellow> <aqua>{player}</aqua> <dark_gray>-</dark_gray> <gold>{time}</gold>";
        String firstLine = "<gold>{rank}.</gold> <yellow>{player}</yellow> <dark_gray>-</dark_gray> <gold>{time}</gold>";

        for (String rawLine : data) {
            int separator = rawLine.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = rawLine.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = rawLine.substring(separator + 1).trim();
            if (value.isBlank()) {
                continue;
            }

            switch (key) {
                case "title" -> title = value;
                case "line" -> line = value;
                case "first", "first-line", "top-line" -> firstLine = value;
                case "limit" -> {
                    try {
                        limit = Math.max(1, Math.min(Integer.parseInt(value), MAX_LIMIT));
                    } catch (NumberFormatException ignored) {
                        // Ignore invalid custom limits and keep the default.
                    }
                }
                default -> {
                }
            }
        }

        return new Options(title, line, firstLine, limit);
    }

    public record Entry(@NotNull String playerName, long timeMillis) {
    }

    private record Options(
        @NotNull String title,
        @NotNull String line,
        @Nullable String firstLine,
        int limit
    ) {
    }
}
