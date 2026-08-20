package dev.stemcraft.feature.quest;

import dev.stemcraft.STEMCraft;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.waypoint.WanderWaypointProvider;
import net.citizensnpcs.trait.waypoint.Waypoints;
import net.citizensnpcs.util.MojangSkinGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.json.simple.JSONObject;

import java.util.concurrent.CompletableFuture;

/** Optional Citizens bridge. This class is only loaded when Citizens is enabled. */
public final class CitizensQuestNpcSupport {
    private static final String SKIN_URL_METADATA = "stemcraft.quest.skin-url";

    private CitizensQuestNpcSupport() { }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens") && CitizensAPI.hasImplementation();
    }

    public static Entity spawn(QuestNpcProfile profile, Location location, NamespacedKey profileKey) {
        NPC npc = profile.citizensNpcId() == null ? null
            : CitizensAPI.getNPCRegistry().getById(profile.citizensNpcId());
        boolean created = npc == null || npc.getCosmeticEntityType() != EntityType.PLAYER;
        if (created) {
            if (npc != null) npc.destroy();
            npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, profile.name());
            profile.citizensNpcId(npc.getId());
        } else {
            npc.setName(profile.name());
        }
        if (!npc.isSpawned() && !npc.spawn(location)) return null;
        configureBehaviour(npc, profile);
        Entity entity = npc.getEntity();
        entity.getPersistentDataContainer().set(profileKey, PersistentDataType.STRING, profile.id());
        if (created && profile.skinUrl() != null) applySkin(npc, profile.skinUrl());
        return entity;
    }

    public static Entity spawnedEntity(QuestNpcProfile profile) {
        if (profile.citizensNpcId() == null) return null;
        NPC npc = CitizensAPI.getNPCRegistry().getById(profile.citizensNpcId());
        return npc != null && npc.isSpawned() ? npc.getEntity() : null;
    }

    public static boolean isCitizensNpc(Entity entity) {
        return CitizensAPI.getNPCRegistry().getNPC(entity) != null;
    }

    public static Location storedLocation(QuestNpcProfile profile) {
        if (profile.citizensNpcId() == null) return null;
        NPC npc = CitizensAPI.getNPCRegistry().getById(profile.citizensNpcId());
        return npc == null ? null : npc.getStoredLocation();
    }

    public static void despawn(QuestNpcProfile profile, boolean destroy) {
        if (profile.citizensNpcId() == null) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(profile.citizensNpcId());
        if (npc == null) return;
        if (destroy) {
            npc.destroy();
            profile.citizensNpcId(null);
        } else if (npc.isSpawned()) {
            npc.despawn();
        }
    }

    private static void configureBehaviour(NPC npc, QuestNpcProfile profile) {
        Waypoints waypoints = npc.getOrAddTrait(Waypoints.class);
        if (profile.behaviour() == QuestNpcProfile.Behaviour.WANDER) {
            waypoints.setWaypointProvider("wander");
            WanderWaypointProvider wander = (WanderWaypointProvider) waypoints.getCurrentProvider();
            wander.setXYRange(profile.wanderRadius(), profile.wanderVerticalRadius());
            wander.setDelay(profile.wanderDelaySeconds() * 20);
        } else {
            waypoints.setWaypointProvider(null);
            npc.getNavigator().cancelNavigation();
        }
        LookClose look = npc.getOrAddTrait(LookClose.class);
        if (look.isEnabled() != profile.lookAtPlayers()) look.toggle();
        look.setRange(Math.max(4, profile.wanderRadius() * 2D));
        look.setDisableWhileNavigating(true);
    }

    private static void applySkin(NPC npc, String url) {
        npc.data().setPersistent(SKIN_URL_METADATA, url);
        CompletableFuture.supplyAsync(() -> {
            try { return MojangSkinGenerator.generateFromURL(url, false); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        }).thenAccept(data -> Bukkit.getScheduler().runTask(STEMCraft.getPlugin(), () -> {
            if (data == null) return;
            JSONObject texture = (JSONObject) data.get("texture");
            if (texture == null) return;
            String id = String.valueOf(data.get("uuid"));
            npc.getOrAddTrait(SkinTrait.class).setSkinPersistent(id,
                String.valueOf(texture.get("signature")), String.valueOf(texture.get("value")));
        })).exceptionally(error -> {
            STEMCraft.getPlugin().getLogger().warning("Could not apply Citizens skin " + url + ": " + error.getMessage());
            return null;
        });
    }
}
