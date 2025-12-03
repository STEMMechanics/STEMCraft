package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;

public interface STEMCraftFeature {
    void onEnable(STEMCraftAPI api);

    @SuppressWarnings("EmptyMethod")
    default void onDisable() { }

    default String getName() {
        return this.getClass().getSimpleName();
    }

    default String getConfigBase(String append) {
        String simple = this.getName();

        if(append == null || append.isEmpty()) {
            append = "";
        } else if(append.startsWith(".")) {
            append = append.substring(1);
        }

        return "features." + camelToSnake(simple) + append;
    }

    default String getConfigBase() {
        return getConfigBase(null);
    }

    private static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
