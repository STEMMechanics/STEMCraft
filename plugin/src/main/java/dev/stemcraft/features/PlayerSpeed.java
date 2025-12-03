package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PlayerSpeed implements STEMCraftFeature {
    private final String[] movementTypes = {"fly", "walk"};

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
                .setDescription("PLAYER_SPEED_DESCRIPTION")
                .setPermission("stemcraft.command.speed")
                .addTabCompletion("{speedtype}", "{speed}", "{player}")
                .addTabCompletion("{speed}", "{player}")
                .addTabCompletion("reset", "{player}")
                .setExecutor((not_used, cmd, ctx) -> {
                    List<String> args = new ArrayList<>(ctx.args());
                    String type = null;
                    Float speed = null;
                    Player targetPlayer = null;

                    // First arg could be type or reset
                    String rawArg = args.getFirst().toLowerCase(Locale.ROOT);
                    if(Arrays.asList(movementTypes).contains(args.getFirst()) || args.getFirst().equals("reset")) {
                        type = rawArg;
                        args.removeFirst();
                    }

                    if(!args.isEmpty()) {
                        if(type == null || !type.equals("reset")) {
                            try {
                                speed = Float.parseFloat(args.getFirst());
                                args.removeFirst();

                                speed = Math.max(0.1f, speed);
                                speed = Math.min(10f, speed);

                            } catch(NumberFormatException ex) {
                                // ignore
                            }
                        }
                    }

                    if(!args.isEmpty()) {
                        targetPlayer = Bukkit.getPlayerExact(args.getFirst());
                    }

                    if(targetPlayer == null) {
                        if(ctx.isConsole()) {
                            cmd.error("CONSOLE_PLAYER_REQUIRED");
                            return;
                        } else if(ctx.isPlayer() && !args.isEmpty()) {
                            cmd.error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", args.getFirst());
                            return;
                        } else {
                            targetPlayer = (Player)ctx.getSender();
                        }
                    }

                    if(type == null) {
                        if(speed == null) {
                            cmd.error(ctx.getSender(), cmd.getUsage());
                        } else {
                            type = (targetPlayer.isFlying()) ? "fly" : "walk";
                        }
                    } else if(type.equals("reset")) {
                        targetPlayer.setFlySpeed(getDefaultSpeed(true));
                        targetPlayer.setWalkSpeed(getDefaultSpeed(false));
                        // output reset
                    }

                    if(speed == null) {
                        // display current
                    } else {
                        // set fly speed
                    }// display current
// set walk speed
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
