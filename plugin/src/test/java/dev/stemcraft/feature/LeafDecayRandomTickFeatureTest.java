package dev.stemcraft.feature;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeafDecayRandomTickFeatureTest {
    @Test
    void mapsNaturalLogsToMatchingSaplings() {
        assertEquals(Material.OAK_SAPLING, LeafDecayRandomTickFeature.saplingForLog(Material.OAK_LOG));
        assertEquals(Material.DARK_OAK_SAPLING, LeafDecayRandomTickFeature.saplingForLog(Material.DARK_OAK_LOG));
        assertEquals(Material.MANGROVE_PROPAGULE, LeafDecayRandomTickFeature.saplingForLog(Material.MANGROVE_LOG));
        assertEquals(Material.CHERRY_SAPLING, LeafDecayRandomTickFeature.saplingForLog(Material.CHERRY_LOG));
        assertEquals(Material.PALE_OAK_SAPLING, LeafDecayRandomTickFeature.saplingForLog(Material.PALE_OAK_LOG));
    }

    @Test
    void breakingSaplingsAndPlacedWoodDoesNotScheduleAnotherSapling() {
        assertNull(LeafDecayRandomTickFeature.saplingForLog(Material.OAK_SAPLING));
        assertNull(LeafDecayRandomTickFeature.saplingForLog(Material.OAK_PLANKS));
        assertNull(LeafDecayRandomTickFeature.saplingForLog(Material.STRIPPED_OAK_LOG));
    }
}
