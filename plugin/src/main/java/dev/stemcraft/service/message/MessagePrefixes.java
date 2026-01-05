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

public class MessagePrefixes {
    public final String log;
    public final String info;
    public final String warn;
    public final String error;
    public final String success;
    public final String broadcast;

    public MessagePrefixes(String log, String info, String warn, String error, String success, String broadcast) {
        this.log = log;
        this.info = info;
        this.warn = warn;
        this.error = error;
        this.success = success;
        this.broadcast = broadcast;
    }

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
