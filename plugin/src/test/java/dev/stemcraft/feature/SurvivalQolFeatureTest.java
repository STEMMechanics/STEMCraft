package dev.stemcraft.feature;

import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import dev.stemcraft.api.util.PatternUtil;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurvivalQolFeatureTest {
    @Test
    void massHarvestUnlocksRequireLevelTenAndHigherTierToolsByDefault() {
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertUnlock(config, "tree-felling", "qol-tree-felling", "skill_herbalism_xp", 10);
        assertUnlock(config, "vein-mining", "qol-vein-mining", "skill_mining_xp", 10);
        assertTrue(config.getBoolean("survival-qol.tree-felling.require-sneaking"));
        assertTrue(config.getBoolean("survival-qol.vein-mining.require-sneaking"));
        assertEquals(List.of("DIAMOND_AXE", "NETHERITE_AXE"), config.getStringList("survival-qol.tree-felling.tools"));
        assertEquals(List.of("DIAMOND_PICKAXE", "NETHERITE_PICKAXE"), config.getStringList("survival-qol.vein-mining.tools"));
    }

    @Test
    void autoSelectLeavesEmptyHandAloneWhenOnlyLogsAreAvailable() {
        var block = mock(org.bukkit.block.Block.class);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        var log = mock(org.bukkit.inventory.ItemStack.class);
        when(log.getType()).thenReturn(Material.OAK_LOG);
        when(block.isPreferredTool(log)).thenReturn(true);
        when(block.getDestroySpeed(log)).thenReturn(1.0f);
        when(inventory.getStorageContents()).thenReturn(new org.bukkit.inventory.ItemStack[]{null, log});
        int selected = SurvivalQolFeature.preferredToolSlot(block, inventory, 0);
        assertEquals(-1, selected);
    }

    @Test
    void autoSelectOnlyRunsWhenStartingToBreakABlock() {
        assertTrue(SurvivalQolFeature.triggersAutoSelect(Action.LEFT_CLICK_BLOCK));
        assertFalse(SurvivalQolFeature.triggersAutoSelect(Action.RIGHT_CLICK_BLOCK));
        assertFalse(SurvivalQolFeature.triggersAutoSelect(Action.RIGHT_CLICK_AIR));
    }

    @Test
    void autoSelectChoosesFastestToolAndKeepsItWhenAlreadyHeld() {
        var block = mock(org.bukkit.block.Block.class);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        var log = mock(org.bukkit.inventory.ItemStack.class);
        var woodenAxe = mock(org.bukkit.inventory.ItemStack.class);
        var ironAxe = mock(org.bukkit.inventory.ItemStack.class);
        when(log.getType()).thenReturn(Material.OAK_LOG);
        when(woodenAxe.getType()).thenReturn(Material.WOODEN_AXE);
        when(ironAxe.getType()).thenReturn(Material.IRON_AXE);
        when(block.isPreferredTool(woodenAxe)).thenReturn(true);
        when(block.isPreferredTool(ironAxe)).thenReturn(true);
        when(block.getDestroySpeed(woodenAxe)).thenReturn(2.0f);
        when(block.getDestroySpeed(ironAxe)).thenReturn(6.0f);
        when(inventory.getStorageContents()).thenReturn(
            new org.bukkit.inventory.ItemStack[]{null, log, woodenAxe, ironAxe});
        assertEquals(3, SurvivalQolFeature.preferredToolSlot(block, inventory, 0));
        assertEquals(3, SurvivalQolFeature.preferredToolSlot(block, inventory, 3));
        when(block.isPreferredTool(woodenAxe)).thenReturn(false);
        when(block.isPreferredTool(ironAxe)).thenReturn(false);
        assertEquals(-1, SurvivalQolFeature.preferredToolSlot(block, inventory, 0));
    }

    @Test
    void onlyRefillsToolsWithTheIdenticalMaterial() {
        assertTrue(SurvivalQolFeature.sameToolMaterial(Material.STONE_PICKAXE, Material.STONE_PICKAXE));
        assertFalse(SurvivalQolFeature.sameToolMaterial(Material.STONE_PICKAXE, Material.WOODEN_PICKAXE));
        assertFalse(SurvivalQolFeature.sameToolMaterial(Material.STONE_PICKAXE, Material.NETHERITE_PICKAXE));
        assertFalse(SurvivalQolFeature.sameToolMaterial(Material.IRON_AXE, Material.IRON_PICKAXE));
    }

    @Test
    void blankPermissionAllowsEveryoneAndConfiguredPermissionIsChecked() {
        Player player = mock(Player.class);
        assertTrue(SurvivalQolFeature.permissionAllows("", player));
        assertTrue(SurvivalQolFeature.permissionAllows(null, player));

        when(player.hasPermission("stemcraft.qol.auto-refill")).thenReturn(true);
        assertTrue(SurvivalQolFeature.permissionAllows(" stemcraft.qol.auto-refill ", player));
        verify(player).hasPermission("stemcraft.qol.auto-refill");
    }

    @Test
    void survivalQolOnlyRunsInSupportedWorldsAndGameModes() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("survival-2");
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        assertTrue(SurvivalQolFeature.contextAllows(player,
            List.of(PatternUtil.globToRegex("survival*")), java.util.Set.of(GameMode.SURVIVAL)));

        when(world.getName()).thenReturn("world");
        assertFalse(SurvivalQolFeature.contextAllows(player,
            List.of(PatternUtil.globToRegex("survival*")), java.util.Set.of(GameMode.SURVIVAL)));

        when(world.getName()).thenReturn("survival");
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertFalse(SurvivalQolFeature.contextAllows(player,
            List.of(PatternUtil.globToRegex("survival*")), java.util.Set.of(GameMode.SURVIVAL)));
    }

    @Test
    void bundledQolPermissionsMatchProfessionEntitlements() {
        InputStream input = getClass().getResourceAsStream("/config.yml");
        assertNotNull(input);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new InputStreamReader(input, StandardCharsets.UTF_8));

        assertEquals(List.of("survival*"), config.getStringList("survival-qol.supported-worlds"));
        assertEquals(List.of("SURVIVAL"), config.getStringList("survival-qol.supported-game-modes"));

        List<Map<?, ?>> refillPaths = config.getMapList("entitlements.definitions.qol-tool-refill.when.any");
        assertEquals(5, refillPaths.size());
        assertEquals("stemcraft.qol.auto-refill-tools", config.getStringList(
            "entitlements.definitions.qol-tool-refill.grants.permissions").getFirst());
        for (Map<?, ?> path : refillPaths) {
            Map<?, ?> stat = (Map<?, ?>) path.get("stat");
            assertEquals(ProfessionsFeature.xpForLevel(3), ((Number) stat.get("at-least")).longValue());
        }
        assertUnlock(config, "hoe-harvest", "qol-farming-hoe-harvest", "skill_farming_xp", 3);
        assertUnlock(config, "auto-refill", "qol-engineering-auto-refill", "skill_engineering_xp", 3);
        assertEquals("stemcraft.qol.auto-select-tool",
            config.getString("survival-qol.auto-select-tool.permission"));
        List<Map<?, ?>> requirements = config.getMapList(
            "entitlements.definitions.qol-auto-select-tool.when.all");
        Map<?, ?> miningRequirement = (Map<?, ?>) requirements.get(0).get("stat");
        Map<?, ?> engineeringRequirement = (Map<?, ?>) requirements.get(1).get("stat");
        assertEquals("skill_mining_xp", miningRequirement.get("key"));
        assertEquals(ProfessionsFeature.xpForLevel(4), ((Number) miningRequirement.get("at-least")).longValue());
        assertEquals("skill_engineering_xp", engineeringRequirement.get("key"));
        assertEquals(ProfessionsFeature.xpForLevel(4), ((Number) engineeringRequirement.get("at-least")).longValue());
        assertUnlock(config, "stronger-leads", "qol-farming-stronger-leads", "skill_farming_xp", 4);
        assertUnlock(config, "powered-minecarts", "qol-engineering-powered-minecarts", "skill_engineering_xp", 5);
        assertUnlock(config, "named-mob-info", "qol-farming-named-mob-info", "skill_farming_xp", 5);
        assertEquals("", config.getString("entitlements.badge-display.separator"));
    }

    private static void assertUnlock(YamlConfiguration config, String feature, String entitlement,
                                     String stat, int level) {
        String permission = config.getString("survival-qol." + feature + ".permission");
        assertEquals("stemcraft.qol." + feature, permission);
        String base = "entitlements.definitions." + entitlement;
        assertEquals(stat, config.getString(base + ".when.stat.key"));
        assertEquals(ProfessionsFeature.xpForLevel(level), config.getLong(base + ".when.stat.at-least"));
        assertEquals(permission, config.getStringList(base + ".grants.permissions").getFirst());
    }
}
