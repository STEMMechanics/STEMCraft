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
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * World setting for configuring the linked nether world for an overworld base world.
 */
public class WorldNetherSetting implements WorldBaseSetting {
    private static final String STORAGE_KEY = "nether-world";
    private WorldService service;

    @Override
    public @NotNull String key() {
        return "nether";
    }

    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        this.service = service;
    }

    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
                new String[]{"unset"},
                new String[]{"{world}"},
                new String[]{"{world-offline}"});
    }

    @Override
    public void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world) {
        String rawValue = ctx.getArg(0, "");
        if (rawValue.isEmpty()) {
            ctx.returnInfo("WORLD_SETTING_NETHER_STATUS", "world", world.getName(), "value", get(world, config));
        }
        String value = rawValue.toLowerCase(Locale.ROOT);

        if (value.equals("unset")) {
            set(world, config, value);
            ctx.returnSuccess("WORLD_SETTING_NETHER_RESET", "world", world.getName());
        }

        if (!service.worldExists(rawValue)) {
            ctx.returnError("WORLD_NOT_FOUND", "world", rawValue);
        }

        if (WorldUtil.resolveEnvironment(rawValue) != World.Environment.NETHER) {
            ctx.returnError("WORLD_SETTING_NETHER_INVALID_WORLD", "world", rawValue);
        }

        set(world, config, rawValue);
        ctx.returnSuccess("WORLD_SETTING_NETHER_SET", "world", world.getName(), "value", rawValue);
    }

    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection unused) {
        String baseName = WorldUtil.baseName(world.getName());
        ConfigSection baseConfig = service.getConfigSection(baseName);
        return baseConfig.getString(STORAGE_KEY, "unset").toLowerCase(Locale.ROOT);
    }

    @Override
    public void set(@NotNull World world, @NotNull ConfigSection unused, @NotNull String value) {
        String baseName = WorldUtil.baseName(world.getName());
        ConfigSection baseConfig = service.getConfigSection(baseName);
        if (value.equalsIgnoreCase("unset")) {
            baseConfig.set(STORAGE_KEY, null);
        } else {
            baseConfig.set(STORAGE_KEY, value);
        }
        baseConfig.save();
    }
}
