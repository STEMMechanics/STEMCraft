package dev.stemcraft.feature.stembot;

import dev.stemcraft.config.ConfigFileImpl;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotSessionTest {
    @TempDir Path folder;
    BotActor actor;
    BotScript script;
    BotSession session;
    World world;
    Location here;
    List<List<String>> messages;
    @BeforeEach void setup() throws Exception {
        Path file=folder.resolve("stembot.yml");
        try(var in=getClass().getResourceAsStream("/stembot.yml")) { Files.copy(Objects.requireNonNull(in),file); }
        var config=new ConfigFileImpl();config.load(file.toFile(),true);script=BotScript.read(config);
        world=mock(World.class);when(world.getName()).thenReturn("world");
        here=new Location(world,-921,89,291);
        actor=mock(BotActor.class);when(actor.valid()).thenReturn(true);when(actor.location()).thenAnswer(call->here.clone());
        messages=new ArrayList<>();session=new BotSession(script,actor,"world",messages::add);
    }
    private void ticks(int ticks,Location owner) { for(int i=0;i<ticks;i+=5) session.tick(owner); }
    @Test void greetsWithThreeCrouchesThenWaitsForConsent() {
        ticks(40,here);
        verify(actor,times(3)).sneak(true);verify(actor,times(3)).sneak(false);
        verify(actor,never()).move(any(),anyDouble());
        session.input("yes");session.tick(here);
        verify(actor).move(argThat(l->l.getX()==-864&&l.getZ()==245),eq(1.15));
    }
    @Test void waitsForOwnerAndResumesWithHysteresis() {
        session.startTour();ticks(40,here);
        clearInvocations(actor);
        session.tick(here.clone().add(15,0,0));verify(actor).pause(true);
        int count=messages.size();
        ticks(100,here.clone().add(8,0,0));assertEquals(count,messages.size());
        session.tick(here.clone().add(5,0,0));verify(actor).pause(false);
    }
    @Test void stopsAndFacesEachWaypointBeforeProceeding() {
        session.startTour();ticks(35,here);
        for(var point:script.tours().get("world")) {
            var p=point.point();here=new Location(world,p.x(),p.y(),p.z());
            session.tick(here);verify(actor,atLeastOnce()).face(p.yaw());
            assertEquals(point.say(),messages.getLast());
            ticks(point.pauseTicks()+5,here);
        }
        session.tick(here);
        assertEquals(script.complete(),messages.getLast());
        assertFalse(session.closed());
        session.input("bye");assertTrue(session.closed());verify(actor).close();
    }
    @Test void jumpsOnlyWhenMovingAndStopsOnBlockedRoute() {
        when(actor.navigating()).thenReturn(true);
        session.startTour();ticks(240,here);
        verify(actor,atLeastOnce()).jump();verify(actor,atLeastOnce()).cancel();
        assertEquals(script.stuck(),messages.getLast());
        clearInvocations(actor);ticks(100,here);verify(actor,never()).move(any(),anyDouble());
        session.input("continue");when(actor.navigating()).thenReturn(false);
        session.tick(here);verify(actor).move(any(),anyDouble());
    }
    @Test void privateCloseIsIdempotentAndWorldChangesCloseTheActor() {
        World other=mock(World.class);when(other.getName()).thenReturn("survival");
        session.tick(new Location(other,0,64,0));
        assertTrue(session.closed());session.close();verify(actor,times(1)).close();
    }
}
