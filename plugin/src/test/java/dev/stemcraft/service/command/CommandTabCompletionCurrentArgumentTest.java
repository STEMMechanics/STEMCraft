package dev.stemcraft.service.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.service.tabcompletion.TabCompleteServiceImpl;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandTabCompletionCurrentArgumentTest {

    @Test
    void passesCompletedProviderContextAndCurrentArgumentSeparately() {
        var server = MockBukkit.mock();
        try {
            var commandPlugin = MockBukkit.createMockPlugin();
            var plugin = mock(STEMCraft.class);
            var api = mock(STEMCraftAPI.class);
            var tabComplete = new TabCompleteServiceImpl(plugin, api);
            when(api.tabComplete()).thenReturn(tabComplete);
            InstanceHolder.set(api);

            Player player = server.addPlayer("Alice");
            AtomicReference<List<String>> context = new AtomicReference<>();
            AtomicReference<String> currentArgument = new AtomicReference<>();
            tabComplete.register("current-argument", (sender, args, currentArgumentValue) -> {
                context.set(List.of(args));
                currentArgument.set(currentArgumentValue);
                return List.of(currentArgumentValue + "{entity}", currentArgumentValue + "{causer}");
            });

            var command = (CommandImpl) new CommandBuilderImpl(api, "currentargumenttest")
                .tabCompletion("message", "{current-argument:$0}")
                .register(commandPlugin);

            assertEquals(
                List.of("He{entity}", "He{causer}"),
                command.onTabComplete(
                    player,
                    command.getRegisteredBukkitCommand(),
                    "currentargumenttest",
                    new String[]{"message", "He"}
                )
            );
            assertEquals(List.of("message"), context.get());
            assertEquals("He", currentArgument.get());
        } finally {
            InstanceHolder.set(null);
            MockBukkit.unmock();
        }
    }
}
