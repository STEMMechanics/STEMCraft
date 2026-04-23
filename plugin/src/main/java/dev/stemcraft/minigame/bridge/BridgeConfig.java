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
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameTeam;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.MiniGameConfigSupport;
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

public class BridgeConfig {
    private static final List<String> REQUIRED_TEAM_IDS = List.of("red", "blue");

    private final STEMCraftAPI api;
    private ConfigSection config;

    public BridgeConfig(STEMCraftAPI api, BridgeMiniGame bridge) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }

    public @NotNull BridgeArenaRecord load(@NotNull String arenaId, @NotNull ConfigSection section) throws MiniGameInvalidArenaConfigException {
        boolean enabled = section.getBoolean("enabled", true);

        String worldName = section.getString("world");
        if (worldName.isEmpty()) {
            throw new MiniGameInvalidArenaConfigException("World not defined for arena '" + arenaId + "'.");
        }

        World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);

        Location lobby = loadLocation(section, world, arenaId, "lobby", true);
        Location spectator = loadLocation(section, world, arenaId, "spectator", false);
        if (spectator == null) {
            spectator = lobby;
        }

        SCRegion bridgeRegion = loadRegion(section, world, arenaId, "bridge", "Bridge");
        SCRegion arenaRegion = loadRegion(section, world, arenaId, "arena", "Arena");

        int minPlayers = section.getInt("min-players", 2);
        int maxPlayers = section.getInt("max-players", 16);
        String name = section.getString("name", StringUtil.beautify(arenaId));
        List<Material> dropItems = loadDropItems(section, arenaId);

        ConfigSection teams = section.getSection("teams", false);
        if (teams == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' does not have any teams defined.");
        }

        Set<String> teamIds = new LinkedHashSet<>(teams.getKeys(false));
        if (!teamIds.equals(new LinkedHashSet<>(REQUIRED_TEAM_IDS))) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' must define exactly the teams 'red' and 'blue'.");
        }

        Map<String, BridgeArenaRecord.TeamDef> teamDefs = new LinkedHashMap<>();
        for (String teamId : REQUIRED_TEAM_IDS) {
            ConfigSection teamSection = teams.getSection(teamId, false);
            if (teamSection == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arenaId + "' is missing team '" + teamId + "'.");
            }

            String teamName = teamSection.getString("name", StringUtil.beautify(teamId));
            Location spawn = loadLocation(teamSection, world, arenaId + "/" + teamId, "spawn", true);
            SCRegion portal = loadRegion(teamSection, world, arenaId + "/" + teamId, "portal", "Portal");
            teamDefs.put(teamId, new BridgeArenaRecord.TeamDef(teamId, teamName, spawn, portal));
        }

        return new BridgeArenaRecord(
            arenaId,
            enabled,
            name,
            world,
            lobby,
            spectator,
            bridgeRegion,
            arenaRegion,
            minPlayers,
            maxPlayers,
            dropItems,
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
        arenaConfig.set("bridge", serializeRegion(arena.get("bridgeRegion", SCRegion.class), arena.id(), "bridge"));
        arenaConfig.set("arena", serializeRegion(arena.get("arenaRegion", SCRegion.class), arena.id(), "arena"));
        arenaConfig.set("min-players", arena.getMinPlayers());
        arenaConfig.set("max-players", arena.getMaxPlayers());
        List<?> rawDropItems = arena.get("dropItems", List.class);
        arenaConfig.set("drop-items", serializeDropItems(rawDropItems, arena.id()));

        ConfigSection teamsConfig = arenaConfig.createSection("teams", true);
        for (String teamId : REQUIRED_TEAM_IDS) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing required team '" + teamId + "'.");
            }

            ConfigSection teamConfig = teamsConfig.createSection(teamId, true);
            teamConfig.set("name", team.get("displayName", String.class, team.getName()));
            teamConfig.set("spawn", serializeLocation(team.getSpawn(), arena.id(), "team " + teamId + " spawn"));
            teamConfig.set("portal", serializeRegion(team.get("portalRegion", SCRegion.class), arena.id(), "team " + teamId + " portal"));
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

    public boolean setArenaEnabled(@NotNull String arenaId, boolean enabled) {
        ensureLoaded();
        ConfigSection arenas = config.getSection("arenas", false);
        if (arenas == null || !arenas.isSection(arenaId)) {
            return false;
        }

        ConfigSection arenaConfig = arenas.getSection(arenaId, false);
        if (arenaConfig == null) {
            return false;
        }

        arenaConfig.set("enabled", enabled);
        config.save();
        return true;
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

    private void validateArenaForSave(@NotNull MiniGameArena arena) {
        if (arena.getLobbySpawn() == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a lobby spawn.");
        }
        if (arena.get("bridgeRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a bridge region.");
        }
        if (arena.get("arenaRegion", SCRegion.class) == null) {
            throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing an arena region.");
        }

        for (String teamId : REQUIRED_TEAM_IDS) {
            MiniGameTeam team = arena.getTeam(teamId);
            if (team == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing required team '" + teamId + "'.");
            }
            if (team.getSpawn() == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a spawn for team '" + teamId + "'.");
            }
            if (team.get("portalRegion", SCRegion.class) == null) {
                throw new MiniGameInvalidArenaConfigException("Arena '" + arena.id() + "' is missing a portal region for team '" + teamId + "'.");
            }
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

    private @Nullable Material parseDropMaterial(@Nullable Object raw) {
        if (raw instanceof Material material) {
            return material;
        }
        if (raw instanceof String name) {
            return Material.matchMaterial(name.trim());
        }
        if (raw instanceof org.bukkit.inventory.ItemStack item) {
            return item.getType();
        }
        return null;
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("Bridge config is not loaded.");
        }
    }
}
