package dev.stemcraft.feature;

import dev.stemcraft.api.service.workshop.Workshop;
import dev.stemcraft.api.service.workshop.WorkshopSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopsFeatureTest {
    @Test
    void parsesEventDatesAndDoesNotUsePublicationDate() throws Exception {
        WorkshopsFeature.FeedParseResult result = WorkshopsFeature.parseFeed(fixture(), ZoneId.of("Australia/Brisbane"));

        assertEquals(2, result.records().size());
        assertEquals(1, result.invalidEntries());
        WorkshopsFeature.ParsedFeedWorkshop robotics = result.records().getFirst();
        assertEquals("https://www.stemmechanics.com.au/workshops/robotics", robotics.sourceKey());
        assertEquals("2026-11-14T10:00+10:00", robotics.startAt().toString());
        assertEquals("STEMMechanics Lab", robotics.location());
        assertTrue(robotics.announcementEligible());
        assertEquals("Closed workshop", result.records().get(1).title());
        assertFalse(result.records().get(1).announcementEligible());
    }

    @Test
    void skipsEntriesWithoutReliableEventDates() throws Exception {
        String xml = "<rss><channel><item><title>Undated</title><guid>one</guid><pubDate>2026-01-01T00:00:00Z</pubDate></item></channel></rss>";

        WorkshopsFeature.FeedParseResult result = WorkshopsFeature.parseFeed(xml.getBytes(StandardCharsets.UTF_8), ZoneId.of("Australia/Brisbane"));

        assertTrue(result.records().isEmpty());
        assertEquals(1, result.invalidEntries());
    }

    @Test
    void malformedXmlIsRejectedWithoutReturningPartialRecords() {
        assertThrows(Exception.class, () -> WorkshopsFeature.parseFeed(
                "<rss><channel><item><title>broken".getBytes(StandardCharsets.UTF_8), ZoneId.of("Australia/Brisbane")));
    }

    @Test
    void externalEntitiesAreNotResolved() throws Exception {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY secret SYSTEM \"file:///etc/passwd\">]>" +
                "<rss><channel><item><title>&secret;</title><guid>xxe</guid>" +
                "<startDate>2026-11-14T10:00:00+10:00</startDate></item></channel></rss>";

        assertThrows(Exception.class, () -> WorkshopsFeature.parseFeed(
                xml.getBytes(StandardCharsets.UTF_8), ZoneId.of("Australia/Brisbane")));
    }

    @Test
    void unsafeLinksAreDiscarded() {
        assertEquals("", WorkshopsFeature.safeHttpUrl("javascript:alert(1)"));
        assertEquals("", WorkshopsFeature.safeHttpUrl("https://example.test/unsafe'link"));
        assertEquals("https://example.test/workshop", WorkshopsFeature.safeHttpUrl("https://example.test/workshop"));
    }

    @Test
    void displayLocationFilterIsCaseInsensitiveAndEmptyMeansAll() {
        assertTrue(WorkshopsFeature.matchesLocationFilter(" STEMCraft ", List.of("stemcraft")));
        assertFalse(WorkshopsFeature.matchesLocationFilter("Community Hall", List.of("STEMCraft")));
        assertTrue(WorkshopsFeature.matchesLocationFilter("Community Hall", List.of()));
    }

    @Test
    void accessInstructionsSupportFormattedMultipleLines() {
        assertEquals(List.of(
                "<gray>Go to <yellow>workshop-world</yellow>.</gray>",
                "<gray>Run <yellow>/server workshop</yellow>.</gray>"),
                WorkshopsFeature.parseAccessInstructions(
                        "<gray>Go to <yellow>workshop-world</yellow>.</gray> | " +
                                "<gray>Run <yellow>/server workshop</yellow>.</gray>"));
    }

    @Test
    void displayPlaceholdersUseConfiguredTimezoneAndSafeWorkshopValues() {
        Workshop workshop = new Workshop(
                "manual-1", "Robotics <Challenge>", "Build <robots>",
                ZonedDateTime.parse("2026-11-14T10:00:00+10:00"),
                ZonedDateTime.parse("2026-11-14T12:00:00+10:00"),
                "STEMCraft", "https://example.test/book", WorkshopSource.MANUAL, Instant.now(), true);

        var values = WorkshopsFeature.displayPlaceholders(workshop, 2, 4, 3, ZoneId.of("Australia/Brisbane"));

        assertEquals("2", values.get("index"));
        assertEquals("Robotics \\<Challenge\\>", values.get("title"));
        assertEquals("Saturday 14 November 2026", values.get("date"));
        assertEquals("10:00 AM", values.get("time"));
        assertEquals("Saturday 14 November 2026 at 12:00 PM", values.get("end"));
        assertEquals("https://example.test/book", values.get("booking_url"));
    }

    @Test
    void duplicateFeedKeysKeepTheFirstRecordAndStableIdsAllowUpdates() {
        ZonedDateTime start = ZonedDateTime.parse("2026-11-14T10:00:00+10:00");
        WorkshopsFeature.ParsedFeedWorkshop first = new WorkshopsFeature.ParsedFeedWorkshop(
                "same-guid", "First title", "", start, null, "", "", true);
        WorkshopsFeature.ParsedFeedWorkshop update = new WorkshopsFeature.ParsedFeedWorkshop(
                "same-guid", "Updated title", "", start, null, "", "", true);

        assertEquals("First title", WorkshopsFeature.deduplicateRecords(List.of(first, update)).get("same-guid").title());
        assertEquals(WorkshopsFeature.stableWebsiteId("same-guid"), WorkshopsFeature.stableWebsiteId("same-guid"));
    }

    @Test
    void workshopExpiryUsesEndTimeWhenPresentAndStartTimeOtherwise() {
        Instant now = Instant.parse("2026-11-14T01:30:00Z");
        ZonedDateTime start = Instant.parse("2026-11-14T00:00:00Z").atZone(ZoneId.of("UTC"));
        Workshop ongoing = new Workshop("one", "One", "", start, start.plusHours(2), "", "", WorkshopSource.WEBSITE, now, true);
        Workshop started = new Workshop("two", "Two", "", start, null, "", "", WorkshopSource.MANUAL, now, true);

        assertTrue(ongoing.isUpcoming(now));
        assertFalse(started.isUpcoming(now));
        assertNull(WorkshopsFeature.parseDateTime("not-a-date", ZoneId.of("Australia/Brisbane"), false));
    }

    private static byte[] fixture() throws IOException {
        try (InputStream input = WorkshopsFeatureTest.class.getResourceAsStream("/fixtures/workshops-feed.xml")) {
            if (input == null) throw new IOException("Fixture not found");
            return input.readAllBytes();
        }
    }
}
