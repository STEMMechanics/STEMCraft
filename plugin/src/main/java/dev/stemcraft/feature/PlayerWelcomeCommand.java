package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.command.BaseCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PlayerWelcomeCommand {
    private static final String PERMISSION = "stemcraft.admin.welcome";
    private final STEMCraft plugin;
    private final PlayerWelcome editorView;

    public PlayerWelcomeCommand(STEMCraft plugin, STEMCraftAPI api) {
        this.plugin = plugin;
        this.editorView = new PlayerWelcome(api);
    }

    public void addTabCompletions(BaseCommand command) {
        command.addTabCompletion("welcome");
        command.addTabCompletion("welcome", "show");
        command.addTabCompletion("welcome", "preview");
        command.addTabCompletion("welcome", "clear");
        command.addTabCompletion("welcome", "addline");
        command.addTabCompletion("welcome", "insertline");
        command.addTabCompletion("welcome", "setline");
        command.addTabCompletion("welcome", "removeline");
        command.addTabCompletion("welcome", "addblank");
        command.addTabCompletion("welcome", "insertblank");

        for (String action : List.of("show", "preview", "clear", "addline", "insertline", "setline", "removeline", "addblank", "insertblank")) {
            command.addTabCompletion("welcome", action, "first");
            command.addTabCompletion("welcome", action, "returning");
            command.addTabCompletion("welcome", action, "anniversary");
            command.addTabCompletion("welcome", action, "anniversary", "");
        }
    }

    public boolean handle(Command cmd, CommandContext ctx) {
        String root = Objects.requireNonNullElse(ctx.getArgLower(0), "");
        if (!"welcome".equals(root)) {
            return false;
        }

        if (!ctx.hasPermission(PERMISSION)) {
            ctx.returnError("You do not have permission to manage player welcome messages.");
            return true;
        }

        String action = ctx.getArgLower(1);
        if (action == null) {
            ctx.returnError(usage());
            return true;
        }

        switch (action) {
            case "show" -> showMessage(ctx);
            case "preview" -> previewMessage(ctx);
            case "clear" -> clearMessage(ctx);
            case "addline" -> addLine(ctx);
            case "insertline" -> insertLine(ctx);
            case "setline" -> setLine(ctx);
            case "removeline" -> removeLine(ctx);
            case "addblank" -> addBlank(ctx);
            case "insertblank" -> insertBlank(ctx);
            default -> ctx.returnError(usage());
        }
        return true;
    }

    private void showMessage(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        List<String> lines = editorView.getConfiguredLines(target.kind(), target.anniversaryYear());

        ctx.info("Welcome message lines for " + target.describe() + ":");
        if (lines.isEmpty()) {
            ctx.getSender().sendMessage(Component.text("<empty>", NamedTextColor.DARK_GRAY));
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Component component = Component.text("#" + (i + 1) + " ", NamedTextColor.DARK_GRAY)
                .append(Component.text(PlayerWelcome.displayLine(line), line == null || line.isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD));
            ctx.getSender().sendMessage(component);
        }
    }

    private void previewMessage(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        Player player = resolvePreviewPlayer(ctx, target.nextArgIndex());
        List<Component> previewLines = editorView.previewComponents(player, target.kind(), target.anniversaryYear());
        if (previewLines.isEmpty()) {
            ctx.returnInfo("No usable welcome message is configured for " + target.describe() + ".");
        }

        ctx.info("Previewing " + target.describe() + " using " + player.getName() + ":");
        for (Component line : previewLines) {
            ctx.getSender().sendMessage(line);
        }
    }

    private void clearMessage(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        editorView.clearConfiguredLines(target.kind(), target.anniversaryYear());
        syncRuntimeFeature();
        ctx.returnSuccess("Cleared the " + target.describe() + " welcome message.");
    }

    private void addLine(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        String line = requireRemainingText(ctx, target.nextArgIndex(), "Line text is required.");
        editorView.addConfiguredLine(target.kind(), target.anniversaryYear(), line);
        syncRuntimeFeature();
        ctx.returnSuccess("Added line #" + editorView.getConfiguredLines(target.kind(), target.anniversaryYear()).size() + " to " + target.describe() + ".");
    }

    private void insertLine(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        int lineNumber = ctx.getArgAsInt(target.nextArgIndex(), -1);
        if (lineNumber < 1) {
            ctx.returnError("Line number must be 1 or greater.");
        }
        String line = requireRemainingText(ctx, target.nextArgIndex() + 1, "Line text is required.");
        editorView.insertConfiguredLine(target.kind(), target.anniversaryYear(), lineNumber, line);
        syncRuntimeFeature();
        ctx.returnSuccess("Inserted line #" + lineNumber + " into " + target.describe() + ".");
    }

    private void setLine(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        int lineNumber = ctx.getArgAsInt(target.nextArgIndex(), -1);
        if (lineNumber < 1) {
            ctx.returnError("Line number must be 1 or greater.");
        }
        String line = requireRemainingText(ctx, target.nextArgIndex() + 1, "Line text is required.");
        editorView.setConfiguredLine(target.kind(), target.anniversaryYear(), lineNumber, line);
        syncRuntimeFeature();
        ctx.returnSuccess("Updated line #" + lineNumber + " in " + target.describe() + ".");
    }

    private void removeLine(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        int lineNumber = ctx.getArgAsInt(target.nextArgIndex(), -1);
        if (lineNumber < 1) {
            ctx.returnError("Line number must be 1 or greater.");
        }
        String removed = editorView.removeConfiguredLine(target.kind(), target.anniversaryYear(), lineNumber);
        syncRuntimeFeature();
        ctx.returnSuccess("Removed line #" + lineNumber + " from " + target.describe() + ": " + PlayerWelcome.displayLine(removed));
    }

    private void addBlank(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        editorView.addConfiguredLine(target.kind(), target.anniversaryYear(), "");
        syncRuntimeFeature();
        ctx.returnSuccess("Added a blank line to " + target.describe() + ".");
    }

    private void insertBlank(CommandContext ctx) {
        TargetSpec target = parseTarget(ctx, 2);
        int lineNumber = ctx.getArgAsInt(target.nextArgIndex(), -1);
        if (lineNumber < 1) {
            ctx.returnError("Line number must be 1 or greater.");
        }
        editorView.insertConfiguredLine(target.kind(), target.anniversaryYear(), lineNumber, "");
        syncRuntimeFeature();
        ctx.returnSuccess("Inserted a blank line at #" + lineNumber + " in " + target.describe() + ".");
    }

    private void syncRuntimeFeature() {
        PlayerWelcome liveFeature = plugin.feature(PlayerWelcome.class);
        if (liveFeature != null) {
            liveFeature.onReload();
        }
        editorView.onReload();
    }

    private Player resolvePreviewPlayer(CommandContext ctx, int playerArgIndex) {
        Player player = ctx.getPlayer(playerArgIndex, null);
        if (player != null) {
            return player;
        }

        if (ctx.isPlayer()) {
            return ctx.asPlayer();
        }

        ctx.returnError("Preview from console requires an online player: /stemcraft welcome preview <type> ... <player>");
        return null;
    }

    private String requireRemainingText(CommandContext ctx, int startIndex, String error) {
        String value = ctx.getArgsAsString(startIndex, "").trim();
        if (value.isEmpty()) {
            ctx.returnError(error);
        }
        return value;
    }

    private TargetSpec parseTarget(CommandContext ctx, int startIndex) {
        String rawType = ctx.getArgLower(startIndex);
        if (rawType == null) {
            ctx.returnError(usage());
        }

        PlayerWelcome.MessageKind kind = parseKind(rawType);
        if (kind == null) {
            ctx.returnError("Unknown welcome message type '" + rawType + "'. Use first, returning, or anniversary.");
        }

        Integer year = null;
        int nextArgIndex = startIndex + 1;
        if (kind == PlayerWelcome.MessageKind.ANNIVERSARY) {
            year = ctx.getArgAsInt(nextArgIndex, -1);
            if (year == null || year < 1) {
                ctx.returnError("Anniversary welcome messages require a year number, for example: /stemcraft welcome show anniversary 1");
            }
            nextArgIndex++;
        }

        return new TargetSpec(kind, year, nextArgIndex);
    }

    private PlayerWelcome.MessageKind parseKind(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "first", "firsttime", "first-time", "new" -> PlayerWelcome.MessageKind.FIRST_TIME;
            case "return", "returning", "back" -> PlayerWelcome.MessageKind.RETURNING;
            case "anniversary", "anniversaries", "cake", "cakeday", "cake-day" -> PlayerWelcome.MessageKind.ANNIVERSARY;
            default -> null;
        };
    }

    private String usage() {
        return "/stemcraft welcome <show|preview|clear|addline|insertline|setline|removeline|addblank|insertblank> <first|returning|anniversary [year]> ...";
    }

    private record TargetSpec(PlayerWelcome.MessageKind kind, Integer anniversaryYear, int nextArgIndex) {
        private String describe() {
            if (kind == PlayerWelcome.MessageKind.ANNIVERSARY) {
                return "anniversary " + anniversaryYear;
            }
            return kind.displayName();
        }
    }
}
