package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.service.playerstats.PlayerStatsRecord;
import dev.stemcraft.api.service.playerstats.PlayerStatValue;
import dev.stemcraft.api.util.FontUtil;
import dev.stemcraft.api.util.TextUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HologramLeaderboardTest {
    @Test
    void defaultStatRowsAlignNamesAndValuesDespiteDifferentNameLengths() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        when(api.playerStats().getDefinition("wins")).thenReturn(new PlayerStatDefinition("wins", "Wins", ""));
        when(api.playerStats().top("wins", 10, "all")).thenReturn(List.of(
                record("Project_HSI", 15), record("Remi", 5), record("iiiiiiii", 3.25)));
        var service = new HologramServiceImpl(mock(STEMCraft.class), api);
        List<String> lines = render(service, List.of());
        int maximum = lines.stream().skip(1).mapToInt(HologramLeaderboardTest::width).max().orElseThrow();
        int minimum = lines.stream().skip(1).mapToInt(HologramLeaderboardTest::width).min().orElseThrow();
        assertTrue(maximum - minimum < 4, "Centered rows need the same rendered width to align both edges");
        assertTrue(maximum < 120, "Short leaderboards should not reserve a wide empty column");
        assertEquals("Wins", TextUtil.plain(TextUtil.colourise(lines.getFirst())));
        assertTrue(TextUtil.plain(TextUtil.colourise(lines.get(1))).startsWith("1. Project_HSI"));
        assertTrue(TextUtil.plain(TextUtil.colourise(lines.get(2))).endsWith("5"));
        assertFalse(lines.toString().contains("{gap}"));
    }

    @Test
    void customTemplatesKeepTheirFormattingUnlessTheyRequestAGap() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        when(api.playerStats().getDefinition("wins")).thenReturn(new PlayerStatDefinition("wins", "Wins", ""));
        when(api.playerStats().top("wins", 10, "all")).thenReturn(List.of(record("Project_HSI", 15), record("Remi", 5)));
        var service = new HologramServiceImpl(mock(STEMCraft.class), api);
        assertEquals(List.of("<gold>Wins</gold>", "TOP Project_HSI=15", "QA:2 Remi=5"),
                render(service, List.of("first=TOP {player}={value}", "line=QA:{rank} {player}={value}")));
        List<String> aligned = render(service, List.of("first={rank}. {player}{gap}{value}", "line={rank}. {player}{gap}{value}"));
        assertTrue(Math.abs(width(aligned.get(1)) - width(aligned.get(2))) < 4);
    }

    private static PlayerStatsRecord record(String name, double value) {
        return new PlayerStatsRecord(UUID.randomUUID(), name, null, null,
                List.of(new PlayerStatValue("wins", "Wins", "", value, null)));
    }

    private static int width(String line) {
        return FontUtil.calculatePixelWidth(TextUtil.colourise(line));
    }

    @SuppressWarnings("unchecked")
    private static List<String> render(HologramServiceImpl service, List<String> data) throws Exception {
        var method = HologramServiceImpl.class.getDeclaredMethod("renderStatLeaderboard", String.class, List.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, "wins", data);
    }
}
