package dev.stemcraft.api.services.punishment;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface PunishmentAlertCallback {
    boolean run(String type, Player player, PunishmentRecord record);
}
