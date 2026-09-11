package dev.stemcraft.feature.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestNpcSpawnRulesTest {
    @Test
    void enforcesWorldLevelTimeAndBiome() {
        QuestNpcProfile tim = new QuestNpcProfile("tim", "Tim");
        tim.world("survival"); tim.minimumLevel(3); tim.timeFrom(0); tim.timeUntil(9000); tim.biomes().add("PLAINS");
        assertTrue(QuestNpcSpawnRules.eligible(tim, "survival", 3, 6000, "PLAINS"));
        assertFalse(QuestNpcSpawnRules.eligible(tim, "world", 3, 6000, "PLAINS"));
        assertFalse(QuestNpcSpawnRules.eligible(tim, "survival", 2, 6000, "PLAINS"));
        assertFalse(QuestNpcSpawnRules.eligible(tim, "survival", 3, 12000, "PLAINS"));
        assertFalse(QuestNpcSpawnRules.eligible(tim, "survival", 3, 6000, "DESERT"));
    }

    @Test
    void dailyChanceHasExactBoundary() {
        assertTrue(QuestNpcSpawnRules.dailyRoll(.25, .2499));
        assertFalse(QuestNpcSpawnRules.dailyRoll(.25, .25));
    }

    @Test
    void supportsOneGlobalProfileAcrossMultipleWorlds() {
        QuestNpcProfile tribute = new QuestNpcProfile("tribute", "Tribute");
        tribute.worlds(java.util.List.of("survival", "survival_nether"));

        assertTrue(QuestNpcSpawnRules.eligible(tribute, "survival", 0, 6000, "PLAINS"));
        assertTrue(QuestNpcSpawnRules.eligible(tribute, "survival_nether", 0, 6000, "NETHER_WASTES"));
        assertFalse(QuestNpcSpawnRules.eligible(tribute, "creative", 0, 6000, "PLAINS"));
    }
}
