package dev.stemcraft.minigame.tntrun;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGamePlayer;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TntRunMiniGame extends BaseMiniGame {
    private static final int HUD_LINE_HOLD_UPDATES = 2;

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "tntrun";

    private final TntRunConfig config;

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;

    public TntRunMiniGame(STEMCraftAPI api) {
        super(api);
        this.config = new TntRunConfig(api, this);
    }

    @Override
    public void onLoad() {
        TntRunArenaHandler handler = new TntRunArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("winner", (arena, team, player) -> arena == null ? "-" : winnerName(arena))
            .registerArenaPlaceholder("alive", (arena, team, player) -> arena == null ? "0" : Integer.toString(arena.numPlayers()))
            .registerArenaPlaceholder("floor-count", (arena, team, player) -> arena == null ? "0" : Integer.toString(floorRegions(arena).size()))
            .registerPlayerPlaceholder("state", (arena, team, player) -> player == null ? "out" : playerState(player))
            .registerPlayerPlaceholder("grid-slot", (arena, team, player) -> player == null ? "-" : gridSlot(player));

        configFile = api.config().load("tntrun.yml");
        if (configFile == null) {
            api.messages().warn("TNT Run config could not be loaded.");
            return;
        }
        configFile.setAutoSave(true);
        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());

        new TntRunCommand(api, this).onEnable();
        loadArenas();
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.WAITING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                ":warning_yellow: <gold>Waiting for runners</gold> <dark_gray>•</dark_gray> <yellow>{arena:joined-players}</yellow>/<yellow>{arena:max-players}</yellow>"
            ),
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":info_green: <gray>Players</gray> <yellow>{arena:joined-players}</yellow>/<yellow>{arena:max-players}</yellow>",
                ":warning_yellow: <gray>Floors</gray> <gold>{arena:floor-count}</gold>",
                ":location: <gray>Status</gray> <green>Waiting</green>"
            ),
            HUD_LINE_HOLD_UPDATES,
            "YELLOW"
        ));
        definitions.put(MiniGameArena.ArenaStatus.STARTING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                ":click_action_right: <gold>Round starts in</gold> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>Grid Slot</gray> <gold>{player:grid-slot}</gold>"
            ),
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":click_action_right: <gold>Starts In</gold> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>Grid Slot</gray> <gold>{player:grid-slot}</gold>",
                ":info_green: <gray>Alive</gray> <yellow>{arena:alive}</yellow>"
            ),
            HUD_LINE_HOLD_UPDATES,
            "GOLD"
        ));
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                ":warning_yellow: <gray>Alive</gray> <gold>{arena:alive}</gold>",
                ":click_action_right: <gray>Collapse</gray> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>You</gray> <gold>{player:state}</gold>"
            ),
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":warning_yellow: <gray>Alive</gray> <gold>{arena:alive}</gold>",
                ":click_action_right: <gray>Time Left</gray> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>Status</gray> <gold>{player:state}</gold>"
            ),
            HUD_LINE_HOLD_UPDATES,
            "RED"
        ));
        definitions.put(MiniGameArena.ArenaStatus.ENDING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                ":info_green: <gold>Winner</gold> <yellow>{arena:winner}</yellow>",
                ":warning_yellow: <gray>Reset in</gray> <yellow>{arena:time-remaining}</yellow>"
            ),
            List.of(
                "<gradient:#f97316:#ef4444><bold>{arena:name}</bold></gradient>",
                "",
                ":info_green: <gold>Winner</gold> <yellow>{arena:winner}</yellow>",
                ":warning_yellow: <gray>Reset In</gray> <yellow>{arena:time-remaining}</yellow>",
                ":location: <gray>You</gray> <gold>{player:state}</gold>"
            ),
            HUD_LINE_HOLD_UPDATES,
            "GREEN"
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
            .setMinPlayers(2)
            .setMaxPlayers(16)
            .set("arenaRegion", null)
            .set("floorRegions", new ArrayList<SCRegion>())
            .set("startingGrid", new ArrayList<Location>())
            .set("startCountdownSeconds", 10)
            .set("roundSeconds", 180)
            .set("endingSeconds", 8)
            .set("fadeDelayTicks", 8)
            .set("voidY", world.getMinHeight())
            .set("joinOrder", new ArrayList<UUID>())
            .set("assignedSpawnSlots", new LinkedHashMap<UUID, Integer>())
            .set("eliminatedPlayers", new LinkedHashSet<UUID>())
            .set("winnerName", "-");
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
                TntRunArenaRecord arenaDef = config.load(arenaId, arenaSection);
                MiniGameArena arena = minigame.createArena(arenaId, arenaDef.world())
                    .setName(arenaDef.name())
                    .setLobbySpawn(arenaDef.lobby())
                    .setSpectatorSpawn(arenaDef.spectator())
                    .setRegion(arenaDef.arenaRegion())
                    .setMinPlayers(arenaDef.minPlayers())
                    .setMaxPlayers(arenaDef.maxPlayers())
                    .set("arenaRegion", arenaDef.arenaRegion())
                    .set("floorRegions", new ArrayList<>(arenaDef.floorRegions()))
                    .set("startingGrid", new ArrayList<>(arenaDef.startingGrid()))
                    .set("startCountdownSeconds", arenaDef.startCountdownSeconds())
                    .set("roundSeconds", arenaDef.roundSeconds())
                    .set("endingSeconds", arenaDef.endingSeconds())
                    .set("fadeDelayTicks", arenaDef.fadeDelayTicks())
                    .set("voidY", arenaDef.voidY())
                    .set("joinOrder", new ArrayList<UUID>())
                    .set("assignedSpawnSlots", new LinkedHashMap<UUID, Integer>())
                    .set("eliminatedPlayers", new LinkedHashSet<UUID>())
                    .set("winnerName", "-");

                ArenaValidationResult result = arena.validate();
                if (result.hasErrors()) {
                    api.messages().error("TNT Run arena '" + arenaId + "' has validation errors and will be disabled:");
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
                api.messages().error("Failed to load TNT Run arena '" + arenaId + "': " + exception.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public @NotNull List<SCRegion> floorRegions(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("floorRegions", List.class, ArrayList::new);
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
    public @NotNull Map<UUID, Integer> assignedSpawnSlots(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("assignedSpawnSlots", Map.class, LinkedHashMap::new);
    }

    @SuppressWarnings("unchecked")
    public @NotNull Set<UUID> eliminatedPlayers(@NotNull MiniGameArena arena) {
        return arena.getOrCreate("eliminatedPlayers", Set.class, LinkedHashSet::new);
    }

    public void registerJoinOrder(@NotNull MiniGameArena arena, @NotNull Player player) {
        List<UUID> joinOrder = joinOrder(arena);
        joinOrder.remove(player.getUniqueId());
        joinOrder.add(player.getUniqueId());
    }

    public void unregisterJoinOrder(@NotNull MiniGameArena arena, @NotNull Player player) {
        joinOrder(arena).remove(player.getUniqueId());
        assignedSpawnSlots(arena).remove(player.getUniqueId());
        eliminatedPlayers(arena).remove(player.getUniqueId());
    }

    public void resetRoundState(@NotNull MiniGameArena arena) {
        joinOrder(arena).removeIf(uuid -> {
            Player player = player(uuid);
            return player == null || !arena.hasOccupant(player);
        });
        assignedSpawnSlots(arena).clear();
        eliminatedPlayers(arena).clear();
        setWinner(arena, null);
        for (Player player : arena.getPlayers()) {
            MiniGamePlayer mgPlayer = arena.getPlayer(player);
            if (mgPlayer != null) {
                mgPlayer.setDeaths(0);
                mgPlayer.setKills(0);
                mgPlayer.setScore(0);
            }
        }
    }

    public void setWinner(@NotNull MiniGameArena arena, @Nullable Player winner) {
        arena.set("winnerName", winner == null ? "-" : winner.getName());
    }

    public @NotNull String winnerName(@NotNull MiniGameArena arena) {
        return arena.get("winnerName", String.class, "-");
    }

    private @NotNull String playerState(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "out";
        }
        return arena.hasSpectator(player.getPlayer()) ? "spectating" : "alive";
    }

    private @NotNull String gridSlot(@NotNull MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "-";
        }
        Integer slot = assignedSpawnSlots(arena).get(player.getPlayer().getUniqueId());
        return slot == null ? "-" : Integer.toString(slot + 1);
    }

    private @Nullable Player player(@NotNull UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }
}
