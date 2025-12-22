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
        api.tabComplete().register("speedtype", (player, args) -> {
            return Arrays.asList(movementTypes);
        });

        // Tab Completion - Speed
        api.tabComplete().register("speed", (player, args) -> {
            String[] speed = {"1", "1.5", "1.75", "2"};
            return Arrays.asList(speed);
        });

        api.registerCommand("speed")
                .setDescription("PLAYER_SPEED_DESCRIPTION")
                .setUsage("PLAYER_SPEED_USAGE")
                .setPermission("stemcraft.command.speed")
                .addTabCompletion("{speedtype}", "{speed}", "{player}")
                .addTabCompletion("{speedtype}", "{player}")
                .addTabCompletion("{speed}", "{player}")
                .addTabCompletion("reset", "{player}")
                .setExecutor((not_used, cmd, ctx) -> {
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
