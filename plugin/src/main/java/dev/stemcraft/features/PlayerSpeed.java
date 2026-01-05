/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2025 James Collins
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PlayerSpeed extends BaseFeature {
    private final String[] movementTypes = {"fly", "walk"};

    public PlayerSpeed(STEMCraftAPI api) {
        super(api);
    }

    @Override
    public void onEnable() {
        // Tab Completion - Type
        api.tabComplete().register("speedtype", (player, args) -> Arrays.asList(movementTypes));

        // Tab Completion - Speed
        api.tabComplete().register("speed", (player, args) -> {
            String[] speed = {"1", "1.5", "1.75", "2"};
            return Arrays.asList(speed);
        });

        api.commands().create("speed")
                .description("PLAYER_SPEED_DESCRIPTION")
                .usage("PLAYER_SPEED_USAGE")
                .permission("stemcraft.command.speed")
                .tabCompletion("{speedtype}", "{speed}", "{player}")
                .tabCompletion("{speedtype}", "{player}")
                .tabCompletion("{speed}", "{player}")
                .tabCompletion("reset", "{player}")
                .executor((not_used, cmd, ctx) -> {
                    List<String> args = new ArrayList<>(ctx.args());
                    String type = null;
                    Float speed = null;
                    Player targetPlayer = null;

                    if(!args.isEmpty()) {
                        String firstArg = args.getFirst().toLowerCase(Locale.ROOT);
                        if(firstArg.equals("reset") || firstArg.equals("walk") || firstArg.equals("fly")) {
                            type = firstArg;
                            args.removeFirst();
                        }
                    }

                    if(!args.isEmpty()) {
                        try {
                            speed = Float.parseFloat(args.getFirst());
                            args.removeFirst();
                        } catch (NumberFormatException ex) {
                            // ignore
                        }
                    }

                    if(!args.isEmpty()) {
                        targetPlayer = Bukkit.getPlayerExact(args.getFirst());
                        if(targetPlayer == null) {
                            if(ctx.isConsole()) {
                                ctx.returnError("CONSOLE_PLAYER_REQUIRED");
                            } else if (ctx.isPlayer()) {
                                ctx.returnError("PLAYER_NOT_FOUND", "player", args.getFirst());
                            }
                        }
                    } else {
                        if(!ctx.isPlayer()) {
                            ctx.returnError("CONSOLE_PLAYER_REQUIRED");
                        } else {
                            targetPlayer = ctx.getSenderAsPlayer();
                        }
                    }

                    if(targetPlayer == null) {
                        ctx.returnError("COULD_NOT_RESOLVE_PLAYER");
                    }

                    if(type == null) {
                        type = (targetPlayer.isFlying()) ? "fly" : "walk";
                    } else if(type.equals("reset")) {
                        targetPlayer.setFlySpeed(getDefaultSpeed(true));
                        targetPlayer.setWalkSpeed(getDefaultSpeed(false));

                        if(targetPlayer.equals(ctx.getSender())) {
                            ctx.returnInfo("PLAYER_SPEED_RESET");
                        } else {
                            ctx.returnInfo("PLAYER_SPEED_RESET_OTHER", "player", targetPlayer.getName());
                        }
                    }

                    if(speed == null) {
                        if(type.equals("fly")) {
                            speed = targetPlayer.getFlySpeed();
                        } else {
                            speed = targetPlayer.getWalkSpeed();
                        }

                        ctx.returnInfo("PLAYER_SPEED_GET", "type", type, "player", targetPlayer.getName(), "speed", getDisplaySpeed(speed, type.equals("fly")));
                    } else {
                        speed = getRealSpeed(speed, type.equals("fly"));
                        if(type.equals("fly")) {
                            targetPlayer.setFlySpeed(speed);
                        } else {
                            targetPlayer.setWalkSpeed(speed);
                        }

                        ctx.returnInfo("PLAYER_SPEED_SET", "type", type, "player", targetPlayer.getName(), "speed", getDisplaySpeed(speed, type.equals("fly")));
                    }
                })
                .register(STEMCraft.getPlugin());
        }

        private float getDefaultSpeed(final boolean isFly) {
            return isFly ? 0.1f : 0.2f;
        }

        private float getRealSpeed(final float speed, final boolean isFly) {
            final float defaultSpeed = getDefaultSpeed(isFly);
            float maxSpeed = 1f;

            if (speed < 1f) {
                return defaultSpeed * speed;
            } else {
                final float ratio = ((speed - 1) / 9) * (maxSpeed - defaultSpeed);
                return ratio + defaultSpeed;
            }
        }

    private float getDisplaySpeed(final float realSpeed, final boolean isFly) {
        final float defaultSpeed = getDefaultSpeed(isFly);
        final float maxSpeed = 1f;

        // Below default should never happen, but clamp defensively
        if (realSpeed <= defaultSpeed) {
            return 1f;
        }

        // Reverse of: default + ((speed - 1) / 9) * (max - default)
        float ratio = (realSpeed - defaultSpeed) / (maxSpeed - defaultSpeed);
        float display = 1f + (ratio * 9f);

        // Round to something sane for chat
        return Math.round(display * 10f) / 10f;
    }
}
