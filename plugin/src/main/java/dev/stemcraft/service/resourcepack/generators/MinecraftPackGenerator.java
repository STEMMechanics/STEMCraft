package dev.stemcraft.service.resourcepack.generators;

import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.generator.AbstractResourcePackGenerator;
import dev.stemcraft.exception.ResourcePackGeneratorException;
import dev.stemcraft.service.resourcepack.ResourcePackServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies data-pack assets into the generated resource pack.
 */
public class MinecraftPackGenerator extends AbstractResourcePackGenerator {
    private final ResourcePackServiceImpl service;

    public MinecraftPackGenerator(@NotNull ResourcePackServiceImpl service) {
        super("minecraft");
        this.service = service;
    }

    @Override
    public void generate(@NotNull ResourcePackBuildContext context) throws IOException {
        for (File dataPackDir : service.dataPackDirectories()) {
            File contentsDir = new File(dataPackDir, "contents");
            File[] namespaceDirs = contentsDir.listFiles(file ->
                file.isDirectory() && !file.getName().startsWith(".")
            );

            if (namespaceDirs == null) {
                continue;
            }

            for (File namespaceDir : namespaceDirs) {
                try {
                    String outputNamespace = "assets/" + namespaceDir.getName();
                    context.writer().copyDirectory(
                        namespaceDir.toPath(),
                        outputNamespace
                    );
                    if (context.target().packFormat() >= 88 && namespaceDir.getName().equals("minecraft")) {
                        migrate26_2Textures(context.writer().resolve(outputNamespace));
                    }
                } catch (IOException e) {
                    throw new ResourcePackGeneratorException(
                        "Failed to copy resource pack namespace: " + namespaceDir.getName(),
                        e
                    );
                }
            }
        }
    }

    private void migrate26_2Textures(@NotNull Path minecraftAssets) throws IOException {
        migrateRenamedTexture(minecraftAssets, "textures/block/quartz_pillar.png", "textures/block/quartz_pillar_side.png");
        migrateRenamedTexture(minecraftAssets, "textures/block/purpur_pillar.png", "textures/block/purpur_pillar_side.png");
    }

    private void migrateRenamedTexture(@NotNull Path minecraftAssets,
                                       @NotNull String legacyPath,
                                       @NotNull String currentPath) throws IOException {
        Path legacy = minecraftAssets.resolve(legacyPath);
        if (!Files.isRegularFile(legacy)) {
            return;
        }

        Path current = minecraftAssets.resolve(currentPath);
        if (Files.notExists(current)) {
            Files.createDirectories(current.getParent());
            Files.move(legacy, current, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.delete(legacy);
        }
    }
}
