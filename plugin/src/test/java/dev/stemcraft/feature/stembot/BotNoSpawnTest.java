package dev.stemcraft.feature.stembot;

import dev.stemcraft.config.ConfigSectionImpl;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotNoSpawnTest {
    private YamlConfiguration config() {
        var yaml=new YamlConfiguration();
        yaml.set("actions.directions",List.of(
            "require-world:survival* normal -> wrong-world","exit-region:old-terrain"));
        yaml.set("actions.wrong-world",List.of("talk:Go to Survival","end"));
        yaml.set("actions.mail",List.of("talk:Mail help","end"));
        yaml.set("actions.done",List.of("end"));
        yaml.set("regions.old-terrain.bounds","-7985,-8025;8023,7975");
        yaml.set("regions.old-terrain.align-to-chunks",true);
        yaml.set("help-topics.wilderness","no-spawn:directions");
        yaml.set("help-topics.mail","mail");
        yaml.set("messages.farewell",List.of("Goodbye"));
        return yaml;
    }

    private BotScript script(YamlConfiguration yaml) {
        return BotScript.read(new ConfigSectionImpl(null,yaml));
    }

    @Test void routesKeepTheActionNameAndSpawnPreferenceSeparate() {
        var script=script(config());
        assertEquals(new BotScript.HelpTopic("directions",true),script.helpTopic("WILDERNESS"));
        assertEquals("directions",script.helpTopicAction("wilderness"));
        assertEquals(new BotScript.HelpTopic("mail",false),script.helpTopic("mail"));
        assertNull(script.helpTopic("missing"));
    }

    @ParameterizedTest
    @ValueSource(strings={"no-spawn:","no-spawn:missing"})
    void rejectsMissingActions(String route) {
        var yaml=config();
        yaml.set("help-topics.wilderness",route);
        assertThrows(IllegalArgumentException.class,()->script(yaml));
    }

    @ParameterizedTest
    @ValueSource(strings={"wave","walk:1 64 1","look:player","point:player","sneak","stand","speed:1",
        "listen:* -> done","await:stemcraft:coordbar 60 -> done, done"})
    void acceptsActorAndReplyStepsForLazySpawning(String step) {
        var yaml=config();
        yaml.set("actions.directions",List.of(step));
        assertDoesNotThrow(()->script(yaml));
    }

    @Test void acceptsActorStepsThroughActionJumpsAndWrongWorldFallbacks() {
        var yaml=config();
        yaml.set("actions.actor-step",List.of("wave","end"));
        yaml.set("actions.directions",List.of("action:actor-step"));
        assertDoesNotThrow(()->script(yaml));
        yaml.set("actions.directions",List.of("require-world:survival normal -> actor-step","end"));
        assertDoesNotThrow(()->script(yaml));
    }

    @ParameterizedTest
    @ValueSource(strings={"survival","creative"})
    void sendsOnePrivateTextResponseWithoutSpeechDepartureOrFarewell(String name) {
        var world=mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        var border=mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any())).thenReturn(true);
        var messages=new ArrayList<String>();
        var session=BotSession.noSpawn(script(config()),name,"directions",output(messages));

        session.tick(new Location(world,0,64,0));

        assertTrue(session.closed());
        assertFalse(session.chatEngaged());
        assertFalse(session.controlled());
        assertNull(session.actor());
        assertEquals(1,messages.size());
        if(name.equals("survival")) assertTrue(messages.getFirst().contains("south about 7985 blocks"));
        else assertEquals("Go to Survival",messages.getFirst());
        session.close();
        assertEquals(1,messages.size());
    }

    @Test void delayedTextContinuesThroughActionJumpsAndCanBeCancelledOnWorldChange() {
        var yaml=config();
        yaml.set("actions.directions",List.of("say:First","sleep:10","action:finish"));
        yaml.set("actions.finish",List.of("talk:Last","end"));
        var world=mock(World.class);
        when(world.getName()).thenReturn("survival");
        var here=new Location(world,0,64,0);
        var messages=new ArrayList<String>();
        var session=BotSession.noSpawn(script(yaml),"survival","directions",output(messages));
        session.tick(here);
        session.tick(here);
        assertEquals(List.of("First"),messages);
        assertFalse(session.chatEngaged());
        session.tick(here);
        assertEquals(List.of("First","Last"),messages);
        assertTrue(session.closed());

        messages.clear();
        var cancelled=BotSession.noSpawn(script(yaml),"survival","directions",output(messages));
        cancelled.tick(here);
        when(world.getName()).thenReturn("creative");
        cancelled.tick(here);
        cancelled.tick(here);
        assertTrue(cancelled.closed());
        assertEquals(List.of("First"),messages);
    }

    @Test void cyclicNoSpawnActionsRetainTheInstructionLimit() {
        var yaml=config();
        yaml.set("actions.directions",List.of("action:directions"));
        var world=mock(World.class);
        when(world.getName()).thenReturn("survival");
        var session=BotSession.noSpawn(script(yaml),"survival","directions",output(new ArrayList<>()));
        assertThrows(IllegalStateException.class,()->session.tick(new Location(world,0,64,0)));
    }

    private BotSession.Output output(List<String> messages) {
        return new BotSession.Output() {
            @Override public void say(String text) { messages.add(text); }
            @Override public int talk(String text) { throw new AssertionError("A no-spawn reply must not start robot speech"); }
            @Override public void depart(BotActor actor) { throw new AssertionError("A no-spawn reply has no actor to depart"); }
        };
    }
}
