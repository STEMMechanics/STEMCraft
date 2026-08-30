package dev.stemcraft.feature.quest;

import dev.stemcraft.STEMCraft;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.json.simple.JSONObject;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/** Optional Citizens bridge. This class is only loaded when Citizens is enabled. */
public final class CitizensQuestNpcSupport {
    private static final String SKIN_URL_METADATA = "stemcraft.quest.skin-url";

    private CitizensQuestNpcSupport() { }

    public static boolean available() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) return false;
        try {
            return (boolean) invokeStatic("net.citizensnpcs.api.CitizensAPI", "hasImplementation");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static Entity spawn(QuestNpcProfile profile, Location location, NamespacedKey profileKey) {
        Object registry = registry();
        Object npc = profile.citizensNpcId() == null ? null
            : invoke(registry, "getById", profile.citizensNpcId());
        boolean created = npc == null || invoke(npc, "getCosmeticEntityType") != EntityType.PLAYER;
        if (created) {
            if (npc != null) invoke(npc, "destroy");
            npc = invoke(registry, "createNPC", EntityType.PLAYER, profile.name());
            profile.citizensNpcId((int) invoke(npc, "getId"));
        } else {
            invoke(npc, "setName", profile.name());
        }
        if (!(boolean) invoke(npc, "isSpawned") && !(boolean) invoke(npc, "spawn", location)) return null;
        configureBehaviour(npc, profile);
        Entity entity = (Entity) invoke(npc, "getEntity");
        entity.getPersistentDataContainer().set(profileKey, PersistentDataType.STRING, profile.id());
        if (created && profile.skinUrl() != null) applySkin(npc, profile.skinUrl());
        return entity;
    }

    public static Entity spawnedEntity(QuestNpcProfile profile) {
        if (profile.citizensNpcId() == null) return null;
        Object npc = invoke(registry(), "getById", profile.citizensNpcId());
        return npc != null && (boolean) invoke(npc, "isSpawned") ? (Entity) invoke(npc, "getEntity") : null;
    }

    public static boolean isCitizensNpc(Entity entity) {
        return invoke(registry(), "getNPC", entity) != null;
    }

    public static Location storedLocation(QuestNpcProfile profile) {
        if (profile.citizensNpcId() == null) return null;
        Object npc = invoke(registry(), "getById", profile.citizensNpcId());
        return npc == null ? null : (Location) invoke(npc, "getStoredLocation");
    }

    public static void despawn(QuestNpcProfile profile, boolean destroy) {
        if (profile.citizensNpcId() == null) return;
        Object npc = invoke(registry(), "getById", profile.citizensNpcId());
        if (npc == null) return;
        if (destroy) {
            invoke(npc, "destroy");
            profile.citizensNpcId(null);
        } else if ((boolean) invoke(npc, "isSpawned")) {
            invoke(npc, "despawn");
        }
    }

    public static void setPaused(QuestNpcProfile profile, boolean paused) {
        if (profile.citizensNpcId() == null) return;
        Object npc = invoke(registry(), "getById", profile.citizensNpcId());
        if (npc == null || !(boolean) invoke(npc, "isSpawned")) return;
        invoke(invoke(npc, "getNavigator"), "setPaused", paused);
    }

    public static boolean isNavigating(QuestNpcProfile profile) {
        if (profile.citizensNpcId() == null) return false;
        Object npc = invoke(registry(), "getById", profile.citizensNpcId());
        return npc != null && (boolean) invoke(npc, "isSpawned")
            && (boolean) invoke(invoke(npc, "getNavigator"), "isNavigating");
    }

    public static void moveTo(QuestNpcProfile profile, Location destination, double speed) {
        if (profile.citizensNpcId() == null) return;
        Object npc = invoke(registry(), "getById", profile.citizensNpcId());
        if (npc == null || !(boolean) invoke(npc, "isSpawned")) return;
        Object navigator = invoke(npc, "getNavigator");
        invoke(invoke(navigator, "getDefaultParameters"), "speedModifier", (float) speed);
        invoke(navigator, "setTarget", destination);
    }

    private static void configureBehaviour(Object npc, QuestNpcProfile profile) {
        Object waypoints = invoke(npc, "getOrAddTrait", type("net.citizensnpcs.trait.waypoint.Waypoints"));
        invoke(waypoints, "setWaypointProvider", new Object[] { null });
        invoke(invoke(npc, "getNavigator"), "cancelNavigation");
        Object look = invoke(npc, "getOrAddTrait", type("net.citizensnpcs.trait.LookClose"));
        if ((boolean) invoke(look, "isEnabled") != profile.lookAtPlayers()) invoke(look, "toggle");
        invoke(look, "setRange", Math.max(4, profile.wanderRadius() * 2D));
        invoke(look, "setDisableWhileNavigating", true);
    }

    private static void applySkin(Object npc, String url) {
        invoke(invoke(npc, "data"), "setPersistent", SKIN_URL_METADATA, url);
        CompletableFuture.supplyAsync(() -> {
            try { return (JSONObject) invokeStatic("net.citizensnpcs.util.MojangSkinGenerator",
                "generateFromURL", url, false); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        }).thenAccept(data -> Bukkit.getScheduler().runTask(STEMCraft.getPlugin(), () -> {
            if (data == null) return;
            JSONObject texture = (JSONObject) data.get("texture");
            if (texture == null) return;
            String id = String.valueOf(data.get("uuid"));
            Object skin = invoke(npc, "getOrAddTrait", type("net.citizensnpcs.trait.SkinTrait"));
            invoke(skin, "setSkinPersistent", id,
                String.valueOf(texture.get("signature")), String.valueOf(texture.get("value")));
        })).exceptionally(error -> {
            STEMCraft.getPlugin().getLogger().warning("Could not apply Citizens skin " + url + ": " + error.getMessage());
            return null;
        });
    }

    private static Object registry() {
        return invokeStatic("net.citizensnpcs.api.CitizensAPI", "getNPCRegistry");
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, true, CitizensQuestNpcSupport.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Citizens API is unavailable", ex);
        }
    }

    private static Object invokeStatic(String className, String method, Object... arguments) {
        return invoke(type(className), null, method, arguments);
    }

    private static Object invoke(Object target, String method, Object... arguments) {
        return invoke(target.getClass(), target, method, arguments);
    }

    private static Object invoke(Class<?> owner, Object target, String method, Object... arguments) {
        for (Method candidate : owner.getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != arguments.length) continue;
            try {
                return candidate.invoke(target, arguments);
            } catch (IllegalArgumentException ignored) {
                // Try another overload with the same arity.
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Could not call Citizens method " + method, ex);
            }
        }
        throw new IllegalStateException("Citizens method not found: " + owner.getName() + "." + method);
    }
}
