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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;
import java.util.Locale;

/**
 * World setting to deny creature spawns based on configuration.
 */
public class WorldDenySpawnSetting implements WorldBaseSetting {
    private WorldService service;

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @Override
    public String key() {
        return "deny-spawn";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(STEMCraftAPI api, WorldService service) {
        this.service = service;

        api.events().register(CreatureSpawnEvent.class, event -> {
            Location loc = event.getLocation();
            World world = loc.getWorld();
            if (world == null) return;
            ConfigSection cfg = service.getConfigSection(world);

            String mode = cfg.getString("deny-spawn", "unset");
            if (mode == null) mode = "unset";
            mode = mode.toLowerCase(Locale.ROOT);

            if ("unset".equals(mode)) return;

            // Normalize unknown values to "animals"
            if (!"all".equals(mode) && !"mobs".equals(mode) && !"animals".equals(mode)) {
                mode = "all";
            }

            // animals: allow non-hostiles, block hostiles
            if ("animals".equals(mode)) {
                if (isAnimal(event.getEntity())) {
                    event.setCancelled(true);
                }
            }

            // mobs: allow hostiles (Monster), block non-hostiles
            if ("mobs".equals(mode)) {
                if (isMonster(event.getEntity())) {
                    event.setCancelled(true);
                }
                return;
            }

            // none: block all creature spawns
            if ("all".equals(mode)) {
                if(isAll(event.getEntity())) {
                    event.setCancelled(true);
                }
            }
        });
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return A list of tab completion options.
     */
    @Override
    public List<String[]> tabCompletions() {
        return List.of(
            new String[]{"unset"},
            new String[]{"all"},
            new String[]{"mobs"},
            new String[]{"animals"});
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
        // flags {world} deny-spawn [all|mobs|animals|unset]

        String value = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            value = service.getConfigSection(world).getString("deny-spawn", "unset");
            ctx.returnInfo("Current deny-spawn setting for world '" + world.getName() + "' is '" + value + "'.");
        }

        if (!value.equals("all") && !value.equals("mobs") && !value.equals("animals") && !value.equals("unset")) {
            ctx.returnError("Invalid deny-spawn value '" + value + "'. Valid values are: all, mobs, animals, unset.");
        } else {
            set(world, config, value);
            if (value.equals("unset")) {
                ctx.returnSuccess("Reset deny-spawn setting for world '" + world.getName() + "' to normal.");
            } else {
                ctx.returnSuccess("Set deny-spawn setting for world '" + world.getName() + "' to '" + value + "'.");
            }
        }
    }

    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world The world to apply the setting to.
     * @param config The configuration section for the world.
     * @param value The value to set (all, mobs, animals, unset).
     */
    @Override
    public void set(World world, ConfigSection config, String value) {
         value = value == null ? "unset" : value.toLowerCase(Locale.ROOT);

        switch (value) {
            case "all" -> world.getEntities().removeIf(this::isAll);
            case "mobs" -> world.getEntities().removeIf(this::isMonster);
            case "animals" -> world.getEntities().removeIf(this::isAnimal);
            case "unset" -> {
                // Do nothing
            }
            default -> throw new IllegalArgumentException("Invalid deny-spawn value '" + value + "'.");
        }

        if(!value.equals("unset")) {
            config.set("deny-spawn", value);
        } else {
            config.set("deny-spawn", null);
        }

        config.save();
    }

    /**
     * Check if an entity matches our "all".
     *
     * @param entity The entity to check.
     * @return true if the entity is a living entity but not a player or armor stand.
     */
    private boolean isAll(Entity entity) {
        return entity instanceof LivingEntity &&
                !(entity instanceof Player) &&
                !(entity instanceof ArmorStand);
    }

    /**
     * Check if an entity is a monster.
     *
     * @param entity The entity to check.
     * @return true if the entity is a monster.
     */
    private boolean isMonster(Entity entity) {
        return entity instanceof Monster;
    }

    /**
     * Check if an entity is an animal.
     *
     * @param entity The entity to check.
     * @return true if the entity is an animal.
     */
    private boolean isAnimal(Entity entity) {
        return entity instanceof Animals ||
                entity instanceof WaterMob ||
                entity instanceof Ambient;
    }
}