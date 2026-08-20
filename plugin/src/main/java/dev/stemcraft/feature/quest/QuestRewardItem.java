package dev.stemcraft.feature.quest;

import org.bukkit.Material;
import java.util.List;

/** A structured quest item reward that can be delivered safely. */
public record QuestRewardItem(Material material, int amount, String name, List<String> lore, boolean unbreakable) {
    public QuestRewardItem(Material material, int amount) { this(material, amount, null, List.of(), false); }
    public QuestRewardItem {
        if (material == null || !material.isItem()) throw new IllegalArgumentException("Reward material must be an item");
        if (amount < 1) throw new IllegalArgumentException("Reward amount must be positive");
        name = name == null || name.isBlank() ? null : name.trim();
        lore = lore == null ? List.of() : List.copyOf(lore);
    }
}
