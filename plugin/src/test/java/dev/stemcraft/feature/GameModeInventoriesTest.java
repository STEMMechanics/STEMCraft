package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameModeInventoriesTest {
    private GameModeInventories feature(YamlConfiguration yaml) {
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        ConfigFile config = mock(ConfigFile.class);
        when(config.getString(anyString(), anyString())).thenAnswer(invocation ->
            yaml.getString(invocation.getArgument(0), invocation.getArgument(1)));
        when(api.config().load("config.yml")).thenReturn(config);
        return new GameModeInventories(api);
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    @Test
    void bundledConfigSharesExistingAndFutureSurvivalWorlds() {
        var stream = getClass().getResourceAsStream("/config.yml");
        assertNotNull(stream);
        var feature = feature(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        for (String name : new String[]{"survival", "survival_nether", "survival_the_end", "survival_deep", "survival_future_dimension"}) {
            assertEquals("survival", feature.worldGroup(world(name)));
        }
        assertEquals("creative", feature.worldGroup(world("creative")));
        assertEquals("survivalist", feature.worldGroup(world("survivalist")));
        assertEquals("bridge", feature.worldGroup(world("bridge_amazon")));
        assertEquals("bridge", feature.worldGroup(world("bridge_western")));
    }

    @Test
    void worldOverridesMoveOnlyConfiguredWorldsIntoSharedGroup() {
        var yaml = new YamlConfiguration();
        yaml.set("worlds.survival_nether.inventory-group", "nether");
        yaml.set("worlds.bridge_western.inventory-group", "nether");
        var feature = feature(yaml);
        assertEquals("survival", feature.worldGroup(world("survival")));
        assertEquals("survival", feature.worldGroup(world("survival_deep")));
        assertEquals("hub", feature.worldGroup(world("hub")));
        assertEquals("bridge", feature.worldGroup(world("bridge_amazon")));
        assertEquals("nether", feature.worldGroup(world("survival_nether")));
        assertEquals("nether", feature.worldGroup(world("bridge_western")));
    }

    @Test
    void defaultsUseFirstPrefixAndNeverProduceEmptyGroup() {
        var feature = feature(new YamlConfiguration());
        assertEquals("survival", feature.worldGroup(world("survival_deep_caves")));
        assertEquals("hub", feature.worldGroup(world("hub")));
        assertEquals("_hidden", feature.worldGroup(world("_hidden")));
        assertEquals("Survival", feature.worldGroup(world("Survival_sky")));
    }

    @Test
    void minigameParticipantsSkipInventorySwitching() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class);
        var game = mock(dev.stemcraft.api.minigame.MiniGame.class);
        when(api.minigames().list()).thenReturn(java.util.List.of(game));
        when(game.findPlayer(player)).thenReturn(mock(dev.stemcraft.api.minigame.MiniGameArena.class));
        var feature = new GameModeInventories(api);
        var method = GameModeInventories.class.getDeclaredMethod("onWorldChange", PlayerChangedWorldEvent.class);
        method.setAccessible(true);
        method.invoke(feature, new PlayerChangedWorldEvent(player, world("bridge_amazon")));
        verifyNoInteractions(player);
    }

    @Test
    void blankOverridesUsePrefixAndExplicitGroupNamesRemainLiteral() {
        var yaml = new YamlConfiguration();
        yaml.set("worlds.survival_deep.inventory-group", "  ");
        yaml.set("worlds.survival_nether.inventory-group", " separate_group ");
        var feature = feature(yaml);
        assertEquals("survival", feature.worldGroup(world("survival_deep")));
        assertEquals("survival", feature.worldGroup(world("survival_the_end")));
        assertEquals("separate_group", feature.worldGroup(world("survival_nether")));
    }

    @Test
    void sharedWorldTransitionKeepsItemsAndAnotherGroupRestoresItsOwnInventory() throws Exception {
        var server = MockBukkit.mock();
        try {
            World survival = server.addSimpleWorld("survival");
            World deep = server.addSimpleWorld("survival_deep");
            World other = server.addSimpleWorld("other");
            var player = server.addPlayer();
            player.teleport(survival.getSpawnLocation());
            player.setGameMode(GameMode.SURVIVAL);
            var yaml = new YamlConfiguration();
            var feature = feature(yaml);
            var join = GameModeInventories.class.getDeclaredMethod("onJoin", PlayerJoinEvent.class);
            join.setAccessible(true);
            join.invoke(feature, new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
            transition(feature, player, deep);
            assertEquals(5, player.getInventory().getItem(0).getAmount());
            yaml.set("worlds.survival_deep.inventory-group", "separate");
            feature.onReload();
            assertNull(player.getInventory().getItem(0));
            player.getInventory().setItem(0, new ItemStack(Material.EMERALD, 2));
            yaml.set("worlds.survival_deep.inventory-group", null);
            feature.onReload();
            assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
            yaml.set("worlds.survival_deep.inventory-group", "separate");
            feature.onReload();
            assertEquals(Material.EMERALD, player.getInventory().getItem(0).getType());
            assertEquals(2, player.getInventory().getItem(0).getAmount());
            yaml.set("worlds.survival_deep.inventory-group", null);
            feature.onReload();
            player.setGameMode(GameMode.CREATIVE);
            feature.onReload();
            assertNull(player.getInventory().getItem(0));
            player.setGameMode(GameMode.SURVIVAL);
            feature.onReload();
            assertEquals(5, player.getInventory().getItem(0).getAmount());
            transition(feature, player, other);
            assertNull(player.getInventory().getItem(0));
            transition(feature, player, survival);
            assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
            assertEquals(5, player.getInventory().getItem(0).getAmount());
        } finally {
            MockBukkit.unmock();
        }
    }

    private void transition(GameModeInventories feature, Player player, World to) throws Exception {
        World from = player.getWorld();
        player.teleport(to.getSpawnLocation());
        var method = GameModeInventories.class.getDeclaredMethod("onWorldChange", PlayerChangedWorldEvent.class);
        method.setAccessible(true);
        method.invoke(feature, new PlayerChangedWorldEvent(player, from));
    }
}
