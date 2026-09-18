package dev.stemcraft.minigame.minecartrace;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.minigame.MiniGameConfigSupport;
import org.bukkit.Location;
import org.bukkit.World;

final class MinecartRaceConfig {
    private final STEMCraftAPI api;
    private final MinecartRaceMiniGame owner;
    private final ConfigFile config;

    MinecartRaceConfig(STEMCraftAPI api, MinecartRaceMiniGame owner, ConfigFile config) {
        this.api = api;
        this.owner = owner;
        this.config = config;
    }

    void save(MiniGameArena a) {
        ConfigSection s = config.createSection("arenas." + a.id(), true);
        MinecartRaceArenaSettings d = owner.settings(a);
        s.set("world", a.world().getName());
        s.set("name", a.getName());
        s.set("enabled", a.getStatus() != MiniGameArena.ArenaStatus.DISABLED && a.getStatus() != MiniGameArena.ArenaStatus.SETUP);
        s.set("lobby", location(a.getLobbySpawn()));
        s.set("spectator", location(a.getSpectatorSpawn()));
        s.set("arena", a.getRegion() == null ? null : a.getRegion().toString());
        s.set("spawns", d.spawns.stream().map(this::location).toList());
        s.set("checkpoints", d.checkpoints.stream().map(SCRegion::toString).toList());
        s.set("min-players", a.getMinPlayers());
        s.set("max-players", a.getMaxPlayers());
        s.set("round-seconds", d.roundSeconds);
        s.set("countdown", d.countdown);
        s.set("laps", d.laps);
        config.save();
    }

    private String location(Location l) {
        return l == null ? null : LocationUtil.serialize(l, false, true);
    }

    void loadArenas() {
        ConfigSection arenas = config.getSection("arenas", false);
        if (arenas == null) return;
        for (String id : arenas.getKeys(false)) {
            MiniGameArena a = null;
            try {
                ConfigSection s = arenas.getSection(id, false);
                World world = MiniGameConfigSupport.requireWorld(api, id, s.getString("world"));
                a = owner.create(id, world);
                a.setName(s.getString("name", id));
                a.setLobbySpawn(LocationUtil.deserialize(s.getString("lobby"), world));
                a.setSpectatorSpawn(LocationUtil.deserialize(s.getString("spectator"), world));
                a.setRegion(SCRegion.fromString(s.getString("arena"), world));
                a.setMinPlayers(s.getInt("min-players", 2));
                a.setMaxPlayers(s.getInt("max-players", 8));
                MinecartRaceArenaSettings d = owner.settings(a);
                for (String value : s.getStringList("spawns")) d.spawns.add(LocationUtil.deserialize(value, world));
                for (String value : s.getStringList("checkpoints"))
                    d.checkpoints.add(SCRegion.fromString(value, world));
                d.roundSeconds = s.getInt("round-seconds", 180);
                d.countdown = s.getInt("countdown", 10);
                d.laps = s.getInt("laps", 3);
                var validation = a.validate();
                if (validation.hasErrors())
                    api.messages().warn(MinecartRaceMiniGame.NAMESPACE + ": " + id + ": " + String.join("; ", validation.getErrors()));
                a.setStatus(s.getBoolean("enabled", false) && !validation.hasErrors()
                        ? MiniGameArena.ArenaStatus.WAITING : MiniGameArena.ArenaStatus.DISABLED);
            } catch (RuntimeException ex) {
                if (a != null) a.setStatus(MiniGameArena.ArenaStatus.DISABLED);
                api.messages().warn("Cannot load " + MinecartRaceMiniGame.NAMESPACE + " arena " + id + ": " + ex.getMessage());
            }
        }
    }

    void delete(String id) {
        config.remove("arenas." + id);
        config.save();
    }
}
