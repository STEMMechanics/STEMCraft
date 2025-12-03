package dev.stemcraft.api.services.tabcomplete;

import dev.stemcraft.api.services.STEMCraftService;

import java.util.List;
import java.util.function.Supplier;

public interface TabCompleteService extends STEMCraftService {
    void register(String name, Supplier<List<String>> callback);

    List<String> getCompletionList(String name);
}
