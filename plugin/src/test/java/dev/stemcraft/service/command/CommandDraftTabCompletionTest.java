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

class CommandDraftTabCompletionTest {

    @Test
    void passesCompletedProviderContextAndActiveDraftSeparately() {
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
            AtomicReference<String> draft = new AtomicReference<>();
            tabComplete.register("draft", (sender, args, currentDraft) -> {
                context.set(List.of(args));
                draft.set(currentDraft);
                return List.of(currentDraft + "{entity}", currentDraft + "{causer}");
            });

            var command = (CommandImpl) new CommandBuilderImpl(api, "drafttabtest")
                .tabCompletion("message", "{draft:$0}")
                .register(commandPlugin);

            assertEquals(
                List.of("He{entity}", "He{causer}"),
                command.onTabComplete(
                    player,
                    command.getRegisteredBukkitCommand(),
                    "drafttabtest",
                    new String[]{"message", "He"}
                )
            );
            assertEquals(List.of("message"), context.get());
            assertEquals("He", draft.get());
        } finally {
            InstanceHolder.set(null);
            MockBukkit.unmock();
        }
    }
}
