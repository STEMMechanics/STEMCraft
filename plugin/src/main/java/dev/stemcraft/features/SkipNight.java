package dev.stemcraft.features;

import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.util.HashMap;
import java.util.List;

public class SkipNight implements STEMCraftFeature {
    STEMCraftAPI api;
    private double skipPercentange = 1d;
    private int skipRandomTickSpeed = 3;
    private final HashMap<World, BossBar> worlds = new HashMap<>();
    private final HashMap<World, Integer> worldRandomTickCount = new HashMap<>();

    /**
     * When the feature is enabled
     */
    @Override
    public void onEnable(STEMCraftAPI api) {
        this.api = api;
        String base = getConfigBase();

        skipPercentange = api.config().getDouble(base + ".required", 0.25d);
        skipRandomTickSpeed = api.config().getInt(base + ".random_tick_speed", 300);
        List<String> worldsList = api.config().getStringList(base + ".worlds");

        worldsList.forEach(worldName -> {
            World world = Bukkit.getServer().getWorld(worldName);
            if (world != null) {
                worlds.put(world, null);
            }
        });

        /*
         * PlayerBedEnterEvent
         */
        api.registerEvent(PlayerBedEnterEvent.class, (event) -> {
            Player player = event.getPlayer();
            World world = player.getLocation().getWorld();

            if (player.getGameMode() == GameMode.SURVIVAL && worlds.containsKey(world)) {
                api.tasks().runLater(1, () -> {
                    updateSleepers(world);
                });
            }
        });

        /*
         * PlayerJoinEvent
         */
        api.registerEvent(PlayerJoinEvent.class, (event) -> {
            api.tasks().runLater(20, () -> {
                Player player = event.getPlayer();
                World world = player.getLocation().getWorld();

                if (player.getGameMode() == GameMode.SURVIVAL && worlds.containsKey(world)) {
                    updateSleepers(world);
                }
            });
        });


        /*
         * PlayerBedLeaveEvent
         */
        api.registerEvent(PlayerBedLeaveEvent.class, (event) -> {
            Player player = event.getPlayer();
            World world = player.getLocation().getWorld();

            if (player.getGameMode() == GameMode.SURVIVAL && worlds.containsKey(world)) {
                api.tasks().runLater(1, () -> {
                    updateSleepers(world);
                });
            }
        });

        /*
         * PlayerGameModeChangeEvent
         */
        api.registerEvent(PlayerGameModeChangeEvent.class, (event) -> {
            Player player = event.getPlayer();
            World world = player.getLocation().getWorld();

            if (worlds.containsKey(world)) {
                updateSleepers(world);
            }
        });

        /*
         * PlayerDeathEvent
         */
        api.registerEvent(PlayerDeathEvent.class, event -> {
            if (event.getEventName().equalsIgnoreCase("playerdeathevent")) {
                Player player = event.getEntity();
                World world = player.getLocation().getWorld();

                if (worlds.containsKey(world)) {
                    updateSleepers(world);
                }
            }
        });

        /*
         * PlayerQuitEvent
         */
        api.registerEvent(PlayerQuitEvent.class, (event) -> {
            updateAllSleepers();
        });

        /*
         * PlayerQuitEvent
         */
        api.registerEvent(PlayerTeleportEvent.class, (event) -> {
            updateAllSleepers();
        });
    }

    /**
     * Update All Sleepers in each world
     */
    private void updateAllSleepers() {
        worlds.forEach((world, bossbar) -> {
            updateSleepers(world);
        });
    }

    /**
     * Update Sleepers in the specified world
     *
     * @param world The world to update
     */
    private void updateSleepers(World world) {
        List<Player> players = world.getPlayers();
        int numPlayers = players.size();
        int numSleepers = 0;

        for (Player player : players) {
            if (player.isSleeping()) {
                numSleepers++;
            }
        }

        int required = (int)Math.round(numPlayers * skipPercentange);
        BossBar bar = worlds.get(world);

        if (numSleepers == 0) {
            if (bar != null) {
                bar.removeAll();
                worlds.put(world, null);
            }

            return;
        }

        String title = api.locale().get("SKIP_NIGHT_BOSSBAR_TITLE", "sleeping", String.valueOf(numSleepers), "required", String.valueOf(required));

        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.BLUE, BarStyle.SOLID);
            worlds.put(world, bar);
        } else {
            bar.setTitle(title);
        }
        bar.setProgress((double) numSleepers / required);

        for (Player player : bar.getPlayers()) {
            if (player.getLocation().getWorld() != world || player.getGameMode() != GameMode.SURVIVAL) {
                bar.removePlayer(player);
            }
        }

        for (Player player : players) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }

        if (!isSkippingNight(world)) {
            if (numSleepers >= required) {
                for (Player player : players) {
                    api.info(player, "SKIP_NIGHT_ENOUGH_PLAYERS");
                }

                skipNight(world);
            }
        } else {
            if (numSleepers < required) {
                skipNightFinish(world);
            }
        }
    }

    /**
     * Skip the night of a specified world
     *
     * @param world The world to skip the night
     */
    private void skipNight(World world) {
        if (!worldRandomTickCount.containsKey(world)) {
            if (world.getTime() > 13000) {
                if (!worldRandomTickCount.containsKey(world)) {
                    worldRandomTickCount.put(world, world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED));
                    world.setGameRule(GameRule.RANDOM_TICK_SPEED, skipRandomTickSpeed);

                    skipNightStep(world);
                }
            }
        }
    }

    /**
     * Skip the night of a specified world
     *
     * @param world The world to skip the night
     */
    private void skipNightStep(World world) {
        api.tasks().runOnceDelay("skip_night_" + world.getName(), 1, () -> {
            if (world.getTime() > 1000) {
                world.setTime(world.getTime() + 100);
            }

            if (world.getTime() < 24000 && world.getTime() > 1000 && worldRandomTickCount.containsKey(world)) {
                skipNightStep(world);
            } else {
                skipNightFinish(world);
            }
        });
    }

    /**
     * Complete the skip night task
     *
     * @param world The world to finish
     */
    private void skipNightFinish(World world) {
        if (worldRandomTickCount.containsKey(world)) {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, worldRandomTickCount.get(world));
            worldRandomTickCount.remove(world);
        }
    }

    /**
     * Are we skipping the night in the specified world
     *
     * @param world The world to check
     * @return boolean True if the night being skipped
     */
    public boolean isSkippingNight(World world) {
        return worldRandomTickCount.containsKey(world);
    }
}

