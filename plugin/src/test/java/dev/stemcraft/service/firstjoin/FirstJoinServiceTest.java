package dev.stemcraft.service.firstjoin;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.database.DatabaseService;
import dev.stemcraft.api.service.message.MessageService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirstJoinServiceTest {
    private ServerMock server;
    private STEMCraft plugin;
    private STEMCraftAPI api;
    private DatabaseService database;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(STEMCraft.class);
        api = mock(STEMCraftAPI.class);
        database = mock(DatabaseService.class);
        messages = mock(MessageService.class);

        when(plugin.getName()).thenReturn("STEMCraft");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("firstjoin-test"));
        when(api.database()).thenReturn(database);
        when(api.messages()).thenReturn(messages);
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void additionQuestionGenerationProducesExpectedPromptAndAnswer() throws Exception {
        FirstJoinService service = createService(new FixedRandom(true, 7, 4));
        FirstJoinQuestion question = service.generateQuestion();

        assertEquals("8 + 5 = ?", question.prompt());
        assertEquals(13, question.answer());
    }

    @Test
    void subtractionNeverProducesNegativeAnswers() throws Exception {
        FirstJoinService service = createService(new Random(1234L));

        for (int i = 0; i < 200; i++) {
            FirstJoinQuestion question = service.generateQuestion();
            if (question.prompt().contains("-")) {
                assertTrue(question.answer() > 0);
            }
        }
    }

    @Test
    void correctResponseReturnsSuccess() throws Exception {
        FirstJoinService service = createService(new Random(1L));
        FirstJoinSession session = new FirstJoinSession(UUID.randomUUID(), 13, 3, System.currentTimeMillis() + 1_000L, server.addPlayer().getLocation(), "8 + 5 = ?");

        FirstJoinService.FirstJoinEvaluationResult result = service.evaluateResponse(session, "13", System.currentTimeMillis());

        assertEquals(FirstJoinService.FirstJoinOutcome.success, result.outcome());
        assertEquals(3, session.attemptsRemaining());
    }

    @Test
    void incorrectResponseReducesAttemptsAndGeneratesNewQuestion() throws Exception {
        FirstJoinService service = createService(new FixedRandom(true, 1, 1));
        FirstJoinSession session = new FirstJoinSession(UUID.randomUUID(), 9, 3, System.currentTimeMillis() + 1_000L, server.addPlayer().getLocation(), "5 + 4 = ?");

        FirstJoinService.FirstJoinEvaluationResult result = service.evaluateResponse(session, "7", System.currentTimeMillis());

        assertEquals(FirstJoinService.FirstJoinOutcome.incorrect, result.outcome());
        assertEquals(2, session.attemptsRemaining());
        assertNotEquals("5 + 4 = ?", session.prompt());
    }

    @Test
    void expiredSessionReturnsExpired() throws Exception {
        FirstJoinService service = createService(new Random(1L));
        FirstJoinSession session = new FirstJoinSession(UUID.randomUUID(), 4, 3, System.currentTimeMillis() - 100L, server.addPlayer().getLocation(), "2 + 2 = ?");

        FirstJoinService.FirstJoinEvaluationResult result = service.evaluateResponse(session, "4", System.currentTimeMillis());

        assertEquals(FirstJoinService.FirstJoinOutcome.expired, result.outcome());
    }

    @Test
    void markVerifiedPersistsAndSetsPlayerMarker() throws Exception {
        FirstJoinService service = createService(new Random(1L));
        PlayerMock player = server.addPlayer("Alex");
        NamespacedKey key = new NamespacedKey("stemcraft", "human_verified");
        setField(service, "verifiedKey", key);

        Method markVerified = FirstJoinService.class.getDeclaredMethod("markVerified", UUID.class, String.class, Player.class);
        markVerified.setAccessible(true);
        markVerified.invoke(service, player.getUniqueId(), player.getName(), player);

        verify(database, times(1)).update(anyString(), any());
        assertEquals((byte) 1, player.getPersistentDataContainer().get(key, PersistentDataType.BYTE));
    }

    @Test
    void adminResetRemovesPersistentMarkerAndClearsActiveSession() throws Exception {
        FirstJoinService service = createService(new Random(1L));
        PlayerMock player = server.addPlayer("Jamie");
        NamespacedKey key = new NamespacedKey("stemcraft", "human_verified");
        setField(service, "verifiedKey", key);

        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        Field sessionsField = FirstJoinService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var sessions = (java.util.Map<UUID, FirstJoinSession>) sessionsField.get(service);
        sessions.put(player.getUniqueId(), new FirstJoinSession(player.getUniqueId(), 2, 3, Instant.now().toEpochMilli() + 1000L, player.getLocation(), "1 + 1 = ?"));

        service.resetFirstJoinStatus(player, "console");

        verify(database, times(1)).update(anyString(), any());
        assertFalse(player.getPersistentDataContainer().has(key, PersistentDataType.BYTE));
        assertFalse(service.hasActiveSession(player.getUniqueId()));
    }

    private FirstJoinService createService(Random random) throws Exception {
        FirstJoinService service = new FirstJoinService(plugin, api, random);
        setField(service, "minimumNumber", 1);
        setField(service, "maximumNumber", 20);
        setField(service, "maximumAttempts", 3);
        setField(service, "timeoutSeconds", 60);
        setField(service, "movementTolerance", 0.5d);
        setField(service, "bypassPermission", "stemcraft.firstjoin.bypass");
        setField(service, "verifiedKeyId", "human_verified");
        setField(service, "enabled", true);
        setField(service, "verifiedKey", new NamespacedKey("stemcraft", "human_verified"));
        return service;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FixedRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final boolean firstBoolean;
        private final int[] ints;
        private int index;

        private FixedRandom(boolean firstBoolean, int... ints) {
            this.firstBoolean = firstBoolean;
            this.ints = ints;
        }

        @Override
        public boolean nextBoolean() {
            return firstBoolean;
        }

        @Override
        public int nextInt(int bound) {
            int value = ints[index % ints.length];
            index++;
            return Math.floorMod(value, bound);
        }
    }
}
