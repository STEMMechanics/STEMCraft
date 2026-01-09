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
import dev.stemcraft.api.util.StringUtil;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * World setting to control weather conditions.
 */
public class WorldTimeSetting implements WorldBaseSetting {
    private final Map<World, Long> worldLockedTimes = new HashMap<>();

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @Override
    public @NotNull String key() {
        return "time";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        api.tasks().repeating("world-time-setting", 20L, 20L, () -> {
            for (Map.Entry<World, Long> entry : worldLockedTimes.entrySet()) {
                World world = entry.getKey();
                long time = entry.getValue();
                world.setTime(time);
            }
        });
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return List of tab completions.
     */
    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
                new String[]{"unset"},
                new String[]{"sunrise", "always"},
                new String[]{"noon", "always"},
                new String[]{"sunset", "always"},
                new String[]{"night", "always"},
                new String[]{"midnight", "always"});
    }

    /**
     * Called when a world is loaded.
     *
     * @param world The world being loaded.
     * @param config The configuration section for the world.
     */
    @Override
    public void onWorldLoad(@NotNull World world, @NotNull ConfigSection config) {
        String setting = get(world, config);

        if (setting.endsWith(" always")) {
            String timeStr = setting.replace(" always", "");
            long timeValue = parseTime(timeStr);
            if (timeValue != -1L) {
                worldLockedTimes.put(world, timeValue);
                world.setTime(timeValue);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onWorldUnload(@NotNull World world, @NotNull ConfigSection config) {
        worldLockedTimes.remove(world);
    }

    /**
     * Handle the command for this setting.
     *
     * @param ctx The command context.
     * @param config The configuration section for the world.
     * @param world The world to apply the setting to.
     */
    @Override
    public void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world) {
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
     * @param world The world to get the setting for.
     * @param config The configuration section.
     * @return The weather setting value.
     */
    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection config) {
        long timeSet = config.getLong("time.set", -1L);
        boolean always = config.getBoolean("time.always", false);
        if(timeSet != -1L) {
            return timeSet + (always ? " always" : "");
        }

        return "unset";
    }

    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world The world to apply the setting to.
     * @param config The configuration section for the world.
     * @param value The value to set (clear, rain, thunder, unset).
     */
    @Override
    public void set(@NotNull World world, @NotNull ConfigSection config, @NotNull String value) {
        String valueLower = value.toLowerCase(Locale.ROOT);
        boolean always = valueLower.endsWith(" always");
        String timeStr = valueLower.replace(" always", "");
        long timeValue;

        if(StringUtil.isInteger(valueLower)) {
            timeValue = Long.parseLong(valueLower);
            if(timeValue < 0L || timeValue >= 24000L) {
                return;
            }
        } else {
            if(timeStr.isEmpty() || timeStr.equals("unset")) {
                timeValue = -1L;
            } else {
                timeValue = parseTime(timeStr);
                if(timeValue == -1L) {
                    return;
                }
            }
        }

        if(timeValue != -1L) {
            world.setTime(timeValue);
            config.set("time.set", timeValue);
            config.set("time.always", always);
        } else {
            always = false;
            config.set("time", null);
        }

        if(always) {
            worldLockedTimes.put(world, timeValue);
        } else {
            worldLockedTimes.remove(world);
        }

        config.save();
    }

    /**
     * Parse a time string into a Minecraft time value.
     *
     * @param timeStr The time string to parse.
     * @return The corresponding Minecraft time value, or -1 if invalid.
     */
    private long parseTime(String timeStr) {
        return switch (timeStr) {
            case "sunrise" -> 0L;
            case "noon" -> 6000L;
            case "sunset" -> 12000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> -1L;
        };
    }
}
