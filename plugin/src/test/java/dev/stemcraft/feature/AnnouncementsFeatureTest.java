package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
