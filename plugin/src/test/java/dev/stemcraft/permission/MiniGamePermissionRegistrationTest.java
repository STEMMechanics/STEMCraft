package dev.stemcraft.permission;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiniGamePermissionRegistrationTest {
    @ParameterizedTest
    @CsvSource({"bedwars,BedWars", "bridge,Bridge", "boatrace,BoatRace", "minefield,Minefield",
        "nightfall,Nightfall", "parkour,Parkour", "tntrun,TntRun", "skyblock,SkyBlock"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void actualCommandRegistrationAcceptsPlayOnlyAndDeniesDelete(String game, String name) throws Exception {
        var api=mock(STEMCraftAPI.class,RETURNS_DEEP_STUBS);
        var builder=mock(CommandBuilder.class,RETURNS_SELF);
        when(api.commands().create(game)).thenReturn(builder);
        var server = org.mockbukkit.mockbukkit.MockBukkit.mock();
        var plugin = mock(STEMCraft.class);
        when(plugin.getServer()).thenReturn(server);
        try(var pluginStatic=mockStatic(STEMCraft.class)) {
            pluginStatic.when(STEMCraft::getPlugin).thenReturn(plugin);
            Class<?> gameClass=Class.forName("dev.stemcraft.minigame."+game+"."+name+"MiniGame");
            Class<?> commandClass=Class.forName("dev.stemcraft.minigame."+game+"."+name+"Command");
            Object command=commandClass.getConstructor(STEMCraftAPI.class,gameClass).newInstance(api,mock(gameClass));
            commandClass.getMethod("onEnable").invoke(command);
            ArgumentCaptor<BiPredicate<CommandSender,List<String>>> policy=ArgumentCaptor.forClass(BiPredicate.class);
            verify(builder).access(policy.capture());
            verify(builder,never()).permission(anyString());
            var player=mock(Player.class);
            when(player.hasPermission("stemcraft.minigame."+game+".play")).thenReturn(true);
            assertTrue(policy.getValue().test(player,game.equals("skyblock")?List.of("join"):List.of("join","arena")));
            assertFalse(policy.getValue().test(player,List.of("delete","arena")));
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }
}
