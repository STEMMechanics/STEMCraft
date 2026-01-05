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

package dev.stemcraft.service.world.settings;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.service.world.WorldServiceImpl;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

public class WorldGameModeSetting implements WorldBaseSetting {
    private WorldService service;

    /**
     * Returns the unique key for this setting.
     */
    @Override
    public String key() {
        return "gamemode";
    }

    /**
     * Called when the setting is enabled.
     */
    @Override
    public void onEnable(STEMCraftAPI api, WorldService service) {
        this.service = service;

        api.events().register(PlayerTeleportEvent.class, event -> {
            Player player = event.getPlayer();
            Location to = event.getTo();

            World world = to.getWorld();
            if (world == null) return;

            GameMode mode = get(world);
            if (mode != null) player.setGameMode(mode);
        });
    }

    /**
     * Returns a list of tab completions for this setting.
     */
    @Override
    public List<String[]> tabCompletions() {
        return List.of(
                new String[]{"unset"},
                new String[]{"adventure"},
                new String[]{"creative"},
                new String[]{"spectator"},
                new String[]{"survival"});
    }

    /**
     * Handle the command for this setting.
     */
    @Override
    public void onCommand(CommandContext ctx, ConfigSection config, World world) {
        if(ctx.numArgs() == 0) {
            GameMode currentMode = get(world);
            ctx.returnInfo("World '" + world.getName() + "' game mode is " +
                    (currentMode != null ? currentMode.name().toLowerCase() : "unset") + ".");
            return;
        }
        
        String mode = ctx.getArgLower(0);
        if(mode.equals("unset") || mode.equals("adventure") || mode.equals("creative")
                || mode.equals("spectator") || mode.equals("survival")) {

            set(world, config, mode);
            if(mode.equals("unset")) {
                ctx.returnSuccess("World '" + world.getName() + "' game mode is unset.");
            } else {
                ctx.returnSuccess("Set world '" + world.getName() + "' game mode to " + mode + ".");
            }

        }

        ctx.returnError("Invalid game mode '" + mode + "'. Valid options are: unset, survival, creative, adventure, spectator.");
    }


    /**
     * Set the value of this setting for the given world in the config.
     */
    GameMode get(World world) {
        ConfigSection config = service.getConfigSection(world);
        String gamemode = get(world, config);

        if (gamemode == null) {
            return null;
        }
        return GameMode.valueOf(gamemode.toUpperCase(Locale.ROOT));
    }

    /**
     * Set the value of this setting for the given world in the config.
     */
    @Override
    public void set(World world, ConfigSection config, String value) {
        if(value.equals("adventure") || value.equals("creative") || value.equals("spectator") || value.equals("survival")) {
            config.set("gamemode", value);

            world.getPlayers().forEach(player -> {
                player.setGameMode(GameMode.valueOf(value.toUpperCase(Locale.ROOT)));
            });
        } else {
            config.set("gamemode", null);
        }

        config.save();
    }
}
