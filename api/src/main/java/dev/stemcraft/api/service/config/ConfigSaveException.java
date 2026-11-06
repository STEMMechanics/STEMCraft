/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.stemcraft.api.service.config;

/**
 * Thrown by the legacy void save API when a configuration cannot be saved.
 * Callers that need structured conflict handling should use saveChecked().
 */
public class ConfigSaveException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final transient ConfigSaveResult result;

    public ConfigSaveException(ConfigSaveResult result) {
        super(result == null ? "Configuration save failed" : result.message());
        this.result = result;
    }

    public ConfigSaveResult result() { return result; }
}
