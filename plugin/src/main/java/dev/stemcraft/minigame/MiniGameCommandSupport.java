package dev.stemcraft.minigame;

import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** Shared target and arena-join handling for minigame commands. */
public final class MiniGameCommandSupport {
    private MiniGameCommandSupport() { }

    /**
     * Resolves a player-list argument, defaulting to the command sender when it is omitted.
     * The command context accepts exact names, comma-separated names, {@code *}, and exclusions.
     */
    public static List<Player> targets(CommandContext ctx, int index) {
        if (ctx.getArg(index) == null) {
            Player sender = ctx.asPlayer();
            return sender == null ? List.of() : List.of(sender);
        }
        return ctx.getPlayers(index);
    }

    /** Adds the supplied players to one arena and reports what happened. */
    public static JoinResult addPlayers(
            MiniGame minigame,
            MiniGameArena arena,
            Collection<? extends Player> targets,
            boolean spectateOnly
    ) {
        int joined = 0;
        int spectating = 0;
        int skipped = 0;

        for (Player player : targets) {
            if (minigame.findPlayer(player) != null) {
                skipped++;
                continue;
            }

            if (spectateOnly) {
                arena.addSpectator(player);
                if (arena.hasSpectator(player)) {
                    spectating++;
                } else {
                    skipped++;
                }
                continue;
            }

            arena.addPlayer(player);
            if (arena.hasPlayer(player)) {
                joined++;
            } else if (arena.hasSpectator(player)) {
                spectating++;
            } else {
                skipped++;
            }
        }

        return new JoinResult(joined, spectating, skipped);
    }

    /** Adds every online player who is not already in this minigame to one arena. */
    public static JoinResult addOnlinePlayers(MiniGame minigame, MiniGameArena arena, boolean spectateOnly) {
        return addPlayers(minigame, arena, Bukkit.getOnlinePlayers(), spectateOnly);
    }

    public record JoinResult(int joined, int spectating, int skipped) {
        public int added() {
            return joined + spectating;
        }
    }
}
