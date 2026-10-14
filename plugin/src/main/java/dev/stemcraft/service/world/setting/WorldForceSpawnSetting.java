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

package dev.stemcraft.service.world.setting;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.util.LocationUtil;
import dev.stemcraft.api.util.WorldUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * World setting to force players to respawn at the world spawn on death.
 */
public class WorldForceSpawnSetting implements WorldBaseSetting {
    private static final NamespacedKey WORLD_RESPAWNS_KEY = new NamespacedKey("stemcraft", "world-respawns");
    WorldService worldService;
    private final Set<UUID> switchingRespawn = new HashSet<>();

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @Override
    public @NotNull String key() {
        return "force-spawn-on-death";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        this.worldService = service;

        api.events().register(PlayerSetSpawnEvent.class, event -> {
            if (switchingRespawn.contains(event.getPlayer().getUniqueId())) {
                return;
            }

            Location spawn = event.getLocation();
            World world = spawn == null ? event.getPlayer().getWorld() : spawn.getWorld();
            if (world == null) {
                return;
            }
            storeWorldRespawn(event.getPlayer(), world, spawn, event.isForced());
        }, EventPriority.MONITOR, true);

        api.events().register(PlayerChangedWorldEvent.class, event -> {
            Player player = event.getPlayer();
            importExistingRespawn(player);
            activateWorldRespawn(player, player.getWorld());
        });

        api.events().register(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            importExistingRespawn(player);
            activateWorldRespawn(player, player.getWorld());
        });

