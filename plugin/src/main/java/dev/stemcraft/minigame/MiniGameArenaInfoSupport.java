package dev.stemcraft.minigame;

import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.model.SCRegion;
import org.bukkit.Location;

import java.util.List;

/** Shared presentation for arena setup reports; games add their own settings. */
public final class MiniGameArenaInfoSupport {
    private MiniGameArenaInfoSupport() {
    }

    public static void common(CommandContext ctx, MiniGameArena arena, int countdown, int roundSeconds) {
        ctx.info("Arena '" + arena.id() + "':");
        ctx.info(" - Name: " + arena.getName());
        ctx.info(" - World: " + arena.world().getName());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - Players: " + arena.numPlayers() + "/" + arena.getMaxPlayers());
        ctx.info(" - Spectators: " + arena.numSpectators());
        ctx.info(" - Min players: " + arena.getMinPlayers());
        ctx.info(" - Max players: " + arena.getMaxPlayers());
        ctx.info(" - Start countdown: " + countdown + " sec");
        ctx.info(" - Round duration: " + roundSeconds + " sec");
        ctx.info(" - Reset countdown: 20 sec");
        ctx.info(" - Lobby: " + formatLocation(arena.getLobbySpawn()));
        ctx.info(" - Spectator: " + formatLocation(arena.getSpectatorSpawn()));
        ctx.info(" - Arena region: " + formatRegion(arena.getRegion()));
    }

    public static void locations(CommandContext ctx, String label, List<Location> locations) {
        ctx.info(" - " + label + ": " + locations.size());
        for (int i = 0; i < locations.size(); i++) {
            ctx.info("   - " + (i + 1) + ": " + formatLocation(locations.get(i)));
        }
    }

    public static void regions(CommandContext ctx, String label, List<SCRegion> regions) {
        ctx.info(" - " + label + ": " + regions.size());
        for (int i = 0; i < regions.size(); i++) {
            ctx.info("   - " + (i + 1) + ": " + formatRegion(regions.get(i)));
        }
    }

    public static String formatLocation(Location location) {
        if (location == null) return "unset";
        return (location.getWorld() == null ? "unset world" : location.getWorld().getName()) + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    public static String formatRegion(SCRegion region) {
        if (region == null) return "unset";
        Location max = region.getMaximumLocation();
        return formatLocation(region.getMinimumLocation()) + " -> "
                + max.getBlockX() + "," + max.getBlockY() + "," + max.getBlockZ();
    }
}
