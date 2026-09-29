package dev.stemcraft.api.service.stembot;

import org.bukkit.entity.Player;
import java.util.Optional;

/** Optional private guide. Mutating operations must run on the server thread. */
public interface StemBotService {
    StemBotService UNAVAILABLE = new StemBotService() {
        public boolean available() { return false; }
        public Optional<StemBotSession> open(Player player) { return Optional.empty(); }
        public boolean hasAction(String action) { return false; }
        public boolean startAction(Player player,String action) { return false; }
    };

    boolean available();

    /**
     * Reserve exclusive control of the player's guide. Empty if unavailable or already owned.
     * A handle can be inactive when no safe spawn exists; close it when finished regardless.
     * Automatic introductions and manual summons are suspended until release.
     * The caller owns gameplay restrictions. Use the handle's listen callback to consume
     * private replies; without a listener, ordinary chat remains public.
     */
    Optional<StemBotSession> open(Player player);

    /** Whether the loaded script defines this exact action name. */
    boolean hasAction(String action);

    /** Start a named action. False if unavailable, unknown, owned by another caller, or unable to spawn. */
    boolean startAction(Player player,String action);

}
