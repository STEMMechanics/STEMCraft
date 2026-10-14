package dev.stemcraft.minigame;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MiniGameHudConfigSupport {
    private MiniGameHudConfigSupport() {
    }

    /** State-specific timed match HUD, styled consistently with BedWars and Nightfall. */
    public static @NotNull Map<MiniGameArena.ArenaStatus, HudDefinition> timedRoundDefaults(
            @NotNull String title, @NotNull List<String> runningDetails) {
        String heading = "<gold>" + title + ":</gold> <white>{arena:name}</white>";
        String players = ":info_green: <white>Players</white> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>";
        String needed = ":info_purple: <white>Need</white> <yellow>{arena:min-players}</yellow> <white>players</white>";
        String starting = ":clock: <gold>Starting in</gold> <yellow>{arena:time-remaining}</yellow>";
        String time = ":clock: <white>Time Left</white> <gold>{arena:time-remaining}</gold>";
        String winner = ":crown: <white>Winner</white> <yellow>{arena:winner}</yellow>";
        String reset = ":clock: <white>Reset In</white> <yellow>{arena:time-remaining}</yellow>";
        Map<MiniGameArena.ArenaStatus, HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new HudDefinition(
                List.of(heading, ":info_blue: <aqua>Waiting for players</aqua>",
                        ":info_blue: <aqua>Require {arena:min-players} players to start</aqua>"),
                List.of(heading, "", ":info_green: <white>Joined</white> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>", needed), 5));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new HudDefinition(
                List.of(heading, starting, players), List.of(heading, "", starting, players, needed), 5));
        List<String> runningBossbar = new ArrayList<>(List.of(heading));
        runningBossbar.addAll(runningDetails);
        runningBossbar.add(time);
        List<String> runningScoreboard = new ArrayList<>(List.of(heading, ""));
        runningScoreboard.addAll(runningDetails);
        runningScoreboard.add("");
        runningScoreboard.add(time);
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new HudDefinition(runningBossbar, runningScoreboard, 5));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new HudDefinition(
                List.of(heading, winner, reset), List.of(heading, "", winner, "", reset), 5));
        return definitions;
    }

    /** Upgrade generated timed-match defaults, preserving custom lines and display settings. */
    public static void applyTimedRound(@NotNull MiniGame minigame, @NotNull ConfigSection config,
                                      @NotNull String title, @NotNull List<String> runningDetails) {
        Map<MiniGameArena.ArenaStatus, HudDefinition> defaults = timedRoundDefaults(title, runningDetails);
        List<String> oldBossbar = List.of("<gold>" + title + " <white>{arena:time-remaining}", "<yellow>{arena:objective}");
        List<String> oldScoreboard = List.of("<gold>{arena:name}", "<white>{arena:status}", "Players: {arena:players}",
                "Time: {arena:time-remaining}", "Score: {player:score}", "{player:progress}", "Winner: {arena:winner}");
        ConfigSection hud = config.getSection("hud");
        boolean repairEmptyHud = !hud.contains("timed-round-version") && hasBrokenEmptyHud(hud, defaults);
        for (var entry : defaults.entrySet()) {
            ConfigSection status = config.getSection("hud." + entry.getKey().name().toLowerCase(Locale.ROOT));
            ConfigSection bossbar = status.getSection("bossbar");
            if (repairEmptyHud || (bossbar.contains("lines") && bossbar.getStringList("lines").equals(oldBossbar))) {
                bossbar.set("lines", entry.getValue().bossBarLines());
                if (bossbar.getInt("hold-updates", 2) == 2) {
                    bossbar.set("hold-updates", entry.getValue().bossBarLineHoldUpdates());
                }
                if (bossbar.getString("color", "YELLOW").equals("YELLOW")) {
                    bossbar.set("color", entry.getValue().bossBarColor());
                }
            }
            ConfigSection scoreboard = status.getSection("scoreboard");
            if (repairEmptyHud || (scoreboard.contains("lines") && scoreboard.getStringList("lines").equals(oldScoreboard))) {
                scoreboard.set("lines", entry.getValue().scoreboardLines());
            }
        }
        hud.set("timed-round-version", 1);
        apply(minigame, config, defaults);
    }

    /** A separate countdown after players have moved onto the playing floor. */
    public static void applyPreparation(@NotNull MiniGame minigame, @NotNull ConfigSection config,
                                        @NotNull String title) {
        String heading = "<gold>" + title + ":</gold> <white>{arena:name}</white>";
        String countdown = ":clock: <gold>Starts in</gold> <yellow>{arena:time-remaining}</yellow>";
        apply(minigame, config, Map.of(MiniGameArena.ArenaStatus.PREPARATION, new HudDefinition(
            List.of(heading, countdown, ":info_blue: <aqua>Get ready!</aqua>"),
            List.of(heading, "", countdown,
                ":info_green: <white>Players</white> <green>{arena:players}</green>"), 5)));
    }

    /** The previous migration accidentally persisted all eight missing lists as empty. */
    private static boolean hasBrokenEmptyHud(ConfigSection hud, Map<MiniGameArena.ArenaStatus, HudDefinition> defaults) {
        for (var status : defaults.keySet()) {
            ConfigSection section = hud.getSection(status.name().toLowerCase(Locale.ROOT), false);
            if (section == null) return false;
            for (String display : List.of("bossbar", "scoreboard")) {
                ConfigSection lines = section.getSection(display, false);
                if (lines == null || !lines.contains("lines") || !lines.getStringList("lines").isEmpty()) return false;
            }
            ConfigSection bossbar = section.getSection("bossbar", false);
            if (!bossbar.contains("hold-updates") || bossbar.getInt("hold-updates") != 5
                    || !bossbar.contains("color") || !bossbar.getString("color").equals("PURPLE")) return false;
        }
        return true;
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
