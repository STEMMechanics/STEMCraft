/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.config;

/** A removable configuration change listener registration. */
public interface ConfigSubscription extends AutoCloseable {
    /**
     * Removes this listener. Calling this method more than once is safe.
     */
    @Override
    void close();

    /**
     * Returns whether this registration has been removed.
     *
     * @return true when this subscription will no longer receive events
     */
    default boolean isClosed() { return false; }

    /** A subscription which intentionally does nothing. */
    static ConfigSubscription noop() {
        return new ConfigSubscription() {
            @Override public void close() { }
            @Override public boolean isClosed() { return true; }
        };
    }
}
