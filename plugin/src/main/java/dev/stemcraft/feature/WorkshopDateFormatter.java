/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.feature;

import dev.stemcraft.api.service.workshop.Workshop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats workshop date placeholders using a compact, administrator-friendly syntax. */
final class WorkshopDateFormatter {
    private static final Pattern DATE_PLACEHOLDER = Pattern.compile(
            "\\{(date|start_date|start|end_date|end|workshop_date|next_workshop_date|next_workshop_end_date|"
                    + "active_workshop_date|active_workshop_end_date):([^{}]*)}",
            Pattern.CASE_INSENSITIVE);

    private WorkshopDateFormatter() {
    }

    static @NotNull String apply(@NotNull String template, @NotNull Map<String, ZonedDateTime> dates) {
        Matcher matcher = DATE_PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String field = matcher.group(1).toLowerCase(Locale.ROOT);
            ZonedDateTime date = dates.get(field);
            String value = date == null ? "" : format(date, matcher.group(2));
            matcher.appendReplacement(result, Matcher.quoteReplacement(AnnouncementsFeature.escapeLiteral(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static @NotNull String format(@NotNull ZonedDateTime date, @NotNull String pattern) {
        return format(date, pattern, ZonedDateTime.now(date.getZone()));
    }

    static @NotNull String format(@NotNull ZonedDateTime date, @NotNull String pattern,
                                  @NotNull ZonedDateTime now) {
        String trimmed = pattern.trim();
        if (isRelativePattern(trimmed)) {
            String value = formatRelative(date, now);
            return isUppercaseRelative(trimmed) ? value.toUpperCase(Locale.ENGLISH) : value;
        }
        try {
            return DateTimeFormatter.ofPattern(toJavaPattern(trimmed), Locale.ENGLISH).format(date);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static @NotNull String formatRelative(@NotNull ZonedDateTime date, @NotNull ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(date.getZone()).toLocalDate();
        LocalDate target = date.toLocalDate();
        long daysUntil = ChronoUnit.DAYS.between(today, target);
        if (daysUntil == 0L) return "Today";
        if (daysUntil == 1L) return "Tomorrow";
        if (daysUntil >= 2L && daysUntil < 6L) {
            return DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH).format(date);
        }
        return DateTimeFormatter.ofPattern(toJavaPattern("ddd dd mmm"), Locale.ENGLISH).format(date);
    }

    private static boolean isRelativePattern(@NotNull String pattern) {
        String normalized = pattern.toLowerCase(Locale.ROOT);
        return normalized.equals("relative") || normalized.equals("reletive");
    }

    private static boolean isUppercaseRelative(@NotNull String pattern) {
        return pattern.equals("RELATIVE") || pattern.equals("RELETIVE");
    }

    static @NotNull Map<String, ZonedDateTime> dateValues(@Nullable Workshop workshop, @NotNull ZoneId timezone) {
        if (workshop == null) return Map.of();

        ZonedDateTime start = workshop.startAt().withZoneSameInstant(timezone);
        Map<String, ZonedDateTime> dates = new LinkedHashMap<>();
        for (String field : List.of(
                "date", "start_date", "start", "workshop_date",
                "next_workshop_date", "active_workshop_date")) {
            dates.put(field, start);
        }

        if (workshop.endAt() != null) {
            ZonedDateTime end = workshop.endAt().withZoneSameInstant(timezone);
            for (String field : List.of(
                    "end_date", "end", "next_workshop_end_date", "active_workshop_end_date")) {
                dates.put(field, end);
            }
        }
        return dates;
    }

    private static @NotNull String toJavaPattern(@NotNull String pattern) {
        StringBuilder converted = new StringBuilder(pattern.length());
        boolean quoted = false;

        for (int index = 0; index < pattern.length();) {
            char character = pattern.charAt(index);
            if (character == '\'') {
                converted.append(character);
                if (index + 1 < pattern.length() && pattern.charAt(index + 1) == '\'') {
                    converted.append(character);
                    index += 2;
                } else {
                    quoted = !quoted;
                    index++;
                }
                continue;
            }

            if (quoted || !Character.isLetter(character)) {
                converted.append(character);
                index++;
                continue;
            }

            int end = index + 1;
            while (end < pattern.length() && pattern.charAt(end) == character) end++;
            int count = end - index;
            String token = switch (Character.toLowerCase(character)) {
                case 'd' -> switch (count) {
                    case 1 -> "d";
                    case 2 -> "dd";
                    case 3 -> "EEE";
                    default -> "EEEE";
                };
                case 'm' -> switch (count) {
                    case 1 -> "M";
                    case 2 -> "MM";
                    case 3 -> "MMM";
                    default -> "MMMM";
                };
                case 'y' -> count == 2 ? "yy" : count >= 4 ? "yyyy" : "y";
                case 'h', 'H', 's', 'a' -> String.valueOf(character).repeat(count);
                default -> "'" + pattern.substring(index, end).replace("'", "''") + "'";
            };
            converted.append(token);
            index = end;
        }

        return converted.toString();
    }
}
