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
                .usage("/resourcepack [send] [player] | /resourcepack zip")
                .description("Manage the STEMCraft resource pack")
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

                    switch(ctx.getArgLower(1)) {
                        case "info" -> subCommandInfo(ctx);
                        case "send" -> subCommandSend(ctx);
                        case "sendall" -> subCommandSendAll(ctx);
                        case "zip" -> subCommandZip(ctx);
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

        ctx.info("Resource pack info:");
        ctx.info(" - URL: " + packUrl);
        ctx.info(" - Hash: " + (packHash != null ? packHash : "not available"));
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
                target = ctx.getSenderAsPlayer();
            } else {
                ctx.returnError("You must specify a player when executing from console.");
            }
        } else {
            target = ctx.getArgAsPlayer(1);
        }

        if(target == null) {
            ctx.returnError("Player not found.");
        }

        service.host().getUrl();

        service.sendPack(target);
        ctx.returnInfo("Requesting resource pack download for " + target.getName() + "...");
    }

    /**
     * Sends the resource pack to all players.
     *
     * @param ctx The command context.
     */
    private void subCommandSendAll(CommandContext ctx) {
        service.host().getUrl();

        service.sendPackToAll();
        ctx.returnInfo("Sending resource pack to all online players...");
    }

    /**
     * Generates and uploads the resource pack.
     *
     * @param ctx The command context.
     */
    private void subCommandZip(CommandContext ctx) {
        service.generatePack(progress -> {
            switch(progress) {
                case "generating" -> ctx.info("Generating resource pack...");
                case "compressing" -> ctx.info("Compressing resource pack...");
                case "uploading" -> ctx.info("Uploading resource pack...");
                case "complete" -> {
                    ctx.info("Resource pack is complete!");
                    service.sendPackToAll();
                }
            }
        });
    }
}