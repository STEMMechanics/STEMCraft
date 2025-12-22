package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
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
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
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

    private Player resolveTarget(STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
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