package dev.stemcraft.minigame.mobarena;

import org.bukkit.entity.EntityType;

/**
 * <p>A record that stores information about a Mob Arena Spawner Config (until it is processed into the Arena KV-store).</p>
 *
 * @param entityType
 * @param initialAmount
 * @param incrementAmount
 * @param incrementType
 * @param initialWave
 * @param spawnZone
 * @param countTowardsMobCount
 */
record MobArenaSpawnerRecord(
        EntityType entityType,
        int initialAmount,
        double incrementAmount,
        IncrementType incrementType,
        int initialWave,
        String spawnZone,
        boolean countTowardsMobCount
) {
    /**
     * <p>Determines how the amount of entities spawned will rise.</p>
     */
    enum IncrementType {
        /**
         * <p>Linear incrementation.</p>
         *
         * <p>The amount of mobs spawned by the spawner will increase linearly.</p>
         */
        Linear,
        /**
         * <p>Exponential incrementation.</p>
         *
         * <p>The amount of mobs spawned by the spawner will increase exponentially.</p>
         */
        Exponential
    }
}
