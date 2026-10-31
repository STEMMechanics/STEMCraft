package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkipNightTest {
    @Test
    void oneOfOnePlayerAtTwentyFivePercentIsACompleteVote() {
        int required = SkipNight.requiredSleeperCount(1, 0.25d);

        assertEquals(1, required);
        assertEquals(1.0d, SkipNight.sleeperProgress(1, required));
    }

    @Test
    void requiredCountUsesCeilingAndNeverDropsBelowOne() {
        assertEquals(1, SkipNight.requiredSleeperCount(2, 0.25d));
        assertEquals(2, SkipNight.requiredSleeperCount(5, 0.25d));
        assertEquals(1, SkipNight.requiredSleeperCount(5, 0.0d));
        assertEquals(0, SkipNight.requiredSleeperCount(0, 0.25d));
    }

    @Test
    void bossBarProgressIsAlwaysFiniteAndClamped() {
        assertEquals(0.0d, SkipNight.sleeperProgress(1, 0));
        assertEquals(1.0d, SkipNight.sleeperProgress(3, 2));
        assertEquals(0.0d, SkipNight.sleeperProgress(-1, 2));
        assertTrue(Double.isFinite(SkipNight.sleeperProgress(1, 0)));
    }

    @Test
    void nightSkipUsesTheVanillaBedWindow() {
        assertTrue(SkipNight.canStartNightSkip(12542));
        assertTrue(SkipNight.canStartNightSkip(18000));
        assertFalse(SkipNight.canStartNightSkip(12541));
        assertFalse(SkipNight.canStartNightSkip(0));
        assertTrue(SkipNight.canStartNightSkip(36542));
    }
}
