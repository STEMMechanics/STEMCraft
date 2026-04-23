package dev.stemcraft.minigame.bedwars;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.minigame.util.TeamNames;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BedWarsConfig {
    static final int DEFAULT_START_COUNTDOWN_SECONDS = 30;
    static final int DEFAULT_ENDING_SECONDS = 20;
    private static final int MIN_TEAMS = 2;
    private static final int MAX_TEAMS = 8;

    private final STEMCraftAPI api;
    private ConfigSection config;

    public BedWarsConfig(STEMCraftAPI api, BedWarsMiniGame bedWars) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }

    public @NotNull BedWarsArenaRecord load(@NotNull String arenaId, @NotNull ConfigSection section) {
        boolean enabled = section.getBoolean("enabled", true);

        String worldName = section.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }
        if(Bukkit.getWorld(worldName) == null) {
            throw new MiniGameInvalidArenaConfigException("World '" + worldName + "' does not exist.");
        }
        World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        Location lobby = loadLocation(section, world, arenaId, "lobby", true);
        Location spectator = loadLocation(section, world, arenaId, "spectator", false);
        if (spectator == null) {
            spectator = lobby;
        }

        SCRegion arenaRegion = loadRegion(section, world, arenaId, "arena", "Arena");
        int minPlayers = section.getInt("min-players", 2);
        int maxPlayers = section.getInt("max-players", 8);
        int startCountdownSeconds = section.getInt("start-countdown-seconds", DEFAULT_START_COUNTDOWN_SECONDS);
        int endingSeconds = section.getInt("ending-seconds", DEFAULT_ENDING_SECONDS);
        int teamSize = section.getInt("team-size", 1);
        String name = section.getString("name", StringUtil.beautify(arenaId));
        List<Material> dropItems = loadDropItems(section, arenaId);
        List<Material> dropSurfaceMaterials = loadDropSurfaceMaterials(section, arenaId);

        ConfigSection teams = section.getSection("teams", false);
        if (teams == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' does not have any teams defined.");
        }

        Set<String> teamIds = new LinkedHashSet<>(teams.getKeys(false));
        if (teamIds.size() < MIN_TEAMS || teamIds.size() > MAX_TEAMS) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' must define between 2 and 8 teams.");
        }

        Map<String, BedWarsArenaRecord.TeamDef> teamDefs = new LinkedHashMap<>();
        for (String teamId : teamIds) {
            validateTeamId(arenaId, teamId);

            ConfigSection teamSection = teams.getSection(teamId, false);
            if (teamSection == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing team '" + teamId + "'.");
            }

            String teamName = teamSection.getString("name", StringUtil.beautify(teamId));
            Location spawn = loadLocation(teamSection, world, arenaId + "/" + teamId, "spawn", true);
            SCRegion bedRegion = loadRegion(teamSection, world, arenaId + "/" + teamId, "bed", "Bed");
            teamDefs.put(teamId, new BedWarsArenaRecord.TeamDef(teamId, teamName, spawn, bedRegion));
        }

        return new BedWarsArenaRecord(
            arenaId,
            enabled,
            name,
            worldName,
            lobby,
            spectator,
            arenaRegion,
            minPlayers,
            maxPlayers,
            startCountdownSeconds,
            endingSeconds,
            teamSize,
            dropItems,
            dropSurfaceMaterials,
            teamDefs
        );
    }

    public void saveArena(@NotNull MiniGameArena arena) {
        ensureLoaded();
        validateArenaForSave(arena);

        ConfigSection arenaConfig = config.createSection("arenas." + arena.id(), true);
        arenaConfig.set("enabled", arena.getStatus() != MiniGameArena.ArenaStatus.DISABLED);
        arenaConfig.set("world", arena.world().getName());
        arenaConfig.set("name", arena.getName());
        arenaConfig.set("lobby", serializeLocation(arena.getLobbySpawn(), arena.id(), "lobby"));
        arenaConfig.set("spectator", serializeLocation(arena.getSpectatorSpawn(), arena.id(), "spectator"));
        arenaConfig.set("arena", serializeRegion(arena.get("arenaRegion", SCRegion.class), arena.id(), "arena"));
        arenaConfig.set("min-players", arena.getMinPlayers());
        arenaConfig.set("max-players", arena.getMaxPlayers());
        arenaConfig.set("start-countdown-seconds", arena.get("startCountdownSeconds", Integer.class, DEFAULT_START_COUNTDOWN_SECONDS));
        arenaConfig.set("ending-seconds", arena.get("endingSeconds", Integer.class, DEFAULT_ENDING_SECONDS));
        arenaConfig.set("team-size", arena.get("teamSize", Integer.class, 1));
        List<?> rawDropItems = arena.get("dropItems", List.class);
        arenaConfig.set("drop-items", serializeDropItems(rawDropItems, arena.id()));
        List<?> rawDropSurfaceMaterials = arena.get("dropSurfaceMaterials", List.class);
        arenaConfig.set("drop-surface-materials", serializeDropSurfaceMaterials(rawDropSurfaceMaterials, arena.id()));

        ConfigSection teamsConfig = arenaConfig.createSection("teams", true);
        for (MiniGameTeam team : arena.getTeams()) {
            validateTeamId(arena.id(), team.getName());

            ConfigSection teamConfig = teamsConfig.createSection(team.getName(), true);
            teamConfig.set("name", team.get("displayName", String.class, team.getName()));
            teamConfig.set("spawn", serializeLocation(team.getSpawn(), arena.id(), "team " + team.getName() + " spawn"));
            teamConfig.set("bed", serializeRegion(team.get("bedRegion", SCRegion.class), arena.id(), "team " + team.getName() + " bed"));
        }

        config.save();
    }

    public void deleteArena(@NotNull String arenaId) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas");
        arenas.remove(arenaId);
        config.save();
    }

    public boolean hasArena(@NotNull String arenaId) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas", false);
        return arenas != null && arenas.isSection(arenaId);
    }

    public void setArenaEnabled(@NotNull String arenaId, boolean enabled) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas", false);
        if (arenas == null || !arenas.isSection(arenaId)) {
            return;
        }

        ConfigSection arenaConfig = arenas.getSection(arenaId, false);
        if (arenaConfig == null) {
            return;
        }

        arenaConfig.set("enabled", enabled);
        config.save();
    }

    public @Nullable ConfigSection getSection(String path) {
        if (config == null) {
            return null;
        }
        return config.getSection(path, false);
    }

    static @NotNull List<Material> defaultDropItems() {
        List<Material> items = new ArrayList<>();
        items.add(Material.NETHERITE_HELMET);
        items.add(Material.NETHERITE_CHESTPLATE);
        items.add(Material.ENCHANTED_BOOK);
        items.add(Material.ENDER_PEARL);
        items.add(Material.TNT);
        items.add(Material.GOLDEN_APPLE);
        return items;
    }

    static @NotNull List<Material> defaultDropSurfaceMaterials() {
        return List.of(Material.GRASS_BLOCK);
    }

    private void validateArenaForSave(@NotNull MiniGameArena arena) {
        if (arena.getLobbySpawn() == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a lobby spawn.");
        }
        if (arena.get("arenaRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing an arena region.");
        }

        int teamSize = arena.get("teamSize", Integer.class, 1);
        if (teamSize < 1) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' must have a team size of at least 1.");
        }

        if (arena.getTeams().size() < MIN_TEAMS || arena.getTeams().size() > MAX_TEAMS) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' must define between 2 and 8 teams.");
        }

        if (arena.getMaxPlayers() > arena.getTeams().size() * teamSize) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' max players exceeds team capacity.");
        }

        for (MiniGameTeam team : arena.getTeams()) {
            validateTeamId(arena.id(), team.getName());
            if (team.getSpawn() == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a spawn for team '" + team.getName() + "'.");
            }
            if (team.get("bedRegion", SCRegion.class) == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a bed region for team '" + team.getName() + "'.");
            }
        }
    }

    private void validateTeamId(@NotNull String arenaId, @NotNull String teamId) {
        if (!TeamNames.isPredefinedName(teamId) || TeamNames.TEAM_AUTO.equals(teamId)) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has unsupported team id '" + teamId + "'.");
        }
    }

    private @NotNull SCRegion loadRegion(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId, @NotNull String key, @NotNull String title) {
        String regionString = section.getString(key);
        if (regionString.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException(title + " region for arena '" + arenaId + "' is not defined.");
        }

        SCRegion region = SCRegion.fromString(regionString, world);
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException(title + " region for arena '" + arenaId + "' is invalid.");
        }
        return region;
    }

    private @Nullable Location loadLocation(@NotNull ConfigSection section, @NotNull World world, @NotNull String arenaId, @NotNull String key, boolean required) {
        String locationString = section.getString(key);
        if (locationString.isEmpty()) {
            if (required) {
                throw new MiniGameInvalidArenaConfigException("Location '" + key + "' for arena '" + arenaId + "' is not defined.");
            }
            return null;
        }

        Location location = LocationUtil.deserialize(locationString, world);
        if (location == null && required) {
            throw new MiniGameInvalidArenaConfigException("Location '" + key + "' for arena '" + arenaId + "' is invalid.");
        }
        return location;
    }

    private @NotNull String serializeLocation(@Nullable Location location, @NotNull String arenaId, @NotNull String name) {
        if (location == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return LocationUtil.serialize(location, false, false);
    }

    private @NotNull String serializeRegion(@Nullable SCRegion region, @NotNull String arenaId, @NotNull String name) {
        if (region == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing " + name + ".");
        }
        return region.toString();
    }

    private @NotNull List<Material> loadDropItems(@NotNull ConfigSection section, @NotNull String arenaId) {
        if (!section.contains("drop-items")) {
            List<Material> defaults = defaultDropItems();
            section.set("drop-items", serializeDropItems(new ArrayList<>(defaults), arenaId));
            section.save();
            return new ArrayList<>(defaults);
        }

        List<Material> items = new ArrayList<>();
        boolean normalized = false;
        for (Object raw : section.getList("drop-items")) {
            Material material = parseDropMaterial(raw);
            if (material != null && !material.isAir()) {
                items.add(material);
                normalized |= !(raw instanceof String);
                continue;
            }

            if (raw != null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid drop item entry.");
            }
        }

        if (normalized) {
            section.set("drop-items", serializeDropItems(new ArrayList<>(items), arenaId));
            section.save();
        }

        return items;
    }

    private @NotNull List<Material> loadDropSurfaceMaterials(@NotNull ConfigSection section, @NotNull String arenaId) {
        if (!section.contains("drop-surface-materials")) {
            List<Material> defaults = defaultDropSurfaceMaterials();
            section.set("drop-surface-materials", serializeDropSurfaceMaterials(new ArrayList<>(defaults), arenaId));
            section.save();
            return new ArrayList<>(defaults);
        }

        List<Material> materials = new ArrayList<>();
        boolean normalized = false;
        for (Object raw : section.getList("drop-surface-materials")) {
            Material material = parseDropMaterial(raw);
            if (material != null && !material.isAir()) {
                materials.add(material);
                normalized |= !(raw instanceof String);
                continue;
            }

            if (raw != null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid drop surface material entry.");
            }
        }

        if (normalized) {
            section.set("drop-surface-materials", serializeDropSurfaceMaterials(new ArrayList<>(materials), arenaId));
            section.save();
        }

        return materials;
    }

    private @NotNull List<String> serializeDropItems(@Nullable List<?> rawDropItems, @NotNull String arenaId) {
        if (rawDropItems == null) {
            return List.of();
        }

        List<String> serialized = new ArrayList<>(rawDropItems.size());
        for (Object raw : rawDropItems) {
            Material material = parseDropMaterial(raw);
            if (material == null || material.isAir()) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid drop item entry.");
            }
            serialized.add(material.name());
        }
        return serialized;
    }

    private @NotNull List<String> serializeDropSurfaceMaterials(@Nullable List<?> rawDropSurfaceMaterials, @NotNull String arenaId) {
        if (rawDropSurfaceMaterials == null) {
            return List.of();
        }

        List<String> serialized = new ArrayList<>(rawDropSurfaceMaterials.size());
        for (Object raw : rawDropSurfaceMaterials) {
            Material material = parseDropMaterial(raw);
            if (material == null || material.isAir()) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' has an invalid drop surface material entry.");
            }
            serialized.add(material.name());
        }
        return serialized;
    }

    private @Nullable Material parseDropMaterial(@Nullable Object raw) {
        if (raw instanceof Material material) {
            return material;
        }
        if (raw instanceof String name) {
            String normalized = name.trim();
            Material material = Material.matchMaterial(normalized);
            if (material == null && normalized.equalsIgnoreCase("grass")) {
                return Material.GRASS_BLOCK;
            }
            return material;
        }
        if (raw instanceof org.bukkit.inventory.ItemStack item) {
            return item.getType();
        }
        return null;
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("BedWars config is not loaded.");
        }
    }
}
