package dev.stemcraft.permission;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared authorization rules. Default-deny unknown administrative subcommands. */
public final class PlayerCommandAccess {
    private PlayerCommandAccess() { }

    public static boolean has(CommandSender sender, String permission) {
        return sender instanceof ConsoleCommandSender || sender.hasPermission(permission);
    }

    public static boolean owns(CommandSender sender, java.util.UUID owner, String ownPermission, String adminPermission) {
        return has(sender, adminPermission) || sender instanceof Player player
            && player.getUniqueId().equals(owner) && has(sender, ownPermission);
    }

    public static boolean ownTarget(CommandSender sender, List<String> args, int index, String others) {
        if (index >= args.size() || args.get(index).isBlank()) return true;
        return sender instanceof Player player && player.getName().equalsIgnoreCase(args.get(index))
            || has(sender, others);
    }

    public static boolean gameAdmin(CommandSender sender, String game) {
        return has(sender, "stemcraft.minigame." + game + ".admin")
            || has(sender, "stemcraft.command." + game); // legacy root was administrative
    }

    public static boolean minigame(CommandSender sender, List<String> args, String game) {
        if (gameAdmin(sender, game)) return true;
        String prefix = "stemcraft.minigame." + game;
        if (!has(sender, prefix + ".play")) return false;
        String sub = first(args);
        if (Set.of("", "list", "info").contains(sub)) return true;
        int target = switch (sub) {
            case "join" -> game.equals("skyblock") ? 1 : 2;
            case "leave" -> 1;
            case "spectate" -> game.equals("parkour") ? -1 : 2;
            case "restart" -> game.equals("parkour") ? 1 : -1;
            // Island ownership is checked after resolving its name/id in SkyBlockCommand.
            case "reset" -> game.equals("skyblock") && has(sender, prefix + ".reset") ? Integer.MAX_VALUE : -1;
            default -> -1;
        };
        return target >= 0 && ownTarget(sender, args, target, prefix + ".others");
    }

    public static boolean mailbox(CommandSender sender, List<String> args) {
        if (has(sender, "stemcraft.mailbox.admin") || has(sender, "stemcraft.mailbox")) return true;
        return first(args).equals("send") && has(sender, "stemcraft.mailbox.send");
    }

    public static boolean namedRegion(CommandSender sender, List<String> args) {
        if (has(sender, "stemcraft.command.namedregion") || has(sender, "stemcraft.namedregion.admin")) return true;
        return switch (first(args)) {
            case "", "info", "list", "find", "nearby" -> has(sender, "stemcraft.namedregion.read");
            case "teleport" -> has(sender, "stemcraft.namedregion.teleport");
            default -> false;
        };
    }

    public static boolean noticeboard(CommandSender sender, List<String> args) {
        if (has(sender, "stemcraft.noticeboard.admin")) return true;
        return switch (first(args)) {
            case "", "list", "mine" -> has(sender, "stemcraft.noticeboard.read");
            case "post", "edit", "remove" -> has(sender, "stemcraft.noticeboard.post");
            default -> false;
        };
    }

    public static boolean book(CommandSender sender, List<String> args) {
        return switch (first(args)) {
            case "", "get", "list" -> true; // base stemcraft.book still checked
            case "show" -> ownTarget(sender, args, 2, "stemcraft.book.others");
            default -> has(sender, "stemcraft.book.edit");
        };
    }

    public static boolean spawn(CommandSender sender, List<String> args) {
        if (!ownTarget(sender, args, 1, "stemcraft.command.spawn.others")) return false;
        return args.isEmpty() || sender instanceof Player player && player.getWorld().getName().equalsIgnoreCase(args.getFirst())
            || has(sender, "stemcraft.command.spawn.worlds")
            || has(sender, "stemcraft.command.spawn.world." + args.getFirst().toLowerCase(Locale.ROOT));
    }

    public static boolean speed(CommandSender sender, List<String> args) {
        int index = 0;
        if (args.isEmpty()) return true;
        if (Set.of("walk", "fly", "reset").contains(args.getFirst().toLowerCase(Locale.ROOT))) index++;
        if (index < args.size()) {
            try { Float.parseFloat(args.get(index)); index++; } catch (NumberFormatException ignored) { }
        }
        return ownTarget(sender, args, index, "stemcraft.command.speed.others");
    }

    private static String first(List<String> args) {
        return args.isEmpty() ? "" : args.getFirst().toLowerCase(Locale.ROOT);
    }
}
