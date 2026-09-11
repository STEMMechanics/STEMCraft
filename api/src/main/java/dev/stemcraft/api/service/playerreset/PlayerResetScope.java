package dev.stemcraft.api.service.playerreset;

/** Increasing levels of player-owned data removal. Moderation and audit history are never included. */
public enum PlayerResetScope {
    PROGRESSION,
    GAMEPLAY,
    COMPLETE;

    public boolean includes(PlayerResetScope minimum) {
        return ordinal() >= minimum.ordinal();
    }
}
