package dev.stemcraft.feature;

import dev.stemcraft.feature.quest.QuestNpcProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private QuestNpcProfile profile(long from, long until) {
        QuestNpcProfile profile = new QuestNpcProfile("test", "Test");
        profile.timeFrom(from);
        profile.timeUntil(until);
        return profile;
    }
}
