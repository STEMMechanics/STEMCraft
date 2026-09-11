package dev.stemcraft.feature;

import dev.stemcraft.feature.quest.QuestNpcProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestNpcSpawnPeriodTest {
    @Test
    void daytimeWindowAdvancesAtItsNextStart() {
        QuestNpcProfile profile = profile(0, 12000);
        assertEquals(0, QuestFeature.npcSpawnPeriod(profile, 6000));
        assertEquals(1, QuestFeature.npcSpawnPeriod(profile, 30000));
    }

    @Test
    void nighttimeWindowAdvancesAtItsNextStart() {
        QuestNpcProfile profile = profile(13000, 23000);
        assertEquals(0, QuestFeature.npcSpawnPeriod(profile, 18000));
        assertEquals(1, QuestFeature.npcSpawnPeriod(profile, 42000));
    }

    @Test
    void midnightCrossingWindowKeepsOnePeriodAcrossMidnight() {
        QuestNpcProfile profile = profile(18000, 6000);
        assertEquals(0, QuestFeature.npcSpawnPeriod(profile, 23000));
        assertEquals(0, QuestFeature.npcSpawnPeriod(profile, 25000));
        assertEquals(1, QuestFeature.npcSpawnPeriod(profile, 43000));
    }

    @Test
    void migratesLegacySeleneProfileToDaytimeCaveSpawns() {
        QuestNpcProfile profile = new QuestNpcProfile("expansion-selene", "Selene");
        profile.biomes().addAll(List.of("LUSH_CAVES", "DRIPSTONE_CAVES", "PLAINS"));

        assertTrue(QuestFeature.migrateLegacySeleneProfile(profile));
        assertEquals(12000, profile.timeUntil());
        assertEquals(List.of("LUSH_CAVES", "DRIPSTONE_CAVES"), profile.biomes());
    }

    @Test
    void preservesCustomizedSeleneProfile() {
        QuestNpcProfile profile = new QuestNpcProfile("expansion-selene", "Selene");
        profile.biomes().addAll(List.of("LUSH_CAVES", "DRIPSTONE_CAVES", "PLAINS", "MEADOW"));

        assertFalse(QuestFeature.migrateLegacySeleneProfile(profile));
        assertEquals(24000, profile.timeUntil());
        assertTrue(profile.biomes().contains("PLAINS"));
    }

    private QuestNpcProfile profile(long from, long until) {
        QuestNpcProfile profile = new QuestNpcProfile("test", "Test");
        profile.timeFrom(from);
        profile.timeUntil(until);
        return profile;
    }
}
