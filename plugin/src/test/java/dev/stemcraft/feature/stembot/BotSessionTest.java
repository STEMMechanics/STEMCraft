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
        setUp(0);
    }

    private void setUp(int postDelayTicks) {
        world=mock(World.class);
        when(world.getName()).thenReturn("world");

        here=new Location(world,0,64,0);

        actor=mock(BotActor.class);
        when(actor.valid()).thenReturn(true);
        when(actor.location()).thenAnswer(i->here.clone());

        output=new ArrayList<>();

        var speech=new BotScript.SpeechSettings(
            true,"block.note_block.bit",.2f,1.2f,1.5f,
            2,6,6,2,30,8,18,postDelayTicks
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
            "STEMBot","","{message}","","","",false,
            2.5,8,60,3,0.9,10,6,600,
            new BotScript.ChatSettings(12,6,60,"public {seconds}","private"),speech,
            List.of("wait"),List.of("stuck"),List.of("farewell"),
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
    void walkingAwayPausesChatAndResumesOnlyInsideReturnDistance() {
        session.tick(here);
        var far=new Location(world,13,64,0);
        session.tick(far);
        assertFalse(session.chatEngaged());
        assertEquals(1,output.stream().filter(line->line.equals("public 60")).count());
        clearInvocations(actor);
        for(int i=0;i<10;i++) session.tick(new Location(world,8,64,0));
        assertFalse(session.chatEngaged());
        verify(actor,never()).move(any(),anyDouble());
        verify(actor,never()).animate(anyString());
        session.tick(new Location(world,6,64,0));
        assertTrue(session.chatEngaged());
        assertEquals(1,output.stream().filter(line->line.equals("private")).count());
        assertFalse(output.contains("stuck"));
    }

    @Test
    void abandonedSessionExpiresSilentlyEvenIfPublicChatContinues() {
        session.tick(here);
        var far=new Location(world,13,64,0);
        for(int i=0;i<239;i++) {
            session.tick(far);
            session.input("public conversation");
        }
        assertFalse(session.closed());
        session.tick(far);
        assertTrue(session.closed());
        assertFalse(output.contains("farewell"));
        verify(actor).close();
    }

    @Test
    void returningResumesTheExistingReplyPrompt() {
        session.tick(here);
        session.tick(here);
        session.tick(here);
        here=new Location(world,10,64,0);
        session.tick(here);
        session.tick(new Location(world,30,64,0));
        session.input("yes");
        assertFalse(session.closed());
        session.tick(here);
        session.input("yes");
        session.tick(here);
        assertTrue(session.closed());
    }

    @Test
    void stalledWalkCanBeSkippedWithoutRepeatingIt() {
        for(int i=0;i<45;i++) session.tick(here);
        assertTrue(output.contains("stuck"));
        assertTrue(output.stream().anyMatch(line->line.contains("continue to skip")));
        clearInvocations(actor);
        session.input("continue");
        session.tick(here);
        verify(actor,never()).move(any(),anyDouble());
        session.input("yes");
        session.tick(here);
        assertTrue(session.closed());
    }

    @Test
    void stalledWalkCanBeRetried() {
        for(int i=0;i<45;i++) session.tick(here);
        assertTrue(output.contains("stuck"));
        clearInvocations(actor);
        session.input("retry");
        session.tick(here);
        verify(actor).move(argThat(l->l.getX()==10),eq(0.9));
        assertFalse(session.closed());
    }

    @Test
    void silentCloseSuppressesFarewellAndOnlyClosesOnce() {
        session.close(false);
        session.close();
        assertTrue(session.closed());
        assertFalse(output.contains("farewell"));
        verify(actor,times(1)).close();
    }

    @Test
    void acceptsArrivalWithinThreeBlocks() {
        session.tick(here);
        session.tick(here);
        session.tick(here);
        here=new Location(world,7.5,64,0);
        for(int i=0;i<45;i++) session.tick(here);
        assertFalse(output.contains("stuck"));
        session.input("yes");
        session.tick(here);
        assertTrue(session.closed());
    }

    @Test
    void quietReadingPauseDelaysNextActionAfterSpeech() {
        setUp(60);
        session.tick(here); // Speech starts at tick 5 and ends at tick 15.
        session.tick(here);
        session.tick(here);
        clearInvocations(actor);

        for(int i=0;i<11;i++) session.tick(here);
        verify(actor,never()).move(any(),anyDouble());
        verify(actor,never()).animate(anyString());

        session.tick(here); // Three seconds after speech ends.
        verify(actor).move(argThat(l->l.getX()==10),eq(0.9));
    }

    @Test
    void talkBlocksBeforeWalk() {
        session.tick(here);
        assertEquals(List.of("hello","talking"),output);
        verify(actor,never()).move(any(),anyDouble());

        session.tick(here);
        verify(actor,never()).move(any(),anyDouble());

        session.tick(here);
        verify(actor).move(argThat(l->l.getX()==10),eq(0.9));
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

        session.tick(new Location(world,11,64,0));

        verify(actor,atLeastOnce()).cancel();
        assertTrue(output.contains("wait"));
    }
}
