package dev.stemcraft.feature.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** YAML persistence for reusable quest NPC profiles. */
public final class QuestNpcProfileStore {
    private QuestNpcProfileStore() { }

    public static Map<String, QuestNpcProfile> load(File file) {
        return load(YamlConfiguration.loadConfiguration(file));
    }

    public static Map<String, QuestNpcProfile> load(Reader reader) {
        return load(YamlConfiguration.loadConfiguration(reader));
    }

    private static Map<String, QuestNpcProfile> load(YamlConfiguration yaml) {
        Map<String, QuestNpcProfile> result = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root == null) return result;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            QuestNpcProfile profile = new QuestNpcProfile(id, section.getString("name", id));
            String configuredType = section.getString("npc-type", section.getString("entity-type", "VILLAGER"));
            try { profile.npcType(EntityType.valueOf(configuredType.toUpperCase(java.util.Locale.ROOT))); } catch (IllegalArgumentException ignored) { }
            profile.world(section.getString("spawn.world", "survival"));
            if (section.isList("spawn.worlds")) profile.worlds(section.getStringList("spawn.worlds"));
            profile.minDistance(section.getInt("spawn.min-distance", 30));
            profile.maxDistance(section.getInt("spawn.max-distance", 100));
            profile.uniquenessRadius(section.getInt("spawn.uniqueness-radius", 1200));
            profile.despawnRadius(section.getInt("despawn-radius", 150));
            profile.minimumLevel(section.getInt("spawn.minimum-level", 0));
            profile.timeFrom(section.getLong("spawn.time-from", 0));
            profile.timeUntil(section.getLong("spawn.time-until", 24000));
            profile.dailyChance(section.getDouble("spawn.daily-chance", 1));
            String legacyBehaviour = section.getBoolean("ai", true) ? "WANDER" : "STATIONARY";
            try { profile.behaviour(QuestNpcProfile.Behaviour.valueOf(section.getString("behaviour.type", legacyBehaviour).toUpperCase(java.util.Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { }
            profile.wanderRadius(section.getInt("behaviour.wander-radius", QuestNpcProfile.DEFAULT_WANDER_RADIUS));
            profile.wanderVerticalRadius(section.getInt("behaviour.wander-vertical-radius", QuestNpcProfile.DEFAULT_WANDER_VERTICAL_RADIUS));
            profile.wanderDelaySeconds(section.getInt("behaviour.wander-delay-seconds", QuestNpcProfile.DEFAULT_WANDER_DELAY_SECONDS));
            profile.lookAtPlayers(section.getBoolean("behaviour.look-at-players", true));
            profile.invulnerable(section.getBoolean("invulnerable", false));
            profile.skinUrl(section.getString("skin.url"));
            if (section.contains("citizens-npc-id")) profile.citizensNpcId(section.getInt("citizens-npc-id"));
            profile.lifetimeSeconds(section.getLong("lifetime-seconds", 0));
            profile.spawnedAt(section.getLong("spawned-at", 0));
            profile.biomes().addAll(section.getStringList("spawn.biomes"));
            profile.idleDialogue().addAll(section.getStringList("dialogue.idle"));
            profile.leavingDialogue().addAll(section.getStringList("dialogue.leaving"));
            try { profile.spawnedEntity(UUID.fromString(section.getString("spawned-entity", ""))); } catch (IllegalArgumentException ignored) { }
            result.put(id, profile);
        }
        return result;
    }

    public static void save(File file, Map<String, QuestNpcProfile> profiles) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (QuestNpcProfile profile : profiles.values()) {
            String path = "npcs." + profile.id();
            yaml.set(path + ".name", profile.name());
            yaml.set(path + ".npc-type", profile.npcType().name());
            if (profile.behaviour() != QuestNpcProfile.Behaviour.WANDER)
                yaml.set(path + ".behaviour.type", profile.behaviour().name());
            if (profile.wanderRadius() != QuestNpcProfile.DEFAULT_WANDER_RADIUS)
                yaml.set(path + ".behaviour.wander-radius", profile.wanderRadius());
            if (profile.wanderVerticalRadius() != QuestNpcProfile.DEFAULT_WANDER_VERTICAL_RADIUS)
                yaml.set(path + ".behaviour.wander-vertical-radius", profile.wanderVerticalRadius());
            if (profile.wanderDelaySeconds() != QuestNpcProfile.DEFAULT_WANDER_DELAY_SECONDS)
                yaml.set(path + ".behaviour.wander-delay-seconds", profile.wanderDelaySeconds());
            if (!profile.lookAtPlayers()) yaml.set(path + ".behaviour.look-at-players", false);
            if (profile.invulnerable()) yaml.set(path + ".invulnerable", true);
            if (profile.skinUrl() != null) yaml.set(path + ".skin.url", profile.skinUrl());
            if (profile.citizensNpcId() != null) yaml.set(path + ".citizens-npc-id", profile.citizensNpcId());
            yaml.set(path + ".lifetime-seconds", profile.lifetimeSeconds());
            yaml.set(path + ".spawned-at", profile.spawnedAt());
            yaml.set(path + ".despawn-radius", profile.despawnRadius());
            if (profile.worlds().size() > 1) yaml.set(path + ".spawn.worlds", profile.worlds());
            else yaml.set(path + ".spawn.world", profile.world());
            yaml.set(path + ".spawn.min-distance", profile.minDistance());
            yaml.set(path + ".spawn.max-distance", profile.maxDistance());
            yaml.set(path + ".spawn.uniqueness-radius", profile.uniquenessRadius());
            yaml.set(path + ".spawn.minimum-level", profile.minimumLevel());
            yaml.set(path + ".spawn.time-from", profile.timeFrom());
            yaml.set(path + ".spawn.time-until", profile.timeUntil());
            yaml.set(path + ".spawn.daily-chance", profile.dailyChance());
            yaml.set(path + ".spawn.biomes", profile.biomes());
            yaml.set(path + ".dialogue.idle", profile.idleDialogue());
            if (!profile.leavingDialogue().isEmpty()) yaml.set(path + ".dialogue.leaving", profile.leavingDialogue());
            yaml.set(path + ".spawned-entity", profile.spawnedEntity() == null ? null : profile.spawnedEntity().toString());
        }
        if (file.getParentFile() != null && !file.getParentFile().exists() && !file.getParentFile().mkdirs()) throw new IOException("Could not create profile directory");
        yaml.save(file);
    }
}
