package dev.stemcraft.service.resourcepack;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import org.bukkit.entity.Player;

/**
 * Command for managing the STEMCraft resource pack.
 */
public class ResourcePackCommand {
    private final STEMCraftAPI api;
    private final ResourcePackService service;

    /**
     * Constructs a ResourcePackCommand with the specified API and service.
     *
     * @param api The STEMCraft API instance.
     * @param service The ResourcePackService instance.
     */
    public ResourcePackCommand(STEMCraftAPI api, ResourcePackService service) {
        this.api = api;
        this.service = service;
    }

    /**
     * Registers the resource pack command.
     */
    public void onEnable() {
        api.commands().create("resourcepack")
                .aliases("respack")
                .usage("RESOURCEPACK_USAGE")
                .description("RESOURCEPACK_DESCRIPTION")
                .tabCompletion("{player}")
                .tabCompletion("send", "{player}")
                .tabCompletion("sendall")
                .tabCompletion("zip")
                .permission("stemcraft.command.resourcepack")
                .executor((api, cmd, ctx) -> {
                    if(ctx.numArgs() == 0) {
                        subCommandInfo(ctx);
                        return;
                    }

                    String subCommand = ctx.getArgLower(0);
                    if (subCommand == null) {
                        ctx.returnUsage();
                    }

                    switch(subCommand) {
                        case "info" -> subCommandInfo(ctx);
                        case "send" -> subCommandSend(ctx);
                        case "sendall" -> subCommandSendAll(ctx);
                        case "zip" -> subCommandZip(ctx);
                        default -> ctx.returnUsage();
                    }
                })
                .register(STEMCraft.getPlugin());
    }

    /**
     * Handles the "info" sub-command to display resource pack information.
     *
     * @param ctx The command context.
     */
    private void subCommandInfo(CommandContext ctx) {
        String packUrl = service.host().getUrl();
        String packHash = service.getResourcePackHash();

        ctx.info("RESOURCEPACK_INFO_HEADER");
        ctx.info("RESOURCEPACK_INFO_URL", "url", packUrl);
        if (packHash != null) {
            ctx.info("RESOURCEPACK_INFO_HASH", "hash", packHash);
        } else {
            ctx.info("RESOURCEPACK_INFO_HASH_UNAVAILABLE");
        }
    }

    /**
     * Sends the resource pack to a player.
     *
     * @param ctx The command context.
     */
    private void subCommandSend(CommandContext ctx) {
        Player target = null;

        if (ctx.numArgs() == 1) {
            if (ctx.isPlayer()) {
                target = ctx.asPlayer();
            } else {
                ctx.returnError("RESOURCEPACK_PLAYER_REQUIRED");
            }
        } else {
            target = ctx.getPlayer(1);
        }

        if(target == null) {
            ctx.returnError("RESOURCEPACK_PLAYER_NOT_FOUND");
            return;
        }

        service.host().getUrl();

        service.sendPack(target);
        ctx.returnInfo("RESOURCEPACK_REQUESTING", "player", target.getName());
    }

    /**
     * Sends the resource pack to all players.
     *
     * @param ctx The command context.
     */
    private void subCommandSendAll(CommandContext ctx) {
        service.host().getUrl();

        service.sendPackToAll();
        ctx.returnInfo("RESOURCEPACK_SENDING_ALL");
    }

    /**
     * Generates and uploads the resource pack.
     *
     * @param ctx The command context.
     */
    private void subCommandZip(CommandContext ctx) {
        service.generatePack(progress -> {
            switch(progress) {
                case "generating" -> ctx.info("RESOURCEPACK_GENERATING");
                case "compressing" -> ctx.info("RESOURCEPACK_COMPRESSING");
                case "uploading" -> ctx.info("RESOURCEPACK_UPLOADING");
                case "complete" -> {
                    ctx.info("RESOURCEPACK_COMPLETE");
                    service.sendPackToAll();
                }
                default -> {
                }
            }
        });
    }
}
