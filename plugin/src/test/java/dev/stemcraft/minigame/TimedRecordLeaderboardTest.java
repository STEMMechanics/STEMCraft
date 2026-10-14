package dev.stemcraft.minigame;

import dev.stemcraft.api.util.FontUtil;
import dev.stemcraft.api.util.TextUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimedRecordLeaderboardTest {
    @Test
    void namesAndTimesAlignAcrossSingleAndDoubleDigitRanks() {
        List<TimedRecordLeaderboard.Entry> entries = new ArrayList<>();
        entries.add(new TimedRecordLeaderboard.Entry("Project_HSI", 950));
        entries.add(new TimedRecordLeaderboard.Entry("Remi", 65_000));
        for (int i = 0; i < 8; i++) entries.add(new TimedRecordLeaderboard.Entry("iplayer_" + i, 70_000 + i * 1000));
        List<String> lines = TimedRecordLeaderboard.render("<gold>Records</gold>", List.of(), entries);
        assertEquals(11, lines.size());
        int smallest = lines.stream().skip(1).mapToInt(TimedRecordLeaderboardTest::width).min().orElseThrow();
        int largest = lines.stream().skip(1).mapToInt(TimedRecordLeaderboardTest::width).max().orElseThrow();
        assertTrue(largest - smallest < 4);
        List<String> prefixes = lines.stream().skip(1).map(TimedRecordLeaderboardTest::plain)
                .map(line -> line.substring(0, line.indexOf(line.stripLeading().split("\\s+")[1])))
                .toList();
        int shortestPrefix = prefixes.stream().mapToInt(FontUtil::calculatePixelWidth).min().orElseThrow();
        int longestPrefix = prefixes.stream().mapToInt(FontUtil::calculatePixelWidth).max().orElseThrow();
        assertTrue(longestPrefix - shortestPrefix < 4);
        assertTrue(plain(lines.get(1)).endsWith("0.950s"));
        assertTrue(plain(lines.get(2)).endsWith("1m 5s"));
        assertFalse(lines.toString().contains("{gap}"));
    }

    @Test
    void equalLengthNamesUseTheirPixelWidthsRatherThanCharacterCounts() {
        String narrow = "iiiiiiiiiiiiiiii";
        String wide = "WWWWWWWWWWWWWWWW";
        List<String> lines = TimedRecordLeaderboard.render("Records", List.of(), List.of(
                new TimedRecordLeaderboard.Entry(narrow, 950), new TimedRecordLeaderboard.Entry(wide, 950)));
        String narrowRow = plain(lines.get(1));
        String wideRow = plain(lines.get(2));
        int narrowGap = narrowRow.indexOf("0.950s") - narrowRow.indexOf(narrow) - narrow.length();
        int wideGap = wideRow.indexOf("0.950s") - wideRow.indexOf(wide) - wide.length();
        assertEquals(16, narrowGap - wideGap);
        assertEquals(width(lines.get(1)), width(lines.get(2)));
    }

    @Test
    void customRowsAndEmptyBoardKeepTheirContent() {
        assertEquals(List.of("Records", "TOP Project_HSI=0.950s", "2 Remi=1m 5s"),
                TimedRecordLeaderboard.render("Records", List.of("first=TOP {player}={time}", "line={rank} {player}={time}"), List.of(
                        new TimedRecordLeaderboard.Entry("Project_HSI", 950), new TimedRecordLeaderboard.Entry("Remi", 65_000))));
        assertEquals(List.of("Records", "<gray>No recorded times yet.</gray>"),
                TimedRecordLeaderboard.render("Records", List.of(), List.of()));
    }

    private static String plain(String text) {
        return TextUtil.plain(TextUtil.colourise(text));
    }

    private static int width(String text) {
        return FontUtil.calculatePixelWidth(TextUtil.colourise(text));
    }
}
