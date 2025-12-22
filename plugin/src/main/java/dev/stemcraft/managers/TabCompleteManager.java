package dev.stemcraft.managers;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.tabcomplete.TabCompleteService;
import dev.stemcraft.api.services.tabcomplete.TabCompletionProvider;
import dev.stemcraft.api.utils.SCPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TabCompleteManager implements TabCompleteService {
    private final HashMap<String, TabCompletionProvider> tabCompletionPlaceholders = new HashMap<>();

    public TabCompleteManager(STEMCraft plugin) { }

    public void onEnable() {
        // Online players
        register("player", (player, args) ->
                Bukkit.getOnlinePlayers()
                        .stream()
                        .filter(SCPlayer::isWhitelisted)
                        .map(Player::getName)
                        .toList()
        );

        // Common durations
        register("duration", (player, args) -> List.of(
                "1m", "2m", "5m", "10m", "15m", "30m",
                "1h", "2h", "4h",
                "1d", "1w"
        ));

        // Worlds
        register("world", (player, args) -> Bukkit.getWorlds().stream().map(World::getName).toList());

        // Gamemodes
        register("gamemode", (player, args) -> List.of(
                "survival", "creative", "spectator", "adventure"
        ));

        register("int", (player, args) -> List.of(
                "1", "2", "5", "10", "15", "20", "25", "50", "100"
        ));
    }

    public void register(String name, TabCompletionProvider callback) {
        tabCompletionPlaceholders.put(name, callback);
    }

    public List<String> getCompletionList(String name, Player player, String... args) {
        if (tabCompletionPlaceholders.containsKey(name)) {
            return tabCompletionPlaceholders.get(name).provide(player, args);
        }

        return new ArrayList<String>();
    }
}
