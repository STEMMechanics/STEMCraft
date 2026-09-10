package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.feature.stembot.BotSession;
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
    @Test void capturesAndClearsAudienceBeforeDispatchAndRemembersClosedSessionsChat() {
        BotSession session=mock(BotSession.class);sessions.put(owner.getUniqueId(),session);
        when(session.closed()).thenReturn(true);
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
        feature.onDisable();verify(session).close();assertFalse(feature.hasActiveSession(owner.getUniqueId()));
    }
}
