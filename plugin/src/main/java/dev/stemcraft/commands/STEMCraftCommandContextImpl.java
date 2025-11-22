package dev.stemcraft.commands;

import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class STEMCraftCommandContextImpl implements STEMCraftCommandContext {
    @Getter
    private STEMCraftCommand command;
    @Getter
    private CommandSender sender;
    @Getter String labelUsed;
    private List<String> args;

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
}
