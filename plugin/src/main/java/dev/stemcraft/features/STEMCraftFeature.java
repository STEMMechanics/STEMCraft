package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;

public interface STEMCraftFeature {
    void onEnable(STEMCraftAPI api);
    default void onDisable() { }

    default String getName() {
        return this.getClass().getSimpleName();
    }

    default String getConfigBase() {
        String simple = this.getName();
        return "features." + camelToSnake(simple);
    }

    private static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
