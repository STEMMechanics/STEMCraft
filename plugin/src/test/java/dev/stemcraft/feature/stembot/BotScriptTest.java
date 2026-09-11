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
}
