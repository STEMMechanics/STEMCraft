package dev.stemcraft.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenSystemOutTest extends BaseSourceScanTest {

    private static final Pattern PATTERN = Pattern.compile("System\\s*\\.\\s*(out|err)\\s*\\.\\s*print");

    @Test
    void noSystemOutOrErrAllowed() throws IOException {
        List<Path> javaFiles = listJavaFiles();
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles) {
            List<String> lines = readLines(file);

            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                String trimmed = raw.trim();

                if (isCommentLine(trimmed)) continue;

                if (PATTERN.matcher(raw).find()) {
                    violations.add(file + ":" + (i + 1) + " → " + trimmed);
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "System.out/System.err usage detected:\n" + String.join("\n", violations));
    }
}