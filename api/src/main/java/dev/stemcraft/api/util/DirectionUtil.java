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

public final class DirectionUtil {

    /**
     * Converts a YAW value to a compass direction.
     *
     * @param yaw The yaw value to convert.
     * @return The compass direction.
     */
    public static String getCompassDirection(float yaw) {
        double rotation = (yaw - 90) % 360;
        if (rotation < 0) {
            rotation += 360.0;
        }

        if (0 <= rotation && rotation < 22.5 || 337.5 <= rotation && rotation < 360) {
            return "W"; // West
        } else if (22.5 <= rotation && rotation < 67.5) {
            return "NW"; // Northwest
        } else if (67.5 <= rotation && rotation < 112.5) {
            return "N"; // North
        } else if (112.5 <= rotation && rotation < 157.5) {
            return "NE"; // Northeast
        } else if (157.5 <= rotation && rotation < 202.5) {
            return "E"; // East
        } else if (202.5 <= rotation && rotation < 247.5) {
            return "SE"; // Southeast
        } else if (247.5 <= rotation && rotation < 292.5) {
            return "S"; // South
        } else if (292.5 <= rotation && rotation < 337.5) {
            return "SW"; // Southwest
        }
        return ""; // This should never happen
    }
}