package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.utils.SCString;
import dev.stemcraft.api.utils.SCWorld;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

public class Coordinates implements STEMCraftFeature {
    /**
     * This class represents the player coordinate data.
     */
    public static class CoordData {
        public BossBar bossBar;
        public Boolean actionBar;

        // Constructor
        public CoordData() {
            this.bossBar = null;
            this.actionBar = false;
        }

        // Constructor
        public CoordData(BossBar bossBar, Boolean actionBar) {
            this.bossBar = bossBar;
            this.actionBar = actionBar;
        }
    }

    /**
     * A list of CoordData per player (if enabled).
     */
    private static final Map<Player, CoordData> coordBars = new HashMap<>();

    /**
     * Called when the feature is requested to be enabled.
     */
    @Override
    public void onEnable(STEMCraftAPI api) {
        api.registerEvent(PlayerQuitEvent.class, event -> {
            removeCoordBars(event.getPlayer());
        });

        api.registerCommand("coord")
            .setUsage("/coord")
            .setDescription("Toggle coordinate action bar.")
            .setPermission("stemcraft.command.coord")
            .setExecutor((unused, cmd, ctx) -> {
                ctx.checkNotConsole();
                toggleActionBar(ctx.getSenderAsPlayer());
            }).register(STEMCraft.getInstance());

        api.registerCommand("coordbar")
            .setUsage("/coordbar")
            .setDescription("Toggle coordinate boss bar.")
            .setPermission("stemcraft.command.coordbar")
            .setExecutor((unused, cmd, ctx) -> {
                ctx.checkNotConsole();
                toggleBossBar(ctx.getSenderAsPlayer());
            }).register(STEMCraft.getInstance());

        Bukkit.getScheduler().runTaskTimer(STEMCraft.getInstance(), () -> {
            for (Player player : coordBars.keySet()) {
                if (!player.isOnline()) {
                    removeCoordBars(player);
                    continue;
                }

                CoordData coordData = coordBars.get(player);
                if (coordData.bossBar == null && coordData.actionBar == false) {
                    return;
                }

                String world = SCString.capitalize(SCString.beautify(player.getLocation().getWorld().getName()));
                String time = SCWorld.convertWorldToRealTime(player.getLocation().getWorld());
                String direction = SCString.getCompassDirection(player.getLocation().getYaw());

                if (coordData.bossBar != null) {
                    coordData.bossBar.setTitle(
                            api.locale().get(":world: " + world + " :mc_clock_00: " + time + " :mc_compass_00: " + direction));
                }

                if (coordData.actionBar == true) {
                    String subtitle = String.format("&6XYZ: &f%d %d %d  &6%s      %s",
                            player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                            player.getLocation().getBlockZ(), direction, time);

                    player.spigot().sendMessage(
                            ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', subtitle))
                    );
                }
            }
        }, 0L, 5L);
    }

    /**
     * Add a boss bar to a player.
     *
     * @param player The player to add the bar.
     */
    private static void addBossBar(Player player) {
        if (!coordBars.containsKey(player)) {
            coordBars.put(player, new CoordData(null, false));
        }

        if (coordBars.get(player).bossBar == null) {
            BossBar bossBar = Bukkit.createBossBar(
                    "",
                    BarColor.WHITE,
                    BarStyle.SOLID
            );
            bossBar.setProgress(0.0);
            bossBar.addPlayer(player);
            bossBar.setVisible(true);
            coordBars.get(player).bossBar = bossBar;
        }
    }

    /**
     * Add a action bar to a player.
     *
     * @param player The player to add the bar.
     */
    private static void addActionBar(Player player) {
        if (!coordBars.containsKey(player)) {
            coordBars.put(player, new CoordData(null, true));
        } else {
            coordBars.get(player).actionBar = true;
        }
    }

    /**
     * Remove a boss bar from a player.
     *
     * @param player The player to remove the bar.
     */
    private static void removeBossBar(Player player) {
        if (coordBars.containsKey(player)) {
            CoordData bars = coordBars.get(player);

            if (bars.bossBar != null) {
                bars.bossBar.removeAll();
                bars.bossBar = null;
            }
        }
    }

    /**
     * Remove a action bar from a player.
     *
     * @param player The player to remove the bar.
     */
    private static void removeActionBar(Player player) {
        if (coordBars.containsKey(player)) {
            coordBars.get(player).actionBar = false;
        }
    }

    /**
     * Toggle the boss bar of a player.
     *
     * @param player The player to toggle the bar.
     */
    private static void toggleBossBar(Player player) {
        if (coordBars.containsKey(player)) {
            if (coordBars.get(player).bossBar != null) {
                removeBossBar(player);
                return;
            }
        }

        addBossBar(player);
    }

    /**
     * Toggle the action bar of a player.
     *
     * @param player The player to toggle the bar.
     */
    private static void toggleActionBar(Player player) {
        if (coordBars.containsKey(player)) {
            if (coordBars.get(player).actionBar == true) {
                removeActionBar(player);
                return;
            }
        }

        addActionBar(player);
    }

    /**
     * Remove all coords bars from the player.
     *
     * @param player The player to remove.
     */
    private static void removeCoordBars(Player player) {
        if (coordBars.containsKey(player)) {
            CoordData bars = coordBars.get(player);

            if (bars.bossBar != null) {
                bars.bossBar.removeAll();
                bars.bossBar = null;
            }

            bars.actionBar = false;
            coordBars.remove(player);
        }
    }
}
