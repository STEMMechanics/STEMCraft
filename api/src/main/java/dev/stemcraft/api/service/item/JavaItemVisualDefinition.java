package dev.stemcraft.api.service.item;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record JavaItemVisualDefinition(
    int customModelData,
    @NotNull String itemModelId,
    @NotNull String modelId,
    @NotNull String texturePath,
    @Nullable String parentModel,
    boolean frontFacingInHand,
    boolean generateModel,
    @Nullable String guiModelId
) {
    public JavaItemVisualDefinition(int customModelData, @NotNull String itemModelId,
                                    @NotNull String modelId, @NotNull String texturePath) {
        this(customModelData, itemModelId, modelId, texturePath, null, false, true, null);
    }

    public JavaItemVisualDefinition(int customModelData, @NotNull String itemModelId,
                                    @NotNull String modelId, @NotNull String texturePath,
                                    @Nullable String parentModel) {
        this(customModelData, itemModelId, modelId, texturePath, parentModel, false, true, null);
    }

    public JavaItemVisualDefinition(int customModelData, @NotNull String itemModelId,
                                    @NotNull String modelId, @NotNull String texturePath,
                                    @Nullable String parentModel, boolean frontFacingInHand) {
        this(customModelData, itemModelId, modelId, texturePath, parentModel, frontFacingInHand, true, null);
    }

    public JavaItemVisualDefinition(int customModelData, @NotNull String itemModelId,
                                    @NotNull String modelId, @NotNull String texturePath,
                                    @Nullable String parentModel, boolean frontFacingInHand,
                                    boolean generateModel) {
        this(customModelData, itemModelId, modelId, texturePath, parentModel, frontFacingInHand,
            generateModel, null);
    }

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
        if (parentModel != null && parentModel.isBlank()) {
            throw new IllegalArgumentException("parentModel must not be blank");
        }
        if (guiModelId != null && guiModelId.isBlank()) {
            throw new IllegalArgumentException("guiModelId must not be blank");
        }
    }
}
