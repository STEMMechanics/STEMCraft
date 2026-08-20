package dev.stemcraft.feature.quest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/** One ordered objective in a quest. */
public record QuestObjective(Type type, String target, int amount, boolean consume, String label,
                             @Nullable String world, double x, double y, double z, double radius) {
    public static final int DEFAULT_BIOME_STAY_SECONDS = 10;
    public enum Type { COLLECT, KILL, LOCATION, NPC, BIOME, ALTITUDE_ABOVE, ALTITUDE_BELOW, STRUCTURE, SLEEP, INTERACT, UNDERWATER, NIGHT }

    public QuestObjective {
        amount = type == Type.BIOME && amount <= 1 ? DEFAULT_BIOME_STAY_SECONDS : Math.max(1, amount);
        radius = Math.max(0.5D, radius);
        label = label == null || label.isBlank() ? defaultLabel(type, target, amount) : label;
    }

    public static QuestObjective collect(Material material, int amount, boolean consume, String label) {
        return new QuestObjective(Type.COLLECT, material.name(), amount, consume, label, null, 0, 0, 0, 1);
    }

    public static QuestObjective kill(EntityType type, int amount, String label) {
        return new QuestObjective(Type.KILL, type.name(), amount, false, label, null, 0, 0, 0, 1);
    }

    public static QuestObjective location(Location location, double radius, String label) {
        return new QuestObjective(Type.LOCATION, "", 1, false, label, location.getWorld().getName(),
            location.getX(), location.getY(), location.getZ(), radius);
    }

    public static QuestObjective npc(UUID entityUuid, String label) {
        return new QuestObjective(Type.NPC, entityUuid.toString(), 1, false, label, null, 0, 0, 0, 1);
    }

    public String displayTarget() {
        return switch (type) {
            case COLLECT, KILL, BIOME, STRUCTURE, INTERACT -> target.toLowerCase(Locale.ROOT).replace('_', ' ');
            case LOCATION, NPC, ALTITUDE_ABOVE, ALTITUDE_BELOW, SLEEP, UNDERWATER, NIGHT -> label;
        };
    }

    private static String defaultLabel(Type type, String target, int amount) {
        return switch (type) {
            case COLLECT -> "Collect " + amount + " " + target.toLowerCase(Locale.ROOT).replace('_', ' ');
            case KILL -> "Defeat " + amount + " " + target.toLowerCase(Locale.ROOT).replace('_', ' ');
            case LOCATION -> "Visit the marked location";
            case NPC -> "Speak to the quest contact";
            case BIOME -> "Explore a " + target.toLowerCase(Locale.ROOT).replace('_', ' ') + " biome";
            case ALTITUDE_ABOVE -> "Climb above Y " + target;
            case ALTITUDE_BELOW -> "Descend below Y " + target;
            case STRUCTURE -> "Discover a " + target.toLowerCase(Locale.ROOT).replace('_', ' ');
            case SLEEP -> "Sleep through the night";
            case INTERACT -> "Interact with a " + target.toLowerCase(Locale.ROOT).replace('_', ' ');
            case UNDERWATER -> "Dive underwater below Y " + target;
            case NIGHT -> "Venture outside at night";
        };
    }
}
