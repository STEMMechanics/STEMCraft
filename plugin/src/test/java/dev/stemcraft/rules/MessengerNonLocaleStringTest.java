package dev.stemcraft.rules;

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

class MessengerNonLocaleStringTest {

    // Only used for this non-locale warning test
    private static final Pattern RAW_CALL_PATTERN = Pattern.compile(
            "(?:error|info|success|warn|log)\\s*\\(([^;]*)\\);",
            Pattern.DOTALL
    );

    @Test
    void warnOnNonLocaleMessengerStrings() throws IOException {
        Set<String> rawMessages = findNonLocaleMessagesInSource();

        if (!rawMessages.isEmpty()) {
            StringBuilder warning = new StringBuilder();
            warning.append("Warning: the following messenger calls are using raw strings instead of locale keys:\n");
            for (String msg : rawMessages) {
                warning.append(" - ").append(msg).append('\n');
            }
            System.out.println(warning);
        }

        // Intentionally informational only
        assertTrue(true);
    }

    private Set<String> findNonLocaleMessagesInSource() throws IOException {
        Set<String> messages = new HashSet<>();

        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path srcMainJava = projectDir.resolve("src/main/java");

        System.out.println("Scanning for non-locale messenger strings under: " + srcMainJava.toAbsolutePath());

        if (!Files.isDirectory(srcMainJava)) {
            throw new IllegalStateException("Cannot find src/main/java from " + projectDir);
        }

        try (var stream = Files.walk(srcMainJava)) {
            stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> extractNonLocaleMessagesFromFile(path, messages));
        }

        System.out.println("Found " + messages.size() + " non-locale messenger strings");
        return messages;
    }

    private void extractNonLocaleMessagesFromFile(Path file, Set<String> messages) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                // ignore marker
                if (line.contains("@locale-ignore")) {
                    continue;
                }

                Matcher callMatcher = RAW_CALL_PATTERN.matcher(line);
                while (callMatcher.find()) {
                    String argsPart = callMatcher.group(1);

                    Matcher stringMatcher = Pattern.compile("\"([^\"]*)\"").matcher(argsPart);

                    // only consider the FIRST string literal in the arguments
                    if (stringMatcher.find()) {
                        String firstString = stringMatcher.group(1);

                        if (!firstString.matches("[A-Z0-9_]+")) {
                            int lineNumber = i + 1;
                            messages.add(
                                    file + ":" + lineNumber +
                                            " → \"" + firstString + "\"\n" +
                                            "    " + line.trim()
                            );
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }
}