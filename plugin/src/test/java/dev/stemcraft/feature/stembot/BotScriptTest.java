package dev.stemcraft.feature.stembot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotScriptTest {
    private dev.stemcraft.api.config.ConfigSection config(String text) throws Exception {
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString(text);
        return new dev.stemcraft.config.ConfigSectionImpl(null,yaml);
    }

    @Test
    void worldActionsUseFirstVisitThenAlwaysAndKeepCommandsSeparate() throws Exception {
        var config=config("""
            actions:
              help: ['say:Help']
              first: ['say:Welcome']
              arrival: ['say:Hello again']
            worlds:
              hub:
                command-action: help
                first-time-action: first
                always-action: arrival
                always-action-spawn: [20.5, 65, 20.5, 90, 0]
              floorshuffle:
                always-action: arrival
            """);
        var script=BotScript.read(config);
        assertEquals("help",script.commandAction("HUB"));
        assertEquals("first",script.arrivalAction("hub",false));
        assertEquals("arrival",script.arrivalAction("hub",true));
        assertEquals("arrival",script.arrivalAction("floorshuffle",false));
        assertEquals("arrival",script.arrivalAction("floorshuffle",true));
        assertNull(script.commandAction("floorshuffle"));
        assertNull(script.arrivalAction("unknown",false));
        assertEquals(new BotScript.SpawnPoint(20.5,65,20.5,90,0),script.alwaysActionSpawn("hub"));
        assertNull(script.alwaysActionSpawn("floorshuffle"));
    }

    @Test
    void legacyMappingsMigrateWithoutOverwritingNewSettingsOrBlankOptOuts() throws Exception {
        var config=config("""
            actions:
              old: ['say:Old']
              first: ['say:First']
              custom: ['say:Custom']
            command:
              world:
                hub: old
                survival: old
            first-time:
              world:
                hub: first
                survival: first
            worlds:
              hub:
                command-action: custom
                first-time-action: ''
            """);
        var before=BotScript.read(config);
        assertEquals("custom",before.commandAction("hub"));
        assertNull(before.firstTimeAction("hub"));
        assertTrue(BotScript.migrateWorldRoutes(config));
        assertFalse(config.contains("command.world"));
        assertFalse(config.contains("first-time.world"));
        assertEquals("old",config.getString("worlds.survival.command-action"));
        assertEquals(before,BotScript.read(config));
        assertFalse(BotScript.migrateWorldRoutes(config));
    }

    @Test
    void rejectsUnknownAlwaysActionsAndInvalidFixedSpawns() throws Exception {
        var config=config("""
            actions:
              greeting: ['say:Hello']
            worlds:
              hub:
                always-action: missing
            """);
        assertThrows(IllegalArgumentException.class,()->BotScript.read(config));
        config.set("worlds.hub.always-action","greeting");
        for(Object invalid:java.util.List.of(
                java.util.List.of(1,2),java.util.List.of(1,"oops",3),
                java.util.List.of(1,Double.NaN,3),java.util.List.of(1,2,3,0,100))) {
            config.set("worlds.hub.always-action-spawn",invalid);
            assertThrows(IllegalArgumentException.class,()->BotScript.read(config));
        }
        config.set("worlds.hub.always-action-spawn",java.util.List.of(1,65,3));
        assertEquals(new BotScript.SpawnPoint(1,65,3,0,0),BotScript.read(config).alwaysActionSpawn("hub"));
        config.remove("worlds.hub.always-action");
        assertThrows(IllegalArgumentException.class,()->BotScript.read(config));
    }

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
            assertEquals(3, script.scanning().size());
            assertEquals(10, script.scanningCooldownSeconds());
            yaml.set("messages.scanning-cooldown-seconds", 60);
            assertEquals(60, BotScript.read(new dev.stemcraft.config.ConfigSectionImpl(null, yaml)).scanningCooldownSeconds());
            yaml.set("messages.scanning", java.util.List.of("Custom scan message"));
            assertEquals(java.util.List.of("Custom scan message"),
                BotScript.read(new dev.stemcraft.config.ConfigSectionImpl(null, yaml)).scanning());
            yaml.set("messages.scanning", java.util.List.of());
            assertTrue(BotScript.read(new dev.stemcraft.config.ConfigSectionImpl(null, yaml)).scanning().isEmpty());
            yaml.set("messages.scanning", null);
            assertEquals(script.scanning(), BotScript.read(new dev.stemcraft.config.ConfigSectionImpl(null, yaml)).scanning());
        }
    }
}
