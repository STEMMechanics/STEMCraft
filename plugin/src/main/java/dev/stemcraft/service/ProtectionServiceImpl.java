package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.protection.ProtectionRequest;
import dev.stemcraft.api.service.protection.ProtectionRule;
import dev.stemcraft.api.service.protection.ProtectionService;
import dev.stemcraft.api.service.protection.ProtectionType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionServiceImpl extends BaseService implements ProtectionService {
    private final Map<UUID, EnumMap<ProtectionType, Long>> protectionUntil = new ConcurrentHashMap<>();
    private final Map<String, ProtectionRule> rules = new LinkedHashMap<>();

    public ProtectionServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public void onEnable() {
        api.events().register(PlayerQuitEvent.class, event -> clearAll(event.getPlayer()));
        api.events().register(EntityDamageEvent.class, event -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            if (isProtected(player, ProtectionType.TELEPORT_DAMAGE)) {
                event.setCancelled(true);
            }
        }, EventPriority.HIGHEST, false);
    }

    @Override
    public boolean request(@NotNull Player player, @NotNull Duration duration, @NotNull ProtectionRequest request) {
        long durationMillis = Math.max(0L, duration.toMillis());
        ProtectionType type = request.type();
        if (durationMillis <= 0L) {
            clear(player, type);
            return false;
        }
        if (type == ProtectionType.TELEPORT_DAMAGE && player.getGameMode() != GameMode.SURVIVAL) {
            clear(player, type);
            return false;
        }
        for (ProtectionRule rule : rules.values()) {
            if (!rule.shouldApply(player, request)) {
                clear(player, type);
                return false;
            }
        }

        long until = System.currentTimeMillis() + durationMillis;
        protectionUntil
            .computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(ProtectionType.class))
            .put(type, until);

        long delayTicks = Math.max(1L, (long) Math.ceil(durationMillis / 50.0d));
        api.tasks().runOnceDelay(taskId(player.getUniqueId(), type), delayTicks, () -> expire(player.getUniqueId(), type, until));
        return true;
    }

    @Override
    public void clear(@NotNull Player player, @NotNull ProtectionType type) {
        clear(player.getUniqueId(), type);
    }

    @Override
    public boolean isProtected(@NotNull Player player, @NotNull ProtectionType type) {
        EnumMap<ProtectionType, Long> active = protectionUntil.get(player.getUniqueId());
        if (active == null) {
            return false;
        }

        Long until = active.get(type);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() < until) {
            return true;
        }

        clear(player.getUniqueId(), type);
        return false;
    }

    @Override
    public void registerRule(@NotNull String id, @NotNull ProtectionRule rule) {
        rules.put(id, rule);
    }

    @Override
    public void unregisterRule(@NotNull String id) {
        rules.remove(id);
    }

    private void clearAll(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        EnumMap<ProtectionType, Long> active = protectionUntil.remove(uuid);
        if (active == null) {
            return;
        }
        for (ProtectionType type : active.keySet()) {
            api.tasks().cancel(taskId(uuid, type));
        }
    }

    private void clear(@NotNull UUID uuid, @NotNull ProtectionType type) {
        EnumMap<ProtectionType, Long> active = protectionUntil.get(uuid);
        if (active != null) {
            active.remove(type);
            if (active.isEmpty()) {
                protectionUntil.remove(uuid);
            }
        }
        api.tasks().cancel(taskId(uuid, type));
    }

    private void expire(@NotNull UUID uuid, @NotNull ProtectionType type, long expectedUntil) {
        EnumMap<ProtectionType, Long> active = protectionUntil.get(uuid);
        if (active == null) {
            return;
        }

        Long currentUntil = active.get(type);
        if (currentUntil == null || currentUntil != expectedUntil) {
            return;
        }
        if (System.currentTimeMillis() < currentUntil) {
            return;
        }

        active.remove(type);
        if (active.isEmpty()) {
            protectionUntil.remove(uuid);
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline() && player.getGameMode() == GameMode.SURVIVAL && type == ProtectionType.TELEPORT_DAMAGE) {
            api.messages().info(player, "TELEPORT_DAMAGE_PROTECTION_ENDED");
        }
    }

    private @NotNull String taskId(@NotNull UUID uuid, @NotNull ProtectionType type) {
        return "protection:expire:" + type.name().toLowerCase() + ":" + uuid;
    }
}
