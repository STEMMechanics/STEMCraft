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

package dev.stemcraft.service.message;

import dev.stemcraft.api.config.ConfigSection;

/**
 * Class representing message prefixes for different message types.
 */
public record MessagePrefixes(String log, String info, String warn, String error, String success, String broadcast) {
    /**
     * Constructor for MessagePrefixes.
     *
     * @param log       The log message prefix.
     * @param info      The info message prefix.
     * @param warn      The warn message prefix.
     * @param error     The error message prefix.
     * @param success   The success message prefix.
     * @param broadcast The broadcast message prefix.
     */
    public MessagePrefixes { }

    /**
     * Create MessagePrefixes from a configuration section.
     * If the config is null, default prefixes will be used.
     *
     * @param config The configuration section.
     * @return A MessagePrefixes instance.
     */
    public static MessagePrefixes from(ConfigSection config) {
        if (config == null) {
            return defaults();
        }
        return new MessagePrefixes(
                config.getString("log", "&7[STEM]&r "),
                config.getString("info", "&9[INFO]&r "),
                config.getString("warn", "&e[WARN]&r "),
                config.getString("error", "&c[ERROR]&r "),
                config.getString("success", "&a[SUCCESS]&r "),
                config.getString("broadcast", "&e[SERVER] ")
        );
    }

    /**
     * Get the default message prefixes.
     *
     * @return The default MessagePrefixes instance.
     */
    private static MessagePrefixes defaults() {
        return new MessagePrefixes(
                "&7[LOG]&r ",
                "&9[INFO]&r ",
                "&e[WARN]&r ",
                "&c[ERROR]&r ",
                "&a[SUCCESS]&r ",
                "&e[SERVER] "
        );
    }
}
