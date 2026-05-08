package dev.stemcraft.service.resourcepack.generators;

import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.generator.AbstractResourcePackGenerator;
import dev.stemcraft.exception.ResourcePackGeneratorException;
import dev.stemcraft.service.resourcepack.ResourcePackServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

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
                    context.writer().copyDirectory(
                        namespaceDir.toPath(),
                        "assets/" + namespaceDir.getName()
                    );
                } catch (IOException e) {
                    throw new ResourcePackGeneratorException(
                        "Failed to copy resource pack namespace: " + namespaceDir.getName(),
                        e
                    );
                }
            }
        }
    }
}
