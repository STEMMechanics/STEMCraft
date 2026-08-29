package dev.stemcraft.feature;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
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
    void bundledQolPermissionsMatchProfessionEntitlements() {
        InputStream input = getClass().getResourceAsStream("/config.yml");
        assertNotNull(input);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new InputStreamReader(input, StandardCharsets.UTF_8));

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
