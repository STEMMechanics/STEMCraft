package dev.stemcraft.permission;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.skyblock.SkyBlockCommand;
import dev.stemcraft.minigame.skyblock.SkyBlockMiniGame;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class SkyBlockResetPermissionTest {
    @Test void resetRequiresOwnershipAndExplicitConfirmation() throws Exception {
        var game=mock(SkyBlockMiniGame.class,RETURNS_DEEP_STUBS);
        var arena=mock(MiniGameArena.class);when(arena.id()).thenReturn("island");
        when(game.minigame().arena("island")).thenReturn(arena);
        var player=mock(Player.class);var ctx=mock(CommandContext.class);
        when(ctx.numArgs()).thenReturn(3);when(ctx.getArg(1)).thenReturn("island");
        when(ctx.asPlayer()).thenReturn(player);when(ctx.getSender()).thenReturn(player);
        when(ctx.getArg(2, "")).thenReturn("confirm");
        var command=new SkyBlockCommand(mock(STEMCraftAPI.class),game);
        var reset=SkyBlockCommand.class.getDeclaredMethod("commandReset",CommandContext.class);reset.setAccessible(true);
        reset.invoke(command,ctx);
        verify(game,never()).endGame(any(),anyString());
        when(game.isOwner(arena,player)).thenReturn(true);
        when(ctx.getArg(2, "")).thenReturn("");
        reset.invoke(command,ctx);
        verify(game,never()).endGame(any(),anyString());
        when(ctx.getArg(2, "")).thenReturn("confirm");
        reset.invoke(command,ctx);
        verify(game).endGame(arena,"Your SkyBlock island was reset.");
    }
}
