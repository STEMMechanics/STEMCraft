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
 * World setting to toggle random first-spawn behavior for a world.
 */
public class WorldRandomSpawnSetting implements WorldBaseSetting {
    private STEMCraftAPI api;

    @Override
    public @NotNull String key() {
        return "randomspawn";
    }

    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        this.api = api;
    }

    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
                new String[]{"true"},
                new String[]{"false"},
                new String[]{"unset"});
    }

    @Override
    public void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world) {
        String value = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            ctx.returnInfo("WORLD_SETTING_RANDOMSPAWN_STATUS", "world", world.getName(), "value", get(world, config));
        }

        if (value.equals("unset")) {
            set(world, config, value);
            ctx.returnSuccess("WORLD_SETTING_RANDOMSPAWN_RESET", "world", world.getName());
        }

        if (value.equals("true") || value.equals("yes") || value.equals("1")
                || value.equals("false") || value.equals("no") || value.equals("0")) {
            set(world, config, value);
            ctx.returnSuccess("WORLD_SETTING_RANDOMSPAWN_SET", "world", world.getName(), "value", value);
        }

        ctx.returnError("WORLD_SETTING_RANDOMSPAWN_INVALID", "value", value);
    }

    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection unused) {
        ConfigSection root = api.config().load("config.yml");
        if (root == null) {
            return "false";
        }

        String baseName = WorldUtil.baseName(world.getName());
        String path = "random-first-spawn.worlds." + baseName + ".enabled";
        return Boolean.toString(root.getBoolean(path, false));
    }

    @Override
    public void set(@NotNull World world, @NotNull ConfigSection unused, @NotNull String value) {
        ConfigSection root = api.config().load("config.yml");
        if (root == null) {
            return;
        }

        String baseName = WorldUtil.baseName(world.getName());
        String basePath = "random-first-spawn.worlds." + baseName;
        boolean enabled = value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("yes")
                || value.equalsIgnoreCase("1");

        if (value.equalsIgnoreCase("unset")) {
            root.set(basePath, null);
            root.save();
            return;
        }

        root.set("random-first-spawn.enabled", true);
        root.set(basePath + ".enabled", enabled);
        root.save();
    }
}
