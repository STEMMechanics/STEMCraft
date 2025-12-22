package dev.stemcraft.api.services;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface GateKeeperService extends STEMCraftService {

    boolean isBlacklisted(UUID uuid);
    default boolean isBlacklisted(Player player) { return isBlacklisted(player.getUniqueId()); }
    void blacklist(UUID uuid, boolean value);

    boolean isWhitelisted(UUID uuid);
    default boolean isWhitelisted(Player player) { return isWhitelisted(player.getUniqueId()); }
    void whitelist(UUID uuid, boolean value);
    default void whitelist(Player player, boolean value) { whitelist(player.getUniqueId(), value); }
}
