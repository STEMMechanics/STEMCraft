package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
            if (java.generateModel()) writeGeneratedModel(context, java);
            writeItemDefinition(context, java);
            for (CustomItemClientDefinition state : definition.visualStates().values()) {
                if (state.java() == null) continue;
                if (state.java().generateModel()) writeGeneratedModel(context, state.java());
                writeItemDefinition(context, state.java());
            }
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
        if (java.parentModel() != null) {
            root.addProperty("parent", java.parentModel());
        } else {
            root.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", java.texturePath());
            root.add("textures", textures);
        }
        if (java.frontFacingInHand()) {
            root.add("display", frontFacingHandDisplay());
        }

        context.writer().writeString(
            "assets/" + namespace + "/models/" + path + ".json",
            new GsonBuilder().setPrettyPrinting().create().toJson(root)
        );
    }

    private @NotNull JsonObject frontFacingHandDisplay() {
        JsonObject display = new JsonObject();
        display.add("firstperson_righthand", transform(4.0d, -2.0d, -3.0d, 0.35d));
        display.add("firstperson_lefthand", transform(-4.0d, -2.0d, -3.0d, 0.35d));
        display.add("thirdperson_righthand", transform(2.0d, 1.0d, -1.0d, 0.35d));
        display.add("thirdperson_lefthand", transform(-2.0d, 1.0d, -1.0d, 0.35d));
        return display;
    }

    private @NotNull JsonObject transform(double x, double y, double z, double scale) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        JsonObject transform = new JsonObject();
        transform.add("rotation", gson.toJsonTree(new int[] {0, 0, 0}));
        transform.add("translation", gson.toJsonTree(new double[] {x, y, z}));
        transform.add("scale", gson.toJsonTree(new double[] {scale, scale, scale}));
        return transform;
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
        JsonObject heldModel = modelReference(java.modelId());
        if (java.guiModelId() == null) {
            root.add("model", heldModel);
        } else {
            JsonObject selector = new JsonObject();
            selector.addProperty("type", "minecraft:select");
            selector.addProperty("property", "minecraft:display_context");
            JsonArray cases = new JsonArray();
            JsonObject guiCase = new JsonObject();
            guiCase.addProperty("when", "gui");
            guiCase.add("model", modelReference(java.guiModelId()));
            cases.add(guiCase);
            selector.add("cases", cases);
            selector.add("fallback", heldModel);
            root.add("model", selector);
        }
        context.writer().writeString(
            "assets/" + namespace + "/items/" + path + ".json",
            new GsonBuilder().setPrettyPrinting().create().toJson(root)
        );
    }

    private @NotNull JsonObject modelReference(@NotNull String modelId) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modelId);
        return model;
    }
}
