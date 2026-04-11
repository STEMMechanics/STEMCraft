/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.service.minigame;

import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.api.util.TextUtil;
import dev.stemcraft.capability.HasMetaImpl;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.Player;

import java.util.List;

@Getter
class MiniGamePlayerImpl extends HasMetaImpl<MiniGamePlayer> implements MiniGamePlayer {
    private final MiniGameServiceImpl service;
    private final Player player;
    private static final Criteria criteria = Criteria.DUMMY;
    @Setter
    private String team;
    @Setter
    private int score;
    @Setter
    private int kills;
    @Setter
    private int deaths;
    private int bossBarFrame;

    // HUD state
    private BossBar bossBar;
    private Scoreboard scoreboard;
    private Objective objective;
    private boolean hudBedrockCompatibility;

    MiniGamePlayerImpl(MiniGameServiceImpl service, Player player) {
        this.service = service;
        this.player = player;
    }

    @Override
    public MiniGameArena arena() {
        return service.findPlayerArena(player);
    }

    @Override
    public void addScore(int delta) {
        this.score += delta;
    }

    @Override
    public void subScore(int delta) {
        this.score -= delta;
    }

    @Override
    public void addKill() {
        this.kills += 1;
    }

    @Override
    public void addDeath() {
        this.deaths += 1;
    }

    /**
     * Initialise HUD resources for this player (bossbar + sidebar scoreboard).
     * Safe to call multiple times.
     */
    public void hudInit() {
        boolean bedrockCompatibility = PlayerUtil.isBedrock(player);
        if (scoreboard != null && hudBedrockCompatibility != bedrockCompatibility) {
            hudDispose();
        }

        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
            bossBar.addPlayer(player);
            bossBar.setVisible(true);
        }

        if (scoreboard == null) {
            hudBedrockCompatibility = bedrockCompatibility;
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            if (bedrockCompatibility) {
                objective = scoreboard.registerNewObjective("stmc", criteria, "");
            } else {
                objective = scoreboard.registerNewObjective("stmc", criteria, Component.empty());
                objective.numberFormat(NumberFormat.blank());
            }
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(scoreboard);
        }
    }

    /**
     * Dispose HUD resources for this player.
     */
    public void hudDispose() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }

        if (scoreboard != null) {
            try {
                if (objective != null) objective.unregister();
            } catch (IllegalStateException ignored) {
                // already unregistered
            }
            objective = null;
            scoreboard = null;

            // restore main scoreboard
            Bukkit.getScoreboardManager();
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        bossBarFrame = 0;
        hudBedrockCompatibility = false;
    }

    /**
     * Update HUD contents. Strings are expected to already be rendered.
     * Callers may pass null/empty for either bossbarTitle or scoreboardLines.
     */
    public void hudUpdate(String bossbarTitle, BarColor bossbarColor, List<String> scoreboardLines) {
        hudInit();
        boolean bedrockCompatibility = hudBedrockCompatibility;

        if (bossBar != null) {
            refreshBossBarAttachment(bedrockCompatibility);
            BarColor colour = bossbarColor == null ? BarColor.PURPLE : bossbarColor;
            if (bossBar.getColor() != colour) {
                bossBar.setColor(colour);
            }
            String title = TextUtil.colouriseToSection(service.api().messages().tokens().apply(bossbarTitle));
            if (bedrockCompatibility || !title.equals(bossBar.getTitle())) {
                bossBar.setTitle(title);
            }

            MiniGameArena activeArena = arena();
            double progress = 1.0d;
            if (activeArena != null
                && activeArena.getStatus() == MiniGameArena.ArenaStatus.RUNNING
                && activeArena.contains("bossBarProgress")) {
                progress = Math.max(0.0d, Math.min(1.0d, activeArena.get("bossBarProgress", Double.class, 1.0d)));
            } else if (activeArena != null && activeArena.getCountdown() > 0 && activeArena.getCountdownMax() > 0) {
                progress = Math.max(0.0d, Math.min(1.0d, (double) activeArena.getCountdown() / (double) activeArena.getCountdownMax()));
            }
            bossBar.setProgress(progress);
            bossBar.setVisible(true);
        }

        if (objective == null || scoreboard == null) return;

        // Clear existing sidebar entries
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        if (scoreboardLines == null || scoreboardLines.isEmpty()) {
            if (hudBedrockCompatibility) {
                objective.setDisplayName("");
            } else {
                objective.displayName(Component.empty());
            }
            return;
        }

        // First line as scoreboard title, remaining lines as entries.
        String title = service.api().messages().tokens().apply(scoreboardLines.getFirst());
        if (hudBedrockCompatibility) {
            objective.setDisplayName(TextUtil.colouriseToSection(title));
        } else {
            objective.displayName(TextUtil.colourise(title));
        }

        int maxLines = Math.min(15, scoreboardLines.size() - 1);
        int score = maxLines;

        for (int i = 0; i < maxLines; i++) {
            String raw = service.api().messages().tokens().apply(scoreboardLines.get(i + 1));
            String legacy = TextUtil.colouriseToSection(raw);
            String entry = uniqueEntry(legacy, i);
            objective.getScore(entry).setScore(score--);
        }
    }

    private void refreshBossBarAttachment(boolean bedrockCompatibility) {
        if (bossBar == null) {
            return;
        }

        if (bedrockCompatibility) {
            bossBar.removePlayer(player);
        }

        if (!bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
    }

    // Section-code colour suffixes used only for scoreboard entry uniqueness
    private static final String[] UNIQUE_SUFFIX = new String[] {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6",
            "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e"
    };

    /**
     * Scoreboard entries must be unique and within the scoreboard entry max length.
     * If duplicates occur, suffix with a colour code (invisible unless more text follows).
     */
    private String uniqueEntry(String line, int i) {
        if (line == null) line = "";

        String suffix = UNIQUE_SUFFIX[i % UNIQUE_SUFFIX.length];
        int max = 40 - suffix.length();

        if (line.length() > max) {
            line = line.substring(0, max);
        }
        return line + suffix;
    }

    int nextBossBarLineIndex(int size, int holdUpdates) {
        if (size <= 0) {
            bossBarFrame = 0;
            return 0;
        }

        int effectiveHold = Math.max(1, holdUpdates);
        int cycleLength = size * effectiveHold;
        int index = (bossBarFrame / effectiveHold) % size;
        bossBarFrame = (bossBarFrame + 1) % cycleLength;
        return index;
    }
}
