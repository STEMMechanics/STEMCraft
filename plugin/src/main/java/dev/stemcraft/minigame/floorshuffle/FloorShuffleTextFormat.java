package dev.stemcraft.minigame.floorshuffle;

import dev.stemcraft.api.config.ConfigSection;
import org.bukkit.Material;

record FloorShuffleTextFormat(String chat, String title, String subtitle, String objective) {
    static FloorShuffleTextFormat defaults() {
        return new FloorShuffleTextFormat(
                "<{colour-tag}>Stand on {colour}!</{colour-tag}>",
                "<{colour-tag}><bold>{colour}</bold></{colour-tag}>",
                "<{colour-tag}>Stand on this colour!</{colour-tag}>",
                "<{colour-tag}>Stand on {colour}!</{colour-tag}>");
    }

    static FloorShuffleTextFormat load(ConfigSection config) {
        ConfigSection section = config.getSection("strings.colour");
        FloorShuffleTextFormat defaults = defaults();
        if (!section.contains("chat")) section.set("chat", defaults.chat());
        if (!section.contains("title")) section.set("title", defaults.title());
        if (!section.contains("subtitle")) section.set("subtitle", defaults.subtitle());
        if (!section.contains("objective")) section.set("objective", defaults.objective());
        return new FloorShuffleTextFormat(section.getString("chat"), section.getString("title"),
                section.getString("subtitle"), section.getString("objective"));
    }

    static String render(String template, Material colour) {
        String tag = switch (colour) {
            case RED_CONCRETE -> "#ff5555";
            case BLUE_CONCRETE -> "#5555ff";
            case YELLOW_CONCRETE -> "#ffff55";
            case LIME_CONCRETE -> "#55ff55";
            case ORANGE_CONCRETE -> "#ffaa00";
            case PURPLE_CONCRETE -> "#aa00aa";
            default -> throw new IllegalArgumentException("Unsupported floor colour: " + colour);
        };
        return template.replace("{colour}", colour.name().replace("_CONCRETE", ""))
                .replace("{colour-tag}", tag);
    }
}
