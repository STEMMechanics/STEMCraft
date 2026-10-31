package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalBarrelsTest {
    @Test
    void recognizesCurrentAndLegacyItemIds() {
        assertTrue(AnimalBarrels.isAnimalCrateId("animal-crate"));
        assertTrue(AnimalBarrels.isAnimalCrateId("animal-barrel"));
        assertTrue(AnimalBarrels.isAnimalCrateId("ANIMAL-BARREL"));
        assertFalse(AnimalBarrels.isAnimalCrateId(null));
        assertFalse(AnimalBarrels.isAnimalCrateId("barrel"));
    }
}
