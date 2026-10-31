package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.database.DatabaseService;
import dev.stemcraft.api.service.playerstats.PlayerStatDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStatsPersistenceTest {
    private STEMCraft plugin;
    private STEMCraftAPI api;
    private DatabaseService database;
    private PlayerStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = mock(STEMCraft.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        api = mock(STEMCraftAPI.class);
        database = mock(DatabaseService.class);
        when(api.database()).thenReturn(database);
        service = new PlayerStatsServiceImpl(plugin, api);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void queriesRefreshMemoryWithoutWritingStatistics() {
        service.register(new PlayerStatDefinition("wins", "Wins", "Wins"));
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        service.set(bob, "Bob", "wins", 4.0d);
        service.set(alice, "Alice", "wins", 4.0d);

        assertEquals(List.of(alice, bob), service.top("wins", 10, "all").stream()
            .map(record -> record.uuid()).toList());
        assertEquals(4.0d, service.list(null, "Alice", "wins", "all").getFirst().stats().getFirst().value());
        verify(database, never()).update(any(), any());
        verify(database, never()).transaction(any());
    }

    @Test
    void failedPersistenceRetainsDirtyStateForRetry() throws Exception {
        service.register(new PlayerStatDefinition("wins", "Wins", "Wins"));
        setField(service, "definitionsDirty", false);
        UUID player = UUID.randomUUID();
        service.set(player, "Alice", "wins", 4.0d);

        when(database.transaction(any())).thenReturn(false, true);
        requestPersistence();

        @SuppressWarnings("unchecked")
        ConcurrentMap<UUID, Long> dirty = (ConcurrentMap<UUID, Long>) getField(service, "dirtyRevisions");
        assertTrue(dirty.containsKey(player));

        requestPersistence();
        assertFalse(dirty.containsKey(player));
        verify(database, org.mockito.Mockito.times(2)).transaction(any());
    }

    @Test
    void equalScoresUseStableUsernameAndUuidOrdering() {
        service.register(new PlayerStatDefinition("wins", "Wins", "Wins"));
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        service.set(second, "Same", "wins", 7.0d);
        service.set(first, "Same", "wins", 7.0d);

        assertEquals(List.of(first, second), service.top("wins", 10, "all").stream()
            .map(record -> record.uuid()).toList());
    }

    private void requestPersistence() throws Exception {
        var method = PlayerStatsServiceImpl.class.getDeclaredMethod("requestPersistence");
        method.setAccessible(true);
        method.invoke(service);
    }

    private Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
