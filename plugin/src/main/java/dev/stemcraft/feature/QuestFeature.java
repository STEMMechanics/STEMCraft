package dev.stemcraft.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.service.playerreset.*;
import dev.stemcraft.api.service.mailbox.MailSendResult;
import dev.stemcraft.api.service.web.WebServiceRequest;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.api.util.PatternUtil;
import dev.stemcraft.feature.quest.QuestDefinition;
import dev.stemcraft.feature.quest.QuestDefinitionStore;
import dev.stemcraft.feature.quest.QuestObjective;
import dev.stemcraft.feature.quest.QuestProgress;
import dev.stemcraft.feature.quest.QuestRewardItem;
import dev.stemcraft.feature.quest.QuestNpcProfile;
import dev.stemcraft.feature.quest.QuestNpcProfileStore;
import dev.stemcraft.feature.quest.QuestNpcSpawnRules;
import dev.stemcraft.feature.quest.CitizensQuestNpcSupport;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Book-driven, player-private quest system with NPC hologram markers. */
public final class QuestFeature extends BaseFeature {
    private static final String HOLOGRAM_TYPE = "quest";
    private static final String PERMISSION_ADMIN = "stemcraft.quest.admin";
    private static final String EXAMPLE_RESOURCE = "quests/examples.yml";
    private static final String NPC_EXAMPLE_RESOURCE = "quests/npc-examples.yml";
    private static final String CAMPAIGN_RESOURCE = "quests/survival-campaign.yml";
    private static final String NPC_TASK = "quest:npc-lifecycle";
    private static final String NPC_BEHAVIOUR_TASK = "quest:npc-behaviour";
    private static final String TIMEOUT_TASK = "quest:timeouts";
    private static final String BIOME_TASK = "quest:biome-stays";
    private static final String TRACKING_TASK = "quest:tracking";
    private static final int NPC_OVERHEAD_CLEARANCE_BLOCKS = 3;
    private static final int NPC_OPEN_AREA_RADIUS = 5;
    private static final int NPC_INTERACTED_RELOCATION_RADIUS = 250;
    private static final String EDITOR_PATH = "/quests/editor/";
    private static final long EDITOR_TOKEN_LIFETIME_MS = TimeUnit.HOURS.toMillis(24);
    private static final long AUTO_TRACK_ACTIVITY_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long URGENT_TIMED_QUEST_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern EXPERIENCE_REWARD = Pattern.compile("(?i)^(?:experience|xp)\\s+add\\s+\\{player}\\s+(\\d+)\\s+points$");
    private static final Gson EDITOR_GSON = new GsonBuilder().serializeNulls().create();
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();
    private final Map<UUID, Map<String, QuestProgress>> active = new HashMap<>();
    private final Map<UUID, Set<String>> completed = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> revisions = new HashMap<>();
    private final Map<UUID, Map<String, Long>> attemptStarted = new HashMap<>();
    private final Map<UUID, Map<String, Long>> timedQuestRemaining = new HashMap<>();
    private final Map<String, QuestNpcProfile> npcProfiles = new LinkedHashMap<>();
    private final Map<String, Location> npcWanderOrigins = new HashMap<>();
    private final Map<String, Long> npcNextMovement = new HashMap<>();
    private final Map<String, UUID> npcAttentionPlayers = new HashMap<>();
    private final Map<String, Long> npcAttentionUntil = new HashMap<>();
    private final Map<String, Long> npcInteractionUntil = new HashMap<>();
    private final Map<String, Long> npcDepartureDue = new HashMap<>();
    private final Map<String, Long> npcLeavingSince = new HashMap<>();
    private final Map<UUID, String> npcMenuEngagements = new HashMap<>();
    private final Map<UUID, UUID> questMenuSessions = new HashMap<>();
    private final Map<UUID, String> trackedQuests = new HashMap<>();
    private final Map<UUID, String> autoTrackActivityQuests = new HashMap<>();
    private final Map<UUID, Long> autoTrackActivityUntil = new HashMap<>();
    private final Set<UUID> autoTrackPlayers = new HashSet<>();
    private final Set<UUID> trackingPreferencePlayers = new HashSet<>();
    private final Map<UUID, BossBar> trackingBossBars = new HashMap<>();
    private final Map<String, Long> editorTokens = new ConcurrentHashMap<>();
    private List<String> npcNames = List.of("Rokar", "Tailor", "Mira", "Bram", "Elowen", "Tobin", "Nessa", "Orin");
    private List<Pattern> trackingWorlds = List.of(PatternUtil.globToRegex("survival*"));
    private int npcLeavingRandomDelayTicks = 500;
    private int npcLeavingPlayerDistance = 150;
    private int npcLeavingTimeoutTicks = 3600;
    private File questFile;
    private File npcFile;
    private NamespacedKey questIdKey;
    private NamespacedKey questOwnerKey;
    private NamespacedKey questRevisionKey;
    private NamespacedKey questMenuSessionKey;
    private NamespacedKey npcProfileKey;

    public QuestFeature(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        api.playerStats().register(new PlayerStatDefinition("quests_completed_total", "Quests Completed",
            "Total quest turn-ins, including repeatable quests.", "stemcraft", "quest", "all"));
        api.playerStats().register(new PlayerStatDefinition("quests_completed_unique", "Unique Quests Completed",
            "Different quests completed at least once.", "stemcraft", "quest", "unique"));
        questFile = new File(STEMCraft.getPlugin().getDataFolder(), "quests/quests.yml");
        npcFile = new File(STEMCraft.getPlugin().getDataFolder(), "quests/npcs.yml");
        questIdKey = new NamespacedKey(STEMCraft.getPlugin(), "quest-id");
        questOwnerKey = new NamespacedKey(STEMCraft.getPlugin(), "quest-owner");
        questRevisionKey = new NamespacedKey(STEMCraft.getPlugin(), "quest-revision");
        questMenuSessionKey = new NamespacedKey(STEMCraft.getPlugin(), "quest-menu-session");
        npcProfileKey = new NamespacedKey(STEMCraft.getPlugin(), "quest-npc-profile");
        boolean initializeDefinitions = !questFile.exists();
        reloadSettings();
        ensureStorage();
        reloadDefinitions();
        npcProfiles.clear();
        npcProfiles.putAll(QuestNpcProfileStore.load(npcFile));
        if (initializeDefinitions) initializeBundledDefinitions();
        migrateBundledNpcProfiles();
        loadPlayerState();
        registerEvents();
        registerCommands();
        registerQuestCommand();
        api.web().registerEndpointHandler("/quests/editor", this::handleWebEditor);
        registerMarkers();
        startNpcLifecycle();
        api.tasks().repeating(TIMEOUT_TASK, 20L, 20L, this::expireTimedQuests);
        api.tasks().repeating(BIOME_TASK, 20L, 20L, this::tickBiomeObjectives);
        api.tasks().repeating(TRACKING_TASK, 20L, 10L, this::tickQuestTracking);
        api.playerResets().register(new PlayerResetHandler() {
            public @NotNull String id() { return "quests"; }
            public @NotNull Set<PlayerResetScope> scopes() { return Set.of(PlayerResetScope.PROGRESSION, PlayerResetScope.GAMEPLAY, PlayerResetScope.COMPLETE); }
            public int priority() { return 120; }
            public @NotNull PlayerResetPreview preview(@NotNull PlayerResetContext context) {
                return new PlayerResetPreview("Quest progress, completions and tracking", active.getOrDefault(context.playerUuid(), Map.of()).size() + completed.getOrDefault(context.playerUuid(), Set.of()).size());
            }
            public void reset(@NotNull PlayerResetContext context) {
                UUID uuid = context.playerUuid();
                active.remove(uuid); completed.remove(uuid); revisions.remove(uuid); attemptStarted.remove(uuid); timedQuestRemaining.remove(uuid);
                trackedQuests.remove(uuid); autoTrackPlayers.remove(uuid); trackingPreferencePlayers.remove(uuid);
                autoTrackActivityQuests.remove(uuid); autoTrackActivityUntil.remove(uuid);
                BossBar bar = trackingBossBars.remove(uuid); if (bar != null) Bukkit.getOnlinePlayers().forEach(bar::removeViewer);
                questMenuSessions.remove(uuid);
                npcMenuEngagements.remove(uuid);
            }
        });
    }

    @Override
    public void onReload() {
        super.onReload();
        reloadSettings();
        reloadDefinitions();
        npcProfiles.clear();
        npcProfiles.putAll(QuestNpcProfileStore.load(npcFile));
        registerMarkers();
    }

    private void reloadSettings() {
        List<String> configured = getConfigSection().getStringList("npc-names").stream()
            .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (!configured.isEmpty()) npcNames = configured;
        List<Pattern> configuredWorlds = getConfigSection().getStringList("tracking-worlds").stream()
            .map(String::trim).filter(value -> !value.isEmpty())
            .map(value -> PatternUtil.globToRegex(value.toLowerCase(Locale.ROOT))).toList();
        trackingWorlds = configuredWorlds.isEmpty()
            ? List.of(PatternUtil.globToRegex("survival*")) : configuredWorlds;
        npcLeavingRandomDelayTicks = Math.max(0, getConfigSection().getInt("npc-leaving.random-delay-ticks", 500));
        npcLeavingPlayerDistance = Math.max(16, getConfigSection().getInt("npc-leaving.player-distance", 150));
        npcLeavingTimeoutTicks = Math.max(20, getConfigSection().getInt("npc-leaving.timeout-ticks", 3600));
    }

    @Override
    public void onDisable() {
        api.tasks().cancel(NPC_TASK);
        api.tasks().cancel(NPC_BEHAVIOUR_TASK);
        api.tasks().cancel(TIMEOUT_TASK);
        api.tasks().cancel(BIOME_TASK);
        api.tasks().cancel(TRACKING_TASK);
        for (Map.Entry<UUID, BossBar> entry : trackingBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.hideBossBar(entry.getValue());
        }
        trackingBossBars.clear();
        timedQuestRemaining.clear();
        questMenuSessions.clear();
        for (QuestDefinition quest : quests.values()) deleteMarkers(quest);
    }

    private void startNpcLifecycle() {
        api.tasks().cancel(NPC_TASK);
        api.tasks().repeating(NPC_TASK, 100L, 200L, this::tickNpcProfiles);
        api.tasks().cancel(NPC_BEHAVIOUR_TASK);
        api.tasks().repeating(NPC_BEHAVIOUR_TASK, 120L, 20L, this::tickNativeNpcBehaviour);
    }

