package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWelcomeTest {

    @Test
    void hasUsableMessageRequiresAtLeastOneNonBlankLine() {
        assertFalse(PlayerWelcome.hasUsableMessage(null));
        assertFalse(PlayerWelcome.hasUsableMessage(List.of()));
        assertFalse(PlayerWelcome.hasUsableMessage(List.of("", "  ", "\t")));
        assertTrue(PlayerWelcome.hasUsableMessage(List.of("", "<green>Hello")));
    }

    @Test
    void completedAnniversaryYearsOnlyCountsFullYears() {
        ZoneId zone = ZoneId.of("Australia/Brisbane");
        long firstJoin = ZonedDateTime.of(2025, 7, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli();

        long beforeFirstAnniversary = ZonedDateTime.of(2026, 7, 22, 23, 59, 59, 0, zone).toInstant().toEpochMilli();
        long firstAnniversary = ZonedDateTime.of(2026, 7, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli();
        long secondAnniversary = ZonedDateTime.of(2027, 7, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli();

        assertEquals(0, PlayerWelcome.completedAnniversaryYears(firstJoin, beforeFirstAnniversary, zone));
        assertEquals(1, PlayerWelcome.completedAnniversaryYears(firstJoin, firstAnniversary, zone));
        assertEquals(2, PlayerWelcome.completedAnniversaryYears(firstJoin, secondAnniversary, zone));
    }

    @Test
    void selectAnniversaryYearPicksHighestEligibleConfiguredYear() {
        List<Integer> configuredYears = List.of(1, 2, 5);

        assertEquals(2, PlayerWelcome.selectAnniversaryYear(configuredYears, 2, 0));
        assertEquals(5, PlayerWelcome.selectAnniversaryYear(configuredYears, 8, 2));
        assertEquals(2, PlayerWelcome.selectAnniversaryYear(configuredYears, 4, 0));
        assertNull(PlayerWelcome.selectAnniversaryYear(configuredYears, 1, 1));
        assertNull(PlayerWelcome.selectAnniversaryYear(configuredYears, 0, 0));
    }

    @Test
    void ordinalFormatsEnglishSuffixes() {
        assertEquals("1st", PlayerWelcome.ordinal(1));
        assertEquals("2nd", PlayerWelcome.ordinal(2));
        assertEquals("3rd", PlayerWelcome.ordinal(3));
        assertEquals("4th", PlayerWelcome.ordinal(4));
        assertEquals("11th", PlayerWelcome.ordinal(11));
        assertEquals("12th", PlayerWelcome.ordinal(12));
        assertEquals("13th", PlayerWelcome.ordinal(13));
        assertEquals("21st", PlayerWelcome.ordinal(21));
    }

    @Test
    void messagePathRequiresYearForAnniversaryMessages() {
        assertEquals("first-time", PlayerWelcome.messagePath(PlayerWelcome.MessageKind.FIRST_TIME, null));
        assertEquals("returning", PlayerWelcome.messagePath(PlayerWelcome.MessageKind.RETURNING, null));
        assertEquals("anniversaries.2", PlayerWelcome.messagePath(PlayerWelcome.MessageKind.ANNIVERSARY, 2));
        assertThrows(IllegalArgumentException.class, () -> PlayerWelcome.messagePath(PlayerWelcome.MessageKind.ANNIVERSARY, null));
    }

    @Test
    void lineHelpersSupportBlankDisplayAndBoundsChecks() {
        assertEquals("<blank>", PlayerWelcome.displayLine(""));
        assertEquals("hello", PlayerWelcome.displayLine("hello"));
        assertEquals(0, PlayerWelcome.normalizeInsertIndex(1, 0));
        assertEquals(2, PlayerWelcome.normalizeInsertIndex(3, 2));
        assertEquals(1, PlayerWelcome.normalizeExistingLineIndex(2, 3));
        assertThrows(IllegalArgumentException.class, () -> PlayerWelcome.normalizeInsertIndex(0, 1));
        assertThrows(IllegalArgumentException.class, () -> PlayerWelcome.normalizeExistingLineIndex(4, 3));
    }
}
