package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Arrays;
import java.util.Locale;

public class PlayerSpeed implements STEMCraftFeature {
    private String[] movementTypes = {"fly", "walk"};

    @Override
    public void onEnable(STEMCraftAPI api) {
        // Tab Completion - Type
        api.tabComplete().register("speedtype", () -> {
            return Arrays.asList(movementTypes);
        });

        // Tab Completion - Speed
        api.tabComplete().register("speed", () -> {
            String[] speed = {"1", "1.5", "1.75", "2"};
            return Arrays.asList(speed);
        });

        api.registerCommand("speed")
                .setPermission("stemcraft.command.speed")
                .addTabCompletion("{speedtype}", "{speed}", "{player}")
                .addTabCompletion("{speed}", "{player}")
                .addTabCompletion("reset", "{player}")
                .setExecutor((not_used, cmd, ctx) -> {
                    if(!Arrays.asList(movementTypes).contains(ctx.getArg(1).toLowerCase(Locale.ROOT))) {
                        cmd.error(ctx.getSender(), cmd.getUsage());
                        return;
                    }

                    String type = ctx.getArg(1);
                    Float speed = null;
                    Player targetPlayer = null;
                    boolean reset = false;

//                    if (SMCommon.isInArrayIgnoreCase(movementTypes, type)) {
//                        speed = ctx.getArgFloatLocale(2, "SPEED_NUMBER_USAGE");
//                        targetPlayer = ctx.getArgAsPlayer(3, ctx.player);
//                    } else {
//                        if (!type.equalsIgnoreCase("reset")) {
//                            speed = ctx.getArgFloatLocale(1, "SPEED_NUMBER_USAGE");
//                        } else {
//                            reset = true;
//                        }
//
//                        targetPlayer = ctx.getArgAsPlayer(2, ctx.player);
//                    }
//
//                    if (ctx.fromConsole() && targetPlayer == null) {
//                        ctx.returnErrorLocale("CMD_PLAYER_REQ_FROM_CONSOLE");
//                    }

                    if (reset) {
                        targetPlayer.setFlySpeed(getDefaultSpeed(true));
                        targetPlayer.setWalkSpeed(getDefaultSpeed(false));
//                        ctx.returnInfoLocale("SPEED_RESET");
                    } else {
                        if (speed < 0.1f) {
                            speed = 0.1f;
                        } else if (speed > 10f) {
                            speed = 10f;
                        }

//                        if (!SMCommon.isInArrayIgnoreCase(movementTypes, type)) {
//                            type = targetPlayer.isFlying() ? "fly" : "walk";
//                        }

                        if (type.equalsIgnoreCase("fly")) {
                            targetPlayer.setFlySpeed(getRealSpeed(speed, true));
                        } else {
                            targetPlayer.setWalkSpeed(getRealSpeed(speed, false));
                        }

//                        ctx.returnInfoLocale("SPEED_CHANGED", "type", SMCommon.capitalize(type), "speed", speed.toString());
                    }
                })
                .register(STEMCraft.getInstance());
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
}
