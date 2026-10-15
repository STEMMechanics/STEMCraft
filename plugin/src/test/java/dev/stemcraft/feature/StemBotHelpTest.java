package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandBuilder;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.command.CommandExecutor;
import dev.stemcraft.api.service.event.EventHandler;
import dev.stemcraft.config.ConfigFileImpl;
import dev.stemcraft.config.ConfigSectionImpl;
import dev.stemcraft.feature.stembot.BotSession;
import dev.stemcraft.feature.stembot.BotActor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StemBotHelpTest {
    @TempDir Path tempDir;
    private ServerMock server;
    private PlayerMock player;
    private STEMCraftAPI api;
    private StemBotFeature feature;
    private ConfigFileImpl config;
    private Command command;
    private CommandExecutor executor;
    private Runnable tick;
    private Map<UUID, BotSession> sessions;
    private MockedStatic<STEMCraft> pluginAccess;

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        server=MockBukkit.mock();
        player=server.addPlayer();
        var eventOwner=MockBukkit.createMockPlugin();
        var plugin=mock(STEMCraft.class);
        when(plugin.getName()).thenReturn("STEMCraft");
        when(plugin.namespace()).thenReturn("stemcraft");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        pluginAccess=mockStatic(STEMCraft.class);
        pluginAccess.when(STEMCraft::getPlugin).thenReturn(plugin);
        api=mock(STEMCraftAPI.class,RETURNS_DEEP_STUBS);
        when(api.getDataFolder()).thenReturn(tempDir.toFile());
        config=new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(),"stembot.yml",true));
        config.set("actions.directions",List.of("say:Directions","end"));
        config.set("actions.mail-help",List.of("say:Mail","end"));
        config.set("help-topics.wilderness","directions");
        config.set("help-topics.mail","mail-help");
        when(api.messages().tokens().apply(anyString())).thenAnswer(call->call.getArgument(0));
        when(api.config().load(tempDir.resolve("stembot.yml").toFile())).thenReturn(config);

        var builder=mock(CommandBuilder.class,RETURNS_SELF);
        command=mock(Command.class);
        when(api.commands().create("stembot")).thenReturn(builder);
        when(builder.register(plugin)).thenReturn(command);
        var events=api.events();
        doAnswer(call->{
            Class<? extends Event> event=call.getArgument(0);
            EventHandler<Event> callback=call.getArgument(1);
            EventPriority priority=call.getArgument(2);
            Listener listener=new Listener() {};
            server.getPluginManager().registerEvent(event,listener,priority,
                (ignored,raw)->callback.handle(raw),eventOwner,call.getArgument(3));
            return listener;
        }).when(events).register(any(),any(),any(),anyBoolean());

        feature=spy(new StemBotFeature(api));
        doReturn(true).when(feature).available();
        var settings=new YamlConfiguration();
        settings.set("enabled",true);
        doReturn(new ConfigSectionImpl(null,settings)).when(feature).getConfigSection();
        var sessionsField=StemBotFeature.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        sessions=(Map<UUID,BotSession>)sessionsField.get(feature);
        doAnswer(call->{
            sessions.put(player.getUniqueId(),mock(BotSession.class));
            return null;
        }).when(feature).start(player,"directions",false);
        feature.onEnable();
        var executorCaptor=ArgumentCaptor.forClass(CommandExecutor.class);
        verify(builder).executor(executorCaptor.capture());
        executor=executorCaptor.getValue();
        var tickCaptor=ArgumentCaptor.forClass(Runnable.class);
        verify(api.tasks()).repeating(eq("feature:stembot"),eq(5L),eq(5L),tickCaptor.capture());
        tick=tickCaptor.getValue();
    }

    @AfterEach void cleanup() {
        if(feature!=null) feature.onDisable();
        if(pluginAccess!=null) pluginAccess.close();
        MockBukkit.unmock();
    }

    @Test void helpCompletionReplacesBukkitSuggestionsWithConfiguredTopics() {
        assertEquals(List.of("close","mail","wilderness"),complete(player,"/help ",true));
        assertEquals(List.of("wilderness"),complete(player,"/HELP WI",true));
        assertEquals(List.of("mail"),complete(player,"help m",true));
        assertEquals(List.of(),complete(player,"/help missing",true));
        assertEquals(List.of(),complete(player,"/help wilderness ",true));
    }

    @Test void completionTracksReloadedTopicsForHelpAndStemBot() {
        config.set("help-topics.mail",null);
        config.set("help-topics.quests","mail-help");
        clearInvocations(command);
        feature.onReload();
        assertEquals(List.of("close","quests","wilderness"),complete(player,"/help ",true));
        verify(command).clearTabCompletions();
        verify(command).addTabCompletion("quests");
        verify(command,never()).addTabCompletion("mail");
    }

    @Test void unrelatedConsoleAndCancelledCompletionsArePreserved() {
        assertEquals(List.of("original"),complete(player,"/helpful ",true));
        assertEquals(List.of("original"),complete(player,"/help ",false));
        assertEquals(List.of("original"),complete(server.getConsoleSender(),"help ",true));
        var cancelled=new TabCompleteEvent(player,"/help ",List.of("original"),true,null);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertEquals(List.of("original"),cancelled.getCompletions());
    }

    @Test void idleHelpStartsOnNextTickWithoutAWaitingMessage() {
        var context=request("wilderness");
        verify(context,never()).returnInfo(anyString());
        verify(context,never()).returnError(anyString());
        verify(feature,never()).start(player,"directions",false);
        tick.run();
        verify(feature).start(player,"directions",false);
        tick.run();
        verify(feature,times(1)).start(player,"directions",false);
    }

    @Test void combatWaitIsExplainedOnlyWhileTheCombatCooldownBlocksHelp() {
        var combatKey=new NamespacedKey("stemcraft","stembot-last-combat");
        player.getPersistentDataContainer().set(combatKey,PersistentDataType.LONG,System.currentTimeMillis());
        var context=request("wilderness");
        verify(context).returnInfo("I will help with wilderness once you are out of combat.");
        tick.run();
        verify(feature,never()).start(player,"directions",false);
        player.getPersistentDataContainer().remove(combatKey);
        tick.run();
        verify(feature).start(player,"directions",false);
    }

    @Test void aCurrentConversationQueuesHelpWithoutClaimingThePlayerIsInCombat() {
        var current=mock(BotSession.class);
        sessions.put(player.getUniqueId(),current);
        var context=request("wilderness");
        verify(context).returnInfo("I will help with wilderness when I finish what I am doing.");
        tick.run();
        verify(feature,never()).start(player,"directions",false);
        request("close");
        verify(current).close(true);
        tick.run();
        verify(feature).start(player,"directions",false);
    }

    @Test void noSpawnWildernessWorksWithoutCitizensAndRecalculatesOnEveryRequest() {
        configureNoSpawnWilderness();
        doReturn(false).when(feature).available();
        var survival=server.addSimpleWorld("survival");
        player.teleport(new org.bukkit.Location(survival,0,64,0));
        var context=request("wilderness");
        assertTrue(player.nextMessage().contains("south about 7985 blocks"));
        assertNull(player.nextMessage(),"Directions must not append a menu or farewell");
        verify(context,never()).returnInfo(anyString());
        verify(context,never()).returnError(anyString());
        verify(feature,never()).start(eq(player),anyString(),anyBoolean());
        assertFalse(feature.hasActiveSession(player.getUniqueId()));

        player.teleport(new org.bukkit.Location(survival,8031.5,64,0));
        request("wilderness");
        assertTrue(player.nextMessage().contains("east about 1 blocks"));
        assertNull(player.nextMessage());
    }

    @Test void noSpawnHelpLeavesCurrentGuideControlAndCombatStateAlone() {
        configureNoSpawnWilderness();
        player.teleport(new org.bukkit.Location(server.addSimpleWorld("survival"),0,64,0));
        var current=mock(BotSession.class);
        sessions.put(player.getUniqueId(),current);
        var combatKey=new NamespacedKey("stemcraft","stembot-last-combat");
        player.getPersistentDataContainer().set(combatKey,PersistentDataType.LONG,System.currentTimeMillis());
        var context=request("wilderness");
        assertTrue(player.nextMessage().contains("Head south"));
        assertSame(current,sessions.get(player.getUniqueId()));
        verify(current,never()).close(anyBoolean());
        verify(context,never()).returnInfo(anyString());
        assertNotNull(player.getPersistentDataContainer().get(combatKey,PersistentDataType.LONG));
    }

    @Test void noSpawnWrongWorldReplyDoesNotSummonAnActor() {
        configureNoSpawnWilderness();
        request("wilderness");
        assertEquals("Go to Survival",player.nextMessage());
        assertNull(player.nextMessage());
        verify(feature,never()).start(eq(player),anyString(),anyBoolean());
    }

    @Test void noSpawnDelayedRepliesKeepChatPublicAndReloadCancelsPendingText() {
        config.set("messages.prefix","");
        config.set("actions.directions",List.of("say:First","sleep:10","talk:Last","end"));
        config.set("help-topics.wilderness","no-spawn:directions");
        feature.onReload();
        doReturn(false).when(feature).available();
        request("wilderness");
        assertEquals("First",player.nextMessage());
        var chat=mock(io.papermc.paper.event.player.AsyncChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        feature.chat(chat);
        verify(chat,never()).setCancelled(anyBoolean());
        assertFalse(feature.isPrivateChat(chat));
        tick.run();
        assertNull(player.nextMessage());
        tick.run();
        assertEquals("Last",player.nextMessage());

        request("wilderness");
        assertEquals("First",player.nextMessage());
        feature.onReload();
        tick.run();
        tick.run();
        assertNull(player.nextMessage());
    }

    @Test void aLazyListenSpawnsTheActorAndCapturesRepliesInTheSameSession() {
        configureLazyTopic("say:First","listen:yes -> done");
        var actor=availableActor();
        request("wilderness");
        assertEquals("First",player.nextMessage());
        var session=sessions.get(player.getUniqueId());
        assertNotNull(session);
        assertSame(actor,session.actor());
        assertTrue(session.chatEngaged());
        var chat=mock(io.papermc.paper.event.player.AsyncChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        assertTrue(feature.isPrivateChat(chat));
        verify(feature).createActor(player,null);
        verify(actor).puff();

        session.input("yes");
        tick.run();
        assertEquals("Done",player.nextMessage());
        assertFalse(feature.hasActiveSession(player.getUniqueId()));
    }

    @Test void aLazyActorWaitsBehindTheCurrentGuideWithoutRepeatingMessagesOrTickingTwice() {
        configureLazyTopic("say:First","wave","end");
        var actor=availableActor();
        var current=mock(BotSession.class);
        sessions.put(player.getUniqueId(),current);
        request("wilderness");
        assertEquals("First",player.nextMessage());
        assertTrue(player.nextMessage().contains("after my current task finishes"));
        tick.run();
        assertNull(player.nextMessage());
        verify(feature,never()).createActor(player,null);
        verify(current,never()).close(anyBoolean());

        when(current.closed()).thenReturn(true);
        tick.run();

        assertSame(actor,sessions.get(player.getUniqueId()).actor());
        verify(feature).createActor(player,null);
        verify(actor,times(1)).lookAt(any());
        assertNull(player.nextMessage());
    }

    @Test void combatDefersOnlyTheActorStepAndAnnouncesTheWaitOnce() {
        configureLazyTopic("say:First","wave","end");
        var actor=availableActor();
        var combatKey=new NamespacedKey("stemcraft","stembot-last-combat");
        player.getPersistentDataContainer().set(combatKey,PersistentDataType.LONG,System.currentTimeMillis());
        request("wilderness");
        assertEquals("First",player.nextMessage());
        assertTrue(player.nextMessage().contains("once you are out of combat"));
        tick.run();
        tick.run();
        assertNull(player.nextMessage());
        verify(feature,never()).createActor(player,null);
        player.getPersistentDataContainer().remove(combatKey);
        tick.run();
        assertSame(actor,sessions.get(player.getUniqueId()).actor());
        verify(feature).createActor(player,null);
    }

    @Test void aMissingNpcServiceStopsOnlyTheInteractiveRequest() {
        configureLazyTopic("say:First","wave","say:After");
        doReturn(false).when(feature).available();
        request("wilderness");
        assertEquals("First",player.nextMessage());
        assertTrue(player.nextMessage().contains("not available right now"));
        tick.run();
        assertNull(player.nextMessage());
        assertFalse(feature.hasActiveSession(player.getUniqueId()));
        assertTrue(complete(player,"/help ",true).contains("wilderness"));
        verify(feature,never()).createActor(player,null);
    }

    @Test void anUnusableSpawnStopsTheRequestWithoutRetryingOrDisablingOtherTopics() {
        configureLazyTopic("say:First","wave","say:After");
        doReturn(null).when(feature).createActor(player,null);
        request("wilderness");
        assertEquals("First",player.nextMessage());
        tick.run();
        tick.run();
        verify(feature,times(1)).createActor(player,null);
        assertNull(player.nextMessage());
        assertFalse(feature.hasActiveSession(player.getUniqueId()));
        assertTrue(complete(player,"/help ",true).contains("mail"));
    }

    @Test void reloadCancelsAnInteractiveStepWaitingForTheCurrentGuide() {
        configureLazyTopic("say:First","wave","end");
        availableActor();
        sessions.put(player.getUniqueId(),mock(BotSession.class));
        request("wilderness");
        assertEquals("First",player.nextMessage());
        assertNotNull(player.nextMessage());
        feature.onReload();
        tick.run();
        verify(feature,never()).createActor(player,null);
        assertNull(player.nextMessage());
    }

    @Test void initialAppearanceFailuresDisposeOfTheActorAndSession() {
        var actor=availableActor();
        doThrow(new IllegalStateException("Appearance failed")).when(actor).puff();
        feature.start(player,"mail-help",false);
        verify(actor).close();
        assertFalse(feature.hasActiveSession(player.getUniqueId()));
        assertTrue(player.nextMessage().contains("could not join you"));
    }

    private void configureLazyTopic(String... steps) {
        config.set("messages.prefix","");
        config.set("speech.enabled",false);
        config.set("speech.post-delay-ticks",0);
        config.set("actions.directions",List.of(steps));
        config.set("actions.done",List.of("say:Done","end"));
        config.set("help-topics.wilderness","no-spawn:directions");
        feature.onReload();
    }

    private BotActor availableActor() {
        var actor=mock(BotActor.class);
        when(actor.valid()).thenReturn(true);
        when(actor.location()).thenAnswer(call->player.getLocation());
        doReturn(actor).when(feature).createActor(player,null);
        return actor;
    }

    private void configureNoSpawnWilderness() {
        config.set("messages.prefix","");
        config.set("actions.directions",List.of(
            "require-world:survival* normal -> wrong-world","exit-region:old-terrain"));
        config.set("actions.wrong-world",List.of("talk:Go to Survival","end"));
        config.set("regions.old-terrain.bounds","-7985,-8025;8023,7975");
        config.set("regions.old-terrain.align-to-chunks",true);
        config.set("help-topics.wilderness","no-spawn:directions");
        feature.onReload();
    }

    private List<String> complete(CommandSender sender,String buffer,boolean isCommand) {
        var event=new TabCompleteEvent(sender,buffer,List.of("original"),isCommand,null);
        server.getPluginManager().callEvent(event);
        return event.getCompletions();
    }

    private CommandContext request(String topic) {
        var context=mock(CommandContext.class);
        when(context.asPlayer()).thenReturn(player);
        when(context.getArg(0,"")).thenReturn(topic);
        executor.execute(api,command,context);
        return context;
    }
}
