package dev.stemcraft.minigame.skyblock;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.exception.MiniGameInvalidArenaConfigException;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class SkyBlockConfig {
    private final STEMCraftAPI api;
    private ConfigSection config;

    public SkyBlockConfig(STEMCraftAPI api, SkyBlockMiniGame skyBlock) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("settings");
        config.getSection("games");
    }

    public int standbyWorldTarget() {
        ensureLoaded();
        return Math.max(0, config.getInt("settings.standby-worlds", 8));
    }

    public int preferredIdleOnlinePlayers() {
        ensureLoaded();
        return Math.max(0, config.getInt("settings.preferred-idle-online-players", 0));
    }

    public @NotNull String worldPrefix() {
        ensureLoaded();
        String prefix = config.getString("settings.world-prefix", "skyblock_").trim();
        return prefix.isEmpty() ? "skyblock_" : prefix;
    }

    public int islandY() {
        ensureLoaded();
        return config.getInt("settings.island-y", 64);
    }

    public long nextWorldSequence() {
        ensureLoaded();
        long next = Math.max(1L, config.getLong("settings.next-world-sequence", 1L));
        config.set("settings.next-world-sequence", next + 1L);
        config.save();
        return next;
    }

    public @NotNull List<String> standbyWorlds() {
        ensureLoaded();
        return new ArrayList<>(config.getStringList("standby-worlds"));
    }

    public void setStandbyWorlds(@NotNull Collection<String> worlds) {
        ensureLoaded();
        config.set("standby-worlds", new ArrayList<>(worlds));
        config.save();
    }

    public @NotNull List<SkyBlockArenaRecord> loadArenas() {
        ensureLoaded();

        List<SkyBlockArenaRecord> arenas = new ArrayList<>();
        ConfigSection games = config.getSection("games", false);
        if (games == null) {
            return arenas;
        }

        for (String arenaId : games.getKeys(false)) {
            ConfigSection arenaSection = games.getSection(arenaId, false);
            if (arenaSection == null) {
                continue;
            }

            String ownerUuidString = arenaSection.getString("owner-uuid");
            if (ownerUuidString.isBlank()) {
                throw new MiniGameInvalidArenaConfigException("SkyBlock arena '" + arenaId + "' is missing an owner uuid.");
            }

            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(ownerUuidString);
            } catch (IllegalArgumentException exception) {
                throw new MiniGameInvalidArenaConfigException("SkyBlock arena '" + arenaId + "' has an invalid owner uuid.");
            }

            String ownerName = arenaSection.getString("owner-name", ownerUuid.toString());
            String worldName = arenaSection.getString("world");
            if (worldName.isBlank()) {
                throw new MiniGameInvalidArenaConfigException("SkyBlock arena '" + arenaId + "' is missing a world.");
            }

            World world = MiniGameConfigSupport.requireWorld(api, arenaId, worldName);
            Location islandSpawn = loadLocation(arenaSection, world, arenaId, "spawn", true);
            SkyBlockPlayerState playerState = null;
            ConfigSection stateSection = arenaSection.getSection("state", false);
            if (stateSection != null) {
                playerState = SkyBlockPlayerState.load(stateSection, world);
            }

            arenas.add(new SkyBlockArenaRecord(
                arenaId,
                ownerUuid,
                ownerName,
                world,
                islandSpawn,
                playerState
            ));
        }

        return arenas;
    }

    public void saveArena(@NotNull MiniGameArena arena, @Nullable SkyBlockPlayerState playerState) {
        ensureLoaded();

        String ownerUuid = arena.get("ownerUuid", String.class, "");
        if (ownerUuid.isBlank()) {
            throw new MiniGameInvalidArenaConfigException("SkyBlock arena '" + arena.id() + "' is missing an owner uuid.");
        }

        ConfigSection arenaSection = config.createSection("games." + arena.id(), true);
        arenaSection.set("owner-uuid", ownerUuid);
        arenaSection.set("owner-name", arena.get("ownerName", String.class, arena.id()));
        arenaSection.set("world", arena.world().getName());
        arenaSection.set("spawn", serializeLocation(arena.getLobbySpawn(), arena.id(), "spawn"));

        ConfigSection stateSection = arenaSection.createSection("state", true);
        stateSection.removeAll();
        if (playerState != null) {
            playerState.save(stateSection);
        }

        config.save();
    }

    public void deleteArena(@NotNull String arenaId) {
        ensureLoaded();
        ConfigSection games = config.getSection("games");
        games.remove(arenaId);
        config.save();
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
            throw new MiniGameInvalidArenaConfigException("SkyBlock arena '" + arenaId + "' is missing " + name + ".");
        }
        return LocationUtil.serialize(location, false, true);
    }

    private void ensureLoaded() {
        if (config == null) {
            throw new IllegalStateException("SkyBlock config is not loaded.");
        }
    }
}
