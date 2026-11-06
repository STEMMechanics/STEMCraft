/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.config;

/** Describes how a configuration change entered the active configuration. */
public enum ConfigChangeOrigin {
    /** A change made through {@code ConfigSection.set}, {@code remove}, or a related API. */
    INTERNAL,
    /** A valid change detected in the configuration file on disk. */
    EXTERNAL,
    /** A change applied by an explicit {@code ConfigFile.reload()} call. */
    RELOAD
}
