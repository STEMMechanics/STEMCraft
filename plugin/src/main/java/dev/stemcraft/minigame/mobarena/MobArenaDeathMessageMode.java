package dev.stemcraft.minigame.mobarena;

/// <p>An enum for determining whether to make the death messages EXCLUSIVEly the arenas, or make a UNION of the global death messages and the arena.</p>
enum MobArenaDeathMessageMode {
    /// <p>Specifies that only the arena's messages will be used.</p>
    EXCLUSIVE,
    /// <p>Specifies that both the global and arena's messages will be used.</p>
    UNION
}
