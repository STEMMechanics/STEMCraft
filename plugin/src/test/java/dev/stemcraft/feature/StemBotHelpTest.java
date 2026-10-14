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
