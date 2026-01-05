/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.minigames;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.minigame.MiniGameBossBarRenderer;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class STEMCraftMiniGame<T extends MiniGameArena> {
    private STEMCraftAPI api;
    private Map<String, T> arenas = new HashMap<>();

    private Map<UUID, BossBar> bars = new HashMap<>();

    private static String INFINITE_WATCHER_TASK_KEY = "minigame-infinite-watcher";
    private Map<Player, List<Material>> infiniteWatcher = new HashMap<>();

    /**
     * When the mini-game is enabled.
     */
    public void onEnable(STEMCraftAPI api) {
        this.api = api;

    }

    /**
     * When the mini-game is disabled.
     */
    public void onDisable() {
    }

    /**
     * When a player leaves the mini-game.
     */
    public void playerLeave(Player player) {
        // Hide and remove any bossbar associated with this player
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }

        infiniteWatcher.remove(player);
    }


    // ---------------------------------------------------------------------
    // INFINITE ITEMS
    // ---------------------------------------------------------------------
    public void enableInfiniteItems(Player player, List<Material> materialList) {
        infiniteWatcher.put(player, materialList);

        if (infiniteWatcher.size() == 1) {
            startInfiniteWatcherTask();
        }
    }

    public void disableInfiniteItems(Player player) {
        infiniteWatcher.remove(player);
        if (infiniteWatcher.isEmpty()) {
            stopInfiniteWatcherTask();
        }
    }

    private void startInfiniteWatcherTask() {
        api.tasks().repeating(INFINITE_WATCHER_TASK_KEY, 0, 10, () -> {
            for (Map.Entry<Player, List<Material>> entry : infiniteWatcher.entrySet()) {
                Player player = entry.getKey();
                List<Material> materialList = entry.getValue();

                if (!player.isOnline()) continue;

                var inv = player.getInventory();
                for (int slot = 0; slot < inv.getSize(); slot++) {
                    var item = inv.getItem(slot);
                    if (item == null || item.getType().isAir()) continue;

                    Material mat = item.getType();
                    if (!materialList.contains(mat)) continue;

                    int maxStackSize = mat.getMaxStackSize();
                    if (maxStackSize <= 1) continue;

                    if (item.getAmount() < maxStackSize) {
                        item.setAmount(maxStackSize);
                        inv.setItem(slot, item);
                    }
                }
            }
        });
    }

    private void stopInfiniteWatcherTask() {
        api.tasks().cancel(INFINITE_WATCHER_TASK_KEY);
    }



    public void bossBarRenderer(MiniGameBossBarRenderer renderer) {

    }
}

/**
    public void barCountDown() {

    }


    public final class BridgeBossbar {
        private static final MiniMessage MM = MiniMessage.miniMessage();

        private final Plugin plugin;
        private final BossBar bar;

        private BukkitTask task;

        private int totalSeconds;
        private int secondsLeft;

        private int rotateEverySeconds = 5;

        // Provide title frames as functions so they can show live values (kills, lives, etc)
        private List<Function<Player, Component>> frames = List.of(
                p -> MM.deserialize("<gold><bold>TheBridge</bold></gold> <gray>|</gray> <yellow>Time:</yellow> <white>" + format(secondsLeft) + "</white>")
        );

        private int frameIndex = 0;

        public BridgeBossbar(Plugin plugin) {
            this.plugin = plugin;
            this.bar = BossBar.bossBar(Component.text(""), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        }

        public BridgeBossbar frames(List<Function<Player, Component>> frames) {
            this.frames = (frames == null || frames.isEmpty()) ? this.frames : frames;
            return this;
        }

        public BridgeBossbar rotateEverySeconds(int seconds) {
            this.rotateEverySeconds = Math.max(1, seconds);
            return this;
        }

        public void start(Collection<? extends Player> players, int durationSeconds) {
            stop();

            this.totalSeconds = Math.max(1, durationSeconds);
            this.secondsLeft = this.totalSeconds;

            // show to all players
            for (Player p : players) p.showBossBar(bar);

            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(players), 0L, 20L);
        }

        public void stop() {
            if (task != null) task.cancel();
            task = null;
        }

        public void hideFromAll(Collection<? extends Player> players) {
            for (Player p : players) p.hideBossBar(bar);
        }

        private void tick(Collection<? extends Player> players) {
            // progress
            float progress = Math.max(0f, Math.min(1f, secondsLeft / (float) totalSeconds));
            bar.progress(progress);

            // rotate title every N seconds
            if ((secondsLeft % rotateEverySeconds) == 0) {
                frameIndex = (frameIndex + 1) % frames.size();
            }

            // BossBar title is global, so pick a "reference" player for dynamic frames
            Player ref = players.stream().findFirst().orElse(null);
            if (ref != null) bar.name(frames.get(frameIndex).apply(ref));

            // end
            secondsLeft--;
            if (secondsLeft < 0) {
                stop();
            }
        }

        private static String format(int totalSeconds) {
            int m = Math.max(0, totalSeconds) / 60;
            int s = Math.max(0, totalSeconds) % 60;
            return String.format("%d:%02d", m, s);
        }
    }


    BridgeBossbar bb = new BridgeBossbar(plugin)
            .rotateEverySeconds(5)
            .frames(List.of(
                    p -> MM.deserialize("<gold>TheBridge</gold> <gray>|</gray> <yellow>Time</yellow> <white>" + time + "</white>"),
                    p -> MM.deserialize("<red>Red</red> " + redHearts + "  <blue>Blue</blue> " + blueHearts),
                    p -> MM.deserialize("<green>Kills</green> <white>" + kills + "</white>  <gray>Deaths</gray> <white>" + deaths + "</white>")
            ));


}


private BukkitTask restockTask;

private void startRestockTask(Collection<? extends Player> players) {
    stopRestockTask();

    restockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
        for (Player p : players) {
            if (!region.contains(p.getLocation())) continue;

            PlayerInventory inv = p.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack it = inv.getItem(slot);
                if (it == null || it.getType().isAir()) continue;

                Material m = it.getType();
                if (m == Material.TNT) continue;

                int max = m.getMaxStackSize();
                if (max <= 1) continue;

                if (it.getAmount() < max) {
                    it.setAmount(max);
                    inv.setItem(slot, it);
                }
            }
        }
    }, 0L, 10L); // every 0.5s
}

private void stopRestockTask() {
    if (restockTask != null) restockTask.cancel();
    restockTask = null;
}
 **/