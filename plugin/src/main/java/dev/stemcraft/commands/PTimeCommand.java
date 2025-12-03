package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PTimeCommand extends STEMCraftCommandImpl {

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("ptime");
        setDescription("PTIME_DESCRIPTION");
        setUsage("/ptime <day|night|reset|ticks> [player]");
        setPermission("stemcraft.command.ptime");
        addTabCompletion("day", "{player}");
        addTabCompletion("night", "{player}");
        addTabCompletion("reset", "{player}");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
        if (ctx.args().isEmpty()) {
            cmd.error("PTIME_MODE_REQUIRED");
            return;
        }

        String mode = ctx.getArg(1).toLowerCase();
        Player target = resolveTarget(cmd, ctx);
        if (target == null) return;

        switch (mode) {
            case "day" -> {
                target.setPlayerTime(6000L, false);
                cmd.info(ctx.getSender(), "PTIME_SET_DAY", "player", target.getName());
            }
            case "night" -> {
                target.setPlayerTime(18000L, false);
                cmd.info(ctx.getSender(), "PTIME_SET_NIGHT", "player", target.getName());
            }
            case "reset" -> {
                target.resetPlayerTime();
                cmd.info(ctx.getSender(), "PTIME_RESET", "player", target.getName());
            }
            default -> {
                long ticks;
                try {
                    ticks = Long.parseLong(mode);
                } catch (NumberFormatException e) {
                    cmd.error("PTIME_INVALID_MODE", "mode", mode);
                    return;
                }

                target.setPlayerTime(ticks, false);
                cmd.info(ctx.getSender(), "PTIME_SET_TICKS",
                        "player", target.getName(),
                        "time", String.valueOf(ticks));
            }
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
            cmd.error("PLAYER_ONLY");
            return null;
        }

        return sender;
    }
}