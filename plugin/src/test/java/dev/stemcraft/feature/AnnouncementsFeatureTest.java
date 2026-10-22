package dev.stemcraft.feature;

import dev.stemcraft.api.service.workshop.Workshop;
import dev.stemcraft.api.service.workshop.WorkshopSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementsFeatureTest {
    @Test
    void dueAnnouncementsRotateDeterministicallyByTimeThenId() {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        Map<String, Instant> due = new LinkedHashMap<>();
        due.put("zeta", now.minusSeconds(10));
        due.put("alpha", now.minusSeconds(10));
        due.put("future", now.plusSeconds(10));

        assertEquals(List.of("alpha", "zeta"), AnnouncementsFeature.orderedDueIds(due, now));
    }

    @Test
    void placeholdersRenderMultipleLinesAndMissingValuesSafely() {
        String rendered = AnnouncementsFeature.renderTemplate(
                "<aqua>{next_workshop_title}</aqua>\n{next_workshop_url} {missing}",
                Map.of("next_workshop_title", "Robotics", "next_workshop_url", "https://example.test"));

        assertEquals("<aqua>Robotics</aqua>\nhttps://example.test {missing}", rendered);
    }

    @Test
    void feedValuesAreEscapedBeforeTemplateInsertion() {
        assertEquals("Example \\<red\\>", AnnouncementsFeature.escapeLiteral("Example <red>"));
        assertEquals("https://example.test/a\\'b", AnnouncementsFeature.escapeLiteral("https://example.test/a'b"));
    }

    @Test
    void workshopFiltersUseStrictDayBoundsAndCaseInsensitiveLocations() {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        AnnouncementsFeature.WorkshopFilter filter = new AnnouncementsFeature.WorkshopFilter(
                2.0, 5.0, List.of("stemcraft", "STEMMechanics Lab"));
        Workshop matching = workshop("STEMCRAFT", now.plusSeconds(3 * 86_400L));

        assertTrue(filter.matches(matching, now));
        assertFalse(filter.matches(workshop("STEMCraft", now.plusSeconds(2 * 86_400L)), now));
        assertFalse(filter.matches(workshop("STEMCraft", now.plusSeconds(5 * 86_400L)), now));
        assertFalse(filter.matches(workshop("Community Hall", now.plusSeconds(3 * 86_400L)), now));
    }

    @Test
    void activeWorkshopWindowIncludesStartAndExcludesEnd() {
        Instant start = Instant.parse("2026-10-22T10:00:00Z");
        Workshop workshop = new Workshop("active", "Workshop", "",
                start.atZone(ZoneId.of("UTC")), start.plusSeconds(3600).atZone(ZoneId.of("UTC")),
                "STEMCraft", "", WorkshopSource.WEBSITE, Instant.now(), true);

        assertFalse(AnnouncementsFeature.isActiveWorkshop(workshop, start.minusSeconds(1)));
        assertTrue(AnnouncementsFeature.isActiveWorkshop(workshop, start));
        assertTrue(AnnouncementsFeature.isActiveWorkshop(workshop, start.plusSeconds(3599)));
        assertFalse(AnnouncementsFeature.isActiveWorkshop(workshop, start.plusSeconds(3600)));
        assertFalse(AnnouncementsFeature.isActiveWorkshop(
                new Workshop("undated-end", "Workshop", "", start.atZone(ZoneId.of("UTC")), null,
                        "STEMCraft", "", WorkshopSource.WEBSITE, Instant.now(), true), start));
    }

    private static Workshop workshop(String location, Instant start) {
        ZonedDateTime date = start.atZone(ZoneId.of("UTC"));
        return new Workshop("id-" + location, "Workshop", "", date, null, location, "",
                WorkshopSource.WEBSITE, Instant.now(), true);
    }
}
