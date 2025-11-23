package dev.stemcraft.tabcomplete;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.tabcomplete.TabCompleteService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

public class TabCompleteManager implements TabCompleteService {
    private HashMap<String, Supplier<List<String>>> tabCompletionPlaceholders = new HashMap<>();

    public TabCompleteManager(STEMCraft plugin) { }

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
