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

package dev.stemcraft.api.service.chatmenu;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.DirectionUtil;
import dev.stemcraft.api.util.FontUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Service for rendering chat menus with pagination.
 */
public class SCChatMenuService {
    private final static int ITEMS_PER_PAGE = 8;

    /**
     * Render a Chat Menu for the player
     *
     * @param sender The command sender (player or console)
     * @param title The title of the menu
     * @param command The base command for pagination links
     * @param page The current page number
     * @param count The total number of items
     * @param rendererFunc The function to render the menu items
     * @param noneText The text to display if there are no items
     */
    public static void render(CommandSender sender, String title, String command, int page, int count, SCChatMenuRenderer rendererFunc, String noneText) {
        if(count <= 0) {
            STEMCraftAPI.api().messages().error(sender, noneText);
            return;
        }

        int start = (page - 1) * ITEMS_PER_PAGE;
        int maxPages = (int)Math.ceil((double) count / ITEMS_PER_PAGE);
        boolean isPlayer = sender instanceof Player;
        int renderCount = Math.min(ITEMS_PER_PAGE, count - start);
        List<Component> lines = rendererFunc.render(start, renderCount, isPlayer);

        if(lines.isEmpty()) {
            STEMCraftAPI.api().messages().error(sender, noneText);
            return;
        }

        sender.sendMessage(createSeparatorString(Component.text(title, NamedTextColor.AQUA)));

        // Display the content for the current page
        for (Component line : lines) {
            sender.sendMessage(line);
        }

        // Pagination
        Component prev = Component.text("<<< ", (page <= 1 ? NamedTextColor.GRAY : NamedTextColor.GOLD));
        if(page > 1) {
            prev = prev.clickEvent(ClickEvent.runCommand("/" + command + " " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Previous page")));
        }

        Component pageInfo = Component.text("Page ", NamedTextColor.YELLOW)
                .append(Component.text(page, NamedTextColor.GOLD)
                        .append(Component.text(" of " + maxPages, NamedTextColor.YELLOW)));

        Component next = Component.text(" >>>", (page >= maxPages ? NamedTextColor.GRAY : NamedTextColor.GOLD));
        if(page < maxPages) {
            next = next.clickEvent(ClickEvent.runCommand("/" + command + " " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Next page")));
        }

        if(isPlayer) {
            sender.sendMessage(createSeparatorString(prev.append(pageInfo).append(next)));
        } else {
            sender.sendMessage(createSeparatorString(pageInfo));
        }
    }

    /**
     * Generates the dash line texts with text centered ie ------ TITLE --------
     *
     * @param title The title component to center
     * @return The formatted separator component
     */
    private static Component createSeparatorString(Component title) {
        // Separator character and max chat width
        String separator = "-";
        int maxWidth = 320; // Pixels (default chat width in Minecraft)


        // Calculate title width in pixels
        int titleWidth = FontUtil.calculatePixelWidth(title);

        // Calculate separator width
        int separatorWidth = FontUtil.calculatePixelWidth(separator);
        int paddingWidth = (maxWidth - titleWidth - 8) / 2; // Account for 4 pixels padding on each side

        // Calculate how many separators fit
        int separatorCount = paddingWidth / separatorWidth;
        String separatorStr = separator.repeat(separatorCount);

        // Build and return the component
        return Component.text(separatorStr + " ", NamedTextColor.YELLOW)
                .append(title)
                .append(Component.text(" " + separatorStr, NamedTextColor.YELLOW));
    }

    /**
     * Get the page number requested from command args
     *
     * @param args The command arguments
     * @param index The index to check for page number
     * @param defaultPage The default page if none found
     * @return The page number
     */
    public static int getPageFromArgs(List<String> args, int index, int defaultPage) {
        if (args != null && !args.isEmpty()) {
            if(index < 0 || index >= args.size()) {
                index = args.size() - 1;
            }

            try {
                int p = Integer.parseInt(args.get(index));
                if(p >= 1) {
                    return p;
                }
            } catch (NumberFormatException e) {
                // empty
            }
        }

        return defaultPage;
    }

    public static int getPageFromArgs(List<String> args) {
        return getPageFromArgs(args, -1, 1);
    }
}