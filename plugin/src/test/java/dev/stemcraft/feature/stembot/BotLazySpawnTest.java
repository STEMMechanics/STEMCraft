package dev.stemcraft.feature.stembot;

import dev.stemcraft.config.ConfigSectionImpl;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotLazySpawnTest {
    private World world;
    private BotActor actor;
    private Location here;
    private YamlConfiguration yaml;
    private final List<String> messages=new ArrayList<>();
    private final List<String> voiced=new ArrayList<>();
    private boolean ready;
    private int requests;
    private int appearances;
    private String callback;

    @BeforeEach void setup() {
        world=mock(World.class);
        when(world.getName()).thenReturn("survival");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        here=new Location(world,0,64,0);
        actor=mock(BotActor.class);
        when(actor.valid()).thenReturn(true);
        when(actor.location()).thenAnswer(call->here.clone());
        when(actor.navigating()).thenReturn(true);
        yaml=new YamlConfiguration();
        yaml.set("speech.post-delay-ticks",0);
        yaml.set("actions.done",List.of("listen:* -> close"));
        yaml.set("help-topics.demo","no-spawn:start");
    }

    private BotSession session(String... steps) {
        yaml.set("actions.start",List.of(steps));
        var script=BotScript.read(new ConfigSectionImpl(null,yaml));
        return BotSession.noSpawn(script,"survival","start",new BotSession.Output() {
            @Override public void say(String text) { messages.add(text); }
            @Override public int talk(String text) { voiced.add(text); messages.add(text); return 1; }
            @Override public BotActor spawn() { requests++; return ready?actor:null; }
            @Override public void spawned(BotSession session) {
                assertSame(actor,session.actor());
                assertTrue(session.chatEngaged());
                appearances++;
            }
            @Override public Runnable await(String key,java.util.function.Consumer<Boolean> completion) {
                callback=key;
                return () -> {};
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings={"walk:8 64 0","look:player","point:player","wave","sneak","stand",
        "listen:yes -> done","await:stemcraft:coordbar 60 -> done, done"})
    void actorAndReplyStepsSpawnOnlyWhenReached(String step) {
        ready=true;
        var session=session("say:First",step,"listen:* -> done");
        assertNull(session.actor());
        assertFalse(session.chatEngaged());
        assertEquals(0,requests);

        session.tick(here);

        assertEquals(List.of("First"),messages);
        assertSame(actor,session.actor());
        assertTrue(session.chatEngaged());
        assertEquals(1,requests);
        assertEquals(1,appearances);
        if(step.startsWith("await:")) assertEquals("stemcraft:coordbar",callback);
    }

    @Test void aDeferredSpawnPreservesTheInstructionActionAndSpeedWithoutRepeatingText() {
        yaml.set("actions.travel",List.of("walk:8 64 0","listen:* -> done"));
        var session=session("talk:First","speed:1.6","action:travel");
        session.tick(here);
        session.tick(here);
        session.input("yes");
        assertEquals(List.of("First"),messages);
        assertTrue(voiced.isEmpty());
        assertEquals("travel",session.action());
        assertFalse(session.chatEngaged());
        assertEquals(0,appearances);

        ready=true;
        session.tick(here);

        assertEquals(List.of("First"),messages);
        assertEquals(1,appearances);
        verify(actor).move(new Location(world,8,64,0),1.6);
    }

    @Test void textBeforeTheActorIsSilentAndLaterTalkUsesSpeech() {
        ready=true;
        var session=session("talk:Before","look:player","talk:After","end");
        session.tick(here);
        assertEquals(List.of("Before","After"),messages);
        assertEquals(List.of("After"),voiced);
        assertEquals(1,appearances);
        session.tick(here);
        assertTrue(session.closed());
        verify(actor).close();
    }

    @Test void aWorldChangeCancelsThePendingSpawn() {
        var session=session("say:First","wave","end");
        session.tick(here);
        assertEquals(1,requests);
        var other=mock(World.class);
        when(other.getName()).thenReturn("creative");
        ready=true;
        session.tick(new Location(other,0,64,0));
        assertTrue(session.closed());
        assertEquals(1,requests);
        assertEquals(0,appearances);
        assertEquals(List.of("First"),messages);
    }
}
