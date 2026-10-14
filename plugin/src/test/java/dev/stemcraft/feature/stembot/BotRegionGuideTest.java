package dev.stemcraft.feature.stembot;

import dev.stemcraft.config.ConfigSectionImpl;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BotRegionGuideTest {
    private static final BotRegionGuide.Bounds AREA = new BotRegionGuide.Bounds(0, 0, 7999, 7999);

    @ParameterizedTest
    @CsvSource({
        "4000.5,100.5,north,4000.5,-0.5,101",
        "7900.5,4000.5,east,8000.5,4000.5,100",
        "4000.5,7900.5,south,4000.5,8000.5,100",
        "100.5,4000.5,west,-0.5,4000.5,101"})
    void choosesTheClosestExitForEachEdge(double x, double z, String direction,
                                        double targetX, double targetZ, double distance) {
        var route = AREA.nearestExit(x, z, (tx, tz) -> true);
        assertNotNull(route);
        assertEquals(direction, route.direction());
        assertEquals(targetX, route.x());
        assertEquals(targetZ, route.z());
        assertEquals(distance, route.distance());
        assertFalse(AREA.contains(route.x(), route.z()));
    }

    @Test
    void equalDistancesConsistentlyChooseNorth() {
        assertEquals("north", AREA.nearestExit(4000, 4000, (x, z) -> true).direction());
    }

    @Test
    void exactBoundsPreserveReversedAndNegativeBlockCorners() {
        var bounds = new BotRegionGuide.Bounds(8000, 8000, -1, -1);
        assertEquals(-1, bounds.minX());
        assertEquals(8000, bounds.maxX());
        assertTrue(bounds.contains(8000.9, 4000));
        assertFalse(bounds.contains(8001, 4000));
        assertTrue(bounds.contains(-0.9, 4000));
        assertFalse(bounds.contains(-1.01, 4000));
        assertEquals(8001.5, bounds.nearestExit(8000.5, 4000, (x, z) -> true).x());
    }

    @Test
    void expandsToWholeChunksOnlyWhenRequested() throws Exception {
        var config = configuration("'8000,8000;-1,-1'");
        var exact = BotScript.read(config).regionGuide().regions().get("test-area").bounds();
        assertEquals(-1, exact.minX());
        assertEquals(8000, exact.maxX());

        config.set("regions.test-area.align-to-chunks", true);
        var bounds = BotScript.read(config).regionGuide().regions().get("test-area").bounds();
        assertEquals(-16, bounds.minX());
        assertEquals(-16, bounds.minZ());
        assertEquals(8015, bounds.maxX());
        assertEquals(8015, bounds.maxZ());
        assertTrue(bounds.contains(8015.9, 4000));
        assertFalse(bounds.contains(8016, 4000));
        assertEquals(8016.5, bounds.nearestExit(8015.5, 4000, (x, z) -> true).x());
        assertEquals(-16.5, bounds.nearestExit(-15.5, 4000, (x, z) -> true).x());
    }

    @Test
    void choosesAnotherExitWhenTheWorldBorderBlocksTheClosestOne() {
        var bounds = new BotRegionGuide.Bounds(0, 0, 999, 999);
        assertEquals("north", bounds.nearestExit(100, 500, (x, z) -> x >= 0).direction());
        assertNull(bounds.nearestExit(100, 500, (x, z) -> false));
        assertNull(bounds.nearestExit(-10, 500, (x, z) -> true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0, 0, 7999, 7999]", "'0,0;7999,7999'"})
    void readingARegionDoesNotAddTopicsActionsOrChangeDialogue(String raw) throws Exception {
        var script = BotScript.read(configuration(raw));
        assertEquals(AREA, script.regionGuide().regions().get("test-area").bounds());
        assertTrue(script.helpTopics().isEmpty());
        assertEquals(java.util.Set.of("start"), script.actions().keySet());
        assertEquals("custom intro", script.actions().get("start").getFirst().text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "''"})
    void emptyBoundsRemainUnmappedWithoutInventingCoordinates(String raw) throws Exception {
        var script = BotScript.read(configuration(raw));
        var guide = script.regionGuide();
        assertNull(guide.regions().get("test-area").bounds());
        var advice = guide.advise("test-area", new Location(mock(World.class), 100, 64, 100));
        assertTrue(advice.message().contains("not have this region mapped"));
        assertNull(advice.route());
        assertTrue(script.helpTopics().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0, 0, 8000]", "[0, 0, 8000, 8000, 1]", "[0, nope, 8000, 8000]",
        "[0, 0.5, 8000, 8000]", "[0, .nan, 8000, 8000]", "[0, .inf, 8000, 8000]",
        "[0, 0, 30000001, 8000]", "'0,0;8000'", "{x: 0}"})
    void rejectsInvalidCoordinatesWithTheConfigPath(String raw) {
        var failure = assertThrows(IllegalArgumentException.class, () -> BotScript.read(configuration(raw)));
        assertTrue(failure.getMessage().contains("regions.test-area.bounds"));
    }

    @Test
    void topicsExistOnlyWhenExplicitlyConfiguredAndCanBeRemoved() throws Exception {
        var config = configuration("[0, 0, 7999, 7999]");
        config.set("help-topics.wilderness", "start");
        assertEquals("start", BotScript.read(config).helpTopicAction("wilderness"));
        config.set("help-topics.wilderness", "");
        assertNull(BotScript.read(config).helpTopicAction("wilderness"));
        config.remove("help-topics");
        assertTrue(BotScript.read(config).helpTopics().isEmpty());
    }

    @Test
    void adviceReportsDistanceAndNegativeCoordinatesAndExplainsOutsideOrBlocked() throws Exception {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        var guide = BotScript.read(configuration("[0, 0, 7999, 7999]")).regionGuide();

        var inside = guide.advise("test-area", new Location(world, 4000.5, 64, 100.5));
        assertTrue(inside.message().contains("north about 101 blocks"));
        assertTrue(inside.message().contains("X 4000, Z -1"));
        assertNotNull(inside.route());

        var outside = guide.advise("test-area", new Location(world, -0.1, 64, 4000));
        assertTrue(outside.message().contains("already outside"));
        assertNull(outside.route());

        when(border.isInside(any(Location.class))).thenReturn(false);
        var blocked = guide.advise("test-area", new Location(world, 4000, 64, 4000));
        assertTrue(blocked.message().contains("world border blocks"));
        assertNull(blocked.route());
    }

    @Test
    void suppliedRegionSelectsIndependentBoundsAndDialogue() throws Exception {
        var config = configuration("[0, 0, 7999, 7999]");
        config.set("regions.training-room.bounds", java.util.List.of(0, 0, 9, 9));
        config.set("regions.training-room.messages.inside", "Leave the room to the {direction}: {x},{z} ({distance} blocks).");
        config.set("regions.training-room.messages.outside", "You left the room.");
        var guide = BotScript.read(config).regionGuide();
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);

        var room = guide.advise("training-room", new Location(world, 8.5, 64, 5));
        assertEquals("Leave the room to the east: 10,5 (2 blocks).", room.message());
        assertEquals(10.5, room.route().x());
        assertEquals("You left the room.", guide.advise("training-room", new Location(world, 10, 64, 5)).message());
        assertNotNull(guide.advise("test-area", new Location(world, 10, 64, 5)).route());
    }

    @Test
    void rejectsReferencesToUnknownRegionsBeforeStartingASession() throws Exception {
        var config = configuration("[0, 0, 7999, 7999]");
        config.set("actions.start", java.util.List.of("exit-region:missing"));
        var failure = assertThrows(IllegalArgumentException.class, () -> BotScript.read(config));
        assertTrue(failure.getMessage().contains("actions.start references unknown region 'missing'"));
        config.set("actions.start", java.util.List.of("exit-region:test-area"));
        assertEquals(BotScript.Op.EXIT_REGION, BotScript.read(config).actions().get("start").getFirst().op());
        assertThrows(IllegalArgumentException.class, () -> BotScript.parseInstruction("exit-region"));
        assertThrows(IllegalArgumentException.class, () -> BotScript.parseInstruction("wilderness"));
    }

    private static ConfigSectionImpl configuration(String bounds) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            actions:
              start: ['talk:custom intro', 'listen:* -> start']
            regions:
              test-area:
                bounds: %s
            """.formatted(bounds));
        return new ConfigSectionImpl(null, yaml);
    }
}
