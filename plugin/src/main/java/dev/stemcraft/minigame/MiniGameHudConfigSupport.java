package dev.stemcraft.minigame;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MiniGameHudConfigSupport {
    private MiniGameHudConfigSupport() {}

    public static void apply(
        @NotNull MiniGame minigame,
        @NotNull ConfigSection config,
        @NotNull Map<MiniGameArena.ArenaStatus, HudDefinition> defaults
    ) {
        ConfigSection hudSection = config.getSection("hud");

        for (Map.Entry<MiniGameArena.ArenaStatus, HudDefinition> entry : defaults.entrySet()) {
            MiniGameArena.ArenaStatus status = entry.getKey();
            HudDefinition definition = entry.getValue();

            String statusKey = status.name().toLowerCase(Locale.ROOT);
            ConfigSection statusSection = hudSection.getSection(statusKey);
            ConfigSection bossbarSection = statusSection.getSection("bossbar");
            ConfigSection scoreboardSection = statusSection.getSection("scoreboard");

            List<String> bossbarLines = resolveLines(bossbarSection, "lines", definition.bossBarLines());
            List<String> scoreboardLines = resolveLines(scoreboardSection, "lines", definition.scoreboardLines());
            int holdUpdates = resolveHoldUpdates(bossbarSection, definition.bossBarLineHoldUpdates());
            String bossBarColor = resolveString(bossbarSection, "color", definition.bossBarColor());

            minigame.registerHud(status, bossbarLines, scoreboardLines, holdUpdates, bossBarColor);
        }

        config.save();
    }

    private static @NotNull List<String> resolveLines(@NotNull ConfigSection section, @NotNull String path, @NotNull List<String> defaults) {
        if (!section.contains(path)) {
            section.set(path, defaults);
        }
        return List.copyOf(section.getStringList(path));
    }

    private static int resolveHoldUpdates(@NotNull ConfigSection section, int defaults) {
        if (!section.contains("hold-updates")) {
            section.set("hold-updates", defaults);
        }
        return Math.max(1, section.getInt("hold-updates", defaults));
    }

    private static @NotNull String resolveString(@NotNull ConfigSection section, @NotNull String path, @NotNull String defaults) {
        if (!section.contains(path)) {
            section.set(path, defaults);
        }
        return section.getString(path, defaults);
    }

    public record HudDefinition(
        @NotNull List<String> bossBarLines,
        @NotNull List<String> scoreboardLines,
        int bossBarLineHoldUpdates,
        @NotNull String bossBarColor
    ) {
        public HudDefinition {
            bossBarLines = List.copyOf(bossBarLines);
            scoreboardLines = List.copyOf(scoreboardLines);
            bossBarLineHoldUpdates = Math.max(1, bossBarLineHoldUpdates);
            bossBarColor = bossBarColor.isBlank() ? "PURPLE" : bossBarColor;
        }

        public HudDefinition(@NotNull List<String> bossBarLines, @NotNull List<String> scoreboardLines, int bossBarLineHoldUpdates) {
            this(bossBarLines, scoreboardLines, bossBarLineHoldUpdates, "PURPLE");
        }

        public HudDefinition(@NotNull List<String> bossBarLines, @NotNull List<String> scoreboardLines) {
            this(bossBarLines, scoreboardLines, 1, "PURPLE");
        }
    }
}
