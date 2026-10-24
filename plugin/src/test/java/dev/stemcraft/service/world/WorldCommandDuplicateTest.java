package dev.stemcraft.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.command.CommandException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldCommandDuplicateTest {
    @Test
    void successCommandExitIsNotReportedAsDuplicationFailure() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        CommandContext ctx = context();
        var worlds = api.worlds();
        CommandException exit = new CommandException();
        doThrow(exit).when(ctx).returnInfo("WORLD_DUPLICATED", "source", "floorshuffle", "target", "copy");
        WorldCommand command = new WorldCommand(api, mock(WorldServiceImpl.class));

        assertSame(exit, assertThrows(CommandException.class, () -> command.handleSubCommandDuplicate(ctx)));

        verify(worlds).duplicateWorld("floorshuffle", "copy");
        verifyNoInteractions(api.messages());
        verify(ctx, never()).returnError("WORLD_FAILED_DUPLICATE", "world", "floorshuffle");
    }

    @Test
    void actualCopyFailureReportsErrorWithoutSuccess() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        CommandContext ctx = context();
        var worlds = api.worlds();
        IOException failure = new IOException("Destination already exists");
        doThrow(failure).when(worlds).duplicateWorld("floorshuffle", "copy");
        WorldCommand command = new WorldCommand(api, mock(WorldServiceImpl.class));

        command.handleSubCommandDuplicate(ctx);

        verify(api.messages()).error("WORLD_FAILED_DUPLICATE", failure, "world", "floorshuffle");
        verify(ctx).returnError("WORLD_FAILED_DUPLICATE", "world", "floorshuffle");
        verify(ctx, never()).returnInfo("WORLD_DUPLICATED", "source", "floorshuffle", "target", "copy");
    }

    private CommandContext context() {
        CommandContext ctx = mock(CommandContext.class);
        when(ctx.getArg(1)).thenReturn("floorshuffle");
        when(ctx.getArg(2)).thenReturn("copy");
        return ctx;
    }
}
