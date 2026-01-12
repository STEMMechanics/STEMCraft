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

package dev.stemcraft.minigame.bridge;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.minigame.BaseMiniGame;
import org.bukkit.entity.Player;

import java.util.Set;

public class BridgeCommand extends BaseMiniGame {
    private STEMCraftAPI api;
    private BridgeMiniGame bridge;

    public BridgeCommand(STEMCraftAPI api, BridgeMiniGame bridge) {
        super(api);
    }

    public void onEnable() {

        api.tabComplete().register("bridge-arenas", (sender, args) -> api.minigames().getArenas(NAMESPACE).stream()
                .map(MiniGameArena::getId)
                .toList());

        api.commands().create("bridge")
                .permission("stemcraft.command.bridge")
                .usage("/bridge <join|leave|stop|start|restart> [arena]")
                .tabCompletion("join", "{bridge-arenas}", "{player}")
                .tabCompletion("leave", "{player}")
                .tabCompletion("stop", "{bridge-arenas}")
                .tabCompletion("start", "{bridge-arenas}")
                .tabCompletion("restart", "{bridge-arenas}")
                .tabCompletion("enable", "{bridge-arenas}")
                .tabCompletion("disable", "{bridge-arenas}")
                .tabCompletion("create")
                .tabCompletion("set", "lobbyspawn")
                .tabCompletion("set", "bridgeregion")
                .tabCompletion("set", "arenaregion")
                .tabCompletion("set", "teamspawn", "{minigame-teams}")
                .executor((ignored, cmd, ctx) -> {
                    ctx.checkArgsSizeAtLeast(1);

                    String subCommand = ctx.getArg(1).toLowerCase();
                    switch (subCommand) {
                        case "join" -> commandJoin(ctx);
                        case "leave" -> commandLeave(ctx);
                        case "stop" -> commandStart(ctx);
                        case "start" -> commandStart(ctx);
                        case "restart" -> commandRestart(ctx);
                        default -> ctx.returnUsage();
                    }
                })
                .register(STEMCraft.getPlugin());
    }

    private static final Set<String> JOINABLE_STATUSES = Set.of(
            MiniGameArena.STATUS_WAITING,
            MiniGameArena.STATUS_STARTING
    );

    private void commandJoin(CommandContext ctx) {
        // /bridge join <arena> [player]

        String arenaId = ctx.requireArg(1); // errors if missing with /usage
        Player targetPlayer = ctx.requirePlayerArg(2); // uses sender if missing, if console errors with player required

        MiniGameArena arena = api.minigames().getArena(BridgeMiniGame.namespace(), arenaId);
        if (arena == null) ctx.returnError("ARENA_NOT_EXIST", "arena", arenaId);

        if (!JOINABLE_STATUSES.contains(arena.getStatus())) {
            ctx.returnError("ARENA_ALREADY_RUNNING", "arena", arenaId);
        }

        api.minigames().addPlayer(targetPlayer, arena);
        ctx.success("PLAYER_JOINED_ARENA", "player", targetPlayer.getName(), "arena", arenaId);
    }

    private void commandLeave(CommandContext ctx) {

    }


}
