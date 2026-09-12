package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    private java.util.function.Consumer<Boolean> pendingCallback;
    private int callbackCancels;
    private Boolean immediateResult;

    @BeforeEach
    void setUp() {
        setUp(0);
    }

    private void setUp(int postDelayTicks) {
        setUp(postDelayTicks,List.of("say:hello","talk:talking","walk:10 64 0",
            "listen:yes|tour -> done, * -> start"));
    }

    private void setUp(int postDelayTicks,List<String> instructions) {
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
        actions.put("start",instructions.stream().map(BotScript::parseInstruction).toList());
        actions.put("menu",List.of(BotScript.parseInstruction("listen:*quest* -> done, * -> start")));
        actions.put("done",List.of(BotScript.parseInstruction("end")));

        var script=new BotScript(
            "STEMBot","","{message}","","","",false,
            2.5,8,60,3,0.9,10,6,600,
            new BotScript.ChatSettings(12,6,60,"public {seconds}","private"),speech,
            List.of("wait"),List.of("scanning"),30,List.of("stuck"),List.of("farewell"),
            Map.copyOf(actions),Map.of("world","start"),Map.of()
        );

        session=new BotSession(
            script,
            actor,
            "world",
            "start",
            new BotSession.Output() {
                public Runnable await(String key, java.util.function.Consumer<Boolean> result) {
                    pendingCallback = result;
                    if (immediateResult != null) result.accept(immediateResult);
                    return () -> callbackCancels++;
                }
                public void say(String text) { output.add(text); }
                public int talk(String text) { output.add(text); return 10; }
            }
        );
    }

    @Test void providerSuccessAdvancesButTypedSuccessCannotFakeIt() {
        setUp(0, List.of("await:stemcraft:coordbar 60 -> done, menu"));
        session.tick(here);
        session.input("success");
        assertEquals("start", session.action());
        pendingCallback.accept(true);
        assertEquals("done", session.action());
        assertEquals(1, callbackCancels);
    }

    @Test void skipCancelsPracticeAndIgnoresItsLateCallback() {
        setUp(0, List.of("await:stemcraft:quest-practice 60 -> done, menu"));
        session.tick(here);
        session.input("skip");
        assertEquals("menu", session.action());
        pendingCallback.accept(true);
        assertEquals("menu", session.action());
        assertEquals(1, callbackCancels);
    }

    @Test void callbackTimeoutAndDismissalCleanUpWithoutSuccess() {
        setUp(0, List.of("await:stemcraft:coordbar 1 -> done, menu"));
        session.tick(here);
        for (int i = 0; i < 4; i++) session.tick(here);
        assertEquals("menu", session.action());
        assertEquals(1, callbackCancels);
        session.close();
        pendingCallback.accept(true);
        assertTrue(session.closed());
    }

    @Test void synchronousProvidersAndTopicInterruptionsAreSupported() {
        immediateResult = true;
        setUp(0, List.of("await:stemcraft:coordbar 60 -> done, menu"));
        session.tick(here);
        assertEquals("done", session.action());
        assertEquals(1, callbackCancels);
        immediateResult = null;
        setUp(0, List.of("await:stemcraft:quest-practice 60 -> done, menu", "action:menu"));
        session.tick(here);
        session.input("quests");
        assertEquals("done", session.action());
        pendingCallback.accept(false);
        assertEquals("done", session.action());
    }

    @Test
    void topicInterruptsWalkingThroughALaterActionMenu() {
        setUp(0,List.of("walk:10 64 0","action:menu"));
        session.tick(here);
        clearInvocations(actor);
        session.input("tell me about quests");
        assertEquals("done",session.action());
        verify(actor).cancel();
        session.tick(here);
        verify(actor,never()).move(any(),anyDouble());
        assertTrue(session.closed());
    }

    @Test
    void topicInterruptsSpeechAndInvalidatesQueuedSpeechSounds() {
        session.tick(here);
        long revision=session.speechRevision();
        session.input("tour");
        assertEquals("done",session.action());
        assertTrue(session.speechRevision()>revision);
        session.tick(here);
        verify(actor,never()).move(any(),anyDouble());
    }

    @ParameterizedTest
    @ValueSource(strings={"talk:talking","sleep:100","wave","point:10 64 0","walk:10 64 0"})
    void byeInterruptsEveryBlockingInstruction(String instruction) {
        setUp(0,List.of(instruction,"action:menu"));
        session.tick(here);
        session.input("bye");
        assertTrue(session.closed());
        session.tick(here);
        verify(actor,times(1)).close();
        assertEquals(1,output.stream().filter(line->line.equals("farewell")).count());
    }

    @ParameterizedTest
    @ValueSource(strings={"bye","GOODBYE!","stop","cancel","go away","please go away","leave me alone please"})
    void recognisesDismissalPhrases(String phrase) {
        assertTrue(BotSession.isDismissal(phrase));
        assertFalse(BotSession.isDismissal("how do I stop tracking quests"));
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
    void stalledWalkUsesReachableWaypointThenResumesOriginalDestination() {
        setUp(0,List.of("walk:10 64 0", "listen:yes -> done"));
        Location blocked = new Location(world, -2, 64, 0);
        Location landing = new Location(world, -4, 65, 0);
        when(actor.recoveryWaypoints(any())).thenReturn(List.of(blocked, landing));
        when(actor.canNavigateTo(landing)).thenReturn(true);
        for(int i=0;i<41;i++) session.tick(here);
        clearInvocations(actor);
        session.tick(here);
        verify(actor).canNavigateTo(blocked);
        verify(actor,never()).canNavigateTo(landing);
        session.tick(here);
        verify(actor).move(landing, .9);
        assertEquals(1, output.stream().filter("scanning"::equals).count());
        assertFalse(output.contains("stuck"));
        here=landing;
        clearInvocations(actor);
        session.tick(here);
        verify(actor).move(new Location(world,10,64,0), .9);
        here=new Location(world,10,64,0);
        session.tick(here);
        session.input("yes");
        session.tick(here);
        assertTrue(session.closed());
    }

    @Test
    void unreachableRecoveryCandidatesFallBackToPromptAndDoNotAdvanceWalk() {
        setUp(0,List.of("walk:10 64 0", "say:arrived", "listen:yes -> done"));
        when(actor.recoveryWaypoints(any())).thenReturn(List.of(new Location(world,-4,65,0)));
        for(int i=0;i<45;i++) session.tick(here);
        assertTrue(output.contains("stuck"));
        assertFalse(output.contains("arrived"));
        verify(actor,times(1)).canNavigateTo(any());
    }

    @Test
    void recoveryStopsWhenDismissed() {
        setUp(0,List.of("walk:10 64 0", "listen:yes -> done"));
        when(actor.recoveryWaypoints(any())).thenReturn(List.of(new Location(world,-4,65,0)));
        for(int i=0;i<41;i++) session.tick(here);
        session.input("bye");
        clearInvocations(actor);
        session.tick(here);
        verify(actor,never()).canNavigateTo(any());
        verify(actor,never()).move(any(),anyDouble());
        assertTrue(session.closed());
    }

    @Test
    void scanningAnnouncementCooldownSurvivesRetry() {
        setUp(0,List.of("walk:10 64 0", "listen:yes -> done"));
        when(actor.recoveryWaypoints(any())).thenReturn(List.of(new Location(world,-4,65,0)));
        for(int i=0;i<45;i++) session.tick(here);
        assertEquals(1,output.stream().filter("scanning"::equals).count());
        session.input("retry");
        for(int i=0;i<45;i++) session.tick(here);
        assertEquals(1,output.stream().filter("scanning"::equals).count());
        // Wait beyond the cooldown before starting another recovery attempt.
        for(int i=0;i<80;i++) session.tick(here);
        session.input("retry");
        for(int i=0;i<45;i++) session.tick(here);
        assertEquals(2,output.stream().filter("scanning"::equals).count());
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
