package dev.stemcraft.minigame.skyblock;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.BaseMiniGame;
import dev.stemcraft.minigame.MiniGameHudConfigSupport;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SkyBlockMiniGame extends BaseMiniGame {
    private static final String STANDBY_TASK_ID = "skyblock-standby-maintainer";
    private static final String VOID_GENERATOR_KEY = "void";
    private static final GameRule<Boolean> DO_DAYLIGHT_CYCLE_RULE = requireGameRule("DO_DAYLIGHT_CYCLE", Boolean.class);
    private static final GameRule<Boolean> DO_WEATHER_CYCLE_RULE = requireGameRule("DO_WEATHER_CYCLE", Boolean.class);
    private static final GameRule<Boolean> KEEP_INVENTORY_RULE = requireGameRule("KEEP_INVENTORY", Boolean.class);
    private static final GameRule<Boolean> ANNOUNCE_ADVANCEMENTS_RULE = requireGameRule("ANNOUNCE_ADVANCEMENTS", Boolean.class);
    private static final GameRule<Boolean> DO_INSOMNIA_RULE = requireGameRule("DO_INSOMNIA", Boolean.class);

    @Getter
    @Accessors(fluent = true)
    private static final String namespace = "skyblock";

    private final SkyBlockConfig config;
    private final Set<String> standbyWorlds = new LinkedHashSet<>();

    @Getter
    @Accessors(fluent = true)
    private MiniGame minigame;

    private ConfigFile configFile;

    public SkyBlockMiniGame(STEMCraftAPI api) {
        super(api);
        this.config = new SkyBlockConfig(api, this);
    }

    @Override
    public void onLoad() {
        if (!api.worlds().generator().isRegistered(VOID_GENERATOR_KEY)) {
            api.messages().warn("SkyBlock minigame is disabled because world generator '" + VOID_GENERATOR_KEY + "' is unavailable.");
            return;
        }

        SkyBlockArenaHandler handler = new SkyBlockArenaHandler(api, this);

        minigame = createMiniGame(namespace, handler)
            .registerArenaPlaceholder("owner", (arena, team, player) -> arena == null ? "-" : ownerName(arena))
            .registerArenaPlaceholder("world", (arena, team, player) -> arena == null ? "-" : arena.world().getName())
            .registerPlayerPlaceholder("state", (arena, team, player) -> player == null ? "offline" : playerState(player));

        configFile = api.config().load("skyblock.yml");
        if (configFile == null) {
            api.messages().warn("SkyBlock config could not be loaded.");
            return;
        }
        configFile.setAutoSave(true);
        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());

        new SkyBlockCommand(api, this).onEnable();
        loadArenas();
        refreshStandbyWorlds();
        ensureStandbyWorlds(true);
        api.tasks().repeating(STANDBY_TASK_ID, 40L, 200L, () -> ensureStandbyWorlds(false));
    }

    @Override
    protected boolean disablesHungerByDefault() {
        return false;
    }

    private @NotNull Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> defaultHudDefinitions() {
        Map<MiniGameArena.ArenaStatus, MiniGameHudConfigSupport.HudDefinition> definitions = new LinkedHashMap<>();
        definitions.put(MiniGameArena.ArenaStatus.RUNNING, new MiniGameHudConfigSupport.HudDefinition(
            List.of(
                "<gradient:#22c55e:#16a34a><bold>SkyBlock</bold></gradient>",
                ":house: <gray>Island</gray> <green>{arena:owner}</green>",
                ":world: <gray>World</gray> <green>{arena:world}</green>"
            ),
            List.of(
                "<gradient:#22c55e:#16a34a><bold>SkyBlock</bold></gradient>",
                "",
                ":house: <gray>Owner</gray> <green>{arena:owner}</green>",
                ":world: <gray>World</gray> <green>{arena:world}</green>",
                ":location: <gray>Status</gray> <green>{player:state}</green>"
            ),
            2,
            "GREEN"
        ));
        return definitions;
    }

    public @NotNull MiniGameArena createOrResumeGame(@NotNull Player owner) {
        MiniGameArena arena = findArenaByOwner(owner.getUniqueId());
        if (arena != null) {
            return arena;
        }

        World standbyWorld = allocateStandbyWorld(true);
        if (standbyWorld == null) {
            throw new IllegalStateException("No SkyBlock standby worlds are available.");
        }

        String arenaId = owner.getUniqueId().toString();
        Location spawn = islandSpawn(standbyWorld);
        arena = minigame.createArena(arenaId, standbyWorld)
            .setName(owner.getName() + "'s SkyBlock")
            .setLobbySpawn(spawn)
            .setSpectatorSpawn(spawn)
            .setMinPlayers(1)
            .setMaxPlayers(1)
            .set("ownerUuid", owner.getUniqueId().toString())
            .set("ownerName", owner.getName())
            .set("allowRunningJoin", true)
            .set("savedPlayerState", null)
            .set("endingGame", false)
            .set("skipStateCapture", false);
        arena.setStatus(MiniGameArena.ArenaStatus.RUNNING);
        saveArenaState(arena, null);
        ensureStandbyWorlds(false);
        return arena;
    }

    public void loadArenas() {
        for (SkyBlockArenaRecord record : config.loadArenas()) {
            MiniGameArena existing = minigame.arena(record.arenaId());
            if (existing != null) {
                continue;
            }

            MiniGameArena arena = minigame.createArena(record.arenaId(), record.world())
                .setName(record.ownerName() + "'s SkyBlock")
                .setLobbySpawn(record.islandSpawn())
                .setSpectatorSpawn(record.islandSpawn())
                .setMinPlayers(1)
                .setMaxPlayers(1)
                .set("ownerUuid", record.ownerUuid().toString())
                .set("ownerName", record.ownerName())
                .set("allowRunningJoin", true)
                .set("savedPlayerState", record.playerState())
                .set("endingGame", false)
                .set("skipStateCapture", false);
            arena.setStatus(MiniGameArena.ArenaStatus.RUNNING);
        }
    }

    public boolean reloadFromConfig() {
        if (!reloadConfigFile(configFile)) {
            return false;
        }

        for (MiniGameArena arena : minigame.arenas()) {
            if (!arena.getOccupants().isEmpty()) {
                return false;
            }
        }

        config.onEnable(configFile);
        MiniGameHudConfigSupport.apply(minigame, configFile, defaultHudDefinitions());
        unloadArenas(minigame);
        standbyWorlds.clear();
        loadArenas();
        refreshStandbyWorlds();
        ensureStandbyWorlds(true);
        return true;
    }

    public void endGame(@NotNull MiniGameArena arena, @Nullable String message) {
        if (arena.get("endingGame", Boolean.class, false)) {
            return;
        }

        arena.set("endingGame", true);
        arena.set("skipStateCapture", true);
        arena.set("savedPlayerState", null);
        config.deleteArena(arena.id());

        for (Player occupant : new ArrayList<>(arena.getOccupants())) {
            if (message != null && !message.isBlank()) {
                arena.warn(occupant, message);
            }
            arena.removeOccupant(occupant);
        }

        String worldName = arena.world().getName();
        minigame.removeArena(arena.id());
        deleteWorld(worldName);
        ensureStandbyWorlds(false);
    }

    public void saveArenaState(@NotNull MiniGameArena arena, @Nullable SkyBlockPlayerState playerState) {
        arena.set("savedPlayerState", playerState);
        config.saveArena(arena, playerState);
    }

    public @Nullable SkyBlockPlayerState savedPlayerState(@NotNull MiniGameArena arena) {
        return arena.get("savedPlayerState", SkyBlockPlayerState.class);
    }

    public @NotNull String ownerName(@NotNull MiniGameArena arena) {
        return arena.get("ownerName", String.class, arena.id());
    }

    public @Nullable UUID ownerUuid(@NotNull MiniGameArena arena) {
        String raw = arena.get("ownerUuid", String.class, "");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isOwner(@NotNull MiniGameArena arena, @NotNull Player player) {
        UUID ownerUuid = ownerUuid(arena);
        return ownerUuid != null && ownerUuid.equals(player.getUniqueId());
    }

    public @Nullable MiniGameArena findArenaByOwner(@NotNull UUID ownerUuid) {
        for (MiniGameArena arena : minigame.arenas()) {
            UUID arenaOwner = ownerUuid(arena);
            if (arenaOwner != null && arenaOwner.equals(ownerUuid)) {
                return arena;
            }
        }
        return null;
    }

    public @NotNull List<String> standbyWorlds() {
        return new ArrayList<>(standbyWorlds);
    }

    private void refreshStandbyWorlds() {
        standbyWorlds.clear();

        Set<String> activeWorlds = new LinkedHashSet<>();
        for (MiniGameArena arena : minigame.arenas()) {
            activeWorlds.add(arena.world().getName());
        }

        for (String worldName : config.standbyWorlds()) {
            if (activeWorlds.contains(worldName)) {
                continue;
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                world = api.worlds().loadWorld(worldName);
            }
            if (world == null) {
                continue;
            }
            buildStarterIsland(world);
            standbyWorlds.add(worldName);
        }

        persistStandbyWorlds();
    }

    private void ensureStandbyWorlds(boolean force) {
        int target = config.standbyWorldTarget();
        while (standbyWorlds.size() < target) {
            if (!force && Bukkit.getOnlinePlayers().size() > config.preferredIdleOnlinePlayers()) {
                return;
            }
            World created = createStandbyWorld();
            if (created == null) {
                return;
            }
            standbyWorlds.add(created.getName());
            persistStandbyWorlds();
        }
    }

    private @Nullable World allocateStandbyWorld(boolean immediate) {
        if (standbyWorlds.isEmpty() && immediate) {
            World created = createStandbyWorld();
            if (created != null) {
                standbyWorlds.add(created.getName());
                persistStandbyWorlds();
            }
        }

        if (standbyWorlds.isEmpty()) {
            return null;
        }

        String worldName = standbyWorlds.iterator().next();
        standbyWorlds.remove(worldName);
        persistStandbyWorlds();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = api.worlds().loadWorld(worldName);
        }
        if (world != null) {
            buildStarterIsland(world);
        }
        return world;
    }

    private @Nullable World createStandbyWorld() {
        String worldName = config.worldPrefix() + config.nextWorldSequence();
        World world = api.worlds().createWorld(worldName, VOID_GENERATOR_KEY);
        if (world == null) {
            api.messages().warn("Failed to create SkyBlock standby world '" + worldName + "'.");
            return null;
        }
        configureWorld(world);
        buildStarterIsland(world);
        return world;
    }

    private void configureWorld(@NotNull World world) {
        world.setGameRule(DO_DAYLIGHT_CYCLE_RULE, false);
        world.setGameRule(DO_WEATHER_CYCLE_RULE, false);
        world.setGameRule(KEEP_INVENTORY_RULE, false);
        world.setGameRule(ANNOUNCE_ADVANCEMENTS_RULE, false);
        world.setGameRule(DO_INSOMNIA_RULE, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setAutoSave(true);
        world.setSpawnLocation(0, config.islandY() + 2, 0);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.0d, 0.0d);
        border.setSize(256.0d);
    }

    private void buildStarterIsland(@NotNull World world) {
        world.getChunkAt(0, 0).load();
        int y = config.islandY();

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Material top = Math.abs(x) <= 2 && Math.abs(z) <= 2 ? Material.GRASS_BLOCK : Material.DIRT;
                world.getBlockAt(x, y, z).setType(top, false);
                world.getBlockAt(x, y - 1, z).setType(Material.DIRT, false);
            }
        }
        world.getBlockAt(0, y - 2, 0).setType(Material.BEDROCK, false);

        for (int trunkY = y + 1; trunkY <= y + 3; trunkY++) {
            world.getBlockAt(-1, trunkY, -1).setType(Material.OAK_LOG, false);
        }
        for (int x = -3; x <= 1; x++) {
            for (int z = -3; z <= 1; z++) {
                if (Math.abs(x + 1) + Math.abs(z + 1) > 3) {
                    continue;
                }
                world.getBlockAt(x, y + 4, z).setType(Material.OAK_LEAVES, false);
            }
        }
        world.getBlockAt(-1, y + 5, -1).setType(Material.OAK_LEAVES, false);

        world.getBlockAt(2, y + 1, 2).setType(Material.CHEST, false);
        if (world.getBlockAt(2, y + 1, 2).getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
            chest.getBlockInventory().addItem(new ItemStack(Material.LAVA_BUCKET));
            chest.getBlockInventory().addItem(new ItemStack(Material.WATER_BUCKET));
            chest.getBlockInventory().addItem(new ItemStack(Material.OAK_SAPLING, 2));
            chest.getBlockInventory().addItem(new ItemStack(Material.SUGAR_CANE, 2));
            chest.getBlockInventory().addItem(new ItemStack(Material.MELON_SEEDS));
            chest.getBlockInventory().addItem(new ItemStack(Material.PUMPKIN_SEEDS));
            chest.getBlockInventory().addItem(new ItemStack(Material.CACTUS));
            chest.getBlockInventory().addItem(new ItemStack(Material.BONE_MEAL, 4));
            chest.update(true, false);
        }
    }

    public @NotNull Location islandSpawn(@NotNull World world) {
        return new Location(world, 0.5d, config.islandY() + 2.0d, 0.5d, 90.0f, 0.0f);
    }

    private void deleteWorld(@NotNull String worldName) {
        standbyWorlds.remove(worldName);
        persistStandbyWorlds();
        try {
            api.worlds().deleteWorld(worldName);
        } catch (Exception exception) {
            api.messages().error("Failed to delete SkyBlock world '" + worldName + "'.", exception);
        }
    }

    private void persistStandbyWorlds() {
        config.setStandbyWorlds(standbyWorlds);
    }

    private @NotNull String playerState(@NotNull dev.stemcraft.api.minigame.MiniGamePlayer player) {
        MiniGameArena arena = player.arena();
        if (arena == null) {
            return "offline";
        }
        if (arena.hasSpectator(player.getPlayer())) {
            return "spectating";
        }
        return "playing";
    }

    @SuppressWarnings("unchecked")
    private static <T> GameRule<T> requireGameRule(String name, Class<T> type) {
        try {
            Object value = GameRule.class.getField(name).get(null);
            if (!(value instanceof GameRule<?> rule) || !type.equals(rule.getType())) {
                throw new IllegalStateException("Missing expected gamerule " + name);
            }

            return (GameRule<T>) rule;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing expected gamerule " + name, exception);
        }
    }
}
