package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Leads non-combatant Iron Golems using a main-hand Poppy and normal pathfinding. */
public final class IronGolemPoppyFeature extends BaseFeature {
    private static final String TASK_ID = "feature:iron-golem-poppy";
    private final Map<UUID, UUID> targets = new HashMap<>();

    private double range;
    private double pathSpeed;
    private double switchAdvantage;
    private long updateTicks;

    public IronGolemPoppyFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        loadSettings();
        api.events().register(EntityTargetLivingEntityEvent.class, event -> {
            if (event.getEntity() instanceof IronGolem golem && event.getTarget() != null) release(golem.getUniqueId());
        }, EventPriority.MONITOR, true);
        api.events().register(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof IronGolem) targets.remove(event.getEntity().getUniqueId());
        });
        api.events().register(ChunkUnloadEvent.class, event -> {
            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof IronGolem) targets.remove(entity.getUniqueId());
            }
        });
        api.events().register(PlayerQuitEvent.class, event -> releasePlayer(event.getPlayer().getUniqueId()));
        api.tasks().repeating(TASK_ID, updateTicks, this::updateLures);
    }

    @Override
    public void onReload() {
        super.onReload();
        loadSettings();
        api.tasks().cancel(TASK_ID);
        api.tasks().repeating(TASK_ID, updateTicks, this::updateLures);
    }

    @Override
    public void onDisable() {
        api.tasks().cancel(TASK_ID);
        targets.keySet().forEach(this::stopPath);
        targets.clear();
    }

    private void loadSettings() {
        var config = getConfigSection();
        range = Math.max(1.0, config.getDouble("range", 10.0));
        pathSpeed = Math.max(0.1, config.getDouble("path-speed", 1.0));
        switchAdvantage = Math.max(0.0, config.getDouble("switch-advantage", 2.0));
        updateTicks = Math.max(5L, config.getLong("update-ticks", 10L));
    }

    private void updateLures() {
        Map<UUID, Candidate> candidates = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().getItemInMainHand().getType() != Material.POPPY) continue;
            for (Entity nearby : player.getNearbyEntities(range, range, range)) {
                if (!(nearby instanceof IronGolem golem) || !golem.isValid() || golem.isDead()
                    || golem.getTarget() != null) continue;
                double distance = golem.getLocation().distance(player.getLocation());
                Candidate current = candidates.get(golem.getUniqueId());
                if (current == null || distance < current.distance()) {
                    candidates.put(golem.getUniqueId(), new Candidate(player, distance));
                }
            }
        }

        for (Map.Entry<UUID, UUID> entry : new HashMap<>(targets).entrySet()) {
            IronGolem golem = entity(entry.getKey(), IronGolem.class);
            Player current = Bukkit.getPlayer(entry.getValue());
            Candidate nearest = candidates.get(entry.getKey());
            if (!isValid(golem, current)) {
                release(entry.getKey());
                continue;
            }
            double currentDistance = golem.getLocation().distance(current.getLocation());
            if (nearest != null && nearest.player() != current
                && nearest.distance() + switchAdvantage < currentDistance) {
                targets.put(entry.getKey(), nearest.player().getUniqueId());
            }
            candidates.remove(entry.getKey());
        }
        for (Map.Entry<UUID, Candidate> entry : candidates.entrySet()) {
            targets.put(entry.getKey(), entry.getValue().player().getUniqueId());
        }

        for (Map.Entry<UUID, UUID> entry : new HashMap<>(targets).entrySet()) {
            IronGolem golem = entity(entry.getKey(), IronGolem.class);
            Player player = Bukkit.getPlayer(entry.getValue());
            if (!isValid(golem, player)) {
                release(entry.getKey());
                continue;
            }
            if (golem.getLocation().distanceSquared(player.getLocation()) <= 4.0) {
                golem.getPathfinder().stopPathfinding();
            } else {
                golem.getPathfinder().moveTo(player, pathSpeed);
            }
        }
    }

    private boolean isValid(IronGolem golem, Player player) {
        return golem != null && player != null && player.isOnline() && golem.getTarget() == null
            && player.getInventory().getItemInMainHand().getType() == Material.POPPY
            && golem.getWorld().equals(player.getWorld())
            && golem.getLocation().distanceSquared(player.getLocation()) <= range * range;
    }

    private void releasePlayer(UUID playerId) {
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(targets).entrySet()) {
            if (entry.getValue().equals(playerId)) release(entry.getKey());
        }
    }

    private void release(UUID golemId) {
        targets.remove(golemId);
        stopPath(golemId);
    }

    private void stopPath(UUID golemId) {
        IronGolem golem = entity(golemId, IronGolem.class);
        if (golem != null) golem.getPathfinder().stopPathfinding();
    }

    private static <T extends Entity> T entity(UUID id, Class<T> type) {
        Entity entity = Bukkit.getEntity(id);
        return type.isInstance(entity) && entity.isValid() ? type.cast(entity) : null;
    }

    private record Candidate(Player player, double distance) { }
}
