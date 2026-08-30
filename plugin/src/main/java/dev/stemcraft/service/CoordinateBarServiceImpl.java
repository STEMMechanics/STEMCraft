/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.coordinatebar.CoordinateBarProvider;
import dev.stemcraft.api.service.coordinatebar.CoordinateBarService;
import dev.stemcraft.api.service.coordinatebar.CoordinateBarSection;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Default coordinate-bar provider registry. */
public final class CoordinateBarServiceImpl extends BaseService implements CoordinateBarService {
    private final Map<Key, Registration> providers = new LinkedHashMap<>();
    private final Map<AmendmentKey, Registration> amendments = new LinkedHashMap<>();

    public CoordinateBarServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api);
    }

    @Override
    public synchronized void register(@NotNull Plugin owner, @NotNull String id, int priority,
                                      @NotNull CoordinateBarProvider provider) {
        Objects.requireNonNull(owner, "owner");
        String normalized = normalizeId(id);
        providers.put(new Key(owner, normalized),
            new Registration(owner, normalized, priority, Objects.requireNonNull(provider, "provider")));
    }

    @Override
    public synchronized void unregister(@NotNull Plugin owner, @NotNull String id) {
        providers.remove(new Key(Objects.requireNonNull(owner, "owner"), normalizeId(id)));
    }

    @Override
    public synchronized void registerAmendment(@NotNull Plugin owner, @NotNull String id,
                                               @NotNull CoordinateBarSection section, int priority,
                                               @NotNull CoordinateBarProvider provider) {
        Objects.requireNonNull(owner, "owner"); Objects.requireNonNull(section, "section");
        String normalized = normalizeId(id);
        amendments.put(new AmendmentKey(owner, normalized, section),
            new Registration(owner, normalized, priority, Objects.requireNonNull(provider, "provider")));
    }

    @Override
    public synchronized void unregisterAmendment(@NotNull Plugin owner, @NotNull String id,
                                                 @NotNull CoordinateBarSection section) {
        amendments.remove(new AmendmentKey(Objects.requireNonNull(owner, "owner"), normalizeId(id),
            Objects.requireNonNull(section, "section")));
    }

    @Override
    public @NotNull List<Component> renderAmendments(@NotNull CoordinateBarSection section,
                                                      @NotNull Player player) {
        Objects.requireNonNull(section, "section"); Objects.requireNonNull(player, "player");
        List<Registration> snapshot;
        synchronized (this) {
            amendments.entrySet().removeIf(entry -> !entry.getValue().owner().isEnabled());
            snapshot = amendments.entrySet().stream().filter(entry -> entry.getKey().section() == section)
                .map(Map.Entry::getValue).toList();
        }
        return render(snapshot, player);
    }

    @Override
    public @NotNull List<Component> render(@NotNull Player player) {
        Objects.requireNonNull(player, "player");
        List<Registration> snapshot;
        synchronized (this) {
            providers.entrySet().removeIf(entry -> !entry.getValue().owner().isEnabled());
            snapshot = new ArrayList<>(providers.values());
        }
        return render(snapshot, player);
    }

    private List<Component> render(List<Registration> registrations, Player player) {
        List<Registration> snapshot = new ArrayList<>(registrations);
        snapshot.sort(Comparator.comparingInt(Registration::priority)
            .thenComparing(registration -> registration.owner().getName())
            .thenComparing(Registration::id));

        List<Component> rendered = new ArrayList<>();
        for (Registration registration : snapshot) {
            try {
                Component component = registration.provider().render(player);
                if (component != null && !component.equals(Component.empty())) rendered.add(component);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Coordinate bar provider " + registration.owner().getName() + ":"
                    + registration.id() + " failed: " + exception.getMessage());
            }
        }
        return List.copyOf(rendered);
    }

    private String normalizeId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("Coordinate bar provider id must contain only letters, numbers, '.', '_' or '-'");
        }
        return normalized;
    }

    private record Key(Plugin owner, String id) { }
    private record AmendmentKey(Plugin owner, String id, CoordinateBarSection section) { }
    private record Registration(Plugin owner, String id, int priority, CoordinateBarProvider provider) { }
}
