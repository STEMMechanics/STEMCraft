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
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Locale;

/**
 * World setting to prevent entities from taking damage.
 */
public class WorldNoDamageSetting implements WorldBaseSetting {

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @Override
    public String key() {
        return "no-damage";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(STEMCraftAPI api, WorldService service) {
        api.events().register(EntityDamageEvent.class, event -> {
            World world = event.getEntity().getWorld();
            ConfigSection config = service.getConfigSection(world);

            if (get(world, config).equals("true")) {
                event.setCancelled(true);
            }
        });
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return A list of tab completion string arrays.
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
     *
     * @param ctx The command context.
     * @param config The configuration section for the world.
     * @param world The world to apply the setting to.
     */
    @Override
    public void onCommand(CommandContext ctx, ConfigSection config, World world) {
        String value = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            value = config.getString("no-damage", "unset");
            ctx.returnInfo("Current no-damage setting for world '" + world.getName() + "' is '" + value + "'.");
        }

        if (!value.equals("unset") && !value.equals("true") && !value.equals("false")) {
            ctx.returnError("Invalid no-damage value '" + value + "'. Valid values are: true, false, unset.");
        } else {
            set(world, config, value);
            if (value.equals("unset")) {
                ctx.returnSuccess("Reset no-damage setting for world '" + world.getName() + "' to normal.");
            } else {
                ctx.returnSuccess("Set no-damage setting for world '" + world.getName() + "' to '" + value + "'.");
            }
        }
    }

    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world The world to apply the setting to.
     * @param config The configuration section for the world.
     * @param value The value to set (true, false, unset).
     */
    @Override
    public void set(World world, ConfigSection config, String value) {
        String valueLower = value.toLowerCase(Locale.ROOT);

        if(valueLower.equals("true")) {
            config.set("no-damage", true);
            world.getPlayers().forEach(PlayerUtil::setMaxHealth);
        } else if(valueLower.equals("false")) {
            config.set("no-damage", false);
        } else {
            config.set("no-damage", null);
        }

        config.save();
    }
}