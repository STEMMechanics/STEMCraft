package dev.stemcraft.minigame.skyblock;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SkyBlockCommand {
    private static final String PERMISSION = "stemcraft.command.skyblock";
    private static final String PERMISSION_OTHERS = PERMISSION + ".others";

    private final STEMCraftAPI api;
    private final SkyBlockMiniGame skyBlock;

    public SkyBlockCommand(STEMCraftAPI api, SkyBlockMiniGame skyBlock) {
        this.api = api;
        this.skyBlock = skyBlock;
    }

    public void onEnable() {
        api.tabComplete().register("skyblock-owners", (sender, args) -> skyBlock.minigame().arenas().stream()
            .map(skyBlock::ownerName)
            .sorted(String::compareToIgnoreCase)
            .toList());

        api.commands().create("skyblock")
            .permission(PERMISSION)
            .usage("/skyblock <list|info|join|spectate|leave|reset|reload|pool>")
            .tabCompletion("list")
            .tabCompletion("list", "{int}")
            .tabCompletion("info", "{skyblock-owners}")
            .tabCompletion("join", "{player}")
            .tabCompletion("spectate", "{skyblock-owners}", "{player}")
            .tabCompletion("leave", "{player}")
            .tabCompletion("reset", "{skyblock-owners}")
            .tabCompletion("reload")
            .tabCompletion("pool")
            .executor((ignored, cmd, ctx) -> {
                ctx.checkArgsSizeAtLeast(1);

                switch (ctx.getArgLower(0)) {
                    case "list" -> commandList(ctx);
                    case "info" -> commandInfo(ctx);
                    case "join" -> commandJoin(ctx);
                    case "spectate" -> commandSpectate(ctx);
                    case "leave" -> commandLeave(ctx);
                    case "reset" -> commandReset(ctx);
                    case "reload" -> commandReload(ctx);
                    case "pool" -> commandPool(ctx);
                    default -> ctx.returnUsage();
                }
            })
            .register(STEMCraft.getPlugin());
    }

    private void commandList(CommandContext ctx) {
        List<MiniGameArena> arenas = skyBlock.minigame().arenas().stream()
            .sorted((left, right) -> skyBlock.ownerName(left).compareToIgnoreCase(skyBlock.ownerName(right)))
            .toList();

        ChatMenuUtil.render(
            ctx.getSender(),
            "SkyBlock Games",
            "skyblock list",
            ctx.getArgAsInt(1, 1),
            arenas.size(),
            (start, count, isPlayer) -> {
                List<Component> lines = new ArrayList<>();
                int end = Math.min(start + count, arenas.size());
                for (int i = start; i < end; i++) {
                    MiniGameArena arena = arenas.get(i);
                    Component line = Component.text(skyBlock.ownerName(arena), NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(Component.text("Show info for " + skyBlock.ownerName(arena))))
                        .clickEvent(ClickEvent.runCommand("/skyblock info " + skyBlock.ownerName(arena)))
                        .append(Component.text(" [" + arena.getStatus().name().toLowerCase(Locale.ROOT) + "] ", statusColour(arena)))
                        .append(Component.text(arena.world().getName(), NamedTextColor.GRAY))
                        .append(Component.text(" " + arena.numPlayers() + "/" + arena.getMaxPlayers(), NamedTextColor.AQUA));

                    if (isPlayer) {
                        line = line
                            .append(Component.text(" "))
                            .append(Component.text("[Info]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/skyblock info " + skyBlock.ownerName(arena)))
                                .hoverEvent(HoverEvent.showText(Component.text("Show island details"))));
                    }

                    lines.add(line);
                }
                return lines;
            },
            "No SkyBlock games are active."
        );
    }

    private void commandInfo(CommandContext ctx) {
        MiniGameArena arena = requireArenaByOwnerOrId(ctx, 1, true);
        ctx.info("SkyBlock '" + skyBlock.ownerName(arena) + "':");
        ctx.info(" - Arena id: " + arena.id());
        ctx.info(" - Status: " + arena.getStatus().name());
        ctx.info(" - World: " + arena.world().getName());
        ctx.info(" - Owner UUID: " + arena.get("ownerUuid", String.class, ""));
        ctx.info(" - Spawn: " + formatLocation(arena.getLobbySpawn()));
        ctx.info(" - Occupants: " + arena.getOccupants().size());
        ctx.info(" - Saved owner state: " + (skyBlock.savedPlayerState(arena) != null ? "yes" : "no"));
    }

    private void commandJoin(CommandContext ctx) {
        Player targetPlayer = ctx.getArgAsPlayerOrSender(1);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }

        MiniGameArena existingArena = skyBlock.minigame().findPlayer(targetPlayer);
        MiniGameArena targetArena = skyBlock.createOrResumeGame(targetPlayer);
        if (existingArena != null && existingArena == targetArena) {
            ctx.success("Player '" + targetPlayer.getName() + "' is already in their SkyBlock game.");
            return;
        }
        if (existingArena != null) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }

        targetArena.addPlayer(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' joined SkyBlock '" + skyBlock.ownerName(targetArena) + "'.");
    }

    private void commandSpectate(CommandContext ctx) {
        ctx.checkArgsSizeAtLeast(2);
        MiniGameArena arena = requireArenaByOwnerOrId(ctx, 1, false);
        Player spectator = ctx.getArgAsPlayerOrSender(2);
        if (spectator == null) {
            ctx.returnError("Player is required.");
        }

        MiniGameArena existingArena = skyBlock.minigame().findPlayer(spectator);
        if (existingArena != null) {
            ctx.returnError("Player '" + spectator.getName() + "' is already in arena '" + existingArena.id() + "'.");
        }

        arena.addSpectator(spectator);
        ctx.success("Player '" + spectator.getName() + "' is now spectating SkyBlock '" + skyBlock.ownerName(arena) + "'.");
    }

    private void commandLeave(CommandContext ctx) {
        Player targetPlayer = ctx.getArgAsPlayerOrSender(1);
        if (targetPlayer == null) {
            ctx.returnError("Player is required.");
        }

        MiniGameArena arena = skyBlock.minigame().findPlayer(targetPlayer);
        if (arena == null || !SkyBlockMiniGame.namespace().equals(arena.namespace())) {
            ctx.returnError("Player '" + targetPlayer.getName() + "' is not in a SkyBlock game.");
        }

        arena.removeOccupant(targetPlayer);
        ctx.success("Player '" + targetPlayer.getName() + "' left SkyBlock '" + skyBlock.ownerName(arena) + "'.");
    }

    private void commandReset(CommandContext ctx) {
        MiniGameArena arena = requireArenaByOwnerOrId(ctx, 1, true);
        Player sender = ctx.asPlayer();
        if ((sender == null || !skyBlock.isOwner(arena, sender)) && !ctx.hasPermission(PERMISSION_OTHERS)) {
            ctx.returnError("COMMAND_NO_PERMISSION_OTHERS");
        }

        skyBlock.endGame(arena, "Your SkyBlock island was reset.");
        ctx.success("SkyBlock '" + skyBlock.ownerName(arena) + "' was reset.");
    }

    private void commandReload(CommandContext ctx) {
        if (!skyBlock.reloadFromConfig()) {
            ctx.returnError("SkyBlock config could not be reloaded while islands have active occupants.");
        }
        ctx.success("SkyBlock config reloaded. Loaded " + skyBlock.minigame().arenas().size() + " active games.");
    }

    private void commandPool(CommandContext ctx) {
        List<String> standbyWorlds = skyBlock.standbyWorlds();
        ctx.info("Standby worlds: " + standbyWorlds.size());
        for (String worldName : standbyWorlds) {
            ctx.info(" - " + worldName);
        }
    }

    private MiniGameArena requireArenaByOwnerOrId(CommandContext ctx, int index, boolean allowCurrentPlayerDefault) {
        if (ctx.numArgs() <= index) {
            if (!allowCurrentPlayerDefault) {
                ctx.returnError("Specify a SkyBlock owner or arena id.");
            }

            Player player = ctx.asPlayer();
            if (player == null) {
                ctx.returnError("Specify a SkyBlock owner or arena id when running from console.");
            }

            MiniGameArena ownerArena = skyBlock.findArenaByOwner(player.getUniqueId());
            if (ownerArena != null) {
                return ownerArena;
            }

            MiniGameArena currentArena = skyBlock.minigame().findPlayer(player);
            if (currentArena != null && SkyBlockMiniGame.namespace().equals(currentArena.namespace())) {
                return currentArena;
            }

            ctx.returnError("No SkyBlock game was found for '" + player.getName() + "'.");
        }

        String token = ctx.getArg(index);
        MiniGameArena direct = skyBlock.minigame().arena(token);
        if (direct != null) {
            return direct;
        }

        Player onlineOwner = Bukkit.getPlayerExact(token);
        if (onlineOwner != null) {
            MiniGameArena arena = skyBlock.findArenaByOwner(onlineOwner.getUniqueId());
            if (arena != null) {
                return arena;
            }
        }

        try {
            UUID ownerUuid = UUID.fromString(token);
            MiniGameArena arena = skyBlock.findArenaByOwner(ownerUuid);
            if (arena != null) {
                return arena;
            }
        } catch (IllegalArgumentException ignored) {
            // not a UUID token
        }

        for (MiniGameArena arena : skyBlock.minigame().arenas()) {
            if (skyBlock.ownerName(arena).equalsIgnoreCase(token)) {
                return arena;
            }
        }

        ctx.returnError("No SkyBlock game was found for '" + token + "'.");
        return null;
    }

    private NamedTextColor statusColour(MiniGameArena arena) {
        return switch (arena.getStatus()) {
            case IDLE, WAITING, RUNNING -> NamedTextColor.GREEN;
            case STARTING, PREPARATION, COOLDOWN, ENDING, RESETTING -> NamedTextColor.GOLD;
            case SETUP -> NamedTextColor.BLUE;
            case DISABLED, SHUTDOWN -> NamedTextColor.RED;
        };
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "<unset>";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
