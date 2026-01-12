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

package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.DirectionUtil;
import dev.stemcraft.api.util.StringUtil;
import dev.stemcraft.api.util.WorldTimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Feature that adds player coordinates to boss bar or action bar.
 */
public class Coordinates extends BaseFeature {
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
     * Constructor for Coordinates feature.
     *
     * @param api The STEMCraft API instance.
     */
    public Coordinates(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Called when the feature is requested to be enabled.
     */
    @Override
    public void onEnable() {
        api.events().register(PlayerQuitEvent.class, event -> removeCoordBars(event.getPlayer()));

        api.commands().create("coord")
            .usage("/coord")
            .description("Toggle coordinate action bar.")
            .permission("stemcraft.command.coord")
            .executor((unused, cmd, ctx) -> {
                ctx.checkNotConsole();
                toggleActionBar(ctx.asPlayer());
            }).register(STEMCraft.getPlugin());

        api.commands().create("coordbar")
            .usage("/coordbar")
            .description("Toggle coordinate boss bar.")
            .permission("stemcraft.command.coordbar")
            .executor((unused, cmd, ctx) -> {
                ctx.checkNotConsole();
                toggleBossBar(ctx.asPlayer());
            }).register(STEMCraft.getPlugin());

        api.tasks().repeating(5, () -> {
            for (Player player : coordBars.keySet()) {
                if (!player.isOnline()) {
                    removeCoordBars(player);
                    continue;
                }

                CoordData coordData = coordBars.get(player);
                if (coordData.bossBar == null && coordData.actionBar == false) {
                    return;
                }

                String world = StringUtil.capitalize(StringUtil.beautify(player.getLocation().getWorld().getName()));
                String time = WorldTimeUtil.toClock(player.getLocation().getWorld());
                String direction = DirectionUtil.getCompassDirection(player.getLocation().getYaw());

                if (coordData.bossBar != null) {
                    coordData.bossBar.setTitle(
                            api.locales().resolve(":world: " + world + " :mc_clock_00: " + time + " :mc_compass_00: " + direction));
                }

                if (coordData.actionBar == true) {
                    Location l = player.getLocation();

                    Component component = Component.text()
                            .append(Component.text("XYZ: ", NamedTextColor.GOLD))
                            .append(Component.text(
                                    l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ(),
                                    NamedTextColor.WHITE
                            ))
                            .append(Component.space())
                            .append(Component.text(direction, NamedTextColor.GOLD))
                            .append(Component.space())
                            .append(Component.text(time, NamedTextColor.WHITE))
                            .build();

                    player.sendActionBar(component);
                }
            }
        });
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
     * Add an action bar to a player.
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
     * Remove an action bar from a player.
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