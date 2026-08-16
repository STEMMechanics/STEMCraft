package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.minigame.MiniGameArena;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NightfallMiniGameDefaultsTest {
    @Test
    void gameplayDefaultsMatchNightfallTuning() {
        MiniGameArena arena = mock(MiniGameArena.class);
        NightfallMiniGame nightfall = new NightfallMiniGame(null);

        when(arena.get("dropMaxActiveItems", Integer.class, 10)).thenReturn(10);
        when(arena.get("dropGroupDistance", Integer.class, 100)).thenReturn(100);
        when(arena.get("zombieNightlyHealthMultiplier", Double.class, 1.05d)).thenReturn(1.05d);
        when(arena.get("bloodMoonZombieSpawnMultiplier", Double.class, 2.0d)).thenReturn(2.0d);
        when(arena.get("bloodMoonBabyZombieChancePercent", Integer.class, 20)).thenReturn(20);
        when(arena.get("bloodMoonComets", BloodMoonCometSettings.class, BloodMoonCometSettings.defaults()))
            .thenReturn(BloodMoonCometSettings.defaults());

        assertEquals(10, nightfall.dropMaxActiveItems(arena));
        assertEquals(100, nightfall.dropGroupDistance(arena));
        assertEquals(1.05d, nightfall.zombieNightlyHealthMultiplier(arena), 0.0001d);
        assertEquals(2.0d, nightfall.bloodMoonZombieSpawnMultiplier(arena), 0.0001d);
        assertEquals(20, nightfall.bloodMoonBabyZombieChancePercent(arena));
        assertEquals(8, nightfall.bloodMoonComets(arena).startNight());
        assertEquals(20, nightfall.bloodMoonComets(arena).chancePercent());
        assertEquals(4, nightfall.bloodMoonComets(arena).loot().size());
    }

    @Test
    void gameplaySettingsClampToSafeRanges() {
        MiniGameArena arena = mock(MiniGameArena.class);
        NightfallMiniGame nightfall = new NightfallMiniGame(null);

        when(arena.get("dropMaxActiveItems", Integer.class, 10)).thenReturn(-3);
        when(arena.get("dropGroupDistance", Integer.class, 100)).thenReturn(-1);
        when(arena.get("zombieNightlyHealthMultiplier", Double.class, 1.05d)).thenReturn(0.5d);
        when(arena.get("bloodMoonZombieSpawnMultiplier", Double.class, 2.0d)).thenReturn(0.75d);
        when(arena.get("bloodMoonBabyZombieChancePercent", Integer.class, 20)).thenReturn(120);

        assertEquals(0, nightfall.dropMaxActiveItems(arena));
        assertEquals(0, nightfall.dropGroupDistance(arena));
        assertEquals(1.0d, nightfall.zombieNightlyHealthMultiplier(arena), 0.0001d);
        assertEquals(1.0d, nightfall.bloodMoonZombieSpawnMultiplier(arena), 0.0001d);
        assertEquals(100, nightfall.bloodMoonBabyZombieChancePercent(arena));
    }

    @Test
    void zeroDropDelayDisablesDrops() {
        MiniGameArena arena = mock(MiniGameArena.class);
        NightfallMiniGame nightfall = new NightfallMiniGame(null);

        when(arena.get("dropMinSeconds", Integer.class, 1)).thenReturn(0);
        when(arena.get("dropMaxSeconds", Integer.class, 5)).thenReturn(0);

        assertEquals(0, nightfall.dropMinSeconds(arena));
        assertEquals(0, nightfall.dropMaxSeconds(arena));
        org.junit.jupiter.api.Assertions.assertFalse(nightfall.dropsEnabled(arena));
    }

    @Test
    void bloodMoonTntChanceEscalatesByNightAndStopsAtConfiguredCap() {
        MiniGameArena arena = mock(MiniGameArena.class);
        NightfallMiniGame nightfall = new NightfallMiniGame(null);

        when(arena.get("bloodMoonTntZombieChancePercent", Integer.class, 3)).thenReturn(3);
        when(arena.get("bloodMoonEscalation", BloodMoonEscalation.class, BloodMoonEscalation.defaults()))
            .thenReturn(BloodMoonEscalation.defaults());
        when(arena.get("currentNight", Integer.class, 0)).thenReturn(8);

        assertEquals(17, nightfall.bloodMoonTntZombieChancePercentForNight(arena));

        when(arena.get("currentNight", Integer.class, 0)).thenReturn(20);
        assertEquals(25, nightfall.bloodMoonTntZombieChancePercentForNight(arena));
        assertTrue(nightfall.bloodMoonEscalation(arena).spongeRadius() > 0);
    }
}
