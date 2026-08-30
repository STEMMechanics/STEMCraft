package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.feature.quest.CitizensQuestNpcSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Suspends non-quest Citizens NPCs while no players are close enough to see or interact with them. */
public final class CitizensNpcProximityFeature extends BaseFeature {
    private static final String TASK_ID = "feature:citizens-npc-proximity";
    private final Map<Integer, Location> proximityDespawned = new HashMap<>();
    private NamespacedKey questProfileKey;
    private double spawnRadius;
    private double despawnRadius;
    private long checkTicks;

    public CitizensNpcProximityFeature(STEMCraftAPI api) { super(api); }

    @Override public void onEnable() {
        questProfileKey = new NamespacedKey(STEMCraft.getPlugin(), "quest-npc-profile");
        loadSettings();
        if (!CitizensQuestNpcSupport.available()) return;
        api.tasks().repeating(TASK_ID, 20L, checkTicks, this::updateNpcs);
    }

    @Override public void onReload() {
        super.onReload();loadSettings();api.tasks().cancel(TASK_ID);
        if (CitizensQuestNpcSupport.available()) api.tasks().repeating(TASK_ID, 20L, checkTicks, this::updateNpcs);
    }

    @Override public void onDisable() {
        api.tasks().cancel(TASK_ID);
        for (Map.Entry<Integer,Location> entry : new HashMap<>(proximityDespawned).entrySet())
            CitizensQuestNpcSupport.spawnNpc(entry.getKey(),entry.getValue());
        proximityDespawned.clear();
    }

    private void loadSettings() {
        spawnRadius=Math.max(8D,getConfigSection().getDouble("spawn-radius",96D));
        despawnRadius=Math.max(spawnRadius,getConfigSection().getDouble("despawn-radius",128D));
        checkTicks=Math.max(20L,getConfigSection().getLong("check-ticks",100L));
    }

    private void updateNpcs() {
        Set<Integer> present=new HashSet<>();
        for (CitizensQuestNpcSupport.ProximityNpc npc : CitizensQuestNpcSupport.allNpcs()) {
            present.add(npc.id());
            if (npc.spawned()) {
                if (isQuestNpc(npc.entity()) || npc.location()==null || hasNearbyPlayer(npc.location(),despawnRadius)) continue;
                proximityDespawned.put(npc.id(),npc.location());CitizensQuestNpcSupport.despawnNpc(npc.id());
            } else {
                Location stored=proximityDespawned.get(npc.id());
                if(stored!=null&&hasNearbyPlayer(stored,spawnRadius)&&CitizensQuestNpcSupport.spawnNpc(npc.id(),stored))
                    proximityDespawned.remove(npc.id());
            }
        }
        proximityDespawned.keySet().removeIf(id->!present.contains(id));
    }

    private boolean isQuestNpc(Entity entity) {
        return entity!=null&&entity.getPersistentDataContainer().has(questProfileKey,PersistentDataType.STRING);
    }

    private boolean hasNearbyPlayer(Location location,double radius) {
        if(location.getWorld()==null)return false;double radiusSquared=radius*radius;
        for(Player player:location.getWorld().getPlayers())if(player.getLocation().distanceSquared(location)<=radiusSquared)return true;
        return false;
    }
}
