/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.save;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable result from a complete STEMCraft save checkpoint. */
public record SaveReport(int attempted, int succeeded, Map<String, String> failures) {
    public SaveReport {
        failures = Map.copyOf(new LinkedHashMap<>(failures));
    }

    public boolean successful() { return failures.isEmpty(); }
}
