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

package dev.stemcraft.service.world.setting;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldBaseSetting;
import dev.stemcraft.api.service.world.WorldService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * World setting to set the game mode for players when they enter the world.
 */
public class WorldGameModeSetting implements WorldBaseSetting {
    private WorldService service;

    /**
     * Returns the unique key for this setting.
     *
     * @return The setting key.
     */
    @Override
    public @NotNull String key() {
        return "gamemode";
    }

    /**
     * Called when the setting is enabled.
     *
     * @param api The STEMCraft API instance.
     * @param service The WorldService instance.
     */
    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull WorldService service) {
        this.service = service;

        api.events().register(PlayerTeleportEvent.class, event -> {
            Player player = event.getPlayer();
            Location to = event.getTo();

            World world = to.getWorld();
            if (world == null) return;

            GameMode mode = get(world);
            if (mode != null) player.setGameMode(mode);
        });
    }

    /**
     * Returns a list of tab completions for this setting.
     *
     * @return A list of tab completion string arrays.
     */
    @Override
    public @NotNull List<String[]> tabCompletions() {
        return List.of(
                new String[]{"unset"},
                new String[]{"adventure"},
                new String[]{"creative"},
                new String[]{"spectator"},
                new String[]{"survival"});
    }

    /**
     * Handle the command for this setting.
     *
     * @param ctx The command context.
     * @param config The configuration section for the world.
     * @param world The world to apply the setting to.
     */
    @Override
    public void onCommand(@NotNull CommandContext ctx, @NotNull ConfigSection config, @NotNull World world) {
        if(ctx.numArgs() == 0) {
            GameMode currentMode = get(world);
            ctx.returnInfo("World '" + world.getName() + "' game mode is " +
                    (currentMode != null ? currentMode.name().toLowerCase() : "unset") + ".");
            return;
        }
        
        String mode = ctx.getArgLower(0);
        if(mode.equals("unset") || mode.equals("adventure") || mode.equals("creative")
                || mode.equals("spectator") || mode.equals("survival")) {

            set(world, config, mode);
            if(mode.equals("unset")) {
                ctx.returnSuccess("World '" + world.getName() + "' game mode is unset.");
            } else {
                ctx.returnSuccess("Set world '" + world.getName() + "' game mode to " + mode + ".");
            }

        }

        ctx.returnError("Invalid game mode '" + mode + "'. Valid options are: unset, survival, creative, adventure, spectator.");
    }


    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world The world to get the setting for.
     * @return The GameMode for the world, or null if unset.
     */
    GameMode get(@NotNull World world) {
        ConfigSection config = service.getConfigSection(world);
        String gamemode = get(world, config);

        return GameMode.valueOf(gamemode.toUpperCase(Locale.ROOT));
    }

    /**
     * Set the value of this setting for the given world in the config.
     *
     * @param world The world to apply the setting to.
     * @param config The configuration section for the world.
     * @param value The value to set (adventure, creative, spectator, survival, unset).
     */
    @Override
    public void set(@NotNull World world, @NotNull ConfigSection config, @NotNull String value) {
        if(value.equals("adventure") || value.equals("creative") || value.equals("spectator") || value.equals("survival")) {
            config.set("gamemode", value);

            world.getPlayers().forEach(player -> player.setGameMode(GameMode.valueOf(value.toUpperCase(Locale.ROOT))));
        } else {
            config.set("gamemode", null);
        }

        config.save();
    }
}