    private void tickNpcProfiles() {
        for (QuestNpcProfile profile : npcProfiles.values()) {
            Player trackingPlayer = trackingPlayerForProfile(profile.id());
            Entity existing = reconcileProfileEntities(profile);
            if (existing != null && existing.isValid()) {
                if (trackingPlayer == null && profile.lifetimeSeconds() > 0 && profile.spawnedAt() > 0
                    && System.currentTimeMillis() - profile.spawnedAt() >= profile.lifetimeSeconds() * 1000L) {
                    queueNpcDeparture(profile, System.currentTimeMillis());
                }
                boolean nearby = existing.getWorld().getPlayers().stream().anyMatch(player ->
                    player.getLocation().distanceSquared(existing.getLocation()) <= (double) profile.despawnRadius() * profile.despawnRadius());
                if (trackingPlayer == null && !nearby) { despawnProfileEntity(profile, existing, false); profile.spawnedEntity(null); saveNpcProfiles(); registerMarkers(); }
                continue;
            }
            profile.spawnedEntity(null);
            if (npcDiedToday(profile)) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!profileRelevant(profile.id(), player)) continue;
                long period = npcSpawnPeriod(profile, player.getWorld().getFullTime());
                Location anchor = profile.anchorPeriod() == period ? npcAnchor(profile) : null;
                String eligibilityBiome = anchor != null && anchor.getWorld().equals(player.getWorld())
                    ? anchor.getBlock().getBiome().getKey().getKey()
                    : player.getLocation().getBlock().getBiome().getKey().getKey();
                if (!QuestNpcSpawnRules.eligible(profile, player.getWorld().getName(), player.getLevel(),
                    player.getWorld().getTime(), eligibilityBiome)) continue;
                if (anchor != null) {
                    if (!anchor.getWorld().equals(player.getWorld())
                        || player.getLocation().distanceSquared(anchor) > (double) profile.despawnRadius() * profile.despawnRadius()) continue;
                } else if (!dailySpawnAllowed(player, profile)) continue;
                Location location = anchor != null ? anchor : findNpcSpawn(profile, player,
                    profile.hasAnchor() && profile.anchorInteracted() ? NPC_INTERACTED_RELOCATION_RADIUS : 0);
                if (location == null) continue;
                LivingEntity living = spawnProfileEntity(profile, location);
                if (living == null) continue;
                profile.spawnedEntity(living.getUniqueId());
                profile.spawnedAt(System.currentTimeMillis());
                if (anchor == null) profile.anchor(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), period);
                saveNpcProfiles();
                registerMarkers();
                break;
            }
        }
    }

    private @Nullable Player trackingPlayerForProfile(String profileId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String questId = trackedQuests.get(player.getUniqueId());
            QuestDefinition quest = questId == null ? null : quests.get(questId);
            QuestProgress progress = quest == null ? null : progress(player, questId);
            if (quest == null || progress == null || !hasOwnedQuestBook(player, questId)) continue;
            QuestObjective objective = currentObjective(quest, progress);
            if (objective != null && objective.type() == QuestObjective.Type.NPC
                && objective.target().equals("profile:" + profileId)) return player;
        }
        return null;
    }

    private @Nullable Entity reconcileProfileEntities(QuestNpcProfile profile) {
        List<Entity> tagged = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String id = entity.getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
                if (profile.id().equals(id) && entity.isValid()) tagged.add(entity);
            }
        }
        boolean expectsCitizens = citizensAvailable() && profile.npcType() == EntityType.PLAYER;
        if (expectsCitizens) {
            for (Entity entity : List.copyOf(tagged)) {
                if (CitizensQuestNpcSupport.isCitizensNpc(entity)) continue;
                entity.remove();
                tagged.remove(entity);
            }
        }
        Entity citizensEntity = expectsCitizens
            ? CitizensQuestNpcSupport.spawnedEntity(profile) : null;
        Entity preferred = citizensEntity != null ? citizensEntity
            : profile.spawnedEntity() == null ? null : Bukkit.getEntity(profile.spawnedEntity());
        if (preferred == null || !tagged.contains(preferred)) preferred = tagged.isEmpty() ? null : tagged.getFirst();
        for (Entity duplicate : tagged) if (!duplicate.equals(preferred)) duplicate.remove();
        if (preferred != null) {
            preferred.getPersistentDataContainer().set(npcProfileKey, PersistentDataType.STRING, profile.id());
        }
        UUID resolved = preferred == null ? null : preferred.getUniqueId();
        if (!Objects.equals(profile.spawnedEntity(), resolved)) {
            profile.spawnedEntity(resolved);
            saveNpcProfiles();
            registerMarkers();
        }
        return preferred;
    }

    private void removeProfileEntities(QuestNpcProfile profile) {
        if (citizensAvailable() && profile.citizensNpcId() != null)
            CitizensQuestNpcSupport.despawn(profile, true);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String id = entity.getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
                if (profile.id().equals(id)) entity.remove();
            }
        }
        profile.spawnedEntity(null);
        profile.spawnedAt(0);
    }

    private boolean citizensAvailable() {
        try { return CitizensQuestNpcSupport.available(); }
        catch (LinkageError ignored) { return false; }
    }

    private @Nullable LivingEntity spawnProfileEntity(QuestNpcProfile profile, Location location) {
        Entity spawned;
        if (profile.npcType() == EntityType.PLAYER && citizensAvailable()) {
            spawned = CitizensQuestNpcSupport.spawn(profile, location, npcProfileKey);
        } else {
            EntityType nativeType = profile.npcType() == EntityType.PLAYER ? EntityType.VILLAGER : profile.npcType();
            if (!nativeType.isAlive() || !nativeType.isSpawnable()) return null;
            spawned = location.getWorld().spawnEntity(location, nativeType);
        }
        if (!(spawned instanceof LivingEntity living)) {
            if (spawned != null) spawned.remove();
            return null;
        }
        living.customName(Component.text(profile.name()));
        living.setCustomNameVisible(true);
        living.setPersistent(true);
        living.setRemoveWhenFarAway(false);
        living.setInvulnerable(profile.invulnerable());
        if (!(profile.npcType() == EntityType.PLAYER && citizensAvailable())) {
            living.setAI(profile.behaviour() == QuestNpcProfile.Behaviour.WANDER);
        }
        living.getPersistentDataContainer().set(npcProfileKey, PersistentDataType.STRING, profile.id());
        npcWanderOrigins.put(profile.id(), location.clone());
        return living;
    }

    private void despawnProfileEntity(QuestNpcProfile profile, Entity entity, boolean destroy) {
        if (profile.npcType() == EntityType.PLAYER && citizensAvailable() && profile.citizensNpcId() != null)
            CitizensQuestNpcSupport.despawn(profile, destroy);
        else entity.remove();
        npcWanderOrigins.remove(profile.id());
        npcNextMovement.remove(profile.id());
        npcAttentionPlayers.remove(profile.id());
        npcAttentionUntil.remove(profile.id());
        npcInteractionUntil.remove(profile.id());
        npcDepartureDue.remove(profile.id());
        npcLeavingSince.remove(profile.id());
    }

    private boolean profileRelevant(String profileId, Player player) {
        for (QuestDefinition quest : quests.values()) {
            if (!quest.enabled()) continue;
            QuestProgress progress = progress(player, quest.id());
            if (profileId.equals(quest.startNpcProfile()) && progress == null && isAvailable(player, quest)) return true;
            if (profileId.equals(quest.endNpcProfile()) && progress != null) return true;
            QuestObjective objective = progress == null ? null : currentObjective(quest, progress);
            if (objective != null && objective.type() == QuestObjective.Type.NPC
                && objective.target().equals("profile:" + profileId)) return true;
        }
        return false;
    }

    private void tickNativeNpcBehaviour() {
        long now = System.currentTimeMillis();
        for (QuestNpcProfile profile : npcProfiles.values()) {
            Entity entity = reconcileProfileEntity(profile);
            if (entity == null || !entity.isValid()) continue;
            if (trackingPlayerForProfile(profile.id()) == null
                && availabilityEnded(profile, entity.getWorld().getTime())
                && !npcDepartureDue.containsKey(profile.id()) && !npcLeavingSince.containsKey(profile.id())) {
                long delay = isNpcEngaged(profile.id()) ? 0L
                    : ThreadLocalRandom.current().nextLong(npcLeavingRandomDelayTicks + 1L) * 50L;
                npcDepartureDue.put(profile.id(), now + delay);
            }
            Long departureDue = npcDepartureDue.get(profile.id());
            if (!npcLeavingSince.containsKey(profile.id()) && departureDue != null && departureDue <= now
                && !isNpcEngaged(profile.id())) npcLeavingSince.put(profile.id(), now);
            if (npcLeavingSince.containsKey(profile.id())) {
                tickLeavingNpc(profile, entity, now);
                continue;
            }
            Player attention = attentionPlayer(profile, entity, now);
            if (profile.npcType() == EntityType.PLAYER && citizensAvailable()) {
                CitizensQuestNpcSupport.setPaused(profile, attention != null);
                if (attention != null || profile.behaviour() == QuestNpcProfile.Behaviour.STATIONARY) continue;
                Location origin = npcWanderOrigins.computeIfAbsent(profile.id(), ignored -> entity.getLocation().clone());
                if (!origin.getWorld().equals(entity.getWorld())) {
                    origin = entity.getLocation().clone();
                    npcWanderOrigins.put(profile.id(), origin);
                }
                if (entity.getLocation().distanceSquared(origin) > (double) profile.wanderRadius() * profile.wanderRadius()) {
                    CitizensQuestNpcSupport.moveTo(profile, origin, 1D);
                    continue;
                }
                if (CitizensQuestNpcSupport.isNavigating(profile)
                    || npcNextMovement.getOrDefault(profile.id(), 0L) > now) continue;
                Location destination = findWanderDestination(origin, profile);
                if (destination != null) CitizensQuestNpcSupport.moveTo(profile, destination, 0.8D);
                npcNextMovement.put(profile.id(), now + profile.wanderDelaySeconds() * 1000L);
                continue;
            }
            if (!(entity instanceof Mob mob)) continue;
            if (profile.behaviour() == QuestNpcProfile.Behaviour.STATIONARY) {
                mob.setAI(false);
                if (attention != null) mob.lookAt(attention);
                continue;
            }
            mob.setAI(true);
            if (attention != null) {
                mob.getPathfinder().stopPathfinding();
                mob.lookAt(attention);
                npcNextMovement.put(profile.id(), now + 2000L);
                continue;
            }
            Location origin = npcWanderOrigins.computeIfAbsent(profile.id(), ignored -> mob.getLocation().clone());
            if (!origin.getWorld().equals(mob.getWorld())) {
                origin = mob.getLocation().clone();
                npcWanderOrigins.put(profile.id(), origin);
            }
            if (mob.getLocation().distanceSquared(origin) > (double) profile.wanderRadius() * profile.wanderRadius()) {
                mob.getPathfinder().moveTo(origin, 1D);
                continue;
            }
            if (mob.getPathfinder().hasPath() || npcNextMovement.getOrDefault(profile.id(), 0L) > now) continue;
            Location destination = findWanderDestination(origin, profile);
            if (destination != null) mob.getPathfinder().moveTo(destination, 0.8D);
            npcNextMovement.put(profile.id(), now + profile.wanderDelaySeconds() * 1000L);
            if (profile.lookAtPlayers() && destination == null) {
                mob.getWorld().getPlayers().stream()
                    .filter(player -> player.getLocation().distanceSquared(mob.getLocation()) <= 100D)
                    .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(mob.getLocation())))
                    .ifPresent(mob::lookAt);
            }
        }
    }

    private boolean availabilityEnded(QuestNpcProfile profile, long worldTime) {
        if (profile.timeUntil() == 24000) return false;
        long tick = Math.floorMod(worldTime, 24000);
        return profile.timeFrom() <= profile.timeUntil()
            ? tick >= profile.timeUntil()
            : tick >= profile.timeUntil() && tick < profile.timeFrom();
    }

    private boolean isNpcEngaged(String profileId) {
        return npcMenuEngagements.containsValue(profileId)
            || npcInteractionUntil.getOrDefault(profileId, 0L) > System.currentTimeMillis();
    }

    private void queueNpcDeparture(QuestNpcProfile profile, long now) {
        if (npcLeavingSince.containsKey(profile.id()) || npcDepartureDue.containsKey(profile.id())) return;
        long delay = isNpcEngaged(profile.id()) ? 0L
            : ThreadLocalRandom.current().nextLong(npcLeavingRandomDelayTicks + 1L) * 50L;
        npcDepartureDue.put(profile.id(), now + delay);
    }

    private void tickLeavingNpc(QuestNpcProfile profile, Entity entity, long now) {
        long timeoutMillis = npcLeavingTimeoutTicks * 50L;
        boolean farEnough = entity.getWorld().getPlayers().stream().allMatch(player ->
            player.getLocation().distanceSquared(entity.getLocation()) >= (double) npcLeavingPlayerDistance * npcLeavingPlayerDistance);
        if (farEnough || now - npcLeavingSince.get(profile.id()) >= timeoutMillis) {
            if (profile.lifetimeSeconds() > 0) recordNpcDeath(profile, entity.getWorld());
            despawnProfileEntity(profile, entity, false);
            profile.spawnedEntity(null);
            profile.spawnedAt(0);
            saveNpcProfiles();
            registerMarkers();
            return;
        }
        Location destination = leavingDestination(entity);
        if (destination == null) return;
        if (profile.npcType() == EntityType.PLAYER && citizensAvailable()) {
            CitizensQuestNpcSupport.setPaused(profile, false);
            CitizensQuestNpcSupport.moveTo(profile, destination, 1D);
        } else if (entity instanceof Mob mob) {
            mob.setAI(true);
            mob.getPathfinder().moveTo(destination, 1D);
        }
    }

    private @Nullable Location leavingDestination(Entity entity) {
        List<Player> players = entity.getWorld().getPlayers();
        if (players.isEmpty()) return null;
        double awayX = 0D, awayZ = 0D;
        for (Player player : players) {
            double dx = entity.getLocation().getX() - player.getLocation().getX();
            double dz = entity.getLocation().getZ() - player.getLocation().getZ();
            double length = Math.max(0.001D, Math.hypot(dx, dz));
            awayX += dx / length;
            awayZ += dz / length;
        }
        double length = Math.hypot(awayX, awayZ);
        if (length < 0.001D) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
            awayX = Math.cos(angle); awayZ = Math.sin(angle); length = 1D;
        }
        Location current = entity.getLocation();
        int x = (int) Math.floor(current.getX() + awayX / length * 24D);
        int z = (int) Math.floor(current.getZ() + awayZ / length * 24D);
        Integer y = findSafeNpcY(entity.getWorld(), x, z, current.getBlockY());
        return y == null ? null : new Location(entity.getWorld(), x + .5D, y, z + .5D);
    }

    private @Nullable Entity reconcileProfileEntity(QuestNpcProfile profile) {
        if (profile.npcType() == EntityType.PLAYER && citizensAvailable())
            return CitizensQuestNpcSupport.spawnedEntity(profile);
        return profile.spawnedEntity() == null ? null : Bukkit.getEntity(profile.spawnedEntity());
    }

    private @Nullable Player attentionPlayer(QuestNpcProfile profile, Entity entity, long now) {
        if (!profile.lookAtPlayers()) return null;
        Player nearby = entity.getWorld().getPlayers().stream()
            .filter(player -> player.getLocation().distanceSquared(entity.getLocation()) <= 36D)
            .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(entity.getLocation())))
            .orElse(null);
        if (nearby != null) {
            npcAttentionPlayers.put(profile.id(), nearby.getUniqueId());
            npcAttentionUntil.put(profile.id(), now + 2000L);
            return nearby;
        }
        if (npcAttentionUntil.getOrDefault(profile.id(), 0L) <= now) {
            npcAttentionPlayers.remove(profile.id());
            npcAttentionUntil.remove(profile.id());
            return null;
        }
        Player player = Bukkit.getPlayer(npcAttentionPlayers.get(profile.id()));
        return player != null && player.getWorld().equals(entity.getWorld()) ? player : null;
    }

    private void focusNpcOnPlayer(Entity entity, Player player) {
        String profileId = entity.getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
        QuestNpcProfile profile = profileId == null ? null : npcProfiles.get(profileId);
        if (profile == null) return;
        if (!profile.anchorInteracted()) {
            profile.anchorInteracted(true);
            saveNpcProfiles();
        }
        if (!profile.lookAtPlayers()) return;
        npcAttentionPlayers.put(profileId, player.getUniqueId());
        npcAttentionUntil.put(profileId, System.currentTimeMillis() + 8000L);
        npcInteractionUntil.put(profileId, System.currentTimeMillis() + 8000L);
        if (profile.npcType() == EntityType.PLAYER && citizensAvailable()) {
            CitizensQuestNpcSupport.setPaused(profile, true);
        } else if (entity instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            mob.lookAt(player);
        }
    }

    private @Nullable Location findWanderDestination(Location origin, QuestNpcProfile profile) {
        World world = origin.getWorld();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
            double distance = ThreadLocalRandom.current().nextDouble(1D, profile.wanderRadius() + 1D);
            int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
            Integer y = findSafeNpcY(world, x, z, origin.getBlockY());
            if (y == null) continue;
            if (Math.abs(y - origin.getBlockY()) > profile.wanderVerticalRadius()) continue;
            if (!profileAllowsBiome(profile, world, x, y, z)) continue;
            return new Location(world, x + .5D, y, z + .5D);
        }
        return null;
    }

    private @Nullable Location findNpcSpawn(QuestNpcProfile profile, Player player) {
        return findNpcSpawn(profile, player, 0);
    }

    private @Nullable Location findNpcSpawn(QuestNpcProfile profile, Player player, int maximumAnchorDistance) {
        World world = player.getWorld();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
            double distance = ThreadLocalRandom.current().nextDouble(profile.minDistance(), profile.maxDistance() + 1D);
            int x = (int) Math.floor(player.getLocation().getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getLocation().getZ() + Math.sin(angle) * distance);
            Integer y = findSafeNpcY(world, x, z, player.getLocation().getBlockY());
            if (y == null) continue;
            Location candidate = new Location(world, x + .5D, y, z + .5D);
            Location previousAnchor = maximumAnchorDistance > 0 ? npcAnchor(profile) : null;
            if (previousAnchor != null && (!previousAnchor.getWorld().equals(world)
                || previousAnchor.distanceSquared(candidate) > (double) maximumAnchorDistance * maximumAnchorDistance)) continue;
            String biome = world.getBiome(x, y, z).getKey().getKey();
            if (!profile.biomes().isEmpty() && profile.biomes().stream().noneMatch(value -> value.equalsIgnoreCase(biome))) continue;
            return preferOpenNpcSpawn(profile, candidate, true);
        }
        return null;
    }

    static long npcSpawnPeriod(QuestNpcProfile profile, long fullTime) {
        return Math.floorDiv(fullTime - profile.timeFrom(), 24000L);
    }

    private @Nullable Location npcAnchor(QuestNpcProfile profile) {
        if (!profile.hasAnchor()) return null;
        World world = Bukkit.getWorld(profile.anchorWorld());
        if (world == null) return null;
        int x = (int) Math.floor(profile.anchorX()), z = (int) Math.floor(profile.anchorZ());
        Integer y = findSafeNpcY(world, x, z, (int) Math.floor(profile.anchorY()));
        return y == null ? null : new Location(world, profile.anchorX(), y, profile.anchorZ());
    }

    /** Prefer nearby headroom for the NPC marker, while retaining the original safe spawn as a fallback. */
    private Location preferOpenNpcSpawn(QuestNpcProfile profile, Location fallback, boolean enforceBiome) {
        World world = fallback.getWorld();
        int fallbackX = fallback.getBlockX();
        int fallbackY = fallback.getBlockY();
        int fallbackZ = fallback.getBlockZ();
        if (hasNpcClearance(world, fallbackX, fallbackY, fallbackZ)) return fallback;

        for (int radius = 1; radius <= NPC_OPEN_AREA_RADIUS; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) continue;
                    int x = fallbackX + xOffset;
                    int z = fallbackZ + zOffset;
                    Integer y = findSafeNpcY(world, x, z, fallbackY);
                    if (y == null || !hasNpcClearance(world, x, y, z)) continue;
                    if (enforceBiome && !profileAllowsBiome(profile, world, x, y, z)) continue;
                    return new Location(world, x + .5D, y, z + .5D, fallback.getYaw(), fallback.getPitch());
                }
            }
        }
        return fallback;
    }

    private boolean profileAllowsBiome(QuestNpcProfile profile, World world, int x, int y, int z) {
        if (profile.biomes().isEmpty()) return true;
        String biome = world.getBiome(x, y, z).getKey().getKey();
        return profile.biomes().stream().anyMatch(value -> value.equalsIgnoreCase(biome));
    }

    static boolean hasNpcClearance(World world, int x, int y, int z) {
        for (int offset = 0; offset <= NPC_OVERHEAD_CLEARANCE_BLOCKS; offset++) {
            if (!world.getBlockAt(x, y + offset, z).isPassable()) return false;
        }
        return true;
    }

    private @Nullable Integer findSafeNpcY(World world, int x, int z, int preferredY) {
        if (world.getEnvironment() != World.Environment.NETHER) {
            int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            return safeNpcSpace(world, x, surface, z) ? surface : null;
        }
        int minimum = world.getMinHeight() + 1;
        int maximum = world.getMaxHeight() - 2;
        int centre = Math.max(minimum, Math.min(maximum, preferredY));
        for (int offset = 0; offset <= 48; offset++) {
            int above = centre + offset;
            if (above <= maximum && safeNpcSpace(world, x, above, z)) return above;
            int below = centre - offset;
            if (offset > 0 && below >= minimum && safeNpcSpace(world, x, below, z)) return below;
        }
        return null;
    }

    private boolean safeNpcSpace(World world, int x, int y, int z) {
        return safeNpcGround(world.getBlockAt(x, y - 1, z).getType())
            && world.getBlockAt(x, y, z).isPassable()
            && world.getBlockAt(x, y + 1, z).isPassable();
    }

    private boolean safeNpcGround(Material ground) {
        return ground.isSolid() && ground != Material.MAGMA_BLOCK && ground != Material.CACTUS
            && !Tag.LEAVES.isTagged(ground) && !Tag.LOGS.isTagged(ground);
    }

    private void saveNpcProfiles() {
        try { QuestNpcProfileStore.save(npcFile, npcProfiles); }
        catch (IOException ex) { api.messages().error("Could not save quest NPC profiles: {error}", "error", ex.getMessage()); }
    }

    private void migrateBundledNpcProfiles() {
        if (api.database().migrationVersion("quest-npc-profiles") >= 1) return;
        if (migrateLegacySeleneProfile(npcProfiles.get("expansion-selene"))) saveNpcProfiles();
        api.database().setMigrationVersion("quest-npc-profiles", 1);
    }

    static boolean migrateLegacySeleneProfile(QuestNpcProfile profile) {
        if (profile == null || profile.timeFrom() != 0 || profile.timeUntil() != 24000) return false;
        Set<String> biomes = profile.biomes().stream()
            .map(value -> value.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        if (!biomes.equals(Set.of("LUSH_CAVES", "DRIPSTONE_CAVES", "PLAINS"))) return false;
        profile.timeUntil(12000);
        profile.biomes().removeIf(value -> value.equalsIgnoreCase("PLAINS"));
        return true;
    }

    private void ensureStorage() {
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_progress (
              player_uuid TEXT NOT NULL,
              quest_id TEXT NOT NULL,
              objective_index INTEGER NOT NULL DEFAULT 0,
              objective_progress INTEGER NOT NULL DEFAULT 0,
              state TEXT NOT NULL DEFAULT 'ACTIVE',
              PRIMARY KEY (player_uuid, quest_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_npc_daily_roll (
              player_uuid TEXT NOT NULL,
              profile_id TEXT NOT NULL,
              minecraft_day INTEGER NOT NULL,
              allowed INTEGER NOT NULL,
              PRIMARY KEY (player_uuid, profile_id, minecraft_day)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_completed (
              player_uuid TEXT NOT NULL,
              quest_id TEXT NOT NULL,
              completed_at INTEGER NOT NULL,
              PRIMARY KEY (player_uuid, quest_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_attempt_revision (
              player_uuid TEXT NOT NULL,
              quest_id TEXT NOT NULL,
              revision INTEGER NOT NULL,
              PRIMARY KEY (player_uuid, quest_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_npc_death_day (
              profile_id TEXT PRIMARY KEY,
              minecraft_day INTEGER NOT NULL
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_attempt_timing (
              player_uuid TEXT NOT NULL, quest_id TEXT NOT NULL, started_at INTEGER NOT NULL,
              PRIMARY KEY (player_uuid, quest_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_failure (
              player_uuid TEXT NOT NULL, quest_id TEXT NOT NULL, failed_at INTEGER NOT NULL, reason TEXT NOT NULL,
              PRIMARY KEY (player_uuid, quest_id)
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_tracking (
              player_uuid TEXT PRIMARY KEY, quest_id TEXT NOT NULL
            );
            """);
        api.database().execute("""
            CREATE TABLE IF NOT EXISTS quest_tracking_preferences (
              player_uuid TEXT PRIMARY KEY, auto_enabled INTEGER NOT NULL
            );
            """);
        api.database().execute("""
            INSERT OR IGNORE INTO quest_attempt_revision(player_uuid, quest_id, revision)
            SELECT player_uuid, quest_id, 1 FROM quest_progress;
            """);
    }

    boolean dailySpawnAllowed(Player player, QuestNpcProfile profile) {
        long day = player.getWorld().getFullTime() / 24000L;
        Boolean stored = api.database().querySingleMapped(
            "SELECT allowed FROM quest_npc_daily_roll WHERE player_uuid=? AND profile_id=? AND minecraft_day=?",
            statement -> { statement.setString(1, player.getUniqueId().toString()); statement.setString(2, profile.id()); statement.setLong(3, day); },
            result -> result.getInt("allowed") != 0
        );
        if (stored != null) return stored;
        boolean allowed = QuestNpcSpawnRules.dailyRoll(profile.dailyChance(), ThreadLocalRandom.current().nextDouble());
        api.database().update("INSERT INTO quest_npc_daily_roll(player_uuid,profile_id,minecraft_day,allowed) VALUES(?,?,?,?)", statement -> {
            statement.setString(1, player.getUniqueId().toString()); statement.setString(2, profile.id()); statement.setLong(3, day); statement.setInt(4, allowed ? 1 : 0);
        });
        api.database().update("DELETE FROM quest_npc_daily_roll WHERE minecraft_day < ?", statement -> statement.setLong(1, day - 7));
        return allowed;
    }

    private boolean npcDiedToday(QuestNpcProfile profile) {
        World world = Bukkit.getWorld(profile.world());
        if (world == null) return false;
        long today = world.getFullTime() / 24000L;
        Long deathDay = api.database().querySingleMapped("SELECT minecraft_day FROM quest_npc_death_day WHERE profile_id=?",
            statement -> statement.setString(1, profile.id()), result -> result.getLong("minecraft_day"));
        return deathDay != null && deathDay >= today;
    }

    private void recordNpcDeath(QuestNpcProfile profile, World world) {
        long day = world.getFullTime() / 24000L;
        api.database().update("INSERT OR REPLACE INTO quest_npc_death_day(profile_id,minecraft_day) VALUES(?,?)", statement -> {
            statement.setString(1, profile.id()); statement.setLong(2, day);
        });
    }

    private void loadPlayerState() {
        active.clear();
        completed.clear();
        revisions.clear();
        attemptStarted.clear();
        trackedQuests.clear();
        autoTrackPlayers.clear();
        trackingPreferencePlayers.clear();
        api.database().queryEach("SELECT player_uuid, quest_id FROM quest_tracking", null, rs ->
            trackedQuests.put(UUID.fromString(rs.getString("player_uuid")), rs.getString("quest_id")));
        api.database().queryEach("SELECT player_uuid, auto_enabled FROM quest_tracking_preferences", null, rs -> {
            UUID player = UUID.fromString(rs.getString("player_uuid"));
            trackingPreferencePlayers.add(player);
            if (rs.getInt("auto_enabled") != 0) autoTrackPlayers.add(player);
        });
        api.database().queryEach("SELECT player_uuid, quest_id, started_at FROM quest_attempt_timing", null, rs ->
            attemptStarted.computeIfAbsent(UUID.fromString(rs.getString("player_uuid")), ignored -> new HashMap<>())
                .put(rs.getString("quest_id"), rs.getLong("started_at")));
        api.database().queryEach("SELECT player_uuid, quest_id, objective_index, objective_progress, state FROM quest_progress", null, rs -> {
            UUID player = UUID.fromString(rs.getString("player_uuid"));
            QuestProgress progress = new QuestProgress(player, rs.getString("quest_id"), rs.getInt("objective_index"),
                rs.getInt("objective_progress"), QuestProgress.State.valueOf(rs.getString("state")));
            active.computeIfAbsent(player, ignored -> new HashMap<>()).put(progress.questId(), progress);
        });
        api.database().queryEach("SELECT player_uuid, quest_id FROM quest_completed", null, rs ->
            completed.computeIfAbsent(UUID.fromString(rs.getString("player_uuid")), ignored -> new java.util.HashSet<>())
                .add(rs.getString("quest_id")));
        api.database().queryEach("SELECT player_uuid, quest_id, revision FROM quest_attempt_revision", null, rs ->
            revisions.computeIfAbsent(UUID.fromString(rs.getString("player_uuid")), ignored -> new HashMap<>())
                .put(rs.getString("quest_id"), rs.getInt("revision")));
    }

    private void reloadDefinitions() {
        for (QuestDefinition quest : quests.values()) deleteMarkers(quest);
        quests.clear();
        quests.putAll(QuestDefinitionStore.load(questFile));
    }

    private void saveDefinitions() {
        try {
            QuestDefinitionStore.save(questFile, quests);
        } catch (IOException ex) {
            api.messages().error("Could not save quests.yml: {error}", "error", ex.getMessage());
        }
    }

    private void registerEvents() {
        // Citizens may cancel its Bukkit interaction event before quest dialogue is handled. Receive cancelled events,
        // then restrict them to managed quest NPCs in onNpcClick so protected unrelated entities remain ignored.
        api.events().register(PlayerInteractEntityEvent.class, this::onNpcClick, EventPriority.NORMAL, false);
        api.events().register(EntityDeathEvent.class, this::onEntityDeath, EventPriority.HIGHEST, true);
        api.events().register(PlayerMoveEvent.class, this::onPlayerMove, EventPriority.MONITOR, true);
        api.events().register(TimeSkipEvent.class, this::onTimeSkip, EventPriority.MONITOR, true);
        api.events().register(PlayerQuitEvent.class, event -> {
            BossBar bar = trackingBossBars.remove(event.getPlayer().getUniqueId());
            if (bar != null) event.getPlayer().hideBossBar(bar);
            timedQuestRemaining.remove(event.getPlayer().getUniqueId());
            autoTrackActivityQuests.remove(event.getPlayer().getUniqueId());
            autoTrackActivityUntil.remove(event.getPlayer().getUniqueId());
            npcMenuEngagements.remove(event.getPlayer().getUniqueId());
        }, EventPriority.MONITOR, true);
        api.events().register(EntityPickupItemEvent.class, event -> {
            if (!(event.getEntity() instanceof Player player)) return;
            if (!isCurrentQuestBook(event.getItem().getItemStack())) {
                event.setCancelled(true);
                event.getItem().remove();
                return;
            }
            api.tasks().nextTick(() -> {
                validateInventory(player.getInventory());
                syncCollectObjectives(player);
                reconcileAutomaticTracking(player);
            });
        }, EventPriority.HIGHEST, true);
        api.events().register(PlayerItemConsumeEvent.class, event -> api.tasks().nextTick(() -> {
            Player player = event.getPlayer();
            refreshOwnedBooks(player);
            refreshMarkers(player);
            if (isAutoTracking(player.getUniqueId())) updateAutomaticTracking(player);
        }), EventPriority.MONITOR, true);
        api.events().register(InventoryClickEvent.class, event -> {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            api.tasks().nextTick(() -> {
                validateInventory(event.getView().getTopInventory());
                validateInventory(player.getInventory());
                refreshOwnedBooks(player);
                reconcileAutomaticTracking(player);
            });
        }, EventPriority.HIGHEST, true);
        api.events().register(InventoryOpenEvent.class, event -> {
            validateInventory(event.getInventory());
            if (event.getPlayer() instanceof Player player) {
                validateInventory(player.getInventory());
                refreshOwnedBooks(player);
            }
        }, EventPriority.HIGHEST, true);
        api.events().register(InventoryCloseEvent.class, this::onQuestNpcInventoryClose, EventPriority.HIGHEST, true);
        api.events().register(PlayerInteractEvent.class, this::onQuestBookOpen, EventPriority.HIGHEST, true);
    }

    void registerQuestCommand() {
        api.tabComplete().register("quest", (player, args) -> new ArrayList<>(quests.keySet()));
        api.tabComplete().register("active-quest", (player, args) ->
            new ArrayList<>(active.getOrDefault(player.getUniqueId(), Map.of()).keySet()));
        api.commands().create("quest")
            .description("Manage and play book quests.")
            .usage("/quest [view|track|abandon|abandon-all|admin]")
            .tabCompletion("abandon", "{active-quest}")
            .tabCompletion("abandon-all", "confirm")
            .tabCompletion("track", "{active-quest}")
            .tabCompletion("view", "{active-quest}")
            .tabCompletion("track", "off")
            .tabCompletion("track", "auto")
            .tabCompletion("admin")
            .tabCompletion("admin", "create")
            .tabCompletion("admin", "edit", "{quest}")
            .tabCompletion("admin", "delete", "{quest}")
            .tabCompletion("admin", "npc-spawned")
            .tabCompletion("admin", "player")
            .tabCompletion("admin", "reload")
            .tabCompletion("admin", "editor")
            .executor((unused, command, context) -> execute(context))
            .register(STEMCraft.getPlugin());
    }

    private void registerCommands() {
        // Registration is performed in registerTabCompletion so command and suggestions stay together.
    }

    private void execute(CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            showPlayerMenu(ctx);
            return;
        }
        switch (ctx.getArgLower(0)) {
            case "abandon" -> cancelCommand(ctx);
            case "abandon-all" -> cancelAllCommand(ctx);
            case "track" -> trackCommand(ctx);
            case "view" -> viewQuestBook(ctx);
            case "admin" -> adminCommand(ctx);
            default -> {
                if (ctx.getArg(0).matches("\\d+")) showPlayerMenu(ctx);
                else ctx.error("Use /quest, /quest view <id>, /quest track <id|off|auto>, /quest abandon <id>, /quest abandon-all, or /quest admin.");
            }
        }
    }

    private void showPlayerMenu(CommandContext ctx) {
        Player player = ctx.asPlayer();
        List<QuestDefinition> visible = activeFor(player).values().stream()
            .map(progress -> quests.get(progress.questId())).filter(Objects::nonNull)
            .sorted(Comparator.comparing(QuestDefinition::title, String.CASE_INSENSITIVE_ORDER)).toList();
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 0, 1);
        ChatMenuUtil.render(ctx.getSender(), "Active quests", "quest", page, visible.size(), (start, count, interactive) -> {
            List<Component> lines = new ArrayList<>();
            for (QuestDefinition quest : visible.subList(start, start + count)) {
                String state = questState(player, quest);
                Component line = Component.text("[" + state + "] ", state.equals("READY") ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                    .append(Component.text(quest.title(), NamedTextColor.AQUA))
                    .append(Component.text(" — given by " + questGiverName(quest), NamedTextColor.GRAY));
                if (!PlayerUtil.isBedrock(player)) {
                    line = line.append(Component.space()).append(hasOwnedQuestBook(player, quest.id())
                        ? button("[View]", "/quest view " + quest.id(), "Open your quest book")
                        : disabledButton("[View]", "Carry your current quest book to view it"));
                }
                line = line.append(Component.space())
                    .append(hasOwnedQuestBook(player, quest.id())
                        ? button("[Track]", "/quest track " + quest.id(), "Track this quest")
                        : disabledButton("[Track]", "Carry your current quest book to track it"))
                    .append(Component.space()).append(button("[Abandon]", "/quest abandon " + quest.id(), "Abandon this quest"));
                lines.add(line);
            }
            return lines;
        }, "You have no active quests.");
    }

    private String questGiverName(QuestDefinition quest) {
        QuestNpcProfile profile = quest.startNpcProfile() == null ? null : npcProfiles.get(quest.startNpcProfile());
        if (profile != null && profile.name() != null && !profile.name().isBlank()) return profile.name();
        if (quest.startNpcName() != null && !quest.startNpcName().isBlank()) return quest.startNpcName();
        return quest.author();
    }

    private Component disabledButton(String text, String hover) {
        return Component.text(text, NamedTextColor.DARK_GRAY)
            .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private void viewQuestBook(CommandContext ctx) {
        if (!ctx.isPlayer()) { ctx.error("This command can only be used by a player."); return; }
        Player player = ctx.asPlayer();
        if (PlayerUtil.isBedrock(player)) { ctx.error("Open the quest book in your inventory to read it."); return; }
        String requested = ctx.getArg(1, "");
        if (requested.isBlank()) {
            ctx.error("You haven't selected a quest to view. Use /quest view <id>."); return;
        }
        String questId = normalizeId(requested);
        if (progress(player, questId) == null) {
            ctx.error("You don't currently have that quest book in your inventory."); return;
        }
        refreshOwnedBooks(player);
        ItemStack book = ownedQuestBook(player, questId);
        if (book == null) { ctx.error("You don't currently have that quest book in your inventory."); return; }
        player.openBook(book);
    }

    private void showQuest(CommandContext ctx, String id) {
        QuestDefinition quest = quest(id, ctx);
        if (quest == null) return;
        ctx.getSender().sendMessage(Component.text(quest.title(), NamedTextColor.AQUA)
            .append(Component.text(" by " + quest.author(), NamedTextColor.GRAY)));
        ctx.getSender().sendMessage(Component.text(quest.description(), NamedTextColor.WHITE));
        for (int i = 0; i < quest.objectives().size(); i++) {
            ctx.getSender().sendMessage(Component.text("  " + (i + 1) + ". " + quest.objectives().get(i).label(), NamedTextColor.YELLOW));
        }
        if (ctx.hasPermission(PERMISSION_ADMIN)) {
            ctx.getSender().sendMessage(adminActions(quest));
        }
    }

    private Component adminActions(QuestDefinition quest) {
        return button("[Edit]", "/quest admin edit " + quest.id(), "Edit this quest")
            .append(Component.space())
            .append(button("[Delete]", "/quest admin delete " + quest.id() + " confirm", "Delete this quest"));
    }

    private Component button(String text, String command, String hover) {
        return Component.text(text, NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private void openWebEditor(CommandContext ctx) {
        byte[] bytes = new byte[12];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        editorTokens.put(token, System.currentTimeMillis() + EDITOR_TOKEN_LIFETIME_MS);
        String url = api.web().getPublicUrl() + EDITOR_PATH + token;
        ctx.getSender().sendMessage(Component.text("Quest editor: ", NamedTextColor.YELLOW)
            .append(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Open the private quest editor")))));
        ctx.getSender().sendMessage(Component.text("This private link expires in 24 hours or when the server restarts.", NamedTextColor.GRAY));
    }

    Object handleWebEditor(WebServiceRequest request) {
        String path = request.path();
        if (!path.startsWith(EDITOR_PATH)) return webResponse(403, "text/plain; charset=utf-8", "Not permitted");
        String tail = path.substring(EDITOR_PATH.length());
        String[] parts = tail.split("/", -1);
        if (parts.length == 0 || !validEditorToken(parts[0])) return webResponse(403, "text/plain; charset=utf-8", "Not permitted");
        if (parts.length == 1 && "GET".equalsIgnoreCase(request.method()))
            return webResponse(200, "text/html; charset=utf-8", QUEST_EDITOR_HTML);
        if (parts.length == 2 && "data".equals(parts[1])) {
            if ("GET".equalsIgnoreCase(request.method())) {
                try { return webResponse(200, "application/json; charset=utf-8", onServerThread(() -> EDITOR_GSON.toJson(editorData()))); }
                catch (Exception ex) { return webResponse(500, "text/plain; charset=utf-8", "Could not load editor data: " + ex.getMessage()); }
            }
            if ("PUT".equalsIgnoreCase(request.method())) {
                if (request.body().length > 2_000_000) return webResponse(413, "text/plain; charset=utf-8", "Editor data is too large");
                try {
                    String result = onServerThread(() -> saveStructuredEditor(request.bodyAsString()));
                    return webResponse(200, "text/plain; charset=utf-8", result);
                } catch (Exception ex) { return webResponse(400, "text/plain; charset=utf-8", "Not saved: " + ex.getMessage()); }
            }
            return webResponse(405, "text/plain; charset=utf-8", "Method not allowed");
        }
        if (parts.length == 2 && "api".equals(parts[1])) {
            boolean editingNpcs = "npcs".equalsIgnoreCase(request.queryParam("file"));
            if ("GET".equalsIgnoreCase(request.method())) {
                try { return webResponse(200, "text/yaml; charset=utf-8", onServerThread(() -> Files.readString((editingNpcs ? npcFile : questFile).toPath()))); }
                catch (Exception ex) { return webResponse(500, "text/plain; charset=utf-8", "Could not read configuration: " + ex.getMessage()); }
            }
            if ("PUT".equalsIgnoreCase(request.method())) {
                if (request.body().length > 1_000_000) return webResponse(413, "text/plain; charset=utf-8", "Quest configuration is too large");
                try {
                    String result = onServerThread(() -> editingNpcs ? saveWebNpcYaml(request.bodyAsString()) : saveWebQuestYaml(request.bodyAsString()));
                    return webResponse(200, "text/plain; charset=utf-8", result);
                } catch (Exception ex) {
                    return webResponse(400, "text/plain; charset=utf-8", "Not saved: " + ex.getMessage());
                }
            }
            return webResponse(405, "text/plain; charset=utf-8", "Method not allowed");
        }
        return webResponse(404, "text/plain; charset=utf-8", "Not found");
    }

    private boolean validEditorToken(String token) {
        long now = System.currentTimeMillis();
        editorTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        Long expires = editorTokens.get(token);
        return expires != null && expires >= now;
    }

    private EditorData editorData() {
        List<EditorQuest> questValues = quests.values().stream().map(quest -> new EditorQuest(
            quest.id(), quest.title(), quest.author(), quest.description(), quest.shortDescription(), quest.rewardText(), quest.enabled(), quest.repeatable(),
            quest.timeLimitSeconds(), quest.restartCooldownSeconds(), quest.globalMaxCompletions(),
            quest.startNpcProfile(), quest.endNpcProfile(), quest.startNpcName(), quest.endNpcName(),
            new LinkedHashMap<>(quest.dialogue()), new ArrayList<>(quest.requirements()),
            quest.objectives().stream().map(objective -> new EditorObjective(objective.type().name(), objective.target(), objective.amount(),
                objective.consume(), objective.label(), objective.world(), objective.x(), objective.y(), objective.z(), objective.radius())).toList(),
            quest.rewardItems().stream().map(item -> new EditorReward(item.material().name(), item.amount(), item.name(), item.lore(), item.unbreakable())).toList(),
            new ArrayList<>(quest.rewardCommands()))).toList();
        List<EditorNpc> npcValues = npcProfiles.values().stream().map(profile -> new EditorNpc(profile.id(), profile.name(),
            profile.entityType().name(), profile.ai(), profile.world(), profile.minDistance(), profile.maxDistance(),
            profile.uniquenessRadius(), profile.despawnRadius(), profile.minimumLevel(), profile.timeFrom(), profile.timeUntil(),
            profile.dailyChance(), profile.lifetimeSeconds(), new ArrayList<>(profile.biomes()), new ArrayList<>(profile.idleDialogue()))).toList();
        List<String> materials = java.util.Arrays.stream(Material.values()).filter(Material::isItem).map(Enum::name).toList();
        List<String> entities = java.util.Arrays.stream(EntityType.values()).filter(EntityType::isAlive).map(Enum::name).toList();
        return new EditorData(questValues, npcValues, materials, entities);
    }

    private String saveStructuredEditor(String json) throws IOException {
        EditorData data;
        try { data = EDITOR_GSON.fromJson(json, EditorData.class); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid editor data", ex); }
        if (data == null || data.quests() == null || data.npcs() == null) throw new IllegalArgumentException("Quest and NPC lists are required");

        Map<String, QuestNpcProfile> newProfiles = new LinkedHashMap<>();
        for (EditorNpc value : data.npcs()) {
            String id = requireEditorId(value.id(), "NPC");
            if (newProfiles.containsKey(id)) throw new IllegalArgumentException("Duplicate NPC ID: " + id);
            EntityType entityType;
            try { entityType = EntityType.valueOf(value.entityType()); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid entity type for " + id); }
            if (!entityType.isAlive()) throw new IllegalArgumentException("NPC entity must be living: " + id);
            QuestNpcProfile profile = new QuestNpcProfile(id, required(value.name(), "NPC name", id));
            profile.entityType(entityType); profile.ai(value.ai()); profile.world(required(value.world(), "world", id));
            profile.minDistance(value.minDistance()); profile.maxDistance(value.maxDistance());
            profile.uniquenessRadius(value.uniquenessRadius()); profile.despawnRadius(value.despawnRadius());
            profile.minimumLevel(value.minimumLevel()); profile.timeFrom(value.timeFrom()); profile.timeUntil(value.timeUntil());
            profile.dailyChance(value.dailyChance()); profile.lifetimeSeconds(value.lifetimeSeconds());
            if (value.biomes() != null) profile.biomes().addAll(value.biomes().stream().filter(Objects::nonNull).map(String::trim).filter(entry -> !entry.isEmpty()).toList());
            if (value.idleDialogue() != null) profile.idleDialogue().addAll(value.idleDialogue().stream().filter(Objects::nonNull).map(String::trim).filter(entry -> !entry.isEmpty()).toList());
            QuestNpcProfile existing = npcProfiles.get(id);
            if (existing != null) {
                profile.spawnedEntity(existing.spawnedEntity());
                profile.spawnedAt(existing.spawnedAt());
                if (existing.hasAnchor()) {
                    profile.anchor(existing.anchorWorld(), existing.anchorX(), existing.anchorY(), existing.anchorZ(), existing.anchorPeriod());
                    profile.anchorInteracted(existing.anchorInteracted());
                }
            }
            newProfiles.put(id, profile);
        }

        Map<String, QuestDefinition> newQuests = new LinkedHashMap<>();
        for (EditorQuest value : data.quests()) {
            String id = requireEditorId(value.id(), "Quest");
            if (newQuests.containsKey(id)) throw new IllegalArgumentException("Duplicate quest ID: " + id);
            QuestDefinition quest = new QuestDefinition(id, required(value.title(), "title", id));
            quest.author(required(value.author(), "author", id)); quest.description(required(value.description(), "story", id));
            quest.shortDescription(value.shortDescription() == null || value.shortDescription().isBlank()
                ? quest.description() : value.shortDescription().trim());
            quest.rewardText(required(value.rewardText(), "reward description", id)); quest.enabled(value.enabled()); quest.repeatable(value.repeatable());
            quest.timeLimitSeconds(value.timeLimitSeconds()); quest.restartCooldownSeconds(value.restartCooldownSeconds());
            quest.globalMaxCompletions(value.globalMaxCompletions());
            quest.startNpcProfile(optionalProfile(value.startNpcProfile(), newProfiles, id));
            quest.endNpcProfile(optionalProfile(value.endNpcProfile(), newProfiles, id));
            quest.startNpcName(required(value.startNpcName(), "start NPC name", id)); quest.endNpcName(required(value.endNpcName(), "end NPC name", id));
            QuestDefinition existing = quests.get(id);
            if (existing != null) { quest.startNpc(existing.startNpc()); quest.endNpc(existing.endNpc()); }
            quest.dialogue().clear();
            if (value.dialogue() != null) value.dialogue().forEach((state, lines) -> quest.dialogue(state).addAll(
                lines == null ? List.of() : lines.stream().filter(Objects::nonNull).map(String::trim).filter(line -> !line.isEmpty()).toList()));
            if (value.requirements() != null) quest.requirements().addAll(value.requirements().stream().filter(Objects::nonNull).map(String::trim).filter(entry -> !entry.isEmpty()).toList());
            if (value.objectives() != null) for (EditorObjective objective : value.objectives()) {
                QuestObjective.Type type;
                try { type = QuestObjective.Type.valueOf(objective.type()); }
                catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid objective type in " + id); }
                String target = Objects.toString(objective.target(), "");
                switch (type) {
                    case COLLECT -> {
                        Material material = Material.matchMaterial(target);
                        if (material == null || !material.isItem()) throw new IllegalArgumentException("Invalid collection material in " + id);
                        target = material.name();
                    }
                    case KILL -> {
                        try { if (!EntityType.valueOf(target).isAlive()) throw new IllegalArgumentException(); }
                        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid kill entity in " + id); }
                    }
                    case NPC -> {
                        if (!target.startsWith("profile:") || !newProfiles.containsKey(target.substring(8)))
                            throw new IllegalArgumentException("Invalid NPC objective target in " + id);
                    }
                    case BIOME -> { if (target.isBlank()) throw new IllegalArgumentException("Missing biome target in " + id); }
                    case ALTITUDE_ABOVE, ALTITUDE_BELOW, UNDERWATER -> {
                        try { Double.parseDouble(target); }
                        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid altitude target in " + id); }
                    }
                    case STRUCTURE -> {
                        if (!Set.of("SHIPWRECK", "SHIPWRECK_BEACHED", "PILLAGER_OUTPOST", "OCEAN_RUIN_COLD", "OCEAN_RUIN_WARM",
                            "RUINED_PORTAL", "MINESHAFT", "BURIED_TREASURE").contains(target))
                            throw new IllegalArgumentException("Invalid structure target in " + id);
                    }
                    case INTERACT -> {
                        try { if (!EntityType.valueOf(target).isAlive()) throw new IllegalArgumentException(); }
                        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid interaction entity in " + id); }
                    }
                    case SLEEP, NIGHT -> target = "";
                    case LOCATION -> target = "";
                }
                quest.objectives().add(new QuestObjective(type, target, objective.amount(), objective.consume(),
                    objective.label(), objective.world(), objective.x(), objective.y(), objective.z(), objective.radius()));
            }
            if (value.rewardItems() != null) for (EditorReward reward : value.rewardItems()) {
                Material material = Material.matchMaterial(Objects.toString(reward.material(), ""));
                if (material == null || !material.isItem()) throw new IllegalArgumentException("Invalid reward material in " + id);
                quest.rewardItems().add(new QuestRewardItem(material, reward.amount(), reward.name(), reward.lore(), reward.unbreakable()));
            }
            if (value.rewardCommands() != null) quest.rewardCommands().addAll(value.rewardCommands().stream().filter(Objects::nonNull).map(String::trim).filter(command -> !command.isEmpty()).toList());
            newQuests.put(id, quest);
        }
        for (QuestDefinition quest : newQuests.values()) for (String required : quest.requirements())
            if (!newQuests.containsKey(required)) throw new IllegalArgumentException("Quest " + quest.id() + " requires unknown quest " + required);

        File questTemp = File.createTempFile("quests-", ".yml", questFile.getParentFile());
        File npcTemp = File.createTempFile("npcs-", ".yml", npcFile.getParentFile());
        try {
            QuestDefinitionStore.save(questTemp, newQuests); QuestNpcProfileStore.save(npcTemp, newProfiles);
            if (questFile.exists()) Files.copy(questFile.toPath(), questFile.toPath().resolveSibling("quests.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
            if (npcFile.exists()) Files.copy(npcFile.toPath(), npcFile.toPath().resolveSibling("npcs.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(questTemp.toPath(), questFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(npcTemp.toPath(), npcFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(questTemp.toPath()); Files.deleteIfExists(npcTemp.toPath()); }
        for (QuestNpcProfile existing : npcProfiles.values()) if (!newProfiles.containsKey(existing.id())) removeProfileEntities(existing);
        quests.clear(); quests.putAll(newQuests); npcProfiles.clear(); npcProfiles.putAll(newProfiles); registerMarkers();
        return "Saved " + quests.size() + " quests and " + npcProfiles.size() + " NPC profiles";
    }

    private String requireEditorId(String raw, String type) {
        String id = normalizeId(raw);
        if (raw == null || !id.equals(raw) || id.isBlank()) throw new IllegalArgumentException(type + " ID must use lowercase letters, numbers, hyphens or underscores: " + raw);
        return id;
    }

    private String required(String value, String field, String id) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field + " for " + id);
        return value.trim();
    }

    private @Nullable String optionalProfile(@Nullable String profile, Map<String, QuestNpcProfile> profiles, String questId) {
        if (profile == null || profile.isBlank()) return null;
        if (!profiles.containsKey(profile)) throw new IllegalArgumentException("Quest " + questId + " references unknown NPC " + profile);
        return profile;
    }

    private record EditorData(List<EditorQuest> quests, List<EditorNpc> npcs, List<String> materials, List<String> entityTypes) { }
    private record EditorQuest(String id, String title, String author, String description, String shortDescription, String rewardText, boolean enabled,
        boolean repeatable, long timeLimitSeconds, long restartCooldownSeconds, int globalMaxCompletions,
        @Nullable String startNpcProfile, @Nullable String endNpcProfile, String startNpcName, String endNpcName,
        Map<String, List<String>> dialogue, List<String> requirements, List<EditorObjective> objectives,
        List<EditorReward> rewardItems, List<String> rewardCommands) { }
    private record EditorObjective(String type, String target, int amount, boolean consume, String label, @Nullable String world,
        double x, double y, double z, double radius) { }
    private record EditorReward(String material, int amount, String name, List<String> lore, boolean unbreakable) { }
    private record EditorNpc(String id, String name, String entityType, boolean ai, String world, int minDistance, int maxDistance,
        int uniquenessRadius, int despawnRadius, int minimumLevel, long timeFrom, long timeUntil, double dailyChance, long lifetimeSeconds,
        List<String> biomes, List<String> idleDialogue) { }

    private String saveWebQuestYaml(String content) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        if (!yaml.isConfigurationSection("quests")) throw new InvalidConfigurationException("A quests section is required");
        Map<String, QuestDefinition> parsed = QuestDefinitionStore.load(new StringReader(content));
        int configured = Objects.requireNonNull(yaml.getConfigurationSection("quests")).getKeys(false).size();
        if (parsed.size() != configured) throw new InvalidConfigurationException("One or more quest definitions are invalid");
        File parent = questFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create quest directory");
        if (questFile.exists()) Files.copy(questFile.toPath(), questFile.toPath().resolveSibling("quests.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(questFile.toPath(), content, StandardCharsets.UTF_8);
        reloadDefinitions();
        registerMarkers();
        return "Saved " + parsed.size() + " quests";
    }

    private String saveWebNpcYaml(String content) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        if (!yaml.isConfigurationSection("npcs")) throw new InvalidConfigurationException("An npcs section is required");
        Map<String, QuestNpcProfile> parsed = QuestNpcProfileStore.load(new StringReader(content));
        int configured = Objects.requireNonNull(yaml.getConfigurationSection("npcs")).getKeys(false).size();
        if (parsed.size() != configured) throw new InvalidConfigurationException("One or more NPC profiles are invalid");
        File parent = npcFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create quest directory");
        if (npcFile.exists()) Files.copy(npcFile.toPath(), npcFile.toPath().resolveSibling("npcs.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(npcFile.toPath(), content, StandardCharsets.UTF_8);
        for (QuestNpcProfile existing : npcProfiles.values()) if (!parsed.containsKey(existing.id())) removeProfileEntities(existing);
        npcProfiles.clear();
        npcProfiles.putAll(parsed);
        registerMarkers();
        return "Saved " + parsed.size() + " NPC profiles";
    }

    private <T> T onServerThread(Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) return task.call();
        return Bukkit.getScheduler().callSyncMethod(STEMCraft.getPlugin(), task).get(10, TimeUnit.SECONDS);
    }

    private Map<String, Object> webResponse(int code, String contentType, String body) {
        return Map.of("responseCode", code, "contentType", contentType, "body", body,
            "headers", Map.of("Cache-Control", "no-store", "X-Content-Type-Options", "nosniff",
                "Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'"));
    }

    private static final String QUEST_EDITOR_HTML = """
        <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>STEMCraft Quest Editor</title><style>
        :root{color-scheme:dark;--bg:#0d1218;--panel:#151d27;--line:#2e3b4b;--muted:#9aa9b9;--gold:#e3aa3c;--blue:#69b7ff}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:#edf2f7;font:14px system-ui,sans-serif}button,input,select,textarea{font:inherit}button{border:0;border-radius:6px;padding:8px 12px;background:#2a394a;color:#edf2f7;cursor:pointer}button.primary{background:var(--gold);color:#1b1408;font-weight:700}button.danger{background:#66343a}.top{height:62px;display:flex;align-items:center;gap:10px;padding:0 16px;background:var(--panel);border-bottom:1px solid var(--line)}.top h1{font-size:18px;margin-right:auto}.status{color:var(--muted)}.error{color:#ff9292}.app{display:grid;grid-template-columns:380px 1fr;height:calc(100vh - 62px)}aside{border-right:1px solid var(--line);background:#111821;display:flex;flex-direction:column;min-width:0}.tabs{display:flex;padding:12px;gap:6px}.tabs button{flex:1}.tabs .active{background:#34506d}.search{margin:0 12px 10px;width:calc(100% - 24px)}input,select,textarea{width:100%;background:#0d141c;border:1px solid #344354;border-radius:6px;color:#edf2f7;padding:8px}textarea{min-height:90px;resize:vertical}.list{overflow:auto;flex:1}.item{width:100%;text-align:left;border-bottom:1px solid #202b38;background:transparent;padding:7px 9px}.item.active{background:#243446}.item-main{display:block;width:100%;text-align:left;background:transparent;padding:4px 5px}.item-main small{display:block;color:var(--muted);margin-top:3px}.tree-mark{color:#607891;white-space:pre;font:12px ui-monospace,monospace;margin-right:5px}.tree-mark.root{color:#c8a85b}.flow{display:flex;align-items:baseline;gap:4px;padding:2px 5px 4px 24px;color:#8fa2b5;font-size:11px;font-style:italic;line-height:1.35}.flow button{display:inline;padding:0;background:transparent;color:#79bfff;font-size:11px;font-style:italic;text-align:left}.flow button:hover{text-decoration:underline}.flow-icon{width:13px;flex:0 0 13px;color:#c8a85b}.dot{color:#67d28a;margin-right:6px}.dot.off{color:#657383}.side-actions{display:flex;gap:6px;padding:12px;border-top:1px solid var(--line)}main{overflow:auto;padding:22px}.empty{color:var(--muted);padding:50px;text-align:center}.head{display:flex;align-items:center;gap:8px;margin-bottom:18px}.head h2{margin:0 auto 0 0}.card{background:var(--panel);border:1px solid var(--line);border-radius:9px;padding:16px;margin-bottom:15px}.card h3{margin:0 0 14px;font-size:15px;color:#bcd5ed}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.grid.three{grid-template-columns:repeat(3,minmax(0,1fr))}.wide{grid-column:1/-1}label{display:block;color:var(--muted);font-size:12px}label input,label select,label textarea{margin-top:5px;color:#edf2f7}.checks{display:flex;gap:20px;align-items:center}.checks label{font-size:14px}.checks input{width:auto;margin-right:7px}.row{border:1px solid #334253;border-radius:7px;padding:12px;margin:9px 0}.row-head{display:flex;gap:8px;align-items:center;margin-bottom:10px}.row-head strong{margin-right:auto}.small{width:auto}.advanced{display:none;height:calc(100vh - 105px)}.advanced textarea{height:calc(100% - 50px);font:13px/1.5 ui-monospace,monospace}.advanced-bar{display:flex;gap:8px;margin-bottom:10px}.advanced-bar select{max-width:180px}@media(max-width:800px){.app{grid-template-columns:1fr}aside{height:300px;border-right:0;border-bottom:1px solid var(--line)}main{padding:12px}.grid,.grid.three{grid-template-columns:1fr}}
        </style></head><body><header class="top"><h1>STEMCraft Quest Editor</h1><button id="structured">Structured</button><button id="advanced">Advanced YAML</button><span id="status" class="status">Loading…</span><button id="discard">Discard</button><button id="save" class="primary">Save all</button></header><div class="app" id="structuredView"><aside><div class="tabs"><button data-kind="quests" class="active">Quests</button><button data-kind="npcs">NPCs</button></div><input id="search" class="search" placeholder="Search…"><div id="list" class="list"></div><div class="side-actions"><button id="new" class="primary">+ New</button><button id="duplicate">Duplicate</button></div></aside><main id="form"><div class="empty">Select a quest or NPC</div></main></div><main id="advancedView" class="advanced"><div class="advanced-bar"><select id="yamlFile"><option value="quests">quests.yml</option><option value="npcs">npcs.yml</option></select><button id="yamlReload">Reload</button><button id="yamlSave" class="primary">Save YAML</button></div><textarea id="yaml" spellcheck="false"></textarea></main><script>
        const base=location.pathname,dataUrl=base+'/data',apiUrl=base+'/api',q=s=>document.querySelector(s),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));let data,kind='quests',selected=null,dirty=false;
        const status=(text,error=false)=>{q('#status').textContent=text;q('#status').className=error?'status error':'status'};const mark=()=>{dirty=true;status('Unsaved changes')};const lines=s=>String(s??'').split('\\n').map(x=>x.trim()).filter(Boolean);const options=(values,current,blank=true)=>(blank?'<option value="">None</option>':'')+values.map(v=>`<option ${v===current?'selected':''}>${esc(v)}</option>`).join('');
        async function loadData(){status('Loading…');const r=await fetch(dataUrl,{cache:'no-store'});if(!r.ok)throw Error(await r.text());data=await r.json();dirty=false;if(!selected||!data[kind].some(x=>x.id===selected))selected=data[kind][0]?.id||null;render();status('Ready')}
        function questDepth(x,seen=new Set()){if(seen.has(x.id))return 0;seen.add(x.id);const parents=(x.requirements||[]).map(id=>data.quests.find(q=>q.id===id)).filter(Boolean);return parents.length?1+Math.max(...parents.map(p=>questDepth(p,new Set(seen)))):0}function questLevel(x){return data.npcs.find(n=>n.id===x.startNpcProfile)?.minimumLevel??0}function questSort(a,b){return questLevel(a)-questLevel(b)||a.title.localeCompare(b.title)}function primaryParent(x){return data.quests.find(q=>q.id===(x.requirements||[])[0])}function treeDepth(x,seen=new Set()){if(!x||seen.has(x.id))return 0;seen.add(x.id);const parent=primaryParent(x);return parent?1+treeDepth(parent,seen):0}function treeOrderedValues(){if(kind!=='quests')return data.npcs;const visited=new Set(),result=[],visit=x=>{if(!x||visited.has(x.id))return;visited.add(x.id);result.push(x);data.quests.filter(child=>primaryParent(child)?.id===x.id).sort(questSort).forEach(visit)};data.quests.filter(x=>!primaryParent(x)).sort(questSort).forEach(visit);data.quests.filter(x=>!visited.has(x.id)).sort(questSort).forEach(visit);return result}function treeMark(x){const depth=treeDepth(x);return depth?'<span class="tree-mark">'+('│  '.repeat(Math.max(0,depth-1)))+'├─</span>':'<span class="tree-mark root">○</span>'}function orderedValues(){if(kind!=='quests')return data.npcs;return [...data.quests].sort((a,b)=>questDepth(a)-questDepth(b)||questSort(a,b))}function flowLink(x){return `<button data-open-quest="${esc(x.id)}">${esc(x.title)}</button>`}function questFlow(x){const parents=(x.requirements||[]).map(id=>data.quests.find(q=>q.id===id)).filter(Boolean),children=data.quests.filter(q=>(q.requirements||[]).includes(x.id));let rows=[];if(!parents.length)rows.push('<div class="flow"><span class="flow-icon">○</span><span>Start of a quest path</span></div>');else if(parents.length>1)rows.push(`<div class="flow"><span class="flow-icon">⋈</span><span>Joins ${parents.length} paths from ${parents.slice(0,2).map(flowLink).join(', ')}${parents.length>2?` +${parents.length-2} more`:''}</span></div>`);if(children.length===1)rows.push(`<div class="flow"><span class="flow-icon">↳</span><span>Continues to ${flowLink(children[0])}</span></div>`);else if(children.length>1)rows.push(`<div class="flow"><span class="flow-icon">⑂</span><span>Branches to ${children.slice(0,2).map(flowLink).join(', ')}${children.length>2?` +${children.length-2} more`:''}</span></div>`);else rows.push('<div class="flow"><span class="flow-icon">◆</span><span>End of this quest path</span></div>');return rows.join('')}function render(){document.querySelectorAll('[data-kind]').forEach(b=>b.classList.toggle('active',b.dataset.kind===kind));const term=q('#search').value.toLowerCase(),values=treeOrderedValues().filter(x=>(x.title||x.name||'').toLowerCase().includes(term)||x.id.includes(term));q('#list').innerHTML=values.map(x=>`<div class="item ${x.id===selected?'active':''}"><button class="item-main" data-id="${esc(x.id)}" style="padding-left:${kind==='quests'?5+treeDepth(x)*13:5}px">${kind==='quests'?treeMark(x):''}<span class="dot ${kind==='quests'&&!x.enabled?'off':''}">●</span>${esc(x.title||x.name)}<small>${esc(x.id)}${kind==='quests'?` · depth ${questDepth(x)} · level ${questLevel(x)}`:''}</small></button>${kind==='quests'?questFlow(x):''}</div>`).join('');q('#list').querySelectorAll('[data-id]').forEach(b=>b.onclick=()=>{selected=b.dataset.id;render()});q('#list').querySelectorAll('[data-open-quest]').forEach(b=>b.onclick=e=>{e.stopPropagation();kind='quests';selected=b.dataset.openQuest;render()});renderForm()}
        function field(label,name,value,type='text',wide=''){return `<label class="${wide}">${label}<input name="${name}" type="${type}" value="${esc(value)}"></label>`}function area(label,name,value,wide='wide'){return `<label class="${wide}">${label}<textarea name="${name}">${esc(value)}</textarea></label>`}
        function renderForm(){const x=data[kind].find(v=>v.id===selected);if(!x){q('#form').innerHTML='<div class="empty">Nothing selected</div>';return}if(kind==='quests'){renderQuest(x);addCharacterLinks(x);addQuestRules(x)}else{renderNpc(x);addNpcRules(x)}bindForm(x)}
        function addCharacterLinks(x){for(const [field,id] of [['startNpcProfile',x.startNpcProfile],['endNpcProfile',x.endNpcProfile]]){const select=q(`[name="${field}"]`);if(!select)continue;select.onchange=()=>setTimeout(renderForm,0);if(id)select.insertAdjacentHTML('afterend',`<button type="button" data-open-npc="${esc(id)}" class="small">Go to NPC ↗</button>`)}}
        function addQuestRules(x){q('#form .card').insertAdjacentHTML('beforeend',`<h3 style="margin-top:18px">Availability and failure</h3><div class="grid three">${field('Time limit in seconds (0 = none)','timeLimitSeconds',x.timeLimitSeconds||0,'number')}${field('Restart cooldown in seconds','restartCooldownSeconds',x.restartCooldownSeconds||0,'number')}${field('Global completion limit (0 = unlimited)','globalMaxCompletions',x.globalMaxCompletions||0,'number')}</div>`)}function addNpcRules(x){const cards=q('#form').querySelectorAll('.card');if(cards[2])cards[2].insertAdjacentHTML('beforeend',`<div class="grid">${field('Maximum appearance in seconds (0 = unlimited)','lifetimeSeconds',x.lifetimeSeconds||0,'number')}</div>`)}
        function renderQuest(x){const profiles=data.npcs.map(n=>n.id),quests=data.quests.filter(v=>v.id!==x.id).map(v=>v.id);q('#form').innerHTML=`<div class="head"><h2>${esc(x.title)}</h2><button id="delete" class="danger">Delete</button></div><section class="card"><h3>Story</h3><div class="grid">${field('Quest ID','id',x.id)}${field('Title','title',x.title)}${field('Author / giver','author',x.author)}<div class="checks"><label><input name="enabled" type="checkbox" ${x.enabled?'checked':''}>Enabled</label><label><input name="repeatable" type="checkbox" ${x.repeatable?'checked':''}>Repeatable</label></div>${area('Quest-giver story','description',x.description)}</div></section><section class="card"><h3>Characters</h3><div class="grid"><label>Starting NPC<select name="startNpcProfile">${options(profiles,x.startNpcProfile)}</select></label><label>Turn-in NPC<select name="endNpcProfile">${options(profiles,x.endNpcProfile)}</select></label>${field('Starting NPC display name','startNpcName',x.startNpcName)}${field('Turn-in NPC display name','endNpcName',x.endNpcName)}</div></section><section class="card"><h3>Requirements</h3><label>Required quests<select name="requirements" multiple size="${Math.min(8,Math.max(3,quests.length))}">${quests.map(v=>`<option value="${esc(v)}" ${x.requirements.includes(v)?'selected':''}>${esc(v)}</option>`).join('')}</select></label></section><section class="card"><h3>Objectives</h3><div id="objectives">${x.objectives.map(objectiveRow).join('')}</div><button data-add="objective">+ Objective</button></section><section class="card"><h3>Rewards</h3>${area('Reward description','rewardText',x.rewardText,'')}<div id="rewards">${x.rewardItems.map(rewardRow).join('')}</div><button data-add="reward">+ Item reward</button>${area('Console commands — one per line','rewardCommands',x.rewardCommands.join('\\n'))}</section><section class="card"><h3>Dialogue</h3><div class="grid">${['offer','idle','incomplete','objective','complete'].map(s=>area(s[0].toUpperCase()+s.slice(1)+' — one saying per line','dialogue.'+s,(x.dialogue[s]||[]).join('\\n'),s==='complete'?'wide':'')).join('')}</div></section>`}
        function objectiveTarget(o){if(o.type==='COLLECT')return `<label>Material<select data-prop="target">${options(data.materials,o.target,false)}</select></label>`;if(['KILL','INTERACT'].includes(o.type))return `<label>Entity type<select data-prop="target">${options(data.entityTypes,o.target,false)}</select></label>`;if(o.type==='NPC')return `<label>NPC profile<select data-prop="target">${options(data.npcs.map(n=>'profile:'+n.id),o.target,false)}</select></label>`;if(o.type==='STRUCTURE')return `<label>Structure<select data-prop="target">${options(['SHIPWRECK','SHIPWRECK_BEACHED','PILLAGER_OUTPOST','OCEAN_RUIN_COLD','OCEAN_RUIN_WARM','RUINED_PORTAL','MINESHAFT','BURIED_TREASURE'],o.target,false)}</select></label>`;if(['ALTITUDE_ABOVE','ALTITUDE_BELOW','UNDERWATER'].includes(o.type))return fieldObj('Y level','target',o.target,'number');if(o.type==='BIOME')return fieldObj('Biome key','target',o.target);return '<label>Target<input value="Automatic" disabled></label>'}function objectiveRow(o,i){return `<div class="row" data-objective="${i}"><div class="row-head"><strong>Objective ${i+1}</strong><button data-up="objective" class="small">↑</button><button data-down="objective" class="small">↓</button><button data-remove="objective" class="danger small">Remove</button></div><div class="grid three"><label>Type<select data-prop="type">${options(['COLLECT','KILL','LOCATION','NPC','BIOME','ALTITUDE_ABOVE','ALTITUDE_BELOW','STRUCTURE','SLEEP','INTERACT','UNDERWATER','NIGHT'],o.type,false)}</select></label>${objectiveTarget(o)}${fieldObj('Amount','amount',o.amount,'number')}${fieldObj('Player-facing objective','label',o.label)}<label>Consume collected items<input data-prop="consume" type="checkbox" ${o.consume?'checked':''}></label>${fieldObj('World','world',o.world||'survival')}${fieldObj('X','x',o.x,'number')}${fieldObj('Y','y',o.y,'number')}${fieldObj('Z','z',o.z,'number')}${fieldObj('Radius','radius',o.radius,'number')}</div></div>`}function fieldObj(label,prop,value,type='text'){return `<label>${label}<input data-prop="${prop}" type="${type}" value="${esc(value)}"></label>`}
        function rewardRow(r,i){return `<div class="row" data-reward="${i}"><div class="row-head"><strong>Item ${i+1}</strong><button data-remove="reward" class="danger small">Remove</button></div><div class="grid"><label>Material<select data-prop="material">${options(data.materials,r.material,false)}</select></label>${fieldObj('Amount','amount',r.amount,'number')}</div></div>`}
        function questLinks(values,empty){return values.length?values.map(v=>`<button data-quest-id="${esc(v.id)}">${esc(v.title)} <small>(${esc(v.id)})</small></button>`).join(' '):`<span class="status">${empty}</span>`}function renderNpc(x){const starts=data.quests.filter(v=>v.startNpcProfile===x.id),ends=data.quests.filter(v=>v.endNpcProfile===x.id);q('#form').innerHTML=`<div class="head"><h2>${esc(x.name)}</h2><button id="delete" class="danger">Delete</button></div><section class="card"><h3>Identity</h3><div class="grid">${field('NPC ID','id',x.id)}${field('Visible name','name',x.name)}<label>Entity type<select name="entityType">${options(data.entityTypes,x.entityType,false)}</select></label><div class="checks"><label><input name="ai" type="checkbox" ${x.ai?'checked':''}>Can walk around</label></div></div></section><section class="card"><h3>Linked quests</h3><div class="grid"><div><label>Quests started here</label><div class="row">${questLinks(starts,'No quests start at this NPC.')}</div></div><div><label>Quests handed in here</label><div class="row">${questLinks(ends,'No quests end at this NPC.')}</div></div></div></section><section class="card"><h3>Spawn conditions</h3><div class="grid three">${field('World','world',x.world)}${field('Minimum player level','minimumLevel',x.minimumLevel,'number')}${field('Daily chance (0–1)','dailyChance',x.dailyChance,'number')}${field('Minimum distance','minDistance',x.minDistance,'number')}${field('Maximum distance','maxDistance',x.maxDistance,'number')}${field('Uniqueness radius','uniquenessRadius',x.uniquenessRadius,'number')}${field('Despawn radius','despawnRadius',x.despawnRadius,'number')}${field('Time from (ticks)','timeFrom',x.timeFrom,'number')}${field('Time until (ticks)','timeUntil',x.timeUntil,'number')}${area('Biomes — one per line','biomes',x.biomes.join('\\n'))}</div></section><section class="card"><h3>Dialogue</h3>${area('Idle sayings — one per line','idleDialogue',x.idleDialogue.join('\\n'),'')}</section>`}
        function defaultObjectiveTarget(type){return {COLLECT:'OAK_LOG',KILL:'ZOMBIE',NPC:data.npcs[0]?'profile:'+data.npcs[0].id:'',BIOME:'plains',ALTITUDE_ABOVE:'120',ALTITUDE_BELOW:'0',UNDERWATER:'50',STRUCTURE:'SHIPWRECK',INTERACT:'IRON_GOLEM'}[type]||''}
        function bindForm(x){q('#delete').onclick=()=>{if(confirm(`Delete ${x.id}?`)){data[kind]=data[kind].filter(v=>v!==x);selected=data[kind][0]?.id||null;mark();render()}};q('#form').oninput=e=>{const el=e.target;if(el.name){if(el.name==='requirements')x.requirements=[...el.selectedOptions].map(o=>o.value);else if(el.name.startsWith('dialogue.'))x.dialogue[el.name.slice(9)]=lines(el.value);else if(['rewardCommands','biomes','idleDialogue'].includes(el.name))x[el.name]=lines(el.value);else x[el.name]=el.type==='checkbox'?el.checked:el.type==='number'?Number(el.value):el.value;mark()}else if(el.dataset.prop){const row=el.closest('[data-objective],[data-reward]'),arr=row.hasAttribute('data-objective')?x.objectives:x.rewardItems,index=Number(row.dataset.objective??row.dataset.reward);arr[index][el.dataset.prop]=el.type==='checkbox'?el.checked:el.type==='number'?Number(el.value):el.value;mark();if(el.dataset.prop==='type'){arr[index].target=defaultObjectiveTarget(el.value);renderForm()}}};q('#form').onclick=e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.questId){kind='quests';selected=b.dataset.questId;render();return}if(b.dataset.openNpc){kind='npcs';selected=b.dataset.openNpc;render();return}if(b.dataset.add==='objective')x.objectives.push({type:'COLLECT',target:'OAK_LOG',amount:1,consume:false,label:'Collect an item',world:'survival',x:0,y:64,z:0,radius:2});if(b.dataset.add==='reward')x.rewardItems.push({material:'EMERALD',amount:1});if(b.dataset.remove){const row=b.closest('[data-objective],[data-reward]'),arr=b.dataset.remove==='objective'?x.objectives:x.rewardItems;arr.splice(Number(row.dataset.objective??row.dataset.reward),1)}if(b.dataset.up||b.dataset.down){const row=b.closest('[data-objective]'),i=Number(row.dataset.objective),j=i+(b.dataset.up?-1:1);if(j>=0&&j<x.objectives.length)[x.objectives[i],x.objectives[j]]=[x.objectives[j],x.objectives[i]]}if(b.dataset.add||b.dataset.remove||b.dataset.up||b.dataset.down){mark();renderForm()}}}
        document.querySelectorAll('[data-kind]').forEach(b=>b.onclick=()=>{kind=b.dataset.kind;selected=data[kind][0]?.id||null;render()});q('#search').oninput=render;q('#new').onclick=()=>{const id=prompt('New '+(kind==='quests'?'quest':'NPC')+' ID (lowercase):');if(!id)return;if(data[kind].some(x=>x.id===id))return alert('That ID already exists');const x=kind==='quests'?{id,title:'New Quest',author:'Quest Giver',description:'Tell this quest as a story.',rewardText:'A reward awaits.',enabled:false,repeatable:false,startNpcProfile:null,endNpcProfile:null,startNpcName:'Quest Giver',endNpcName:'Quest Contact',dialogue:{offer:[],idle:[],incomplete:[],objective:[],complete:[]},requirements:[],objectives:[],rewardItems:[],rewardCommands:[]}:{id,name:'New NPC',entityType:'VILLAGER',ai:true,world:'survival',minDistance:30,maxDistance:100,uniquenessRadius:1200,despawnRadius:150,minimumLevel:0,timeFrom:0,timeUntil:24000,dailyChance:.25,biomes:[],idleDialogue:[]};data[kind].push(x);selected=id;mark();render()};q('#duplicate').onclick=()=>{const source=data[kind].find(x=>x.id===selected);if(!source)return;const id=prompt('ID for duplicate:',source.id+'-copy');if(!id||data[kind].some(x=>x.id===id))return;const copy=structuredClone(source);copy.id=id;if(kind==='quests')copy.title+=' Copy';else copy.name+=' Copy';data[kind].push(copy);selected=id;mark();render()};
        q('#save').onclick=async()=>{status('Saving…');try{const r=await fetch(dataUrl,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),t=await r.text();if(!r.ok)throw Error(t);dirty=false;status(t);await loadData()}catch(e){status(e.message,true)}};q('#discard').onclick=()=>{if(!dirty||confirm('Discard all unsaved changes?'))loadData().catch(e=>status(e.message,true))};window.onbeforeunload=e=>{if(dirty)e.preventDefault()};
        const showAdvanced=async()=>{q('#structuredView').style.display='none';q('#advancedView').style.display='block';await loadYaml()};q('#advanced').onclick=()=>showAdvanced().catch(e=>status(e.message,true));q('#structured').onclick=()=>{q('#advancedView').style.display='none';q('#structuredView').style.display='grid'};const yamlEndpoint=()=>apiUrl+'?file='+q('#yamlFile').value;async function loadYaml(){status('Loading YAML…');const r=await fetch(yamlEndpoint(),{cache:'no-store'});if(!r.ok)throw Error(await r.text());q('#yaml').value=await r.text();status('Ready')}q('#yamlReload').onclick=()=>loadYaml().catch(e=>status(e.message,true));q('#yamlFile').onchange=q('#yamlReload').onclick;q('#yamlSave').onclick=async()=>{const r=await fetch(yamlEndpoint(),{method:'PUT',headers:{'Content-Type':'text/yaml'},body:q('#yaml').value}),t=await r.text();if(!r.ok)return status(t,true);status(t);await loadData()};
        loadData().catch(e=>status(e.message,true));
        </script></body></html>
        """;

    private void cancelCommand(CommandContext ctx) {
        if (!ctx.isPlayer()) { ctx.error("This command is player-only."); return; }
        QuestDefinition quest = quest(ctx.getArg(1), ctx);
        if (quest == null) return;
        if (progress(ctx.asPlayer(), quest.id()) == null) { ctx.error("That quest is not active."); return; }
        if (cancelQuest(ctx.asPlayer().getUniqueId(), quest.id())) {
            removeQuestBooks(ctx.asPlayer(), quest.id());
            questMessage(ctx.asPlayer(), "abandoned", "<yellow>Quest {quest} abandoned.</yellow>",
                "quest", renderQuestText(quest, quest.title()));
            refreshMarkers(ctx.asPlayer());
        } else ctx.error("That quest is not active.");
    }

    private void cancelAllCommand(CommandContext ctx) {
        if (!ctx.isPlayer()) { ctx.error("This command is player-only."); return; }
        Player player = ctx.asPlayer();
        List<String> questIds = new ArrayList<>(activeFor(player).keySet());
        if (questIds.isEmpty()) { ctx.error("You do not have any active quests to abandon."); return; }
        if (!"confirm".equalsIgnoreCase(ctx.getArg(1, ""))) {
            questMessage(player, "abandon-all-warning", "<yellow>This will abandon all {count} active quests.</yellow>",
                "count", questIds.size());
            player.sendMessage(Component.text("Quest progress will be lost. Confirm with /quest abandon-all confirm ", NamedTextColor.RED)
                .append(button("[Abandon all quests]", "/quest abandon-all confirm", "Confirm abandoning every active quest")));
            return;
        }
        int abandoned = 0;
        for (String questId : questIds) {
            if (!cancelQuest(player.getUniqueId(), questId)) continue;
            removeQuestBooks(player, questId);
            abandoned++;
        }
        refreshMarkers(player);
        questMessage(player, abandoned == 1 ? "abandoned-all-one" : "abandoned-all-many",
            abandoned == 1 ? "<yellow>Abandoned {count} active quest.</yellow>" : "<yellow>Abandoned {count} active quests.</yellow>",
            "count", abandoned);
    }

    private void trackCommand(CommandContext ctx) {
        if (!ctx.isPlayer()) { ctx.error("This command is player-only."); return; }
        Player player = ctx.asPlayer();
        String requested = ctx.getArg(1);
        if (requested == null) {
            String tracked = trackedQuests.get(player.getUniqueId());
            if (isAutoTracking(player.getUniqueId())) {
                if (tracked == null) questMessage(player, "tracking-auto-empty", "<yellow>Quest tracking mode: automatic. No quest is currently active.</yellow>");
                else {
                    QuestDefinition quest = quests.get(tracked);
                    questMessage(player, "tracking-auto", "<yellow>Quest tracking mode: automatic. Tracking {quest}.</yellow>",
                        "quest", quest == null ? tracked : renderQuestText(quest, quest.title()));
                }
            } else if (tracked != null) {
                QuestDefinition quest = quests.get(tracked);
                questMessage(player, "tracking-specific", "<yellow>Quest tracking mode: specific. Tracking {quest}.</yellow>",
                    "quest", quest == null ? tracked : renderQuestText(quest, quest.title()));
            } else questMessage(player, "tracking-off-status", "<yellow>Quest tracking mode: off.</yellow>");
            return;
        }
        if ("auto".equalsIgnoreCase(requested)) {
            setAutoTracking(player.getUniqueId(), true);
            updateAutomaticTracking(player);
            questMessage(player, "tracking-auto-enabled", "<yellow>Quest automatic tracking enabled.</yellow>");
            return;
        }
        if ("off".equalsIgnoreCase(requested)) {
            setAutoTracking(player.getUniqueId(), false);
            stopTracking(player.getUniqueId());
            questMessage(player, "tracking-off", "<yellow>Quest tracking turned off.</yellow>");
            return;
        }
        QuestDefinition quest = quest(requested, ctx);
        if (quest == null) return;
        if (progress(player, quest.id()) == null) { ctx.error("That quest is not active."); return; }
        if (!hasOwnedQuestBook(player, quest.id())) { ctx.error("Carry that quest book in your inventory to track it."); return; }
        setAutoTracking(player.getUniqueId(), false);
        trackQuest(player, quest.id());
        questMessage(player, "tracking-quest", "<yellow>Tracking quest {quest}.</yellow>",
            "quest", renderQuestText(quest, quest.title()));
    }

    private void setAutoTracking(UUID playerId, boolean enabled) {
        trackingPreferencePlayers.add(playerId);
        if (enabled) autoTrackPlayers.add(playerId);
        else {
            autoTrackPlayers.remove(playerId);
            autoTrackActivityQuests.remove(playerId);
            autoTrackActivityUntil.remove(playerId);
        }
        api.database().update("INSERT OR REPLACE INTO quest_tracking_preferences(player_uuid,auto_enabled) VALUES(?,?)", statement -> {
            statement.setString(1, playerId.toString()); statement.setInt(2, enabled ? 1 : 0);
        });
    }

    private boolean isAutoTracking(UUID playerId) {
        return autoTrackingEnabled(trackingPreferencePlayers.contains(playerId), autoTrackPlayers.contains(playerId));
    }

    static boolean autoTrackingEnabled(boolean preferenceExists, boolean preferenceEnabled) {
        return !preferenceExists || preferenceEnabled;
    }

    private void trackQuest(Player player, String questId) {
        if (questId.equals(trackedQuests.get(player.getUniqueId()))) {
            updateQuestTracking(player);
            return;
        }
        trackedQuests.put(player.getUniqueId(), questId);
        api.database().update("INSERT OR REPLACE INTO quest_tracking(player_uuid,quest_id) VALUES(?,?)", statement -> {
            statement.setString(1, player.getUniqueId().toString()); statement.setString(2, questId);
        });
        updateQuestTracking(player);
    }

    private void trackMostRecentQuest(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) { stopTracking(playerId); return; }
        String questId = active.getOrDefault(playerId, Map.of()).keySet().stream()
            .filter(id -> hasOwnedQuestBook(player, id))
            .max(Comparator.comparingLong(id -> attemptStarted.getOrDefault(playerId, Map.of()).getOrDefault(id, 0L)))
            .orElse(null);
        if (questId == null) {
            stopTracking(playerId);
            return;
        }
        trackQuest(player, questId);
    }

    private void stopTracking(UUID playerId) {
        trackedQuests.remove(playerId);
        api.database().update("DELETE FROM quest_tracking WHERE player_uuid=?", statement -> statement.setString(1, playerId.toString()));
        BossBar bar = trackingBossBars.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (bar != null && player != null) player.hideBossBar(bar);
    }

    private void adminCommand(CommandContext ctx) {
        if (!ctx.hasPermission(PERMISSION_ADMIN)) { ctx.error("COMMAND_NO_PERMISSION"); return; }
        String action = ctx.getArgLower(1);
        if (action == null) { showAdminMenu(ctx); return; }
        switch (action) {
            case "create" -> adminCreate(ctx);
            case "edit" -> adminEdit(ctx);
            case "delete" -> adminDelete(ctx);
            case "npcs" -> showNpcProfiles(ctx);
            case "npc" -> adminNpc(ctx);
            case "npc-spawned" -> showSpawnedNpcs(ctx);
            case "player" -> showPlayerQuestState(ctx);
            case "editor" -> openWebEditor(ctx);
            case "reload" -> { reloadDefinitions(); registerMarkers(); ctx.success("Quest definitions reloaded."); }
            default -> showAdminMenu(ctx);
        }
    }

    private void showAdminMenu(CommandContext ctx) {
        List<QuestDefinition> values = new ArrayList<>(quests.values());
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 1, 1);
        ChatMenuUtil.render(ctx.getSender(), Component.text("Quest administration", NamedTextColor.AQUA),
            "quest admin", page, values.size(), (start, count, interactive) -> {
                List<Component> lines = new ArrayList<>();
                for (QuestDefinition quest : values.subList(start, start + count)) {
                    Component state = Component.text(quest.enabled() ? "● " : "○ ", quest.enabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY);
                    Component line = state.append(Component.text(quest.title(), NamedTextColor.WHITE))
                        .append(Component.text(" (" + quest.id() + ")", NamedTextColor.DARK_GRAY));
                    if (interactive) line = line.clickEvent(ClickEvent.runCommand("/quest admin edit " + quest.id()))
                        .hoverEvent(HoverEvent.showText(Component.text("Open quest editor")));
                    lines.add(line);
                }
                return lines;
            }, "No quests are configured.");
        ctx.getSender().sendMessage(button("[＋ Create]", "/quest admin create new-quest New Quest", "Create a quest")
            .append(Component.space()).append(button("[NPC profiles]", "/quest admin npcs", "Manage conditional NPCs"))
            .append(Component.space()).append(button("[Spawned NPCs]", "/quest admin npc-spawned", "Inspect spawned quest NPCs"))
            .append(Component.space()).append(button("[Web editor]", "/quest admin editor", "Create a private editor link"))
            .append(Component.space()).append(button("[↻ Reload]", "/quest admin reload", "Reload quests.yml")));
    }

    private void adminCreate(CommandContext ctx) {
        if (ctx.numArgs() < 4) { ctx.error("Usage: /quest admin create <id> <title...>"); return; }
        String id = normalizeId(ctx.getArg(2));
        if (id.isBlank() || quests.containsKey(id)) { ctx.error("That quest ID is invalid or already exists."); return; }
        QuestDefinition quest = new QuestDefinition(id, join(ctx.args(), 3));
        quests.put(id, quest);
        saveDefinitions();
        ctx.success("Created quest {quest}.", "quest", id);
        showQuest(ctx, id);
    }

    private void adminEdit(CommandContext ctx) {
        QuestDefinition quest = quest(ctx.getArg(2), ctx);
        if (quest == null) return;
        String field = ctx.getArgLower(3);
        if (field == null) { showEditMenu(ctx, quest); return; }
        boolean changed = switch (field) {
            case "title" -> setText(ctx, quest::title, 4);
            case "author" -> setText(ctx, quest::author, 4);
            case "description" -> setText(ctx, quest::description, 4);
            case "shortdescription" -> setText(ctx, quest::shortDescription, 4);
            case "rewardtext" -> setText(ctx, quest::rewardText, 4);
            case "startname" -> setNpcName(ctx, quest, true);
            case "endname" -> setNpcName(ctx, quest, false);
            case "startprofile" -> setProfile(ctx, quest, true);
            case "endprofile" -> setProfile(ctx, quest, false);
            case "enabled" -> setBoolean(ctx, quest::enabled, 4);
            case "repeatable" -> setBoolean(ctx, quest::repeatable, 4);
            case "timelimit" -> setNonNegativeLong(ctx, quest::timeLimitSeconds, 4);
            case "cooldown" -> setNonNegativeLong(ctx, quest::restartCooldownSeconds, 4);
            case "globalmax" -> setNonNegativeInt(ctx, quest::globalMaxCompletions, 4);
            case "startnpc" -> bindNpc(ctx, quest, true);
            case "endnpc" -> bindNpc(ctx, quest, false);
            case "require" -> editRequirement(ctx, quest);
            case "reward" -> editReward(ctx, quest);
            case "rewarditem" -> editRewardItem(ctx, quest);
            case "objective" -> editObjective(ctx, quest);
            case "spawnnpc" -> spawnNpc(ctx, quest);
            case "dialogue" -> editDialogue(ctx, quest);
            default -> false;
        };
        if (!changed) {
            ctx.error("Unknown or invalid edit. Open /quest admin edit {quest} for examples.", "quest", quest.id());
            return;
        }
        saveDefinitions();
        registerMarkers();
        ctx.success("Quest {quest} updated.", "quest", quest.id());
        showEditMenu(ctx, quest);
    }

    private void showEditMenu(CommandContext ctx, QuestDefinition quest) {
        String base = "/quest admin edit " + quest.id() + " ";
        ctx.getSender().sendMessage(Component.text("──────── Quest Editor ────────", NamedTextColor.AQUA));
        ctx.getSender().sendMessage(Component.text(quest.title(), NamedTextColor.GOLD)
            .append(Component.text("  " + quest.id(), NamedTextColor.DARK_GRAY)));
        ctx.getSender().sendMessage(editField("Title", quest.title(), base + "title "));
        ctx.getSender().sendMessage(editField("Author", quest.author(), base + "author "));
        ctx.getSender().sendMessage(editField("Description", quest.description(), base + "description "));
        ctx.getSender().sendMessage(editField("Tooltip summary", quest.shortDescription(), base + "shortdescription "));
        ctx.getSender().sendMessage(editField("Reward shown", quest.rewardText(), base + "rewardtext "));
        ctx.getSender().sendMessage(editField("Start NPC name", quest.startNpcName(), base + "startname "));
        ctx.getSender().sendMessage(editField("End NPC name", quest.endNpcName(), base + "endname "));
        ctx.getSender().sendMessage(Component.text("Enabled: ", NamedTextColor.GRAY)
            .append(toggle(quest.enabled(), base + "enabled " + !quest.enabled()))
            .append(Component.text("   Repeatable: ", NamedTextColor.GRAY))
            .append(toggle(quest.repeatable(), base + "repeatable " + !quest.repeatable())));
        ctx.getSender().sendMessage(suggestButton("[time limit " + quest.timeLimitSeconds() + "s]", base + "timelimit ", "Seconds; 0 disables")
            .append(Component.space()).append(suggestButton("[restart cooldown " + quest.restartCooldownSeconds() + "s]", base + "cooldown ", "Seconds after completion or failure"))
            .append(Component.space()).append(suggestButton("[global max " + quest.globalMaxCompletions() + "]", base + "globalmax ", "0 means unlimited")));

        ctx.getSender().sendMessage(Component.text("NPCs  ", NamedTextColor.YELLOW)
            .append(button("[Bind start]", base + "startnpc", "Look at a living entity first"))
            .append(Component.space()).append(button("[Bind end]", base + "endnpc", "The ! appears here when ready"))
            .append(Component.space()).append(button("[Spawn start]", base + "spawnnpc start 0", "Spawn and bind a fixed villager"))
            .append(Component.space()).append(button("[Spawn end]", base + "spawnnpc end 0", "Spawn and bind a fixed villager")));
        ctx.getSender().sendMessage(suggestButton("[Link start profile]", base + "startprofile ", "Enter an NPC profile ID")
            .append(Component.space()).append(suggestButton("[Link end profile]", base + "endprofile ", "Enter an NPC profile ID")));

        ctx.getSender().sendMessage(Component.text("Objectives", NamedTextColor.YELLOW));
        if (quest.objectives().isEmpty()) ctx.getSender().sendMessage(Component.text("  None yet", NamedTextColor.GRAY));
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestObjective objective = quest.objectives().get(i);
            ctx.getSender().sendMessage(Component.text("  " + (i + 1) + ". " + objective.label(), NamedTextColor.WHITE)
                .append(Component.space()).append(button("[remove]", base + "objective remove " + (i + 1), "Remove objective")));
        }
        ctx.getSender().sendMessage(suggestButton("[＋ Collect]", base + "objective add collect OAK_LOG 8 consume Gather 8 oak logs", "Add item objective")
            .append(Component.space()).append(suggestButton("[＋ Kill]", base + "objective add kill ZOMBIE 3 Defeat 3 zombies", "Add kill objective"))
            .append(Component.space()).append(suggestButton("[＋ Location]", base + "objective add location 8 Visit this location", "Uses your current location"))
            .append(Component.space()).append(suggestButton("[＋ NPC]", base + "objective add npc Speak to this NPC", "Look at the NPC before sending")));

        ctx.getSender().sendMessage(Component.text("Prerequisites", NamedTextColor.YELLOW));
        if (quest.requirements().isEmpty()) ctx.getSender().sendMessage(Component.text("  None", NamedTextColor.GRAY));
        for (String requirement : quest.requirements()) ctx.getSender().sendMessage(Component.text("  " + requirement, NamedTextColor.WHITE)
            .append(Component.space()).append(button("[remove]", base + "require remove " + requirement, "Remove prerequisite")));
        ctx.getSender().sendMessage(suggestButton("[＋ prerequisite]", base + "require add ", "Add required quest ID"));

        ctx.getSender().sendMessage(Component.text("Reward commands", NamedTextColor.YELLOW));
        if (quest.rewardCommands().isEmpty()) ctx.getSender().sendMessage(Component.text("  None", NamedTextColor.GRAY));
        for (int i = 0; i < quest.rewardCommands().size(); i++) ctx.getSender().sendMessage(Component.text("  /" + quest.rewardCommands().get(i), NamedTextColor.WHITE)
            .append(Component.space()).append(button("[remove]", base + "reward remove " + (i + 1), "Remove reward command")));
        ctx.getSender().sendMessage(suggestButton("[＋ reward command]", base + "reward add give {player} emerald 1", "Command runs as console"));
        ctx.getSender().sendMessage(Component.text("Reward items", NamedTextColor.YELLOW));
        if (quest.rewardItems().isEmpty()) ctx.getSender().sendMessage(Component.text("  None", NamedTextColor.GRAY));
        for (int i = 0; i < quest.rewardItems().size(); i++) {
            QuestRewardItem item = quest.rewardItems().get(i);
            ctx.getSender().sendMessage(Component.text("  " + item.amount() + " × " + item.material().name().toLowerCase(Locale.ROOT).replace('_', ' '), NamedTextColor.WHITE)
                .append(Component.space()).append(button("[remove]", base + "rewarditem remove " + (i + 1), "Remove reward item")));
        }
        ctx.getSender().sendMessage(suggestButton("[＋ reward item]", base + "rewarditem add EMERALD 1", "Safely delivered; overflow goes to mail"));
        ctx.getSender().sendMessage(Component.text("NPC dialogue", NamedTextColor.YELLOW));
        for (String state : List.of("offer", "idle", "incomplete", "objective", "complete")) {
            List<String> lines = quest.dialogue(state);
            ctx.getSender().sendMessage(Component.text("  " + state + ": ", NamedTextColor.GRAY)
                .append(Component.text(lines.isEmpty() ? "None" : lines.size() + " line(s)", NamedTextColor.WHITE))
                .append(Component.space()).append(suggestButton("[add]", base + "dialogue add " + state + " ", "Add a random " + state + " line")));
            for (int i = 0; i < lines.size(); i++) {
                ctx.getSender().sendMessage(Component.text("    “" + lines.get(i) + "”", NamedTextColor.DARK_GRAY)
                    .append(Component.space()).append(button("[remove]", base + "dialogue remove " + state + " " + (i + 1), "Remove line")));
            }
        }
        ctx.getSender().sendMessage(button("[← All quests]", "/quest admin", "Back to quest list")
            .append(Component.space()).append(button("[Delete]", "/quest admin delete " + quest.id() + " confirm", "Delete permanently")));
    }

    private Component editField(String label, String value, String command) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
            .append(Component.text(value, NamedTextColor.WHITE))
            .append(Component.space()).append(suggestButton("[edit]", command, "Click, edit the command, then send"));
    }

    private Component toggle(boolean enabled, String command) {
        return button(enabled ? "[ON]" : "[OFF]", command, "Click to toggle")
            .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private Component suggestButton(String text, String command, String hover) {
        return Component.text(text, NamedTextColor.GOLD).clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private boolean setText(CommandContext ctx, java.util.function.Consumer<String> setter, int index) {
        if (ctx.numArgs() <= index) return false;
        setter.accept(join(ctx.args(), index));
        return true;
    }

    private boolean setBoolean(CommandContext ctx, java.util.function.Consumer<Boolean> setter, int index) {
        if (ctx.numArgs() <= index) return false;
        String value = ctx.getArgLower(index);
        if (!value.equals("true") && !value.equals("false")) return false;
        setter.accept(Boolean.parseBoolean(value));
        return true;
    }

    private boolean setNonNegativeLong(CommandContext ctx, java.util.function.LongConsumer setter, int index) {
        try { long value = Long.parseLong(ctx.getArg(index)); if (value < 0) return false; setter.accept(value); return true; }
        catch (RuntimeException ex) { return false; }
    }

    private boolean setNonNegativeInt(CommandContext ctx, java.util.function.IntConsumer setter, int index) {
        try { int value = Integer.parseInt(ctx.getArg(index)); if (value < 0) return false; setter.accept(value); return true; }
        catch (RuntimeException ex) { return false; }
    }

    private boolean setNpcName(CommandContext ctx, QuestDefinition quest, boolean start) {
        if (ctx.numArgs() <= 4) return false;
        String name = join(ctx.args(), 4);
        if (start) quest.startNpcName(name); else quest.endNpcName(name);
        UUID uuid = start ? quest.startNpc() : quest.endNpc();
        if (uuid != null && uuid.equals(quest.startNpc())) quest.startNpcName(name);
        if (uuid != null && uuid.equals(quest.endNpc())) quest.endNpcName(name);
        Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        if (entity != null) {
            entity.customName(Component.text(name));
            entity.setCustomNameVisible(true);
        }
        return true;
    }

    private boolean setProfile(CommandContext ctx, QuestDefinition quest, boolean start) {
        String id = ctx.getArgLower(4);
        if (id == null || (!"clear".equals(id) && !npcProfiles.containsKey(id))) return false;
        if (start) quest.startNpcProfile("clear".equals(id) ? null : id);
        else quest.endNpcProfile("clear".equals(id) ? null : id);
        return true;
    }

    private void showNpcProfiles(CommandContext ctx) {
        List<QuestNpcProfile> profiles = new ArrayList<>(npcProfiles.values());
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 2, 1);
        ChatMenuUtil.render(ctx.getSender(), "Quest NPC profiles", "quest admin npcs", page, profiles.size(), (start, count, interactive) -> {
            List<Component> lines = new ArrayList<>();
            for (QuestNpcProfile profile : profiles.subList(start, start + count)) {
                Entity entity = reconcileProfileEntities(profile);
                lines.add(Component.text((entity != null ? "● " : "○ ") + profile.name() + " (" + profile.id() + ")", NamedTextColor.WHITE)
                    .append(Component.space()).append(button("[Edit]", "/quest admin npc edit " + profile.id(), "Edit profile"))
                    .append(Component.space()).append(button(entity == null ? "[Spawn]" : "[Despawn]",
                        "/quest admin npc " + (entity == null ? "spawn " : "despawn ") + profile.id(), "Manage entity"))
                    .append(Component.space()).append(button("[Quests]", "/quest admin npc quests " + profile.id(), "View linked quests")));
            }
            return lines;
        }, "No NPC profiles are configured.");
        ctx.getSender().sendMessage(suggestButton("[＋ Create]", "/quest admin npc create new-npc New NPC", "Create profile"));
    }

    private void showNpcSpawnResult(CommandContext ctx, QuestNpcProfile profile, Entity entity, String message) {
        Location location = entity.getLocation();
        ctx.getSender().sendMessage(Component.text(message + " " + profile.name() + " at ", NamedTextColor.GREEN)
            .append(Component.text(location.getWorld().getName() + " " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ(), NamedTextColor.WHITE)));
        ctx.getSender().sendMessage(button("[Teleport]", "/quest admin npc teleport " + profile.id(), "Teleport to this NPC")
            .append(Component.space()).append(button("[Quests]", "/quest admin npc quests " + profile.id(), "View linked quests"))
            .append(Component.space()).append(button("[NPC profiles]", "/quest admin npcs", "Return to the NPC menu")));
    }

    private Location npcTeleportLocation(Entity entity) {
        Location npc = entity.getLocation();
        org.bukkit.util.Vector forward = npc.getDirection().setY(0);
        if (forward.lengthSquared() < 0.01D) forward.setZ(1);
        forward.normalize();
        for (double angle : new double[]{0D, Math.PI / 2D, -Math.PI / 2D, Math.PI}) {
            org.bukkit.util.Vector offset = forward.clone().rotateAroundY(angle).multiply(1.75D);
            Location candidate = npc.clone().add(offset);
            if (!candidate.getBlock().isPassable() || !candidate.clone().add(0, 1, 0).getBlock().isPassable()
                || !safeNpcGround(candidate.clone().add(0, -1, 0).getBlock().getType())) continue;
            candidate.setDirection(npc.toVector().subtract(candidate.toVector()));
            return candidate;
        }
        Location fallback = npc.clone().add(forward.multiply(1.25D));
        fallback.setDirection(npc.toVector().subtract(fallback.toVector()));
        return fallback;
    }

    private void showSpawnedNpcs(CommandContext ctx) {
        Integer radius = null;
        if (ctx.getArg(2) != null) {
            try {
                radius = Integer.parseInt(ctx.getArg(2));
                if (radius < 1) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                ctx.error("Usage: /quest admin npc-spawned [radius] [player]");
                return;
            }
        }
        Player reference = ctx.getArg(3) == null ? (ctx.isPlayer() ? ctx.asPlayer() : null) : Bukkit.getPlayerExact(ctx.getArg(3));
        if (ctx.getArg(3) != null && reference == null) { ctx.error("That player is not online."); return; }
        if (radius != null && reference == null) { ctx.error("A radius requires an online player when run from console."); return; }

        List<Map.Entry<QuestNpcProfile, Entity>> spawned = new ArrayList<>();
        for (QuestNpcProfile profile : npcProfiles.values()) {
            Entity entity = reconcileProfileEntities(profile);
            if (entity == null || !entity.isValid()) continue;
            if (radius != null && (!entity.getWorld().equals(reference.getWorld())
                || entity.getLocation().distanceSquared(reference.getLocation()) > (double) radius * radius)) continue;
            spawned.add(Map.entry(profile, entity));
        }
        spawned.sort(java.util.Comparator.comparing(entry -> entry.getKey().name(), String.CASE_INSENSITIVE_ORDER));
        String scope = radius == null ? "all worlds" : "within " + radius + " blocks of " + reference.getName();
        ctx.getSender().sendMessage(Component.text("Spawned quest NPCs — " + scope, NamedTextColor.AQUA));
        if (spawned.isEmpty()) { ctx.getSender().sendMessage(Component.text("No spawned quest NPCs matched.", NamedTextColor.GRAY)); return; }
        for (Map.Entry<QuestNpcProfile, Entity> entry : spawned) {
            QuestNpcProfile profile = entry.getKey();
            Location location = entry.getValue().getLocation();
            String targetSuffix = reference == null ? "" : " " + reference.getName();
            ctx.getSender().sendMessage(Component.text(profile.name() + " (" + profile.id() + ")", NamedTextColor.WHITE)
                .append(Component.text(" — " + location.getWorld().getName() + " " + location.getBlockX() + ", "
                    + location.getBlockY() + ", " + location.getBlockZ(), NamedTextColor.GRAY))
                .append(Component.space()).append(button("[Teleport]", "/quest admin npc teleport " + profile.id(), "Teleport to this NPC"))
                .append(Component.space()).append(button("[Quests]", "/quest admin npc quests " + profile.id() + targetSuffix,
                    "View every quest linked to this NPC")));
        }
    }

    private void showPlayerQuestState(CommandContext ctx) {
        Player player = ctx.getArg(2) == null ? (ctx.isPlayer() ? ctx.asPlayer() : null) : Bukkit.getPlayerExact(ctx.getArg(2));
        if (player == null) { ctx.error("Usage: /quest admin player <online-player>"); return; }
        Map<String, List<QuestDefinition>> groups = new LinkedHashMap<>();
        for (String state : List.of("ACTIVE", "READY", "AVAILABLE", "COOLDOWN", "COMPLETED", "LOCKED", "DISABLED"))
            groups.put(state, new ArrayList<>());
        for (QuestDefinition quest : quests.values()) groups.get(adminQuestState(player, quest)).add(quest);
        String filter = ctx.getArgUpper(3);
        if (filter == null) {
            ctx.getSender().sendMessage(Component.text("Quest state for " + player.getName(), NamedTextColor.AQUA));
            for (Map.Entry<String, List<QuestDefinition>> group : groups.entrySet()) {
                Component summary = Component.text(group.getKey() + ": " + group.getValue().size(),
                    group.getValue().isEmpty() ? NamedTextColor.GRAY : NamedTextColor.YELLOW);
                if (!group.getValue().isEmpty()) summary = summary.clickEvent(ClickEvent.runCommand("/quest admin player "
                    + player.getName() + " " + group.getKey().toLowerCase(Locale.ROOT)))
                    .hoverEvent(HoverEvent.showText(Component.text("View " + group.getKey().toLowerCase(Locale.ROOT) + " quests")));
                ctx.getSender().sendMessage(summary);
            }
            return;
        }
        List<QuestDefinition> selected = groups.get(filter);
        if (selected == null) { ctx.error("State must be active, ready, available, cooldown, completed, locked, or disabled."); return; }
        int page = ChatMenuUtil.getPageFromArgs(ctx.args(), 4, 1);
        ChatMenuUtil.render(ctx.getSender(), filter + " quests for " + player.getName(), "quest admin player "
            + player.getName() + " " + filter.toLowerCase(Locale.ROOT), page, selected.size(), (start, count, interactive) -> {
            List<Component> lines = new ArrayList<>();
            for (QuestDefinition quest : selected.subList(start, start + count)) {
                QuestProgress progress = progress(player, quest.id());
                String detail = progress == null ? "" : " — objective " + Math.min(progress.objectiveIndex() + 1, quest.objectives().size())
                    + "/" + quest.objectives().size();
                lines.add(Component.text(quest.title() + " (" + quest.id() + ")" + detail, NamedTextColor.WHITE)
                    .clickEvent(ClickEvent.runCommand("/quest admin edit " + quest.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Edit quest definition"))));
            }
            return lines;
        }, "No quests have that state.");
    }

    private String adminQuestState(Player player, QuestDefinition quest) {
        if (!quest.enabled()) return "DISABLED";
        QuestProgress progress = progress(player, quest.id());
        if (progress != null) return progress.state() == QuestProgress.State.READY ? "READY" : "ACTIVE";
        if (isAvailable(player, quest)) return "AVAILABLE";
        if (quest.restartCooldownSeconds() > 0 && cooldownActive(player, quest)) return "COOLDOWN";
        if (completed.getOrDefault(player.getUniqueId(), Set.of()).contains(quest.id())) return "COMPLETED";
        return "LOCKED";
    }

    private boolean cooldownActive(Player player, QuestDefinition quest) {
        long newest = 0;
        Long failedAt = api.database().querySingleMapped("SELECT failed_at FROM quest_failure WHERE player_uuid=? AND quest_id=?",
            statement -> { statement.setString(1, player.getUniqueId().toString()); statement.setString(2, quest.id()); },
            result -> result.getLong("failed_at"));
        Long completedAt = api.database().querySingleMapped("SELECT completed_at FROM quest_completed WHERE player_uuid=? AND quest_id=?",
            statement -> { statement.setString(1, player.getUniqueId().toString()); statement.setString(2, quest.id()); },
            result -> result.getLong("completed_at"));
        if (failedAt != null) newest = Math.max(newest, failedAt);
        if (completedAt != null) newest = Math.max(newest, completedAt);
        return newest > 0 && System.currentTimeMillis() - newest < quest.restartCooldownSeconds() * 1000L;
    }

    private void showNpcLinkedQuests(CommandContext ctx, QuestNpcProfile profile) {
        Player player = ctx.getArg(4) == null ? (ctx.isPlayer() ? ctx.asPlayer() : null) : Bukkit.getPlayerExact(ctx.getArg(4));
        if (ctx.getArg(4) != null && player == null) { ctx.error("That player is not online."); return; }
        List<QuestDefinition> linked = quests.values().stream().filter(quest -> profile.id().equals(quest.startNpcProfile())
            || profile.id().equals(quest.endNpcProfile()) || quest.objectives().stream().anyMatch(objective ->
                objective.type() == QuestObjective.Type.NPC && objective.target().equals("profile:" + profile.id()))).toList();
        ctx.getSender().sendMessage(Component.text("Quests linked to " + profile.name(), NamedTextColor.AQUA));
        if (linked.isEmpty()) { ctx.getSender().sendMessage(Component.text("No quests are linked.", NamedTextColor.GRAY)); return; }
        for (QuestDefinition quest : linked) {
            String state = player == null ? (quest.enabled() ? "ENABLED" : "DISABLED") : adminQuestState(player, quest);
            ctx.getSender().sendMessage(Component.text("[" + state + "] " + quest.title() + " (" + quest.id() + ")", NamedTextColor.WHITE)
                .clickEvent(ClickEvent.runCommand("/quest admin edit " + quest.id()))
                .hoverEvent(HoverEvent.showText(Component.text("Edit quest definition"))));
        }
    }

    private void adminNpc(CommandContext ctx) {
        String action = ctx.getArgLower(2);
        String id = normalizeId(ctx.getArg(3));
        if ("create".equals(action)) {
            if (id.isBlank() || npcProfiles.containsKey(id) || ctx.numArgs() < 5) { ctx.error("Usage: /quest admin npc create <id> <name...>"); return; }
            npcProfiles.put(id, new QuestNpcProfile(id, join(ctx.args(), 4))); saveNpcProfiles(); showNpcProfiles(ctx); return;
        }
        QuestNpcProfile profile = npcProfiles.get(id);
        if (profile == null) { ctx.error("NPC profile not found."); return; }
        switch (action) {
            case "edit" -> editNpcProfile(ctx, profile);
            case "teleport" -> {
                if (!ctx.isPlayer()) { ctx.error("Run this command in-game to teleport."); return; }
                Entity entity = reconcileProfileEntities(profile);
                if (entity == null || !entity.isValid()) { ctx.error("That NPC is not currently spawned."); return; }
                ctx.asPlayer().teleport(npcTeleportLocation(entity));
                showNpcSpawnResult(ctx, profile, entity, "Teleported to:");
            }
            case "quests" -> showNpcLinkedQuests(ctx, profile);
            case "spawn" -> {
                if (!ctx.isPlayer()) { ctx.error("Run this in-game."); return; }
                Entity existing = reconcileProfileEntities(profile);
                if (existing != null) {
                    showNpcSpawnResult(ctx, profile, existing, "Already spawned:");
                    return;
                }
                Location location = findNpcSpawn(profile, ctx.asPlayer());
                if (location == null) { ctx.error("No safe profile-compatible spawn location was found nearby."); return; }
                LivingEntity living = spawnProfileEntity(profile, location);
                if (living == null) { ctx.error("NPC type could not be spawned here."); return; }
                profile.spawnedEntity(living.getUniqueId()); profile.spawnedAt(System.currentTimeMillis()); saveNpcProfiles(); registerMarkers();
                showNpcSpawnResult(ctx, profile, living, "NPC spawned for testing:");
            }
            case "despawn" -> { removeProfileEntities(profile); saveNpcProfiles(); registerMarkers(); ctx.success("NPC despawned."); showNpcProfiles(ctx); }
            case "delete" -> { if (!"confirm".equalsIgnoreCase(ctx.getArg(4, ""))) { ctx.error("Add confirm to delete."); return; } removeProfileEntities(profile); npcProfiles.remove(id); saveNpcProfiles(); registerMarkers(); ctx.success("NPC profile deleted."); showNpcProfiles(ctx); }
            default -> ctx.error("Use create, edit, spawn, despawn, teleport, quests, or delete.");
        }
    }

    private void editNpcProfile(CommandContext ctx, QuestNpcProfile profile) {
        String field = ctx.getArgLower(4);
        if (field == null) { showNpcProfileEditor(ctx, profile); return; }
        try {
            switch (field) {
                case "name" -> profile.name(join(ctx.args(), 5));
                case "level" -> profile.minimumLevel(Integer.parseInt(ctx.getArg(5)));
                case "distance" -> { profile.minDistance(Integer.parseInt(ctx.getArg(5))); profile.maxDistance(Integer.parseInt(ctx.getArg(6))); }
                case "unique" -> profile.uniquenessRadius(Integer.parseInt(ctx.getArg(5)));
                case "despawn" -> profile.despawnRadius(Integer.parseInt(ctx.getArg(5)));
                case "time" -> { profile.timeFrom(Long.parseLong(ctx.getArg(5))); profile.timeUntil(Long.parseLong(ctx.getArg(6))); }
                case "chance" -> profile.dailyChance(Double.parseDouble(ctx.getArg(5)));
                case "lifetime" -> profile.lifetimeSeconds(Long.parseLong(ctx.getArg(5)));
                case "biome" -> { if ("clear".equalsIgnoreCase(ctx.getArg(5))) profile.biomes().clear(); else profile.biomes().add(ctx.getArgUpper(5)); }
                case "idle" -> profile.idleDialogue().add(join(ctx.args(), 5));
                default -> { ctx.error("Unknown NPC field."); return; }
            }
            saveNpcProfiles(); ctx.success("NPC profile updated."); showNpcProfileEditor(ctx, profile);
        } catch (RuntimeException ex) { ctx.error("Invalid value for that field."); }
    }

    private void showNpcProfileEditor(CommandContext ctx, QuestNpcProfile p) {
        String base = "/quest admin npc edit " + p.id() + " ";
        ctx.getSender().sendMessage(Component.text("NPC: " + p.name() + " (" + p.id() + ")", NamedTextColor.AQUA));
        ctx.getSender().sendMessage(suggestButton("[name]", base + "name ", "Change name").append(Component.space())
            .append(suggestButton("[level " + p.minimumLevel() + "]", base + "level ", "Minimum level")).append(Component.space())
            .append(suggestButton("[distance " + p.minDistance() + "-" + p.maxDistance() + "]", base + "distance ", "Spawn ring")));
        ctx.getSender().sendMessage(suggestButton("[chance " + p.dailyChance() + "]", base + "chance ", "Daily chance 0-1").append(Component.space())
            .append(suggestButton("[time " + p.timeFrom() + "-" + p.timeUntil() + "]", base + "time ", "Minecraft ticks")).append(Component.space())
            .append(suggestButton("[lifetime " + p.lifetimeSeconds() + "s]", base + "lifetime ", "Maximum appearance; 0 means unlimited")).append(Component.space())
            .append(suggestButton("[biome]", base + "biome ", "Add biome or clear")));
        ctx.getSender().sendMessage(suggestButton("[＋ idle saying]", base + "idle ", "Add random idle dialogue"));
        ctx.getSender().sendMessage(button("[← Profiles]", "/quest admin npcs", "Back"));
    }

    private boolean bindNpc(CommandContext ctx, QuestDefinition quest, boolean start) {
        Entity entity = targetedEntity(ctx);
        if (entity == null) return false;
        String name = entity.customName() == null || TextUtil.plain(entity.customName()).isBlank()
            ? randomNpcName() : TextUtil.plain(entity.customName());
        entity.customName(Component.text(name));
        entity.setCustomNameVisible(true);
        if (start) { quest.startNpc(entity.getUniqueId()); quest.startNpcName(name); }
        else { quest.endNpc(entity.getUniqueId()); quest.endNpcName(name); }
        return true;
    }

    private boolean editDialogue(CommandContext ctx, QuestDefinition quest) {
        String operation = ctx.getArgLower(4);
        String state = ctx.getArgLower(5);
        if (!List.of("offer", "idle", "incomplete", "objective", "complete").contains(state)) return false;
        if ("add".equals(operation) && ctx.numArgs() > 6) return quest.dialogue(state).add(join(ctx.args(), 6));
        if ("remove".equals(operation)) {
            try { quest.dialogue(state).remove(Integer.parseInt(ctx.getArg(6)) - 1); return true; }
            catch (RuntimeException ignored) { return false; }
        }
        return false;
    }

    private boolean editRequirement(CommandContext ctx, QuestDefinition quest) {
        String operation = ctx.getArgLower(4);
        String required = normalizeId(ctx.getArg(5));
        if (required.isBlank() || !quests.containsKey(required) || required.equals(quest.id())) return false;
        return "add".equals(operation) ? quest.requirements().add(required) : "remove".equals(operation) && quest.requirements().remove(required);
    }

    private boolean editReward(CommandContext ctx, QuestDefinition quest) {
        String operation = ctx.getArgLower(4);
        if (ctx.numArgs() < 6) return false;
        if ("add".equals(operation)) { quest.rewardCommands().add(join(ctx.args(), 5)); return true; }
        if ("remove".equals(operation)) {
            try { return quest.rewardCommands().remove(Integer.parseInt(ctx.getArg(5)) - 1) != null; }
            catch (RuntimeException ignored) { return false; }
        }
        return false;
    }

    private boolean editRewardItem(CommandContext ctx, QuestDefinition quest) {
        String operation = ctx.getArgLower(4);
        if ("add".equals(operation)) {
            Material material = Material.matchMaterial(ctx.getArg(5, ""));
            try {
                int amount = Integer.parseInt(ctx.getArg(6, "1"));
                quest.rewardItems().add(new QuestRewardItem(material, amount));
                return true;
            } catch (IllegalArgumentException ex) { return false; }
        }
        if ("remove".equals(operation)) {
            try { quest.rewardItems().remove(Integer.parseInt(ctx.getArg(5)) - 1); return true; }
            catch (RuntimeException ignored) { return false; }
        }
        return false;
    }

    private boolean editObjective(CommandContext ctx, QuestDefinition quest) {
        String operation = ctx.getArgLower(4);
        if ("remove".equals(operation)) {
            try { quest.objectives().remove(Integer.parseInt(ctx.getArg(5)) - 1); return true; }
            catch (RuntimeException ignored) { return false; }
        }
        if (!"add".equals(operation)) return false;
        String type = ctx.getArgLower(5);
        try {
            switch (type) {
                case "collect" -> {
                    Material material = Material.matchMaterial(ctx.getArg(6));
                    int amount = Integer.parseInt(ctx.getArg(7));
                    if (material == null || !material.isItem() || amount < 1) return false;
                    boolean consume = "consume".equalsIgnoreCase(ctx.getArg(8, ""));
                    int labelStart = consume ? 9 : 8;
                    quest.objectives().add(QuestObjective.collect(material, amount, consume,
                        ctx.numArgs() > labelStart ? join(ctx.args(), labelStart) : ""));
                }
                case "kill" -> {
                    EntityType entity = EntityType.valueOf(ctx.getArgUpper(6));
                    int amount = Integer.parseInt(ctx.getArg(7));
                    quest.objectives().add(QuestObjective.kill(entity, amount, ctx.numArgs() > 8 ? join(ctx.args(), 8) : ""));
                }
                case "location" -> {
                    if (!ctx.isPlayer()) return false;
                    double radius = Double.parseDouble(ctx.getArg(6));
                    quest.objectives().add(QuestObjective.location(ctx.asPlayer().getLocation(), radius,
                        ctx.numArgs() > 7 ? join(ctx.args(), 7) : ""));
                }
                case "npc" -> {
                    Entity entity = targetedEntity(ctx);
                    if (entity == null) return false;
                    quest.objectives().add(QuestObjective.npc(entity.getUniqueId(), ctx.numArgs() > 6 ? join(ctx.args(), 6) : ""));
                }
                case "biome" -> quest.objectives().add(new QuestObjective(QuestObjective.Type.BIOME, ctx.getArgLower(6), 1, false,
                    ctx.numArgs() > 7 ? join(ctx.args(), 7) : "", null, 0, 0, 0, 1));
                case "above", "below", "underwater" -> {
                    String y = String.valueOf(Double.parseDouble(ctx.getArg(6)));
                    QuestObjective.Type objectiveType = "above".equals(type) ? QuestObjective.Type.ALTITUDE_ABOVE
                        : "below".equals(type) ? QuestObjective.Type.ALTITUDE_BELOW : QuestObjective.Type.UNDERWATER;
                    quest.objectives().add(new QuestObjective(objectiveType, y, 1, false,
                        ctx.numArgs() > 7 ? join(ctx.args(), 7) : "", null, 0, 0, 0, 1));
                }
                case "structure" -> quest.objectives().add(new QuestObjective(QuestObjective.Type.STRUCTURE, ctx.getArgUpper(6), 1, false,
                    ctx.numArgs() > 7 ? join(ctx.args(), 7) : "", null, 0, 0, 0, 1));
                case "sleep", "night" -> quest.objectives().add(new QuestObjective("sleep".equals(type) ? QuestObjective.Type.SLEEP : QuestObjective.Type.NIGHT,
                    "", 1, false, ctx.numArgs() > 6 ? join(ctx.args(), 6) : "", null, 0, 0, 0, 1));
                case "interact" -> {
                    EntityType entity = EntityType.valueOf(ctx.getArgUpper(6));
                    quest.objectives().add(new QuestObjective(QuestObjective.Type.INTERACT, entity.name(), 1, false,
                        ctx.numArgs() > 7 ? join(ctx.args(), 7) : "", null, 0, 0, 0, 1));
                }
                default -> { return false; }
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean spawnNpc(CommandContext ctx, QuestDefinition quest) {
        if (!ctx.isPlayer()) return false;
        String role = ctx.getArgLower(4);
        if (!"start".equals(role) && !"end".equals(role)) return false;
        double radius;
        try { radius = Math.max(0D, Double.parseDouble(ctx.getArg(5, "0"))); }
        catch (NumberFormatException ex) { return false; }
        Location location = ctx.asPlayer().getLocation().clone();
        if (radius > 0) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
            double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
            location.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            location.setY(location.getWorld().getHighestBlockYAt(location) + 1D);
        }
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        String name = randomNpcName();
        villager.customName(Component.text(name));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        if ("start".equals(role)) { quest.startNpc(villager.getUniqueId()); quest.startNpcName(name); }
        else { quest.endNpc(villager.getUniqueId()); quest.endNpcName(name); }
        return true;
    }

    private String randomNpcName() {
        return npcNames.get(ThreadLocalRandom.current().nextInt(npcNames.size()));
    }

    private void adminDelete(CommandContext ctx) {
        QuestDefinition quest = quest(ctx.getArg(2), ctx);
        if (quest == null) return;
        if (!"confirm".equalsIgnoreCase(ctx.getArg(3, ""))) { ctx.error("Add 'confirm' to delete this quest."); return; }
        quests.remove(quest.id());
        deleteMarkers(quest);
        saveDefinitions();
        ctx.success("Deleted quest {quest}.", "quest", quest.id());
    }

    private void onNpcClick(PlayerInteractEntityEvent event) {
        if (!isPrimaryNpcInteraction(event.getHand())) return;
        Player player = event.getPlayer();
        String clickedProfileId = event.getRightClicked().getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
        boolean managedQuestNpc = (clickedProfileId != null && npcProfiles.containsKey(clickedProfileId))
            || npcProfiles.values().stream().anyMatch(profile -> event.getRightClicked().getUniqueId().equals(profile.spawnedEntity()));
        if (event.isCancelled() && !managedQuestNpc) return;
        if (clickedProfileId != null && npcLeavingSince.containsKey(clickedProfileId)) {
            event.setCancelled(true);
            QuestNpcProfile profile = npcProfiles.get(clickedProfileId);
            String message = profile == null || profile.leavingDialogue().isEmpty()
                ? "I'm heading home for now. I will see you next time."
                : profile.leavingDialogue().get(ThreadLocalRandom.current().nextInt(profile.leavingDialogue().size()));
            player.sendMessage(Component.text((profile == null ? npcName(event.getRightClicked(), "Quest Giver") : profile.name()) + ": ", NamedTextColor.GOLD)
                .append(Component.text(message, NamedTextColor.WHITE)));
            return;
        }
        focusNpcOnPlayer(event.getRightClicked(), player);
        for (QuestProgress progress : activeFor(player).values()) {
            QuestDefinition quest = quests.get(progress.questId());
            QuestObjective objective = currentObjective(quest, progress);
            if (objective != null && objective.type() == QuestObjective.Type.INTERACT
                && objective.target().equals(event.getRightClicked().getType().name())) {
                advance(player, quest, progress);
                break;
            }
        }
        UUID npc = event.getRightClicked().getUniqueId();

        // Completing or advancing an existing quest always takes priority over opening offers.
        for (QuestDefinition quest : quests.values()) {
            QuestProgress progress = progress(player, quest.id());
            if (progress == null) continue;
            syncCollectObjective(player, quest, progress);
            UUID endNpc = npcUuid(quest.endNpcProfile(), quest.endNpc());
            if (progress.state() == QuestProgress.State.READY && npc.equals(endNpc)) {
                event.setCancelled(true);
                if (turnIn(player, quest, progress)) speak(player, quest, "complete", quest.endNpcName());
                return;
            }
            QuestObjective objective = currentObjective(quest, progress);
            if (objective != null && objective.type() == QuestObjective.Type.NPC
                && matchesNpcObjective(event.getRightClicked(), objective.target())) {
                event.setCancelled(true);
                speak(player, quest, "objective", npcName(event.getRightClicked(), quest.endNpcName()));
                advance(player, quest, progress);
                return;
            }
        }

        List<QuestDefinition> available = quests.values().stream()
            .filter(QuestDefinition::enabled)
            .filter(quest -> npc.equals(npcUuid(quest.startNpcProfile(), quest.startNpc())))
            .filter(quest -> progress(player, quest.id()) == null && isAvailable(player, quest))
            .toList();
        if (!available.isEmpty()) {
            event.setCancelled(true);
            openQuestNpcInventory(player, event.getRightClicked(), available);
            return;
        }

        QuestDefinition idleQuest = null;
        String idleName = null;
        for (QuestDefinition quest : quests.values()) {
            if (!quest.enabled()) continue;
            UUID startNpc = npcUuid(quest.startNpcProfile(), quest.startNpc());
            UUID endNpc = npcUuid(quest.endNpcProfile(), quest.endNpc());
            QuestProgress progress = progress(player, quest.id());
            if (progress != null) syncCollectObjective(player, quest, progress);
            if (progress == null && npc.equals(startNpc)) {
                event.setCancelled(true);
                idleQuest = quest;
                idleName = quest.startNpcName();
                continue;
            }
            if (progress == null && npc.equals(endNpc)) {
                idleQuest = quest;
                idleName = quest.endNpcName();
                continue;
            }
            if (progress == null) continue;
            if (npc.equals(startNpc) || npc.equals(endNpc)) {
                event.setCancelled(true);
                if (!speak(player, quest, "incomplete", npc.equals(startNpc) ? quest.startNpcName() : quest.endNpcName()))
                    speakNpcProfileIdle(player, event.getRightClicked());
                return;
            }
        }
        if (idleQuest != null) {
            event.setCancelled(true);
            if (!speak(player, idleQuest, "idle", idleName)) speakNpcProfileIdle(player, event.getRightClicked());
        } else if (event.getRightClicked().getPersistentDataContainer().has(npcProfileKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            speakNpcProfileIdle(player, event.getRightClicked());
        }
    }

    static boolean isPrimaryNpcInteraction(EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND;
    }

    private void openQuestNpcInventory(Player player, Entity npc, List<QuestDefinition> available) {
        int size = Math.min(54, Math.max(9, ((available.size() + 8) / 9) * 9));
        String profileId = npc.getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
        QuestNpcInventoryHolder holder = new QuestNpcInventoryHolder(player.getUniqueId(), UUID.randomUUID(), profileId, npc.getLocation().clone());
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(npcName(npc, "Quest Giver") + "'s Quests"));
        holder.inventory(inventory);
        for (int slot = 0; slot < Math.min(size, available.size()); slot++) {
            QuestDefinition quest = available.get(slot);
            QuestProgress preview = new QuestProgress(player.getUniqueId(), quest.id(), 0, 0,
                quest.objectives().isEmpty() ? QuestProgress.State.READY : QuestProgress.State.ACTIVE);
            ItemStack book = createBook(player, quest, preview, currentRevision(player.getUniqueId(), quest.id()) + 1);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.getPersistentDataContainer().set(questMenuSessionKey, PersistentDataType.STRING, holder.sessionId().toString());
            book.setItemMeta(meta);
            inventory.setItem(slot, book);
        }
        questMenuSessions.put(player.getUniqueId(), holder.sessionId());
        if (profileId != null) npcMenuEngagements.put(player.getUniqueId(), profileId);
        player.openInventory(inventory);
    }

    private void onQuestNpcInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
            || !(event.getInventory().getHolder(false) instanceof QuestNpcInventoryHolder holder)
            || !holder.playerId().equals(player.getUniqueId())) return;

        boolean cancelledAny = false;
        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null || item.getType().isAir() || isQuestMenuOffer(item, holder.sessionId())) continue;
            String questId = bookQuestId(item);
            if (questId != null && player.getUniqueId().equals(bookOwner(item)) && progress(player, questId) != null) {
                if (cancelQuest(player.getUniqueId(), questId)) {
                    removeQuestBooks(player, questId);
                    QuestDefinition quest = quests.get(questId);
                    questMessage(player, "abandoned", "<yellow>Quest {quest} abandoned.</yellow>",
                        "quest", quest == null ? questId : renderQuestText(quest, quest.title()));
                    cancelledAny = true;
                }
            } else {
                holder.dropLocation().getWorld().dropItemNaturally(holder.dropLocation(), item.clone());
            }
        }
        event.getInventory().clear();
        if (cancelledAny) refreshMarkers(player);
        api.tasks().nextTick(() -> {
            acceptTakenQuestOffers(player, holder.sessionId());
            questMenuSessions.remove(player.getUniqueId(), holder.sessionId());
            npcMenuEngagements.remove(player.getUniqueId(), holder.npcProfileId());
            validateInventory(player.getInventory());
        });
    }

    private void acceptTakenQuestOffers(Player player, UUID sessionId) {
        Map<String,Integer> selected = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isQuestMenuOffer(item, sessionId)) continue;
            String questId = bookQuestId(item);
            inventory.setItem(slot, null);
            if (questId != null) selected.putIfAbsent(questId,slot);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isQuestMenuOffer(cursor, sessionId)) {
            String questId = bookQuestId(cursor);
            player.setItemOnCursor(null);
            if (questId != null) selected.putIfAbsent(questId,-1);
        }

        int accepted = 0;
        for (Map.Entry<String,Integer> selection : selected.entrySet()) {
            String questId=selection.getKey();
            QuestDefinition quest = quests.get(questId);
            if (quest == null || !startQuest(player, quest, false, false,selection.getValue())) continue;
            questMessage(player, "accepted", "<yellow>Quest {quest} accepted.</yellow>",
                "quest", renderQuestText(quest, quest.title()));
            if (quest.timeLimitSeconds() > 0)
                questMessage(player, "time-limit", "<yellow>Time limit: {duration}.</yellow>",
                    "duration", formatDuration(quest.timeLimitSeconds()));
            accepted++;
        }
        if (accepted > 0) playQuestStartedSound(player);
    }

    private boolean isQuestMenuOffer(@Nullable ItemStack item, UUID sessionId) {
        if (item == null || !(item.getItemMeta() instanceof BookMeta meta)) return false;
        return sessionId.toString().equals(meta.getPersistentDataContainer().get(questMenuSessionKey, PersistentDataType.STRING));
    }

    private boolean isQuestMenuOffer(@Nullable ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BookMeta meta)) return false;
        String rawSession = meta.getPersistentDataContainer().get(questMenuSessionKey, PersistentDataType.STRING);
        UUID owner = bookOwner(item);
        try { return owner != null && UUID.fromString(rawSession).equals(questMenuSessions.get(owner)); }
        catch (IllegalArgumentException | NullPointerException ignored) { return false; }
    }

    private static final class QuestNpcInventoryHolder implements InventoryHolder {
        private final UUID playerId;
        private final UUID sessionId;
        private final String npcProfileId;
        private final Location dropLocation;
        private Inventory inventory;

        private QuestNpcInventoryHolder(UUID playerId, UUID sessionId, String npcProfileId, Location dropLocation) {
            this.playerId = playerId;
            this.sessionId = sessionId;
            this.npcProfileId = npcProfileId;
            this.dropLocation = dropLocation;
        }

        private UUID playerId() { return playerId; }
        private UUID sessionId() { return sessionId; }
        private String npcProfileId() { return npcProfileId; }
        private Location dropLocation() { return dropLocation; }
        private void inventory(Inventory value) { inventory = value; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private @Nullable UUID npcUuid(@Nullable String profileId, @Nullable UUID fixedUuid) {
        QuestNpcProfile profile = profileId == null ? null : npcProfiles.get(profileId);
        return profile != null ? profile.spawnedEntity() : fixedUuid;
    }

    private boolean matchesNpcObjective(Entity entity, String target) {
        if (target.startsWith("profile:")) {
            String actual = entity.getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
            return target.substring("profile:".length()).equals(actual);
        }
        return entity.getUniqueId().toString().equals(target);
    }

    private void onEntityDeath(EntityDeathEvent event) {
        String profileId = event.getEntity().getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
        if (profileId != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            QuestNpcProfile profile = npcProfiles.get(profileId);
            if (profile != null) {
                profile.spawnedEntity(null);
                recordNpcDeath(profile, event.getEntity().getWorld());
                saveNpcProfiles();
                registerMarkers();
            }
        }
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        for (QuestProgress progress : activeFor(player).values()) {
            QuestDefinition quest = quests.get(progress.questId());
            QuestObjective objective = currentObjective(quest, progress);
            if (objective != null && objective.type() == QuestObjective.Type.KILL
                && (objective.target().equals(event.getEntityType().name())
                    || (objective.target().equals("HOSTILE") && event.getEntity() instanceof Monster))) {
                increment(player, quest, progress, 1);
            }
        }
    }

    private void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) return;
        Player player = event.getPlayer();
        for (QuestProgress progress : activeFor(player).values()) {
            QuestDefinition quest = quests.get(progress.questId());
            syncCollectObjective(player, quest, progress);
            QuestObjective objective = currentObjective(quest, progress);
            if (objective == null) continue;
            Location to = event.getTo();
            boolean complete = switch (objective.type()) {
                case LOCATION -> objective.world() != null && objective.world().equals(to.getWorld().getName())
                    && distanceSquared(to, objective) <= objective.radius() * objective.radius();
                case BIOME -> false;
                case ALTITUDE_ABOVE -> to.getY() > objectiveTargetNumber(objective);
                case ALTITUDE_BELOW -> to.getY() < objectiveTargetNumber(objective);
                case UNDERWATER -> (to.getBlock().getType() == Material.WATER || to.getBlock().getType() == Material.BUBBLE_COLUMN)
                    && to.getY() < objectiveTargetNumber(objective);
                case NIGHT -> to.getWorld().getTime() >= 13000 && to.getWorld().getTime() <= 23000;
                case STRUCTURE -> insideStructure(to, objective.target());
                default -> false;
            };
            if (complete) advance(player, quest, progress);
        }
    }

    private void tickBiomeObjectives() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (QuestProgress progress : activeFor(player).values()) {
                QuestDefinition quest = quests.get(progress.questId());
                QuestObjective objective = currentObjective(quest, progress);
                if (objective == null || objective.type() != QuestObjective.Type.BIOME) continue;
                boolean inside = player.getLocation().getBlock().getBiome().getKey().getKey().equalsIgnoreCase(objective.target());
                if (inside) {
                    increment(player, quest, progress, 1);
                } else if (progress.objectiveProgress() != 0) {
                    progress.objectiveProgress(0);
                    saveProgress(progress);
                    updateQuestBook(player, quest, progress);
                }
            }
        }
    }

    private void tickQuestTracking() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAutoTracking(player.getUniqueId())) updateAutomaticTracking(player);
            else if (trackedQuests.containsKey(player.getUniqueId())) updateQuestTracking(player);
        }
    }

    private void updateAutomaticTracking(Player player) {
        String selected = selectAutomaticQuest(player, System.currentTimeMillis());
        if (selected == null) {
            if (trackedQuests.containsKey(player.getUniqueId())) stopTracking(player.getUniqueId());
        } else trackQuest(player, selected);
    }

    private @Nullable String selectAutomaticQuest(Player player, long now) {
        UUID playerId = player.getUniqueId();
        String activityQuest = autoTrackActivityUntil.getOrDefault(playerId, 0L) > now
            ? autoTrackActivityQuests.get(playerId) : null;
        if (activityQuest == null) {
            autoTrackActivityQuests.remove(playerId);
            autoTrackActivityUntil.remove(playerId);
        }
        return active.getOrDefault(playerId, Map.of()).entrySet().stream()
            .filter(entry -> hasOwnedQuestBook(player, entry.getKey()))
            .map(entry -> automaticTrackingCandidate(playerId, entry.getKey(), entry.getValue(), activityQuest, now))
            .filter(Objects::nonNull)
            .max(Comparator.comparingInt(AutoTrackingCandidate::priority)
                .thenComparingLong(AutoTrackingCandidate::tieBreaker))
            .map(AutoTrackingCandidate::questId).orElse(null);
    }

    private @Nullable AutoTrackingCandidate automaticTrackingCandidate(UUID playerId, String questId,
                                                                         QuestProgress progress,
                                                                         @Nullable String activityQuest,
                                                                         long now) {
        QuestDefinition quest = quests.get(questId);
        if (quest == null) return null;
        QuestObjective objective = currentObjective(quest, progress);
        long remaining = timedQuestSecondsRemaining(playerId, quest, now);
        boolean timed = remaining >= 0;
        boolean urgent = timed && remaining < URGENT_TIMED_QUEST_SECONDS;
        boolean activity = questId.equals(activityQuest);
        boolean ready = isReadyToTurnIn(Bukkit.getPlayer(playerId), quest, progress);
        boolean npc = objective != null && objective.type() == QuestObjective.Type.NPC;
        int priority = automaticTrackingPriority(urgent, activity, ready, npc, timed);
        long tieBreaker = timed && (urgent || priority == 200) ? -remaining
            : attemptStarted.getOrDefault(playerId, Map.of()).getOrDefault(questId, 0L);
        return new AutoTrackingCandidate(questId, priority, tieBreaker);
    }

    static int automaticTrackingPriority(boolean urgentTimed, boolean recentActivity, boolean ready,
                                         boolean npc, boolean timed) {
        if (urgentTimed) return 500;
        if (recentActivity) return 400;
        if (ready) return 300;
        if (npc) return 250;
        if (timed) return 200;
        return 100;
    }

    private record AutoTrackingCandidate(String questId, int priority, long tieBreaker) {}

    private void noteAutomaticTrackingActivity(Player player, QuestDefinition quest) {
        if (!isAutoTracking(player.getUniqueId()) || !hasOwnedQuestBook(player, quest.id())) return;
        autoTrackActivityQuests.put(player.getUniqueId(), quest.id());
        autoTrackActivityUntil.put(player.getUniqueId(), System.currentTimeMillis() + AUTO_TRACK_ACTIVITY_MS);
        updateAutomaticTracking(player);
    }

    private void reconcileAutomaticTracking(Player player) {
        UUID playerId = player.getUniqueId();
        if (!isAutoTracking(playerId) || trackedQuests.containsKey(playerId)) return;
        boolean hasTrackableQuest = active.getOrDefault(playerId, Map.of()).keySet().stream()
            .anyMatch(id -> hasOwnedQuestBook(player, id));
        if (hasTrackableQuest) updateAutomaticTracking(player);
    }

    private void updateQuestTracking(Player player) {
        String questId = trackedQuests.get(player.getUniqueId());
        QuestDefinition quest = questId == null ? null : quests.get(questId);
        QuestProgress progress = quest == null ? null : progress(player, questId);
        if (quest == null || progress == null) {
            stopTracking(player.getUniqueId());
            return;
        }
        if (!hasOwnedQuestBook(player, questId)) {
            if (isAutoTracking(player.getUniqueId())) {
                trackMostRecentQuest(player.getUniqueId());
                return;
            }
            BossBar hidden = trackingBossBars.remove(player.getUniqueId());
            if (hidden != null) player.hideBossBar(hidden);
            return;
        }
        if (!matchesTrackingWorld(trackingWorlds, player.getWorld().getName())) {
            BossBar hidden = trackingBossBars.remove(player.getUniqueId());
            if (hidden != null) player.hideBossBar(hidden);
            return;
        }

        Location target = trackingTarget(player, quest, progress);
        boolean npcDestination = trackingNpcDestination(player, quest, progress);
        String indicator = trackingIndicator(player.getLocation(), target, npcDestination, System.currentTimeMillis());
        String objective = trackingObjectiveText(player, quest, progress);
        Component title = glyph("question_yellow", "!").color(NamedTextColor.YELLOW)
            .append(Component.text(" " + trackingQuestTitle(quest.title()), NamedTextColor.YELLOW))
            .append(Component.text(" - " + (indicator.isEmpty() ? "" : indicator + " ") + objective, NamedTextColor.WHITE));
        BossBar bar = trackingBossBars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = BossBar.bossBar(Component.empty(), 0F, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bar.name(title);
        bar.progress(0F);
    }

    static boolean matchesTrackingWorld(List<Pattern> patterns, String worldName) {
        String normalized = worldName.toLowerCase(Locale.ROOT);
        return patterns.stream().anyMatch(pattern -> pattern.matcher(normalized).matches());
    }

    static String trackingQuestTitle(String title) {
        int separator = title.lastIndexOf(": ");
        return separator < 0 ? title : title.substring(separator + 2).trim();
    }

    static String trackingIndicator(Location player, @Nullable Location target, boolean npcDestination, long now) {
        if (target != null && player.getWorld() != null && player.getWorld().equals(target.getWorld()))
            return trackingArrow(player, target);
        return npcDestination && Math.floorDiv(now, 500L) % 2L == 0L ? "·" : "";
    }

    private boolean trackingNpcDestination(Player player, QuestDefinition quest, QuestProgress progress) {
        if (isReadyToTurnIn(player, quest, progress)) return true;
        QuestObjective objective = currentObjective(quest, progress);
        return objective != null && objective.type() == QuestObjective.Type.NPC;
    }

    private String trackingObjectiveText(Player player, QuestDefinition quest, QuestProgress progress) {
        if (isReadyToTurnIn(player, quest, progress))
            return "Return to " + renderQuestText(quest, quest.endNpcName());
        QuestObjective objective = progress.state() == QuestProgress.State.READY
            ? firstMissingCollectObjective(player, quest) : currentObjective(quest, progress);
        if (objective == null) return "Ready";
        String label = renderQuestText(quest, objective.label());
        int objectiveProgress = progress.state() == QuestProgress.State.READY
            ? Math.min(objective.amount(), countMaterial(player.getInventory(), Material.valueOf(objective.target())))
            : progress.objectiveProgress();
        String text = switch (objective.type()) {
            case COLLECT, KILL -> formatTrackedObjective(label, objectiveProgress, objective.amount());
            case BIOME -> label + ": " + progress.objectiveProgress() + "/" + objective.amount() + " seconds";
            case ALTITUDE_ABOVE, ALTITUDE_BELOW -> label + " (Y " + player.getLocation().getBlockY() + ")";
            default -> label;
        };
        long remaining=timedQuestSecondsRemaining(player.getUniqueId(),quest,System.currentTimeMillis());
        if(remaining<0)return text;
        return text.replaceFirst("(?i)\\s+within\\s+.+$", "")+" within "+formatCountdownDisplay(remaining);
    }

    static String formatTrackedObjective(String label, int progress, int amount) {
        label = label.replaceFirst("(?i)\\s+for\\s+.+$", "");
        String count = progress + "/" + amount;
        Matcher amountMatcher = Pattern.compile("(?<!\\d)" + amount + "(?!\\d)").matcher(label);
        String formatted;
        if (amountMatcher.find()) {
            formatted = label.substring(0, amountMatcher.start()) + count + label.substring(amountMatcher.end());
        } else {
            int separator = label.indexOf(' ');
            if (separator < 0) return label + " " + count;
            String subject = label.substring(separator + 1).replaceFirst("(?i)^(?:a|an)\\s+", "");
            formatted = label.substring(0, separator) + " " + count + " " + subject;
        }

        int subjectStart = formatted.indexOf(count) + count.length();
        String prefix = formatted.substring(0, subjectStart);
        String subject = formatted.substring(subjectStart).trim();
        String[] words = subject.split("\\s+");
        StringBuilder titled = new StringBuilder();
        for (String word : words) {
            if (!titled.isEmpty()) titled.append(' ');
            titled.append(word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return prefix + (titled.isEmpty() ? "" : " " + titled);
    }

    private @Nullable Location trackingTarget(Player player, QuestDefinition quest, QuestProgress progress) {
        if (isReadyToTurnIn(player, quest, progress))
            return npcProfileLocation(quest.endNpcProfile(), quest.endNpc());
        if (progress.state() == QuestProgress.State.READY) return null;
        QuestObjective objective = currentObjective(quest, progress);
        if (objective == null) return null;
        return switch (objective.type()) {
            case LOCATION -> objective.world() == null || Bukkit.getWorld(objective.world()) == null ? null
                : new Location(Bukkit.getWorld(objective.world()), objective.x(), objective.y(), objective.z());
            case NPC -> objective.target().startsWith("profile:")
                ? npcProfileLocation(objective.target().substring("profile:".length()), null)
                : entityLocation(objective.target());
            case KILL -> player.getWorld().getNearbyLivingEntities(player.getLocation(), 64D, 64D, 64D,
                    entity -> (entity.getType().name().equals(objective.target())
                        || (objective.target().equals("HOSTILE") && entity instanceof Monster)) && !entity.isDead()).stream()
                .min(java.util.Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .map(Entity::getLocation).orElse(null);
            default -> null;
        };
    }

    private @Nullable Location npcProfileLocation(@Nullable String profileId, @Nullable UUID fixedNpc) {
        if (profileId != null) {
            QuestNpcProfile profile = npcProfiles.get(profileId);
            if (profile == null) return null;
            Entity entity = profile.spawnedEntity() == null ? null : Bukkit.getEntity(profile.spawnedEntity());
            if (entity != null && entity.isValid()) return entity.getLocation();
            return null;
        }
        Entity entity = fixedNpc == null ? null : Bukkit.getEntity(fixedNpc);
        return entity == null ? null : entity.getLocation();
    }

    private @Nullable Location entityLocation(String uuid) {
        try {
            Entity entity = Bukkit.getEntity(UUID.fromString(uuid));
            return entity == null ? null : entity.getLocation();
        } catch (IllegalArgumentException ignored) { return null; }
    }

    static String trackingArrow(Location from, Location target) {
        if (from.getWorld() == null || target.getWorld() == null || !from.getWorld().equals(target.getWorld())) return "";
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        if (dx * dx + dz * dz < 4D) return "↑";
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Math.IEEEremainder(targetYaw - from.getYaw(), 360D);
        if (relative >= -45D && relative <= 45D) return "↑";
        if (relative > 45D && relative < 135D) return "→";
        if (relative < -45D && relative > -135D) return "←";
        return "↓";
    }

    private void onTimeSkip(TimeSkipEvent event) {
        if (!"NIGHT_SKIP".equals(event.getSkipReason().name())) return;
        for (Player player : event.getWorld().getPlayers()) {
            if (!player.isSleeping()) continue;
            for (QuestProgress progress : activeFor(player).values()) {
                QuestDefinition quest = quests.get(progress.questId());
                QuestObjective objective = currentObjective(quest, progress);
                if (objective != null && objective.type() == QuestObjective.Type.SLEEP) increment(player, quest, progress, 1);
            }
        }
    }

    private double objectiveTargetNumber(QuestObjective objective) {
        try { return Double.parseDouble(objective.target()); }
        catch (NumberFormatException ignored) { return 0D; }
    }

    private boolean insideStructure(Location location, String target) {
        Structure structure = switch (target.toUpperCase(Locale.ROOT)) {
            case "SHIPWRECK" -> Structure.SHIPWRECK;
            case "SHIPWRECK_BEACHED" -> Structure.SHIPWRECK_BEACHED;
            case "PILLAGER_OUTPOST" -> Structure.PILLAGER_OUTPOST;
            case "OCEAN_RUIN_COLD" -> Structure.OCEAN_RUIN_COLD;
            case "OCEAN_RUIN_WARM" -> Structure.OCEAN_RUIN_WARM;
            case "RUINED_PORTAL" -> Structure.RUINED_PORTAL;
            case "MINESHAFT" -> Structure.MINESHAFT;
            case "BURIED_TREASURE" -> Structure.BURIED_TREASURE;
            default -> null;
        };
        if (structure == null) return false;
        for (GeneratedStructure generated : location.getWorld().getStructures(location.getChunk().getX(), location.getChunk().getZ(), structure))
            if (generated.getBoundingBox().contains(location.getX(), location.getY(), location.getZ())) return true;
        return false;
    }

    private void syncCollectObjective(Player player, @Nullable QuestDefinition quest, QuestProgress progress) {
        QuestObjective objective = currentObjective(quest, progress);
        if (objective == null || objective.type() != QuestObjective.Type.COLLECT) return;
        int previous = progress.objectiveProgress();
        int count = Math.min(objective.amount(), countMaterial(player.getInventory(), Material.valueOf(objective.target())));
        if (count >= objective.amount()) advance(player, quest, progress);
        else if (count != progress.objectiveProgress()) {
            progress.objectiveProgress(count);
            saveProgress(progress);
            updateQuestBook(player, quest, progress);
        }
        if (quest != null && count > previous) noteAutomaticTrackingActivity(player, quest);
    }

    private void syncCollectObjectives(Player player) {
        for (QuestProgress progress : activeFor(player).values()) {
            syncCollectObjective(player, quests.get(progress.questId()), progress);
        }
    }

    private boolean startQuest(Player player, QuestDefinition quest, boolean force) {
        return startQuest(player, quest, force, true);
    }

    private boolean startQuest(Player player, QuestDefinition quest, boolean force, boolean announce) {
        return startQuest(player,quest,force,announce,-1);
    }

    private boolean startQuest(Player player, QuestDefinition quest, boolean force, boolean announce,int preferredSlot) {
        if (!force && !isAvailable(player, quest)) {
            if (announce) questMessage(player, "unavailable", "/error/That quest is not available.");
            return false;
        }
        if (progress(player, quest.id()) != null) {
            if (announce) questMessage(player, "already-active", "/warn/That quest is already active.");
            return false;
        }
        int revision = currentRevision(player.getUniqueId(), quest.id()) + 1;
        ItemStack book = createBook(player, quest, new QuestProgress(player.getUniqueId(), quest.id(), 0, 0,
            quest.objectives().isEmpty() ? QuestProgress.State.READY : QuestProgress.State.ACTIVE), revision);
        PlayerInventory inventory=player.getInventory();
        Map<Integer,ItemStack> overflow;
        if(preferredSlot>=0&&preferredSlot<inventory.getSize()&&inventory.getItem(preferredSlot)==null){
            inventory.setItem(preferredSlot,book);overflow=Map.of();
        }else overflow=inventory.addItem(book);
        if (!overflow.isEmpty()) {
            if (announce) questMessage(player, "inventory-space-required", "/error/Make room in your inventory for the quest book.");
            return false;
        }
        QuestProgress progress = new QuestProgress(player.getUniqueId(), quest.id(), 0, 0,
            quest.objectives().isEmpty() ? QuestProgress.State.READY : QuestProgress.State.ACTIVE);
        activeFor(player).put(quest.id(), progress);
        long startedAt = System.currentTimeMillis();
        attemptStarted.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(quest.id(), startedAt);
        api.database().update("INSERT OR REPLACE INTO quest_attempt_timing(player_uuid,quest_id,started_at) VALUES(?,?,?)", statement -> {
            statement.setString(1, player.getUniqueId().toString()); statement.setString(2, quest.id()); statement.setLong(3, startedAt);
        });
        saveRevision(player.getUniqueId(), quest.id(), revision);
        saveProgress(progress);
        if (isAutoTracking(player.getUniqueId()) && !trackedQuests.containsKey(player.getUniqueId()))
            trackQuest(player, quest.id());
        if (announce) {
            questMessage(player, "started", "/success/Quest started: {quest}", "quest", quest.title());
            if (quest.timeLimitSeconds() > 0) questMessage(player, "time-limit", "<yellow>Time limit: {duration}.</yellow>",
                "duration", formatDuration(quest.timeLimitSeconds()));
            playQuestStartedSound(player);
        }
        refreshMarkers(player);
        return true;
    }

    private void increment(Player player, QuestDefinition quest, QuestProgress progress, int amount) {
        QuestObjective objective = currentObjective(quest, progress);
        if (objective == null) return;
        progress.objectiveProgress(Math.min(objective.amount(), progress.objectiveProgress() + amount));
        if (progress.objectiveProgress() >= objective.amount()) advance(player, quest, progress);
        else { saveProgress(progress); updateQuestBook(player, quest, progress); }
        noteAutomaticTrackingActivity(player, quest);
    }

    private void advance(Player player, QuestDefinition quest, QuestProgress progress) {
        progress.objectiveIndex(progress.objectiveIndex() + 1);
        progress.objectiveProgress(0);
        if (progress.objectiveIndex() >= quest.objectives().size()) {
            progress.state(QuestProgress.State.READY);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.7F);
            questMessage(player, "ready-to-return", "<yellow>{quest} is complete. Return to {npc}.</yellow>",
                "quest", renderQuestText(quest, quest.title()), "npc", quest.endNpcName());
        } else questMessage(player, "objective-complete", "/success/Quest objective complete.");
        saveProgress(progress);
        updateQuestBook(player, quest, progress);
        refreshMarkers(player);
    }

    private boolean turnIn(Player player, QuestDefinition quest, QuestProgress progress) {
        if (!hasOwnedQuestBook(player, quest.id())) {
            questMessage(player, "book-required", "/error/You must be carrying your quest book to claim the reward.");
            return false;
        }
        if (quest.globalMaxCompletions() > 0 && globalCompletionCount(quest.id()) >= quest.globalMaxCompletions()) {
            failQuest(player, quest, "limited-completed", "Another player completed this limited quest first.");
            return false;
        }
        if (!collectObjectivesSatisfied(player, quest)) {
            questMessage(player, "required-items-missing", "/error/You do not have all required quest items.");
            return false;
        }
        ItemStack[] inventorySnapshot = cloneItems(player.getInventory().getContents());
        for (QuestObjective objective : quest.objectives()) {
            if (objective.type() == QuestObjective.Type.COLLECT && objective.consume()) {
                removeMaterial(player.getInventory(), Material.valueOf(objective.target()), objective.amount());
            }
        }
        List<ItemStack> rewards = createRewardItems(quest);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(rewards.toArray(ItemStack[]::new));
        if (!overflow.isEmpty()) {
            MailSendResult mailed = api.mailboxes() == null ? MailSendResult.failure("Mailbox feature is unavailable")
                : api.mailboxes().send(new MailSendRequest("Quest Rewards", player.getUniqueId(),
                    "Inventory overflow from quest: " + renderQuestText(quest, quest.title()), new ArrayList<>(overflow.values())));
            if (!mailed.queued()) {
                player.getInventory().setContents(inventorySnapshot);
                questMessage(player, "reward-delivery-failed", "/error/Your reward could not fit and could not be mailed. Make inventory space and try again.");
                return false;
            }
            questMessage(player, "rewards-mailed", "/info/Your inventory was full, so some quest rewards were sent to your mailbox.");
        }
        boolean firstCompletion = completed.computeIfAbsent(player.getUniqueId(), ignored -> new java.util.HashSet<>()).add(quest.id());
        try {
            api.database().update("INSERT OR REPLACE INTO quest_completed(player_uuid,quest_id,completed_at) VALUES(?,?,?)", statement -> {
                statement.setString(1, player.getUniqueId().toString()); statement.setString(2, quest.id()); statement.setLong(3, System.currentTimeMillis());
            });
            cancelQuest(player.getUniqueId(), quest.id());
            removeQuestBooks(player, quest.id());
        } catch (RuntimeException exception) {
            completed.getOrDefault(player.getUniqueId(), Set.of()).remove(quest.id());
            player.getInventory().setContents(inventorySnapshot);
            STEMCraft.getPlugin().getLogger().log(java.util.logging.Level.SEVERE,
                "Could not complete quest '" + quest.id() + "' for " + player.getName(), exception);
            questMessage(player, "save-failed", "/error/The quest could not be saved. Your items were restored; please try again.");
            return false;
        }
        questMessage(player, "completed", "<yellow>Quest {quest} completed.</yellow>",
            "quest", renderQuestText(quest, quest.title()));
        playQuestCompletedSound(player);
        refreshMarkers(player);
        runCompletionSideEffects(player, quest, firstCompletion);
        return true;
    }

    private void runCompletionSideEffects(Player player, QuestDefinition quest, boolean firstCompletion) {
        try {
            api.playerStats().increment(player.getUniqueId(), player.getName(), "quests_completed_total", 1);
            if (firstCompletion) api.playerStats().increment(player.getUniqueId(), player.getName(), "quests_completed_unique", 1);
            STEMCraft.getPlugin().entitlements().onFactsChanged(player.getUniqueId());
        } catch (RuntimeException exception) {
            STEMCraft.getPlugin().getLogger().log(java.util.logging.Level.SEVERE,
                "Quest completion progression failed for '" + quest.id() + "' and " + player.getName(), exception);
        }
        for (String command : quest.rewardCommands()) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString()));
            } catch (RuntimeException exception) {
                STEMCraft.getPlugin().getLogger().log(java.util.logging.Level.SEVERE,
                    "Quest reward command failed for '" + quest.id() + "': " + command, exception);
            }
        }
    }

    private void playQuestStartedSound(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9F, 0.8F);
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.75F, 1.25F);
        api.tasks().runLater(5L, () -> {
            if (!player.isOnline()) return;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.45F);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7F, 1.8F);
        });
    }

    private void playQuestCompletedSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.75F, 1.1F);
        api.tasks().runLater(6L, () -> {
            if (!player.isOnline()) return;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 0.9F);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8F, 1.5F);
        });
        api.tasks().runLater(12L, () -> {
            if (player.isOnline()) player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.7F, 1.2F);
        });
    }

    static List<ItemStack> createRewardItems(QuestDefinition quest) {
        List<ItemStack> result = new ArrayList<>();
        for (QuestRewardItem reward : quest.rewardItems()) {
            int remaining = reward.amount();
            int stackSize = reward.material().getMaxStackSize();
            while (remaining > 0) {
                int amount = Math.min(stackSize, remaining);
                ItemStack item = new ItemStack(reward.material(), amount);
                if (reward.name() != null || !reward.lore().isEmpty() || reward.unbreakable()) {
                    var meta = item.getItemMeta();
                    if (reward.name() != null) meta.displayName(Component.text(reward.name(), NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                    if (!reward.lore().isEmpty()) meta.lore(reward.lore().stream().map(line ->
                        Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
                    meta.setUnbreakable(reward.unbreakable());
                    item.setItemMeta(meta);
                }
                result.add(item);
                remaining -= amount;
            }
        }
        return result;
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : items[i].clone();
        return copy;
    }

    private boolean speak(Player player, QuestDefinition quest, String state, String npcName) {
        List<String> choices = quest.dialogue(state);
        if (choices.isEmpty()) return false;
        String line = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        line = renderQuestText(quest, line).replace("{npc}", npcName);
        player.sendMessage(Component.text(npcName + ": ", NamedTextColor.GOLD)
            .append(Component.text(line, NamedTextColor.WHITE)));
        return true;
    }

    private boolean speakNpcProfileIdle(Player player, Entity entity) {
        String profileId = entity.getPersistentDataContainer().get(npcProfileKey, PersistentDataType.STRING);
        QuestNpcProfile profile = profileId == null ? null : npcProfiles.get(profileId);
        if (profile == null) {
            profile = npcProfiles.values().stream()
                .filter(candidate -> entity.getUniqueId().equals(candidate.spawnedEntity()))
                .findFirst().orElse(null);
        }
        if (profile == null || profile.idleDialogue().isEmpty()) return false;
        String line = profile.idleDialogue().get(ThreadLocalRandom.current().nextInt(profile.idleDialogue().size()));
        player.sendMessage(Component.text(profile.name() + ": ", NamedTextColor.GOLD)
            .append(Component.text(line, NamedTextColor.WHITE)));
        return true;
    }

    private String npcName(Entity entity, String fallback) {
        return entity.customName() == null || TextUtil.plain(entity.customName()).isBlank()
            ? fallback : TextUtil.plain(entity.customName());
    }

    private String renderQuestText(QuestDefinition quest, String text) {
        return text.replace("{start-npc}", quest.startNpcName()).replace("{end-npc}", quest.endNpcName());
    }

    private static boolean collectObjectivesSatisfied(Player player, QuestDefinition quest) {
        return firstMissingCollectObjective(player, quest) == null;
    }

    static boolean isReadyToTurnIn(@Nullable Player player, QuestDefinition quest, QuestProgress progress) {
        return player != null && progress.state() == QuestProgress.State.READY
            && collectObjectivesSatisfied(player, quest);
    }

    static @Nullable QuestObjective firstMissingCollectObjective(Player player, QuestDefinition quest) {
        for (QuestObjective objective : quest.objectives()) {
            if (objective.type() == QuestObjective.Type.COLLECT
                && countMaterial(player.getInventory(), Material.valueOf(objective.target())) < objective.amount()) return objective;
        }
        return null;
    }

    private boolean isAvailable(Player player, QuestDefinition quest) {
        if (!quest.enabled() || progress(player, quest.id()) != null) return false;
        if (quest.globalMaxCompletions() > 0 && globalCompletionCount(quest.id()) >= quest.globalMaxCompletions()) return false;
        if (quest.restartCooldownSeconds() > 0 && cooldownActive(player, quest)) return false;
        Set<String> done = completed.getOrDefault(player.getUniqueId(), Set.of());
        return (quest.repeatable() || !done.contains(quest.id())) && done.containsAll(quest.requirements());
    }

    private String questState(@Nullable Player player, QuestDefinition quest) {
        if (player == null) return quest.enabled() ? "ENABLED" : "DISABLED";
        QuestProgress progress = progress(player, quest.id());
        if (progress != null) return progress.state().name();
        if (completed.getOrDefault(player.getUniqueId(), Set.of()).contains(quest.id())) return "DONE";
        return isAvailable(player, quest) ? "AVAILABLE" : "LOCKED";
    }

    private void registerMarkers() {
        for (QuestDefinition quest : quests.values()) {
            deleteMarkers(quest);
            if (!quest.enabled()) continue;
            UUID startNpc = npcUuid(quest.startNpcProfile(), quest.startNpc());
            UUID endNpc = npcUuid(quest.endNpcProfile(), quest.endNpc());
            if (startNpc != null) api.holograms().createDynamic(HOLOGRAM_TYPE, markerKey(quest, "start"), startNpc, 3.0D,
                player -> isAvailable(player, quest), player -> glyph("question_yellow", "?"));
            if (endNpc != null) api.holograms().createDynamic(HOLOGRAM_TYPE, markerKey(quest, "end"), endNpc, 3.0D,
                player -> { QuestProgress progress = progress(player, quest.id()); return progress != null && isReadyToTurnIn(player, quest, progress); },
                player -> glyph("exclamation_yellow", "!"));
            for (int i = 0; i < quest.objectives().size(); i++) {
                QuestObjective objective = quest.objectives().get(i);
                if (objective.type() != QuestObjective.Type.NPC) continue;
                try {
                    int index = i;
                    api.holograms().createDynamic(HOLOGRAM_TYPE, markerKey(quest, "objective-" + i), UUID.fromString(objective.target()), 3.0D,
                        player -> { QuestProgress progress = progress(player, quest.id()); return progress != null && progress.objectiveIndex() == index; },
                        player -> glyph("exclamation_yellow", "!"));
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void deleteMarkers(QuestDefinition quest) {
        api.holograms().deleteDynamic(HOLOGRAM_TYPE, markerKey(quest, "start"));
        api.holograms().deleteDynamic(HOLOGRAM_TYPE, markerKey(quest, "end"));
        for (int i = 0; i < quest.objectives().size(); i++) api.holograms().deleteDynamic(HOLOGRAM_TYPE, markerKey(quest, "objective-" + i));
    }

    private void refreshMarkers(Player player) {
        for (QuestDefinition quest : quests.values()) {
            api.holograms().refreshDynamic(HOLOGRAM_TYPE, markerKey(quest, "start"), player);
            api.holograms().refreshDynamic(HOLOGRAM_TYPE, markerKey(quest, "end"), player);
            for (int i = 0; i < quest.objectives().size(); i++) api.holograms().refreshDynamic(HOLOGRAM_TYPE, markerKey(quest, "objective-" + i), player);
        }
    }

    private String markerKey(QuestDefinition quest, String role) { return quest.id() + ":" + role; }

    private Component glyph(String token, String fallback) {
        String marker = ":" + token + ":";
        String resolved = api.messages().tokens().apply(marker);
        return Component.text(marker.equals(resolved) ? fallback : resolved);
    }

    private void initializeBundledDefinitions() {
        try {
            loadBundledDefinitions(EXAMPLE_RESOURCE);
            loadBundledNpcProfiles(NPC_EXAMPLE_RESOURCE);
            loadBundledDefinitions(CAMPAIGN_RESOURCE);
            loadBundledNpcProfiles(CAMPAIGN_RESOURCE);
            saveDefinitions();
            saveNpcProfiles();
        } catch (IOException ex) {
            api.messages().error("Could not initialize bundled quests: {error}", "error", ex.getMessage());
        }
    }

    private void loadBundledDefinitions(String resource) throws IOException {
        try (InputStream stream = STEMCraft.getPlugin().getResource(resource)) {
            if (stream == null) throw new IOException("Missing resource " + resource);
            QuestDefinitionStore.load(new InputStreamReader(stream, StandardCharsets.UTF_8)).forEach(quests::putIfAbsent);
        }
    }

    private void loadBundledNpcProfiles(String resource) throws IOException {
        try (InputStream stream = STEMCraft.getPlugin().getResource(resource)) {
            if (stream == null) throw new IOException("Missing resource " + resource);
            QuestNpcProfileStore.load(new InputStreamReader(stream, StandardCharsets.UTF_8)).forEach(npcProfiles::putIfAbsent);
        }
    }

    private ItemStack createBook(Player owner, QuestDefinition quest, QuestProgress progress, int revision) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        meta.title(Component.text(renderQuestText(quest, quest.title())));
        meta.author(Component.text(quest.author()));
        meta.displayName(nonItalic(renderQuestText(quest, quest.title()), NamedTextColor.GOLD));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.lore(bookLore(owner, quest, progress));
        meta.addPages(bookPages(quest, progress, PlayerUtil.isBedrock(owner)).toArray(Component[]::new));
        meta.getPersistentDataContainer().set(questIdKey, PersistentDataType.STRING, quest.id());
        meta.getPersistentDataContainer().set(questOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        meta.getPersistentDataContainer().set(questRevisionKey, PersistentDataType.INTEGER, revision);
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> bookLore(Player player, QuestDefinition quest, QuestProgress progress) {
        List<Component> lore = new ArrayList<>();
        lore.add(nonItalic("Quest Book", NamedTextColor.YELLOW));
        lore.add(nonItalic("By " + quest.author(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        appendWrappedLore(lore, renderQuestText(quest, quest.shortDescription()), NamedTextColor.WHITE);
        lore.add(Component.empty());
        if (!quest.objectives().isEmpty()) {
            QuestObjective missing = progress.state() == QuestProgress.State.READY
                ? firstMissingCollectObjective(player, quest) : null;
            int index = Math.min(progress.objectiveIndex(), quest.objectives().size() - 1);
            QuestObjective objective = missing == null ? quest.objectives().get(index) : missing;
            int amount = missing != null
                ? Math.min(objective.amount(), countMaterial(player.getInventory(), Material.valueOf(objective.target())))
                : progress.state() == QuestProgress.State.READY ? objective.amount() : progress.objectiveProgress();
            lore.add(nonItalic(objectiveProgressName(quest, objective) + ": " + amount + "/" + objective.amount(), NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        appendWrappedLore(lore, "Rewards: " + renderQuestText(quest, quest.rewardText()), NamedTextColor.GREEN);
        return lore;
    }

    private void appendWrappedLore(List<Component> lore, String text, NamedTextColor color) {
        for (String line : wrapLoreText(text, 38)) lore.add(nonItalic(line, color));
    }

    static List<String> wrapLoreText(String text, int maxLength) {
        if (text == null || text.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.isEmpty()) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxLength) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private Component nonItalic(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> bookPages(QuestDefinition quest, QuestProgress progress, boolean bedrock) {
        List<Component> pages = new ArrayList<>();
        pages.add(Component.text(renderQuestText(quest, quest.title()) + "\n\n", NamedTextColor.BLACK, TextDecoration.BOLD)
            .append(Component.text(renderQuestText(quest, quest.description()), NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false))
            .append(quest.timeLimitSeconds() > 0 ? Component.text("\n\nTime limit: " + formatDuration(quest.timeLimitSeconds()), NamedTextColor.DARK_RED) : Component.empty()));
        Component objectives = Component.text("Objectives\n\n", NamedTextColor.BLACK, TextDecoration.BOLD);
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestObjective objective = quest.objectives().get(i);
            objectives = objectives.append(Component.text(renderQuestText(quest, objective.label()) + "\n", NamedTextColor.BLACK)
                .decoration(TextDecoration.BOLD, false));
        }
        Component rewards = Component.text("\nRewards\n", NamedTextColor.BLACK, TextDecoration.BOLD)
            .append(Component.text("You will receive:\n", NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
        if (quest.rewardItems().isEmpty()) rewards = rewards.append(Component.text("- "
            + renderQuestText(quest, quest.rewardText()) + "\n", NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
        else for (QuestRewardItem item : quest.rewardItems()) rewards = rewards.append(Component.text("- "
            + rewardItemName(item) + "\n", NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
        int experience = experienceReward(quest);
        if (experience > 0) rewards = rewards.append(Component.text("- " + experience + " XP\n", NamedTextColor.BLACK)
            .decoration(TextDecoration.BOLD, false));
        pages.add(objectives.append(rewards));
        pages.add(bookActions(quest.id(), renderQuestText(quest, quest.title()), bedrock));
        return pages;
    }

    static Component bookActions(String questId, String questTitle, boolean bedrock) {
        String trackCommand = "/quest track " + questId;
        String abandonCommand = "/quest abandon " + questId;
        Component actions = Component.text("Quest actions\n\n", NamedTextColor.BLACK, TextDecoration.BOLD);
        if (bedrock) {
            actions = actions.append(Component.text(trackCommand + "\n\n" + abandonCommand, NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, false));
        } else {
            actions = actions.append(Component.text("[Track]", NamedTextColor.DARK_GREEN)
                    .decoration(TextDecoration.BOLD, false)
                    .clickEvent(ClickEvent.runCommand(trackCommand))
                    .hoverEvent(HoverEvent.showText(Component.text("Track " + questTitle))))
                .append(Component.text("  "))
                .append(Component.text("[Abandon]", NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.BOLD, false)
                    .clickEvent(ClickEvent.runCommand(abandonCommand))
                    .hoverEvent(HoverEvent.showText(Component.text("Abandon " + questTitle))));
        }
        return actions;
    }

    private String objectiveProgressName(QuestDefinition quest, QuestObjective objective) {
        if ((objective.type() == QuestObjective.Type.COLLECT || objective.type() == QuestObjective.Type.KILL)
            && objective.target() != null && !objective.target().isBlank()) return friendlyName(objective.target());
        return objective.label() == null || objective.label().isBlank() ? "Progress" : renderQuestText(quest, objective.label());
    }

    private String rewardItemName(QuestRewardItem item) {
        String name = item.name() == null ? friendlyName(item.material().name()) : item.name();
        return item.amount() + " " + name + (item.name() != null || item.amount() == 1 || uncountable(item.material()) ? "" : "s");
    }

    static int experienceReward(QuestDefinition quest) {
        int total = 0;
        for (String command : quest.rewardCommands()) {
            Matcher matcher = EXPERIENCE_REWARD.matcher(command.trim());
            if (matcher.matches()) total += Integer.parseInt(matcher.group(1));
        }
        return total;
    }

    private String friendlyName(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private boolean uncountable(Material material) {
        return Set.of(Material.COD, Material.COOKED_COD, Material.SALMON, Material.COOKED_SALMON, Material.BREAD).contains(material);
    }


    private void updateQuestBook(Player player, QuestDefinition quest, QuestProgress progress) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (quest.id().equals(bookQuestId(item)) && player.getUniqueId().equals(bookOwner(item)) && isCurrentQuestBook(item))
                inventory.setItem(i, createBook(player, quest, progress, currentRevision(player.getUniqueId(), quest.id())));
        }
    }

    private ItemStack obfuscatedCopy(ItemStack item) {
        ItemStack copy = item.clone();
        if (!(copy.getItemMeta() instanceof BookMeta meta)) return copy;
        int shift = ThreadLocalRandom.current().nextInt(1, 26);
        List<Component> scrambledPages = meta.pages().stream()
            .map(PlainTextComponentSerializer.plainText()::serialize)
            .<Component>map(page -> Component.text(obfuscateBookText(page, shift), NamedTextColor.DARK_GRAY))
            .toList();
        meta.pages(scrambledPages);
        copy.setItemMeta(meta);
        return copy;
    }

    private void onQuestBookOpen(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack book = event.getItem();
        UUID owner = bookOwner(book);
        if (owner == null) return;
        if (!isCurrentQuestBook(book)) {
            event.setCancelled(true);
            event.getPlayer().getInventory().setItemInMainHand(null);
            questMessage(event.getPlayer(), "old-book-removed", "/warn/This old quest book fades away.");
            return;
        }
        if (owner.equals(event.getPlayer().getUniqueId())) {
            refreshOwnedBooks(event.getPlayer());
            return;
        }
        event.setCancelled(true);
        event.getPlayer().openBook(obfuscatedCopy(book));
    }

    private void validateInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (bookQuestId(item) != null && !isQuestMenuOffer(item) && !isCurrentQuestBook(item)) inventory.setItem(slot, null);
        }
    }

    private void refreshOwnedBooks(Player player) {
        for (QuestProgress progress : activeFor(player).values()) {
            QuestDefinition quest = quests.get(progress.questId());
            if (quest != null) updateQuestBook(player, quest, progress);
        }
    }

    static String obfuscateBookText(String text, int shift) {
        int letterShift = Math.floorMod(shift, 26);
        int numberShift = Math.floorMod(shift, 10);
        StringBuilder result = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if (character >= 'a' && character <= 'z') result.append((char) ('a' + (character - 'a' + letterShift) % 26));
            else if (character >= 'A' && character <= 'Z') result.append((char) ('A' + (character - 'A' + letterShift) % 26));
            else if (character >= '0' && character <= '9') result.append((char) ('0' + (character - '0' + numberShift) % 10));
            else result.append(character);
        }
        return result.toString();
    }

    private boolean cancelQuest(UUID player, String questId) {
        Map<String, QuestProgress> values = active.get(player);
        if (values == null || values.remove(questId) == null) return false;
        boolean wasTracked = questId.equals(trackedQuests.get(player));
        if (wasTracked) stopTracking(player);
        api.database().update("DELETE FROM quest_progress WHERE player_uuid=? AND quest_id=?", statement -> {
            statement.setString(1, player.toString()); statement.setString(2, questId);
        });
        Map<String, Long> timings = attemptStarted.get(player);
        if (timings != null) timings.remove(questId);
        Map<String, Long> countdowns = timedQuestRemaining.get(player);
        if (countdowns != null) countdowns.remove(questId);
        api.database().update("DELETE FROM quest_attempt_timing WHERE player_uuid=? AND quest_id=?", statement -> {
            statement.setString(1, player.toString()); statement.setString(2, questId);
        });
        if (wasTracked && isAutoTracking(player)) trackMostRecentQuest(player);
        return true;
    }

    private int globalCompletionCount(String questId) {
        Integer count = api.database().querySingleMapped("SELECT COUNT(*) AS total FROM quest_completed WHERE quest_id=?",
            statement -> statement.setString(1, questId), result -> result.getInt("total"));
        return count == null ? 0 : count;
    }

    private void expireTimedQuests() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<QuestDefinition> expired = new ArrayList<>();
            for (QuestProgress progress : activeFor(player).values()) {
                QuestDefinition quest = quests.get(progress.questId());
                long started = attemptStarted.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(progress.questId(), now);
                if (quest != null && quest.timeLimitSeconds() > 0) {
                    long remaining=Math.max(0L,(started+quest.timeLimitSeconds()*1000L-now+999L)/1000L);
                    announceTimedQuestCountdown(player,quest,remaining);
                    if(remaining==0)expired.add(quest);
                }
            }
            for (QuestDefinition quest : expired) failQuest(player, quest, "time-ran-out", "Time ran out.");
        }
    }

    private void announceTimedQuestCountdown(Player player,QuestDefinition quest,long remaining){
        Map<String,Long> values=timedQuestRemaining.computeIfAbsent(player.getUniqueId(),ignored->new HashMap<>());
        Long previous=values.put(quest.id(),remaining);if(previous==null)return;
        long threshold=countdownThresholdCrossed(quest.timeLimitSeconds(),previous,remaining);
        if(threshold<=0)return;
        boolean urgent = threshold <= 15;
        questMessage(player, urgent ? "countdown-urgent" : "countdown",
            urgent ? "<red>{quest}: {remaining} remaining.</red>" : "<yellow>{quest}: {remaining} remaining.</yellow>",
            "quest", quest.title(), "remaining", formatCountdownExact(threshold));
    }

    static long countdownThresholdCrossed(long total,long previous,long remaining){
        long crossed=-1;
        long highestQuarterHour=(total/900)*900;
        for(long threshold=highestQuarterHour;threshold>=900;threshold-=900)
            if(threshold<total&&previous>threshold&&remaining<=threshold)crossed=threshold;
        for(long threshold:new long[]{600,300,120,60,45,30,15,10,5})
            if(threshold<total&&previous>threshold&&remaining<=threshold)crossed=threshold;
        return crossed;
    }

    private long timedQuestSecondsRemaining(UUID playerId,QuestDefinition quest,long now){
        if(quest.timeLimitSeconds()<=0)return -1;
        Long started=attemptStarted.getOrDefault(playerId,Map.of()).get(quest.id());
        return started==null?-1:Math.max(0L,(started+quest.timeLimitSeconds()*1000L-now+999L)/1000L);
    }

    static String formatCountdownDisplay(long remaining){
        if(remaining>60)return ((remaining+59)/60)+" minutes";
        if(remaining==60)return "1 minute";
        if(remaining>45)return "1 minute";
        if(remaining>30)return "45 seconds";
        if(remaining>15)return "30 seconds";
        if(remaining>10)return "15 seconds";
        if(remaining>5)return "10 seconds";
        if(remaining>0)return "5 seconds";
        return "0 seconds";
    }

    private static String formatCountdownExact(long seconds){
        if(seconds>=60&&seconds%60==0)return (seconds/60)+" minute"+(seconds==60?"":"s");
        return seconds+" seconds";
    }

    private void failQuest(Player player, QuestDefinition quest, String reasonKey, String fallbackReason) {
        long failedAt = System.currentTimeMillis();
        String reason = questMessageText("failure-reasons." + reasonKey, fallbackReason);
        cancelQuest(player.getUniqueId(), quest.id());
        removeQuestBooks(player, quest.id());
        api.database().update("INSERT OR REPLACE INTO quest_failure(player_uuid,quest_id,failed_at,reason) VALUES(?,?,?,?)", statement -> {
            statement.setString(1, player.getUniqueId().toString()); statement.setString(2, quest.id());
            statement.setLong(3, failedAt); statement.setString(4, reason);
        });
        questMessage(player, "failed", "<red>{quest} failed. {reason}</red>",
            "quest", quest.title(), "reason", reason);
        refreshMarkers(player);
    }

    private String questMessageText(String key, String fallback) {
        return getConfigSection().getString("messages." + key, fallback);
    }

    private void questMessage(Player player, String key, String fallback, Object... placeholders) {
        api.messages().send(player, questMessageText(key, fallback), placeholders);
    }

    private String formatDuration(long seconds) {
        if (seconds % 86400 == 0) return (seconds / 86400) + " day" + (seconds == 86400 ? "" : "s");
        if (seconds % 3600 == 0) return (seconds / 3600) + " hour" + (seconds == 3600 ? "" : "s");
        if (seconds % 60 == 0) return (seconds / 60) + " minute" + (seconds == 60 ? "" : "s");
        return seconds + " seconds";
    }

    private void saveProgress(QuestProgress progress) {
        api.database().update("INSERT OR REPLACE INTO quest_progress(player_uuid,quest_id,objective_index,objective_progress,state) VALUES(?,?,?,?,?)", statement -> {
            statement.setString(1, progress.playerUuid().toString()); statement.setString(2, progress.questId());
            statement.setInt(3, progress.objectiveIndex()); statement.setInt(4, progress.objectiveProgress()); statement.setString(5, progress.state().name());
        });
    }

    private Map<String, QuestProgress> activeFor(Player player) { return active.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()); }
    private @Nullable QuestProgress progress(Player player, String questId) { return activeFor(player).get(questId); }
    private @Nullable QuestObjective currentObjective(@Nullable QuestDefinition quest, QuestProgress progress) {
        return quest == null || progress.state() == QuestProgress.State.READY || progress.objectiveIndex() >= quest.objectives().size()
            ? null : quest.objectives().get(progress.objectiveIndex());
    }

    private @Nullable QuestDefinition quest(String id, CommandContext ctx) {
        QuestDefinition value = id == null ? null : quests.get(normalizeId(id));
        if (value == null) ctx.error("Quest not found: {quest}", "quest", String.valueOf(id));
        return value;
    }

    private @Nullable Entity targetedEntity(CommandContext ctx) {
        if (!ctx.isPlayer()) return null;
        Entity entity = ctx.asPlayer().getTargetEntity(8);
        if (!(entity instanceof LivingEntity)) { ctx.error("Look directly at a living entity within 8 blocks."); return null; }
        return entity;
    }

    private String bookQuestId(@Nullable ItemStack item) {
        return item == null || !(item.getItemMeta() instanceof BookMeta meta) ? null
            : meta.getPersistentDataContainer().get(questIdKey, PersistentDataType.STRING);
    }

    private @Nullable UUID bookOwner(@Nullable ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BookMeta meta)) return null;
        String raw = meta.getPersistentDataContainer().get(questOwnerKey, PersistentDataType.STRING);
        try { return raw == null ? null : UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    private @Nullable Integer bookRevision(@Nullable ItemStack item) {
        return item == null || !(item.getItemMeta() instanceof BookMeta meta) ? null
            : meta.getPersistentDataContainer().get(questRevisionKey, PersistentDataType.INTEGER);
    }

    private int currentRevision(UUID player, String questId) {
        return revisions.getOrDefault(player, Map.of()).getOrDefault(questId, 0);
    }

    private void saveRevision(UUID player, String questId, int revision) {
        revisions.computeIfAbsent(player, ignored -> new HashMap<>()).put(questId, revision);
        api.database().update("INSERT OR REPLACE INTO quest_attempt_revision(player_uuid,quest_id,revision) VALUES(?,?,?)", statement -> {
            statement.setString(1, player.toString()); statement.setString(2, questId); statement.setInt(3, revision);
        });
    }

    private boolean isCurrentQuestBook(@Nullable ItemStack item) {
        String questId = bookQuestId(item);
        UUID owner = bookOwner(item);
        if (questId == null || owner == null) return true;
        QuestProgress progress = active.getOrDefault(owner, Map.of()).get(questId);
        if (progress == null) return false;
        int current = currentRevision(owner, questId);
        Integer bookRevision = bookRevision(item);
        return bookRevision != null ? bookRevision == current : current == 1;
    }

    private boolean hasOwnedQuestBook(Player player, String questId) {
        return ownedQuestBook(player, questId) != null;
    }

    private @Nullable ItemStack ownedQuestBook(Player player, String questId) {
        for (ItemStack item : player.getInventory().getContents())
            if (questId.equals(bookQuestId(item)) && player.getUniqueId().equals(bookOwner(item)) && isCurrentQuestBook(item)) return item;
        return null;
    }

    private void removeQuestBooks(Player player, String questId) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (questId.equals(bookQuestId(item)) && player.getUniqueId().equals(bookOwner(item))) inventory.setItem(i, null);
        }
    }

    static int countMaterial(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }

    static void removeMaterial(PlayerInventory inventory, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.getStorageContents().length && remaining > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() != material) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
    }

    static double distanceSquared(Location location, QuestObjective objective) {
        double dx = location.getX() - objective.x();
        double dy = location.getY() - objective.y();
        double dz = location.getZ() - objective.z();
        return dx * dx + dy * dy + dz * dz;
    }

    static String normalizeId(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
    }

    private static String join(List<String> values, int start) { return String.join(" ", values.subList(start, values.size())); }
    private static int countMaterial(PlayerInventory inventory, String material) { return countMaterial(inventory, Material.valueOf(material)); }
}
