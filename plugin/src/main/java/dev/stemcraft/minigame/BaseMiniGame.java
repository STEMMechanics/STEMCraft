package dev.stemcraft.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Consumer;

public class BaseMiniGame {
    protected STEMCraftAPI api;

    public BaseMiniGame(STEMCraftAPI api) {
        this.api = api;
    }

    public void onLoad() { }

    public ConfigSection getConfig() { return null; }

    protected boolean disablesHungerByDefault() {
        return true;
    }

    protected final MiniGame createMiniGame(@NotNull String namespace, @NotNull MiniGameArenaHandler handler) {
        return api.minigames().create(namespace, handler)
            .setDisableHungerByDefault(disablesHungerByDefault());
    }

    protected final boolean reloadConfigFile(@Nullable ConfigFile configFile) {
        return configFile != null && configFile.reload();
    }

    protected final void unloadArenas(@NotNull MiniGame minigame) {
        unloadArenas(minigame, null);
    }

    protected final void unloadArenas(@NotNull MiniGame minigame, @Nullable Consumer<MiniGameArena> beforeRemove) {
        for (MiniGameArena arena : new ArrayList<>(minigame.arenas())) {
            if (beforeRemove != null) {
                beforeRemove.accept(arena);
            }
            minigame.removeArena(arena.id());
        }
    }
}
