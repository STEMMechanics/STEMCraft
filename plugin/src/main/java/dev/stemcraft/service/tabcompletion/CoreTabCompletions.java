package dev.stemcraft.service.tabcompletion;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.SCPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public class CoreTabCompletions {

    /**
     * Register all core tab completions.
     */
    public static void registerAll(STEMCraftAPI api) {
        // Online players
        api.tabComplete().register("player", (player, args) ->
                Bukkit.getOnlinePlayers()
                        .stream()
                        .filter(player::canSee)
                        .map(Player::getName)
                        .toList()
        );

        // Common durations
        api.tabComplete().register("duration", (player, args) -> List.of(
                "1m", "2m", "5m", "10m", "15m", "30m",
                "1h", "2h", "4h",
                "1d", "1w"
        ));

        // Worlds
        api.tabComplete().register("world", (player, args) -> Bukkit.getWorlds().stream().map(World::getName).toList());

        // Game-modes
        api.tabComplete().register("gamemode", (player, args) -> List.of(
                "survival", "creative", "spectator", "adventure"
        ));

        api.tabComplete().register("int", (player, args) -> List.of(
                "1", "2", "5", "10", "15", "20", "25", "50", "100"
        ));
    }
}
