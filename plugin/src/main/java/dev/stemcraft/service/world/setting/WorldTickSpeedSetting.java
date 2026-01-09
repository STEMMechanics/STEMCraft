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
import org.bukkit.GameRule;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * World setting to deny creature spawns based on configuration.
 */
public class WorldTickSpeedSetting implements WorldBaseSetting {

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @Override
    public @NotNull String key() {
        return "tickspeed";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        // not used
    }

    /**
     * Called when a world is loaded to apply the tickspeed setting.
     *
     * @param world The world being loaded.
     * @param config The configuration section for the world.
     */
    @Override
    public void onWorldLoad(@NotNull World world, @NotNull ConfigSection config) {
        String value = get(world, config);

        if(StringUtil.isInteger(value)) {
            int tickSpeed = Integer.parseInt(value);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, tickSpeed);
        } else {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
        }
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return A list of tab completion options.
     */
    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
            new String[]{"reset"},
            new String[]{"{int}"}
        );
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

            if(value.equals("unset")) {
                ctx.returnInfo("Tickspeed for world '" + world.getName() + "' is set to normal.");
            }

            ctx.returnInfo("Tickspeed for world '" + world.getName() + "' is set to '" + value + "'.");
        }

        if (value.equals("reset") || value.equals("unset")) {
            set(world, config, "unset");
            ctx.returnSuccess("Reset tickspeed for world '" + world.getName() + "' to normal.");
        } else if(StringUtil.isInteger(value)) {
            set(world, config, value);
            ctx.returnSuccess("Set tickspeed for world '" + world.getName() + "' to '" + value + "'.");
        } else {
            ctx.returnError("Invalid tickspeed value '" + value + "'.");
        }
    }

    /**
     * Get the value of this setting for the given world from the config.
     *
     * @param world The world to get the setting for.
     * @param config The configuration section for the world.
     * @return The setting value (all, mobs, animals, unset).
     */
    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection config) {
        String value = config.getString("tickspeed", "unset").toLowerCase(Locale.ROOT);

        if(StringUtil.isInteger(value)) {
            return value;
        }

        return "unset";
    }

    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world The world to apply the setting to.
     * @param config The configuration section for the world.
     * @param value The value to set (all, mobs, animals, unset).
     */
    @Override
    public void set(@NotNull World world, @NotNull ConfigSection config, @NotNull String value) {
        value = value == null ? "unset" : value.toLowerCase(Locale.ROOT);

        if(StringUtil.isInteger(value)) {
            int tickSpeed = Integer.parseInt(value);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, tickSpeed);
        } else {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
            value = null;
        }

        config.set("tickspeed", value);
        config.save();
    }
}
