package dev.stemcraft.api.service.stembot;

import org.bukkit.Location;
import java.util.function.Consumer;

/** Exclusive guide handle. Closing a stale handle never affects a newer conversation. */
public interface StemBotSession extends AutoCloseable {
    /** Whether the controlled actor is still present. Safe to query from chat listeners. */
    boolean active();

    /** Speak using STEMBot's configured text and sounds; does not enable reply capture. False allows caller-side fallback. */
    boolean speak(String message);

    /** A copy of the actor's location, or empty when inactive. Server-thread only. */
    java.util.Optional<Location> location();

    /** Teleport within the owner's world, cancelling movement. False if invalid or cancelled. */
    boolean teleport(Location destination);

    /** Follow the player, or stop and wait nearby. */
    void follow(boolean enabled);

    /** Release control into a named action. On failure, retain the handle for retry or close. */
    boolean startAction(String action);

    /**
     * Walk to a same-world destination, replacing any follow/move order.
     * When waitForPlayer is true, pause/resume at the configured player-distance thresholds.
     * False rejects an inactive session or invalid destination; true accepts the order,
     * not a guarantee of arrival. Navigation uses the same bounded recovery as script walks.
     */
    boolean move(Location destination,boolean waitForPlayer);

    /** Consume ordinary chat privately until stopped, replaced, closed or handed to an action.
     * Callback runs on the server thread. Slash commands are unaffected. */
    void listen(Consumer<String> replies);

    /** Return ordinary chat to public routing. Already queued replies are discarded. */
    void stopListening();

    /** Whether this active handle is consuming chat. Safe from chat listeners. */
    boolean listening();

    /** Release control and dismiss the actor. Idempotent. */
    @Override void close();
}
