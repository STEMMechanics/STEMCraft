package dev.stemcraft.feature;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;

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
}
