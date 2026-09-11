package dev.stemcraft.service.command;

import java.util.ArrayList;
import java.util.List;

/** Converts Bukkit's whitespace-split command arguments into logical quoted arguments. */
final class CommandArgumentTokenizer {
    private CommandArgumentTokenizer() { }

    static List<String> tokenize(List<String> input) {
        List<String> output = new ArrayList<>();
        StringBuilder current = null;
        boolean quoted = false;
        boolean escaped = false;

        for (String token : input) {
            if (current == null) current = new StringBuilder();
            else if (quoted) current.append(' ');

            for (int index = 0; index < token.length(); index++) {
                char character = token.charAt(index);
                if (escaped) {
                    current.append(character);
                    escaped = false;
                } else if (character == '\\' && quoted) {
                    escaped = true;
                } else if (character == '"') {
                    quoted = !quoted;
                } else {
                    current.append(character);
                }
            }
            if (escaped) {
                current.append('\\');
                escaped = false;
            }
            if (!quoted) {
                output.add(current.toString());
                current = null;
            }
        }
        if (current != null) output.add(current.toString());
        return output;
    }
}
