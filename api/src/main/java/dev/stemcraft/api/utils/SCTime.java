package dev.stemcraft.api.utils;

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
        sb.append(seconds).append("s");

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
}
