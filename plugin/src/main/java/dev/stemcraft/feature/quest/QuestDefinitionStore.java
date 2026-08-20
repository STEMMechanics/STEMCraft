package dev.stemcraft.feature.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** YAML persistence for administrator-managed quest definitions. */
public final class QuestDefinitionStore {
    private QuestDefinitionStore() { }

    public static Map<String, QuestDefinition> load(File file) {
        return load(YamlConfiguration.loadConfiguration(file));
    }

    public static Map<String, QuestDefinition> load(Reader reader) {
        return load(YamlConfiguration.loadConfiguration(reader));
    }

    private static Map<String, QuestDefinition> load(YamlConfiguration yaml) {
        Map<String, QuestDefinition> result = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("quests");
        if (root == null) return result;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            QuestDefinition quest = new QuestDefinition(id, section.getString("title", id));
            quest.author(section.getString("author", "STEMCraft"));
            quest.description(section.getString("description", "A mysterious task awaits."));
            quest.shortDescription(section.getString("short-description"));
            quest.rewardText(section.getString("rewards.description", "A reward awaits on completion."));
            quest.enabled(section.getBoolean("enabled", true));
            quest.repeatable(section.getBoolean("repeatable", false));
            quest.timeLimitSeconds(section.getLong("time-limit-seconds", 0));
            quest.restartCooldownSeconds(section.getLong("restart-cooldown-seconds", 0));
            quest.globalMaxCompletions(section.getInt("global-max-completions", 0));
            quest.startNpc(parseUuid(section.getString("start-npc")));
            quest.endNpc(parseUuid(section.getString("end-npc")));
            quest.startNpcProfile(nullableString(section.get("start-npc-profile")));
            quest.endNpcProfile(nullableString(section.get("end-npc-profile")));
            quest.startNpcName(section.getString("start-npc-name", "Quest Giver"));
            quest.endNpcName(section.getString("end-npc-name", "Quest Contact"));
            ConfigurationSection dialogue = section.getConfigurationSection("dialogue");
            if (dialogue != null) {
                for (String state : dialogue.getKeys(false)) {
                    quest.dialogue(state).clear();
                    quest.dialogue(state).addAll(dialogue.getStringList(state));
                }
            }
            quest.requirements().addAll(section.getStringList("requires"));
            quest.rewardCommands().addAll(section.getStringList("rewards.commands"));
            for (Map<?, ?> raw : section.getMapList("rewards.items")) {
                try {
                    Material material = Material.matchMaterial(string(raw, "material", ""));
                    Object rawLore = raw.get("lore");
                    java.util.List<String> lore = rawLore instanceof java.util.List<?> values
                        ? values.stream().map(String::valueOf).toList() : java.util.List.of();
                    quest.rewardItems().add(new QuestRewardItem(material, integer(raw, "amount", 1),
                        nullableString(raw.get("name")), lore, bool(raw, "unbreakable", false)));
                } catch (IllegalArgumentException ignored) { }
            }

            for (Map<?, ?> raw : section.getMapList("objectives")) {
                try {
                    QuestObjective.Type type = QuestObjective.Type.valueOf(string(raw, "type", "").toUpperCase());
                    quest.objectives().add(new QuestObjective(
                        type,
                        string(raw, "target", ""),
                        integer(raw, "amount", 1),
                        bool(raw, "consume", false),
                        string(raw, "label", ""),
                        nullableString(raw.get("world")),
                        decimal(raw, "x", 0), decimal(raw, "y", 0), decimal(raw, "z", 0),
                        decimal(raw, "radius", 2)
                    ));
                } catch (IllegalArgumentException ignored) {
                    // Keep loading valid quests when an administrator has one malformed objective.
                }
            }
            result.put(id, quest);
        }
        return result;
    }

    public static void save(File file, Map<String, QuestDefinition> quests) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (QuestDefinition quest : quests.values()) {
            String path = "quests." + quest.id();
            yaml.set(path + ".title", quest.title());
            yaml.set(path + ".author", quest.author());
            yaml.set(path + ".description", quest.description());
            yaml.set(path + ".short-description", quest.shortDescription());
            yaml.set(path + ".rewards.description", quest.rewardText());
            yaml.set(path + ".enabled", quest.enabled());
            yaml.set(path + ".repeatable", quest.repeatable());
            yaml.set(path + ".time-limit-seconds", quest.timeLimitSeconds());
            yaml.set(path + ".restart-cooldown-seconds", quest.restartCooldownSeconds());
            yaml.set(path + ".global-max-completions", quest.globalMaxCompletions());
            yaml.set(path + ".start-npc", quest.startNpc() == null ? null : quest.startNpc().toString());
            yaml.set(path + ".end-npc", quest.endNpc() == null ? null : quest.endNpc().toString());
            yaml.set(path + ".start-npc-profile", quest.startNpcProfile());
            yaml.set(path + ".end-npc-profile", quest.endNpcProfile());
            yaml.set(path + ".start-npc-name", quest.startNpcName());
            yaml.set(path + ".end-npc-name", quest.endNpcName());
            quest.dialogue().forEach((state, lines) -> yaml.set(path + ".dialogue." + state, lines));
            yaml.set(path + ".requires", quest.requirements().stream().toList());
            yaml.set(path + ".rewards.commands", quest.rewardCommands());
            yaml.set(path + ".rewards.items", quest.rewardItems().stream().map(item -> {
                Map<String, Object> reward = new LinkedHashMap<>();
                reward.put("material", item.material().name()); reward.put("amount", item.amount());
                if (item.name() != null) reward.put("name", item.name());
                if (!item.lore().isEmpty()) reward.put("lore", item.lore());
                if (item.unbreakable()) reward.put("unbreakable", true);
                return reward;
            }).toList());
            yaml.set(path + ".objectives", quest.objectives().stream().map(QuestDefinitionStore::serialize).toList());
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        yaml.save(file);
    }

    private static Map<String, Object> serialize(QuestObjective objective) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", objective.type().name().toLowerCase());
        if (!objective.target().isBlank()) value.put("target", objective.target());
        value.put("amount", objective.amount());
        if (objective.consume()) value.put("consume", true);
        value.put("label", objective.label());
        if (objective.world() != null) {
            value.put("world", objective.world());
            value.put("x", objective.x());
            value.put("y", objective.y());
            value.put("z", objective.z());
            value.put("radius", objective.radius());
        }
        return value;
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String nullableString(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double decimal(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }
}
