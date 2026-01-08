/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.command;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.Command;
import dev.stemcraft.api.command.CommandContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public class WorkbenchCommand extends BaseCommand {
    private static final String PERMISSION = "stemcraft.command.workbench";
    private static final Map<String, String> TABLE_TITLES = Map.of(
            "workbench", "Workbench",
            "anvil", "Anvil",
            "cartographytable", "Cartography Table",
            "grindstone", "Grindstone",
            "loom", "Loom",
            "smithingtable", "Smithing Table",
            "stonecutter", "Stonecutter"
    );

    /**
     * Creates a new WorkbenchCommand instance.
     *
     * @param plugin The main STEMCraft plugin instance.
     * @param api The STEMCraft API instance.
     */
    public WorkbenchCommand(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    /**
     * Initializes the command with its label, description, usage, and permission.
     */
    @Override
    public void onLoad() {
        setDescription("WORKBENCH_DESCRIPTION");
        setLabel("workbench");
        addAliases("anvil", "cartographytable", "grindstone", "loom", "smithingtable", "stonecutter");
        setPermission(PERMISSION);
        setUsage("WORKBENCH_USAGE");
        addTabCompletion("{player}");
        register(plugin);
    }

    /**
     * Executes the workbench command, allowing a player to open various workbench interfaces.
     *
     * @param cmd The command being executed.
     * @param ctx The context of the command execution.
     */
    @Override
    public void onExecute(Command cmd, CommandContext ctx) {
        String table = ctx.getLabelUsed();
        String title = TABLE_TITLES.get(table);

        // check if console called without args
        if(ctx.isConsole() && ctx.args().isEmpty()) {
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
            case "workbench" -> workbench = Bukkit.createInventory(null, InventoryType.WORKBENCH);
            case "anvil" -> workbench = Bukkit.createInventory(null, InventoryType.ANVIL);
            case "cartographytable" -> workbench = Bukkit.createInventory(null, InventoryType.CARTOGRAPHY);
            case "grindstone" -> workbench = Bukkit.createInventory(null, InventoryType.GRINDSTONE);
            case "loom" -> workbench = Bukkit.createInventory(null, InventoryType.LOOM);
            case "smithingtable" -> workbench = Bukkit.createInventory(null, InventoryType.SMITHING);
            case "stonecutter" -> workbench = Bukkit.createInventory(null, InventoryType.STONECUTTER);
        }

        if(workbench == null) {
            error(ctx.getSender(), "WORKBENCH_OPEN_FAILED", "table", title);
            return;
        }

        target.openInventory(workbench);

        if (!target.equals(ctx.getSender())) {
            String senderName = ctx.isConsole() ? api.locales().resolve("CONSOLE_NAME") : ctx.getSender().getName();
            success(ctx.getSender(), "WORKBENCH_OPEN_OTHER_SUCCESS_SENDER", "player", target.getName(), "table", title);
            success(target, "WORKBENCH_OPEN_OTHER_SUCCESS_PLAYER", "player", senderName, "table", title);
        }
    }
}