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

package dev.stemcraft.minigame.bridge;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BridgeMiniGame extends BaseMiniGame {
    private static final String GOALS_TOTAL_STAT_KEY = "bridge_goals_total";
    private static final String WIN_STREAK_CURRENT_STAT_KEY = "bridge_win_streak_current";
    private static final String WIN_STREAK_BEST_STAT_KEY = "bridge_win_streak_best";
    private static final int HUD_LINE_HOLD_UPDATES = 3;

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "bridge";

    private BridgeConfig config;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;

    public BridgeMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        config = new BridgeConfig(api, this);
        BridgeArenaHandler handler = new BridgeArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .registerTeamPlaceholder("score", (arena, team, player) -> renderTeamScore(team))
            .registerPlayerPlaceholder("team-status", (arena, team, player) -> renderPlayerTeamStatus(player));

        registerStats();

        configFile = api.config().load("bridge.yml");
        if (configFile == null) {
            api.messages().warn("Bridge config could not be loaded.");
            return;
        }
        configFile.setAutoSave(true);
        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());

        new BridgeCommand(api, this).onEnable();
        loadArenas();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":info_blue: <aqua>Waiting for players</aqua> <dark_gray>•</dark_gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":purple_bed: <gold>Bridge</gold> <dark_gray>•</dark_gray> <aqua>Lobby</aqua>",
                ":info_green: <gray>Joined</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":question_blue: <gray>Need</gray> <yellow>{arena:min-players}</yellow> <gray>players</gray>",
                ":world: <gray>Map ID</gray> <gold>{arena:id}</gold>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":click_action_right: <gold>Starting in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":info_green: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":click_action_right: <gold>Starts In</gold> <yellow>{arena:time-remaining}</yellow>",
                ":info_green: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":question_blue: <gray>Need</gray> <yellow>{arena:min-players}</yellow>",
                ":world: <gray>Map ID</gray> <gold>{arena:id}</gold>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":location: <gray>Team</gray> {player:team-status}",
                ":world: <gray>Time</gray> <aqua>{arena:time-remaining}</aqua>",
                ":info_green: <gray>Goals</gray> <gold>{player:score}</gold> <dark_gray>•</dark_gray> <red>{player:kills}</red>/<yellow>{player:deaths}</yellow>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":location: <gray>Your Team</gray> {player:team-status}",
                ":world: <gray>Time</gray> <aqua>{arena:time-remaining}</aqua>",
                "{team:red:score}",
                "{team:blue:score}",
                "",
                ":info_green: <gray>Goals</gray> <gold>{player:score}</gold>",
                ":click_action_left: <gray>Kills</gray> <red>{player:kills}</red>",
                ":warning_yellow: <gray>Deaths</gray> <yellow>{player:deaths}</yellow>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                ":warning_yellow: <gold>Round ends in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":info_green: <gray>Goals</gray> <gold>{player:score}</gold>"
            ),
            List.of(
                "<gradient:#f59e0b:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":location: <gray>Your Team</gray> {player:team-status}",
                ":warning_yellow: <gold>Round ends in</gold> <yellow>{arena:time-remaining}</yellow>",
                "{team:red:score}",
                "{team:blue:score}",
                "",
                ":info_green: <gray>Goals</gray> <gold>{player:score}</gold>",
                ":click_action_left: <gray>Kills</gray> <red>{player:kills}</red>",
                ":warning_yellow: <gray>Deaths</gray> <yellow>{player:deaths}</yellow>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        return definitions;
    }

    public @Nullable MiniGameArena createArena(@NotNull String arenaId, @NotNull World world) {
        if (minigame.arena(arenaId) != null) {
            return null;
        }

        MiniGameArena arena = minigame.createArena(arenaId, world)
            .setName(StringUtil.beautify(arenaId))
            .setLobbySpawn(world.getSpawnLocation())
            .setSpectatorSpawn(world.getSpawnLocation())
            .setMinPlayers(2)
            .setMaxPlayers(16);

        ensureTeams(arena);
        arena.set("dropItems", BridgeConfig.defaultDropItems());
        refreshArenaKits(arena);
        registerArenaStats(arena);
        return arena;
    }

    public void deleteArena(@NotNull String arenaId) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena != null) {
            minigame.removeArena(arenaId);
        }
        unregisterArenaStats(arenaId);
        config.deleteArena(arenaId);
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        refreshArenaKits(arena);
        registerArenaStats(arena);
        config.saveArena(arena);
    }

    public void persistArenaEnabled(@NotNull MiniGameArena arena, boolean enabled) {
        if (enabled && !config.hasArena(arena.id())) {
            saveArena(arena);
            return;
        }
        config.setArenaEnabled(arena.id(), enabled);
    }

    public boolean reloadFromConfig() {
        if (!reloadConfigFile(configFile)) {
            return false;
        }

        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());
        unloadArenas(minigame, arena -> unregisterArenaStats(arena.id()));
        loadArenas();
        return true;
    }

    public void loadArenas() {
        ConfigSection arenas = config.getSection("arenas");
        if (arenas == null) {
            return;
        }

        for (String arenaId : arenas.getKeys(false)) {
            ConfigSection arenaSection = arenas.getSection(arenaId, false);
            if (arenaSection == null) {
                continue;
            }

            try {
                BridgeArenaRecord arenaDef = config.load(arenaId, arenaSection);
                MiniGameArena arena = minigame.createArena(arenaId, arenaDef.world())
                    .setName(arenaDef.name())
                    .setLobbySpawn(arenaDef.lobby())
                    .setSpectatorSpawn(arenaDef.spectator())
                    .setRegion(arenaDef.arenaRegion())
                    .setMinPlayers(arenaDef.minPlayers())
                    .setMaxPlayers(arenaDef.maxPlayers())
                    .set("bridgeRegion", arenaDef.bridgeRegion())
                    .set("arenaRegion", arenaDef.arenaRegion())
                    .set("dropItems", new ArrayList<>(arenaDef.dropItems()));

                ensureTeams(arena);
                for (BridgeArenaRecord.TeamDef teamDef : arenaDef.teams().values()) {
                    MiniGameTeam team = arena.getTeam(teamDef.teamId());
                    if (team == null) {
                        team = arena.addTeam(teamDef.teamId(), teamDef.displayName(), teamDef.spawn());
                    }
                    team.setSpawn(teamDef.spawn());
                    team.set("portalRegion", teamDef.portalRegion());
                    team.set("displayName", teamDef.displayName());
                }

                refreshArenaKits(arena);
                registerArenaStats(arena);

                ArenaValidationResult result = arena.validate();
                if (result.hasErrors()) {
                    api.messages().error("Bridge arena '" + arenaId + "' has validation errors and will be disabled:");
                    for (String error : result.getErrors()) {
                        api.messages().error(" - " + error);
                    }
                    arena.setStatus(MiniGameArena.ArenaStatus.DISABLED);
                    continue;
                }

                arena.setStatus(arenaDef.enabled()
                    ? MiniGameArena.ArenaStatus.WAITING
                    : MiniGameArena.ArenaStatus.DISABLED);
            } catch (MiniGameInvalidArenaConfigException exception) {
                api.messages().error("Failed to load Bridge arena '" + arenaId + "': " + exception.getMessage());
            }
        }
    }

    public String goalsTotalStatKey() {
        return GOALS_TOTAL_STAT_KEY;
    }

    public String goalsArenaStatKey(@NotNull String arenaId) {
        String normalized = arenaId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return "bridge_goals_" + normalized;
    }

    public String winStreakCurrentStatKey() {
        return WIN_STREAK_CURRENT_STAT_KEY;
    }

    public String winStreakBestStatKey() {
        return WIN_STREAK_BEST_STAT_KEY;
    }

    private void registerStats() {
        api.playerStats().register(new PlayerStatDefinition(
            GOALS_TOTAL_STAT_KEY,
            "Bridge Goals (All Arenas)",
            "Total number of Bridge goals scored by the player across all Bridge arenas.",
            namespace,
            "minigame",
            namespace
        ));
        api.playerStats().register(new PlayerStatDefinition(
            WIN_STREAK_CURRENT_STAT_KEY,
            "Bridge Win Streak (Current)",
            "Current consecutive Bridge wins across all Bridge arenas.",
            namespace,
            "minigame",
            namespace
        ));
        api.playerStats().register(new PlayerStatDefinition(
            WIN_STREAK_BEST_STAT_KEY,
            "Bridge Win Streak (Best)",
            "Best consecutive Bridge win streak achieved across all Bridge arenas.",
            namespace,
            "minigame",
            namespace
        ));
    }

    private void registerArenaStats(@NotNull MiniGameArena arena) {
        api.playerStats().register(new PlayerStatDefinition(
            goalsArenaStatKey(arena.id()),
            "Bridge Goals (" + arena.getName() + ")",
            "Total number of Bridge goals scored by the player in arena '" + arena.getName() + "'.",
            namespace,
            "arena",
            arena.id()
        ));
    }

    private void unregisterArenaStats(@NotNull String arenaId) {
        api.playerStats().unregister(goalsArenaStatKey(arenaId));
    }

    private void ensureTeams(@NotNull MiniGameArena arena) {
        ensureTeam(arena, "red", "Red");
        ensureTeam(arena, "blue", "Blue");
    }

    private void ensureTeam(@NotNull MiniGameArena arena, @NotNull String id, @NotNull String displayName) {
        MiniGameTeam team = arena.getTeam(id);
        if (team == null) {
            team = arena.addTeam(id, displayName, arena.getLobbySpawn());
        }
        team.set("displayName", team.get("displayName", String.class, displayName));
        if (team.getScore() <= 0 || team.getScore() > 7) {
            team.setScore(7);
        }
    }

    private void refreshArenaKits(@NotNull MiniGameArena arena) {
        Map<Material, Integer> redKit = new LinkedHashMap<>();
        redKit.put(Material.STONE_SWORD, 1);
        redKit.put(Material.BOW, 1);
        redKit.put(Material.ARROW, 1);
        redKit.put(Material.RED_WOOL, 64);

        Map<Material, Integer> blueKit = new LinkedHashMap<>();
        blueKit.put(Material.STONE_SWORD, 1);
        blueKit.put(Material.BOW, 1);
        blueKit.put(Material.ARROW, 1);
        blueKit.put(Material.BLUE_WOOL, 64);

        arena.addKit("red", "Red Kit", Material.RED_WOOL, redKit);
        arena.addKit("blue", "Blue Kit", Material.BLUE_WOOL, blueKit);
        arena.setUnlimitedAmmo(Material.ARROW, true);
        arena.setUnlimitedPlacement(Material.RED_WOOL, true);
        arena.setUnlimitedPlacement(Material.BLUE_WOOL, true);
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<Material> dropItems(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("dropItems", List.class, BridgeConfig::defaultDropItems);
    }

    private String renderTeamScore(@Nullable MiniGameTeam team) {
        if (team == null) {
            return "";
        }

        String displayName = team.get("displayName", String.class, team.getName());
        int score = team.getScore();
        String labelColor = switch (team.getName().toLowerCase(Locale.ROOT)) {
            case "red" -> "&c";
            case "blue" -> "&9";
            default -> "&7";
        };

        StringBuilder hearts = new StringBuilder("&8• ").append(labelColor).append(displayName).append("&8 ");
        for (int i = 0; i < 7; i++) {
            if (i < score) {
                hearts.append("&c❤");
            } else {
                hearts.append("&8❤");
            }
        }
        return hearts.toString();
    }

    private String renderPlayerTeamStatus(@Nullable dev.stemcraft.api.minigame.MiniGamePlayer player) {
        if (player == null || player.arena() == null || player.getTeam() == null) {
            return "<dark_gray>Spectating";
        }

        MiniGameTeam team = player.arena().getTeam(player.getTeam());
        if (team == null) {
            return "<dark_gray>Unknown";
        }

        String displayName = team.get("displayName", String.class, team.getName());
        return switch (team.getName().toLowerCase(Locale.ROOT)) {
            case "red" -> "<red>" + displayName + "</red>";
            case "blue" -> "<blue>" + displayName + "</blue>";
            default -> "<gray>" + displayName + "</gray>";
        };
    }
}
