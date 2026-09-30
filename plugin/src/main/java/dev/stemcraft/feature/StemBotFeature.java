package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.util.TextUtil;

import dev.stemcraft.feature.stembot.*;
import dev.stemcraft.api.service.stembot.StemBotService;
import dev.stemcraft.api.service.stembot.StemBotSession;
import dev.stemcraft.api.service.stembot.StemBotTrigger;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Private scripted STEMBot sessions. */
public final class StemBotFeature extends BaseFeature implements StemBotService {
    private static final String TASK="feature:stembot";

    private final Map<UUID,ControlledGuide> controls=new ConcurrentHashMap<>();
    private final Map<UUID,BotSession> sessions=new ConcurrentHashMap<>();
    private final Set<AsyncChatEvent> privateChat=
        Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Map<UUID,Integer> pendingArrivals=new HashMap<>();
    private final Map<UUID,PendingSummon> pendingSummons=new ConcurrentHashMap<>();
    private final Map<String,Integer> triggerRegistrations=new HashMap<>();
    private final Map<UUID,LinkedHashSet<String>> pendingBotRequests=new HashMap<>();
    private final Map<UUID,String> activeBotRequests=new HashMap<>();
    private final Map<BotActor,Departure> departures=new HashMap<>();
    private record Departure(UUID owner,int ticks) {}
    private record PendingSummon(String action,int ticks) {}
    private record ResolvedBotRequest(String action,String triggerId,boolean once) {}
    private final BotSkinCache skins=new BotSkinCache();

    private BotScript script;
    private dev.stemcraft.api.command.Command botCommand;
    private boolean enabled;
    private long epoch;
    private NamespacedKey firstWorldsKey;
    private NamespacedKey seenTriggersKey;
    private NamespacedKey pendingTriggersKey;
    private NamespacedKey lastCombatKey;

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
        seenTriggersKey=new NamespacedKey(STEMCraft.getPlugin(),"stembot-seen-triggers");
        pendingTriggersKey=new NamespacedKey(STEMCraft.getPlugin(),"stembot-pending-triggers");
        lastCombatKey=new NamespacedKey(STEMCraft.getPlugin(),"stembot-last-combat");
        load();

        var command=api.commands().create("stembot")
            .aliases("help")
            .description("Talk privately to your STEMBot guide.")
            .usage("/stembot [close|help-topic]")
            .tabCompletion("close");
        if(script!=null) script.helpTopics().keySet().stream().sorted().forEach(command::tabCompletion);
        command.executor((unused,registeredCommand,ctx)->{
                ctx.checkNotConsole();
                Player player=ctx.asPlayer();

                String argument=ctx.getArg(0,"").trim();
                if(argument.equalsIgnoreCase("close")) {
                    close(player.getUniqueId());
                    return;
                }

                if(!argument.isBlank()) {
                    if(!available()) {
                        ctx.returnError("STEMBot is resting right now.");
                        return;
                    }
                    String action=script.helpTopicAction(argument);
                    if(action==null) {
                        List<String> topics=script.helpTopics().keySet().stream().sorted().toList();
                        ctx.returnInfo(topics.isEmpty()?"I do not have any help topics right now."
                            :"I can help with: "+String.join(", ",topics)+".");
                        return;
                    }
                    if(!queueHelpAction(player,action)) ctx.returnError("I could not queue that help topic right now.");
                    else ctx.returnInfo("I will help with "+argument+" when I am free and you are out of combat.");
                    return;
                }

                if(controls.containsKey(player.getUniqueId())) {
                    ctx.returnError("STEMBot is busy guiding you. Finish the current conversation first.");
                    return;
                }

                if(!available()) {
                    ctx.returnError("STEMBot is resting right now.");
                    return;
                }

                String action=script.commandAction(player.getWorld().getName());
                if(action==null) {
                    ctx.returnInfo("STEMBot is not available right now.");
                    return;
                }

                summon(player,action);
            });
        botCommand=command.register(STEMCraft.getPlugin());
        updateCommandTabCompletions();

        // Bukkit already owns /help, so the alias may be rejected by the command map.
        // Forward it here to keep /help and /help <topic> mapped to STEMBot.
        api.events().register(PlayerCommandPreprocessEvent.class,event->{
            String message=event.getMessage().trim();
            if(!message.equalsIgnoreCase("/help")
                &&!message.regionMatches(true,0,"/help ",0,6)) return;
            event.setCancelled(true);
            String arguments=message.length()<=5?"":message.substring(5).trim();
            String forwarded=arguments.isBlank()?"stembot":"stembot "+arguments;
            Player player=event.getPlayer();
            api.tasks().nextTick(()->{
                if(player.isOnline()) Bukkit.dispatchCommand(player,forwarded);
            });
        },EventPriority.HIGHEST,true);

