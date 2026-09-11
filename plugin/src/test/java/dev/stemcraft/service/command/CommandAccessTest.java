package dev.stemcraft.service.command;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.permission.PlayerCommandAccess;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandAccessTest {
    @Test void registeredExecutorAndTabCompletionBothEnforcePolicy() {
        var server=MockBukkit.mock();
        try {
            var plugin=MockBukkit.createMockPlugin();
            var api=mock(STEMCraftAPI.class);when(api.messages()).thenReturn(mock(MessageService.class));InstanceHolder.set(api);
            var player=server.addPlayer("Alice");player.setOp(false);
            player.addAttachment(plugin,"stemcraft.minigame.nightfall.play",true);
            var executions=new AtomicInteger();
            var command=(CommandImpl)new CommandBuilderImpl(api,"nightfalltest")
                .access((sender,args)->PlayerCommandAccess.minigame(sender,args,"nightfall"))
                .tabCompletion("join").tabCompletion("delete").tabCompletion("reload")
                .executor((unused,cmd,ctx)->executions.incrementAndGet()).register(plugin);
            var registered=command.getRegisteredBukkitCommand();
            registered.execute(player,"nightfalltest",new String[]{"delete","arena"});
            registered.execute(player,"nightfalltest",new String[]{"join","arena","Bob"});
            assertEquals(0,executions.get());
            registered.execute(player,"nightfalltest",new String[]{"join","arena"});
            assertEquals(1,executions.get());
            assertEquals(List.of("join"),command.onTabComplete(player,registered,"nightfalltest",new String[]{""}));
            player.addAttachment(plugin,"stemcraft.minigame.nightfall.admin",true);
            registered.execute(player,"nightfalltest",new String[]{"delete","arena"});
            assertEquals(2,executions.get());
            assertTrue(command.onTabComplete(player,registered,"nightfalltest",new String[]{""}).contains("delete"));
        } finally { InstanceHolder.set(null);MockBukkit.unmock(); }
    }

}
