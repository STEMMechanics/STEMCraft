package dev.stemcraft.api.util;

import java.util.regex.Pattern;

public class PatternUtil {

    /**
     * Convert '*' wildcard to '.*' and escape everything else.
     */
    public static Pattern globToRegex(String glob) {
        StringBuilder out = new StringBuilder();
        out.append('^');
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                out.append(".*");
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
            }
        }
        out.append('$');
        return Pattern.compile(out.toString());
    }
}
