package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceDifficultyTierTest {
    @Test
    void usesTheDocumentedOneThousandBlockBoundaries() {
        assertEquals(0, DistanceDifficulty.tierForDistance(0, 1_000));
        assertEquals(0, DistanceDifficulty.tierForDistance(999, 1_000));
        assertEquals(1, DistanceDifficulty.tierForDistance(1_000, 1_000));
        assertEquals(1, DistanceDifficulty.tierForDistance(1_999, 1_000));
        assertEquals(2, DistanceDifficulty.tierForDistance(2_000, 1_000));
        assertEquals(0, DistanceDifficulty.tierForDistance(-20, 1_000));
    }
}
