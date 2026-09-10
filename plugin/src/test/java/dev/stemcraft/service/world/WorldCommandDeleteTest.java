package dev.stemcraft.service.world;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.task.TaskRetryCallback;
import dev.stemcraft.api.service.task.TaskRetryable;
import dev.stemcraft.api.service.task.TaskService;
import dev.stemcraft.api.service.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldCommandDeleteTest {
    private STEMCraftAPI api;
    private WorldService worlds;
    private CommandContext ctx;
    private WorldCommand command;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach void setup() {
        bukkit = mockStatic(Bukkit.class);
        api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        worlds = api.worlds();
        ctx = mock(CommandContext.class);
        when(ctx.getArg(1)).thenReturn("survival_deep");
        when(worlds.worldExists("survival_deep")).thenReturn(true);
        command = new WorldCommand(api, mock(WorldServiceImpl.class));
    }
    @AfterEach void cleanup() { bukkit.close(); }

    @Test void deletesExistingUnloadedWorldWithoutLoadingOrResolvingItsGenerator() throws Exception {
        command.handleSubCommandDelete(ctx);

        verify(worlds).deleteWorld("survival_deep");
        verify(worlds, never()).loadWorld(anyString());
        verify(worlds, never()).generator();
        verify(worlds, never()).unloadWorld(anyString(), anyBoolean());
        verify(ctx).info("WORLD_DELETED", "world", "survival_deep");
    }

    @Test void loadedWorldIsEvictedAndUnloadedBeforeDeletion() throws Exception {
        World world = mock(World.class);
        when(world.getPlayers()).thenReturn(List.of(mock(Player.class)));
        bukkit.when(() -> Bukkit.getWorld("survival_deep")).thenReturn(world);
        when(worlds.unloadWorld("survival_deep", false)).thenReturn(true);

        command.handleSubCommandDelete(ctx);

        verify(worlds).evictAllPlayers("survival_deep");
        verify(worlds, never()).deleteWorld(anyString());
        var task = ArgumentCaptor.forClass(TaskRetryable.class);
        var callback = ArgumentCaptor.forClass(TaskRetryCallback.class);
        verify(api.tasks()).retry(eq(20), task.capture(), callback.capture());
        assertTrue(task.getValue().run());
        callback.getValue().done(TaskService.RetryResult.SUCCESS);
        var order = inOrder(worlds);
        order.verify(worlds).unloadWorld("survival_deep", false);
        order.verify(worlds).deleteWorld("survival_deep");
    }

    @Test void failedUnloadDoesNotDeleteTheWorld() throws Exception {
        World world = mock(World.class);
        when(world.getPlayers()).thenReturn(List.of());
        bukkit.when(() -> Bukkit.getWorld("survival_deep")).thenReturn(world);
        command.handleSubCommandDelete(ctx);
        var callback = ArgumentCaptor.forClass(TaskRetryCallback.class);
        verify(api.tasks()).retry(eq(20), any(TaskRetryable.class), callback.capture());
        callback.getValue().done(TaskService.RetryResult.EXHAUSTED);
        verify(worlds, never()).deleteWorld(anyString());
        verify(ctx).error("WORLD_UNLOAD_FAILED", "world", "survival_deep");
    }

    @Test void defaultWorldRemainsProtected() throws Exception {
        World world = mock(World.class);
        bukkit.when(() -> Bukkit.getWorld("survival_deep")).thenReturn(world);
        when(worlds.getDefaultWorld()).thenReturn(world);
        doThrow(new IllegalStateException("command stopped")).when(ctx)
                .returnError("WORLD_DELETE_DEFAULT_DENY", "world", "survival_deep");
        assertThrows(IllegalStateException.class, () -> command.handleSubCommandDelete(ctx));
        verify(worlds, never()).deleteWorld(anyString());
    }

    @Test void genuinelyMissingWorldStillReportsNotFound() throws Exception {
        when(worlds.worldExists("survival_deep")).thenReturn(false);
        doThrow(new IllegalStateException("command stopped")).when(ctx)
                .returnError("WORLD_NOT_FOUND", "world", "survival_deep");
        assertThrows(IllegalStateException.class, () -> command.handleSubCommandDelete(ctx));
        verify(worlds, never()).deleteWorld(anyString());
    }

    @Test void deletionFailureDoesNotReportSuccess() throws Exception {
        doThrow(new IOException("unwritable directory")).when(worlds).deleteWorld("survival_deep");
        command.handleSubCommandDelete(ctx);
        verify(ctx).error("WORLD_FAILED_DELETE", "world", "survival_deep");
        verify(ctx, never()).info("WORLD_DELETED", "world", "survival_deep");
    }
}
