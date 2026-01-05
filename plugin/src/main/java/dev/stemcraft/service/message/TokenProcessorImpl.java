package dev.stemcraft.service.message;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.message.TokenProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class TokenProcessorImpl implements TokenProcessor {

    private final Map<Pattern, String> tokens = new HashMap<>();

    /**
     * Constructs a new TokenProcessor with the given configuration.
     */
    public TokenProcessorImpl(ConfigSection config) {
        ConfigSection sec = config.getSection("tokens");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                add(key, sec.getString(key));
            }
        }
    }

    /**
     * Adds a token binding.
     */
    @Override
    public void add(String placeholder, String value) {
        if (placeholder == null || placeholder.isEmpty()) {
            return;
        }
        String safeValue = value == null ? "" : value;
        tokens.put(Pattern.compile(Pattern.quote(":" + placeholder + ":")), safeValue);
    }

    /**
     * Removes a token binding.
     */
    @Override
    public void remove(String placeholder) {
        if (placeholder == null || placeholder.isEmpty()) {
            return;
        }
        Pattern p = Pattern.compile(Pattern.quote(":" + placeholder + ":"));
        tokens.remove(p);
    }

    /**
     * Removes multiple token bindings.
     */
    @Override
    public void remove(Iterable<String> placeholders) {
        if (placeholders == null) {
            return;
        }
        for (String placeholder : placeholders) {
            remove(placeholder);
        }
    }

    /**
     * Processes the input string, replacing all tokens with their bound values.
     */
    public String apply(String str) {
        if (str == null || str.isEmpty() || tokens.isEmpty()) {
            return str;
        }
        String out = str;
        for (var entry : tokens.entrySet()) {
            out = entry.getKey().matcher(out).replaceAll(entry.getValue());
        }
        return out;
    }
}
