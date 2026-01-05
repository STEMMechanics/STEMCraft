package dev.stemcraft.api.util;

public final class ByteFormat {

    /**
     * Convert a byte value into a human-readable string
     * (Bytes, KB, MB, GB, TB)
     *
     * @param bytes the number of bytes
     * @return formatted string such as "512 B", "1.24 KB", "3.8 MB"
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        final String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unitIndex = -1;

        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }

        return String.format("%.2f %s", value, units[unitIndex]);
    }

    public static long toBytes(String formatted) {
        if (formatted == null || formatted.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        String s = formatted.trim().replaceAll("\\s+", ""); // remove all spaces

        // If only digits, assume bytes
        if (s.matches("\\d+")) {
            return Long.parseLong(s);
        }

        // Split numeric + unit (e.g. "14MB" -> "14", "MB")
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
            i++;
        }

        if (i == 0 || i == s.length()) {
            throw new IllegalArgumentException("Invalid formatted byte string: " + formatted);
        }

        double value = Double.parseDouble(s.substring(0, i));
        String unit = s.substring(i).toUpperCase();

        return switch (unit) {
            case "B"  -> (long) value;
            case "KB" -> (long) (value * 1024);
            case "MB" -> (long) (value * 1024 * 1024);
            case "GB" -> (long) (value * 1024 * 1024 * 1024);
            case "TB" -> (long) (value * 1024L * 1024L * 1024L * 1024L);
            case "PB" -> (long) (value * 1024L * 1024L * 1024L * 1024L * 1024L);
            default -> throw new IllegalArgumentException("Unknown byte unit: " + unit);
        };
    }
}