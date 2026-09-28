package dev.stemcraft.minigame;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MiniGameHudConfigSupport {
    private MiniGameHudConfigSupport() {
    }

    /**
     * Default HUD for a timed match. Adopters register objective, winner and progress placeholders;
     * apply() preserves any administrator-provided HUD lines in the game's own config.
     */
    public static @NotNull Map<MiniGameArena.ArenaStatus, HudDefinition> timedRoundDefaults(@NotNull String title) {
        Map<MiniGameArena.ArenaStatus, HudDefinition> definitions = new LinkedHashMap<>();
        for (MiniGameArena.ArenaStatus status : List.of(MiniGameArena.ArenaStatus.WAITING, MiniGameArena.ArenaStatus.STARTING,
                MiniGameArena.ArenaStatus.RUNNING, MiniGameArena.ArenaStatus.ENDING)) {
            definitions.put(status, new HudDefinition(
                    List.of("<gold>" + title + " <white>{arena:time-remaining}", "<yellow>{arena:objective}"),
                    List.of("<gold>{arena:name}", "<white>{arena:status}", "Players: {arena:players}",
                            "Time: {arena:time-remaining}", "Score: {player:score}", "{player:progress}", "Winner: {arena:winner}"), 2, "YELLOW"));
        }
        return definitions;
    }

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

            List<String> bossbarLines = resolveLines(bossbarSection, definition.bossBarLines());
            List<String> scoreboardLines = resolveLines(scoreboardSection, definition.scoreboardLines());
            int holdUpdates = resolveHoldUpdates(bossbarSection, definition.bossBarLineHoldUpdates());
            String bossBarColor = resolveString(bossbarSection, definition.bossBarColor());

            minigame.registerHud(status, bossbarLines, scoreboardLines, holdUpdates, bossBarColor);
        }

        config.save();
    }

    private static @NotNull List<String> resolveLines(@NotNull ConfigSection section, @NotNull List<String> defaults) {
        if (!section.contains("lines")) {
            section.set("lines", defaults);
        }
        return List.copyOf(section.getStringList("lines"));
    }

    private static int resolveHoldUpdates(@NotNull ConfigSection section, int defaults) {
        if (!section.contains("hold-updates")) {
            section.set("hold-updates", defaults);
        }
        return Math.max(1, section.getInt("hold-updates", defaults));
    }

    private static @NotNull String resolveString(@NotNull ConfigSection section, @NotNull String defaults) {
        if (!section.contains("color")) {
            section.set("color", defaults);
        }
        return section.getString("color", defaults);
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
