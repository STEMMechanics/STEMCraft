package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.util.TextUtil;
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
import java.util.concurrent.ThreadLocalRandom;

/** Private scripted STEMBot sessions. */
public final class StemBotFeature extends BaseFeature {
    private static final String TASK="feature:stembot";

    private final Map<UUID,BotSession> sessions=new ConcurrentHashMap<>();
    private final Set<AsyncChatEvent> privateChat=
        Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Map<UUID,Integer> pendingFirstTime=new HashMap<>();
    private final BotSkinCache skins=new BotSkinCache();

    private BotScript script;
    private boolean enabled;
    private long epoch;
    private NamespacedKey firstWorldsKey;

    public StemBotFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public String id() {
        return "stembot";
    }

    @Override
    public void onEnable() {
        firstWorldsKey=new NamespacedKey(STEMCraft.getPlugin(),"stembot-first-worlds");
        load();

        api.commands().create("stembot")
            .description("Talk privately to your STEMBot guide.")
            .usage("/stembot [close]")
            .tabCompletion("close")
            .executor((unused,command,ctx)->{
                ctx.checkNotConsole();
                Player player=ctx.asPlayer();

                if(ctx.getArg(0,"").equalsIgnoreCase("close")) {
                    close(player.getUniqueId());
                    return;
                }

                if(!ctx.getArg(0,"").isBlank()) {
                    ctx.returnUsage();
                    return;
                }

                if(!available()) {
                    ctx.returnError("STEMBot is resting right now.");
                    return;
                }

                if(verifying(player)) {
                    ctx.returnError(
                        "Finish the welcome check first, then ask STEMBot for help.");
                    return;
                }

                String action=script.commandAction(player.getWorld().getName());
                if(action==null) {
                    ctx.returnError("STEMBot has no script for this world yet.");
                    return;
                }

                close(player.getUniqueId());
                start(player,action,false);
            })
            .register(STEMCraft.getPlugin());

        api.events().register(
            PlayerJoinEvent.class,
            event->queueFirstTime(event.getPlayer(),40),
            EventPriority.MONITOR,
            true
        );

        api.events().register(PlayerChangedWorldEvent.class,event->{
            close(event.getPlayer().getUniqueId());
            queueFirstTime(event.getPlayer(),20);
        });

        api.events().register(PlayerQuitEvent.class,event->{
            pendingFirstTime.remove(event.getPlayer().getUniqueId());
            close(event.getPlayer().getUniqueId());
        });

        api.events().register(
            org.bukkit.event.entity.PlayerDeathEvent.class,
            event->close(event.getEntity().getUniqueId())
        );

        api.events().register(PluginDisableEvent.class,event->{
            if(event.getPlugin().getName().equals("Citizens"))
                stop();
        });

        api.events().register(
            AsyncChatEvent.class,
            this::chat,
            EventPriority.LOWEST,
            false
        );

        // STEMBot chat must never leak to ordinary chat viewers.
        api.events().register(
            AsyncChatEvent.class,
            event->{
                if(isPrivateChat(event)) {
                    event.setCancelled(true);
                    event.viewers().clear();
                }
            },
            EventPriority.HIGHEST,
            false
        );

        api.tasks().repeating(TASK,5L,5L,this::tick);
    }

    public boolean hasActiveSession(UUID player) {
        return sessions.containsKey(player);
    }

    public boolean isPrivateChat(AsyncChatEvent event) {
        return privateChat.contains(event)
            ||hasActiveSession(event.getPlayer().getUniqueId());
    }

    private boolean available() {
        return enabled&&script!=null&&CitizensQuestNpcSupport.available();
    }

    void chat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        BotSession session=sessions.get(id);
        if(session==null) return;

        privateChat.add(event);
        event.setCancelled(true);
        event.viewers().clear();

        String text=PlainTextComponentSerializer.plainText()
            .serialize(event.message());

