package dev.stemcraft.service.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlyphCommandTest {
    @Test
    void searchReturnsLooseCaseInsensitiveMatches() {
        assertEquals(List.of(":roles/stemcraft:"), GlyphCommand.filterTokens(
                List.of(":roles/stemcraft:", ":items/compass:", ":roles/helper:"), "STEM"));
        assertEquals(List.of(":roles/stemcraft:", ":items/compass:", ":roles/helper:"), GlyphCommand.filterTokens(
                List.of(":roles/stemcraft:", ":items/compass:", ":roles/helper:"), ""));
    }

    @Test
    void glyphsAreGroupedIntoTwelvePerChatLine() {
        List<String> tokens = java.util.stream.IntStream.range(0, 25)
                .mapToObj(index -> ":glyph/" + index + ":")
                .toList();

        List<List<String>> lines = GlyphCommand.groupTokens(tokens, GlyphCommand.GLYPHS_PER_LINE);

        assertEquals(List.of(12, 12, 1), lines.stream().map(List::size).toList());
    }
}
