package dev.stemcraft.api.service.item;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record JavaItemVisualDefinition(
    int customModelData,
    @NotNull String itemModelId,
    @NotNull String modelId,
    @NotNull String texturePath
) {
    public JavaItemVisualDefinition {
        if (customModelData <= 0) {
            throw new IllegalArgumentException("customModelData must be positive");
        }
        Objects.requireNonNull(itemModelId, "itemModelId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(texturePath, "texturePath");
        if (itemModelId.isBlank()) {
            throw new IllegalArgumentException("itemModelId must not be blank");
        }
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (texturePath.isBlank()) {
            throw new IllegalArgumentException("texturePath must not be blank");
        }
    }
}
