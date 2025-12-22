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
        setUsage("BREAK_USAGE");
        addTabCompletion("{int}");
        setPermission("stemcraft.command.break");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
        ctx.checkNotConsole();

        Player player = ctx.getSenderAsPlayer();

        int radius = 1;
        if (!ctx.args().isEmpty()) {
            ctx.checkArgIsInt(1, "BREAK_RADIUS_INVALID", "radius");
            radius = ctx.getArgAsInt(1, 1, 1, MAX_RADIUS);
        }

        Block target = player.getTargetBlockExact(MAX_DISTANCE);
        if (target == null) {
            ctx.returnError("BREAK_NO_TARGET");
        }

        int broken = 0;

        // Treat 1 as "just the target", 2 as "small cluster", etc
        int r = radius - 1;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {

                    // Manhattan distance within r
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > r) continue;

                    Block b = target.getRelative(x, y, z);
                    if (b.getType() == Material.AIR) continue;

                    b.setType(Material.AIR, false); // no drops, no physics spam
                    broken++;
                }
            }
        }

        ctx.returnInfo("BREAK_SUCCESS",
                "count", broken,
                "radius", radius);
    }
}