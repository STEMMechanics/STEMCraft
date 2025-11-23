package dev.stemcraft.commands;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.commands.STEMCraftCommand;
import dev.stemcraft.api.commands.STEMCraftCommandContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public class WorkbenchCommand extends STEMCraftCommandImpl {
    private static final Map<String, String> TABLE_TITLES = Map.of(
            "workbench", "Workbench",
            "anvil", "Anvil",
            "cartographytable", "Cartography Table",
            "grindstone", "Grindstone",
            "loom", "Loom",
            "smithingtable", "Smithing Table",
            "stonecutter", "Stonecutter"
    );

    @Override
    public void onLoad(STEMCraft plugin) {
        setLabel("workbench");
        setAlias("anvil", "cartographytable", "grindstone", "loom", "smithingtable", "stonecutter");
        register(plugin);
    }

    @Override
    public void onExecute(STEMCraftAPI api, STEMCraftCommand cmd, STEMCraftCommandContext ctx) {
        String table = ctx.getLabelUsed();
        String title = TABLE_TITLES.get(table);

        // check if console called without args
        if(ctx.fromConsole() && ctx.args().isEmpty()) {
            error("CONSOLE_PLAYER_REQUIRED");
            return;
        }

        if(!ctx.hasPermission("stemcraft.command." + table)) {
            error(ctx.getSender(), "COMMAND_NO_PERMISSION");
            return;
        }

        // check permission for others (if args given)
        if(!ctx.args().isEmpty() && !ctx.hasPermission("stemcraft.command." + table + ".others")) {
            error(ctx.getSender(), "WORKBENCH_OTHER_DENY", "table", table);
            return;
        }

        // get target player
        Player target = ctx.getArgAsPlayer(1, ctx.getSender());
        if(target == null) {
            error(ctx.getSender(), "PLAYER_NOT_FOUND", "player", ctx.getArg(1));
            return;
        }

        Inventory workbench = null;
        switch(table) {
            case "workbench" -> {
                workbench = Bukkit.createInventory(null, InventoryType.WORKBENCH);
            }
            case "anvil" -> {
                workbench = Bukkit.createInventory(null, InventoryType.ANVIL);
            }
            case "cartographytable" -> {
                workbench = Bukkit.createInventory(null, InventoryType.CARTOGRAPHY);
            }
            case "grindstone" -> {
                workbench = Bukkit.createInventory(null, InventoryType.GRINDSTONE);
            }
            case "loom" -> {
                workbench = Bukkit.createInventory(null, InventoryType.LOOM);
            }
            case "smithingtable" -> {
                workbench = Bukkit.createInventory(null, InventoryType.SMITHING);
            }
            case "stonecutter" -> {
                workbench = Bukkit.createInventory(null, InventoryType.STONECUTTER);
            }
        }

        if(workbench == null) {
            error(ctx.getSender(), "WORKBENCH_OPEN_FAILED", "table", title);
            return;
        }

        target.openInventory(workbench);

        if (!target.equals(ctx.getSender())) {
            String senderName = ctx.fromConsole() ? api.locale().get("CONSOLE_NAME") : ctx.getSender().getName();
            success(ctx.getSender(), "WORKBENCH_OPEN_OTHER_SUCCESS_SENDER", "player", target.getName(), "table", title);
            success(target, "WORKBENCH_OPEN_OTHER_SUCCESS_PLAYER", "player", senderName, "table", title);
        }
    }
}
