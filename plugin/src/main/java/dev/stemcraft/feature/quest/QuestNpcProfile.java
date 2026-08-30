package dev.stemcraft.feature.quest;

import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reusable quest character and its conditional roaming spawn rules. */
public final class QuestNpcProfile {
    public enum Behaviour { STATIONARY, WANDER }

    public static final int DEFAULT_WANDER_RADIUS = 8;
    public static final int DEFAULT_WANDER_VERTICAL_RADIUS = 3;
    public static final int DEFAULT_WANDER_DELAY_SECONDS = 5;

    private final String id;
    private String name;
    private EntityType npcType = EntityType.VILLAGER;
    private String world = "survival";
    private final List<String> worlds = new ArrayList<>();
    private int minDistance = 30;
    private int maxDistance = 100;
    private int uniquenessRadius = 1200;
    private int despawnRadius = 150;
    private int minimumLevel;
    private long timeFrom;
    private long timeUntil = 24000;
    private double dailyChance = 1.0D;
    private Behaviour behaviour = Behaviour.WANDER;
    private int wanderRadius = DEFAULT_WANDER_RADIUS;
    private int wanderVerticalRadius = DEFAULT_WANDER_VERTICAL_RADIUS;
    private int wanderDelaySeconds = DEFAULT_WANDER_DELAY_SECONDS;
    private boolean lookAtPlayers = true;
    private boolean invulnerable;
    private String skinUrl;
    private Integer citizensNpcId;
    private long lifetimeSeconds;
    private long spawnedAt;
    private UUID spawnedEntity;
    private final List<String> biomes = new ArrayList<>();
    private final List<String> idleDialogue = new ArrayList<>();
    private final List<String> leavingDialogue = new ArrayList<>();

    public QuestNpcProfile(String id, String name) { this.id = id; this.name = name; }
    public String id() { return id; }
    public String name() { return name; }
    public void name(String value) { name = value; }
    public EntityType npcType() { return npcType; }
    public void npcType(EntityType value) { npcType = value; }
    /** Compatibility for the current web editor payload. */
    public EntityType entityType() { return npcType; }
    /** Compatibility for the current web editor payload. */
    public void entityType(EntityType value) { npcType(value); }
    public String world() { return world; }
    public void world(String value) { world = value; }
    public List<String> worlds() { return worlds.isEmpty() ? List.of(world) : List.copyOf(worlds); }
    public void worlds(List<String> values) {
        worlds.clear();
        if (values != null) values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim)
            .distinct().forEach(worlds::add);
        if (!worlds.isEmpty()) world = worlds.getFirst();
    }
    public boolean supportsWorld(String value) { return worlds().stream().anyMatch(world -> world.equalsIgnoreCase(value)); }
    public int minDistance() { return minDistance; }
    public void minDistance(int value) { minDistance = Math.max(8, value); }
    public int maxDistance() { return maxDistance; }
    public void maxDistance(int value) { maxDistance = Math.max(minDistance, value); }
    public int uniquenessRadius() { return uniquenessRadius; }
    public void uniquenessRadius(int value) { uniquenessRadius = Math.max(1, value); }
    public int despawnRadius() { return despawnRadius; }
    public void despawnRadius(int value) { despawnRadius = Math.max(16, value); }
    public int minimumLevel() { return minimumLevel; }
    public void minimumLevel(int value) { minimumLevel = Math.max(0, value); }
    public long timeFrom() { return timeFrom; }
    public void timeFrom(long value) { timeFrom = Math.floorMod(value, 24000); }
    public long timeUntil() { return timeUntil; }
    public void timeUntil(long value) { timeUntil = value == 24000 ? 24000 : Math.floorMod(value, 24000); }
    public double dailyChance() { return dailyChance; }
    public void dailyChance(double value) { dailyChance = Math.max(0, Math.min(1, value)); }
    public Behaviour behaviour() { return behaviour; }
    public void behaviour(Behaviour value) { behaviour = value; }
    /** Compatibility for the current web editor payload. */
    public boolean ai() { return behaviour == Behaviour.WANDER; }
    /** Compatibility for the current web editor payload. */
    public void ai(boolean value) { behaviour = value ? Behaviour.WANDER : Behaviour.STATIONARY; }
    public int wanderRadius() { return wanderRadius; }
    public void wanderRadius(int value) { wanderRadius = Math.max(1, value); }
    public int wanderVerticalRadius() { return wanderVerticalRadius; }
    public void wanderVerticalRadius(int value) { wanderVerticalRadius = Math.max(1, value); }
    public int wanderDelaySeconds() { return wanderDelaySeconds; }
    public void wanderDelaySeconds(int value) { wanderDelaySeconds = Math.max(0, value); }
    public boolean lookAtPlayers() { return lookAtPlayers; }
    public void lookAtPlayers(boolean value) { lookAtPlayers = value; }
    public boolean invulnerable() { return invulnerable; }
    public void invulnerable(boolean value) { invulnerable = value; }
    public String skinUrl() { return skinUrl; }
    public void skinUrl(String value) { skinUrl = value == null || value.isBlank() ? null : value.trim(); }
    public Integer citizensNpcId() { return citizensNpcId; }
    public void citizensNpcId(Integer value) { citizensNpcId = value; }
    public long lifetimeSeconds() { return lifetimeSeconds; }
    public void lifetimeSeconds(long value) { lifetimeSeconds = Math.max(0, value); }
    public long spawnedAt() { return spawnedAt; }
    public void spawnedAt(long value) { spawnedAt = Math.max(0, value); }
    public UUID spawnedEntity() { return spawnedEntity; }
    public void spawnedEntity(UUID value) { spawnedEntity = value; }
    public List<String> biomes() { return biomes; }
    public List<String> idleDialogue() { return idleDialogue; }
    public List<String> leavingDialogue() { return leavingDialogue; }
}
