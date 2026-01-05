package dev.stemcraft.api.minigame;

import dev.stemcraft.api.capability.HasMeta;
import org.bukkit.entity.Player;

public interface MiniGamePlayer extends HasMeta {

    Player getPlayer();

    String getTeam();
    void setTeam(String team);

    int getScore();
    void setScore(int score);
    void addScore(int delta);
    default void addScore() { addScore(1); }
    void subScore(int delta);
    default void subScore() { subScore(1); }

    int getKills();
    void setKills(int kills);
    void addKill();

    int getDeaths();
    void setDeaths(int deaths);
    void addDeath();
}
