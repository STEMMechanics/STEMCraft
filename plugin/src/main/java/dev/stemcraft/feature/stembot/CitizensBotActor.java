package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import java.util.UUID;
import java.util.function.Function;
import static dev.stemcraft.integration.CitizensAccess.*;

/** Non-persistent, owner-filtered player NPC. Filtering is installed before the first spawn packet. */
public final class CitizensBotActor implements BotActor {
    private final Object npc;
    public CitizensBotActor(Plugin plugin,UUID owner,String name,Location spawn,BotSkinCache.Skin skin) {
        Object registry=invokeStatic("net.citizensnpcs.api.CitizensAPI","getTemporaryNPCRegistry");
        npc=invoke(registry,"createNPC",EntityType.PLAYER,name);
        try {
            Object filter=invoke(npc,"getOrAddTrait",type("net.citizensnpcs.api.trait.trait.PlayerFilter"));
            invoke(filter,"setPlayerFilter",(Function<Player,Boolean>)p->!owner.equals(p.getUniqueId()));
            Object data=invoke(npc,"data");
            invoke(data,"setPersistent","removefromplayerlist",true);
            invoke(data,"setPersistent","collidable",false);
            if(skin!=null) skin(skin);
            Object params=invoke(invoke(npc,"getNavigator"),"getDefaultParameters");
            invoke(params,"stuckAction",new Object[]{null}); // Never teleport through a blocked route.
            invoke(params,"range",128F);
            invoke(params,"avoidWater",true);
            invoke(params,"distanceMargin",.8D);
            invoke(params,"stationaryTicks",100);
            if(!(boolean)invoke(npc,"spawn",spawn)) throw new IllegalStateException("Citizens could not spawn STEMBot");
            var entity=(LivingEntity)invoke(npc,"getEntity");
            entity.setSilent(true); entity.setInvulnerable(true); entity.setCollidable(false);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            Player viewer=plugin.getServer().getPlayer(owner);
            if(viewer!=null) viewer.showEntity(plugin,entity);
        } catch(RuntimeException failure) { invoke(npc,"destroy"); throw failure; }
    }
    private Object navigator() { return invoke(npc,"getNavigator"); }
    private LivingEntity entity() { return (LivingEntity)invoke(npc,"getEntity"); }
    public void skin(BotSkinCache.Skin skin) {
        Object trait=invoke(npc,"getOrAddTrait",type("net.citizensnpcs.trait.SkinTrait"));
        invoke(trait,"setSkinPersistent","stembot",skin.signature(),skin.value());
    }
    public Location location() { return entity().getLocation(); }
    public boolean valid() { return (boolean)invoke(npc,"isSpawned")&&entity()!=null&&entity().isValid(); }
    public boolean navigating() { return (boolean)invoke(navigator(),"isNavigating"); }
    public void move(Location target,double speed) {
        invoke(invoke(navigator(),"getDefaultParameters"),"speedModifier",(float)speed);
        invoke(navigator(),"setTarget",target);
        if(entity() instanceof Player p) p.setSprinting(speed>1);
    }
    public void pause(boolean paused) { invoke(navigator(),"setPaused",paused);if(paused&&entity() instanceof Player p) p.setSprinting(false); }
    public void cancel() { invoke(navigator(),"cancelNavigation");if(entity() instanceof Player p) p.setSprinting(false); }
    public void face(float yaw) { entity().setRotation(yaw,0); }
    public void sneak(boolean sneaking) {
        invoke(invoke(npc,"getOrAddTrait",type("net.citizensnpcs.trait.SneakTrait")),"setSneaking",sneaking);
    }
    @SuppressWarnings("deprecation")
    public void jump() {
        if(entity().isOnGround()) entity().setVelocity(entity().getVelocity().setY(.36));
    }
    public void close() { invoke(npc,"destroy"); }
}
