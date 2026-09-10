package dev.stemcraft.feature.stembot;

import dev.stemcraft.config.ConfigFileImpl;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BotScriptTest {
    @TempDir Path folder;
    ConfigFileImpl config;
    @BeforeEach void setup() throws Exception {
        Path file=folder.resolve("stembot.yml");
        try(var in=getClass().getResourceAsStream("/stembot.yml")) { Files.copy(Objects.requireNonNull(in),file); }
        config=new ConfigFileImpl();assertTrue(config.load(file.toFile(),true));
    }
    @Test void defaultsContainTheRequestedSkinSpawnAndOrderedTour() {
        var script=BotScript.read(config);
        assertEquals("http://192.168.1.40:8950/stembot.png",script.skinUrl());
        assertEquals(new BotScript.Point(-919,89,291,90),script.firstSpawn());
        assertEquals(List.of("survival","creative","minigames","challenges"),
            script.tours().get("world").stream().map(BotScript.Waypoint::id).toList());
        assertEquals(new BotScript.Point(-864,85,245,0),script.tours().get("world").getFirst().point());
        assertEquals(new BotScript.Point(-864,85,266,180),script.tours().get("world").get(1).point());
    }
    @Test void worldRulesTakePriorityAndCloseIsAnInternalAction() {
        var script=BotScript.read(config);
        assertTrue(script.respond("world","ready","Where is SURVIVAL?").say().getFirst().contains("-864"));
        assertFalse(script.respond("survival","ready","help").say().equals(script.respond("world","ready","help").say()));
        assertEquals(List.of("@close"),script.respond("world","ready","@close").actions());
        assertTrue(script.respond("world","ready","/op me").actions().isEmpty());
    }
    @Test void stateSupportsFollowupQuestionsWithoutMatchingAnotherState() {
        config.set("worlds.world.responses.question.regex","yes");
        config.set("worlds.world.responses.question.state","feedback");
        config.set("worlds.world.responses.question.next-state","finished");
        config.set("worlds.world.responses.question.say",List.of("Thanks for the feedback!"));
        var script=BotScript.read(config);
        assertEquals("finished",script.respond("world","feedback","yes").nextState());
        assertEquals(List.of("@tour"),script.respond("world","ready","yes").actions());
    }
    @Test void invalidRegexActionsAndMovementFailConfigurationValidation() {
        config.set("responses.close.regex","[");
        assertThrows(IllegalArgumentException.class,()->BotScript.read(config));
        config.set("responses.close.regex","bye");
        config.set("responses.close.actions",List.of("@console op someone"));
        assertThrows(IllegalArgumentException.class,()->BotScript.read(config));
        config.set("responses.close.actions",List.of("@close"));
        config.set("movement.resume-distance",20);
        assertThrows(IllegalArgumentException.class,()->BotScript.read(config));
    }
}
