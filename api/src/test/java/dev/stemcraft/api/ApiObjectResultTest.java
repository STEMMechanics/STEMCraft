package dev.stemcraft.api;

import dev.stemcraft.api.command.CommandException;
import dev.stemcraft.api.event.BaseEvent;
import dev.stemcraft.api.event.server.MaintenanceModeChangedEvent;
import dev.stemcraft.api.event.world.WorldDeleteEvent;
import dev.stemcraft.api.internal.InstanceHolder;
import dev.stemcraft.api.minigame.ArenaValidationResult;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.punishment.PunishmentRecord;
import dev.stemcraft.api.service.web.WebService;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiObjectResultTest {
    @AfterEach
    void tearDown() {
        InstanceHolder.set(null, null);
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void eventsExposeSharedHandlerListsAndPayloads() {
        BaseEvent baseEvent = new BaseEvent();
        MaintenanceModeChangedEvent maintenanceEvent = new MaintenanceModeChangedEvent(true);
        WorldDeleteEvent worldDeleteEvent = new WorldDeleteEvent("archive");

        assertSame(BaseEvent.getHandlerList(), baseEvent.getHandlers());
        assertSame(BaseEvent.getHandlerList(), maintenanceEvent.getHandlers());
        assertSame(BaseEvent.getHandlerList(), worldDeleteEvent.getHandlers());
        assertTrue(maintenanceEvent.isInMaintenanceMode());
        assertEquals("archive", worldDeleteEvent.getWorldName());
    }

    @Test
    void instanceHolderAndStaticApiAccessorReturnHeldValues() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        Plugin plugin = mock(Plugin.class);

        InstanceHolder.set(api, plugin);

        assertSame(api, InstanceHolder.api());
        assertSame(api, STEMCraftAPI.api());
    }

    @Test
    void arenaValidationResultTracksErrors() {
        ArenaValidationResult success = ArenaValidationResult.success();
        assertFalse(success.hasErrors());
        assertEquals(0, success.getErrors().size());

        ArenaValidationResult failure = ArenaValidationResult.failure("Missing spawn", "spawn");
        failure.addError("Missing lobby", "lobby");

        assertTrue(failure.hasErrors());
        assertEquals(java.util.List.of("Missing spawn", "Missing lobby"), failure.getErrors());
    }

    @Test
    void commandExceptionDefaultsToEmptyMessageAndResolvesLocalizedTemplates() {
        assertEquals("", new CommandException().getMessage());

        STEMCraftAPI api = mock(STEMCraftAPI.class);
        LocaleService locales = Mockito.mock(LocaleService.class, Mockito.CALLS_REAL_METHODS);
        when(api.locales()).thenReturn(locales);
        when(locales.getDefaultLocale()).thenReturn("en-AU");
        when(locales.resolve("en-AU", "GREETING")).thenReturn("Hello {name}");
        InstanceHolder.set(api, mock(Plugin.class));

        CommandException exception = new CommandException("GREETING", "name", "Alex");

        assertEquals("Hello Alex", exception.getMessage());
    }

    @Test
    void punishmentRecordExposesState() {
        Instant createdAt = Instant.parse("2026-04-21T00:00:00Z");

        PunishmentRecord permanent = new PunishmentRecord(
            1L,
            UUID.randomUUID(),
            "Target",
            UUID.randomUUID(),
            "Mod",
            "ban",
            false,
            "Reason",
            createdAt,
            null
        );
        assertTrue(permanent.permanent());
        assertFalse(permanent.cancelled());
        assertNull(permanent.expiresAt());

        PunishmentRecord timed = new PunishmentRecord(
            2L,
            UUID.randomUUID(),
            "Other",
            UUID.randomUUID(),
            "Mod",
            "mute",
            false,
            "Reason",
            createdAt,
            60L
        );
        assertEquals(createdAt.plusSeconds(60), timed.expiresAt());
        assertFalse(timed.alerted());
        timed.setAlerted();
        assertTrue(timed.alerted());

        PunishmentRecord cancelled = new PunishmentRecord(
            3L,
            UUID.randomUUID(),
            "Other",
            UUID.randomUUID(),
            "Mod",
            "mute",
            false,
            "Reason",
            createdAt,
            -1L
        );
        assertTrue(cancelled.cancelled());
    }

    @Test
    void webServiceEscapesHtmlSpecialCharacters() {
        assertEquals("", WebService.escapeHtml(null));
        assertEquals("&lt;tag&gt; &amp; value", WebService.escapeHtml("<tag> & value"));
    }
}
