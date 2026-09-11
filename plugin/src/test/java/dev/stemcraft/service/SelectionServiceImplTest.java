package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import org.bukkit.GameMode;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectionServiceImplTest {
    private STEMCraft plugin;
    private STEMCraftAPI api;
    private SelectionServiceImpl service;
    private PlayerMock player;
    private PlayerMock other;

    @BeforeEach void setup() {
        var server=MockBukkit.mock();
        player=server.addPlayer();other=server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);
        plugin=mock(STEMCraft.class);
        when(plugin.namespace()).thenReturn("stemcraft");
        api=mock(STEMCraftAPI.class);
        service=spy(new SelectionServiceImpl(plugin,api));
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void defaultsOnAndSavesOnlyThePlayersPreference() {
        assertTrue(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command("off"));
        assertFalse(service.isWorldEditPreviewEnabled(player));
        assertTrue(service.isWorldEditPreviewEnabled(other));
        assertFalse(new SelectionServiceImpl(plugin,api).isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command("off"));
        assertFalse(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command("ON"));
        assertTrue(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command(""));
        assertFalse(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command(""));
        assertTrue(service.isWorldEditPreviewEnabled(player));
    }

    @Test void invalidArgumentsDoNotChangePreference() {
        var invalid=command("maybe");
        service.commandSelectionPreview(invalid);
        verify(invalid).returnUsage();
        assertTrue(service.isWorldEditPreviewEnabled(player));
        var extra=command("off");
        when(extra.args()).thenReturn(List.of("off","other-player"));
        service.commandSelectionPreview(extra);
        verify(extra).returnUsage();
        assertTrue(service.isWorldEditPreviewEnabled(player));
    }

    @Test void hiddenPreviewSkipsBothWorldEditLookupsAndLeavesOtherPlayersEnabled() throws Exception {
        service.commandSelectionPreview(command("off"));
        doReturn(null).when(service).getWorldEditPreviewSelection(other);
        doReturn(null).when(service).getWorldEditPrimaryPosition(other);
        var render=SelectionServiceImpl.class.getDeclaredMethod("renderWorldEditSelections");
        render.setAccessible(true);
        render.invoke(service);
        verify(service,never()).getWorldEditPreviewSelection(player);
        verify(service,never()).getWorldEditPrimaryPosition(player);
        verify(service).getWorldEditPreviewSelection(other);
        verify(service).getWorldEditPrimaryPosition(other);
        verify(service,never()).clearWorldEditSelection(any());
    }

    private CommandContext command(String argument) {
        var ctx=mock(CommandContext.class);
        when(ctx.args()).thenReturn(argument.isEmpty()?List.of():List.of(argument));
        when(ctx.getArg(0, "")).thenReturn(argument);
        when(ctx.asPlayer()).thenReturn(player);
        return ctx;
    }
}
