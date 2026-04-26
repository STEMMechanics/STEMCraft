package dev.stemcraft.minigame.nightfall;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.service.world.WorldChangeSession;
import dev.stemcraft.api.util.PlaceholderUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NightfallMiniGame extends BaseMiniGame {
    private static final int HUD_LINE_HOLD_UPDATES = 5;
    private static final int LEGACY_HUD_LINE_HOLD_UPDATES = 3;

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "nightfall";

    private NightfallConfig config;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;
    private NightfallTextFormat textFormat = NightfallTextFormat.defaults();

    public NightfallMiniGame(STEMCraftAPI api) {
        super(api);
    }

    @Override
    protected boolean disablesHungerByDefault() {
        return false;
    }

    @Override
    public void onLoad() {
        config = new NightfallConfig(api, this);
        NightfallArenaHandler handler = new NightfallArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("active-players", (arena, team, player) -> arena == null ? "0" : Integer.toString(activeSurvivorCount(arena)))
            .registerArenaPlaceholder("blood-moon", (arena, team, player) -> Boolean.toString(arena != null && isBloodMoonActive(arena)))
            .registerArenaPlaceholder("bossbar-color", (arena, team, player) -> arena != null && isBloodMoonActive(arena) ? "RED" : "PURPLE")
            .registerArenaPlaceholder("cycle-countdown-line", (arena, team, player) -> arena == null ? "-" : arena.get("cycleCountdownLine", String.class, "-"))
            .registerArenaPlaceholder("generator-count", (arena, team, player) -> arena == null ? "0" : Integer.toString(generatorLocations(arena).size()))
            .registerArenaPlaceholder("night", (arena, team, player) -> arena == null ? "0" : Integer.toString(currentNight(arena)))
            .registerArenaPlaceholder("zombies-remaining", (arena, team, player) -> arena == null ? "0" : Integer.toString(zombiesRemaining(arena)))
            .registerArenaPlaceholder("zombies-alive", (arena, team, player) -> arena == null ? "0" : Integer.toString(zombiesAlive(arena)))
            .registerArenaPlaceholder("zombies-queued", (arena, team, player) -> arena == null ? "0" : Integer.toString(zombiesQueued(arena)))
            .registerArenaPlaceholder("phase", (arena, team, player) -> arena == null ? "-" : phaseLabel(arena))
            .registerArenaPlaceholder("phase-number", (arena, team, player) -> arena == null ? "0" : Integer.toString(phaseNumber(arena)))
            .registerArenaPlaceholder("phase-line", (arena, team, player) -> arena == null ? "-" : phaseLine(arena))
            .registerPlayerPlaceholder("lives-left", (arena, team, player) -> player == null ? "0" : Integer.toString(livesLeft(player)));

        configFile = api.config().load("nightfall.yml");
        if (configFile == null) {
            api.messages().warn("Nightfall config could not be loaded.");
            return;
        }
        configFile.setAutoSave(true);
        config.onEnable(configFile);
        textFormat = loadTextFormat(configFile);
        migrateLegacyHudDefaults(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());

        new NightfallCommand(api, this).onEnable();
        loadArenas();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                ":info_blue: <aqua>Waiting for players</aqua>",
                ":info_blue: <aqua>Require {arena:min-players} players to start</aqua>"
            ),
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                "",
                ":info_green: <white>Joined</white> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":info_purple: <white>Need</white> <yellow>{arena:min-players}</yellow> <white>players</white>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                ":clock: <gold>Starting in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":info_green: <white>Players</white> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
            ),
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                "",
                ":clock: <gold>Starts In</gold> <yellow>{arena:time-remaining}</yellow>",
                ":info_green: <white>Players</white> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":info_purple: <white>Need</white> <yellow>{arena:min-players}</yellow>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.PREPARATION, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                ":clock: <gold>Sunset in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":info_green: <white>Players</white> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
            ),
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                "",
                ":clock: <gold>Sunset In</gold> <yellow>{arena:time-remaining}</yellow>",
                ":sunset: <white>Preparation</white>",
                "",
                ":steve: <yellow>Players:</yellow> {arena:active-players}",
                ":zombie: <yellow>Zombies:</yellow> {arena:zombies-remaining}",
                ":swords: <yellow>Kills:</yellow> {player:kills}",
                ":skeleton_head_front: <yellow>Deaths:</yellow> {player:deaths}"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                "?{arena:blood-moon} :warning_red: <red>Blood Moon</red>"
            ),
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                "",
                ":sunset: <white>Day {arena:phase-number}</white>",
                "",
                "?{arena:blood-moon} :warning_red: <red>Blood Moon</red>",
                ":steve: <yellow>Players:</yellow> {arena:active-players}",
                ":zombie: <yellow>Zombies:</yellow> {arena:zombies-remaining}",
                ":swords: <yellow>Kills:</yellow> {player:kills}",
                ":skeleton_head_front: <yellow>Deaths:</yellow> {player:deaths}"
            ),
            HUD_LINE_HOLD_UPDATES,
            "{arena:bossbar-color}"
        ));
        definitions.put(MiniGameArena.ArenaStatus.COOLDOWN, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                ":skeleton_head_front: <red>All survivors are out</red>",
                ":clock: <gray>Reset in {arena:time-remaining}</gray>"
            ),
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                "",
                ":sunset: <white>Reached Night: {arena:night}</white>",
                ":clock: <white>Reset In: {arena:time-remaining}</white>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                ":skeleton_head_front: <red>All survivors are out</red>",
                ":clock: <gray>Reset in {arena:time-remaining}</gray>"
            ),
            List.of(
                "<gold>Nightfall:</gold> <white>{arena:name}</white>",
                "",
                ":sunset: <white>Reached Night: {arena:night}</white>",
                ":clock: <white>Reset In: {arena:time-remaining}</white>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        return definitions;
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> legacyHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient>",
                "<gray>Waiting for survivors</gray>"
            ),
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                "Players: {arena:joined-players}/{arena:max-players}",
                "Need: {arena:min-players}",
                "Build: Locked"
            ),
            LEGACY_HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient>",
                "<gold>Starting in</gold> <yellow>{arena:time-remaining}</yellow>"
            ),
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                "Starts In: {arena:time-remaining}",
                "Players: {arena:joined-players}/{arena:max-players}",
                "Build: Locked"
            ),
            LEGACY_HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient> <gray>-</gray> {arena:cycle-countdown-line}"
            ),
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                "Phase: {arena:phase}",
                "{arena:phase-line}",
                "Alive: {arena:active-players}",
                "Zombies: {arena:zombies-remaining}",
                "Kills: {player:kills}",
                "Deaths: {player:deaths}"
            ),
            LEGACY_HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient>",
                "<red>All survivors are out</red>",
                "<gray>Reset in {arena:time-remaining}</gray>"
            ),
            List.of(
                "<gradient:#fbbf24:#f97316><bold>{arena:name}</bold></gradient>",
                "",
                "Reached Night: {arena:night}",
                "Reset In: {arena:time-remaining}"
            ),
            LEGACY_HUD_LINE_HOLD_UPDATES
        ));
        return definitions;
    }

    public @Nullable MiniGameArena createArena(@NotNull String arenaId, @NotNull World world) {
        if (minigame.arena(arenaId) != null) {
            return null;
        }

        return minigame.createArena(arenaId, world)
            .setName(StringUtil.beautify(arenaId))
            .setLobbySpawn(world.getSpawnLocation())
            .setSpectatorSpawn(world.getSpawnLocation())
            .setMinPlayers(1)
            .setMaxPlayers(8)
            .setRegion(null)
            .set("arenaRegion", null)
            .set("playSpawn", world.getSpawnLocation())
            .set("startCountdownSeconds", NightfallConfig.DEFAULT_START_COUNTDOWN_SECONDS)
            .set("endingSeconds", NightfallConfig.DEFAULT_ENDING_SECONDS)
            .set("lives", 3)
            .set("prepSeconds", 300)
            .set("dayTimeSpeedMultiplier", 2.0d)
            .set("nightTimeSpeedMultiplier", 2.0d)
            .set("dropMinSeconds", 1)
            .set("dropMaxSeconds", 5)
            .set("dropMaxActiveItems", 10)
            .set("zombieBaseNightlySpawns", 4)
            .set("zombieNightlySpawnIncrease", 3)
            .set("zombieNightlyHealthMultiplier", 1.05d)
            .set("zombieWaveSize", 2)
            .set("zombieWaveIntervalSeconds", 8)
            .set("zombieSpawnRadiusMin", 20)
            .set("zombieSpawnRadiusMax", 30)
            .set("bloodMoonChancePercent", 0)
            .set("bloodMoonZombieSpawnMultiplier", 2.0d)
            .set("bloodMoonBabyZombieChancePercent", 20)
            .set("generatorLocations", new ArrayList<Location>())
            .set("dropItems", copyDropItems(defaultDropItems()));
    }

    public void deleteArena(@NotNull String arenaId) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena != null) {
            minigame.removeArena(arenaId);
        }
        config.deleteArena(arenaId);
    }

    public void saveArena(@NotNull MiniGameArena arena) {
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
        textFormat = loadTextFormat(configFile);
        migrateLegacyHudDefaults(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());
        unloadArenas(minigame);
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
                NightfallArenaRecord arenaDef = config.load(arenaId, arenaSection);
                MiniGameArena arena = minigame.createArena(arenaId, arenaDef.world())
                    .setName(arenaDef.name())
                    .setLobbySpawn(arenaDef.lobby())
                    .setSpectatorSpawn(arenaDef.spectator())
                    .setRegion(arenaDef.arenaRegion())
                    .setMinPlayers(arenaDef.minPlayers())
                    .setMaxPlayers(arenaDef.maxPlayers())
                    .set("arenaRegion", arenaDef.arenaRegion())
                    .set("playSpawn", arenaDef.spawn())
                    .set("startCountdownSeconds", arenaDef.startCountdownSeconds())
                    .set("endingSeconds", arenaDef.endingSeconds())
                    .set("lives", arenaDef.lives())
                    .set("prepSeconds", arenaDef.prepSeconds())
                    .set("dayTimeSpeedMultiplier", arenaDef.dayTimeSpeedMultiplier())
                    .set("nightTimeSpeedMultiplier", arenaDef.nightTimeSpeedMultiplier())
                    .set("dropMinSeconds", arenaDef.dropMinSeconds())
                    .set("dropMaxSeconds", arenaDef.dropMaxSeconds())
                    .set("dropMaxActiveItems", arenaDef.dropMaxActiveItems())
                    .set("zombieBaseNightlySpawns", arenaDef.zombieBaseNightlySpawns())
                    .set("zombieNightlySpawnIncrease", arenaDef.zombieNightlySpawnIncrease())
                    .set("zombieNightlyHealthMultiplier", arenaDef.zombieNightlyHealthMultiplier())
                    .set("zombieWaveSize", arenaDef.zombieWaveSize())
                    .set("zombieWaveIntervalSeconds", arenaDef.zombieWaveIntervalSeconds())
                    .set("zombieSpawnRadiusMin", arenaDef.zombieSpawnRadiusMin())
                    .set("zombieSpawnRadiusMax", arenaDef.zombieSpawnRadiusMax())
                    .set("bloodMoonChancePercent", arenaDef.bloodMoonChancePercent())
                    .set("bloodMoonZombieSpawnMultiplier", arenaDef.bloodMoonZombieSpawnMultiplier())
                    .set("bloodMoonBabyZombieChancePercent", arenaDef.bloodMoonBabyZombieChancePercent())
                    .set("generatorLocations", copyLocations(arenaDef.generatorLocations()))
                    .set("dropItems", copyDropItems(arenaDef.dropItems()))
                    .set("pendingWorldRollback", arenaDef.pendingWorldRollback())
                    .set("savedTimeSetting", normalizeRecoverySetting(arenaDef.savedTimeSetting()))
                    .set("savedWeatherSetting", normalizeRecoverySetting(arenaDef.savedWeatherSetting()));

                recoverPendingRollback(arena);

                ArenaValidationResult result = arena.validate();
                if (result.hasErrors()) {
                    api.messages().error("Nightfall arena '" + arenaId + "' has validation errors and will be disabled:");
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
                api.messages().error("Failed to load Nightfall arena '" + arenaId + "': " + exception.getMessage());
            }
        }
    }

    void persistRecoveryState(
        @NotNull MiniGameArena arena,
        boolean pendingWorldRollback,
        @Nullable String savedTimeSetting,
        @Nullable String savedWeatherSetting
    ) {
        arena.set("pendingWorldRollback", pendingWorldRollback);
        arena.set("savedTimeSetting", normalizeRecoverySetting(savedTimeSetting));
        arena.set("savedWeatherSetting", normalizeRecoverySetting(savedWeatherSetting));
        config.setArenaRecoveryState(
            arena.id(),
            pendingWorldRollback,
            savedTimeSetting,
            savedWeatherSetting
        );
    }

    void clearRecoveryState(@NotNull MiniGameArena arena) {
        persistRecoveryState(arena, false, "unset", "unset");
    }

    private void recoverPendingRollback(@NotNull MiniGameArena arena) {
        if (!arena.get("pendingWorldRollback", Boolean.class, false)) {
            return;
        }

        String savedTimeSetting = normalizeRecoverySetting(arena.get("savedTimeSetting", String.class, "unset"));
        String savedWeatherSetting = normalizeRecoverySetting(arena.get("savedWeatherSetting", String.class, "unset"));

        try {
            WorldChangeSession session = api.worlds().changes(arena.world());
            session.stop();
            session.load();
            session.rollback(false);
            session.clear();

            api.worlds().setSetting(arena.world(), "time", savedTimeSetting);
            api.worlds().setSetting(arena.world(), "weather", savedWeatherSetting);
            clearRecoveryState(arena);

            api.messages().info("Recovered pending Nightfall rollback for arena '" + arena.id() + "'.");
        } catch (RuntimeException exception) {
            api.messages().error("Failed to recover pending Nightfall rollback for arena '" + arena.id() + "': " + exception.getMessage());
        }
    }

    private @NotNull String normalizeRecoverySetting(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "unset";
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<Location> generatorLocations(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("generatorLocations", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull Map<Integer, List<Material>> dropItems(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("dropItems", Map.class, LinkedHashMap::new);
    }

    public int dropItemCount(@NotNull MiniGameArena arena) {
        int count = 0;
        for (List<Material> materials : dropItems(arena).values()) {
            if (materials != null) {
                count += materials.size();
            }
        }
        return count;
    }

    public @Nullable Location playSpawn(@NotNull MiniGameArena arena) {
        return arena.get("playSpawn", Location.class, arena.getLobbySpawn());
    }

    public int totalLives(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("lives", Integer.class, 3));
    }

    public int startCountdownSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("startCountdownSeconds", Integer.class, NightfallConfig.DEFAULT_START_COUNTDOWN_SECONDS));
    }

    public int endingSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("endingSeconds", Integer.class, NightfallConfig.DEFAULT_ENDING_SECONDS));
    }

    public int prepSeconds(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("prepSeconds", Integer.class, 300));
    }

    public double dayTimeSpeedMultiplier(@NotNull MiniGameArena arena) {
        return Math.max(1.0d, arena.get("dayTimeSpeedMultiplier", Double.class, 2.0d));
    }

    public double nightTimeSpeedMultiplier(@NotNull MiniGameArena arena) {
        return Math.max(1.0d, arena.get("nightTimeSpeedMultiplier", Double.class, 2.0d));
    }

    public int dropMinSeconds(@NotNull MiniGameArena arena) {
        int min = arena.get("dropMinSeconds", Integer.class, 1);
        int max = arena.get("dropMaxSeconds", Integer.class, 5);
        if (min <= 0 || max <= 0) {
            return 0;
        }
        return min;
    }

    public int dropMaxSeconds(@NotNull MiniGameArena arena) {
        int min = arena.get("dropMinSeconds", Integer.class, 1);
        int max = arena.get("dropMaxSeconds", Integer.class, 5);
        if (min <= 0 || max <= 0) {
            return 0;
        }
        return Math.max(min, max);
    }

    public boolean dropsEnabled(@NotNull MiniGameArena arena) {
        return dropMinSeconds(arena) > 0 && dropMaxSeconds(arena) > 0;
    }

    public int dropMaxActiveItems(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("dropMaxActiveItems", Integer.class, 10));
    }

    public int zombieBaseNightlySpawns(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("zombieBaseNightlySpawns", Integer.class, 4));
    }

    public int zombieNightlySpawnIncrease(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("zombieNightlySpawnIncrease", Integer.class, 3));
    }

    public double zombieNightlyHealthMultiplier(@NotNull MiniGameArena arena) {
        return Math.max(1.0d, arena.get("zombieNightlyHealthMultiplier", Double.class, 1.05d));
    }

    public int zombieWaveSize(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("zombieWaveSize", Integer.class, 2));
    }

    public int zombieWaveIntervalSeconds(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("zombieWaveIntervalSeconds", Integer.class, 8));
    }

    public int zombieSpawnRadiusMin(@NotNull MiniGameArena arena) {
        return Math.max(1, arena.get("zombieSpawnRadiusMin", Integer.class, 20));
    }

    public int zombieSpawnRadiusMax(@NotNull MiniGameArena arena) {
        return Math.max(zombieSpawnRadiusMin(arena), arena.get("zombieSpawnRadiusMax", Integer.class, 30));
    }

    public int bloodMoonChancePercent(@NotNull MiniGameArena arena) {
        return Math.clamp(arena.get("bloodMoonChancePercent", Integer.class, 0), 0, 100);
    }

    public double bloodMoonZombieSpawnMultiplier(@NotNull MiniGameArena arena) {
        return Math.max(1.0d, arena.get("bloodMoonZombieSpawnMultiplier", Double.class, 2.0d));
    }

    public int bloodMoonBabyZombieChancePercent(@NotNull MiniGameArena arena) {
        return Math.clamp(arena.get("bloodMoonBabyZombieChancePercent", Integer.class, 20), 0, 100);
    }

    public int currentNight(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("currentNight", Integer.class, 0));
    }

    public boolean isBloodMoonActive(@NotNull MiniGameArena arena) {
        return arena.get("bloodMoonActive", Boolean.class, false);
    }

    public int activeSurvivorCount(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("activeSurvivorCount", Integer.class, arena.numPlayers()));
    }

    public int zombiesRemaining(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("zombiesRemaining", Integer.class, 0));
    }

    public int zombiesAlive(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("zombiesAlive", Integer.class, 0));
    }

    public int zombiesQueued(@NotNull MiniGameArena arena) {
        return Math.max(0, arena.get("zombiesQueued", Integer.class, 0));
    }

    private @NotNull String phaseLabel(@NotNull MiniGameArena arena) {
        return switch (arena.getStatus()) {
            case WAITING -> textFormat.waitingPhase();
            case STARTING -> textFormat.startingPhase();
            case PREPARATION -> textFormat.preparationPhase();
            case COOLDOWN -> "Cooldown";
            case ENDING -> textFormat.resettingPhase();
            case RUNNING -> {
                boolean isNight = arena.get("wasNight", Boolean.class, false);
                int number = phaseNumber(arena);
                if (isNight) {
                    yield PlaceholderUtil.apply(textFormat.nightPhase(), "number", Integer.toString(number));
                }
                yield PlaceholderUtil.apply(textFormat.dayPhase(), "number", Integer.toString(number));
            }
            default -> arena.getStatus().name().toLowerCase(Locale.ROOT);
        };
    }

    private @NotNull String phaseLine(@NotNull MiniGameArena arena) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.PREPARATION) {
            return PlaceholderUtil.apply(
                textFormat.preparationPhaseLine(),
                "countdown", formatCountdown(arena.getCountdown()),
                "number", Integer.toString(phaseNumber(arena))
            );
        }

        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return phaseLabel(arena);
        }

        boolean isNight = arena.get("wasNight", Boolean.class, false);
        int number = phaseNumber(arena);
        if (isNight) {
            return PlaceholderUtil.apply(textFormat.nightPhaseLine(), "number", Integer.toString(number));
        }
        if (currentNight(arena) <= 0) {
            return PlaceholderUtil.apply(textFormat.firstDayPhaseLine(), "number", Integer.toString(number));
        }
        return PlaceholderUtil.apply(textFormat.respitePhaseLine(), "number", Integer.toString(number));
    }

    public int phaseNumber(@NotNull MiniGameArena arena) {
        if (arena.getStatus() == MiniGameArena.ArenaStatus.PREPARATION) {
            return 1;
        }

        if (arena.getStatus() != MiniGameArena.ArenaStatus.RUNNING) {
            return 0;
        }

        boolean isNight = arena.get("wasNight", Boolean.class, false);
        int night = currentNight(arena);
        if (isNight) {
            return Math.max(1, night);
        }
        if (night <= 0) {
            return 1;
        }
        return night + 1;
    }

    private int livesLeft(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return 0;
        }
        return Math.max(0, player.get("livesRemaining", Integer.class, 1));
    }

    private @NotNull String formatCountdown(int seconds) {
        int mins = Math.max(0, seconds) / 60;
        int secs = Math.max(0, seconds) % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    public @NotNull String roundStartMessage() {
        return textFormat.roundStart();
    }

    public @NotNull String roundEndMessage(int seconds) {
        return PlaceholderUtil.apply(textFormat.roundEnd(), "seconds", Integer.toString(seconds));
    }

    public @NotNull String dayUnlockedMessage() {
        return textFormat.dayUnlocked();
    }

    public @NotNull String nightStartMessage(int number) {
        return PlaceholderUtil.apply(textFormat.nightStart(), "number", Integer.toString(number));
    }

    public @NotNull String sunriseMessage(int nextNumber) {
        return PlaceholderUtil.apply(textFormat.sunrise(), "number", Integer.toString(nextNumber));
    }

    public @NotNull String timeMilestoneThreePmMessage() {
        return renderMilestone(textFormat.threePmLabel(), milestoneRemainingDetail(3, textFormat.sunsetTargetLabel()));
    }

    public @NotNull String timeMilestoneSunsetMessage() {
        return renderMilestone(textFormat.sunsetLabel(), milestoneArrivalDetail(textFormat.sunsetTargetLabel()));
    }

    public @NotNull String timeMilestoneNinePmMessage() {
        return renderMilestone(textFormat.ninePmLabel(), milestoneRemainingDetail(8, textFormat.sunriseTargetLabel()));
    }

    public @NotNull String timeMilestoneMidnightMessage() {
        return renderMilestone(textFormat.midnightLabel(), milestoneRemainingDetail(5, textFormat.sunriseTargetLabel()));
    }

    public @NotNull String timeMilestoneThreeAmMessage() {
        return renderMilestone(textFormat.threeAmLabel(), milestoneRemainingDetail(2, textFormat.sunriseTargetLabel()));
    }

    public @NotNull String playerDownedMessage(@NotNull Player player) {
        return PlaceholderUtil.apply(textFormat.playerDowned(), "player", player.getName());
    }

    public @NotNull String playerReturnedMessage(@NotNull Player player) {
        return PlaceholderUtil.apply(textFormat.playerReturned(), "player", player.getName());
    }

    public @NotNull String playerRespawnMessage(@NotNull Player player) {
        return PlaceholderUtil.apply(textFormat.playerRespawn(), "player", player.getName());
    }

    public @NotNull String bloodMoonStartMessage() {
        return textFormat.bloodMoonStart();
    }

    public @NotNull String nightClearMessage() {
        return textFormat.nightClear();
    }

    private @NotNull String renderMilestone(@NotNull String label, @NotNull String detail) {
        return PlaceholderUtil.apply(textFormat.timeMilestone(),
            "detail", detail,
            "label", label,
            "time", label);
    }

    private @NotNull String milestoneRemainingDetail(int hours, @NotNull String target) {
        return "Only " + hours + " " + (hours == 1 ? "hour" : "hours") + " till " + target + ".";
    }

    private @NotNull String milestoneArrivalDetail(@NotNull String target) {
        return StringUtil.beautify(target) + " has arrived.";
    }

    private @NotNull NightfallTextFormat loadTextFormat(@NotNull ConfigFile config) {
        NightfallTextFormat defaults = NightfallTextFormat.defaults();
        ConfigSection strings = config.getSection("strings");
        boolean changed = defaults.migrateLegacyDefaults(strings);
        changed |= defaults.applyDefaults(strings);
        if (changed) {
            config.save();
        }
        return NightfallTextFormat.from(strings, defaults);
    }

    private void migrateLegacyHudDefaults(@NotNull ConfigSection config) {
        ConfigSection hud = config.getSection("hud");
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> legacy = legacyHudDefinitions();
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaults = defaultHudDefinitions();

        for (Map.Entry<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> entry : legacy.entrySet()) {
            MiniGameArena.ArenaStatus status = entry.getKey();
            MiniGameHudConfigSupport.HudDefinition oldDefinition = entry.getValue();
            MiniGameHudConfigSupport.HudDefinition newDefinition = defaults.get(status);
            if (newDefinition == null) {
                continue;
            }

            ConfigSection statusSection = hud.getSection(status.name().toLowerCase(Locale.ROOT));
            ConfigSection bossbar = statusSection.getSection("bossbar");
            ConfigSection scoreboard = statusSection.getSection("scoreboard");

            if (bossbar.getStringList("lines").equals(oldDefinition.bossBarLines())) {
                bossbar.set("lines", newDefinition.bossBarLines());
                if (bossbar.contains("hold-updates")
                    && bossbar.getInt("hold-updates", oldDefinition.bossBarLineHoldUpdates()) == oldDefinition.bossBarLineHoldUpdates()) {
                    bossbar.set("hold-updates", newDefinition.bossBarLineHoldUpdates());
                }
            }

            if (scoreboard.getStringList("lines").equals(oldDefinition.scoreboardLines())) {
                scoreboard.set("lines", newDefinition.scoreboardLines());
            }
        }
    }

    static @NotNull Map<Integer, List<Material>> defaultDropItems() {
        Map<Integer, List<Material>> items = new LinkedHashMap<>();
        items.put(1, materials("ENCHANTED_GOLDEN_APPLE", "DIAMOND_CHESTPLATE", "NETHERITE_SWORD"));
        items.put(3, materials("DIAMOND_HELMET", "DIAMOND_BOOTS", "CROSSBOW", "BLAST_FURNACE"));
        items.put(6, materials("IRON_CHESTPLATE", "IRON_LEGGINGS", "SHIELD", "WATER_BUCKET", "HOPPER", "PISTON"));
        items.put(10, materials("BOW", "IRON_AXE", "IRON_PICKAXE", "FLINT_AND_STEEL", "SMOKER", "BUCKET", "COOKED_BEEF",
            "COOKED_CHICKEN"));
        items.put(16, materials("IRON_INGOT", "COAL", "TORCH", "ARROW", "BREAD", "COOKED_MUTTON", "LEATHER", "STRING", "SHEARS"));
        items.put(24, materials("CRAFTING_TABLE", "FURNACE", "CHEST", "BARREL", "LADDER", "OAK_DOOR", "OAK_TRAPDOOR", "STONE",
            "COBBLESTONE", "OAK_PLANKS", "STICK"));
        items.put(36, materials("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS", "STICK",
            "COBBLESTONE", "STONE", "DIRT", "GRAVEL", "SAND", "COAL", "CHARCOAL", "TORCH", "BREAD"));
        items.put(52, materials("OAK_SLAB", "OAK_STAIRS", "OAK_FENCE", "OAK_FENCE_GATE", "COBBLESTONE_SLAB", "COBBLESTONE_STAIRS",
            "COBBLESTONE_WALL", "STONE_BRICKS", "STONE_BRICK_SLAB", "STONE_BRICK_STAIRS", "CAMPFIRE", "GLASS", "BRICKS"));
        items.put(70, materials("APPLE", "POTATO", "CARROT", "WHEAT_SEEDS", "BEETROOT_SEEDS", "BONE", "ROTTEN_FLESH", "STRING",
            "FEATHER", "EGG", "PAPER", "LEVER", "STONE_BUTTON", "OAK_PRESSURE_PLATE"));
        items.put(85, materials("DIRT", "GRAVEL", "STONE", "COBBLESTONE", "OAK_LOG", "OAK_PLANKS", "STICK", "WHEAT_SEEDS",
            "POTATO", "CARROT", "BONE", "STRING", "FEATHER", "CHARCOAL"));
        items.put(100, materials("DIRT", "GRAVEL", "STONE", "COBBLESTONE", "ANDESITE", "DIORITE", "GRANITE", "SAND", "OAK_LOG",
            "SPRUCE_LOG", "BIRCH_LOG", "OAK_PLANKS", "STICK", "WHEAT_SEEDS", "POTATO", "CARROT", "ROTTEN_FLESH", "BONE", "STRING",
            "FEATHER"));
        return items;
    }

    private @NotNull List<Location> copyLocations(@NotNull List<Location> source) {
        List<Location> copy = new ArrayList<>(source.size());
        for (Location location : source) {
            copy.add(location == null ? null : location.clone());
        }
        return copy;
    }

    private @NotNull Map<Integer, List<Material>> copyDropItems(@NotNull Map<Integer, List<Material>> source) {
        Map<Integer, List<Material>> copy = new LinkedHashMap<>();
        source.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> copy.put(entry.getKey(), new ArrayList<>(entry.getValue())));
        return copy;
    }

    private static @NotNull List<Material> materials(String... names) {
        List<Material> materials = new ArrayList<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null && !material.isAir()) {
                materials.add(material);
            }
        }
        return materials;
    }

    private record NightfallTextFormat(
        @NotNull String waitingPhase,
        @NotNull String startingPhase,
        @NotNull String resettingPhase,
        @NotNull String preparationPhase,
        @NotNull String dayPhase,
        @NotNull String nightPhase,
        @NotNull String preparationPhaseLine,
        @NotNull String firstDayPhaseLine,
        @NotNull String respitePhaseLine,
        @NotNull String nightPhaseLine,
        @NotNull String roundStart,
        @NotNull String roundEnd,
        @NotNull String dayUnlocked,
        @NotNull String nightStart,
        @NotNull String sunrise,
        @NotNull String timeMilestone,
        @NotNull String threePmLabel,
        @NotNull String sunsetLabel,
        @NotNull String ninePmLabel,
        @NotNull String midnightLabel,
        @NotNull String threeAmLabel,
        @NotNull String sunsetTargetLabel,
        @NotNull String sunriseTargetLabel,
        @NotNull String playerDowned,
        @NotNull String playerReturned,
        @NotNull String playerRespawn,
        @NotNull String bloodMoonStart,
        @NotNull String nightClear
    ) {
        static @NotNull NightfallTextFormat defaults() {
            return new NightfallTextFormat(
                "Waiting",
                "Starting",
                "Resetting",
                "Preparation",
                "Day {number}",
                "Night {number}",
                "Night falls in {countdown}",
                "Daylight before night {number}",
                "Dawn respite before night {number}",
                "Night {number} is active",
                "<gold>Survive until sunset.</gold>",
                "<red>All survivors are out.</red> <gray>The world resets in {seconds} seconds.</gray>",
                "<gold>The day moves on.</gold>",
                "<red>Night {number} has begun.</red>",
                "<gold>Sunrise.</gold> <gray>Prepare for night {number}.</gray>",
                ":clock: <gold>It is now {time}.</gold> <gray>{detail}</gray>",
                "3pm",
                "6pm",
                "9pm",
                "12am",
                "3am",
                "sunset",
                "sunrise",
                "<yellow>{player}</yellow> <gray>is down until sunrise.</gray>",
                "<yellow>{player}</yellow> <gray>fell, but is back in the fight.</gray>",
                "<yellow>{player}</yellow> <gray>fell, but will respawn right back in.</gray>",
                ":warning_red: <dark_red>The blood moon rises. Doors are no longer safe.</dark_red>",
                "<gold>The night is clear. Dawn comes faster.</gold>"
            );
        }

        static @NotNull NightfallTextFormat from(@NotNull ConfigSection section, @NotNull NightfallTextFormat defaults) {
            ConfigSection phase = section.getSection("phase");
            ConfigSection phaseLine = section.getSection("phase-line");
            ConfigSection announcement = section.getSection("announcement");
            ConfigSection milestoneLabels = announcement.getSection("time-labels");
            ConfigSection milestoneTargets = announcement.getSection("time-targets");

            return new NightfallTextFormat(
                phase.getString("waiting", defaults.waitingPhase),
                phase.getString("starting", defaults.startingPhase),
                phase.getString("resetting", defaults.resettingPhase),
                phase.getString("preparation", defaults.preparationPhase),
                phase.getString("day", defaults.dayPhase),
                phase.getString("night", defaults.nightPhase),
                phaseLine.getString("preparation", defaults.preparationPhaseLine),
                phaseLine.getString("first-day", defaults.firstDayPhaseLine),
                phaseLine.getString("respite", defaults.respitePhaseLine),
                phaseLine.getString("night", defaults.nightPhaseLine),
                announcement.getString("round-start", defaults.roundStart),
                announcement.getString("round-end", defaults.roundEnd),
                announcement.getString("day-unlocked", defaults.dayUnlocked),
                announcement.getString("night-start", defaults.nightStart),
                announcement.getString("sunrise", defaults.sunrise),
                announcement.getString("time-milestone", defaults.timeMilestone),
                milestoneLabels.getString("three-pm", defaults.threePmLabel),
                milestoneLabels.getString("sunset", defaults.sunsetLabel),
                milestoneLabels.getString("nine-pm", defaults.ninePmLabel),
                milestoneLabels.getString("midnight", defaults.midnightLabel),
                milestoneLabels.getString("three-am", defaults.threeAmLabel),
                milestoneTargets.getString("sunset", defaults.sunsetTargetLabel),
                milestoneTargets.getString("sunrise", defaults.sunriseTargetLabel),
                announcement.getString("player-downed", defaults.playerDowned),
                announcement.getString("player-returned", defaults.playerReturned),
                announcement.getString("player-respawn", defaults.playerRespawn),
                announcement.getString("blood-moon-start", defaults.bloodMoonStart),
                announcement.getString("night-clear", defaults.nightClear)
            );
        }

        boolean applyDefaults(@NotNull ConfigSection section) {
            ConfigSection phase = section.getSection("phase");
            ConfigSection phaseLine = section.getSection("phase-line");
            ConfigSection announcement = section.getSection("announcement");
            ConfigSection milestoneLabels = announcement.getSection("time-labels");
            ConfigSection milestoneTargets = announcement.getSection("time-targets");

            boolean changed = false;
            changed |= setDefault(phase, "waiting", waitingPhase);
            changed |= setDefault(phase, "starting", startingPhase);
            changed |= setDefault(phase, "resetting", resettingPhase);
            changed |= setDefault(phase, "preparation", preparationPhase);
            changed |= setDefault(phase, "day", dayPhase);
            changed |= setDefault(phase, "night", nightPhase);

            changed |= setDefault(phaseLine, "preparation", preparationPhaseLine);
            changed |= setDefault(phaseLine, "first-day", firstDayPhaseLine);
            changed |= setDefault(phaseLine, "respite", respitePhaseLine);
            changed |= setDefault(phaseLine, "night", nightPhaseLine);

            changed |= setDefault(announcement, "round-start", roundStart);
            changed |= setDefault(announcement, "round-end", roundEnd);
            changed |= setDefault(announcement, "day-unlocked", dayUnlocked);
            changed |= setDefault(announcement, "night-start", nightStart);
            changed |= setDefault(announcement, "sunrise", sunrise);
            changed |= setDefault(announcement, "time-milestone", timeMilestone);
            changed |= setDefault(milestoneLabels, "three-pm", threePmLabel);
            changed |= setDefault(milestoneLabels, "sunset", sunsetLabel);
            changed |= setDefault(milestoneLabels, "nine-pm", ninePmLabel);
            changed |= setDefault(milestoneLabels, "midnight", midnightLabel);
            changed |= setDefault(milestoneLabels, "three-am", threeAmLabel);
            changed |= setDefault(milestoneTargets, "sunset", sunsetTargetLabel);
            changed |= setDefault(milestoneTargets, "sunrise", sunriseTargetLabel);
            changed |= setDefault(announcement, "player-downed", playerDowned);
            changed |= setDefault(announcement, "player-returned", playerReturned);
            changed |= setDefault(announcement, "player-respawn", playerRespawn);
            changed |= setDefault(announcement, "blood-moon-start", bloodMoonStart);
            changed |= setDefault(announcement, "night-clear", nightClear);
            return changed;
        }

        boolean migrateLegacyDefaults(@NotNull ConfigSection section) {
            ConfigSection announcement = section.getSection("announcement");
            ConfigSection milestoneLabels = announcement.getSection("time-labels");

            boolean changed = false;
            changed |= replaceExact(announcement, "time-milestone", "{label}.", timeMilestone);
            changed |= replaceExact(milestoneLabels, "sunset", "<gold>Sunset</gold>", sunsetLabel);
            changed |= replaceExact(milestoneLabels, "nine-pm", "<gold>9pm</gold>", ninePmLabel);
            changed |= replaceExact(milestoneLabels, "midnight", "<red>Midnight</red>", midnightLabel);
            changed |= replaceExact(milestoneLabels, "three-am", "<gold>3am</gold>", threeAmLabel);
            return changed;
        }

        private static boolean setDefault(@NotNull ConfigSection section, @NotNull String key, @NotNull String value) {
            if (section.contains(key)) {
                return false;
            }
            section.set(key, value);
            return true;
        }

        private static boolean replaceExact(@NotNull ConfigSection section, @NotNull String key, @NotNull String oldValue, @NotNull String newValue) {
            if (!section.contains(key) || !oldValue.equals(section.getString(key, ""))) {
                return false;
            }
            section.set(key, newValue);
            return true;
        }
    }
}
