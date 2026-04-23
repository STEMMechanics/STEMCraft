package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.database.DatabaseService;
import dev.stemcraft.api.service.task.TaskService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("UnstableApiUsage")
class WebhookBridgeServiceImplTest {
    private static final String WHITELIST_KICK_MESSAGE = "Webhook whitelist required.";

    private ServerMock server;
    private DatabaseService database;
    private TaskService tasks;
    private WebhookBridgeServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        STEMCraft plugin = mock(STEMCraft.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("WebhookBridgeServiceImplTest"));

        database = mock(DatabaseService.class);
        when(api.database()).thenReturn(database);
        when(database.querySingleMapped(any(), any(), any())).thenReturn(null);

        tasks = mock(TaskService.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(tasks).runLater(eq(1L), any(Runnable.class));
        when(api.tasks()).thenReturn(tasks);

        ConfigSection serviceConfig = mock(ConfigSection.class);
        when(serviceConfig.getBoolean("enabled", false)).thenReturn(true);
        when(serviceConfig.getBoolean("enforce_account_whitelist", true)).thenReturn(true);
        when(serviceConfig.getString("server_name", server.getName())).thenReturn("Test Server");
        when(serviceConfig.getString("site_webhook_url", "")).thenReturn("");
        when(serviceConfig.getString("shared_secret", "")).thenReturn("");

        ConfigFile rootConfig = mock(ConfigFile.class);
        when(rootConfig.getString("whitelist_message", "You are not whitelisted on this server."))
            .thenReturn(WHITELIST_KICK_MESSAGE);

        service = spy(new WebhookBridgeServiceImpl(plugin, api));
        doReturn(serviceConfig).when(service).getConfigSection();
        doReturn(rootConfig).when(service).getRootConfigSection();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nonWhitelistedPlayersAreRejectedBeforeLoginWhenWebhookWhitelistIsEnabled() throws Exception {
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
            "Alex",
            InetAddress.getLoopbackAddress(),
            java.util.UUID.randomUUID(),
            false
        );

        invokeHandler("onAsyncPlayerPreLogin", AsyncPlayerPreLoginEvent.class, event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, event.getLoginResult());
        assertEquals(Component.text(WHITELIST_KICK_MESSAGE), event.kickMessage());
    }

    @Test
    void nonWhitelistedPlayersAreKickedOnJoinWhenWebhookWhitelistIsEnabled() throws Exception {
        PlayerMock player = server.addPlayer("Alex");

        invokeHandler("onPlayerJoin", PlayerJoinEvent.class, new PlayerJoinEvent(player, Component.empty()));

        assertFalse(player.isOnline());
        verify(tasks).runLater(eq(1L), any(Runnable.class));
    }

    @Test
    void whitelistedPlayersAreNotKickedOnJoinWhenWebhookWhitelistIsEnabled() throws Exception {
        when(database.querySingleMapped(any(), any(), any())).thenReturn(1);

        PlayerMock player = server.addPlayer("Alex");

        invokeHandler("onPlayerJoin", PlayerJoinEvent.class, new PlayerJoinEvent(player, Component.empty()));

        assertTrue(player.isOnline());
        verify(tasks, never()).runLater(eq(1L), any(Runnable.class));
        verify(database).update(eq("DELETE FROM webhook_accounts"), eq(null));
    }

    private void invokeHandler(String methodName, Class<?> eventType, Object event) throws Exception {
        Method method = WebhookBridgeServiceImpl.class.getDeclaredMethod(methodName, eventType);
        method.setAccessible(true);
        method.invoke(service, event);
    }
}
