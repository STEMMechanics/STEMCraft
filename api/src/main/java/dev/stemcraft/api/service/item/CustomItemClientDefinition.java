package dev.stemcraft.api.service.item;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record CustomItemClientDefinition(
    @Nullable JavaItemVisualDefinition java,
    @Nullable BedrockItemVisualDefinition bedrock
) {
    public CustomItemClientDefinition {
        if (java == null && bedrock == null) {
            throw new IllegalArgumentException("At least one client definition must be supplied");
        }
    }

    public @NotNull JavaItemVisualDefinition requireJava() {
        return Objects.requireNonNull(java, "Java visual definition is not configured");
    }

    public @NotNull BedrockItemVisualDefinition requireBedrock() {
        return Objects.requireNonNull(bedrock, "Bedrock visual definition is not configured");
    }
}
