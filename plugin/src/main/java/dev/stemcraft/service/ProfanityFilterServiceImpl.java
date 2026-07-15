package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.command.CommandService;
import dev.stemcraft.api.service.profanity.ProfanityFilterResult;
import dev.stemcraft.api.service.profanity.ProfanityFilterService;
import dev.stemcraft.api.service.profanity.ProfanitySeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfanityFilterServiceImpl extends BaseService implements ProfanityFilterService {
    private static final String CONFIG_NAME = "profanity-filter.yml";
    private static final List<String> LIST_KEYS = List.of("allow", "false-positives", "mild", "moderate", "high", "extreme");
    private static final ProfanitySeverity DEFAULT_MINIMUM = ProfanitySeverity.MILD;
    private static final List<String> DEFAULT_SUFFIXES = List.of("s", "es", "ed", "er", "ers", "ing", "ings");
    private static final Pattern LETTER_OR_NUMBER = Pattern.compile("[\\p{L}\\p{N}]");

    private ConfigFile configFile;
    private boolean enabled;
    private char maskCharacter;
    private String separatorPattern;
    private String suffixPattern;
    private final Map<Character, Character> substitutions = new HashMap<>();
    private final Set<String> allowList = new LinkedHashSet<>();
    private final Set<String> falsePositives = new LinkedHashSet<>();
    private List<CompiledWord> compiledWords = List.of();

    public ProfanityFilterServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        ensureConfigFile();
        reloadSettings();
        registerCommands();
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public @NotNull ProfanityFilterResult check(@Nullable String text) {
        return check(text, null);
    }

    @Override
    public @NotNull ProfanityFilterResult check(@Nullable String text, @Nullable ProfanitySeverity minimumSeverity) {
        String original = text == null ? "" : text;
        if (!enabled || original.isBlank()) {
            return ProfanityFilterResult.clean(original);
        }

        String working = normalizeForMatching(original);
        BitSet maskedCharacters = new BitSet(original.length());
        List<String> matches = new ArrayList<>();
        ProfanitySeverity highestSeverity = null;

        for (CompiledWord compiled : compiledWords) {
            if (!compiled.severity().meetsOrExceeds(minimumSeverity)) {
                continue;
            }

            Matcher matcher = compiled.pattern().matcher(working);
            while (matcher.find()) {
                String token = extractToken(working, matcher.start(), matcher.end());
                if (token.isBlank() || allowList.contains(token) || falsePositives.contains(token)) {
                    continue;
                }

                highestSeverity = highestSeverity == null || compiled.severity().ordinal() > highestSeverity.ordinal()
                    ? compiled.severity()
                    : highestSeverity;
                matches.add(compiled.word());
                maskedCharacters.set(matcher.start(), Math.min(original.length(), matcher.end()));
            }
        }

        if (matches.isEmpty() || highestSeverity == null) {
            return ProfanityFilterResult.clean(original);
        }

        return new ProfanityFilterResult(
            true,
            original,
            mask(original, maskedCharacters),
            highestSeverity.score(),
            highestSeverity,
            matches
        );
    }

    void reloadSettings() {
        if (configFile == null) {
            throw new IllegalStateException("Profanity filter config file has not been loaded.");
        }

        enabled = configFile.getBoolean("enabled", true);
        maskCharacter = resolveMaskCharacter(configFile.getString("mask_character", "*"));
        separatorPattern = configFile.getBoolean("matching.allow_separated_letters", true)
            ? "(?:[\\p{Z}\\p{Punct}_-]*)"
            : "";
        suffixPattern = buildSuffixPattern(configFile.getStringList("matching.allowed_suffixes"));

        substitutions.clear();
        substitutions.putAll(loadSubstitutions(configFile));

        allowList.clear();
        allowList.addAll(normalizeWords(configFile.getStringList("allow")));

        falsePositives.clear();
        falsePositives.addAll(normalizeWords(configFile.getStringList("false_positives")));

        compiledWords = compileWords(configFile);
    }

    private void registerCommands() {
        api.commands().create("profanity")
            .description("Manage the profanity filter.")
            .usage("/profanity <status|check|search|list|add|remove|set|reload>")
            .permission("stemcraft.command.profanity")
            .tabCompletion("status")
            .tabCompletion("reload")
            .tabCompletion("check", "{text}")
            .tabCompletion("search", "{word}")
            .tabCompletion("list", "allow")
            .tabCompletion("list", "false-positives")
            .tabCompletion("list", "mild")
            .tabCompletion("list", "moderate")
            .tabCompletion("list", "high")
            .tabCompletion("list", "extreme")
            .tabCompletion("add", "allow", "{word}")
            .tabCompletion("add", "false-positives", "{word}")
            .tabCompletion("add", "mild", "{word}")
            .tabCompletion("add", "moderate", "{word}")
            .tabCompletion("add", "high", "{word}")
            .tabCompletion("add", "extreme", "{word}")
            .tabCompletion("remove", "allow", "{word}")
            .tabCompletion("remove", "false-positives", "{word}")
            .tabCompletion("remove", "mild", "{word}")
            .tabCompletion("remove", "moderate", "{word}")
            .tabCompletion("remove", "high", "{word}")
            .tabCompletion("remove", "extreme", "{word}")
            .tabCompletion("set", "enabled", "true")
            .tabCompletion("set", "enabled", "false")
            .tabCompletion("set", "mask", "*")
            .executor((unused, cmd, ctx) -> {
                String action = Objects.requireNonNullElse(ctx.getArgLower(0), "status");
                switch (action) {
                    case "status" -> sendStatus(ctx);
                    case "check" -> runCheck(ctx);
                    case "search" -> runSearch(ctx);
                    case "list" -> runList(ctx);
                    case "add" -> runAdd(ctx);
                    case "remove" -> runRemove(ctx);
                    case "set" -> runSet(ctx);
                    case "reload" -> runReload(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(plugin);
    }

    private void ensureConfigFile() {
        File file = new File(plugin.getDataFolder(), CONFIG_NAME);
        if (!file.exists()) {
            plugin.saveResource(CONFIG_NAME, false);
        }

        this.configFile = api.config().load(CONFIG_NAME);
        if (this.configFile == null) {
            throw new IllegalStateException("Could not load " + CONFIG_NAME);
        }
        configureDefaults(this.configFile);
    }

    private void sendStatus(dev.stemcraft.api.command.CommandContext ctx) {
        ctx.info("Profanity filter:");
        ctx.info(" - Enabled: " + enabled);
        ctx.info(" - Mask: " + maskCharacter);
        ctx.info(" - Allow separated letters: " + configFile.getBoolean("matching.allow_separated_letters", true));
        ctx.info(" - Allow words: " + configFile.getStringList("allow").size());
        ctx.info(" - False positives: " + configFile.getStringList("false_positives").size());
        ctx.info(" - Mild block words: " + configFile.getStringList("block.mild").size());
        ctx.info(" - Moderate block words: " + configFile.getStringList("block.moderate").size());
        ctx.info(" - High block words: " + configFile.getStringList("block.high").size());
        ctx.info(" - Extreme block words: " + configFile.getStringList("block.extreme").size());
    }

    private void runCheck(dev.stemcraft.api.command.CommandContext ctx) {
        String text = ctx.getArgsAsString(1, "").trim();
        if (text.isBlank()) {
            ctx.returnError("Usage: /profanity check <text>");
        }

        ProfanityFilterResult result = check(text);
        ctx.info("Input: " + result.originalText());
        ctx.info("Offensive: " + result.offensive());
        ctx.info("Cleaned: " + result.cleanedText());
        ctx.info("Score: " + result.score());
        ctx.info("Severity: " + (result.severity() == null ? "none" : result.severity().name().toLowerCase(Locale.ROOT)));
        ctx.info("Matches: " + (result.matchedWords().isEmpty() ? "<none>" : String.join(", ", result.matchedWords())));
    }

    private void runSearch(dev.stemcraft.api.command.CommandContext ctx) {
        String query = normalizeWord(ctx.getArgsAsString(1, ""));
        if (query.isBlank()) {
            ctx.returnError("Usage: /profanity search <word>");
        }

        List<String> matches = new ArrayList<>();
        for (String key : LIST_KEYS) {
            for (String word : listValues(key)) {
                if (word.contains(query)) {
                    matches.add(key + ": " + word);
                }
            }
        }

        if (matches.isEmpty()) {
            ctx.returnInfo("No profanity entries matched '" + query + "'.");
        }

        ctx.info("Matches for '" + query + "':");
        for (String match : matches) {
            ctx.info(" - " + match);
        }
    }

    private void runList(dev.stemcraft.api.command.CommandContext ctx) {
        String key = normalizeListKey(ctx.getArgLower(1));
        if (key == null) {
            ctx.returnError("Usage: /profanity list <allow|false-positives|mild|moderate|high|extreme>");
            return;
        }

        List<String> values = listValues(key);
        ctx.info(key + " (" + values.size() + "):");
        if (values.isEmpty()) {
            ctx.info(" - <empty>");
            return;
        }
        for (String value : values) {
            ctx.info(" - " + value);
        }
    }

    private void runAdd(dev.stemcraft.api.command.CommandContext ctx) {
        String key = normalizeListKey(ctx.getArgLower(1));
        String word = normalizeWord(ctx.getArgsAsString(2, ""));
        if (key == null || word.isBlank()) {
            ctx.returnError("Usage: /profanity add <allow|false-positives|mild|moderate|high|extreme> <word>");
            return;
        }

        if (mutateList(key, word, true)) {
            ctx.returnSuccess("Added '" + word + "' to " + key + ".");
            return;
        }
        ctx.returnInfo("'" + word + "' already exists in " + key + ".");
    }

    private void runRemove(dev.stemcraft.api.command.CommandContext ctx) {
        String key = normalizeListKey(ctx.getArgLower(1));
        String word = normalizeWord(ctx.getArgsAsString(2, ""));
        if (key == null || word.isBlank()) {
            ctx.returnError("Usage: /profanity remove <allow|false-positives|mild|moderate|high|extreme> <word>");
            return;
        }

        if (mutateList(key, word, false)) {
            ctx.returnSuccess("Removed '" + word + "' from " + key + ".");
            return;
        }
        ctx.returnInfo("'" + word + "' was not present in " + key + ".");
    }

    private void runSet(dev.stemcraft.api.command.CommandContext ctx) {
        String setting = Objects.requireNonNullElse(ctx.getArgLower(1), "");
        String value = Objects.requireNonNullElse(ctx.getArg(2, ""), "");
        switch (setting) {
            case "enabled" -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    ctx.returnError("Usage: /profanity set enabled <true|false>");
                }
                configFile.set("enabled", Boolean.parseBoolean(value));
                configFile.save();
                reloadSettings();
                ctx.returnSuccess("Profanity filter enabled set to " + enabled + ".");
            }
            case "mask" -> {
                if (value.isBlank()) {
                    ctx.returnError("Usage: /profanity set mask <character>");
                }
                configFile.set("mask_character", String.valueOf(value.charAt(0)));
                configFile.save();
                reloadSettings();
                ctx.returnSuccess("Profanity filter mask set to '" + maskCharacter + "'.");
            }
            default -> ctx.returnError("Usage: /profanity set <enabled|mask> <value>");
        }
    }

    private void runReload(dev.stemcraft.api.command.CommandContext ctx) {
        if (configFile == null || !configFile.reload()) {
            ctx.returnError("Failed to reload " + CONFIG_NAME + ".");
        }
        configureDefaults(configFile);
        reloadSettings();
        ctx.returnSuccess("Reloaded " + CONFIG_NAME + ".");
    }

    private void configureDefaults(ConfigFile config) {
        boolean changed = false;
        changed |= setDefault(config, "enabled", true);
        changed |= setDefault(config, "mask_character", "*");
        changed |= setDefault(config, "matching.allow_separated_letters", true);
        changed |= setDefault(config, "matching.allowed_suffixes", DEFAULT_SUFFIXES);
        changed |= setDefault(config, "allow", List.of("assignment", "classroom", "aerospace", "workspace"));
        changed |= setDefault(config, "false_positives", List.of("assessment", "assistant", "passionate"));

        ConfigSectionView substitutionSection = config.getSection("substitutions");
        if (substitutionSection.getKeys(false).isEmpty()) {
            config.set("substitutions.@", "a");
            config.set("substitutions.$", "s");
            config.set("substitutions.3", "e");
            config.set("substitutions.0", "o");
            config.set("substitutions.1", "i");
            config.set("substitutions.!", "i");
            changed = true;
        }

        changed |= setDefault(config, "block.mild", List.of("damn", "crap", "bloody"));
        changed |= setDefault(config, "block.moderate", List.of("asshole", "bastard", "bitch", "shit", "twat", "wanker"));
        changed |= setDefault(config, "block.high", List.of("fuck", "motherfucker"));
        changed |= setDefault(config, "block.extreme", List.of("cunt"));

        if (changed) {
            config.save();
        }
    }

    private boolean setDefault(ConfigFile config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    List<String> listValues(@NotNull String key) {
        String path = pathForListKey(key);
        List<String> values = new ArrayList<>(configFile.getStringList(path));
        values.replaceAll(this::normalizeWord);
        values.removeIf(String::isBlank);
        values.sort(String::compareTo);
        return values;
    }

    boolean mutateList(@NotNull String key, @NotNull String word, boolean add) {
        String path = pathForListKey(key);
        String normalized = normalizeWord(word);
        if (normalized.isBlank()) {
            return false;
        }

        LinkedHashSet<String> values = new LinkedHashSet<>(listValues(key));
        boolean changed = add ? values.add(normalized) : values.remove(normalized);
        if (!changed) {
            return false;
        }

        configFile.set(path, new ArrayList<>(values));
        configFile.save();
        reloadSettings();
        return true;
    }

    private @Nullable String normalizeListKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "allowlist", "allow-list" -> "allow";
            case "false", "falsepositive", "falsepositives", "false-positive", "false-positives" -> "false-positives";
            case "mild", "moderate", "high", "extreme" -> raw.trim().toLowerCase(Locale.ROOT);
            default -> null;
        };
    }

    private String pathForListKey(@NotNull String key) {
        return switch (key) {
            case "allow" -> "allow";
            case "false-positives" -> "false_positives";
            case "mild" -> "block.mild";
            case "moderate" -> "block.moderate";
            case "high" -> "block.high";
            case "extreme" -> "block.extreme";
            default -> throw new IllegalArgumentException("Unknown profanity list key: " + key);
        };
    }

    private Map<Character, Character> loadSubstitutions(ConfigSectionView config) {
        Map<Character, Character> loaded = new LinkedHashMap<>();
        ConfigSectionView section = config.getSection("substitutions");
        for (String key : section.getKeys(false)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = section.getString(key, "");
            if (value.isBlank()) {
                continue;
            }
            loaded.put(Character.toLowerCase(key.charAt(0)), Character.toLowerCase(value.charAt(0)));
        }
        return loaded;
    }

    private List<CompiledWord> compileWords(ConfigSectionView config) {
        List<CompiledWord> words = new ArrayList<>();
        compileSeverityWords(words, config, "block.mild", ProfanitySeverity.MILD);
        compileSeverityWords(words, config, "block.moderate", ProfanitySeverity.MODERATE);
        compileSeverityWords(words, config, "block.high", ProfanitySeverity.HIGH);
        compileSeverityWords(words, config, "block.extreme", ProfanitySeverity.EXTREME);
        return List.copyOf(words);
    }

    private void compileSeverityWords(List<CompiledWord> target, ConfigSectionView config, String path, ProfanitySeverity severity) {
        for (String word : config.getStringList(path)) {
            String normalized = normalizeWord(word);
            if (normalized.isBlank()) {
                continue;
            }
            target.add(new CompiledWord(normalized, severity, compilePattern(normalized)));
        }
    }

    private Pattern compilePattern(String word) {
        StringBuilder pattern = new StringBuilder();
        pattern.append("(?<![\\p{L}\\p{N}])");
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            pattern.append(Pattern.quote(String.valueOf(ch))).append('+');
            if (i + 1 < word.length()) {
                pattern.append(separatorPattern);
            }
        }
        pattern.append(suffixPattern);
        pattern.append("(?![\\p{L}\\p{N}])");
        return Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private String buildSuffixPattern(List<String> suffixes) {
        List<String> normalized = new ArrayList<>();
        for (String suffix : suffixes.isEmpty() ? DEFAULT_SUFFIXES : suffixes) {
            String value = normalizeWord(suffix);
            if (!value.isBlank()) {
                normalized.add(Pattern.quote(value));
            }
        }
        if (normalized.isEmpty()) {
            return "";
        }
        return "(?:" + String.join("|", normalized) + ")?";
    }

    private Set<String> normalizeWords(Collection<String> words) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String word : words) {
            String value = normalizeWord(word);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeForMatching(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = Character.toLowerCase(text.charAt(i));
            char substituted = substitutions.getOrDefault(ch, ch);
            normalized.append(substituted);
        }
        return normalized.toString();
    }

    private String normalizeWord(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = Character.toLowerCase(text.charAt(i));
            char substituted = substitutions.getOrDefault(ch, ch);
            if (Character.isLetterOrDigit(substituted)) {
                normalized.append(substituted);
            }
        }
        return normalized.toString();
    }

    private String extractToken(String text, int start, int end) {
        int tokenStart = start;
        while (tokenStart > 0 && isLetterOrNumber(text.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        int tokenEnd = end;
        while (tokenEnd < text.length() && isLetterOrNumber(text.charAt(tokenEnd))) {
            tokenEnd++;
        }
        return normalizeWord(text.substring(tokenStart, tokenEnd));
    }

    private boolean isLetterOrNumber(char ch) {
        return LETTER_OR_NUMBER.matcher(String.valueOf(ch)).matches();
    }

    private String mask(String original, BitSet maskedCharacters) {
        StringBuilder cleaned = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++) {
            char ch = original.charAt(i);
            if (!maskedCharacters.get(i) || Character.isWhitespace(ch)) {
                cleaned.append(ch);
                continue;
            }
            cleaned.append(maskCharacter);
        }
        return cleaned.toString();
    }

    private char resolveMaskCharacter(String configured) {
        if (configured == null || configured.isBlank()) {
            return '*';
        }
        return configured.charAt(0);
    }

    private record CompiledWord(@NotNull String word, @NotNull ProfanitySeverity severity, @NotNull Pattern pattern) {
        private CompiledWord {
            Objects.requireNonNull(word);
            Objects.requireNonNull(severity);
            Objects.requireNonNull(pattern);
        }
    }
}
