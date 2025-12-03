package dev.stemcraft.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenChatColorTest extends BaseSourceScanTest {

    private static final Pattern CHATCOLOR = Pattern.compile("\\bChatColor\\s*\\.");
    private static final Pattern SECTION = Pattern.compile("§[0-9A-FK-ORa-fk-or]");

    @Test
    void noDirectChatColorOrSectionCodes() throws IOException {
        List<Path> javaFiles = listJavaFiles();
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles) {
            List<String> lines = readLines(file);

            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                String trimmed = raw.trim();

                if (isCommentLine(trimmed)) continue;

                boolean badChatColor = CHATCOLOR.matcher(raw).find();
                boolean badSection = SECTION.matcher(raw).find();

                if (badChatColor || badSection) {
                    violations.add(file + ":" + (i + 1) + " → " + trimmed);
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Direct ChatColor/§ codes detected:\n" + String.join("\n", violations));
    }
}