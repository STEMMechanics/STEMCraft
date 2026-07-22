package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.message.MessageService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TargetedUtilityCommandTest {
    private ServerMock server;
    private STEMCraft plugin;
    private STEMCraftAPI api;
    private CommandSender console;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        console = server.getConsoleSender();
        plugin = mock(STEMCraft.class);
        api = mock(STEMCraftAPI.class);

        MessageService messages = mock(MessageService.class);
        LocaleService locales = mock(LocaleService.class);
        when(api.messages()).thenReturn(messages);
        when(api.locales()).thenReturn(locales);
        when(locales.resolve(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        InstanceHolder.set(api, plugin);
    }

    @AfterEach
    void tearDown() {
        InstanceHolder.set(null, null);
        MockBukkit.unmock();
    }

    @Test
    void clearInvUsesFirstArgumentForConsoleTargeting() {
        PlayerMock target = server.addPlayer("nomadjimbob");
        target.getInventory().addItem(new ItemStack(Material.STONE, 8));
        target.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));

        CommandContext ctx = mockConsoleTargetingContext("nomadjimbob", target, "stemcraft.command.clearinv.others");

        new ClearInvCommand(plugin, api).onExecute(mock(Command.class), ctx);

        assertTrue(target.getInventory().isEmpty());
        assertNull(target.getInventory().getHelmet());
    }

    @Test
    void flyUsesFirstArgumentForConsoleTargeting() {
        PlayerMock target = server.addPlayer("nomadjimbob");
        target.setGameMode(GameMode.SURVIVAL);
        target.setAllowFlight(false);

        CommandContext ctx = mockConsoleTargetingContext("nomadjimbob", target, "stemcraft.command.fly.others");
        Command cmd = mock(Command.class);
        when(cmd.getPermission()).thenReturn("stemcraft.command.fly");

        new FlyCommand(plugin, api).onExecute(cmd, ctx);

        assertTrue(target.getAllowFlight());
    }

    @Test
    void repairUsesFirstArgumentForConsoleTargeting() {
        PlayerMock target = server.addPlayer("nomadjimbob");
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(100);
        item.setItemMeta(meta);
        target.getInventory().setItemInMainHand(item);

        CommandContext ctx = mockConsoleTargetingContext("nomadjimbob", target, "stemcraft.command.repair.others");
        Command cmd = mock(Command.class);
        when(cmd.getPermission()).thenReturn("stemcraft.command.repair");

        new RepairCommand(plugin, api).onExecute(cmd, ctx);

        Damageable repairedMeta = (Damageable) target.getInventory().getItemInMainHand().getItemMeta();
        assertEquals(0, repairedMeta.getDamage());
    }

    @Test
    void workbenchUsesFirstArgumentForConsoleTargeting() {
        PlayerMock target = server.addPlayer("nomadjimbob");

        CommandContext ctx = mockConsoleTargetingContext("nomadjimbob", target, "stemcraft.command.workbench.others");
        when(ctx.getLabelUsed()).thenReturn("workbench");
        when(ctx.hasPermission("stemcraft.command.workbench")).thenReturn(true);

        new WorkbenchCommand(plugin, api).onExecute(mock(Command.class), ctx);

        assertEquals(InventoryType.WORKBENCH, target.getOpenInventory().getTopInventory().getType());
    }

    private CommandContext mockConsoleTargetingContext(String playerName, Player target, String othersPermission) {
        CommandContext ctx = mock(CommandContext.class);
        when(ctx.isConsole()).thenReturn(true);
        when(ctx.args()).thenReturn(List.of(playerName));
        when(ctx.getSender()).thenReturn(console);
        when(ctx.hasPermission(othersPermission)).thenReturn(true);
        when(ctx.getPlayer(0, console)).thenReturn(target);
        when(ctx.getArg(0)).thenReturn(playerName);
        return ctx;
    }
}
