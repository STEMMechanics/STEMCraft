package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.stemcraft.api.service.item.CustomItemClientDefinition;
import dev.stemcraft.api.service.item.CustomItemDefinition;
import dev.stemcraft.api.service.item.JavaItemVisualDefinition;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.generator.AbstractResourcePackGenerator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class CustomItemGenerator extends AbstractResourcePackGenerator {

    public CustomItemGenerator() {
        super("custom-items");
    }

    @Override
    public void generate(@NotNull ResourcePackBuildContext context) throws IOException {
        for (CustomItemDefinition definition : dev.stemcraft.api.STEMCraftAPI.api().items().customItemDefinitions()) {
            CustomItemClientDefinition clients = definition.clients();
            if (clients == null || clients.java() == null) {
                continue;
            }
            JavaItemVisualDefinition java = clients.java();
            writeGeneratedModel(context, java);
            writeItemDefinition(context, java);
        }
    }

    private void writeGeneratedModel(@NotNull ResourcePackBuildContext context,
                                     @NotNull JavaItemVisualDefinition java) throws IOException {
        String modelId = java.modelId();
        int separator = modelId.indexOf(':');
        if (separator <= 0 || separator == modelId.length() - 1) {
            throw new IllegalArgumentException("Invalid Java item model id: " + modelId);
        }

        String namespace = modelId.substring(0, separator);
        String path = modelId.substring(separator + 1);
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", java.texturePath());
        root.add("textures", textures);

        context.writer().writeString(
            "assets/" + namespace + "/models/" + path + ".json",
            new GsonBuilder().setPrettyPrinting().create().toJson(root)
        );
    }

    private void writeItemDefinition(@NotNull ResourcePackBuildContext context,
                                     @NotNull JavaItemVisualDefinition java) throws IOException {
        String itemModelId = java.itemModelId();
        int separator = itemModelId.indexOf(':');
        if (separator <= 0 || separator == itemModelId.length() - 1) {
            throw new IllegalArgumentException("Invalid Java item definition id: " + itemModelId);
        }

        String namespace = itemModelId.substring(0, separator);
        String path = itemModelId.substring(separator + 1);
        JsonObject root = new JsonObject();
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", java.modelId());
        root.add("model", model);
        context.writer().writeString(
            "assets/" + namespace + "/items/" + path + ".json",
            new GsonBuilder().setPrettyPrinting().create().toJson(root)
        );
    }
}
