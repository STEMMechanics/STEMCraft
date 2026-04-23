package dev.stemcraft.service.tabcompletion;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TabCompleteServiceImplTest {
    private ServerMock server;
    private TabCompleteServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("tab-tests");
        server.addSimpleWorld("extra-world");
        server.addPlayer("Viewer");
        server.addPlayer("Other");

        STEMCraft plugin = mock(STEMCraft.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        service = new TabCompleteServiceImpl(plugin, api);
        when(api.tabComplete()).thenReturn(service);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersCustomCompletionsAndReturnsEmptyListForUnknownProviders() {
        Player viewer = viewer();
        service.register("custom", (player, args) -> List.of("one", "two"));

        assertEquals(List.of("one", "two"), service.getCompletionList("custom", viewer));
        assertEquals(List.of(), service.getCompletionList("missing", viewer));
    }

    @Test
    void onEnableRegistersCoreTabCompletions() {
        Player viewer = viewer();
        service.onEnable();

        assertTrue(service.getCompletionList("duration", viewer).contains("1m"));
        assertTrue(service.getCompletionList("world", viewer).containsAll(List.of("tab-tests", "extra-world")));
        assertTrue(service.getCompletionList("gamemode", viewer).contains("survival"));
        assertTrue(service.getCompletionList("int", viewer).contains("100"));
        assertTrue(service.getCompletionList("player", viewer).containsAll(List.of("Viewer", "Other")));
    }

    private Player viewer() {
        return Objects.requireNonNull(server.getPlayer("Viewer"));
    }
}
