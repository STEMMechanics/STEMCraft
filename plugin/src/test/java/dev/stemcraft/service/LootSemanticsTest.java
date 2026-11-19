package dev.stemcraft.service;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootSemanticsTest {
    @Test
    void quantityRangesAreInclusiveAndDeterministicForASeed() {
        Random random = new Random(12L);
        for (int index = 0; index < 200; index++) {
            int quantity = LootServiceImpl.inclusiveQuantity(random, 2, 4);
            assertTrue(quantity >= 2 && quantity <= 4);
        }
        assertEquals(7, LootServiceImpl.inclusiveQuantity(random, 7, 7));
    }

    @Test
    void replacementRollsCanBeGuaranteedOrRejectedWithoutAmbiguousBoundaries() {
        Random belowHalf = new Random() {
            @Override public double nextDouble() { return 0.49D; }
        };
        Random aboveHalf = new Random() {
            @Override public double nextDouble() { return 0.50D; }
        };
        assertTrue(LootServiceImpl.roll(0.5D, belowHalf));
        assertFalse(LootServiceImpl.roll(0.5D, aboveHalf));
        assertFalse(LootServiceImpl.roll(0D, belowHalf));
        assertTrue(LootServiceImpl.roll(1D, aboveHalf));
    }
}
