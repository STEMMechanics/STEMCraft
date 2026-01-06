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

package dev.stemcraft.api.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for time-related operations.
 */
public class TimeUtil {
    public enum FormatStyle {
        SHORT,      // e.g., "1d 2h 3m 4s"
        LONG,       // e.g., "1 day 2 hours 3 mins 4 secs"
        VERBOSE     // e.g., "1 day 2 hours 3 minutes 4 seconds"
    }

    /**
     * Formats a duration given in total seconds into a human-readable string.
     *
     * @param totalSeconds The total duration in seconds.
     * @param format       The desired format style.
     * @return A formatted duration string.
     */
    public static String formatDuration(long totalSeconds, FormatStyle format) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        String dayUnit = "day";
        String hourUnit = "hour";
        String minuteUnit = (format == FormatStyle.VERBOSE) ? "minute" : "min";
        String secondUnit = (format == FormatStyle.VERBOSE) ? "second" : "sec";

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            String unit = (format != FormatStyle.SHORT) ? (days == 1 ? " " + dayUnit + " " : " " + dayUnit + "s ") : "d ";
            sb.append(days).append(unit);
        }
        if (hours > 0 || days > 0) {
            String unit = (format != FormatStyle.SHORT) ? (hours == 1 ? " " + hourUnit + " " : " " + hourUnit + "s ") : "h ";
            sb.append(hours).append(unit);
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            String unit = (format != FormatStyle.SHORT) ? (minutes == 1 ? " " + minuteUnit + " " : " " + minuteUnit + "s ") : "m ";
            sb.append(minutes).append(unit);
        }
        if(seconds > 0) {
            String unit = (format != FormatStyle.SHORT) ? (seconds == 1 ? " " + secondUnit + " " : " " + secondUnit + "s ") : "s ";
            sb.append(seconds).append(unit);
        }

        return sb.toString().trim();
    }

    public static String formatDuration(long totalSeconds) { return formatDuration(totalSeconds, FormatStyle.SHORT); }
    public static String formatShortDuration(long totalSeconds) { return formatDuration(totalSeconds, FormatStyle.SHORT); }
    public static String formatLongDuration(long totalSeconds) { return formatDuration(totalSeconds, FormatStyle.LONG); }
    public static String formatFriendlyDuration(long totalSeconds) { return formatDuration(totalSeconds, FormatStyle.VERBOSE); }

    /**
     * Parses a duration string into total seconds.
     * Supports formats like "1d2h3m4s" and "permanent" if allowed.
     *
     * @param durationStr    The duration string to parse.
     * @param allowPermanent Whether to allow "permanent" as a valid input.
     * @return The total duration in seconds, or -1 for permanent.
     * @throws IllegalArgumentException if the format is invalid.
     */
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

    /**
     * Converts an Instant to a friendly time string.
     *
     * @param instant The instant to convert.
     * @return A friendly time string.
     */
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

    /**
     * Converts epoch milliseconds to a timestamp string.
     *
     * @param epochMillis The epoch milliseconds.
     * @return A timestamp string in "yyyy-MM-dd HH:mm:ss" format.
     */
    public static String toTimestamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Calculates the epoch milliseconds for a future time after a given duration.
     *
     * @param duration The duration to add to the current time.
     * @return The epoch milliseconds of the future time.
     */
    public static long DurationToRunAtMillis(Duration duration) {
        Instant targetTime = Instant.now().plus(duration);
        return targetTime.toEpochMilli();
    }

    /**
     * Validates if the given string is a valid date in ISO_LOCAL_DATE format (yyyy-MM-dd).
     *
     * @param date The date string to validate.
     * @return True if valid, false otherwise.
     */
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
