package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.profanity.ProfanitySeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class ChatServiceImplScoreTest {
    private ChatServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ChatServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        service.configureContentFilterScoringForTest(Map.of(
            ProfanitySeverity.MILD, 1,
            ProfanitySeverity.MODERATE, 2,
            ProfanitySeverity.HIGH, 5,
            ProfanitySeverity.EXTREME, 10
        ), 1, 3600L);
    }

    @Test
    void recordViolationAccumulatesConfiguredSeverityPoints() {
        UUID playerId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T00:00:00Z");

        assertEquals(5, service.recordViolation(playerId, ProfanitySeverity.HIGH, start).activeScore());
        assertEquals(6, service.recordViolation(playerId, ProfanitySeverity.MILD, start.plusSeconds(30)).activeScore());
    }

    @Test
    void activeViolationScoreDecaysByConfiguredAmountOverTime() {
        UUID playerId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T00:00:00Z");

        service.recordViolation(playerId, ProfanitySeverity.EXTREME, start);

        assertEquals(10, service.getActiveViolationCount(playerId, start.plusSeconds(3599)));
        assertEquals(9, service.getActiveViolationCount(playerId, start.plusSeconds(3600)));
        assertEquals(7, service.getActiveViolationCount(playerId, start.plusSeconds(3 * 3600L)));
        assertEquals(0, service.getActiveViolationCount(playerId, start.plusSeconds(10 * 3600L)));
    }

    @Test
    void contentFilterPunishmentReasonsUseConfiguredPlayerFacingTemplates() {
        service.configureContentFilterMessagesForTest(
            "blocked",
            "warned",
            "Repeated use of inappropriate language in {type} [{severity}].",
            "Severe or repeated use of inappropriate language in {type} [{severity}]."
        );

        String kickReason = service.contentFilterKickReasonForTest("chat", ProfanitySeverity.HIGH);
        String banReason = service.contentFilterBanReasonForTest("book", ProfanitySeverity.EXTREME);

        assertEquals("Repeated use of inappropriate language in chat [high].", kickReason);
        assertEquals("Severe or repeated use of inappropriate language in book [extreme].", banReason);
        assertFalse(kickReason.contains("mf"));
        assertFalse(banReason.contains("mf"));
        assertFalse(kickReason.contains("content_filter_rejected"));
        assertFalse(banReason.contains("content_filter_rejected"));
    }

    @Test
    void duplicateProtectionAllowsTwoEquivalentMessagesAndResetsOnDifferentContent() {
        UUID playerId = UUID.randomUUID();
        service.configureDuplicateMessageLimitForTest(2);

        assertFalse(service.isDuplicateMessageBlocked(playerId, "Hello   World"));
        assertFalse(service.isDuplicateMessageBlocked(playerId, " hello world "));
        assertEquals(true, service.isDuplicateMessageBlocked(playerId, "HELLO WORLD"));
        assertFalse(service.isDuplicateMessageBlocked(playerId, "Something different"));
        assertFalse(service.isDuplicateMessageBlocked(playerId, "hello world"));
    }
}
