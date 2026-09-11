package dev.stemcraft.feature.stembot;

import org.bukkit.Location;

/** Small Citizens boundary so the action engine can be tested without a live NPC. */
public interface BotActor {
    Location location();
    boolean valid();
    boolean navigating();

    void move(Location target,double speed);
    void pause(boolean paused);
    void cancel();

    void face(float yaw);
    void lookAt(Location target);
    void sneak(boolean sneaking);
    void animate(String animation);
    void skin(BotSkinCache.Skin skin);
    void puff();

    void close();
}
