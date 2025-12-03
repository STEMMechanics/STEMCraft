package dev.stemcraft.api.services.punishment;

import dev.stemcraft.api.services.STEMCraftService;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface PunishmentService extends STEMCraftService {
    /**
     * Generic entry point for other plugins (e.g. Naughty feature).
     */
    void record(UUID playerUuid, Player actor, Duration duration, String type, boolean alerted, String reason);

    /**
     * Latest first, 1-based page index.
     */
    List<PunishmentRecord> list(UUID targetUuid, String type, int page, int pageSize);

    void registerAlert(String type, PunishmentAlertCallback callback);

    default String typeToString(String type) {
        if (type == null || type.isEmpty()) return "";
        type = type.replaceAll("[-_]", " ").toLowerCase();
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }
}