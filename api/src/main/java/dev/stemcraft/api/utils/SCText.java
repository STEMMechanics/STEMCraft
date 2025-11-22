package dev.stemcraft.api.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SCText extends STEMCraftUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int DEFAULT_WIDTH = 6; // Default width for unknown characters
    private static Set<Character> WIDTH_2 = new HashSet<>(Set.of('i', '!', ';', ':', '\'', ',', '.', '|')); // 2 px
    private static Set<Character> WIDTH_3 = new HashSet<>(Set.of('l', '`'));       // 3 px
    private static Set<Character> WIDTH_4 = new HashSet<>(Set.of('t', '*', '(', ')', '[', ']', '{', '}', '"', 'I', ' '));      // 4 px
    private static Set<Character> WIDTH_5 = new HashSet<>(Set.of('f', 'k', '<', '>'));                 // 5 px
    private static Set<Character> WIDTH_6 = new HashSet<>(Set.of('a', 'b', 'c', 'd', 'e', 'g', 'h', 'j',
            'm', 'n', 'o', 'p', 'q', 'r', 's', 'u',
            'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
            'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y',
            'Z', '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', '#', '$', '%', '^', '&',
            '-', '_', '=', '+', '/', '?', '\\')); // 6 px
    private static Set<Character> WIDTH_7 = new HashSet<>(Set.of('~', '@')); // 7 px

    /**
     * Replaces case-insensitive placeholders (e.g. <code>%key%</code>) using
     * alternating key/value arguments.
     *
     * @param text   the input string containing placeholders
     * @param values alternating key/value pairs: "key1", "value1", "key2", "value2", ...
     * @return the processed string with placeholders replaced
     * @throws IllegalArgumentException if an odd number of values is provided
     */
    public static String placeholders(String text, String... values) {
        if (text == null || text.isEmpty() || values == null || values.length == 0) {
            return text;
        }

        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholder values must be in key/value pairs.");
        }

        String result = text;

        for (int i = 0; i < values.length; i += 2) {
            String key = values[i];
            String replacement = values[i + 1];

            if (key == null || key.isEmpty()) continue;
            if (replacement == null) replacement = "";

            // Transform replacement based on key casing
            if (isAllUpper(key)) {
                replacement = replacement.toUpperCase(Locale.ROOT);
            } else if (isTitleCase(key)) {
                replacement = toTitleCase(replacement);
            } // else lowercase → leave as-is

            String pattern = "(?i)\\{" + Pattern.quote(key) + "}";
            result = result.replaceAll(pattern, Matcher.quoteReplacement(replacement));
        }

        return result;
    }

    private static boolean isAllUpper(String s) {
        return s.equals(s.toUpperCase(Locale.ROOT));
    }

    private static boolean isTitleCase(String s) {
        return s.length() > 0 &&
                Character.isUpperCase(s.charAt(0)) &&
                s.substring(1).equals(s.substring(1).toLowerCase(Locale.ROOT));
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Converts a formatted string into an Adventure {@link Component}.
     *
     * @param input the formatted text (MiniMessage or legacy), may be null
     * @return a non-null {@link Component} representing the formatted text
     */
    public static Component colourise(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        // If it looks like MiniMessage (contains tags), treat it as MiniMessage
        if (input.indexOf('<') != -1 && input.indexOf('>') != -1) {
            return MM.deserialize(input);
        }

        // Otherwise, treat it as legacy & codes
        return LEGACY.deserialize(input);
    }

    /**
     * Removes all colour and formatting from a MiniMessage or legacy-formatted string, returning plain text.
     *
     * @param input the formatted text, may be null
     * @return plain unformatted text
    */
    public static String stripColour(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return stripColour(colourise(input));
    }

    public static String stripColour(Component component) {
        if (component == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Helper method to return the text length of a component
     *
     * @param text The text to calculate
     * @return The text length
     */
    public static int componentLength(Component text) {
        String t = LegacyComponentSerializer.legacySection().serialize(text);
        return t.replaceAll("§[0-9a-fk-or]", "").length();
    }

    /**
     * Calculate the pixel width of a string based on the default minecraft font
     *
     * @param text The text to calculate
     * @return The pixel width
     */
    public static int calculatePixelWidth(Component text) {
        return calculatePixelWidth(LegacyComponentSerializer.legacySection().serialize(text));
    }

    /**
     * Calculate the pixel width of a string based on the default minecraft font
     *
     * @param text The text to calculate
     * @return The pixel width
     */
    public static int calculatePixelWidth(String text) {
        // Remove Minecraft color and formatting codes (e.g., §b, §l, etc.)
        text = text.replaceAll("§[0-9a-fk-or]", "");

        int width = 0;
        for (char c : text.toCharArray()) {
            if (WIDTH_2.contains(c)) width += 2;
            else if (WIDTH_3.contains(c)) width += 3;
            else if (WIDTH_4.contains(c)) width += 4;
            else if (WIDTH_5.contains(c)) width += 5;
            else if (WIDTH_6.contains(c)) width += 6;
            else if (WIDTH_7.contains(c)) width += 7;
            else width += DEFAULT_WIDTH; // Fallback for unsupported characters
        }
        return width;
    }

    /**
     * Parse a string to a location
     *
     * @param s     The string to parse
     * @param world Optional world to use if no world found
     * @return A location
     */
    public static Location stringToLocation(String s, World world) {
        String[] data = s.split(",");

        // Check if the first item is a valid world name
        World parsedWorld = Bukkit.getWorld(data[0]);
        if (parsedWorld != null) {
            world = parsedWorld;
            data = Arrays.copyOfRange(data, 1, data.length); // Remove world name from data
        }

        // Use default world if no world provided
        if (world == null) {
            world = Bukkit.getWorlds().getFirst(); // Default to the first server world
        }

        // Parse coordinates
        double x = Double.parseDouble(data[0]);
        double y = Double.parseDouble(data[1]);
        double z = Double.parseDouble(data[2]);

        // Parse optional yaw and pitch
        float yaw = (data.length > 3) ? Float.parseFloat(data[3]) : 0.0f;
        float pitch = (data.length > 4) ? Float.parseFloat(data[4]) : 0.0f;

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Parse a string to a location
     *
     * @param s The string to parse
     * @return A location
     */
    public static Location stringToLocation(String s) {
        return stringToLocation(s, null);
    }

    /**
     * Convert a location to a formatted string
     * @param loc The location to convert
     * @param includeWorld Include the world name in the string
     * @param includeYawPitch Include the yaw and pitch in the string
     * @return The converted string
     */
    public static String locationToString(Location loc, boolean includeWorld, boolean includeYawPitch) {
        StringBuilder sb = new StringBuilder();

        if (includeWorld) {
            sb.append(loc.getWorld().getName()).append(",");
        }

        sb.append(String.format("%.2f,%.2f,%.2f", loc.getX(), loc.getY(), loc.getZ()));

        if (includeYawPitch) {
            sb.append(String.format(",%.2f,%.2f", loc.getYaw(), loc.getPitch()));
        }

        return sb.toString();
    }

    /**
     * Convert a camelCase or PascalCase string to snake_case
     *
     * @param string The string to convert
     * @return The converted string
     */
    public static String toSnakeCase(String string) {
        return string
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }


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

    public static String toString(Inventory inv) {
        ItemStack[] contents = inv.getContents();
        StringBuilder out = new StringBuilder();

        for (ItemStack item : contents) {
            if (item == null) continue;
            out.append(item.getType().name())
                    .append(" x")
                    .append(item.getAmount())
                    .append(", ");
        }

        if (out.isEmpty()) return "(empty)";
        return out.substring(0, out.length() - 2);
    }
}