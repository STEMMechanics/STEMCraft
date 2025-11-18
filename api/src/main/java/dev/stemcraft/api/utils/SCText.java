package dev.stemcraft.api.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SCText {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

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

            String pattern = "(?i)%" + Pattern.quote(key) + "%";

            result = result.replaceAll(
                    pattern,
                    Matcher.quoteReplacement(replacement)
            );
        }

        return result;
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

        // Step 1: interpret legacy & codes
        Component legacy = LEGACY.deserialize(input);

        // Step 2: convert legacy output to MiniMessage-compatible text
        String asMini = MM.serialize(legacy);

        // Step 3: apply MiniMessage parsing
        return MM.deserialize(asMini);
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
}