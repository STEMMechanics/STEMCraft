package dev.stemcraft.api.service.comet;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CometLootTest {
    @Test
    void acceptsPlaceableBlockAndInclusiveRange() {
        CometLoot loot = new CometLoot(Material.GOLD_BLOCK, 2, 15);

        assertEquals(Material.GOLD_BLOCK, loot.material());
        assertEquals(2, loot.minimum());
        assertEquals(15, loot.maximum());
    }

    @Test
    void rejectsItemsAirAndInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new CometLoot(Material.GOLD_INGOT, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new CometLoot(Material.AIR, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new CometLoot(Material.GOLD_BLOCK, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new CometLoot(Material.GOLD_BLOCK, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> new CometLoot(Material.GOLD_BLOCK, 1, 4097));
    }
}
