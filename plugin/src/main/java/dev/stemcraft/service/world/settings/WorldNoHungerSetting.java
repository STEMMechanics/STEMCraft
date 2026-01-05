/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.util.PlayerUtil;
import dev.stemcraft.service.world.WorldServiceImpl;
import org.bukkit.World;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.List;
import java.util.Locale;

public class WorldNoHungerSetting implements WorldBaseSetting {
    private WorldService service;

    /**
     * Returns the unique key for this setting.
     */
    @Override
    public String key() {
        return "no-hunger";
    }

    /**
     * Called when the setting is enabled.
     */
    @Override
    public void onEnable(STEMCraftAPI api, WorldService service) {
        this.service = service;

        api.events().register(FoodLevelChangeEvent.class, event -> {
            World world = event.getEntity().getWorld();
            ConfigSection config = service.getConfigSection(world);

            if (get(world, config).equals("true")) {
                event.setCancelled(true);
            }
        });
    }

    /**
     * Returns a list of tab completions for this setting.
     */
    @Override
    public List<String[]> tabCompletions() {
        return List.of(
                new String[]{"unset"},
                new String[]{"true"},
                new String[]{"false"});
    }

    /**
     * Handle the command for this setting.
     */
    @Override
    public void onCommand(CommandContext ctx, ConfigSection config, World world) {
        String value = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            value = config.getString("no-hunger", "unset");
            ctx.returnInfo("Current no-hunger setting for world '" + world.getName() + "' is '" + value + "'.");
        }

        if (!value.equals("unset") && !value.equals("true") && !value.equals("false")) {
            ctx.returnError("Invalid no-hunger value '" + value + "'. Valid values are: true, false, unset.");
        } else {
            set(world, config, value);
            if (value.equals("unset")) {
                ctx.returnSuccess("Reset no-hunger setting for world '" + world.getName() + "' to normal.");
            } else {
                ctx.returnSuccess("Set no-hunger setting for world '" + world.getName() + "' to '" + value + "'.");
            }
        }
    }

    /**
     * Set the value of this setting for the given world in the config.
     */
    @Override
    public void set(World world, ConfigSection config, String value) {
        String valueLower = value.toLowerCase(Locale.ROOT);

        if(valueLower.equals("true")) {
            config.set("no-hunger", true);
            world.getPlayers().forEach(player -> {
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setExhaustion(0f);
            });
        } else if(valueLower.equals("false")) {
            config.set("no-hunger", false);
        } else {
            config.set("no-hunger", null);
        }

        config.save();
    }
}
