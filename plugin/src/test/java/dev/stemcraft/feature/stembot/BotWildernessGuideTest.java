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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BotWildernessGuideTest {
    private static final BotWildernessGuide.Bounds OLD = new BotWildernessGuide.Bounds(0, 0, 7999, 7999);

    @ParameterizedTest
    @CsvSource({
        "4000.5,100.5,north,4000.5,-0.5,101",
        "7900.5,4000.5,east,8000.5,4000.5,100",
        "4000.5,7900.5,south,4000.5,8000.5,100",
        "100.5,4000.5,west,-0.5,4000.5,101"})
    void choosesTheClosestNewChunkForEachEdge(double x, double z, String direction,
                                             double targetX, double targetZ, double distance) {
        var route = OLD.nearestExit(x, z, (tx, tz) -> true);
        assertNotNull(route);
        assertEquals(direction, route.direction());
        assertEquals(targetX, route.x());
        assertEquals(targetZ, route.z());
        assertEquals(distance, route.distance());
        assertFalse(OLD.contains(route.x(), route.z()));
    }

    @Test
    void equalDistancesConsistentlyChooseNorth() {
        assertEquals("north", OLD.nearestExit(4000, 4000, (x, z) -> true).direction());
    }

    @Test
    void countsWholeEdgeChunksAndHandlesNegativeCorners() {
        var bounds = new BotWildernessGuide.Bounds(8000, 8000, -1, -1);
        assertEquals(-16, bounds.minX());
        assertEquals(-16, bounds.minZ());
        assertEquals(8015, bounds.maxX());
        assertEquals(8015, bounds.maxZ());
        assertTrue(bounds.contains(8015.9, 4000));
        assertFalse(bounds.contains(8016, 4000));
        assertTrue(bounds.contains(-15.9, 4000));
        assertFalse(bounds.contains(-16.01, 4000));
        assertEquals(8016.5, bounds.nearestExit(8015.5, 4000, (x, z) -> true).x());
        assertEquals(-16.5, bounds.nearestExit(-15.5, 4000, (x, z) -> true).x());
    }

    @Test
    void choosesAnotherExitWhenTheWorldBorderBlocksTheClosestOne() {
        var bounds = new BotWildernessGuide.Bounds(0, 0, 999, 999);
        assertEquals("north", bounds.nearestExit(100, 500, (x, z) -> x >= 0).direction());
        assertNull(bounds.nearestExit(100, 500, (x, z) -> false));
        assertNull(bounds.nearestExit(-10, 500, (x, z) -> true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0, 0, 7999, 7999]", "'0,0;7999,7999'"})
    void readsBothBoundaryFormatsAndAddsTopicsWithoutChangingCustomDialogue(String raw) throws Exception {
        var script = script(raw);
        assertEquals(OLD, script.wildernessGuide().worlds().get("survival").bounds());
        assertEquals("survival-wilderness", script.helpTopicAction("wilderness"));
        assertEquals("survival-wilderness", script.helpTopicAction("newterrain"));
        assertEquals("custom intro", script.actions().get("start").getFirst().text());
        assertEquals(BotScript.Op.WILDERNESS, script.actions().get("survival-wilderness").getFirst().op());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "''"})
    void missingBoundaryDoesNotInventCoordinatesOrEnableTopics(String raw) throws Exception {
        var script = script(raw);
        assertTrue(script.wildernessGuide().worlds().isEmpty());
        assertNull(script.helpTopicAction("wilderness"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0, 0, 8000]", "[0, 0, 8000, 8000, 1]", "[0, nope, 8000, 8000]",
        "[0, 0.5, 8000, 8000]", "[0, .nan, 8000, 8000]", "[0, .inf, 8000, 8000]",
        "[0, 0, 30000001, 8000]", "'0,0;8000'", "{x: 0}"})
    void rejectsInvalidCoordinatesWithTheConfigPath(String raw) {
        var failure = assertThrows(IllegalArgumentException.class, () -> script(raw));
        assertTrue(failure.getMessage().contains("worlds.survival.wilderness.old-generation-bounds"));
    }

    @Test
    void preservesExplicitCustomAndDisabledHelpTopics() throws Exception {
        var config = configuration("[0, 0, 7999, 7999]");
        config.set("help-topics.wilderness", "start");
        config.set("help-topics.newterrain", "");
        var script = BotScript.read(config);
        assertEquals("start", script.helpTopicAction("wilderness"));
        assertNull(script.helpTopicAction("newterrain"));
    }

    @Test
    void repliesWithDistanceAndNegativeTargetCoordinatesAndPointsOnlyWhenInside() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getName()).thenReturn("Survival");
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        var guide = new BotWildernessGuide(Map.of("survival", new BotWildernessGuide.Area(OLD, "26.2", "26.3")),
            BotWildernessGuide.Messages.defaults());

        var inside = guide.advise(new Location(world, 4000.5, 64, 100.5));
        assertTrue(inside.message().contains("north about 101 blocks"));
        assertTrue(inside.message().contains("X 4000, Z -1"));
        assertTrue(inside.message().contains("26.2"));
        assertTrue(inside.message().contains("26.3"));
        assertNotNull(inside.route());
        assertFalse(inside.message().contains("/spawn"));

        var outside = guide.advise(new Location(world, -0.1, 64, 4000));
        assertTrue(outside.message().contains("already beyond"));
        assertNull(outside.route());

        when(border.isInside(any(Location.class))).thenReturn(false);
        var blocked = guide.advise(new Location(world, 4000, 64, 4000));
        assertTrue(blocked.message().contains("world border blocks"));
        assertNull(blocked.route());

        when(world.getName()).thenReturn("creative");
        assertTrue(guide.advise(new Location(world, 4000, 64, 4000)).message().contains("not have"));
        assertFalse(guide.recognises("creative", "Where are the new chunks?"));
        assertTrue(guide.recognises("SURVIVAL", "Where is the new forest?"));
    }

    @Test
    void wildernessInstructionCannotAcceptTeleportOrCoordinateArguments() {
        assertEquals(BotScript.Op.WILDERNESS, BotScript.parseInstruction("wilderness").op());
        assertThrows(IllegalArgumentException.class, () -> BotScript.parseInstruction("wilderness:spawn"));
    }

    private static BotScript script(String bounds) throws Exception {
        return BotScript.read(configuration(bounds));
    }

    private static ConfigSectionImpl configuration(String bounds) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            actions:
              start: ['talk:custom intro', 'listen:* -> start']
            worlds:
              survival:
                command-action: start
                wilderness:
                  old-generation-bounds: %s
            """.formatted(bounds));
        return new ConfigSectionImpl(null, yaml);
    }
}
