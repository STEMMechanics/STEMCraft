package dev.stemcraft.service.resourcepack.generators;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyphGeneratorTest {
    @Test
    void onlyReusesPersistedCodePointWhenTokenIsItsSoleOwner() {
        int codePoint = 0xE114;

        assertTrue(GlyphGenerator.canReusePersistedCodePoint(
            "mail",
            codePoint,
            Map.of(codePoint, Set.of("mail"))
        ));
        assertFalse(GlyphGenerator.canReusePersistedCodePoint(
            "mail",
            codePoint,
            Map.of(codePoint, Set.of("mail", "info_purple"))
        ));
    }
}
