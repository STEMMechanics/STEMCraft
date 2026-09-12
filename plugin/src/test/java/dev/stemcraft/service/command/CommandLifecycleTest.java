package dev.stemcraft.service.command;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandLifecycleTest {
    @Test void removesEveryOwnedLabelWithoutMutatingTheEntryIterator() {
        Map<String, Command> entries = new HashMap<>() {
            @Override public Set<Map.Entry<String, Command>> entrySet() {
                return Collections.unmodifiableSet(super.entrySet());
            }
        };
        Command owned = mock(Command.class);
        Command other = mock(Command.class);
        entries.put("noticeboard", owned);
        entries.put("stemcraft:noticeboard", owned);
        entries.put("nb", owned);
        entries.put("stemcraft:nb", owned);
        entries.put("another-plugin", other);
        CommandMap commandMap = mock(CommandMap.class);
        when(commandMap.getKnownCommands()).thenReturn(entries);

        CommandImpl.removeKnownCommands(commandMap, owned);

        assertEquals(Map.of("another-plugin", other), entries);
        CommandImpl.removeKnownCommands(commandMap, owned);
        assertEquals(1, entries.size());
    }

    @Test void repeatedRegistrationAndUnregistrationLeaveNoStaleCommands() {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var api = mock(STEMCraftAPI.class);
            when(api.messages()).thenReturn(mock(MessageService.class));
            InstanceHolder.set(api);
            var command = (CommandImpl) new CommandBuilderImpl(api, "noticeboardtest")
                .register(plugin);
            var first = command.getRegisteredBukkitCommand();
            command.register(plugin);
            var second = command.getRegisteredBukkitCommand();
            assertNotSame(first, second);
            assertFalse(server.getCommandMap().getKnownCommands().containsValue(first));
            assertSame(second, server.getCommandMap().getCommand("noticeboardtest"));
            assertSame(second, server.getCommandMap().getCommand("stemcraft:noticeboardtest"));

            command.unregister();
            command.unregister();
            assertNull(command.getRegisteredBukkitCommand());
            assertFalse(server.getCommandMap().getKnownCommands().containsValue(second));
        } finally {
            InstanceHolder.set(null);
            MockBukkit.unmock();
        }
    }
}
