package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.feature.quest.CitizensQuestNpcSupport;
import dev.stemcraft.feature.stembot.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Private, configurable helper sessions; feature discovery supplies lifecycle and reload integration. */
public final class StemBotFeature extends BaseFeature {
    private static final String TASK="feature:stembot";
    private final Map<UUID,BotSession> sessions=new ConcurrentHashMap<>();
    private final Set<AsyncChatEvent> privateChat=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Map<UUID,Integer> firstJoins=new HashMap<>();
    private final Map<UUID,UUID> starting=new HashMap<>();
    private final BotSkinCache skins=new BotSkinCache();
    private BotScript script;
    private boolean enabled;
    private long epoch;
    private NamespacedKey pendingKey;
    public StemBotFeature(STEMCraftAPI api) { super(api); }
    @Override public String id() { return "stembot"; }
    @Override public void onEnable() {
        pendingKey=new NamespacedKey(STEMCraft.getPlugin(),"stembot-introduction-pending");
        load();
        api.commands().create("stembot").description("Talk privately to your STEMBot guide.")
            .usage("/stembot [tour|close]").tabCompletion("tour").tabCompletion("close")
            .executor((unused,command,ctx)->{
                ctx.checkNotConsole();
                Player player=ctx.asPlayer();
                String action=ctx.getArg(0,"").toLowerCase(Locale.ROOT);
                if(action.equals("close")) { close(player.getUniqueId());return; }
                if(!action.isEmpty()&&!action.equals("tour")) { ctx.returnUsage();return; }
                if(!enabled||script==null||!CitizensQuestNpcSupport.available()) { ctx.returnError("STEMBot is resting right now.");return; }
                if(verifying(player)) { ctx.returnError("Finish the welcome check first, then ask STEMBot for help.");return; }
                close(player.getUniqueId());
                start(player,false,action.equals("tour"));
            }).register(STEMCraft.getPlugin());
        api.events().register(PlayerJoinEvent.class,event->{
            Player p=event.getPlayer();
            if(!p.hasPlayedBefore()&&script!=null&&p.getWorld().getName().equals(script.firstWorld()))
                p.getPersistentDataContainer().set(pendingKey,PersistentDataType.BYTE,(byte)1);
            if(p.getPersistentDataContainer().has(pendingKey)) firstJoins.put(p.getUniqueId(),40);
        },EventPriority.MONITOR,true);
        api.events().register(PlayerQuitEvent.class,e->{ firstJoins.remove(e.getPlayer().getUniqueId());starting.remove(e.getPlayer().getUniqueId());close(e.getPlayer().getUniqueId()); });
        api.events().register(PlayerChangedWorldEvent.class,e->close(e.getPlayer().getUniqueId()));
        api.events().register(org.bukkit.event.entity.PlayerDeathEvent.class,e->close(e.getEntity().getUniqueId()));
        api.events().register(PluginDisableEvent.class,e->{ if(e.getPlugin().getName().equals("Citizens")) stop(); });
        api.events().register(AsyncChatEvent.class,this::chat,EventPriority.LOWEST,false);
        // Keep the audience empty even if an ordinary formatter changes cancellation later.
        api.events().register(AsyncChatEvent.class,e->{ if(isPrivateChat(e)) { e.setCancelled(true);e.viewers().clear(); } },EventPriority.HIGHEST,false);
        api.tasks().repeating(TASK,5L,5L,this::tick);
    }
    public boolean hasActiveSession(UUID player) { return sessions.containsKey(player); }
    public boolean isPrivateChat(AsyncChatEvent event) {
        return privateChat.contains(event)||hasActiveSession(event.getPlayer().getUniqueId());
    }
    void chat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        BotSession session=sessions.get(id);
        if(session==null) return;
        privateChat.add(event);event.setCancelled(true);event.viewers().clear();
        String text=PlainTextComponentSerializer.plainText().serialize(event.message());
        api.tasks().nextTick(()->{
            if(sessions.get(id)!=session) return;
            Player player=Bukkit.getPlayer(id);
            if(player==null) return;
            // Component.text keeps regex input out of markup and commands.
            player.sendMessage(Component.text("You → STEMBot: "+text,NamedTextColor.GRAY));
            session.input(text);
            if(session.closed()) sessions.remove(id,session);
        });
    }
    private void load() {
        enabled=getConfigSection().getBoolean("enabled",true);
        File file=new File(api.getDataFolder(),"stembot.yml");
        if(!file.exists()) STEMCraft.getPlugin().saveResource("stembot.yml",false);
        ConfigFile config=api.config().load(file);
        try {
            script=BotScript.read(config);
            if(enabled&&CitizensQuestNpcSupport.available()) skins.load(api,STEMCraft.getPlugin(),script,skin->{});
        } catch(RuntimeException error) {
            enabled=false;script=null;
            STEMCraft.getPlugin().getLogger().log(java.util.logging.Level.SEVERE,"Invalid stembot.yml; STEMBot is disabled",error);
        }
    }
    private void tick() {
        if(!enabled||script==null) return;
        for(var entry:new ArrayList<>(firstJoins.entrySet())) {
            Player p=Bukkit.getPlayer(entry.getKey());
            if(p==null) { firstJoins.remove(entry.getKey());continue; }
            if(verifying(p)||!CitizensQuestNpcSupport.available()) continue;
            int remaining=entry.getValue()-5;
            if(remaining>0) { firstJoins.put(entry.getKey(),remaining);continue; }
            firstJoins.remove(entry.getKey());
            if(p.getWorld().getName().equals(script.firstWorld())) start(p,true,false);
        }
        for(var entry:new ArrayList<>(sessions.entrySet())) {
            Player p=Bukkit.getPlayer(entry.getKey());
            if(p==null) { close(entry.getKey());continue; }
            try { entry.getValue().tick(p.getLocation());if(entry.getValue().closed()) sessions.remove(entry.getKey(),entry.getValue()); }
            catch(RuntimeException error) {
                close(entry.getKey());
                STEMCraft.getPlugin().getLogger().log(java.util.logging.Level.WARNING,"STEMBot session stopped",error);
            }
        }
    }
    private boolean verifying(Player player) {
        var first=STEMCraft.getPlugin().firstJoin();
        return first!=null&&first.hasActiveSession(player.getUniqueId());
    }
    private void start(Player player,boolean introduction,boolean tour) {
        UUID id=player.getUniqueId();
        UUID startId=UUID.randomUUID();
        if(starting.putIfAbsent(id,startId)!=null) return;
        long request=epoch;
        World world=player.getWorld();
        Location from=player.getLocation();
        var point=script.firstSpawn();
        Location spawn=introduction?new Location(world,point.x(),point.y(),point.z(),point.yaw(),0):from.clone();
        world.getChunkAtAsync(spawn.getBlockX()>>4,spawn.getBlockZ()>>4,true).whenComplete((chunk,error)->api.tasks().nextTick(()->{
            if(request!=epoch||!starting.remove(id,startId)||!enabled||!player.isOnline()||player.getWorld()!=world) return;
            if(error!=null||verifying(player)||!CitizensQuestNpcSupport.available()) return;
            if(!introduction&&player.getLocation().distanceSquared(from)>16) return;
            if(introduction&&!safe(spawn)) { player.sendMessage(Component.text("STEMBot cannot find a safe welcome spot. Please ask a staff member.",NamedTextColor.YELLOW));return; }
            if(introduction&&!player.teleport(spawn)) return;
            var direction=spawn.getDirection().setY(0).normalize().multiply(script.spawnDistance());
            Location front=spawn.clone().add(direction);
            Location actorSpawn=null;
            for(int dy:new int[]{0,-1,1,-2,2}) {
                Location candidate=front.clone().add(0,dy,0);
                if(safe(candidate)) { actorSpawn=candidate;break; }
            }
            if(actorSpawn==null) { player.sendMessage(Component.text("Give me a little clear ground in front of you, then summon me again.",NamedTextColor.YELLOW));return; }
            actorSpawn.setDirection(spawn.toVector().subtract(actorSpawn.toVector()));
            try {
                BotActor actor=new CitizensBotActor(STEMCraft.getPlugin(),id,script.name(),actorSpawn,skins.current());
                BotSession session=new BotSession(script,actor,world.getName(),lines->say(player,lines));
                sessions.put(id,session);
                if(introduction) player.getPersistentDataContainer().remove(pendingKey);
                if(tour) session.startTour();
            } catch(RuntimeException failure) {
                player.sendMessage(Component.text("STEMBot could not join you just now.",NamedTextColor.YELLOW));
                STEMCraft.getPlugin().getLogger().log(java.util.logging.Level.WARNING,"Could not summon STEMBot",failure);
            }
        }));
    }
    static boolean safe(Location at) {
        World w=at.getWorld();int x=at.getBlockX(),y=at.getBlockY(),z=at.getBlockZ();
        if(y<=w.getMinHeight()||y>=w.getMaxHeight()-1||!w.isChunkLoaded(x>>4,z>>4)||!w.getWorldBorder().isInside(at)) return false;
        Material floor=w.getBlockAt(x,y-1,z).getType();
        return floor.isSolid()&&floor.isOccluding()&&floor!=Material.MAGMA_BLOCK
            &&w.getBlockAt(x,y,z).getType().isAir()&&w.getBlockAt(x,y+1,z).getType().isAir();
    }
    private void say(Player player,List<String> lines) {
        if(!player.isOnline()) return;
        for(String line:lines) player.sendMessage(Component.text(script.name()+": ",NamedTextColor.AQUA)
            .append(Component.text(line.replace("{player}",player.getName()),NamedTextColor.WHITE)));
    }
    private void close(UUID id) {
        starting.remove(id);
        BotSession session=sessions.remove(id);
        if(session!=null) session.close();
    }
    private void stop() {
        epoch++;starting.clear();firstJoins.clear();skins.close();
        for(UUID id:List.copyOf(sessions.keySet())) close(id);
    }
    @Override public void onReload() { stop();super.onReload();load(); }
    @Override public void onDisable() { stop();api.tasks().cancel(TASK); }
}
