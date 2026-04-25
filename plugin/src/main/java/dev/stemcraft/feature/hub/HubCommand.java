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

package dev.stemcraft.feature.hub;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.PlayerUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Command to teleport players to the hub world.
 */
public class HubCommand {
    private final HubFeature feature;
    private final STEMCraftAPI api;

    private static final String PERMISSION = "stemcraft.command.hub";

    /**
     * Constructor for the HubCommand feature.
     *
     * @param feature The HubFeature instance.
     * @param api The STEMCraftAPI instance.
     */
    public HubCommand(HubFeature feature, STEMCraftAPI api) {
        this.feature = feature;
        this.api = api;
    }

    /**
     * Called when the feature is requested to be enabled.
     */
    public void register() {
        api.commands().create("hub")
                .description("HUB_DESCRIPTION")
                .usage("HUB_USAGE")
                .tabCompletion("{player}")
                .permission(PERMISSION)
                .executor((unused, cmd, ctx) -> {
                    // check if console called without args
                    if(ctx.isConsole() && ctx.args().isEmpty()) {
                        cmd.error("CONSOLE_PLAYER_REQUIRED");
                        return;
                    }

                    // check permission for others (if args given)
                    if(!ctx.args().isEmpty() && !ctx.hasPermission(PERMISSION + ".others")) {
                        cmd.error(ctx.getSender(), "HUB_TELEPORT_OTHER_DENY");
                        return;
                    }

                    // get target player
                    Player target = ctx.getPlayer(0, ctx.getSender());
                    if(target == null) {
                        cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(0));
                        return;
                    }

                    org.bukkit.World hubWorld = feature.getHubWorld();
                    if (hubWorld == null) {
                        hubWorld = api.worlds().getDefaultWorld();
                    }

                    Location hubLocation = hubWorld.getSpawnLocation().clone();

                    boolean removedFromArena = api.minigames().removePlayerFromArena(target, false);
                    if (!removedFromArena) {
                        feature.runExitCommands(target);
                    }
                    PlayerUtil.teleport(target, hubLocation);

                    if (target.equals(ctx.getSender())) {
                        cmd.success(ctx.getSender(), "HUB_TELEPORT_SUCCESS");
                    } else {
                        String senderName = ctx.isConsole() ? api.locales().resolve("CONSOLE_NAME") : ctx.getSender().getName();
                        cmd.success(ctx.getSender(), "HUB_TELEPORT_OTHER_SUCCESS_SENDER", "player", target.getName());
                        cmd.success(target, "HUB_TELEPORT_OTHER_SUCCESS_PLAYER", "player", senderName);
                    }

                })
                .register(STEMCraft.getPlugin());
    }
}
