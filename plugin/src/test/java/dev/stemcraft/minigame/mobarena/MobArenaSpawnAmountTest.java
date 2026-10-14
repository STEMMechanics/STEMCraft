package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.minigame.mobarena.MobArenaSpawnerRecord.IncrementType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobArenaSpawnAmountTest {
    @Test
    void testSpawnAmount() {
        assertEquals(0, MobArenaArenaHandler.determineMobSpawnCount(1, 2, 2, 2, IncrementType.Linear));
        assertEquals(2, MobArenaArenaHandler.determineMobSpawnCount(2, 2, 2, 2, IncrementType.Linear));
        assertEquals(4, MobArenaArenaHandler.determineMobSpawnCount(3, 2, 2, 2, IncrementType.Linear));
        assertEquals(6, MobArenaArenaHandler.determineMobSpawnCount(4, 2, 2, 2, IncrementType.Linear));
        assertEquals(8, MobArenaArenaHandler.determineMobSpawnCount(5, 2, 2, 2, IncrementType.Linear));
    }
}
