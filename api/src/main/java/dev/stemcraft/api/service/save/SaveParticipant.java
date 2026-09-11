/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.save;

/** A unit of extension-owned state that can be persisted on demand. */
@FunctionalInterface
public interface SaveParticipant {
    /** Persist all pending state, returning only after the save is complete. */
    void save() throws Exception;
}
