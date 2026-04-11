package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.minigame.util.TeamNames;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import dev.stemcraft.api.util.PlaceholderUtil;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final List<String> STAT_SUFFIXES = List.of("wins", "kills", "final_kills", "beds_broken");

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "bedwars";

    private final BedWarsConfig config;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;
    private TeamLineFormat teamLineFormat = TeamLineFormat.defaults();

    public BedWarsMiniGame(STEMCraftAPI api) {
        super(api);
        this.config = new BedWarsConfig(api, this);
    }

    @Override
    public void onLoad() {
        BedWarsArenaHandler handler = new BedWarsArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("team-count", (arena, team, player) -> arena == null ? "0" : String.valueOf(arena.getTeams().size()))
            .registerArenaPlaceholder("team-size", (arena, team, player) -> arena == null ? "0" : String.valueOf(arena.get("teamSize", Integer.class, 1)))
            .registerArenaPlaceholder("winner", (arena, team, player) -> arena == null ? "-" : renderWinner(arena))
            .registerPlayerPlaceholder("final-kills", (arena, team, player) -> player == null ? "0" : String.valueOf(player.get("finalKills", Integer.class, 0)));

        for (int i = 0; i < 8; i++) {
            final int index = i;
            minigame.registerArenaPlaceholder("team-line-" + (i + 1), (arena, team, player) -> renderTeamLine(arena, index));
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
                "Teams: {arena:team-count}",
                "Team Size: {arena:team-size}"
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
                "Teams: {arena:team-count}",
                "Team Size: {arena:team-size}"
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
            .set("teamSize", 1)
            .set("autoAssignTeams", false)
            .set("dropItems", BedWarsConfig.defaultDropItems());

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
                    .set("teamSize", arenaDef.teamSize())
                    .set("autoAssignTeams", false)
                    .set("dropItems", new ArrayList<>(arenaDef.dropItems()));

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

    void incrementStat(@NotNull String suffix, @NotNull MiniGameArena arena, @NotNull MiniGamePlayer player, double amount) {
        api.playerStats().increment(player.getPlayer().getUniqueId(), player.getPlayer().getName(), globalStatKey(suffix), amount);
        api.playerStats().increment(player.getPlayer().getUniqueId(), player.getPlayer().getName(), arenaStatKey(suffix, arena.id()), amount);
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
            kit.put(TeamNames.getMaterial(team.getName()), 64);
            arena.addKit(team.getName(), team.get("displayName", String.class, team.getName()) + " Kit", TeamNames.getMaterial(team.getName()), kit);
        }
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<Material> dropItems(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("dropItems", List.class, BedWarsConfig::defaultDropItems);
    }

    private String renderTeamLine(@Nullable MiniGameArena arena, int index) {
        if (arena == null) {
            return "";
        }

        List<MiniGameTeam> teams = arena.getTeams().stream()
            .sorted(Comparator.comparing(MiniGameTeam::getName))
            .toList();
        if (index >= teams.size()) {
            return "";
        }

        MiniGameTeam team = teams.get(index);
        String displayName = team.get("displayName", String.class, StringUtil.beautify(team.getName()));
        boolean bedAlive = team.get("bedAlive", Boolean.class, true);
        int players = arena.getTeamPlayers(team.getName()).size();
        boolean eliminated = players <= 0
            && !bedAlive
            && arena.getStatus() == MiniGameArena.ArenaStatus.RUNNING;
        return teamLineFormat.render(team, displayName, players, bedAlive, eliminated);
    }

    private String renderWinner(@NotNull MiniGameArena arena) {
        String winner = arena.get("winnerTeam", String.class, "");
        if (winner.isBlank()) {
            return "-";
        }
        MiniGameTeam team = arena.getTeam(winner);
        return team == null ? StringUtil.beautify(winner) : team.get("displayName", String.class, StringUtil.beautify(winner));
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
        @NotNull String teamLine
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
                "{colour}: {state}"
            );
        }

        static @NotNull TeamLineFormat from(@NotNull ConfigSection section, @NotNull TeamLineFormat defaults) {
            Map<String, String> teamColours = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : defaults.teamColours.entrySet()) {
                teamColours.put(entry.getKey(), section.getString(entry.getKey(), entry.getValue()));
            }

            return new TeamLineFormat(
                teamColours,
                section.getString("bed", defaults.bed),
                section.getString("no-bed", defaults.noBed),
                section.getString("remaining-players", defaults.remainingPlayers),
                section.getString("no-remaining-players", defaults.noRemainingPlayers),
                section.getString("team-line", defaults.teamLine)
            );
        }

        boolean applyDefaults(@NotNull ConfigSection section) {
            boolean changed = false;
            for (Map.Entry<String, String> entry : teamColours.entrySet()) {
                if (!section.contains(entry.getKey())) {
                    section.set(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            changed |= setDefault(section, "bed", bed);
            changed |= setDefault(section, "no-bed", noBed);
            changed |= setDefault(section, "remaining-players", remainingPlayers);
            changed |= setDefault(section, "no-remaining-players", noRemainingPlayers);
            changed |= setDefault(section, "team-line", teamLine);
            return changed;
        }

        @NotNull String render(
            @NotNull MiniGameTeam team,
            @NotNull String displayName,
            int playerCount,
            boolean bedAlive,
            boolean eliminated
        ) {
            String colour = teamColours.getOrDefault(team.getName().toLowerCase(Locale.ROOT), displayName);
            String state = eliminated
                ? noRemainingPlayers
                : joinNonBlank(
                    bedAlive ? bed : noBed,
                    PlaceholderUtil.apply(remainingPlayers, "count", Integer.toString(playerCount))
                );

            return PlaceholderUtil.apply(
                teamLine,
                "colour", colour,
                "color", colour,
                "state", state,
                "count", Integer.toString(playerCount)
            );
        }

        private static boolean setDefault(@NotNull ConfigSection section, @NotNull String key, @NotNull String value) {
            if (section.contains(key)) {
                return false;
            }
            section.set(key, value);
            return true;
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
            String name = StringUtil.beautify(teamId);
            return switch (teamId.toLowerCase(Locale.ROOT)) {
                case TeamNames.TEAM_BLACK -> "&0" + name;
                case TeamNames.TEAM_BLUE -> "&9" + name;
                case TeamNames.TEAM_BROWN -> "&6" + name;
                case TeamNames.TEAM_CYAN -> "&3" + name;
                case TeamNames.TEAM_GRAY -> "&8" + name;
                case TeamNames.TEAM_GREEN -> "&2" + name;
                case TeamNames.TEAM_LIGHT_BLUE -> "&b" + name;
                case TeamNames.TEAM_LIGHT_GRAY -> "&7" + name;
                case TeamNames.TEAM_LIME -> "&a" + name;
                case TeamNames.TEAM_MAGENTA -> "&d" + name;
                case TeamNames.TEAM_ORANGE -> "&6" + name;
                case TeamNames.TEAM_PINK -> "&d" + name;
                case TeamNames.TEAM_PURPLE -> "&5" + name;
                case TeamNames.TEAM_RED -> "&c" + name;
                case TeamNames.TEAM_WHITE -> "&f" + name;
                case TeamNames.TEAM_YELLOW -> "&e" + name;
                default -> "&f" + name;
            };
        }
    }
}
