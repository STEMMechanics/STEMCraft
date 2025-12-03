package dev.stemcraft.locale;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MessengerLocaleKeyScanTest {

    // Any "UPPER_SNAKE_CASE" string
    private static final Pattern UPPER_KEY_PATTERN = Pattern.compile(
            "\"([A-Z0-9]+(?:_[A-Z0-9]+)+)\""
    );

    @Test
    void allMessengerKeysExistInLocale() throws IOException {
        Set<String> localeKeys = LocaleUtils.loadLocaleKeys("/locales/en.yml");
        Set<String> usedKeys = findKeysInSource();

        StringBuilder failures = new StringBuilder();

        for (String key : usedKeys) {
            if (!localeKeys.contains(key)) {
                failures.append("Missing locale key in en.yaml: ").append(key).append('\n');
            }
        }

        assertTrue(
                failures.isEmpty(),
                "Some messenger keys are missing from locale file:\n" + failures
        );
    }

    @Test
    void unusedLocaleKeysMustBeRemoved() throws IOException {
        Set<String> localeKeys = LocaleUtils.loadLocaleKeys("/locales/en.yml");
        Set<String> usedKeys = findKeysInSource();

        Set<String> unusedKeys = new HashSet<>(localeKeys);
        unusedKeys.removeAll(usedKeys);

        if (!unusedKeys.isEmpty()) {
            StringBuilder failures = new StringBuilder();
            failures.append("The following locale keys exist but are not used anywhere:\n");
            for (String key : unusedKeys) {
                failures.append(" - ").append(key).append('\n');
            }

            // hard fail
            fail(failures.toString());
        }

        assertTrue(true);
    }

    private Set<String> findKeysInSource() throws IOException {
        Set<String> keys = new HashSet<>();

        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path srcMainJava = projectDir.resolve("src/main/java");

        System.out.println("Scanning Java files under: " + srcMainJava.toAbsolutePath());

        if (!Files.isDirectory(srcMainJava)) {
            throw new IllegalStateException("Cannot find src/main/java from " + projectDir);
        }

        try (var stream = Files.walk(srcMainJava)) {
            stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> extractKeysFromFile(path, keys));
        }

        System.out.println("Found " + keys.size() + " messenger keys in source: " + keys);
        return keys;
    }

    private void extractKeysFromFile(Path file, Set<String> keys) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            boolean inBlockComment = false;

            for (String line : lines) {
                String trimmed = line.trim();

                // Inside a /* ... */ block comment
                if (inBlockComment) {
                    if (trimmed.contains("*/")) {
                        inBlockComment = false;
                    }
                    continue;
                }

                // Start of a block comment
                if (trimmed.startsWith("/*")) {
                    if (!trimmed.contains("*/")) {
                        inBlockComment = true;
                    }
                    continue;
                }

                // Whole line comment
                if (trimmed.startsWith("//")) {
                    continue;
                }

                // Strip trailing block comment if present on the same line
                int blockIndex = line.indexOf("/*");
                String codePart = blockIndex >= 0 ? line.substring(0, blockIndex) : line;

                Matcher m = UPPER_KEY_PATTERN.matcher(codePart);
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }
}