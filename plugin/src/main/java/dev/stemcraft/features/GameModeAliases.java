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

package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;

import java.util.ArrayList;
import java.util.List;

public class GameModeAliases extends BaseFeature {

    /**
     * Constructor for GameModeAliases feature.
     */
    public GameModeAliases(STEMCraftAPI api) {
        super(api);
    }

    /**
     * Registers game mode alias commands.
     */
    @Override
    public void onEnable() {
        register("gmc", "creative");
        register("gms", "survival");
        register("gma", "adventure");
        register("gmsp", "spectator");
    }

    private void register(String alias, String mode) {
        api.commands().create(alias)
                .description("GAMEMODE_ALIAS_" + alias.toUpperCase())
                .usage("GAMEMODE_ALIAS_USAGE")
                .tabCompletion("{player}")
                .executor((sender, cmd, ctx) -> {
                    List<String> args = new ArrayList<>();
                    args.add(mode);
                    args.addAll(ctx.args());

                    ctx.dispatch("gamemode", args);
                })
                .register(STEMCraft.getPlugin());
    }
}