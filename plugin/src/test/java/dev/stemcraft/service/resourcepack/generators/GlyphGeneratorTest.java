package dev.stemcraft.service.resourcepack.generators;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void bundledProfessionGlyphsHaveDefinitionsAndTextures() throws Exception {
        List<String> names = List.of("mining", "herbalism", "farming", "fishing", "cooking", "engineering", "melee", "ranged");
        String configPath = "/data-packs/stemcraft-ui/config.yml";
        String config;
        try (InputStream input = getClass().getResourceAsStream(configPath)) {
            assertNotNull(input, configPath);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(config.contains("generate_font_images:"));
        assertTrue(config.contains("textures_path: font"));
        assertTrue(config.contains("starting_char: \"\\uE100\""));
        for (String name : names) {
            String token = "profession_" + name;
            String texture = "/data-packs/stemcraft-ui/contents/stemcraft/textures/font/" + token + ".png";
            try (InputStream input = getClass().getResourceAsStream(texture)) {
                assertNotNull(input, texture);
            }
        }
        String gravestone = "/data-packs/stemcraft-ui/contents/stemcraft/textures/font/gravestone.png";
        try (InputStream input = getClass().getResourceAsStream(gravestone)) {
            assertNotNull(input, gravestone);
        }
    }
}
