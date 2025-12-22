package dev.stemcraft.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenPluginGetLoggerTest extends BaseSourceScanTest {

    private static final Pattern LOGGER_PATTERN =
            Pattern.compile("\\bplugin\\s*\\.\\s*getLogger\\s*\\(");

    private static final Pattern CLASS_PATTERN =
            Pattern.compile("\\b(class|interface|enum)\\b");

    // Very loose method header matcher, good enough for this check
    private static final Pattern METHOD_PATTERN =
            Pattern.compile("(public|protected|private|static|final|synchronized|abstract|default)\\s+.*\\(");

    @Test
    void noPluginGetLoggerUnlessIgnored() throws IOException {
        List<Path> javaFiles = listJavaFiles();
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles) {
            List<String> lines = readLines(file);

            boolean ignoreClass = false;
            boolean ignoreNextMember = false;
            boolean insideIgnoredMethod = false;
            int braceDepth = 0;
            int ignoredMethodStartDepth = -1;

            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                String trimmed = raw.trim();

                // Track annotation
                if (trimmed.startsWith("@IgnoreForbiddenPluginGetLoggerCheck")) {
                    ignoreNextMember = true;
                    continue;
                }

                // Class declaration
                if (!ignoreClass && CLASS_PATTERN.matcher(trimmed).find()) {
                    if (ignoreNextMember) {
                        ignoreClass = true;
                        ignoreNextMember = false;
                    }
                }

                // Existing ignored class: no need to look further
                if (ignoreClass) {
                    // Still track braces to keep state sane if you want, but we can safely skip checks
                    braceDepth += countChar(raw, '{') - countChar(raw, '}');
                    if (braceDepth < 0) braceDepth = 0;
                    continue;
                }

                int beforeLineDepth = braceDepth;
                braceDepth += countChar(raw, '{') - countChar(raw, '}');
                if (braceDepth < 0) braceDepth = 0;

                // End of ignored method scope
                if (insideIgnoredMethod && braceDepth < ignoredMethodStartDepth) {
                    insideIgnoredMethod = false;
                    ignoredMethodStartDepth = -1;
                }

                // Method declaration (single line with opening brace)
                if (METHOD_PATTERN.matcher(trimmed).find() && trimmed.contains("{")) {
                    boolean thisIgnored = ignoreNextMember;
                    ignoreNextMember = false;

                    if (thisIgnored) {
                        insideIgnoredMethod = true;
                        ignoredMethodStartDepth = beforeLineDepth;
                    }
                }

                if (isCommentLine(trimmed)) continue;

                if (LOGGER_PATTERN.matcher(raw).find()) {
                    if (!insideIgnoredMethod) {
                        violations.add(file + ":" + (i + 1) + " → " + trimmed);
                    }
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "Usage of plugin.getLogger() detected (without @IgnoreForbiddenPluginGetLoggerCheck):\n"
                        + String.join("\n", violations)
        );
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }
}