package dev.stemcraft.feature.stembot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotScriptTest {
    @Test
    void parsesTextWithoutLosingColons() {
        var instruction=BotScript.parseInstruction("talk:Use /warp foo:bar when ready.");
        assertEquals(BotScript.Op.TALK,instruction.op());
        assertEquals("Use /warp foo:bar when ready.",instruction.text());
    }

    @Test
    void parsesWalkWithOptionalOneShotSpeed() {
        var normal=BotScript.parseInstruction("walk:-864 85 245");
        assertEquals(BotScript.Op.WALK,normal.op());
        assertEquals(-864,normal.x());
        assertEquals(0,normal.number());

        var fast=BotScript.parseInstruction("walk:-864 85 245 1.4");
        assertEquals(1.4,fast.number());
    }

    @Test
    void listenSupportsAlternativesWildcardsAndFallback() {
        var routes=BotScript.parseListen(
            "yes|tour -> hub2, *creative* -> creative, * -> confused");

        assertTrue(routes.get(0).matches("YES"));
        assertTrue(routes.get(0).matches("tour"));
        assertTrue(routes.get(1).matches("can you show me creative please"));
        assertTrue(routes.get(2).matches("anything else"));
        assertEquals("hub2",routes.get(0).target());
    }
    @Test
    void awaitRequiresBoundedTimeoutAndTwoDestinations() {
        var step = BotScript.parseInstruction("await:stemcraft:coordbar 60 -> ready, skipped");
        assertEquals(BotScript.Op.AWAIT, step.op());
        assertEquals(1200, step.ticks());
        assertEquals("stemcraft:coordbar", step.text());
        assertThrows(IllegalArgumentException.class, () -> BotScript.parseInstruction("await:coordbar 60 -> ready, skipped"));
        assertThrows(IllegalArgumentException.class, () -> BotScript.parseInstruction("await:stemcraft:test 301 -> ready, skipped"));
        assertThrows(IllegalArgumentException.class, () -> BotScript.parseInstruction("await:stemcraft:test 0 -> ready, skipped"));
        assertThrows(IllegalArgumentException.class, () -> BotScript.parseInstruction("await:stemcraft:test 10 -> ready"));
    }
    @Test
    void bundledScriptLoadsWithAllPracticeAndHelpTargets() throws Exception {
        try (var stream = getClass().getResourceAsStream("/stembot.yml")) {
            assertNotNull(stream);
            var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            var script = BotScript.read(new dev.stemcraft.config.ConfigSectionImpl(null, yaml));
            assertTrue(script.actions().containsKey("creative-merge"));
            assertTrue(script.actions().containsKey("practice-quest"));
        }
    }
}
