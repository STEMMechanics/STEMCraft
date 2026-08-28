/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.integration.pl3xmap;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Immutable grave data exposed to the optional map adapter. */
public record GraveMapMarker(@NotNull UUID id, @NotNull UUID owner, @NotNull String ownerName,
                             @NotNull String world, int x, int y, int z) { }
