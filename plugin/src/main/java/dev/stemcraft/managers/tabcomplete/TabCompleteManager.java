package dev.stemcraft.managers.tabcomplete;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.services.tabcomplete.TabCompleteService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

public class TabCompleteManager implements TabCompleteService {
    private final HashMap<String, Supplier<List<String>>> tabCompletionPlaceholders = new HashMap<>();

    public TabCompleteManager(STEMCraft plugin) { }

    public void onEnable() {
        // Online players
        register("player", () ->
                Bukkit.getOnlinePlayers()
                        .stream()
                        .map(Player::getName)
                        .toList()
        );

        // Common durations
        register("duration", () -> List.of(
                "1m", "2m", "5m", "10m", "15m", "30m",
                "1h", "2h", "4h",
                "1d", "1w"
        ));

        // Worlds
        register("world", () -> Bukkit.getWorlds().stream().map(World::getName).toList());
    }

    public void register(String name, Supplier<List<String>> callback) {
        tabCompletionPlaceholders.put(name, callback);
    }

    public List<String> getCompletionList(String name) {
        if (tabCompletionPlaceholders.containsKey(name)) {
            return tabCompletionPlaceholders.get(name).get();
        }

        return new ArrayList<String>();
    }
}