        api.events().register(
            PlayerJoinEvent.class,
            event->{
                restorePendingBotRequests(event.getPlayer());
                queueArrival(event.getPlayer(),40);
            },
            EventPriority.MONITOR,
            true
        );

        api.events().register(PlayerChangedWorldEvent.class,event->{
            close(event.getPlayer().getUniqueId(),false);
            queueArrival(event.getPlayer(),20);
        });

        api.events().register(PlayerQuitEvent.class,event->{
            pendingArrivals.remove(event.getPlayer().getUniqueId());
            pendingBotRequests.remove(event.getPlayer().getUniqueId());
            close(event.getPlayer().getUniqueId(),false);
        });

        api.events().register(EntityDamageByEntityEvent.class,this::onCombatDamage,EventPriority.MONITOR,true);

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
        ControlledGuide guide=controls.get(event.getPlayer().getUniqueId());
        return privateChat.contains(event)
            ||(session!=null&&(session.controlled()
                ? guide!=null&&guide.listening()
                : session.chatEngaged()))
            ||pendingSummons.containsKey(event.getPlayer().getUniqueId());
    }

    @Override
    public Optional<StemBotSession> open(Player player) {
        UUID id=player.getUniqueId();
        if(!available()||controls.containsKey(id)) return Optional.empty();
        close(id,false);
        start(player,null,false);
        ControlledGuide guide=new ControlledGuide(player,sessions.get(id));
        controls.put(id,guide);
        return Optional.of(guide);
    }

    private final class ControlledGuide implements StemBotSession {
        private final Player player;
        private final BotSession session;
        private volatile java.util.function.Consumer<String> replies;
        private volatile Object replyRevision=new Object();
        private ControlledGuide(Player player,BotSession session) {
            this.player=player;
            this.session=session;
        }
        private boolean owns() { return controls.get(player.getUniqueId())==this; }
        public boolean active() {
            return owns()&&session!=null&&sessions.get(player.getUniqueId())==session&&session.controlled();
        }
        public boolean speak(String message) {
            if(!active()) return false;
            talkLine(player,session.actor(),message);
            return true;
        }
        public Optional<Location> location() {
            return active()?Optional.of(session.actor().location().clone()):Optional.empty();
        }
        public boolean teleport(Location destination) {
            return active()&&session.teleport(destination);
        }
        public void follow(boolean enabled) { if(active()) session.follow(enabled); }
        public boolean startAction(String action) {
            if(!owns()||!available()||!hasAction(action)) return false;
            if(!runAction(player,action,false)) return false;
            controls.remove(player.getUniqueId(),this);
            return true;
        }
        public boolean move(Location destination,boolean waitForPlayer) {
            return active()&&session.move(destination,waitForPlayer);
        }
        public void listen(java.util.function.Consumer<String> listener) {
            if(active()) {
                replyRevision=new Object();
                replies=Objects.requireNonNull(listener);
            }
        }
        public void stopListening() { replyRevision=new Object(); replies=null; }
        public boolean listening() { return active()&&replies!=null; }
        public void close() {
            if(!controls.remove(player.getUniqueId(),this)) return;
            if(session!=null&&sessions.get(player.getUniqueId())==session)
                StemBotFeature.this.close(player.getUniqueId(),false);
        }
    }

    @Override
    public boolean hasAction(String action) {
        return script!=null&&action!=null&&script.actions().containsKey(action);
    }

    @Override
    public StemBotTrigger registerTrigger(String id) {
        String normalized=normalizeTriggerId(id);
        if(!normalized.matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Invalid STEMBot trigger id: "+id);
        triggerRegistrations.merge(normalized,1,Integer::sum);
        return new RegisteredTrigger(normalized);
    }

    private final class RegisteredTrigger implements StemBotTrigger {
        private final String id;
        private boolean closed;

        private RegisteredTrigger(String id) {
            this.id=id;
        }

        @Override
        public boolean fire(Player player) {
            return !closed&&requestTrigger(player,id);
        }

        @Override
        public void close() {
            if(closed) return;
            closed=true;
            triggerRegistrations.computeIfPresent(id,(ignored,count)->count<=1?null:count-1);
        }
    }

    private boolean requestTrigger(Player player,String triggerId) {
        if(!available()||!player.isOnline()||!triggerRegistrations.containsKey(triggerId)) return false;
        UUID playerId=player.getUniqueId();
        String requestKey="trigger:"+triggerId;
        if(isBotRequestActiveOrPending(playerId,requestKey)) return true;
        if(script.triggerAction(triggerId,hasSeenTrigger(player,triggerId))==null) return false;
        enqueueBotRequest(player,requestKey);
        return true;
    }

    private boolean queueHelpAction(Player player,String action) {
        if(!available()||!player.isOnline()) return false;
        UUID playerId=player.getUniqueId();
        String requestKey="help:"+action;
        if(isBotRequestActiveOrPending(playerId,requestKey)) return true;
        enqueueBotRequest(player,requestKey);
        return true;
    }

    private boolean isBotRequestActiveOrPending(UUID playerId,String requestKey) {
        if(requestKey.equals(activeBotRequests.get(playerId))) return true;
        return pendingBotRequests.getOrDefault(playerId,new LinkedHashSet<>()).contains(requestKey);
    }

    private void enqueueBotRequest(Player player,String requestKey) {
        UUID playerId=player.getUniqueId();
        pendingBotRequests.computeIfAbsent(playerId,ignored->new LinkedHashSet<>()).add(requestKey);
        LinkedHashSet<String> stored=readPersistentValues(player,pendingTriggersKey);
        stored.add(requestKey);
        writePersistentValues(player,pendingTriggersKey,stored);
    }

    private void restorePendingBotRequests(Player player) {
        LinkedHashSet<String> stored=readPersistentValues(player,pendingTriggersKey);
        if(!stored.isEmpty()) pendingBotRequests.put(player.getUniqueId(),stored);
    }

    @Override
    public boolean startAction(Player player,String action) {
        return !controls.containsKey(player.getUniqueId())&&available()&&hasAction(action)
            &&runAction(player,action,false);
    }

    private void startArrivalAction(Player player) {
        String world=player.getWorld().getName();
        String action=script.arrivalAction(world,hasSeenWorld(player,world));
        if(action==null||controls.containsKey(player.getUniqueId())||!available()) return;
        boolean firstTime=!hasSeenWorld(player,world)&&script.firstTimeAction(world)!=null;
        if(firstTime) runAction(player,action,true);
        else {
            BotScript.SpawnPoint spawn=script.alwaysActionSpawn(world);
            start(player,action,false,spawn==null?null:spawn.location(player.getWorld()));
        }
    }

    private boolean runAction(Player player,String action,boolean firstTime) {
        if(!player.isOnline()) return false;
        UUID id=player.getUniqueId();
        BotSession session=sessions.get(id);
        if(session!=null&&session.controlled()) {
            session.releaseControl(action);
            if(firstTime) markSeenWorld(player,player.getWorld().getName());
        } else {
            // Preserve a caller's reservation if spawning fails.
            BotSession previous=sessions.remove(id);
            if(previous!=null) previous.close(false);
            start(player,action,firstTime);
            if(!sessions.containsKey(id)) return false;
        }
        pendingArrivals.remove(id);
        pendingSummons.remove(id);
        return true;
    }

    @Override
    public boolean available() {
        return enabled&&script!=null&&dev.stemcraft.integration.CitizensAccess.available();
    }

    void chat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        BotSession session=sessions.get(id);
        if(session==null&&!pendingSummons.containsKey(id)) return;
        if(session!=null&&session.controlled()) {
            ControlledGuide guide=controls.get(id);
            if(guide==null||!guide.listening()) return;
            Object revision=guide.replyRevision;
            var receiver=guide.replies;
            privateChat.add(event);
            event.setCancelled(true);
            event.viewers().clear();
            String text=PlainTextComponentSerializer.plainText().serialize(event.message());
            api.tasks().nextTick(()->{
                if(!guide.listening()||guide.replies!=receiver||guide.replyRevision!=revision) return;
                guide.player.sendMessage(replyLine(guide.player,text));
                receiver.accept(text);
            });
            return;
        }
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
            if(BotScript.migrateWorldRoutes(config)) config.save();

            if(enabled&&dev.stemcraft.integration.CitizensAccess.available()) {
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
        updateCommandTabCompletions();
    }

    private void updateCommandTabCompletions() {
        if(botCommand==null) return;
        botCommand.clearTabCompletions();
        botCommand.addTabCompletion("close");
        if(script!=null) script.helpTopics().keySet().stream().sorted()
            .forEach(topic->botCommand.addTabCompletion(topic));
    }

    private void queueArrival(Player player,int ticks) {
        if(script==null||controls.containsKey(player.getUniqueId())) return;

        String action=script.arrivalAction(player.getWorld().getName(),hasSeenWorld(player,player.getWorld().getName()));
        if(action==null)
            return;

        pendingArrivals.put(player.getUniqueId(),ticks);
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
                if(player!=null&&!controls.containsKey(player.getUniqueId())) start(player,entry.getValue().action(),false);
            } finally {
                pendingSummons.remove(entry.getKey());
            }
        }

        for(var entry:new ArrayList<>(pendingArrivals.entrySet())) {
            Player player=Bukkit.getPlayer(entry.getKey());

            if(player==null) {
                pendingArrivals.remove(entry.getKey());
                continue;
            }

            if(controls.containsKey(player.getUniqueId()))
                continue;

            if(shouldDeferForCombat(player))
                continue;

            String world=player.getWorld().getName();
            String action=script.arrivalAction(world,hasSeenWorld(player,world));

            if(action==null) {
                pendingArrivals.remove(entry.getKey());
                continue;
            }

            int remaining=entry.getValue()-5;
            if(remaining>0) {
                pendingArrivals.put(entry.getKey(),remaining);
                continue;
            }

            pendingArrivals.remove(entry.getKey());

            if(!sessions.containsKey(player.getUniqueId())&&!pendingSummons.containsKey(player.getUniqueId()))
                startArrivalAction(player);
        }

        for(var entry:new ArrayList<>(sessions.entrySet())) {
            Player player=Bukkit.getPlayer(entry.getKey());

            if(player==null) {
                close(entry.getKey());
                continue;
            }

            try {
                entry.getValue().tick(player.getLocation());

                if(entry.getValue().closed()) {
                    sessions.remove(entry.getKey(),entry.getValue());
                    activeBotRequests.remove(entry.getKey());
                }
            } catch(RuntimeException error) {
                close(entry.getKey());

                STEMCraft.getPlugin().getLogger().log(
                    java.util.logging.Level.WARNING,
                    "STEMBot session stopped",
                    error
                );
            }
        }

        processPendingBotRequests();
    }

    private void processPendingBotRequests() {
        for(var entry:new ArrayList<>(pendingBotRequests.entrySet())) {
            UUID playerId=entry.getKey();
            Player player=Bukkit.getPlayer(playerId);
            if(player==null) {
                pendingBotRequests.remove(playerId);
                continue;
            }
            if(controls.containsKey(playerId)||sessions.containsKey(playerId)
                ||pendingSummons.containsKey(playerId)||pendingArrivals.containsKey(playerId)
                ||shouldDeferForCombat(player)
                ||departures.values().stream().anyMatch(value->value.owner().equals(playerId)))
                continue;

            LinkedHashSet<String> requests=pendingBotRequests.get(playerId);
            if(requests==null) continue;
            for(String requestKey:List.copyOf(requests)) {
                ResolvedBotRequest resolved=resolveBotRequest(player,requestKey);
                if(resolved==null) {
                    removeBotRequest(player,requestKey);
                    continue;
                }

                boolean started=runAction(player,resolved.action(),false);
                if(started) {
                    activeBotRequests.put(playerId,requestKey);
                    if(resolved.once()) markSeenTrigger(player,resolved.triggerId());
                }
                removeBotRequest(player,requestKey);
                break;
            }
        }
    }

    private ResolvedBotRequest resolveBotRequest(Player player,String requestKey) {
        if(requestKey.startsWith("trigger:")) {
            String id=requestKey.substring("trigger:".length());
            if(!triggerRegistrations.containsKey(id)) return null;
            BotScript.TriggerAction route=script.triggerAction(id,hasSeenTrigger(player,id));
            return route==null?null:new ResolvedBotRequest(route.action(),id,route.once());
        }
        if(requestKey.startsWith("help:")) {
            String action=requestKey.substring("help:".length());
            if(!script.helpTopics().containsValue(action)) return null;
            return new ResolvedBotRequest(action,null,false);
        }
        return null;
    }

    private void removeBotRequest(Player player,String requestKey) {
        UUID playerId=player.getUniqueId();
        LinkedHashSet<String> requests=pendingBotRequests.get(playerId);
        if(requests!=null) {
            requests.remove(requestKey);
            if(requests.isEmpty()) pendingBotRequests.remove(playerId);
        }
        LinkedHashSet<String> stored=readPersistentValues(player,pendingTriggersKey);
        if(stored.remove(requestKey)) writePersistentValues(player,pendingTriggersKey,stored);
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

    void start(Player player,String action,boolean firstTime) {
        start(player,action,firstTime,null);
    }

    void start(Player player,String action,boolean firstTime,Location fixedSpawn) {
        if(!available()||!player.isOnline()) return;

        UUID id=player.getUniqueId();
        World world=player.getWorld();
        Location playerLocation=player.getLocation();

        Location actorSpawn=fixedSpawn==null
            ?findSpawn(playerLocation,script.spawnDistance(),script.spawnSearchRadius())
            :(safe(fixedSpawn)?fixedSpawn.clone():null);
        if(actorSpawn==null) {
            player.sendMessage(
                Component.text(
                    fixedSpawn==null?"Give me a little clear ground nearby, then summon STEMBot again."
                        :"STEMBot’s usual spot is not available right now.",
                    NamedTextColor.YELLOW
                )
            );
            return;
        }

        if(fixedSpawn==null) actorSpawn.setDirection(
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
                @Override public void navigationDiagnostic(String message) {
                    STEMCraft.getPlugin().getLogger().info("[STEMBot navigation] " + message);
                }
                @Override public Runnable await(String key, java.util.function.Consumer<Boolean> completion) {
                    var request = new dev.stemcraft.api.event.guide.GuideActionRequestEvent(player, key, completion);
                    try {
                        Bukkit.getPluginManager().callEvent(request);
                        if (!request.claimed()) completion.accept(false);
                    } catch (RuntimeException failure) {
                        request.cancel();
                        completion.accept(false);
                        STEMCraft.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                            "STEMBot guide callback failed: " + key, failure);
                    }
                    return request::cancel;
                }

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

    private void onCombatDamage(EntityDamageByEntityEvent event) {
        if(event.isCancelled()||event.getFinalDamage()<=0) return;
        long now=System.currentTimeMillis();
        if(event.getEntity() instanceof Player victim) markCombat(victim,now);
        Player attacker=resolvePlayerDamager(event.getDamager());
        if(attacker!=null) markCombat(attacker,now);
    }

    private Player resolvePlayerDamager(org.bukkit.entity.Entity damager) {
        if(damager instanceof Player player) return player;
        if(damager instanceof Projectile projectile&&projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private void markCombat(Player player,long timestamp) {
        player.getPersistentDataContainer().set(lastCombatKey,PersistentDataType.LONG,timestamp);
    }

    private boolean shouldDeferForCombat(Player player) {
        if(script==null||!script.deferDuringCombat()||script.combatCooldownSeconds()<=0) return false;
        Long lastCombat=player.getPersistentDataContainer().get(lastCombatKey,PersistentDataType.LONG);
        return lastCombat!=null&&System.currentTimeMillis()-lastCombat<script.combatCooldownSeconds()*1000L;
    }

    private boolean hasSeenTrigger(Player player,String triggerId) {
        return readPersistentValues(player,seenTriggersKey).contains(triggerId);
    }

    private void markSeenTrigger(Player player,String triggerId) {
        LinkedHashSet<String> seen=readPersistentValues(player,seenTriggersKey);
        if(seen.add(triggerId)) writePersistentValues(player,seenTriggersKey,seen);
    }

    private LinkedHashSet<String> readPersistentValues(Player player,NamespacedKey key) {
        String raw=player.getPersistentDataContainer().get(key,PersistentDataType.STRING);
        LinkedHashSet<String> values=new LinkedHashSet<>();
        if(raw!=null&&!raw.isBlank()) values.addAll(Arrays.asList(raw.split("\\n")));
        return values;
    }

    private void writePersistentValues(Player player,NamespacedKey key,Collection<String> values) {
        if(values.isEmpty()) {
            player.getPersistentDataContainer().remove(key);
            return;
        }
        player.getPersistentDataContainer().set(key,PersistentDataType.STRING,String.join("\n",values));
    }

    private static String normalizeTriggerId(String id) {
        return id==null?"":id.trim().toLowerCase(Locale.ROOT);
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

    // The actor is disposed below; the handle only represents ownership across ticks.
    @SuppressWarnings("resource")
    private void close(UUID id,boolean farewell) {
        controls.remove(id);
        activeBotRequests.remove(id);
        pendingArrivals.remove(id);
        pendingSummons.remove(id);

        BotSession session=sessions.remove(id);
        if(session!=null)
            session.close(farewell);
    }

    private void stop() {
        epoch++;
        controls.clear();
        pendingArrivals.clear();
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
