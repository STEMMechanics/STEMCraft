package dev.stemcraft.service.firstjoin;

import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.command.BaseCommand;

import java.util.Objects;

public final class FirstJoinCommand {
    private static final String PERMISSION = "stemcraft.admin.firstjoin";
    private final FirstJoinService service;

    public FirstJoinCommand(FirstJoinService service) {
        this.service = service;
    }

    public void addTabCompletions(BaseCommand command) {
        command.addTabCompletion("firstjoin");
        command.addTabCompletion("firstjoin", "status", "{player}");
        command.addTabCompletion("firstjoin", "reset", "{player}");
    }

    public boolean handle(Command cmd, CommandContext ctx) {
        String root = Objects.requireNonNullElse(ctx.getArgLower(0), "");
        if (!"firstjoin".equals(root)) {
            return false;
        }

        if (!ctx.hasPermission(PERMISSION)) {
            ctx.returnError("You do not have permission to manage first-join checks.");
            return true;
        }

        String action = ctx.getArgLower(1);
        if (action == null) {
            ctx.returnError("Usage: /stemcraft firstjoin <status|reset> <player>");
            return true;
        }

        switch (action) {
            case "status" -> service.handleAdminStatus(ctx);
            case "reset" -> service.handleAdminReset(ctx);
            default -> ctx.returnError("Usage: /stemcraft firstjoin <status|reset> <player>");
        }
        return true;
    }
}
