package dev.stemcraft.api.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class SCTime extends STEMCraftUtil {
    public static String formatDuration(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m ");
        }
        if(seconds > 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    public static long parseDuration(String durationStr, boolean allowPermanent) {
        durationStr = durationStr.trim().toLowerCase();
        if (allowPermanent && (durationStr.equals("permanent") || durationStr.equals("perm"))) {
            return -1L;
        }

        long totalSeconds = 0;
        StringBuilder number = new StringBuilder();

        for (char c : durationStr.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.isEmpty()) {
                    throw new IllegalArgumentException("Invalid duration format: " + durationStr);
                }
                long value = Long.parseLong(number.toString());
                number.setLength(0);

                switch (c) {
                    case 'd' -> totalSeconds += value * 86400;
                    case 'h' -> totalSeconds += value * 3600;
                    case 'm' -> totalSeconds += value * 60;
                    case 's' -> totalSeconds += value;
                    default -> throw new IllegalArgumentException("Unknown time unit: " + c);
                }
            }
        }

        if (!number.isEmpty()) {
            throw new IllegalArgumentException("Invalid duration format: " + durationStr);
        }

        return totalSeconds;
    }

    public static long parseDuration(String durationStr) { return parseDuration(durationStr, false); }

    public static String toFriendlyTime(Instant instant) {
        if (instant == null) return "";

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime t = LocalDateTime.ofInstant(instant, zone);
        LocalDate today = LocalDate.now(zone);
        LocalDate date = t.toLocalDate();

        if (date.equals(today)) {
            // Today
            return "Today " + t.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"));
        }

        if (date.equals(today.minusDays(1))) {
            // Yesterday
            return "Yesterday " + t.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"));
        }

        long daysAgo = ChronoUnit.DAYS.between(date, today);
        if (daysAgo <= 7) {
            // Within a week
            return daysAgo + " days ago";
        }

        // Older: nice readable format
        return t.format(DateTimeFormatter.ofPattern("d MMM h:mm a"));
    }

    public static String toTimestamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static long DurationToRunAtMillis(Duration duration) {
        Instant targetTime = Instant.now().plus(duration);
        return targetTime.toEpochMilli();
    }

    public static boolean validDate(String date) {
        if (date == null || date.isBlank()) return false;
        String s = date.trim();
        try {
            LocalDate.parse(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

}
