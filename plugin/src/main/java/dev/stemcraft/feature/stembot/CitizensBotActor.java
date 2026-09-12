package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static dev.stemcraft.integration.CitizensAccess.*;

/** Non-persistent, owner-filtered Citizens player NPC. */
public final class CitizensBotActor implements BotActor {
    private final Plugin plugin;
    private final UUID owner;
    private final Object npc;

    public CitizensBotActor(Plugin plugin,UUID owner,String name,Location spawn,BotSkinCache.Skin skin) {
        this.plugin=plugin;
        this.owner=owner;

        Object registry=invokeStatic(
            "net.citizensnpcs.api.CitizensAPI",
            "getTemporaryNPCRegistry"
        );

        npc=invoke(registry,"createNPC",EntityType.PLAYER,name);

        try {
            Object filter=invoke(
                npc,
                "getOrAddTrait",
                type("net.citizensnpcs.api.trait.trait.PlayerFilter")
            );
            invoke(
                filter,
                "setPlayerFilter",
                (Function<Player,Boolean>)p->!owner.equals(p.getUniqueId())
            );

            Object data=invoke(npc,"data");
            invoke(data,"setPersistent","removefromplayerlist",true);
            invoke(data,"setPersistent","collidable",false);

            if(skin!=null) skin(skin);

            Object params=invoke(invoke(npc,"getNavigator"),"getDefaultParameters");
            invoke(params,"stuckAction",new Object[]{null});
            invoke(params,"range",128F);
            invoke(params,"avoidWater",true);
            invoke(params,"distanceMargin",.8D);
            invoke(params,"stationaryTicks",100);

            if(!(boolean)invoke(npc,"spawn",spawn))
                throw new IllegalStateException("Citizens could not spawn STEMBot");

            LivingEntity entity=entity();
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);

            Player viewer=plugin.getServer().getPlayer(owner);
            if(viewer!=null) viewer.showEntity(plugin,entity);
        } catch(RuntimeException failure) {
            invoke(npc,"destroy");
            throw failure;
        }
    }

    private Object navigator() {
        return invoke(npc,"getNavigator");
    }

    private LivingEntity entity() {
        return (LivingEntity)invoke(npc,"getEntity");
    }

    @Override
    public void skin(BotSkinCache.Skin skin) {
        Object trait=invoke(
            npc,
            "getOrAddTrait",
            type("net.citizensnpcs.trait.SkinTrait")
        );
        invoke(
            trait,
            "setSkinPersistent",
            "stembot",
            skin.signature(),
            skin.value()
        );
    }

    @Override
    public Location location() {
        return entity().getLocation();
    }

    @Override
    public boolean valid() {
        return (boolean)invoke(npc,"isSpawned")
            &&entity()!=null
            &&entity().isValid();
    }

    @Override
    public boolean navigating() {
        return (boolean)invoke(navigator(),"isNavigating");
    }

    @Override
    public void move(Location target,double speed) {
        invoke(
            invoke(navigator(),"getDefaultParameters"),
            "speedModifier",
            (float)speed
        );
        invoke(navigator(),"setTarget",target);

        if(entity() instanceof Player player)
            player.setSprinting(speed>1);
    }

    @Override
    public List<Location> recoveryWaypoints(Location target) {
        return BotNavigationRecovery.candidates(location(), target);
    }

    @Override
    public boolean canNavigateTo(Location target) {
        return (boolean) invoke(navigator(), "canNavigateTo", target);
    }

    @Override
    public void pause(boolean paused) {
        invoke(navigator(),"setPaused",paused);
        if(paused&&entity() instanceof Player player)
            player.setSprinting(false);
    }

    @Override
    public void cancel() {
        invoke(navigator(),"cancelNavigation");
        if(entity() instanceof Player player)
            player.setSprinting(false);
    }

    @Override
    public void face(float yaw) {
        entity().setRotation(yaw,entity().getPitch());
    }

    @Override
    public void lookAt(Location target) {
        Location from=entity().getEyeLocation();
        Vector direction=target.toVector().subtract(from.toVector());

        if(direction.lengthSquared()<0.0001) return;

        double x=direction.getX();
        double y=direction.getY();
        double z=direction.getZ();
        double horizontal=Math.sqrt(x*x+z*z);

        float yaw=(float)Math.toDegrees(Math.atan2(-x,z));
        float pitch=(float)Math.toDegrees(Math.atan2(-y,horizontal));

        entity().setRotation(yaw,pitch);
    }

    @Override
    public void sneak(boolean sneaking) {
        invoke(
            invoke(
                npc,
                "getOrAddTrait",
                type("net.citizensnpcs.trait.SneakTrait")
            ),
            "setSneaking",
            sneaking
        );
    }

    /**
     * Citizens' PlayerAnimation is used through the optional integration boundary.
     * Examples: ARM_SWING, ARM_SWING_OFFHAND, EAT_FOOD.
     */
    @Override
    @SuppressWarnings({"rawtypes","unchecked"})
    public void animate(String animation) {
        if(!(entity() instanceof Player npcPlayer)) return;

        Player viewer=plugin.getServer().getPlayer(owner);
        if(viewer==null) return;

        Class<?> animationType=type("net.citizensnpcs.util.PlayerAnimation");
        Object value=Enum.valueOf((Class<? extends Enum>)animationType,animation);

        invokeStatic(
            "net.citizensnpcs.util.NMS",
            "playAnimation",
            value,
            npcPlayer,
            List.of(viewer)
        );
    }

    @Override
    public void puff() {
        Player viewer=plugin.getServer().getPlayer(owner);
        if(viewer==null||!valid()||!viewer.getWorld().equals(location().getWorld())) return;
        // Like the NPC itself, the effect is visible only to its owner.
        viewer.spawnParticle(Particle.CLOUD,location().add(0,0.9,0),24,0.35,0.65,0.35,0.025);
    }

    @Override
    public void close() {
        invoke(npc,"destroy");
    }
}
