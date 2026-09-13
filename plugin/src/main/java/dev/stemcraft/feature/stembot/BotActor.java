package dev.stemcraft.feature.stembot;

import org.bukkit.Location;

/** Small Citizens boundary so the action engine can be tested without a live NPC. */
public interface BotActor {
    Location location();
    boolean valid();
    boolean navigating();

    void move(Location target,double speed);
    /** Incremental route search. Null means still searching; empty means no route found. */
    interface RouteSearch {
        java.util.List<Location> advance();
    }
    default RouteSearch findRoute(Location target) { return java.util.List::of; }
    /** Read-only path probe from the actor's current position. */
    default boolean canNavigateTo(Location target) { return false; }
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
