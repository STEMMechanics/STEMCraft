package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionsFeatureTest {
    @Test
    void levelsStartAtOneAndFollowTheQuadraticCurve() {
        assertEquals(1, ProfessionsFeature.levelForXp(0));
        assertEquals(1, ProfessionsFeature.levelForXp(99));
        assertEquals(2, ProfessionsFeature.levelForXp(100));
        assertEquals(3, ProfessionsFeature.levelForXp(400));
        assertEquals(100, ProfessionsFeature.levelForXp(Double.MAX_VALUE));
    }

    @Test
    void exposesCumulativeXpThresholds() {
        assertEquals(0, ProfessionsFeature.xpForLevel(1));
        assertEquals(100, ProfessionsFeature.xpForLevel(2));
        assertEquals(400, ProfessionsFeature.xpForLevel(3));
        assertEquals(980100, ProfessionsFeature.xpForLevel(100));
    }

    @Test
    void progressBarClampsAndUsesTenSegments() {
        assertEquals("&a&8■■■■■■■■■■", ProfessionsFeature.progressBar(-1));
        assertEquals("&a■■■■■&8■■■■■", ProfessionsFeature.progressBar(55));
        assertEquals("&a■■■■■■■■■■&8", ProfessionsFeature.progressBar(101));
    }
}
