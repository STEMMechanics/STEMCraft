package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.minigame.MiniGameTeamSelectionInput;
import dev.stemcraft.api.minigame.MiniGameTeamSelectionPolicy;
import dev.stemcraft.api.minigame.util.TeamNames;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import dev.stemcraft.service.minigame.MiniGameTeamSelectionSupport;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BedWarsMiniGame extends BaseMiniGame {
    private static final List<String> DEFAULT_TEAM_LINES = List.of(
        "{arena:team-line-1}",
        "{arena:team-line-2}",
        "{arena:team-line-3}",
        "{arena:team-line-4}",
        "{arena:team-line-5}",
        "{arena:team-line-6}",
        "{arena:team-line-7}",
        "{arena:team-line-8}"
    );
    private static final List<String> DEFAULT_LOBBY_TEAM_LINES = List.of(
        "{arena:lobby-team-line-1}",
        "{arena:lobby-team-line-2}",
        "{arena:lobby-team-line-3}",
        "{arena:lobby-team-line-4}",
        "{arena:lobby-team-line-5}",
        "{arena:lobby-team-line-6}",
        "{arena:lobby-team-line-7}",
        "{arena:lobby-team-line-8}"
    );

    private static final List<String> STAT_SUFFIXES = List.of("wins", "kills", "final_kills", "beds_broken");

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "bedwars";

    private BedWarsConfig config;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;
    private TeamLineFormat teamLineFormat = TeamLineFormat.defaults();

    public BedWarsMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onLoad() {
        config = new BedWarsConfig(api, this);
        BedWarsArenaHandler handler = new BedWarsArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .setTeamSelectionPolicy(teamSelectionPolicy())
            .registerArenaPlaceholder("team-count", (arena, team, player) -> arena == null ? "0" : String.valueOf(arena.getTeams().size()))
            .registerArenaPlaceholder("team-size", (arena, team, player) -> arena == null ? "0" : String.valueOf(arena.get("teamSize", Integer.class, 1)))
            .registerArenaPlaceholder("winner", (arena, team, player) -> arena == null ? "-" : renderWinner(arena))
            .registerPlayerPlaceholder("final-kills", (arena, team, player) -> player == null ? "0" : String.valueOf(player.get("finalKills", Integer.class, 0)));

        for (int i = 0; i < 8; i++) {
            final int index = i;
            minigame.registerArenaPlaceholder("team-line-" + (i + 1), (arena, team, player) -> renderTeamLine(arena, player, index));
        }

        registerStats();

        configFile = api.config().load("bedwars.yml");
        if (configFile == null) {
            api.messages().warn("BedWars config could not be loaded.");
            return;
        }
        configFile.setAutoSave(true);
        config.onEnable(configFile);
        teamLineFormat = loadTeamLineFormat(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());

        new BedWarsCommand(api, this).onEnable();
        loadArenas();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "BedWars: {arena:name}",
                "Waiting for teams..."
            ),
            List.of(
                "<gold>BedWars: <white>{arena:name}",
                "",
                "Players: {arena:joined-players}/{arena:max-players}",
                "Need: {arena:min-players}",
                "Selected: {player:selected-team}",
                "Auto: {arena:auto-selected-count}",
                DEFAULT_LOBBY_TEAM_LINES.get(0),
                DEFAULT_LOBBY_TEAM_LINES.get(1),
                DEFAULT_LOBBY_TEAM_LINES.get(2),
                DEFAULT_LOBBY_TEAM_LINES.get(3),
                DEFAULT_LOBBY_TEAM_LINES.get(4),
                DEFAULT_LOBBY_TEAM_LINES.get(5),
                DEFAULT_LOBBY_TEAM_LINES.get(6),
                DEFAULT_LOBBY_TEAM_LINES.get(7)
            )
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "BedWars: {arena:name}",
                "Starting in {arena:time-remaining}"
            ),
            List.of(
                "<gold>BedWars: <white>{arena:name}",
                "",
                "Starts In: {arena:time-remaining}",
                "Players: {arena:joined-players}/{arena:max-players}",
                "Selected: {player:selected-team}",
                "Auto: {arena:auto-selected-count}",
                DEFAULT_LOBBY_TEAM_LINES.get(0),
                DEFAULT_LOBBY_TEAM_LINES.get(1),
                DEFAULT_LOBBY_TEAM_LINES.get(2),
                DEFAULT_LOBBY_TEAM_LINES.get(3),
                DEFAULT_LOBBY_TEAM_LINES.get(4),
                DEFAULT_LOBBY_TEAM_LINES.get(5),
                DEFAULT_LOBBY_TEAM_LINES.get(6),
                DEFAULT_LOBBY_TEAM_LINES.get(7)
            )
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "BedWars: {arena:name}",
                "Time left: {arena:time-remaining}"
            ),
            List.of(
                "<gold>BedWars: <white>{arena:name}",
                "",
                "Time: {arena:time-remaining}",
                DEFAULT_TEAM_LINES.get(0),
                DEFAULT_TEAM_LINES.get(1),
                DEFAULT_TEAM_LINES.get(2),
                DEFAULT_TEAM_LINES.get(3),
                DEFAULT_TEAM_LINES.get(4),
                DEFAULT_TEAM_LINES.get(5),
                DEFAULT_TEAM_LINES.get(6),
                DEFAULT_TEAM_LINES.get(7),
                "",
                "Kills: {player:kills}",
                "Finals: {player:final-kills}"
            )
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "BedWars: {arena:name}",
                "Ending in {arena:time-remaining}"
            ),
            List.of(
                "<gold>BedWars: <white>{arena:name}",
                "",
                "Winner: {arena:winner}",
                "Kills: {player:kills}",
                "Finals: {player:final-kills}"
            )
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
            .setMaxPlayers(8)
            .set("startCountdownSeconds", BedWarsConfig.DEFAULT_START_COUNTDOWN_SECONDS)
            .set("endingSeconds", BedWarsConfig.DEFAULT_ENDING_SECONDS)
            .set("teamSize", 1)
            .set("autoAssignTeams", false)
            .set("dropItems", BedWarsConfig.defaultDropItems())
            .set("dropSurfaceMaterials", BedWarsConfig.defaultDropSurfaceMaterials());
        MiniGameTeamSelectionSupport.applyArenaDefaults(arena);

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
        teamLineFormat = loadTeamLineFormat(configFile);
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
                BedWarsArenaRecord arenaDef = config.load(arenaId, arenaSection);
                MiniGameArena arena = minigame.createArena(arenaId, arenaDef.world())
                    .setName(arenaDef.name())
                    .setLobbySpawn(arenaDef.lobby())
                    .setSpectatorSpawn(arenaDef.spectator())
                    .setRegion(arenaDef.arenaRegion())
                    .setMinPlayers(arenaDef.minPlayers())
                    .setMaxPlayers(arenaDef.maxPlayers())
                    .set("arenaRegion", arenaDef.arenaRegion())
                    .set("startCountdownSeconds", arenaDef.startCountdownSeconds())
                    .set("endingSeconds", arenaDef.endingSeconds())
                    .set("teamSize", arenaDef.teamSize())
                    .set("autoAssignTeams", false)
                    .setLobbyRegion(arenaDef.lobbyRegion())
                    .setTeamSelectionInput(arenaDef.teamSelectionInput())
                    .set("dropItems", new ArrayList<>(arenaDef.dropItems()))
                    .set("dropSurfaceMaterials", new ArrayList<>(arenaDef.dropSurfaceMaterials()));

                for (BedWarsArenaRecord.TeamDef teamDef : arenaDef.teams().values()) {
                    MiniGameTeam team = arena.addTeam(teamDef.teamId(), teamDef.displayName(), teamDef.spawn());
                    team.setSpawn(teamDef.spawn());
                    team.set("displayName", teamDef.displayName());
                    team.set("bedRegion", teamDef.bedRegion());
                }

                refreshArenaKits(arena);
                registerArenaStats(arena);

                ArenaValidationResult result = arena.validate();
                if (result.hasErrors()) {
                    api.messages().error("BedWars arena '" + arenaId + "' has validation errors and will be disabled:");
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
                api.messages().error("Failed to load BedWars arena '" + arenaId + "': " + exception.getMessage());
            }
        }
    }

    public List<String> supportedTeamIds() {
        return TeamNames.predefined().stream()
            .filter(teamId -> !TeamNames.TEAM_AUTO.equals(teamId))
            .sorted()
            .toList();
    }

    public String globalStatKey(@NotNull String suffix) {
        return namespace + "_" + suffix + "_total";
    }

    public String arenaStatKey(@NotNull String suffix, @NotNull String arenaId) {
        String normalized = arenaId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return namespace + "_" + suffix + "_" + normalized;
    }

    public int startCountdownSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("startCountdownSeconds", Integer.class, BedWarsConfig.DEFAULT_START_COUNTDOWN_SECONDS));
    }

    public int endingSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("endingSeconds", Integer.class, BedWarsConfig.DEFAULT_ENDING_SECONDS));
    }

    private void registerStats() {
        for (String suffix : STAT_SUFFIXES) {
            api.playerStats().register(new PlayerStatDefinition(
                globalStatKey(suffix),
                titleForStat(suffix) + " (All Arenas)",
                descriptionForStat(suffix, null),
                namespace,
                "minigame",
                namespace
            ));
        }
    }

    private void registerArenaStats(@NotNull MiniGameArena arena) {
        for (String suffix : STAT_SUFFIXES) {
            api.playerStats().register(new PlayerStatDefinition(
                arenaStatKey(suffix, arena.id()),
                titleForStat(suffix) + " (" + arena.getName() + ")",
                descriptionForStat(suffix, arena.getName()),
                namespace,
                "arena",
                arena.id()
            ));
        }
    }

    private void unregisterArenaStats(@NotNull String arenaId) {
        for (String suffix : STAT_SUFFIXES) {
            api.playerStats().unregister(arenaStatKey(suffix, arenaId));
        }
    }

    void incrementStat(@NotNull String suffix, @NotNull MiniGameArena arena, @NotNull MiniGamePlayer player) {
        api.playerStats().increment(player.getPlayer().getUniqueId(), player.getPlayer().getName(), globalStatKey(suffix), 1.0);
        api.playerStats().increment(player.getPlayer().getUniqueId(), player.getPlayer().getName(), arenaStatKey(suffix, arena.id()), 1.0);
    }

    private String titleForStat(String suffix) {
        return switch (suffix) {
            case "wins" -> "BedWars Wins";
            case "kills" -> "BedWars Kills";
            case "final_kills" -> "BedWars Final Kills";
            case "beds_broken" -> "BedWars Beds Broken";
            default -> StringUtil.beautify(suffix);
        };
    }

    private String descriptionForStat(String suffix, @Nullable String arenaName) {
        String scope = arenaName == null ? "across all BedWars arenas" : "in arena '" + arenaName + "'";
        return switch (suffix) {
            case "wins" -> "Total number of BedWars wins by the player " + scope + ".";
            case "kills" -> "Total number of BedWars kills by the player " + scope + ".";
            case "final_kills" -> "Total number of BedWars final kills by the player " + scope + ".";
            case "beds_broken" -> "Total number of BedWars beds broken by the player " + scope + ".";
            default -> "BedWars stat " + scope + ".";
        };
    }

    void refreshArenaKits(@NotNull MiniGameArena arena) {
        for (MiniGameTeam team : arena.getTeams()) {
            Map<Material, Integer> kit = new LinkedHashMap<>();
            kit.put(Material.WOODEN_SWORD, 1);
            Material wool = TeamNames.getMaterial(team.getName());
            kit.put(wool, 64);
            arena.addKit(team.getName(), team.get("displayName", String.class, team.getName()) + " Kit", wool, kit);
            arena.setUnlimitedPlacement(wool, true);
        }
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<Material> dropItems(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("dropItems", List.class, BedWarsConfig::defaultDropItems);
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<Material> dropSurfaceMaterials(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("dropSurfaceMaterials", List.class, BedWarsConfig::defaultDropSurfaceMaterials);
    }

    String renderTeamLine(@Nullable MiniGameArena arena, @Nullable MiniGamePlayer viewer, int index) {
        if (arena == null) {
            return "";
        }

        List<MiniGameTeam> teams = scoreboardTeams(arena);
        if (index >= teams.size()) {
            return "";
        }

        MiniGameTeam team = teams.get(index);
        String displayName = team.get("displayName", String.class, StringUtil.beautify(team.getName()));
        boolean bedAlive = team.get("bedAlive", Boolean.class, true);
        int players = arena.getTeamPlayers(team.getName()).size();
        boolean eliminated = players == 0
            && !bedAlive
            && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING;
        return teamLineFormat.render(team, displayName, players, bedAlive, eliminated, isViewerTeam(team, viewer));
    }

    @NotNull List<MiniGameTeam> scoreboardTeams(@NotNull MiniGameArena arena) {
        return arena.getTeams().stream()
            .filter(team -> !arena.getTeamPlayers(team.getName()).isEmpty()
                || !team.get("bedAlive", Boolean.class, true))
            .sorted(Comparator.comparing(MiniGameTeam::getName))
            .toList();
    }

    @NotNull List<MiniGameTeam> lobbyScoreboardTeams(@NotNull MiniGameArena arena) {
        return arena.getTeams().stream()
            .sorted(Comparator.comparing(MiniGameTeam::getName))
            .toList();
    }

    private boolean isViewerTeam(@NotNull MiniGameTeam team, @Nullable MiniGamePlayer viewer) {
        return viewer != null && team.getName().equalsIgnoreCase(viewer.getTeam());
    }

    private String renderWinner(@NotNull MiniGameArena arena) {
        String winner = arena.get("winnerTeam", String.class, "");
        if (winner.isBlank()) {
            return "-";
        }
        return teamDisplayName(arena.getTeam(winner), winner);
    }

    @NotNull String teamDisplayName(@Nullable MiniGameTeam team, @NotNull String fallbackTeamName) {
        if (team == null) {
            return defaultTeamDisplayName(fallbackTeamName);
        }

        String displayName = team.get("displayName", String.class, "");
        return displayName.isBlank() || displayName.equalsIgnoreCase(team.getName())
            ? defaultTeamDisplayName(team.getName())
            : displayName;
    }

    private @NotNull String defaultTeamDisplayName(@NotNull String teamName) {
        return StringUtil.capitalize(StringUtil.beautify(teamName));
    }

    @NotNull List<MiniGameTeam> assignmentTeams(@NotNull MiniGameArena arena, @NotNull Map<Player, String> preferences) {
        List<MiniGameTeam> teams = new ArrayList<>(arena.getTeams());
        if (teams.size() <= 2) {
            return teams;
        }

        int players = Math.max(1, arena.numPlayers());
        int teamSize = Math.max(1, arena.get("teamSize", Integer.class, 1));
        int desiredTeams = Math.max(2, players / 3);
        int requiredTeams = Math.max(2, (players + teamSize - 1) / teamSize);
        int explicitTeams = (int) preferences.values().stream()
            .filter(this::isExplicitTeamSelection)
            .distinct()
            .count();
        int activeTeams = Math.clamp(Math.max(desiredTeams, explicitTeams), requiredTeams, teams.size());

        Set<String> preferredTeamIds = new LinkedHashSet<>();
        for (MiniGameTeam team : teams) {
            boolean selected = preferences.values().stream()
                .anyMatch(preference -> team.getName().equalsIgnoreCase(preference));
            if (selected) {
                preferredTeamIds.add(team.getName().toLowerCase(Locale.ROOT));
            }
        }

        List<MiniGameTeam> active = new ArrayList<>();
        for (MiniGameTeam team : teams) {
            if (preferredTeamIds.contains(team.getName().toLowerCase(Locale.ROOT))) {
                active.add(team);
            }
        }
        for (MiniGameTeam team : teams) {
            if (active.size() >= activeTeams) {
                break;
            }
            if (!preferredTeamIds.contains(team.getName().toLowerCase(Locale.ROOT))) {
                active.add(team);
            }
        }
        return active;
    }

    private @NotNull MiniGameTeamSelectionPolicy teamSelectionPolicy() {
        return new MiniGameTeamSelectionPolicy() {
            @Override
            public @NotNull List<MiniGameTeam> assignableTeams(@NotNull MiniGameArena arena, @NotNull Map<Player, String> preferences) {
                return assignmentTeams(arena, preferences);
            }

            @Override
            public @NotNull List<MiniGameTeam> selectableTeams(@NotNull MiniGameArena arena) {
                return new ArrayList<>(arena.getTeams());
            }

            @Override
            public int teamCapacity(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
                return Math.max(1, arena.get("teamSize", Integer.class, 1));
            }

            @Override
            public int requiredActiveTeams(@NotNull MiniGameArena arena) {
                return assignableTeams(arena).size() > 1 ? 2 : 1;
            }

            @Override
            public @NotNull Set<MiniGameTeamSelectionInput> supportedInputs(@NotNull MiniGameArena arena) {
                return Set.of(MiniGameTeamSelectionInput.FLOOR, MiniGameTeamSelectionInput.HOTBAR);
            }

            @Override
            public @NotNull Set<Material> selectorMaterials(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
                return MiniGameTeamSelectionPolicy.super.selectorMaterials(arena, team);
            }

            @Override
            public @NotNull String renderTeamLabel(@NotNull MiniGameArena arena, @NotNull MiniGameTeam team) {
                return teamLineFormat.renderLabel(team, teamDisplayName(team, team.getName()));
            }

            @Override
            public @NotNull String renderLobbyTeamLine(@NotNull MiniGameArena arena,
                                                       @NotNull MiniGameTeam team,
                                                       int activePlayers,
                                                       int maxPlayers,
                                                       boolean viewerTeam) {
                return teamLineFormat.renderLobby(team, teamDisplayName(team, team.getName()), activePlayers, maxPlayers, viewerTeam);
            }
        };
    }

    private boolean isExplicitTeamSelection(@Nullable String preference) {
        return preference != null
            && !preference.isBlank()
            && !TeamNames.TEAM_AUTO.equalsIgnoreCase(preference);
    }

    private @NotNull TeamLineFormat loadTeamLineFormat(@NotNull ConfigSection root) {
        ConfigSection placeholders = root.getSection("placeholders");
        TeamLineFormat defaults = TeamLineFormat.defaults();
        if (defaults.applyDefaults(placeholders)) {
            root.save();
        }
        return TeamLineFormat.from(placeholders, defaults);
    }

    private record TeamLineFormat(
        @NotNull Map<String, String> teamColours,
        @NotNull String bed,
        @NotNull String noBed,
        @NotNull String remainingPlayers,
        @NotNull String noRemainingPlayers,
        @NotNull String teamLine,
        @NotNull String lobbyRemainingPlayers,
        @NotNull String lobbyTeamLine
    ) {
        private TeamLineFormat {
            teamColours = new LinkedHashMap<>(teamColours);
        }

        static @NotNull TeamLineFormat defaults() {
            Map<String, String> teamColours = new LinkedHashMap<>();
            for (String teamId : TeamNames.predefined().stream()
                .filter(id -> !TeamNames.TEAM_AUTO.equals(id))
                .sorted()
                .toList()) {
                teamColours.put(teamId, defaultTeamLabel(teamId));
            }

            return new TeamLineFormat(
                teamColours,
                "&abed",
                "&cno bed",
                "&7({count})",
                "eliminated",
                "{colour}: {state}",
                "&7({count}/{max})",
                "{colour}: {state}"
            );
        }

        static @NotNull TeamLineFormat from(@NotNull ConfigSection section, @NotNull TeamLineFormat defaults) {
            Map<String, String> teamColours = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : defaults.teamColours.entrySet()) {
                String configKey = teamKey(entry.getKey());
                teamColours.put(entry.getKey(), section.getString(configKey, entry.getValue()));
            }

            return new TeamLineFormat(
                teamColours,
                section.getString("bed", defaults.bed),
                section.getString("no-bed", defaults.noBed),
                section.getString("remaining-players", defaults.remainingPlayers),
                section.getString("no-remaining-players", defaults.noRemainingPlayers),
                section.getString("team-line", defaults.teamLine),
                section.getString("lobby-team-state", defaults.lobbyRemainingPlayers),
                section.getString("lobby-team-line", defaults.lobbyTeamLine)
            );
        }

        boolean applyDefaults(@NotNull ConfigSection section) {
            boolean changed = false;
            for (Map.Entry<String, String> entry : teamColours.entrySet()) {
                String configKey = teamKey(entry.getKey());
                String legacyValue = legacyDefaultTeamLabel(entry.getKey());
                if (!section.contains(configKey)) {
                    section.set(configKey, entry.getValue());
                    changed = true;
                } else if (section.getString(configKey, "").equals(legacyValue)) {
                    section.set(configKey, entry.getValue());
                    changed = true;
                }
            }
            changed |= setDefault(section, "bed", bed);
            changed |= setDefault(section, "no-bed", noBed);
            changed |= setDefault(section, "remaining-players", remainingPlayers);
            changed |= setDefault(section, "no-remaining-players", noRemainingPlayers);
            changed |= setDefault(section, "team-line", teamLine);
            changed |= setDefault(section, "lobby-team-state", lobbyRemainingPlayers);
            changed |= setDefault(section, "lobby-team-line", lobbyTeamLine);
            return changed;
        }

        @NotNull String render(
            @NotNull MiniGameTeam team,
            @NotNull String displayName,
            int playerCount,
            boolean bedAlive,
            boolean eliminated,
            boolean viewerTeam
        ) {
            String colour = teamColours.getOrDefault(team.getName().toLowerCase(Locale.ROOT), displayName);
            String state = eliminated
                ? noRemainingPlayers
                : joinNonBlank(
                    bedAlive ? bed : noBed,
                    PlaceholderUtil.apply(remainingPlayers, "count", Integer.toString(playerCount))
                );
            String you = viewerTeam ? " &7(You)" : "";

            return PlaceholderUtil.apply(
                teamLine + you,
                "colour", colour,
                "color", colour,
                "state", state,
                "count", Integer.toString(playerCount)
            );
        }

        @NotNull String renderLabel(@NotNull MiniGameTeam team, @NotNull String displayName) {
            return teamColours.getOrDefault(team.getName().toLowerCase(Locale.ROOT), displayName);
        }

        @NotNull String renderLobby(
            @NotNull MiniGameTeam team,
            @NotNull String displayName,
            int playerCount,
            int maxPlayers,
            boolean viewerTeam
        ) {
            String colour = teamColours.getOrDefault(team.getName().toLowerCase(Locale.ROOT), displayName);
            String state = PlaceholderUtil.apply(
                lobbyRemainingPlayers,
                "count", Integer.toString(playerCount),
                "max", Integer.toString(maxPlayers)
            );
            return PlaceholderUtil.apply(
                lobbyTeamLine,
                "team", colour,
                "colour", colour,
                "color", colour,
                "state", state,
                "you", viewerTeam ? "&7(You)" : "",
                "active", Integer.toString(playerCount),
                "count", Integer.toString(playerCount),
                "max", Integer.toString(maxPlayers)
            );
        }

        private static boolean setDefault(@NotNull ConfigSection section, @NotNull String key, @NotNull String value) {
            if (section.contains(key)) {
                return false;
            }
            section.set(key, value);
            return true;
        }

        private static @NotNull String teamKey(@NotNull String teamId) {
            return "team-" + teamId.toLowerCase(Locale.ROOT);
        }

        private static @NotNull String joinNonBlank(@NotNull String left, @NotNull String right) {
            if (left.isBlank()) {
                return right;
            }
            if (right.isBlank()) {
                return left;
            }
            return left + " " + right;
        }

        private static @NotNull String defaultTeamLabel(@NotNull String teamId) {
            String name = StringUtil.capitalize(StringUtil.beautify(teamId));
            return switch (teamId.toLowerCase(Locale.ROOT)) {
                case TeamNames.TEAM_BLACK -> "&0" + name;
                case TeamNames.TEAM_BLUE -> "&9" + name;
                case TeamNames.TEAM_BROWN, TeamNames.TEAM_ORANGE -> "&6" + name;
                case TeamNames.TEAM_CYAN -> "&3" + name;
                case TeamNames.TEAM_GRAY -> "&8" + name;
                case TeamNames.TEAM_GREEN -> "&2" + name;
                case TeamNames.TEAM_LIGHT_BLUE -> "&b" + name;
                case TeamNames.TEAM_LIGHT_GRAY -> "&7" + name;
                case TeamNames.TEAM_LIME -> "&a" + name;
                case TeamNames.TEAM_MAGENTA, TeamNames.TEAM_PINK -> "&d" + name;
                case TeamNames.TEAM_PURPLE -> "&5" + name;
                case TeamNames.TEAM_RED -> "&c" + name;
                case TeamNames.TEAM_YELLOW -> "&e" + name;
                default -> "&f" + name;
            };
        }

        private static @NotNull String legacyDefaultTeamLabel(@NotNull String teamId) {
            String name = StringUtil.beautify(teamId);
            return switch (teamId.toLowerCase(Locale.ROOT)) {
                case TeamNames.TEAM_BLACK -> "&0" + name;
                case TeamNames.TEAM_BLUE -> "&9" + name;
                case TeamNames.TEAM_BROWN, TeamNames.TEAM_ORANGE -> "&6" + name;
                case TeamNames.TEAM_CYAN -> "&3" + name;
                case TeamNames.TEAM_GRAY -> "&8" + name;
                case TeamNames.TEAM_GREEN -> "&2" + name;
                case TeamNames.TEAM_LIGHT_BLUE -> "&b" + name;
                case TeamNames.TEAM_LIGHT_GRAY -> "&7" + name;
                case TeamNames.TEAM_LIME -> "&a" + name;
                case TeamNames.TEAM_MAGENTA, TeamNames.TEAM_PINK -> "&d" + name;
                case TeamNames.TEAM_PURPLE -> "&5" + name;
                case TeamNames.TEAM_RED -> "&c" + name;
                case TeamNames.TEAM_YELLOW -> "&e" + name;
                default -> "&f" + name;
            };
        }
    }
}
