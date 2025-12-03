package dev.stemcraft.commands;

import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import dev.stemcraft.api.utils.SCTime;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public class STEMCraftCommandContextImpl implements STEMCraftCommandContext {
    @Getter
    private final STEMCraftCommand command;
    @Getter
    private final CommandSender sender;
    @Getter
    final String labelUsed;
    private final List<String> args;

    public STEMCraftCommandContextImpl(STEMCraftCommand command, CommandSender sender, String labelUsed, List<String> args) {
        this.command = command;
        this.sender = sender;
        this.labelUsed = labelUsed.toLowerCase(Locale.ROOT);
        this.args = args;
    }

    public String getLabel() { return command.getLabel(); }

    public List<String> args() {
        return args;
    }

    public Player getSenderAsPlayer() {
        if(sender instanceof Player player) {
            return player;
        }

        return null;
    }

    public boolean isConsole() {
        return !(sender instanceof org.bukkit.entity.Player);
    }

    public boolean isPlayer() {
        return sender instanceof org.bukkit.entity.Player;
    }

    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    public String getArg(int index) { return getArg(index, null); }

    public String getArg(int index, String def) {
        if(index < 1 || index > args.size()) {
            return def;
        }

        return args.get(index - 1);
    }

    public String getArgsAsString(int index, String def) {
        // convert 1-based → 0-based
        int start = index - 1;

        if (start < 0 || start >= args.size()) {
            return def;
        }

        return String.join(" ", args.subList(start, args.size()));
    }

    public Float getArgAsFloat(int index, Float def) {
        if(index < 1 || index > args.size()) {
            return def;
        }

        try {
            return Float.parseFloat(args.get(index - 1));
        } catch(NumberFormatException ex) {
            return def;
        }
    }

    public Player getArgAsPlayer(int index, CommandSender def) {
        if(def instanceof Player) {
            return getArgAsPlayer(index, (Player)def);
        } else {
            return getArgAsPlayer(index, null);
        }
    }

    public Player getArgAsPlayer(int index, Player def) {
        String playerName = getArg(index, null);
        if(playerName == null) {
            return def;
        }

        return Bukkit.getPlayerExact(playerName);
    }

    public OfflinePlayer getArgAsOfflinePlayer(int index, CommandSender def) {
        String playerName = getArg(index, null);
        if(playerName == null) {
            if (def instanceof Player) {
                return (OfflinePlayer) def;
            }
        }

        return Bukkit.getOfflinePlayerIfCached(playerName);
    }

    public Duration getArgAsDuration(int index, Duration def) {
        String durationStr = getArg(index, null);
        if(durationStr == null) {
            return def;
        }

        try {
            long secs = SCTime.parseDuration(durationStr);
            return Duration.ofSeconds(secs);
        }  catch(Exception ignored) {
            return null;
        }
    }
}
