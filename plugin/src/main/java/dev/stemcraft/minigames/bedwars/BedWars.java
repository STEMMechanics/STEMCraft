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

package dev.stemcraft.minigames.bedwars;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.event.minigame.ArenaCountdownZeroEvent;
import dev.stemcraft.api.event.minigame.ArenaPlayerJoinEvent;
import dev.stemcraft.api.event.minigame.ArenaPlayerLeaveEvent;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.util.TimeUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BedWars {
    private static final String NAMESPACE = "bedwars";

    @Getter
    static
    class BedWarsArenaData {
        public int minPlayers;
        public int maxPlayers;
        public int gameTimeSeconds;
        public int waitTimeSeconds;
        public Map<String, Location> teamSpawns;


    }

    public void onEnable(STEMCraftAPI api) {
        BedWarsArenaData data = new BedWarsArenaData();
        data.minPlayers = 4;
        data.maxPlayers = 16;
        data.gameTimeSeconds = 1800; // 30 minutes
        data.waitTimeSeconds = 60; // 1 minute
        data.teamSpawns = new HashMap<>();
        data.teamSpawns.put("red", new Location(Bukkit.getWorld("bedwars_island"), 100, 65, 0));
        data.teamSpawns.put("blue", new Location(Bukkit.getWorld("bedwars_island"), -100, 65, 0));
        data.teamSpawns.put("green", new Location(Bukkit.getWorld("bedwars_island"), 0, 65, 100));
        data.teamSpawns.put("yellow", new Location(Bukkit.getWorld("bedwars_island"), 0, 65, -100));

        List<String> teams = List.of("red", "blue", "green", "yellow");
        MiniGameArena<BedWarsArenaData> my_arena = api.minigames().addArena(
                NAMESPACE,
                "island",
                "Island",
                Bukkit.getWorld("bedwars_island"),
                teams,
                data
        );

        my_arena.setStatus("waiting");

        api.commands().create("bedwars")
                .usage("/bedwars <subcommand>")
                .executor((unused, cmd, ctx) -> {
                    ctx.checkArgsSizeAtLeast(1);

                    String subCommand = ctx.getArg(1);
                    switch (subCommand.toLowerCase()) {
                        case "join" -> {
                            ctx.checkArgsSizeAtLeast(2);
                            String arenaId = ctx.getArg(2).toLowerCase();

                            if(!api.minigames().hasArena(NAMESPACE, arenaId)) {
                                ctx.returnError("Arena " + arenaId + " does not exist.");
                            }

                            api.minigames().addPlayer(ctx.getSenderAsPlayer(), NAMESPACE, arenaId);
                        }
                        default -> ctx.returnError("Unknown subcommand: " + subCommand);
                    }
                });

        api.minigames().registerHud(NAMESPACE, "waiting",
                List.of(
                        "Bedwars: {arena-name}",
                        "Waiting for players…"
                ),
                List.of(
                        "Bedwars: {arena-name}",
                        "",
                        "Players required: {min-players}",
                        "Players joined: {joined-players}",
                        "Waiting for players…"
                ),
                BedWarsArenaData.class,
                (arena, hudPlayer) -> {
                    int minPlayers = arena.getData().getMinPlayers();
                    int joined = arena.getPlayers().size();

                    Map<String, String> map = new HashMap<>();
                    map.put("min-players", Integer.toString(minPlayers));
                    map.put("joined-players", Integer.toString(joined));
                    return map;
                }
        );

        api.minigames().registerHud(NAMESPACE, "starting",
                List.of(
                        "Bedwars: {arena-name}",
                        "Game starting in {countdown}"
                ),
                List.of(
                        "Bedwars: {arena-name}",
                        "",
                        "Players required: {min-players}",
                        "Players joined: {joined-players}",
                        "Game starting in {countdown}"
                ),
                BedWarsArenaData.class,
                (arena, hudPlayer) -> {
                    int minPlayers = arena.getData().getMinPlayers();
                    int joined = arena.numPlayers();
                    int countdown = arena.getCountdown();

                    Map<String, String> map = new HashMap<>();
                    map.put("min-players", Integer.toString(minPlayers));
                    map.put("joined-players", Integer.toString(joined));
                    map.put("countdown", TimeUtil.formatLongDuration(countdown));
                    return map;
                }
        );

        api.minigames().registerHud(NAMESPACE, "in-game",
                List.of(
                        "Bedwars: {arena-name}",
                        "Time left: {time-left}"
                ),
                List.of(
                        "Bedwars: {arena-name}",
                        "",
                        "{team-status}"
                ),
                BedWarsArenaData.class,
                (arena, hudPlayer) -> {
                    int timeLeft = arena.getCountdown();

                    Map<String, String> map = new HashMap<>();
                    map.put("time-left", TimeUtil.formatLongDuration(timeLeft));
                    map.put("players-alive", Integer.toString(playersAlive));
                    return map;
                }
        );

        api.events().register(ArenaPlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            MiniGameArena<BedWarsArenaData> arena = event.getArena(BedWarsArenaData.class);

            arena.info(player, "You have joined the BedWars arena: " + arena.getName());

            if(arena.getStatus().equals("waiting")) {
                if(arena.getPlayers().size() >= arena.getData().getMinPlayers()) {
                    arena.setStatus("starting");
                    arena.setCountdown(30);
                }
            } else if(arena.getStatus().equals("in-game")) {
                if(arena.getPlayers().size() >= arena.getData().getMaxPlayers()) {
                    arena.error(player, "The arena is full. You will join as a spectator.");
                    arena.setPlayerAsSpectator(player);
                } else {
                    arena.setRandomTeam(player);
                    arena.info(player, "You have joined as a player.");
                }
            }
        });

        api.events().register(ArenaPlayerLeaveEvent.class, event -> {
            Player player = event.getPlayer();
            MiniGameArena<BedWarsArenaData> arena = event.getArena(BedWarsArenaData.class);

            arena.info(player, "You have left the BedWars arena: " + arena.getName());

            if(arena.numPlayers() < arena.getData().getMinPlayers()) {
                arena.setCountdown(0);
                arena.setStatus("waiting");
            }
        });

        api.events().register(ArenaCountdownZeroEvent.class, event -> {
            MiniGameArena<BedWarsArenaData> arena = event.getArena(BedWarsArenaData.class);

            if(arena.getStatus().equals("starting")) {
                arena.setStatus("in_game");
                arena.setCountdown(300);
                arena.broadcast("The BedWars game in arena " + arena.getName() + " has started!");
            } else if(arena.getStatus().equals("in_game")) {
                arena.setStatus("game_over");
                arena.setCountdown(20);
                arena.broadcast("The BedWars game in arena " + arena.getName() + " is over!");
            } else if(arena.getStatus().equals("game_over")) {
                arena.setStatus("waiting");
            }
        });

        // Additional event handlers for bed destruction, player deaths, etc. would go here
    }
}
