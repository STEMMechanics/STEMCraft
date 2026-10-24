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
import dev.stemcraft.api.service.debug.DebugRegistration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DebugServiceImplTest {
    @Test
    void registrationIsCaseInsensitiveAndOwnedByAClosableHandle() {
        DebugServiceImpl service = new DebugServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));

        DebugRegistration registration = service.register(" Example-State ", "Inspect example state.", context -> { });

        assertEquals(List.of("example-state"), service.tests().stream().map(test -> test.id()).toList());
        assertEquals("Inspect example state.", service.find("EXAMPLE-STATE").orElseThrow().description());

        registration.close();

        assertTrue(service.find("example-state").isEmpty());
    }

    @Test
    void replacingATestDoesNotLetTheOldHandleRemoveTheReplacement() {
        DebugServiceImpl service = new DebugServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));

        DebugRegistration oldRegistration = service.register("state", "Old", context -> { });
        DebugRegistration newRegistration = service.register("state", "New", context -> { });

        oldRegistration.close();
        assertEquals("New", service.find("state").orElseThrow().description());

        newRegistration.close();
        assertTrue(service.find("state").isEmpty());
    }

    @Test
    void invalidIdsAreRejected() {
        DebugServiceImpl service = new DebugServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.register("not a test", "Description", context -> { }));
    }
}
