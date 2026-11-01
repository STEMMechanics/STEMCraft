/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.condition.ConditionContext;
import dev.stemcraft.api.service.condition.ConditionProvider;
import dev.stemcraft.api.service.condition.ConditionService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Shared condition registry used by content-pack consumers. */
public final class ConditionServiceImpl extends BaseService implements ConditionService {
    private final Map<String, ConditionProvider> providers = new ConcurrentHashMap<>();

    public ConditionServiceImpl(STEMCraft plugin, STEMCraftAPI api) {
        super(plugin, api, "conditions");
    }

    @Override
    public void onEnable() {
        register(new SimpleProvider("world", context -> {
            if (context.location() != null && context.location().getWorld() != null) {
                return context.location().getWorld().getName();
            }
            return context.values().getOrDefault("world", "");
        }));
        register(new SimpleProvider("entity-type", ConditionContext::entityType));
        register(new SimpleProvider("biome", ConditionContext::biome));
        register(new SimpleProvider("spawn-reason", ConditionContext::spawnReason));
        register(new SimpleProvider("player-kill", context -> context.values().getOrDefault("player-kill", false)));
        register(new SimpleProvider("block", context -> context.values().getOrDefault("block", "")));
        register(new SimpleProvider("block-type", context -> context.values().getOrDefault("block", "")));
    }

    @Override
    public void onDisable() {
        providers.clear();
    }

    @Override
    public void register(@NotNull ConditionProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = normalize(provider.id());
        ConditionProvider previous = providers.putIfAbsent(id, provider);
        if (previous != null && previous != provider) {
            throw new IllegalArgumentException("Condition provider already registered: " + id);
        }
    }

    @Override
    public void unregister(@NotNull String id) {
        providers.remove(normalize(id));
    }

    @Override
    public @NotNull List<String> validate(@NotNull Map<String, ?> conditions, @NotNull String path) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, ?> entry : conditions.entrySet()) {
            String id = normalize(entry.getKey());
            if (id.equals("all") || id.equals("any")) {
                if (!(entry.getValue() instanceof Collection<?> values)) {
                    errors.add(path + "." + entry.getKey() + " must be a list of condition maps.");
                    continue;
                }
                for (int index = 0; index < values.size(); index++) {
                    Object value = values.toArray()[index];
                    if (!(value instanceof Map<?, ?> raw)) {
                        errors.add(path + "." + entry.getKey() + "[" + index + "] must be a map.");
                        continue;
                    }
                    errors.addAll(validate(stringMap(raw), path + "." + entry.getKey() + "[" + index + "]"));
                }
                continue;
            }
            if (id.equals("not")) {
                if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                    errors.add(path + ".not must be a condition map.");
                } else {
                    errors.addAll(validate(stringMap(raw), path + ".not"));
                }
                continue;
            }
            ConditionProvider provider = providers.get(id);
            if (provider == null) {
                errors.add(path + "." + entry.getKey() + " requires missing condition provider '" + id + "'.");
                continue;
            }
            errors.addAll(provider.validate(entry.getValue(), path + "." + entry.getKey()));
        }
        return List.copyOf(errors);
    }

    @Override
    public boolean matches(@NotNull Map<String, ?> conditions, @NotNull ConditionContext context) {
        for (Map.Entry<String, ?> entry : conditions.entrySet()) {
            String id = normalize(entry.getKey());
            Object value = entry.getValue();
            boolean result;
            if (id.equals("all")) {
                result = value instanceof Collection<?> values && values.stream()
                    .allMatch(child -> child instanceof Map<?, ?> raw && matches(stringMap(raw), context));
            } else if (id.equals("any")) {
                result = value instanceof Collection<?> values && values.stream()
                    .anyMatch(child -> child instanceof Map<?, ?> raw && matches(stringMap(raw), context));
            } else if (id.equals("not")) {
                result = value instanceof Map<?, ?> raw && !matches(stringMap(raw), context);
            } else {
                ConditionProvider provider = providers.get(id);
                result = provider != null && provider.matches(value, context);
            }
            if (!result) return false;
        }
        return true;
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Condition provider ID must not be blank");
        return id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private record SimpleProvider(String id, java.util.function.Function<ConditionContext, Object> actual)
            implements ConditionProvider {
        @Override
        public @NotNull List<String> validate(@NotNull Object value, @NotNull String path) {
            if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                    || value instanceof Collection<?> || value instanceof Map<?, ?>) return List.of();
            return List.of(path + " must be a scalar, list, or range map.");
        }

        @Override
        public boolean matches(@NotNull Object value, @NotNull ConditionContext context) {
            return matchesValue(actual.apply(context), value);
        }
    }

    public static boolean matchesValue(Object actual, Object expected) {
        if (expected instanceof Map<?, ?> raw) {
            Object min = raw.get("min"), max = raw.get("max");
            if (!(actual instanceof Number number)) return false;
            double actualValue = number.doubleValue();
            if (min instanceof Number minimum && actualValue < minimum.doubleValue()) return false;
            if (max instanceof Number maximum && actualValue > maximum.doubleValue()) return false;
            return min instanceof Number || max instanceof Number;
        }
        if (expected instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> matchesValue(actual, value));
        }
        if (actual instanceof Boolean actualBoolean) {
            return expected instanceof Boolean expectedBoolean
                ? actualBoolean.equals(expectedBoolean)
                : actualBoolean.toString().equalsIgnoreCase(String.valueOf(expected));
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue()) == 0;
        }
        return String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
    }
}
