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
    private final Map<UUID,PendingSummon> pendingSummons=new ConcurrentHashMap<>();
    private final Map<BotActor,Departure> departures=new HashMap<>();
    private record Departure(UUID owner,int ticks) {}
    private record PendingSummon(String action,int ticks) {}
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
            .aliases("help")
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

                summon(player,action);
            })
            .register(STEMCraft.getPlugin());

        api.events().register(
            PlayerJoinEvent.class,
            event->queueFirstTime(event.getPlayer(),40),
            EventPriority.MONITOR,
            true
        );

        api.events().register(PlayerChangedWorldEvent.class,event->{
            close(event.getPlayer().getUniqueId(),false);
            queueFirstTime(event.getPlayer(),20);
        });

        api.events().register(PlayerQuitEvent.class,event->{
            pendingFirstTime.remove(event.getPlayer().getUniqueId());
            close(event.getPlayer().getUniqueId(),false);
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
        return sessions.containsKey(player)||pendingSummons.containsKey(player);
    }

    public boolean isPrivateChat(AsyncChatEvent event) {
        BotSession session=sessions.get(event.getPlayer().getUniqueId());
        return privateChat.contains(event)
            ||(session!=null&&session.chatEngaged())
            ||pendingSummons.containsKey(event.getPlayer().getUniqueId());
    }

    private boolean available() {
        return enabled&&script!=null&&CitizensQuestNpcSupport.available();
    }

    void chat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        BotSession session=sessions.get(id);
        if(session==null&&!pendingSummons.containsKey(id)) return;
        if(session!=null&&!session.chatEngaged()) return;

        privateChat.add(event);
        event.setCancelled(true);
        event.viewers().clear();
        String text=PlainTextComponentSerializer.plainText()
            .serialize(event.message());

        // A dismissal also cancels a re-summon while the old actor is departing.
        if(session==null) {
            PendingSummon pending=pendingSummons.get(id);
            if(pending!=null&&BotSession.isDismissal(text))
                api.tasks().nextTick(()->pendingSummons.remove(id,pending));
            return;
        }

        api.tasks().nextTick(()->{
            if(sessions.get(id)!=session) return;

            Player player=Bukkit.getPlayer(id);
            if(player==null) return;

            player.sendMessage(
                replyLine(player,text)
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

        tickDepartures();
        for(var entry:new ArrayList<>(pendingSummons.entrySet())) {
            int remaining=entry.getValue().ticks()-5;
            if(remaining>0) {
                pendingSummons.put(entry.getKey(),new PendingSummon(entry.getValue().action(),remaining));
                continue;
            }
            Player player=Bukkit.getPlayer(entry.getKey());
            try {
                if(player!=null&&!verifying(player)) start(player,entry.getValue().action(),false);
            } finally {
                pendingSummons.remove(entry.getKey());
            }
        }

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

            if(!sessions.containsKey(player.getUniqueId())&&!pendingSummons.containsKey(player.getUniqueId()))
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

    void summon(Player player,String action) {
        UUID id=player.getUniqueId();
        close(id,false);
        int delay=departures.values().stream().filter(departure->departure.owner().equals(id))
            .mapToInt(Departure::ticks).max().orElse(0);
        if(delay>0)
            pendingSummons.put(id,new PendingSummon(action,delay));
        else
            start(player,action,false);
    }

    void depart(UUID owner,BotActor actor,int delay) {
        departures.put(actor,new Departure(owner,delay));
    }

    void tickDepartures() {
        for(var entry:new ArrayList<>(departures.entrySet())) {
            int remaining=entry.getValue().ticks()-5;
            if(remaining>0) {
                departures.put(entry.getKey(),new Departure(entry.getValue().owner(),remaining));
                continue;
            }
            departures.remove(entry.getKey());
            BotActor actor=entry.getKey();
            try {
                if(actor.valid()) actor.puff();
            } finally {
                actor.close();
            }
        }
    }

    private void start(Player player,String action,boolean firstTime) {
        if(!available()||!player.isOnline()) return;

        UUID id=player.getUniqueId();
        World world=player.getWorld();
        Location playerLocation=player.getLocation();

        Location actorSpawn=findSpawn(playerLocation,script.spawnDistance(),script.spawnSearchRadius());
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
                public void depart(BotActor departing) {
                    StemBotFeature.this.depart(id,departing,script.departureDelayTicks());
                }

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
            actor.puff();

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

    static @javax.annotation.Nullable Location findSpawn(
        Location player,
        double distance,
        double searchRadius
    ) {
        World world=player.getWorld();
        if(world==null) return null;

        var direction=player.getDirection().setY(0);
        if(direction.lengthSquared()<0.001)
            direction.setZ(1);
        direction.normalize();

        // Prefer nearby ground in front, then search rings around the player.
        List<Double> radii=new ArrayList<>(List.of(distance,Math.max(1.5,distance-1)));
        for(double radius=distance+1;radius<=searchRadius;radius++) radii.add(radius);
        radii.add(searchRadius);
        for(double radius:radii) {
            for(int degrees:new int[]{0,30,-30,60,-60,90,-90,120,-120,150,-150,180}) {
                Location around=player.clone().add(direction.clone().rotateAroundY(Math.toRadians(degrees)).multiply(radius));
                for(int dy:new int[]{0,-1,1,-2,2,-3,3}) {
                    Location candidate=around.clone();
                    candidate.setX(candidate.getBlockX()+0.5);
                    candidate.setY(player.getBlockY()+dy);
                    candidate.setZ(candidate.getBlockZ()+0.5);
                    if(candidate.distanceSquared(player)<1.5*1.5) continue;
                    if(safe(candidate)) return candidate;
                }
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

    private Component replyLine(Player player,String text) {
        String format=script==null?"&7You → STEMBot: {message}":script.replyFormat();
        format=format.replace("{player}",player.getName())
            .replace("{bot}",script==null?"STEMBot":script.name());
        if(script!=null) format=api.messages().tokens().apply(format);
        return formatReply(format,text);
    }

    static Component formatReply(String format,String text) {
        // Parse only the configured format; player replies remain literal text.
        return TextUtil.colourise(format).replaceText(builder->builder
            .matchLiteral("{message}").replacement(Component.text(text)));
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
        BotSession speaking=sessions.get(player.getUniqueId());
        long revision=speaking==null?-1:speaking.speechRevision();

        for(int i=0;i<beepCount;i++) {
            int beepDelay=delay;

            Bukkit.getScheduler().runTaskLater(
                STEMCraft.getPlugin(),
                ()->{
                    BotSession active=sessions.get(player.getUniqueId());
                    if(active!=null&&active==speaking&&active.speechRevision()==revision
                        &&active.chatEngaged()&&active.actor()==actor)
                        playSpeechBeep(player,actor);
                },
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
        close(id,true);
    }

    private void close(UUID id,boolean farewell) {
        pendingFirstTime.remove(id);
        pendingSummons.remove(id);

        BotSession session=sessions.remove(id);
        if(session!=null)
            session.close(farewell);
    }

    private void stop() {
        epoch++;
        pendingFirstTime.clear();
        pendingSummons.clear();
        skins.close();

        for(UUID id:List.copyOf(sessions.keySet()))
            close(id,false);
        for(BotActor actor:List.copyOf(departures.keySet())) actor.close();
        departures.clear();
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
