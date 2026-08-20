/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 */
package dev.stemcraft.api.service.save;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/** Coordinates explicit save checkpoints for STEMCraft and its extensions. */
public interface SaveService {
    /**
     * Register or replace an extension save participant.
     *
     * @param owner plugin which owns the pending state
     * @param id stable participant id, unique within the owning plugin
     * @param participant synchronous save callback
     */
    void register(@NotNull Plugin owner, @NotNull String id, @NotNull SaveParticipant participant);

    /** Remove a previously registered extension participant. */
    void unregister(@NotNull Plugin owner, @NotNull String id);

    /** Save all STEMCraft features, services, configs, and enabled extension participants. */
    @NotNull SaveReport saveAll();

    /** Whether a save checkpoint is currently running. */
    boolean isSaving();
}
