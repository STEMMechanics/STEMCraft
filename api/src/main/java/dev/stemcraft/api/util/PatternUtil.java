/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

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
