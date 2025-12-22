package dev.stemcraft.features;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;

import java.util.ArrayList;
import java.util.List;

public class GameModeAliases implements STEMCraftFeature {

    @Override
    public void onEnable(STEMCraftAPI api) {
        register(api, "gmc", "creative");
        register(api, "gms", "survival");
        register(api, "gma", "adventure");
        register(api, "gmsp", "spectator");
    }

    private void register(STEMCraftAPI api, String alias, String mode) {
        api.registerCommand(alias)
                .setDescription("GAMEMODE_ALIAS_" + alias.toUpperCase())
                .setUsage("GAMEMODE_ALIAS_USAGE")
                .addTabCompletion("{player}")
                .setExecutor((sender, cmd, ctx) -> {
                    List<String> args = new ArrayList<>();
                    args.add(mode);
                    args.addAll(ctx.args());

                    ctx.dispatch("gamemode", args);
                })
                .register(STEMCraft.getInstance());
    }
}