        api.tasks().nextTick(()->{
            if(sessions.get(id)!=session) return;

            Player player=Bukkit.getPlayer(id);
            if(player==null) return;

            player.sendMessage(
                Component.text("You → STEMBot: "+text,NamedTextColor.GRAY)
            );

            session.input(text);

            if(session.closed())
                sessions.remove(id,session);
        });
    }

    private void load() {
        enabled=getConfigSection().getBoolean("enabled",true);

        File file=new File(api.getDataFolder(),"stembot.yml");
        if(!file.exists())
            STEMCraft.getPlugin().saveResource("stembot.yml",false);

        ConfigFile config=api.config().load(file);

        try {
            assert config != null;
            script=BotScript.read(config);

            if(enabled&&CitizensQuestNpcSupport.available()) {
                skins.load(
                    api,
                    STEMCraft.getPlugin(),
                    script,
                    skin->sessions.values().forEach(session->{
                        try {
                            if(session.actor().valid())
                                session.actor().skin(skin);
                        } catch(RuntimeException ignored) {
                            // Session may disappear while asynchronous skin conversion finishes.
                        }
                    })
                );
            }
        } catch(RuntimeException error) {
            enabled=false;
            script=null;

            STEMCraft.getPlugin().getLogger().log(
                java.util.logging.Level.SEVERE,
                "Invalid stembot.yml; STEMBot is disabled",
                error
            );
        }
    }

    private void queueFirstTime(Player player,int ticks) {
        if(script==null) return;

        String action=script.firstTimeAction(player.getWorld().getName());
        if(action==null||hasSeenWorld(player,player.getWorld().getName()))
            return;

        pendingFirstTime.put(player.getUniqueId(),ticks);
    }

    private void tick() {
        if(!available()) return;

        for(var entry:new ArrayList<>(pendingFirstTime.entrySet())) {
            Player player=Bukkit.getPlayer(entry.getKey());

            if(player==null) {
                pendingFirstTime.remove(entry.getKey());
                continue;
            }

            if(verifying(player))
                continue;

            String world=player.getWorld().getName();
            String action=script.firstTimeAction(world);

            if(action==null||hasSeenWorld(player,world)) {
                pendingFirstTime.remove(entry.getKey());
                continue;
            }

            int remaining=entry.getValue()-5;
            if(remaining>0) {
                pendingFirstTime.put(entry.getKey(),remaining);
                continue;
            }

            pendingFirstTime.remove(entry.getKey());

            if(!sessions.containsKey(player.getUniqueId()))
                start(player,action,true);
        }

        for(var entry:new ArrayList<>(sessions.entrySet())) {
            Player player=Bukkit.getPlayer(entry.getKey());

            if(player==null) {
                close(entry.getKey());
                continue;
            }

            try {
                entry.getValue().tick(player.getLocation());

                if(entry.getValue().closed())
                    sessions.remove(entry.getKey(),entry.getValue());
            } catch(RuntimeException error) {
                close(entry.getKey());

                STEMCraft.getPlugin().getLogger().log(
                    java.util.logging.Level.WARNING,
                    "STEMBot session stopped",
                    error
                );
            }
        }
    }

    private boolean verifying(Player player) {
        var first=STEMCraft.getPlugin().firstJoin();
        return first!=null&&first.hasActiveSession(player.getUniqueId());
    }

    private void start(Player player,String action,boolean firstTime) {
        if(!available()||!player.isOnline()) return;

        UUID id=player.getUniqueId();
        World world=player.getWorld();
        Location playerLocation=player.getLocation();

        Location actorSpawn=findSpawn(playerLocation,script.spawnDistance());
        if(actorSpawn==null) {
            player.sendMessage(
                Component.text(
                    "Give me a little clear ground nearby, then summon STEMBot again.",
                    NamedTextColor.YELLOW
                )
            );
            return;
        }

        actorSpawn.setDirection(
            playerLocation.toVector().subtract(actorSpawn.toVector())
        );

        try {
            BotActor actor=new CitizensBotActor(
                STEMCraft.getPlugin(),
                id,
                script.name(),
                actorSpawn,
                skins.current()
            );

            BotSession.Output output=new BotSession.Output() {
                @Override
                public void say(String text) {
                    sayLine(player,text);
                }

                @Override
                public int talk(String text) {
                    return talkLine(player,actor,text);
                }
            };

            BotSession session=new BotSession(
                script,
                actor,
                world.getName(),
                action,
                output
            );

            sessions.put(id,session);

            if(firstTime)
                markSeenWorld(player,world.getName());

        } catch(RuntimeException failure) {
            player.sendMessage(
                Component.text(
                    "STEMBot could not join you just now.",
                    NamedTextColor.YELLOW
                )
            );

            STEMCraft.getPlugin().getLogger().log(
                java.util.logging.Level.WARNING,
                "Could not summon STEMBot",
                failure
            );
        }
    }

    private @javax.annotation.Nullable Location findSpawn(
        Location player,
        double distance
    ) {
        World world=player.getWorld();
        if(world==null) return null;

        var direction=player.getDirection().setY(0);
        if(direction.lengthSquared()<0.001)
            direction.setZ(1);
        direction.normalize();

        // Prefer in front; then try slightly shorter distances and vertical offsets.
        for(double scale:new double[]{distance,Math.max(1.2,distance-0.8),1.2}) {
            Location front=player.clone().add(direction.clone().multiply(scale));

            for(int dy:new int[]{0,-1,1,-2,2}) {
                Location candidate=front.clone().add(0,dy,0);
                if(safe(candidate))
                    return candidate;
            }
        }

        return null;
    }

    static boolean safe(Location at) {
        World world=at.getWorld();
        if(world==null) return false;

        int x=at.getBlockX();
        int y=at.getBlockY();
        int z=at.getBlockZ();

        if(y<=world.getMinHeight()
            ||y>=world.getMaxHeight()-1
            ||!world.isChunkLoaded(x>>4,z>>4)
            ||!world.getWorldBorder().isInside(at))
            return false;

        Material floor=world.getBlockAt(x,y-1,z).getType();

        return floor.isSolid()
            &&floor.isOccluding()
            &&floor!=Material.MAGMA_BLOCK
            &&world.getBlockAt(x,y,z).getType().isAir()
            &&world.getBlockAt(x,y+1,z).getType().isAir();
    }

    private void sayLine(Player player,String raw) {
        if(!player.isOnline()||script==null) return;

        String message=script.prefix()
                +render(player,raw);

        player.sendMessage(
                TextUtil.colourise(
                        api.messages().tokens().apply(message)
                )
        );
    }

    /**
     * Displays the line, schedules randomized robot beeps and returns the exact
     * resulting duration. BotSession blocks the script for this duration.
     */
    private int talkLine(Player player,BotActor actor,String raw) {
        sayLine(player,raw);

        if(script==null||!script.speech().enabled())
            return 1;

        String text=render(player,raw);
        int characters=(int)text.codePoints()
            .filter(cp->!Character.isWhitespace(cp))
            .count();

        int beepCount=(characters+script.speech().charactersPerBeep()-1)
            /script.speech().charactersPerBeep();

        beepCount= Math.clamp(beepCount,
                script.speech().minBeeps(), script.speech().maxBeeps());

        int delay=0;

        for(int i=0;i<beepCount;i++) {
            int beepDelay=delay;

            Bukkit.getScheduler().runTaskLater(
                STEMCraft.getPlugin(),
                ()->playSpeechBeep(player,actor),
                beepDelay
            );

            if(i<beepCount-1) {
                delay+=ThreadLocalRandom.current().nextInt(
                    script.speech().beepIntervalMin(),
                    script.speech().beepIntervalMax()+1
                );
            }
        }

        return Math.max(1,delay+2);
    }

    private void playSpeechBeep(Player player,BotActor actor) {
        if(!player.isOnline()||script==null) return;

        float min=script.speech().pitchMin();
        float max=script.speech().pitchMax();
        float pitch=min+ThreadLocalRandom.current().nextFloat()*(max-min);

        Location source=player.getLocation();

        try {
            if(actor.valid())
                source=actor.location();
        } catch(RuntimeException ignored) {
            // A queued beep may outlive a closed session.
        }

        player.playSound(
            source,
            script.speech().sound(),
            script.speech().volume(),
            pitch
        );
    }

    private String render(Player player,String raw) {
        return raw.replace("{player}",player.getName());
    }

    private boolean hasSeenWorld(Player player,String world) {
        String raw=player.getPersistentDataContainer().get(
            firstWorldsKey,
            PersistentDataType.STRING
        );

        if(raw==null||raw.isBlank()) return false;

        return Arrays.stream(raw.split("\\n"))
            .anyMatch(value->value.equalsIgnoreCase(world));
    }

    private void markSeenWorld(Player player,String world) {
        String raw=player.getPersistentDataContainer().get(
            firstWorldsKey,
            PersistentDataType.STRING
        );

        LinkedHashSet<String> worlds=new LinkedHashSet<>();

        if(raw!=null&&!raw.isBlank())
            worlds.addAll(Arrays.asList(raw.split("\\n")));

        worlds.add(world.toLowerCase(Locale.ROOT));

        player.getPersistentDataContainer().set(
            firstWorldsKey,
            PersistentDataType.STRING,
            String.join("\n",worlds)
        );
    }

    private void close(UUID id) {
        pendingFirstTime.remove(id);

        BotSession session=sessions.remove(id);
        if(session!=null)
            session.close();
    }

    private void stop() {
        epoch++;
        pendingFirstTime.clear();
        skins.close();

        for(UUID id:List.copyOf(sessions.keySet()))
            close(id);
    }

    @Override
    public void onReload() {
        stop();
        super.onReload();
        load();
    }

    @Override
    public void onDisable() {
        stop();
        api.tasks().cancel(TASK);
    }
}
