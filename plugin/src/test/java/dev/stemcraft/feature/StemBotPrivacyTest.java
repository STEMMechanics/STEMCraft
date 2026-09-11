package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.feature.stembot.BotSession;
import dev.stemcraft.feature.stembot.BotActor;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StemBotPrivacyTest {
    StemBotFeature feature;
    STEMCraftAPI api;
    TaskService tasks;
    org.mockbukkit.mockbukkit.entity.PlayerMock owner,other;
    Map<UUID,BotSession> sessions;
    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        var server=MockBukkit.mock();owner=server.addPlayer();other=server.addPlayer();
        api=mock(STEMCraftAPI.class);tasks=mock(TaskService.class);when(api.tasks()).thenReturn(tasks);
        feature=new StemBotFeature(api);
        var field=StemBotFeature.class.getDeclaredField("sessions");field.setAccessible(true);
        sessions=(Map<UUID,BotSession>)field.get(feature);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    @Test void disengagedSessionsLeavePublicChatUntouchedAndReengagedSessionsCaptureIt() {
        BotSession session=mock(BotSession.class);
        sessions.put(owner.getUniqueId(),session);
        AsyncChatEvent event=mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(owner);
        feature.chat(event);
        verify(event,never()).setCancelled(anyBoolean());
        assertFalse(feature.isPrivateChat(event));

        when(session.chatEngaged()).thenReturn(true);
        when(event.message()).thenReturn(Component.text("tour"));
        Set<Audience> viewers=new HashSet<>(List.of(owner,other));
        when(event.viewers()).thenReturn(viewers);
        feature.chat(event);
        verify(event).setCancelled(true);
        assertTrue(viewers.isEmpty());
        when(session.chatEngaged()).thenReturn(false);
        assertTrue(feature.isPrivateChat(event),"An already-captured reply must remain private after walking away");
    }

    @Test void resummonIsSilentAndWaitsForDepartureWithoutDuplicatingRequests() throws Exception {
        BotSession session=mock(BotSession.class);
        BotActor actor=mock(BotActor.class);
        sessions.put(owner.getUniqueId(),session);
        doAnswer(call->{feature.depart(owner.getUniqueId(),actor,60);return null;})
            .when(session).close(false);

        feature.summon(owner,"hub");
        verify(session).close(false);
        verify(session,never()).close();
        assertTrue(feature.hasActiveSession(owner.getUniqueId()));
        AsyncChatEvent event=mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(owner);
        when(event.message()).thenReturn(Component.text("hello"));
        Set<Audience> viewers=new HashSet<>(List.of(owner,other));
        when(event.viewers()).thenReturn(viewers);
        feature.chat(event);
        verify(event).setCancelled(true);
        assertTrue(viewers.isEmpty());
        assertTrue(feature.isPrivateChat(event));
        var field=StemBotFeature.class.getDeclaredField("pendingSummons");
        field.setAccessible(true);
        Map<?,?> pending=(Map<?,?>)field.get(feature);
        assertTrue(pending.containsKey(owner.getUniqueId()));
        feature.summon(owner,"hub");
        assertEquals(1,pending.size());
        List<Runnable> queued=new ArrayList<>();
        doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(tasks).nextTick(any());
        when(event.message()).thenReturn(Component.text("go away"));
        feature.chat(event);
        queued.getFirst().run();
        assertTrue(pending.isEmpty(),"Dismissal must cancel the pending re-summon");
        feature.onDisable();
        assertTrue(pending.isEmpty());
        verify(actor).close();
    }

    @Test void departureLingersThenPuffsAndDestroysExactlyOnce() {
        BotActor actor=mock(BotActor.class);
        when(actor.valid()).thenReturn(true);
        feature.depart(owner.getUniqueId(),actor,60);
        for(int i=0;i<11;i++) feature.tickDepartures();
        verify(actor,never()).puff();
        verify(actor,never()).close();
        feature.tickDepartures();
        var order=inOrder(actor);
        order.verify(actor).puff();
        order.verify(actor).close();
        feature.tickDepartures();
        verify(actor,times(1)).close();
    }

    @Test void shutdownImmediatelyDestroysLingeringActors() {
        BotActor actor=mock(BotActor.class);
        feature.depart(owner.getUniqueId(),actor,60);
        feature.onDisable();
        verify(actor).close();
        verify(actor,never()).puff();
        feature.tickDepartures();
        verify(actor,times(1)).close();
    }

    @Test void spawnSearchFindsGroundFartherOutToTheSide() {
        var world=mock(org.bukkit.World.class);
        var border=mock(org.bukkit.WorldBorder.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any())).thenReturn(true);
        var air=mock(org.bukkit.block.Block.class);
        var stone=mock(org.bukkit.block.Block.class);
        when(air.getType()).thenReturn(org.bukkit.Material.AIR);
        when(stone.getType()).thenReturn(org.bukkit.Material.STONE);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air);
        when(world.getBlockAt(7,63,0)).thenReturn(stone);

        var found=StemBotFeature.findSpawn(new org.bukkit.Location(world,0,64,0),2.5,8);
        assertNotNull(found);
        assertEquals(7,found.getBlockX());
        assertEquals(64,found.getBlockY());
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertNull(StemBotFeature.findSpawn(new org.bukkit.Location(world,0,64,0),2.5,8));
    }

    @Test void replyFormatPreservesPlayerTextLiterally() {
        String reply="&c<red>tour</red> :chat_bubble: {message}";
        Component formatted=StemBotFeature.formatReply("&7You → STEMBot: {message}",reply);
        assertEquals("You → STEMBot: "+reply,
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(formatted));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GRAY,formatted.color());
    }
    @Test void capturesAndClearsAudienceBeforeDispatchAndRemembersClosedSessionsChat() {
        BotSession session=mock(BotSession.class);sessions.put(owner.getUniqueId(),session);
        when(session.closed()).thenReturn(true);
        when(session.chatEngaged()).thenReturn(true);
        AsyncChatEvent event=mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(owner);when(event.message()).thenReturn(Component.text("@close"));
        Set<Audience> viewers=new HashSet<>(List.of(owner,other));when(event.viewers()).thenReturn(viewers);
        List<Runnable> queued=new ArrayList<>();
        doAnswer(call->{ queued.add(call.getArgument(0));return null; }).when(tasks).nextTick(any());
        feature.chat(event);
        verify(event).setCancelled(true);assertTrue(viewers.isEmpty());
        verify(session,never()).input(anyString());
        queued.getFirst().run();
        verify(session).input("@close");
        assertFalse(feature.hasActiveSession(owner.getUniqueId()));
        assertTrue(feature.isPrivateChat(event),"Later audit/formatting handlers must still skip this consumed event");
        assertNull(other.nextMessage());
    }
    @Test void unrelatedPlayersChatIsUntouchedAndReloadDisableDestroysSessions() {
        BotSession session=mock(BotSession.class);sessions.put(owner.getUniqueId(),session);
        AsyncChatEvent event=mock(AsyncChatEvent.class);when(event.getPlayer()).thenReturn(other);
        feature.chat(event);verify(event,never()).setCancelled(anyBoolean());
        feature.onDisable();verify(session).close(false);assertFalse(feature.hasActiveSession(owner.getUniqueId()));
    }
}
