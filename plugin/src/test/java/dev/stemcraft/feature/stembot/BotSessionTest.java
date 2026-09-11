package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BotSessionTest {
    private BotActor actor;
    private World world;
    private Location here;
    private List<String> output;
    private BotSession session;

    @BeforeEach
    void setUp() {
        world=mock(World.class);
        when(world.getName()).thenReturn("world");

        here=new Location(world,0,64,0);

        actor=mock(BotActor.class);
        when(actor.valid()).thenReturn(true);
        when(actor.location()).thenAnswer(i->here.clone());

        output=new ArrayList<>();

        var speech=new BotScript.SpeechSettings(
            true,"block.note_block.bit",.2f,1.2f,1.5f,
            2,6,6,2,30,8,18
        );

        var actions=new LinkedHashMap<String,List<BotScript.Instruction>>();
        actions.put("start",List.of(
            BotScript.parseInstruction("say:hello"),
            BotScript.parseInstruction("talk:talking"),
            BotScript.parseInstruction("walk:10 64 0"),
            BotScript.parseInstruction("listen:yes|tour -> done, * -> start")
        ));
        actions.put("done",List.of(BotScript.parseInstruction("end")));

        var script=new BotScript(
            "STEMBot","","","","",false,
            2.5,1.15,10,6,600,speech,
            List.of("wait"),List.of("stuck"),List.of(),
            Map.copyOf(actions),Map.of("world","start"),Map.of()
        );

        session=new BotSession(
            script,
            actor,
            "world",
            "start",
            new BotSession.Output() {
                public void say(String text) { output.add(text); }
                public int talk(String text) { output.add(text); return 10; }
            }
        );
    }

    @Test
    void talkBlocksBeforeWalk() {
        session.tick(here);
        assertEquals(List.of("hello","talking"),output);
        verify(actor,never()).move(any(),anyDouble());

        session.tick(here);
        verify(actor,never()).move(any(),anyDouble());

        session.tick(here);
        verify(actor).move(argThat(l->l.getX()==10),eq(1.15));
    }

    @Test
    void walkBlocksUntilArrivalThenListenBranches() {
        session.tick(here);
        session.tick(here);
        session.tick(here);

        here=new Location(world,10,64,0);
        session.tick(here);

        assertFalse(session.closed());

        session.input("yes");
        session.tick(here);

        assertTrue(session.closed());
        verify(actor).close();
    }

    @Test
    void fallingBehindCancelsWalkAndRandomWaitingMessageIsShown() {
        session.tick(here);
        session.tick(here);
        session.tick(here);

        session.tick(new Location(world,20,64,0));

        verify(actor,atLeastOnce()).cancel();
        assertTrue(output.contains("wait"));
    }
}
