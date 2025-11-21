package dev.stemcraft.commands;

import dev.stemcraft.api.commands.STEMCraftCommandContext;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class STEMCraftCommandContextImpl implements STEMCraftCommandContext {
    @Getter
    private String label;
    @Getter
    private CommandSender sender;
    private List<String> args;

    public STEMCraftCommandContextImpl(String label, CommandSender sender, List<String> args) {
        this.label = label;
        this.sender = sender;
        this.args = args;
    }

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
