package dev.stemcraft.feature.stembot;

import org.bukkit.Location;

/** Small navigation boundary, independently testable without a live Citizens server. */
public interface BotActor {
    Location location();
    boolean valid();
    boolean navigating();
    void move(Location target,double speed);
    void pause(boolean paused);
    void cancel();
    void face(float yaw);
    void sneak(boolean sneaking);
    void jump();
    void close();
}
