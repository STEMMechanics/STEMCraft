package dev.stemcraft.api.tabcomplete;

import java.util.List;
import java.util.function.Supplier;

public interface TabCompleteService {
    void register(String name, Supplier<List<String>> callback);

    List<String> getCompletionList(String name);
}
