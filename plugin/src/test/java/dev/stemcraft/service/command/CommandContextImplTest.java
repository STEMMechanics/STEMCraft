package dev.stemcraft.service.command;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandException;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.locale.LocaleService;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandContextImplTest {
    private ServerMock server;
    private WorldMock world;
    private PlayerMock sender;
    private PlayerMock target;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("command-tests");
        sender = server.addPlayer("Sender");
        target = server.addPlayer("Target");

        STEMCraftAPI api = mock(STEMCraftAPI.class);
        LocaleService locales = mock(LocaleService.class, CALLS_REAL_METHODS);
        when(api.locales()).thenReturn(locales);
        when(locales.getDefaultLocale()).thenReturn("en-AU");
        when(locales.resolve(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        InstanceHolder.set(api, mock(org.bukkit.plugin.Plugin.class));
    }

    @AfterEach
    void tearDown() {
        InstanceHolder.set(null, null);
        MockBukkit.unmock();
    }

    @Test
    void parsesFlagsOptionsAndTypedArguments() {
        Command command = mock(Command.class);
        when(command.getLabel()).thenReturn("root");
        when(command.getUsage()).thenReturn("/root <player>");

        CommandContextImpl ctx = new CommandContextImpl(
            command,
            sender,
            "ROOT",
            List.of("Target", "-force", "mode:hard", "yes", "5", "1.5", "2.5", world.getName(), "5m")
        );

        assertEquals("root", ctx.getLabel());
        assertEquals("root", ctx.getLabelUsed());
        assertEquals(List.of("Target", "-force", "mode:hard", "yes", "5", "1.5", "2.5", world.getName(), "5m"), ctx.rawArgs());
        assertEquals(List.of("Target", "yes", "5", "1.5", "2.5", world.getName(), "5m"), ctx.args());
        assertEquals(7, ctx.numArgs());
        assertTrue(ctx.hasFlag("force"));
        assertTrue(ctx.hasFlag("-force"));
        assertFalse(ctx.hasFlag("missing"));
        assertEquals("hard", ctx.getOption("MODE", "none"));
        assertEquals("fallback", ctx.getOption("missing", "fallback"));
        assertEquals("Target", ctx.getArg(0));
        assertEquals("5m", ctx.getArg(-1, null));
        assertEquals("yes 5 1.5 2.5 command-tests 5m", ctx.getArgsAsString(2, ""));
        assertEquals("Target yes 5 1.5 2.5 command-tests 5m", ctx.getArgsAsString());
        assertTrue(ctx.getArgAsBoolean(1));
        assertEquals(5, ctx.getArgAsInt(2, 0, 1, 10));
        assertEquals(2.0f, ctx.getArgAsFloat(3, 0.0f, 2.0f, 3.0f));
        assertEquals(2.5d, ctx.getArgAsDouble(4, 0.0d, 1.0d, 3.0d));
        assertSame(target, ctx.getPlayer(0, (CommandSender) null));
        assertSame(target, ctx.getArgAsPlayerOrSender(0));
        assertEquals(Duration.ofMinutes(5), ctx.getArgAsDuration(6));
        assertSame(world, ctx.getArgAsWorld(5, null));
        assertTrue(ctx.isPlayer());
        assertFalse(ctx.isConsole());
        assertSame(sender, ctx.asPlayer());
        assertTrue(ctx.equalsPlayer(sender));
        assertEquals("Sender", ctx.getSenderName());

        ctx.dropArg();
        assertEquals(List.of("yes", "5", "1.5", "2.5", world.getName(), "5m"), ctx.args());
    }

    @Test
    void checkMethodsAndReturnMethodsUseCommandExceptions() {
        Command command = mock(Command.class);
        when(command.getUsage()).thenReturn("/root <player>");

        CommandContextImpl empty = new CommandContextImpl(command, sender, "root", List.of());
        CommandException missingArgs = assertThrows(CommandException.class, () -> empty.checkArgsNotEmpty(""));
        assertEquals("Usage: /root <player>", missingArgs.getMessage());

        CommandContextImpl consoleCtx = new CommandContextImpl(command, server.getConsoleSender(), "root", List.of());
        CommandException consoleOnly = assertThrows(CommandException.class, () -> consoleCtx.checkNotConsole(""));
        assertEquals("COMMAND_COMMAND_PLAYER_ONLY", consoleOnly.getMessage());

        CommandContextImpl invalidWorld = new CommandContextImpl(command, sender, "root", List.of("missing-world"));
        CommandException invalidWorldEx = assertThrows(CommandException.class, () -> invalidWorld.checkArgIsWorld(0, ""));
        assertEquals("COMMAND_ARGUMENT_INVALID_WORLD", invalidWorldEx.getMessage());

        CommandContextImpl invalidPlayer = new CommandContextImpl(command, sender, "root", List.of("missing-player"));
        CommandException invalidPlayerEx = assertThrows(CommandException.class, () -> invalidPlayer.checkArgIsPlayer(0, ""));
        assertEquals("COMMAND_ARGUMENT_INVALID_PLAYER", invalidPlayerEx.getMessage());

        CommandContextImpl invalidDuration = new CommandContextImpl(command, sender, "root", List.of("oops"));
        assertNull(invalidDuration.getArgAsDuration(0, null));

        doAnswer(invocation -> null).when(command).info(sender, "INFO_KEY", "name", "Alex");
        CommandException returned = assertThrows(CommandException.class, () -> empty.returnInfo("INFO_KEY", "name", "Alex"));
        assertEquals("", returned.getMessage());
        verify(command).info(sender, "INFO_KEY", "name", "Alex");
    }

    @Test
    void dispatchThrowsWhenCommandCannotBeFound() {
        Command command = mock(Command.class);
        CommandContextImpl ctx = new CommandContextImpl(command, sender, "root", List.of());

        CommandException exception = assertThrows(CommandException.class, () -> ctx.dispatch("missing-command", List.of("arg")));
        assertEquals("COMMAND_NOT_FOUND", exception.getMessage());
    }

    @Test
    void resolvesSenderNameToServerForConsole() {
        Command command = mock(Command.class);
        CommandContextImpl ctx = new CommandContextImpl(command, server.getConsoleSender(), "root", List.of());

        assertEquals("SERVER", ctx.getSenderName());
        assertFalse(ctx.isPlayer());
        assertTrue(ctx.isConsole());
        assertNotNull(ctx.getSender());
    }
}
