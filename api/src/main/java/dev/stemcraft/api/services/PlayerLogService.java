package dev.stemcraft.api.services;

import org.bukkit.entity.Player;

public interface PlayerLogService extends STEMCraftService {
    void logPlayerAction(Player player, String action);
}
