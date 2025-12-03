package dev.stemcraft.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandConventionTest extends BaseSourceScanTest {

    // class Foo extends STEMCraftCommandImpl
    private static final Pattern EXTENDS_COMMAND_IMPL =
            Pattern.compile("class\\s+([A-Za-z0-9_]+)\\s+extends\\s+STEMCraftCommandImpl\\b");

    // api.registerCommand("naughty") or registerCommand("naughty")
    // requires a string literal, so it ignores method signatures like:
    //   public STEMCraftCommand registerCommand(String label) { ... }
    private static final Pattern REGISTER_COMMAND_LITERAL =
            Pattern.compile("\\bregisterCommand\\s*\\(\\s*\"");

    // setDescription("UPPER_SNAKE_CASE") or obj.setDescription("UPPER_SNAKE_CASE")
    private static final Pattern DESCRIPTION_CALL =
            Pattern.compile("(?:\\.|\\b)setDescription\\s*\\(\\s*\"([A-Z0-9_]+)\"\\s*\\)");

    // setPermission("stemcraft.command.*") or obj.setPermission("stemcraft.command.*")
    private static final Pattern PERMISSION_CALL =
            Pattern.compile("(?:\\.|\\b)setPermission\\s*\\(\\s*\"stemcraft\\.[^\"]+\"\\s*\\)");

    @Test
    void commandDefinitionsHaveDescriptionAndPermission() throws IOException {
        List<Path> javaFiles = listJavaFiles();
        List<String> violations = new ArrayList<>();

        for (Path file : javaFiles) {
            List<String> lines = readLines(file);
            String content = String.join("\n", lines);

            boolean hasCommandImpl = EXTENDS_COMMAND_IMPL.matcher(content).find();
            boolean hasRegisterCommandUsage = REGISTER_COMMAND_LITERAL.matcher(content).find();

            // not a command-related file
            if (!hasCommandImpl && !hasRegisterCommandUsage) {
                continue;
            }

            boolean hasDescription = DESCRIPTION_CALL.matcher(content).find();
            boolean hasPermission = PERMISSION_CALL.matcher(content).find();

            if (!hasDescription || !hasPermission) {
                StringBuilder sb = new StringBuilder();
                sb.append(file).append(":\n");
                if (!hasDescription) {
                    sb.append(" - Missing setDescription(\"UPPER_SNAKE_CASE\") in command definition\n");
                }
                if (!hasPermission) {
                    sb.append(" - Missing setPermission(\"stemcraft.*\") in command definition\n");
                }
                violations.add(sb.toString());
            }
        }

        assertTrue(
                violations.isEmpty(),
                "Command convention violations:\n" + String.join("\n", violations)
        );
    }
}