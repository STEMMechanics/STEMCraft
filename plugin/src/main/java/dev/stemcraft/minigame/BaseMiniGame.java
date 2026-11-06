package dev.stemcraft.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameArenaHandler;
import dev.stemcraft.api.service.config.ConfigChangeOrigin;
import dev.stemcraft.api.service.config.ConfigSubscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BaseMiniGame {
    protected final STEMCraftAPI api;

    public BaseMiniGame(STEMCraftAPI api) {
        this.api = api;
    }

    public void onLoad() { }


    @SuppressWarnings("SameReturnValue")
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

    /**
     * Opts a minigame into live configuration updates without interrupting an
     * occupied arena. External changes are applied at the next safe point;
     * command-triggered reloads continue to use the existing explicit path.
     */
    protected final ConfigSubscription watchConfigWhenSafe(@Nullable ConfigFile configFile,
                                                            @NotNull MiniGame minigame,
                                                            @NotNull Supplier<Boolean> reload) {
        if (configFile == null || api.config() == null) return ConfigSubscription.noop();

        api.config().watch(configFile);
        AtomicBoolean pending = new AtomicBoolean();
        return api.config().onChange(configFile, event -> {
            if (event.origin() != ConfigChangeOrigin.EXTERNAL || !pending.compareAndSet(false, true)) return;
            Runnable[] attempt = new Runnable[1];
            attempt[0] = () -> {
                boolean occupied = minigame.arenas().stream()
                    .anyMatch(arena -> !arena.getOccupants().isEmpty());
                if (occupied) {
                    if (api.tasks() != null) api.tasks().runLater(20L, attempt[0]);
                    return;
                }
                try {
                    reload.get();
                } finally {
                    pending.set(false);
                }
            };
            if (api.tasks() != null) api.tasks().nextTick(attempt[0]);
            else attempt[0].run();
        });
    }

    protected final void configureRewards(@NotNull MiniGame minigame, @Nullable ConfigFile configFile) {
        ConfigSectionView rewards = configFile == null ? null : configFile.getSection("rewards");
        minigame.configureRewards(rewards);
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
