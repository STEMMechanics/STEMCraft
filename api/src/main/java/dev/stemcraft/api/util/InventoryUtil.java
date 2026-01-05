package dev.stemcraft.api.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InventoryUtil {

    public static String toString(Inventory inv) {
        ItemStack[] contents = inv.getContents();
        StringBuilder out = new StringBuilder();

        for (ItemStack item : contents) {
            if (item == null) continue;
            out.append(item.getType().name())
                    .append(" x")
                    .append(item.getAmount())
                    .append(", ");
        }

        if (out.isEmpty()) return "(empty)";
        return out.substring(0, out.length() - 2);
    }


}