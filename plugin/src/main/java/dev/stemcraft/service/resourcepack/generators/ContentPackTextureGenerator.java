/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service.resourcepack.generators;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.contentpack.ContentPackInfo;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.generator.AbstractResourcePackGenerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

/** Copies user-owned content-pack PNGs into the Java resource pack. */
public final class ContentPackTextureGenerator extends AbstractResourcePackGenerator {

    public ContentPackTextureGenerator() {
        super("content-pack-textures");
    }

    @Override
    public void generate(@NotNull ResourcePackBuildContext context) throws IOException {
        for (ContentPackInfo pack : STEMCraftAPI.api().contentPacks().packs()) {
            if (!pack.enabled() || !pack.valid()) continue;
            Path textures = Path.of(pack.path(), "textures");
            if (!Files.isDirectory(textures)) continue;

            Files.walkFileTree(textures, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file,
                                                          @NotNull BasicFileAttributes attributes)
                    throws IOException {
                    if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String relative = textures.relativize(file).toString().replace(File.separatorChar, '/');
                    context.writer().copyFile(file, "assets/" + pack.namespace() + "/textures/" + relative);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
