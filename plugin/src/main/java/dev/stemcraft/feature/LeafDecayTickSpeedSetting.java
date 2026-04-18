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

package dev.stemcraft.feature;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import dev.stemcraft.api.util.StringUtil;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Example feature-owned world flag that overrides leaf decay random tick speed.
 */
public final class LeafDecayTickSpeedSetting implements WorldBaseSetting {
    public static final String KEY = "leaf-decay-tickspeed";

    @Override
    public @NotNull String key() {
        return KEY;
    }

    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        // runtime behavior is owned by LeafDecayRandomTickFeature
    }

    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
            new String[]{"reset"},
            new String[]{"unset"},
            new String[]{"{int}"}
        );
    }

    @Override
    public void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world) {
        String value = ctx.getArg(0, "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            value = get(world, config);

            if (value.equals("unset")) {
                ctx.returnInfo("WORLD_SETTING_LEAF_DECAY_TICKSPEED_NORMAL", "world", world.getName());
            }

            ctx.returnInfo("WORLD_SETTING_LEAF_DECAY_TICKSPEED_STATUS", "world", world.getName(), "value", value);
        }

        if (value.equals("reset") || value.equals("unset")) {
            set(world, config, "unset");
            ctx.returnSuccess("WORLD_SETTING_LEAF_DECAY_TICKSPEED_RESET", "world", world.getName());
            return;
        }

        if (!StringUtil.isInteger(value) || Integer.parseInt(value) <= 0) {
            ctx.returnError("WORLD_SETTING_LEAF_DECAY_TICKSPEED_INVALID", "value", value);
        }

        set(world, config, value);
        ctx.returnSuccess("WORLD_SETTING_LEAF_DECAY_TICKSPEED_SET", "world", world.getName(), "value", value);
    }

    @Override
    public @NotNull String get(@NotNull World world, @NotNull ConfigSection config) {
        String value = config.getString(KEY, "unset").toLowerCase(Locale.ROOT);
        if (StringUtil.isInteger(value) && Integer.parseInt(value) > 0) {
            return value;
        }

        return "unset";
    }

    @Override
    public void set(@NotNull World world, @NotNull ConfigSection config, @NotNull String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (StringUtil.isInteger(normalized) && Integer.parseInt(normalized) > 0) {
            config.set(KEY, Integer.parseInt(normalized));
        } else {
            config.set(KEY, null);
        }

        config.save();
    }
}
