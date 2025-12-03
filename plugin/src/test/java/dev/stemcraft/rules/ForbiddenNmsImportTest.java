package dev.stemcraft.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenNmsImportTest extends BaseSourceScanTest {

    private static final Pattern NMS_IMPORT = Pattern.compile("^import\\s+net\\.minecraft\\.server\\.", Pattern.MULTILINE);
    private static final Pattern CRAFT_IMPORT = Pattern.compile("^import\\s+org\\.bukkit\\.craftbukkit\\.", Pattern.MULTILINE);

    @Test
    void noNmsOrCraftBukkitImports() throws IOException {
        List<Path> javaFiles = listJavaFiles();
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles) {
            List<String> lines = readLines(file);
            String content = String.join("\n", lines);

            if (NMS_IMPORT.matcher(content).find() || CRAFT_IMPORT.matcher(content).find()) {
                violations.add(file.toString());
            }
        }

        assertTrue(violations.isEmpty(),
                "NMS/CraftBukkit imports detected:\n" + String.join("\n", violations));
    }
}