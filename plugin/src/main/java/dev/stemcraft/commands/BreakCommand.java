package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BreakCommand extends STEMCraftCommandImpl {

    private static final int MAX_RADIUS = 10;
    private static final int MAX_DISTANCE = 120;

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("break");
        setDescription("BREAK_DESCRIPTION");
        setUsage("/break [radius]");
        setPermission("stemcraft.command.break");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
        if (!(ctx.getSender() instanceof Player player)) {
            cmd.error("PLAYER_ONLY");
            return;
        }

        int radius = 1;
        if (!ctx.args().isEmpty()) {
            String arg = ctx.getArg(1);
            try {
                radius = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                cmd.error("BREAK_RADIUS_INVALID", "radius", arg);
                return;
            }
        }

        if (radius < 1) {
            cmd.error("BREAK_RADIUS_INVALID", "radius", String.valueOf(radius));
            return;
        }

        if (radius > MAX_RADIUS) {
            radius = MAX_RADIUS;
            cmd.info(ctx.getSender(), "BREAK_RADIUS_CLAMPED",
                    "radius", String.valueOf(radius),
                    "max", String.valueOf(MAX_RADIUS));
        }

        Block target = player.getTargetBlockExact(MAX_DISTANCE);
        if (target == null) {
            cmd.error("BREAK_NO_TARGET");
            return;
        }

        int broken = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = target.getRelative(x, y, z);
                    if (b.getType() == Material.AIR) continue;
                    b.setType(Material.AIR, false); // no drops, no physics spam
                    broken++;
                }
            }
        }

        cmd.info(ctx.getSender(), "BREAK_SUCCESS",
                "count", String.valueOf(broken),
                "radius", String.valueOf(radius));
    }
}