package dev.stemcraft.service.resourcepack;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.service.command.CommandBuilderImpl;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourcePackCommandTest {
    private static final String PACK_URL = "https://packs.example.test/resource-pack.zip?token=a%2Bb&v=12345";
    private ServerMock server;
    private PlayerMock sender;
    private ResourcePackService service;
    private Command command;
    private MockedStatic<STEMCraft> pluginAccess;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        server.addSimpleWorld("pack-tests");
        sender = server.addPlayer("Alice");
        sender.setOp(true);
        server.addPlayer("Bob");

        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getName()).thenReturn("STEMCraft");
        when(plugin.isEnabled()).thenReturn(true);
        pluginAccess = mockStatic(STEMCraft.class);
        pluginAccess.when(STEMCraft::getPlugin).thenReturn(plugin);

        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        InstanceHolder.set(api);
        when(api.commands().create(anyString())).thenAnswer(call -> new CommandBuilderImpl(api, call.getArgument(0)));
        TabCompleteServiceImpl completions = new TabCompleteServiceImpl(plugin, api);
        when(api.tabComplete()).thenReturn(completions);
        completions.onEnable();

        service = mock(ResourcePackService.class, RETURNS_DEEP_STUBS);
        when(service.host().getUrl()).thenReturn(PACK_URL);
        new ResourcePackCommand(api, service).onEnable();
        command = server.getCommandMap().getCommand("resourcepack");
        assertNotNull(command);
    }

    @AfterEach
    void cleanup() {
        if (pluginAccess != null) pluginAccess.close();
        InstanceHolder.set(null);
        MockBukkit.unmock();
    }

    @Test
    void urlIsClickableAndPreservesTheCurrentAddressWithoutSendingOrBuilding() {
        assertTrue(server.dispatchCommand(sender, "resourcepack url"));

        Component message = sender.nextComponentMessage();
        assertNotNull(message);
        assertEquals(PACK_URL, PlainTextComponentSerializer.plainText().serialize(message));
        assertEquals(ClickEvent.openUrl(PACK_URL), message.clickEvent());
        verify(service, never()).sendPack(any());
        verify(service, never()).sendPackToAll();
        verify(service, never()).generatePack(any());
    }

    @Test
    void aliasSupportsTheDownloadUrl() {
        assertTrue(server.dispatchCommand(sender, "respack url"));

        Component message = sender.nextComponentMessage();
        assertNotNull(message);
        assertEquals(ClickEvent.openUrl(PACK_URL), message.clickEvent());
    }

    @Test
    void consoleCanReadTheExactDownloadAddress() {
        var console = server.getConsoleSender();
        assertTrue(server.dispatchCommand(console, "resourcepack url"));

        assertEquals(PACK_URL, console.nextMessage());
    }

    @Test
    void rootCompletionOffersSubcommandsWithoutPlayerNames() {
        assertEquals(Set.of("info", "send", "sendall", "url", "zip"),
                Set.copyOf(command.tabComplete(sender, "resourcepack", new String[]{""})));
        assertTrue(command.tabComplete(sender, "resourcepack", new String[]{"url", ""}).isEmpty());
    }

    @Test
    void sendCompletionOffersMatchingOnlinePlayers() {
        assertEquals(Set.of("Alice", "Bob"),
                Set.copyOf(command.tabComplete(sender, "resourcepack", new String[]{"send", ""})));
        assertEquals(Set.of("Bob"),
                Set.copyOf(command.tabComplete(sender, "resourcepack", new String[]{"send", "Bo"})));
    }
}
