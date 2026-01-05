package dev.stemcraft.api.minigame;

import dev.stemcraft.api.capability.HasMeta;
import org.bukkit.Location;

public interface MiniGameTeam extends HasMeta {

    String getName();

    int getScore();
    void setScore(int score);
    void addScore(int delta);
    default void addScore() { addScore(1); }
    void subScore(int delta);
    default void subScore() { subScore(1); }

    Location getSpawn();
    void setSpawn(Location location);

    void teleportAllToSpawn();
}
