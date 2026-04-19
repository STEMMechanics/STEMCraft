package dev.stemcraft.minigame.mobarena;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.minigame.bridge.BridgeMiniGame;

public class MobArenaConfig {
    private final STEMCraftAPI api;
    private ConfigSection config;

    public MobArenaConfig(STEMCraftAPI api, MobArenaMiniGame bridge) {
        this.api = api;
    }

    public void onEnable(ConfigSection config) {
        this.config = config;
        config.getSection("arenas");
    }
}
