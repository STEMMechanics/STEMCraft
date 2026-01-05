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

package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import org.bukkit.OfflinePlayer;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;

public class PWeatherCommand extends STEMCraftCommandImpl {

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("pweather");
        setDescription("PWEATHER_DESCRIPTION");
        setUsage("PWEATHER_USAGE");
        setPermission("stemcraft.command.pweather");
        addTabCompletion("sun", "{player}");
        addTabCompletion("rain", "{player}");
        addTabCompletion("storm", "{player}");
        addTabCompletion("reset", "{player}");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, Command cmd, CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            cmd.error("PWEATHER_MODE_REQUIRED");
            return;
        }

        String mode = ctx.getArg(1).toLowerCase();
        Player target = resolveTarget(cmd, ctx);
        if (target == null) return;

        switch (mode) {
            case "sun", "clear" -> {
                target.setPlayerWeather(WeatherType.CLEAR);
                cmd.info(ctx.getSender(), "PWEATHER_SET_SUN", "player", target.getName());
            }
            case "rain", "storm" -> {
                target.setPlayerWeather(WeatherType.DOWNFALL);
                cmd.info(ctx.getSender(), "PWEATHER_SET_RAIN", "player", target.getName());
            }
            case "reset" -> {
                target.resetPlayerWeather();
                cmd.info(ctx.getSender(), "PWEATHER_RESET", "player", target.getName());
            }
            default -> cmd.error("PWEATHER_INVALID_MODE", "mode", mode);
        }
    }

    private Player resolveTarget(Command cmd, CommandContext ctx) {
        if (ctx.args().size() >= 2) {
            OfflinePlayer off = ctx.getArgAsOfflinePlayer(2, null);
            if (off == null || !off.isOnline()) {
                cmd.error("PLAYER_NOT_FOUND", "player", ctx.getArg(2));
                return null;
            }
            return off.getPlayer();
        }

        if (!(ctx.getSender() instanceof Player sender)) {
            cmd.error("COMMAND_PLAYER_ONLY");
            return null;
        }

        return sender;
    }
}