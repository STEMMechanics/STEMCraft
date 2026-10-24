package dev.stemcraft.hologram;

import dev.stemcraft.api.util.FontUtil;
import dev.stemcraft.api.util.TextUtil;

import java.util.List;

/** Aligns centered nametag rows with spaces measured in the default Minecraft font. */
public final class LeaderboardLayout {
    private static final int SPACE_WIDTH = 4;

    private LeaderboardLayout() { }

    /**
     * {rank-gap} aligns names after the rank; {gap} pushes scores/times to the right.
     * Custom rows without these markers keep their original formatting. A space has
     * four pixels of advance, so the resulting widths can differ by at most three.
     */
    public static List<String> align(List<String> rows) {
        List<String> ranked = fillGap(rows, "{rank-gap}", true, SPACE_WIDTH);
        return fillGap(ranked, "{gap}", false, SPACE_WIDTH * 2);
    }

    private static List<String> fillGap(List<String> rows, String marker, boolean prefixOnly, int minimumGap) {
        int target = rows.stream().filter(row -> row.contains(marker))
                .mapToInt(row -> width(prefixOnly ? row.substring(0, row.indexOf(marker)) : row.replace(marker, "")))
                .max().orElse(0) + minimumGap;
        return rows.stream().map(row -> {
            int index = row.indexOf(marker);
            if (index < 0) return row;
            int measured = width(prefixOnly ? row.substring(0, index) : row.replace(marker, ""));
            int spaces = (target - measured + SPACE_WIDTH - 1) / SPACE_WIDTH;
            return row.replace(marker, " ".repeat(spaces));
        }).toList();
    }

    private static int width(String text) {
        return FontUtil.calculatePixelWidth(TextUtil.colourise(text));
    }
}