        // Respawn rules:
        // - Each world has an independent bed/anchor respawn, activated on world entry.
        // - Never allow Minecraft to respawn a player outside the world where they died.
        // - Optional per-world override: force respawn at that world's spawn even if bed/anchor exists.
        api.events().register(PlayerRespawnEvent.class, this::handleRespawn, EventPriority.HIGHEST, true);
    }

    private void importExistingRespawn(@NotNull Player player) {
        // Keep the bed/anchor block coordinates, rather than its validated landing
        // position, so Minecraft can still validate the point after world switching.
        Location current = player.getRespawnLocation(false);
        World respawnWorld = current == null ? null : current.getWorld();
        if (respawnWorld != null && loadWorldRespawn(player, respawnWorld) == null) {
            storeWorldRespawn(player, respawnWorld, current, false);
        }
    }

    void handleRespawn(@NotNull PlayerRespawnEvent event) {
        World deathWorld = event.getPlayer().getWorld();
        if (isEnabledForWorld(deathWorld)) {
            event.setRespawnLocation(resolveWorldSpawn(deathWorld));
            return;
        }

        World respawnWorld = event.getRespawnLocation().getWorld();
        if (respawnWorld != null && !respawnWorld.equals(deathWorld)) {
            event.setRespawnLocation(resolveWorldSpawn(deathWorld));
        }
    }

    void activateWorldRespawn(@NotNull Player player, @NotNull World currentWorld) {
        StoredRespawn stored = loadWorldRespawn(player, currentWorld);
        switchingRespawn.add(player.getUniqueId());
        try {
            player.setRespawnLocation(stored == null ? null : stored.location(), stored != null && stored.forced());
        } finally {
            switchingRespawn.remove(player.getUniqueId());
        }
    }

    void storeWorldRespawn(@NotNull Player player, @NotNull World world,
                           @Nullable Location location, boolean forced) {
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        PersistentDataContainer respawns = playerData.get(WORLD_RESPAWNS_KEY, PersistentDataType.TAG_CONTAINER);
        if (respawns == null) {
            respawns = playerData.getAdapterContext().newPersistentDataContainer();
        }

        NamespacedKey worldKey = worldKey(world);
        if (location == null) {
            respawns.remove(worldKey);
        } else {
            String serialized = (forced ? "1|" : "0|") + LocationUtil.serialize(location, false, true);
            respawns.set(worldKey, PersistentDataType.STRING, serialized);
        }
        playerData.set(WORLD_RESPAWNS_KEY, PersistentDataType.TAG_CONTAINER, respawns);
    }

    private @Nullable StoredRespawn loadWorldRespawn(@NotNull Player player, @NotNull World world) {
        PersistentDataContainer respawns = player.getPersistentDataContainer()
            .get(WORLD_RESPAWNS_KEY, PersistentDataType.TAG_CONTAINER);
        if (respawns == null) {
            return null;
        }

        String stored = respawns.get(worldKey(world), PersistentDataType.STRING);
        if (stored == null || stored.length() < 3 || stored.charAt(1) != '|') {
            return null;
        }

        Location location = LocationUtil.deserialize(stored.substring(2), world);
        return location == null ? null : new StoredRespawn(location, stored.charAt(0) == '1');
    }

    private @NotNull NamespacedKey worldKey(@NotNull World world) {
        return new NamespacedKey("stemcraft", "world_" + world.getUID().toString().replace("-", ""));
    }

    private record StoredRespawn(@NotNull Location location, boolean forced) { }

    private boolean isEnabledForWorld(@NotNull World world) {
        return get(world, worldService.getConfigSection(world)).equals("true");
    }

    private @NotNull Location resolveWorldSpawn(@NotNull World world) {
        return world.getSpawnLocation();
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return A list of tab completion options.
     */
    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
                new String[]{"unset"},
                new String[]{"true"},
                new String[]{"false"});
    }

    /**
     * Handle the command for this setting.
     *
     * @param ctx The command context.
     * @param config The configuration section.
     * @param world The world to apply the setting to.
     */
    @Override
    public void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world) {
        // world flags [world] <flag> [options]

        if(ctx.numArgs() == 0) {
            ctx.returnInfo("WORLD_SETTING_FORCE_SPAWN_STATUS", "world", world.getName(), "value", get(world, config));
        }

        String value = ctx.getArgLower(0);
        if(value.equals("true") || value.equals("yes") || value.equals("1")
                || value.equals("false") || value.equals("no") || value.equals("0")
                || value.equals("unset")) {
            set(world, config, value);
            if (value.equals("unset")) {
                ctx.returnSuccess("WORLD_SETTING_FORCE_SPAWN_RESET", "world", world.getName());
            } else {
                ctx.returnSuccess("WORLD_SETTING_FORCE_SPAWN_SET", "world", world.getName(), "value", value);
            }
        } else {
            ctx.returnError("WORLD_SETTING_FORCE_SPAWN_INVALID", "value", value);
        }
    }

    /**
     * Get the force-spawn setting for a world.
     *
     * @param world The world to get the setting for.
     * @param unused Unused configuration section.
     * @return The force-spawn setting ("true", "false", or "unset").
     */
    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection unused) {
        String value = normalizeValue(worldService.getConfigSection(world).getString("force-spawn-on-death"));
        if (!value.equals("unset")) {
            return value;
        }

        String baseName = WorldUtil.baseName(world.getName());
        if (!world.getName().equals(baseName)) {
            return normalizeValue(worldService.getConfigSection(baseName).getString("force-spawn-on-death"));
        }

        return "unset";
    }


    /**
     * Set the force-spawn setting for a world.
     *
     * @param world The world to set the setting for.
     * @param unused Unused configuration section.
     * @param value The value to set ("true", "false", or "unset").
     */
    @Override
    public void set(@NotNull World world, @NotNull ConfigSection unused, @NotNull String value) {
        ConfigSection config = worldService.getConfigSection(world);

        String valueLower = value.toLowerCase(Locale.ROOT);

        if(valueLower.equals("true") || valueLower.equals("yes") || valueLower.equals("1")) {
            config.set("force-spawn-on-death", true);
        } else if(valueLower.equals("false") || valueLower.equals("no") || valueLower.equals("0")) {
            config.set("force-spawn-on-death", false);
        } else {
            config.set("force-spawn-on-death", null);
        }

        config.save();
    }

    private @NotNull String normalizeValue(String value) {
        if (value == null) {
            return "unset";
        }

        value = value.toLowerCase(Locale.ROOT);
        if(value.equals("true") || value.equals("yes") || value.equals("1")) return "true";
        if(value.equals("false") || value.equals("no") || value.equals("0")) return "false";
        return "unset";
    }
}
