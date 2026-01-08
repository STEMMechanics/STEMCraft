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
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.List;
import java.util.Locale;

/**
 * World setting to control weather conditions.
 */
public class WorldWeatherSetting implements WorldBaseSetting {

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @Override
    public String key() {
        return "weather";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api     The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(STEMCraftAPI api, WorldService service) {
        api.events().register(WeatherChangeEvent.class, event -> {
            World world = event.getWorld();
            ConfigSection config = service.getConfigSection(world);
            String setting = get(world, config);

            if (!setting.endsWith(" always")) return;

            event.setCancelled(true);
            set(world, config, setting);
        });
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return List of tab completions.
     */
    @Override
    public List<String[]> tabCompletions() {
        return List.of(
                new String[]{"unset"},
                new String[]{"clear", "always"},
                new String[]{"rain", "always"},
                new String[]{"thunder", "always"});
    }

    /**
     * Called when a world is loaded.
     *
     * @param world  The world being loaded.
     * @param config The configuration section for the world.
     */
    @Override
    public void onWorldLoad(World world, ConfigSection config) {
        String setting = get(world, config);
        if (setting.equals("unset")) {
            return;
        }

        set(world, config, setting);
    }

    /**
     * Handle the command for this setting.
     *
     * @param ctx    The command context.
     * @param config The configuration section for the world.
     * @param world  The world to apply the setting to.
     */
    @Override
    public void onCommand(CommandContext ctx, ConfigSection config, World world) {
        String value = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            value = get(world, config);
            ctx.returnInfo("Current weather setting for world '" + world.getName() + "' is '" + value + "'.");
        }

        if(!value.equals("clear") && !value.equals("rain") && !value.equals("thunder") &&
           !value.equals("unset")) {
            ctx.returnError("Invalid weather value '" + value + "'. Valid values are: clear, rain, thunder, unset.");
        }

        boolean always = ctx.getArgAsBoolean(1, false);
        set(world, config, value + (always ? " always" : ""));

        if(value.equals("unset")) {
            ctx.returnSuccess("Reset weather for world '" + world.getName() + "' to normal cycle.");
        } else {
            ctx.returnSuccess("Set weather for world '" + world.getName() + "' to " + value +
                    (always ? " always." : "."));
        }
    }

    /**
     * Get the value of this setting for the given world in the config.
     *
     * @param world  The world to get the setting for.
     * @param config The configuration section.
     * @return The weather setting value.
     */
    @Override
    public String get(World world, ConfigSection config) {
        String state = config.getString("weather.state", "unset").toLowerCase(Locale.ROOT);
        if(state.equals("clear") || state.equals("rain") || state.equals("thunder")) {
            String always = config.getBoolean("weather.always", false) ? " always" : "";

            return state + always;
        }

        return "unset";
    }

    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world  The world to apply the setting to.
     * @param config The configuration section for the world.
     * @param value  The value to set (clear, rain, thunder, unset).
     */
    @Override
    public void set(World world, ConfigSection config, String value) {
        String valueLower = value.toLowerCase(Locale.ROOT);
        boolean always = valueLower.endsWith(" always");
        String weather = valueLower.replace(" always", "");

        switch (weather) {
            case "clear" -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "thunder" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
        }

        world.setGameRule(GameRule.DO_WEATHER_CYCLE, !always);

        if(always) {
            config.set("weather.state", weather);
            config.set("weather.always", true);
        } else {
            config.set("weather", null);
        }

        config.save();
    }
}
