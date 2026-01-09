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

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.util.WorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * World setting to force players to respawn at the world spawn on death.
 */
public class WorldForceSpawnSetting implements WorldBaseSetting {
    WorldService worldService;

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

        // Respawn rules:
        // - Default: let Minecraft handle bed/anchor respawns.
        // - If no bed/anchor and Minecraft would respawn the player in a different world,
        //   respawn them at the death world's spawn.
        // - Optional per-world override: force respawn at world spawn even if bed/anchor exists.
        api.events().register(PlayerRespawnEvent.class, event -> {
            Player player = event.getPlayer();

            World deathWorld = player.getWorld();
            String baseWorld = WorldUtil.baseName(deathWorld.getName());

            if(get(deathWorld, service.getConfigSection(deathWorld)).equals("true")) {

                // Force respawn at world spawn
                World overworld = Bukkit.getWorld(baseWorld);
                if (overworld != null) {
                    Location spawn = overworld.getSpawnLocation();
                    event.setRespawnLocation(spawn);
                    return;
                }

                Location spawn = deathWorld.getSpawnLocation();
                event.setRespawnLocation(spawn);
            }
        }, EventPriority.HIGHEST, true);
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
            ctx.returnInfo("World '" + world.getName() + "' force-spawn-on-death is " +
                    get(world, config) + ".");
        }

        String value = ctx.getArgLower(1);
        if(value.equals("true") || value.equals("yes") || value.equals("1")
                || value.equals("false") || value.equals("no") || value.equals("0")
                || value.equals("unset")) {
            set(world, config, value);
            if (value.equals("unset")) {
                ctx.returnSuccess("Reset force-spawn-on-death setting for world '" + world.getName() + "' to normal.");
            } else {
                ctx.returnSuccess("Set force-spawn-on-death setting for world '" + world.getName() + "' to '" + value + "'.");
            }
        } else {
            ctx.returnError("Invalid value '" + value + "'. Valid values are: true, false, unset.");
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
        String baseName = WorldUtil.baseName(world.getName());
        ConfigSection config = worldService.getConfigSection(baseName);

        if(config == null) {
            return "unset";
        }

        String value = config.getString("force-spawn-on-death");
        if(value != null) {
            value = value.toLowerCase(Locale.ROOT);
            if(value.equals("true") || value.equals("yes") || value.equals("1")) return "true";
            if(value.equals("false") || value.equals("no") || value.equals("0")) return "false";
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
        String baseName = WorldUtil.baseName(world.getName());
        ConfigSection config = worldService.getConfigSection(baseName);

        String valueLower = value == null ? "false" : value.toLowerCase(Locale.ROOT);

        if(valueLower.equals("true") || valueLower.equals("yes") || valueLower.equals("1")) {
            config.set("force-spawn-on-death", true);
        } else if(valueLower.equals("false") || valueLower.equals("no") || valueLower.equals("0")) {
            config.set("force-spawn-on-death", false);
        } else {
            config.set("force-spawn-on-death", null);
        }

        config.save();
    }
}
