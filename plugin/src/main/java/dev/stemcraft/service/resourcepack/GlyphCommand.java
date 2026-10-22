/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.service.resourcepack;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import dev.stemcraft.api.util.chatmenu.ChatMenuUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Displays the glyph shortcuts registered by the resource-pack manifest. */
public final class GlyphCommand {
    static final int GLYPHS_PER_LINE = 12;

    private final STEMCraftAPI api;
    private final ResourcePackService service;

    public GlyphCommand(@NotNull STEMCraftAPI api, @NotNull ResourcePackService service) {
        this.api = api;
        this.service = service;
    }

    /** Registers the player-readable glyph command. */
    public void onEnable() {
        api.commands().create("glyph")
                .usage("GLYPH_USAGE")
                .description("GLYPH_DESCRIPTION")
                .executor((ignoredApi, ignoredCommand, ctx) -> show(ctx))
                .register(STEMCraft.getPlugin());
    }

    private void show(@NotNull CommandContext ctx) {
        List<String> args = ctx.args();
        boolean hasPage = hasPageArgument(args);
        int searchEnd = hasPage ? args.size() - 1 : args.size();
        String search = String.join(" ", args.subList(0, searchEnd)).trim();

        List<String> matches = filterTokens(service.registeredGlyphTokens(), search);
        List<List<String>> lines = groupTokens(matches, GLYPHS_PER_LINE);
        String command = search.isBlank() ? "glyph" : "glyph " + search;

        ChatMenuUtil.render(ctx.getSender(), Component.text("Registered glyphs", NamedTextColor.AQUA),
                command, ChatMenuUtil.getPageFromArgs(args), lines.size(), (start, count, ignoredPlayer) -> {
                    List<Component> rendered = new ArrayList<>();
                    for (int index = start; index < start + count && index < lines.size(); index++) {
                        rendered.add(renderLine(lines.get(index)));
                    }
                    return rendered;
                }, "GLYPH_NO_MATCHES");
    }

    private Component renderLine(@NotNull List<String> tokens) {
        Component line = Component.empty();
        for (int index = 0; index < tokens.size(); index++) {
            if (index > 0) line = line.append(Component.space());

            String shortcut = tokens.get(index);
            String glyph = api.messages().tokens().apply(shortcut);
            if (glyph == null || glyph.isEmpty()) glyph = shortcut;
            line = line.append(Component.text(glyph)
                    .hoverEvent(HoverEvent.showText(Component.text(shortcut))));
        }
        return line;
    }

    static @NotNull List<String> filterTokens(@NotNull List<String> tokens, @NotNull String search) {
        String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
        return tokens.stream()
                .filter(token -> normalizedSearch.isEmpty()
                        || token.toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .toList();
    }

    static @NotNull List<List<String>> groupTokens(@NotNull List<String> tokens, int perLine) {
        if (perLine <= 0) throw new IllegalArgumentException("perLine must be positive");
        List<List<String>> lines = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += perLine) {
            lines.add(List.copyOf(tokens.subList(start, Math.min(start + perLine, tokens.size()))));
        }
        return List.copyOf(lines);
    }

    private static boolean hasPageArgument(@NotNull List<String> args) {
        if (args.isEmpty()) return false;
        try {
            return Integer.parseInt(args.getLast()) >= 1;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
