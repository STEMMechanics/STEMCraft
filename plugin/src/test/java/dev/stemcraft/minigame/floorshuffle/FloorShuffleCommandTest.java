package dev.stemcraft.minigame.floorshuffle;

import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.Mockito.*;

class FloorShuffleCommandTest {
    @Test
    void listReportsWhenNoArenasAreLoaded() throws Exception {
        FloorShuffleMiniGame owner = new FloorShuffleMiniGame(null);
        MiniGame game = mock(MiniGame.class);
        when(game.arenas()).thenReturn(List.of());
        owner.game = game;

        CommandContext context = mock(CommandContext.class);
        when(context.numArgs()).thenReturn(1);
        when(context.getArgLower(0)).thenReturn("list");

        Method execute = FloorShuffleCommand.class.getDeclaredMethod("execute", CommandContext.class);
        execute.setAccessible(true);
        execute.invoke(new FloorShuffleCommand(null, owner), context);

        verify(context).info("No Floor Shuffle arenas are loaded.");
    }
}
