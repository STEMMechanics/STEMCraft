package dev.stemcraft.minigame.boatrace;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.service.hologram.HologramTypeHandler;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import dev.stemcraft.minigame.TimedRecordLeaderboard;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BoatRaceMiniGame extends BaseMiniGame {
    private static final int HUD_LINE_HOLD_UPDATES = 3;
    private static final String RECORD_HOLOGRAM_TYPE = "boatrace_records";
    private static final String RACE_START_MILLIS_KEY = "raceStartMillis";

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "boatrace";

    private final BoatRaceConfig config;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;

    public BoatRaceMiniGame(STEMCraftAPI api) {
        super(api);
        this.config = new BoatRaceConfig(api, this);
    }

    @Override
    public void onLoad() {
        BoatRaceArenaHandler handler = new BoatRaceArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("leader", (arena, team, player) -> arena == null ? "-" : leaderName(arena))
            .registerArenaPlaceholder("winner", (arena, team, player) -> arena == null ? "-" : winnerName(arena))
            .registerArenaPlaceholder("record-time", (arena, team, player) -> arena == null ? "-" : formatMillis(arenaBestMillis(arena)))
            .registerArenaPlaceholder("record-holder", (arena, team, player) -> arena == null ? "-" : arenaBestHolder(arena))
            .registerArenaPlaceholder("stage-count", (arena, team, player) -> arena == null ? "0" : Integer.toString(stageCount(arena)))
            .registerPlayerPlaceholder("best-time", (arena, team, player) -> player == null ? "-" : formatMillis(bestTimeMillis(player)))
            .registerPlayerPlaceholder("place", (arena, team, player) -> player == null ? "-" : placeText(player))
            .registerPlayerPlaceholder("progress", (arena, team, player) -> player == null ? "0/0" : progressText(player))
            .registerPlayerPlaceholder("next-target", (arena, team, player) -> player == null ? "-" : nextTargetLabel(player))
            .registerPlayerPlaceholder("next-distance", (arena, team, player) -> player == null ? "-" : nextTargetDistance(player));

        configFile = api.config().load("boatrace.yml");
        if (configFile == null) {
            api.messages().warn("Boat Race config could not be loaded.");
            return;
        }
        configFile.setAutoSave(true);
        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());
        loadArenas();
        registerRecordHolograms();
        new BoatRaceCommand(api, this).onEnable();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                ":info_blue: <aqua>Waiting for racers</aqua> <dark_gray>•</dark_gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":clock: <gray>Record</gray> <gold>{arena:record-time}</gold> <dark_gray>•</dark_gray> <gray>Holder</gray> <aqua>{arena:record-holder}</aqua>"
            ),
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                "",
                ":info_green: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>",
                ":clock: <gray>Your Best</gray> <gold>{player:best-time}</gold>",
                ":location: <gray>Record Holder</gray> <aqua>{arena:record-holder}</aqua>",
                ":click_action_right: <gray>Record</gray> <gold>{arena:record-time}</gold>",
                ":warning_yellow: <gray>Checkpoints</gray> <yellow>{arena:stage-count}</yellow>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                ":click_action_right: <gold>Race starts in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>Grid Position</gray> <aqua>{player:place}</aqua>"
            ),
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                "",
                ":click_action_right: <gold>Starts In</gold> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>Grid Position</gray> <aqua>{player:place}</aqua>",
                ":warning_yellow: <gray>Checkpoints</gray> <yellow>{arena:stage-count}</yellow>",
                ":info_green: <gray>Players</gray> <green>{arena:joined-players}</green>/<green>{arena:max-players}</green>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                ":location: <gray>Place</gray> <gold>{player:place}</gold> <dark_gray>•</dark_gray> <gray>Leader</gray> <aqua>{arena:leader}</aqua>",
                ":warning_yellow: <gray>Progress</gray> <yellow>{player:progress}</yellow>",
                ":world: <gray>Next</gray> <aqua>{player:next-target}</aqua>"
            ),
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                "",
                ":location: <gray>Your Place</gray> <gold>{player:place}</gold>",
                ":info_green: <gray>Leader</gray> <aqua>{arena:leader}</aqua>",
                ":warning_yellow: <gray>Progress</gray> <yellow>{player:progress}</yellow>",
                ":world: <gray>Next Target</gray> <aqua>{player:next-target}</aqua>",
                ":compass: <gray>Distance</gray> <aqua>{player:next-distance}</aqua>",
                ":click_action_right: <gray>Time Left</gray> <gold>{arena:time-remaining}</gold>"
            ),
            HUD_LINE_HOLD_UPDATES
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                ":info_green: <gold>Winner</gold> <yellow>{arena:winner}</yellow>",
                ":warning_yellow: <gray>Reset in</gray> <yellow>{arena:time-remaining}</yellow>"
            ),
            List.of(
                "<gradient:#06b6d4:#3b82f6><bold>{arena:name}</bold></gradient>",
                "",
                ":info_green: <gold>Winner</gold> <yellow>{arena:winner}</yellow>",
                ":warning_yellow: <gray>Reset In</gray> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>Your Place</gray> <gold>{player:place}</gold>"
            ),
            HUD_LINE_HOLD_UPDATES
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
            .set("arenaRegion", null)
            .set("finishRegion", null)
            .set("stageRegions", new ArrayList<SCRegion>())
            .set("startingGrid", new ArrayList<Location>())
            .set("joinOrder", new ArrayList<UUID>())
            .set("assignedGridSlots", new LinkedHashMap<UUID, Integer>())
            .set("boatAssignments", new LinkedHashMap<UUID, UUID>())
            .set("checkpointLocations", new LinkedHashMap<UUID, Location>())
            .set("stageProgress", new LinkedHashMap<UUID, Integer>())
            .set("bestTimes", new LinkedHashMap<UUID, BoatRaceArenaRecord.BestTime>());
    }

    public void deleteArena(@NotNull String arenaId) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena != null) {
            minigame.removeArena(arenaId);
        }
        config.deleteArena(arenaId);
        api.holograms().delete(RECORD_HOLOGRAM_TYPE, arenaId);
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        config.saveArena(arena);
        api.holograms().update(RECORD_HOLOGRAM_TYPE, arena.id());
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
        unloadArenas(minigame);
        loadArenas();
        api.holograms().update(RECORD_HOLOGRAM_TYPE, null);
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
                BoatRaceArenaRecord arenaDef = config.load(arenaId, arenaSection);
                MiniGameArena arena = minigame.createArena(arenaId, arenaDef.world())
                    .setName(arenaDef.name())
                    .setLobbySpawn(arenaDef.lobby())
                    .setSpectatorSpawn(arenaDef.spectator())
                    .setRegion(arenaDef.arenaRegion())
                    .setMinPlayers(arenaDef.minPlayers())
                    .setMaxPlayers(arenaDef.maxPlayers())
                    .set("arenaRegion", arenaDef.arenaRegion())
                    .set("finishRegion", arenaDef.finishRegion())
                    .set("stageRegions", new ArrayList<>(arenaDef.stages()))
                    .set("startingGrid", new ArrayList<>(arenaDef.startingGrid()))
                    .set("joinOrder", new ArrayList<UUID>())
                    .set("assignedGridSlots", new LinkedHashMap<UUID, Integer>())
                    .set("boatAssignments", new LinkedHashMap<UUID, UUID>())
                    .set("checkpointLocations", new LinkedHashMap<UUID, Location>())
                    .set("stageProgress", new LinkedHashMap<UUID, Integer>())
                    .set("bestTimes", new LinkedHashMap<>(arenaDef.bestTimes()));

                ArenaValidationResult result = arena.validate();
                if (result.hasErrors()) {
                    api.messages().error("Boat Race arena '" + arenaId + "' has validation errors and will be disabled:");
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
                api.messages().error("Failed to load Boat Race arena '" + arenaId + "': " + exception.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<SCRegion> stageRegions(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("stageRegions", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<Location> startingGrid(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("startingGrid", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<UUID> joinOrder(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("joinOrder", List.class, ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull Map<UUID, Integer> assignedGridSlots(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("assignedGridSlots", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull Map<UUID, UUID> boatAssignments(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("boatAssignments", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull Map<UUID, Location> checkpointLocations(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("checkpointLocations", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull Map<UUID, Integer> stageProgress(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("stageProgress", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull Map<UUID, BoatRaceArenaRecord.BestTime> bestTimes(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("bestTimes", Map.class, LinkedHashMap::new);
    }

    public int stageCount(@NotNull MiniGameArena arena) {
        return stageRegions(arena).size();
    }

    public int stageProgress(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return 0;
        }
        return stageProgress(arena).getOrDefault(player.getPlayer().getUniqueId(), 0);
    }

    public String progressText(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "0/0";
        }
        return stageProgress(player) + "/" + stageCount(arena);
    }

    public String nextTargetLabel(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "-";
        }

        int progress = stageProgress(player);
        int stageCount = stageCount(arena);
        if (progress < stageCount) {
            return "Checkpoint " + (progress + 1);
        }
        return "Finish";
    }

    public String nextTargetDistance(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "-";
        }

        Location nextTarget = nextTargetLocation(arena, player.getPlayer().getUniqueId());
        if (nextTarget == null || nextTarget.getWorld() == null || player.getPlayer().getWorld() == null
            || !nextTarget.getWorld().equals(player.getPlayer().getWorld())) {
            return "-";
        }

        double distance = player.getPlayer().getLocation().distance(nextTarget);
        int rounded = Math.max(1, (int) Math.round(distance));
        return rounded + (rounded == 1 ? " block" : " blocks");
    }

    public String leaderName(@NotNull MiniGameArena arena) {
        RaceStanding leader = standings(arena).stream().findFirst().orElse(null);
        if (leader == null || leader.player() == null) {
            return "-";
        }
        return leader.player().getName();
    }

    public String winnerName(@NotNull MiniGameArena arena) {
        String winnerName = arena.get("winnerName", String.class);
        return winnerName == null || winnerName.isBlank() ? "-" : winnerName;
    }

    public long bestTimeMillis(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return 0L;
        }
        BoatRaceArenaRecord.BestTime bestTime = bestTimes(arena).get(player.getPlayer().getUniqueId());
        return bestTime == null ? 0L : bestTime.timeMillis();
    }

    public long arenaBestMillis(@NotNull MiniGameArena arena) {
        BoatRaceArenaRecord.BestTime bestTime = arenaBest(arena);
        return bestTime == null ? 0L : bestTime.timeMillis();
    }

    public @NotNull String arenaBestHolder(@NotNull MiniGameArena arena) {
        BoatRaceArenaRecord.BestTime bestTime = arenaBest(arena);
        if (bestTime == null || bestTime.playerName() == null || bestTime.playerName().isBlank()) {
            return "-";
        }
        return bestTime.playerName();
    }

    public String placeText(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "-";
        }

        List<RaceStanding> standings = standings(arena);
        UUID uuid = player.getPlayer().getUniqueId();
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).uuid().equals(uuid)) {
                return ordinal(i + 1);
            }
        }
        return "-";
    }

    public @Nullable Location nextTargetLocation(@NotNull MiniGameArena arena, @NotNull UUID playerId) {
        int progress = stageProgress(arena).getOrDefault(playerId, 0);
        List<SCRegion> stages = stageRegions(arena);
        if (progress < stages.size()) {
            return centerOfRegion(stages.get(progress));
        }

        SCRegion finishRegion = arena.get("finishRegion", SCRegion.class);
        return finishRegion == null ? null : centerOfRegion(finishRegion);
    }

    public @NotNull List<RaceStanding> standings(@NotNull MiniGameArena arena) {
        List<RaceStanding> standings = new ArrayList<>();
        UUID winner = arena.get("winnerUuid", UUID.class);

        for (Player player : arena.getPlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }

            UUID uuid = player.getUniqueId();
            int progress = stageProgress(arena).getOrDefault(uuid, 0);
            Location nextTarget = nextTargetLocation(arena, uuid);
            double distance = nextTarget == null ? Double.MAX_VALUE : player.getLocation().distanceSquared(nextTarget);
            boolean finished = winner != null && winner.equals(uuid);
            standings.add(new RaceStanding(uuid, player, progress, distance, finished));
        }

        standings.sort(Comparator
            .comparing(RaceStanding::finished).reversed()
            .thenComparingInt(RaceStanding::progress).reversed()
            .thenComparingDouble(RaceStanding::distanceSquared)
            .thenComparing(standing -> standing.player().getName(), String.CASE_INSENSITIVE_ORDER));
        return standings;
    }

    public void setWinner(@NotNull MiniGameArena arena, @Nullable Player player) {
        if (player == null) {
            arena.remove("winnerUuid");
            arena.remove("winnerName");
            return;
        }
        arena.set("winnerUuid", player.getUniqueId());
        arena.set("winnerName", player.getName());
    }

    public long raceStartMillis(@NotNull MiniGameArena arena) {
        return arena.get(RACE_START_MILLIS_KEY, Long.class, 0L);
    }

    public void markRaceStarted(@NotNull MiniGameArena arena) {
        arena.set(RACE_START_MILLIS_KEY, System.currentTimeMillis());
    }

    public void clearRaceTimer(@NotNull MiniGameArena arena) {
        arena.remove(RACE_START_MILLIS_KEY);
    }

    public @NotNull FinishRecord recordFinish(@NotNull MiniGameArena arena, @NotNull Player player, long durationMillis) {
        BoatRaceArenaRecord.BestTime existing = bestTimes(arena).get(player.getUniqueId());
        BoatRaceArenaRecord.BestTime arenaRecord = arenaBest(arena);
        long previousBestMillis = existing == null ? 0L : existing.timeMillis();
        boolean personalBest = durationMillis > 0L && (existing == null || durationMillis < existing.timeMillis());
        boolean arenaBest = durationMillis > 0L && (arenaRecord == null || durationMillis < arenaRecord.timeMillis());

        if (personalBest) {
            bestTimes(arena).put(player.getUniqueId(), new BoatRaceArenaRecord.BestTime(player.getUniqueId(), player.getName(), durationMillis));
            saveArena(arena);
        }

        return new FinishRecord(durationMillis, personalBest, arenaBest, previousBestMillis);
    }

    public @NotNull String formatMillis(long durationMillis) {
        return TimedRecordLeaderboard.formatMillis(durationMillis);
    }

    private String ordinal(int value) {
        int mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return value + "th";
        }
        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }

    private @Nullable Location centerOfRegion(@Nullable SCRegion region) {
        if (region == null) {
            return null;
        }
        Location min = region.getMinimumLocation();
        Location max = region.getMaximumLocation();
        if (min == null || max == null || min.getWorld() == null) {
            return null;
        }
        return new Location(
            min.getWorld(),
            (min.getX() + max.getX() + 1.0d) / 2.0d,
            (min.getY() + max.getY() + 1.0d) / 2.0d,
            (min.getZ() + max.getZ() + 1.0d) / 2.0d
        );
    }

    private @Nullable BoatRaceArenaRecord.BestTime arenaBest(@NotNull MiniGameArena arena) {
        return bestTimes(arena).values().stream()
            .min(Comparator
                .comparingLong(BoatRaceArenaRecord.BestTime::timeMillis)
                .thenComparing(BoatRaceArenaRecord.BestTime::playerName, String.CASE_INSENSITIVE_ORDER))
            .orElse(null);
    }

    private void registerRecordHolograms() {
        api.holograms().registerType(RECORD_HOLOGRAM_TYPE, new HologramTypeHandler() {
            @Override
            public List<String> list(@NotNull String type) {
                return minigame.arenas().stream()
                    .map(MiniGameArena::id)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            }

            @Override
            public @NotNull List<String> lines(@NotNull String type, @NotNull String context, int id, @NotNull List<String> data) {
                return renderRecordHologram(context, data);
            }
        });
    }

    private @NotNull List<String> renderRecordHologram(@NotNull String arenaId, @NotNull List<String> data) {
        MiniGameArena arena = minigame.arena(arenaId);
        if (arena == null) {
            return List.of("<red>Unknown Boat Race arena:</red> <yellow>" + arenaId + "</yellow>");
        }

        List<TimedRecordLeaderboard.Entry> entries = bestTimes(arena).values().stream()
            .map(bestTime -> new TimedRecordLeaderboard.Entry(bestTime.playerName(), bestTime.timeMillis()))
            .toList();

        return TimedRecordLeaderboard.render("<gold>" + arena.getName() + " Fastest Times</gold>", data, entries);
    }

    public record RaceStanding(
        @NotNull UUID uuid,
        @NotNull Player player,
        int progress,
        double distanceSquared,
        boolean finished
    ) {}

    public record FinishRecord(
        long durationMillis,
        boolean personalBest,
        boolean arenaBest,
        long previousBestMillis
    ) { }
}
