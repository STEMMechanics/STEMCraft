package dev.stemcraft.service.placeholderapi;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.minigame.MiniGame;
import dev.stemcraft.api.minigame.MiniGameArena;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

final class PlaceholderApiService extends PlaceholderExpansion {
    private final STEMCraftAPI api;

    PlaceholderApiService(STEMCraftAPI api) {
        this.api = api;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "stemcraft";
    }

    @Override
    public @NotNull String getAuthor() {
        return "STEMMechanics";
    }

    @Override
    public @NotNull String getVersion() {
        return api.getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public int hashCode() {
        return getIdentifier().hashCode();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.isBlank()) {
            return null;
        }

        String normalized = params.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("glyph:")) {
            return glyph(params.substring("glyph:".length()));
        }
        if (normalized.equals("badge")) {
            return player == null ? "" : STEMCraft.getPlugin().entitlements().renderBadges(player.getUniqueId(), 0);
        }
        if (normalized.startsWith("badge:")) {
            if (player == null) return "";
            try {
                return STEMCraft.getPlugin().entitlements().renderBadges(player.getUniqueId(),
                    Integer.parseInt(normalized.substring("badge:".length())));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (normalized.startsWith("arena_status:")) {
            return arenaStatus(params.substring("arena_status:".length()));
        }
        if (normalized.startsWith("arena_open_closed:")) {
            return arenaOpenClosed(params.substring("arena_open_closed:".length()));
        }
        if (normalized.startsWith("arena_open:")) {
            return arenaOpen(params.substring("arena_open:".length()));
        }
        if (normalized.startsWith("if_arena_open:")) {
            return ifArenaOpen(params.substring("if_arena_open:".length()));
        }

        return null;
    }

    private @Nullable String glyph(@NotNull String tokenKey) {
        String trimmed = tokenKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String marker = ":" + trimmed + ":";
        String resolved = api.messages().tokens().apply(marker);
        return marker.equals(resolved) ? null : resolved;
    }

    private @NotNull String arenaStatus(@NotNull String args) {
        MiniGameArena arena = resolveArena(args);
        return arena == null ? "unknown" : arena.getStatus().name().toLowerCase(Locale.ROOT);
    }

    private @NotNull String arenaOpen(@NotNull String args) {
        MiniGameArena arena = resolveArena(args);
        return arena == null ? "false" : String.valueOf(arena.isJoinable());
    }

    private @NotNull String arenaOpenClosed(@NotNull String args) {
        MiniGameArena arena = resolveArena(args);
        return arena != null && arena.isJoinable() ? "open" : "closed";
    }

    private @Nullable String ifArenaOpen(@NotNull String args) {
        String[] branches = args.split("\\|", 3);
        if (branches.length < 3) {
            return null;
        }

        MiniGameArena arena = resolveArena(branches[0]);
        String selected = arena != null && arena.isJoinable() ? branches[1] : branches[2];
        return api.messages().tokens().apply(selected);
    }

    private @Nullable MiniGameArena resolveArena(@NotNull String args) {
        String[] parts = args.split(":", 2);
        if (parts.length < 2) {
            return null;
        }

        String namespace = parts[0].trim();
        String arenaId = parts[1].trim();
        if (namespace.isEmpty() || arenaId.isEmpty()) {
            return null;
        }

        MiniGame miniGame = api.minigames().get(namespace);
        if (miniGame == null) {
            miniGame = api.minigames().get(namespace.toLowerCase(Locale.ROOT));
        }
        if (miniGame == null) {
            return null;
        }

        MiniGameArena arena = miniGame.arena(arenaId);
        if (arena != null) {
            return arena;
        }

        for (MiniGameArena candidate : miniGame.arenas()) {
            if (candidate.id().equalsIgnoreCase(arenaId)) {
                return candidate;
            }
        }

        return null;
    }
}
