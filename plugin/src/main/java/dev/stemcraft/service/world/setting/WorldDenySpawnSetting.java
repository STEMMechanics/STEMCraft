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
import org.bukkit.event.world.EntitiesLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
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
    public @NotNull String key() {
        return "deny-spawn";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        this.service = service;

        api.events().register(CreatureSpawnEvent.class, event -> {
            Location loc = event.getLocation();
            World world = loc.getWorld();
            if (world == null) return;
            if (shouldDeny(world, event.getEntity(), event.getSpawnReason())) {
                event.setCancelled(true);
            }
        });

        api.events().register(EntitiesLoadEvent.class, event ->
            removeDeniedEntities(event.getEntities(), getMode(service.getConfigSection(event.getWorld())))
        );
    }

    @Override
    public void onWorldLoad(@NotNull World world, @NotNull ConfigSection config) {
        removeDeniedEntities(world.getEntities(), getMode(config));
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
            new String[]{"all"},
            new String[]{"mobs"},
            new String[]{"animals"},
            new String[]{"all", "natural"},
            new String[]{"mobs", "natural"},
            new String[]{"animals", "natural"});
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
        // flags {world} deny-spawn [unset|all|mobs|animals] [natural]

        String type = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            ctx.returnInfo("WORLD_SETTING_DENY_SPAWN_STATUS", "world", world.getName(), "value", get(world, config));
        }

        if (type.equals("unset")) {
            set(world, config, "unset");
            ctx.returnSuccess("WORLD_SETTING_DENY_SPAWN_RESET", "world", world.getName());
        }

        if (!isValidType(type)) {
            ctx.returnError("WORLD_SETTING_DENY_SPAWN_INVALID", "value", ctx.getArgsAsString(1));
        }

        String scope = ctx.getArg(1, "").toLowerCase(Locale.ROOT);
        if (!scope.isEmpty() && !scope.equals("natural")) {
            ctx.returnError("WORLD_SETTING_DENY_SPAWN_INVALID", "value", ctx.getArgsAsString(1));
        }

        String value = scope.equals("natural") ? type + ":natural" : type;
        set(world, config, value);
        ctx.returnSuccess("WORLD_SETTING_DENY_SPAWN_SET", "world", world.getName(), "value", displayMode(value));
    }

    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection config) {
        return displayMode(getMode(config));
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
        value = normalizeMode(value.toLowerCase(Locale.ROOT));

        switch (value) {
            case "all", "mobs", "animals" -> removeDeniedEntities(world.getEntities(), value);
            case "natural", "natural-mobs", "natural-animals", "unset", "all:natural", "mobs:natural",
                 "animals:natural" -> {
                // Natural-only modes affect future natural spawns only.
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

    private boolean shouldDeny(@NotNull World world, @NotNull Entity entity, @NotNull CreatureSpawnEvent.SpawnReason reason) {
        return shouldDeny(entity, getMode(service.getConfigSection(world)), reason);
    }

    private boolean shouldDeny(@NotNull Entity entity, @NotNull String mode) {
        return switch (baseMode(mode)) {
            case "animals" -> isAnimal(entity);
            case "mobs" -> isMonster(entity);
            case "all" -> isAll(entity);
            default -> false;
        };
    }

    private boolean shouldDeny(@NotNull Entity entity, @NotNull String mode, @NotNull CreatureSpawnEvent.SpawnReason reason) {
        if (!isNaturalMode(mode)) {
            return shouldDeny(entity, mode);
        }
        if (!isNaturalSpawn(reason)) {
            return false;
        }

        return switch (mode) {
            case "animals:natural" -> isAnimal(entity);
            case "mobs:natural" -> isMonster(entity);
            case "all:natural" -> isAll(entity);
            default -> false;
        };
    }

    private void removeDeniedEntities(@NotNull Collection<? extends Entity> entities, @NotNull String mode) {
        if ("unset".equals(mode) || isNaturalMode(mode)) {
            return;
        }

        entities.stream()
            .filter(entity -> shouldDeny(entity, mode))
            .forEach(Entity::remove);
    }

    private @NotNull String getMode(@NotNull ConfigSection config) {
        String mode = normalizeMode(config.getString("deny-spawn", "unset").toLowerCase(Locale.ROOT));
        if ("unset".equals(mode)) {
            return mode;
        }

        if (!isValidStoredMode(mode)) {
            return "all";
        }

        return mode;
    }

    private boolean isValidType(@NotNull String type) {
        return type.equals("all") || type.equals("mobs") || type.equals("animals");
    }

    private boolean isValidStoredMode(@NotNull String mode) {
        return mode.equals("all")
            || mode.equals("mobs")
            || mode.equals("animals")
            || mode.equals("all:natural")
            || mode.equals("mobs:natural")
            || mode.equals("animals:natural")
            || mode.equals("unset");
    }

    private boolean isNaturalMode(@NotNull String mode) {
        return mode.endsWith(":natural");
    }

    private boolean isNaturalSpawn(@NotNull CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.NATURAL;
    }

    private @NotNull String normalizeMode(@NotNull String mode) {
        return switch (mode) {
            case "natural" -> "all:natural";
            case "natural-mobs" -> "mobs:natural";
            case "natural-animals" -> "animals:natural";
            default -> mode;
        };
    }

    private @NotNull String baseMode(@NotNull String mode) {
        int separator = mode.indexOf(':');
        return separator > 0 ? mode.substring(0, separator) : mode;
    }

    private @NotNull String displayMode(@NotNull String mode) {
        String normalized = normalizeMode(mode);
        if (!isNaturalMode(normalized)) {
            return normalized;
        }
        return baseMode(normalized) + " natural";
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
