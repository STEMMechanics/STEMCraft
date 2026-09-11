package dev.stemcraft.feature.quest;

import java.util.Locale;

/** Pure spawn-rule checks shared by the runtime and tests. */
public final class QuestNpcSpawnRules {
    private QuestNpcSpawnRules() { }

    public static boolean eligible(QuestNpcProfile profile, String world, int level, long time, String biome) {
        if (!profile.supportsWorld(world) || level < profile.minimumLevel()) return false;
        long tick = Math.floorMod(time, 24000);
        long from = profile.timeFrom();
        long until = profile.timeUntil();
        boolean withinTime = until == 24000 || from <= until ? tick >= from && tick <= until : tick >= from || tick <= until;
        if (!withinTime) return false;
        return profile.biomes().isEmpty() || profile.biomes().stream()
            .map(value -> value.toUpperCase(Locale.ROOT)).anyMatch(value -> value.equals(biome.toUpperCase(Locale.ROOT)));
    }

    public static boolean dailyRoll(double chance, double roll) {
        return roll >= 0D && roll < Math.max(0D, Math.min(1D, chance));
    }
}
