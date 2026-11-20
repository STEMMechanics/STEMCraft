package dev.stemcraft.feature;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VotingFeatureTest {
    @Test void lampsShowRawVotesUntilCapacity() {
        assertEquals(0, VotingFeature.lampCount(0, 8, 10));
        assertEquals(7, VotingFeature.lampCount(7, 8, 10));
    }

    @Test void lampsScaleAgainstLeaderBeyondCapacity() {
        assertEquals(10, VotingFeature.lampCount(25, 25, 10));
        assertEquals(8, VotingFeature.lampCount(20, 25, 10));
        assertEquals(4, VotingFeature.lampCount(10, 25, 10));
        assertEquals(1, VotingFeature.lampCount(1, 25, 10));
    }

    @Test void loginVoteSummaryIncludesNewVotesAndRank() {
        assertEquals("Monthly Challenge: +3 votes, 12 total — 2nd",
            VotingFeature.formatLoginVoteSummary("Monthly Challenge", 3, 12, 2));
    }

    @Test void loginVoteSummaryCanShowCurrentTopThreeWithoutNewVotes() {
        assertEquals("Monthly Challenge: 2nd — 12 votes",
            VotingFeature.formatLoginVoteSummary("Monthly Challenge", 0, 12, 2));
    }

    @Test void loginVoteSummaryOmitsPlayersOutsideTopThreeWithoutNewVotes() {
        assertEquals("", VotingFeature.formatLoginVoteSummary("Monthly Challenge", 0, 12, null));
    }

    @Test void ordinalUsesEnglishSuffixes() {
        assertEquals("1st", VotingFeature.ordinal(1));
        assertEquals("2nd", VotingFeature.ordinal(2));
        assertEquals("3rd", VotingFeature.ordinal(3));
        assertEquals("11th", VotingFeature.ordinal(11));
        assertEquals("22nd", VotingFeature.ordinal(22));
    }

    @Test void reminderDaysRoundUpUntilTheCutoff() {
        long now = 1_000_000L;
        assertEquals(3, VotingFeature.daysRemaining(now + 3 * 86_400L, now));
        assertEquals(1, VotingFeature.daysRemaining(now + 1, now));
        assertEquals(0, VotingFeature.daysRemaining(now, now));
    }

    @Test void reminderMessagesRenderConfiguredPlaceholders() {
        assertEquals("Vote for Monthly Challenge — 2 days left, closes at 2026-11-05, 12 votes so far.",
            VotingFeature.formatReminderMessage(
                "Vote for {group} — {days} days left, closes at {end}, {votes} votes so far.",
                "Monthly Challenge", 2, "2026-11-05", 12));
    }

    @Test void dateCompletionBuildsTheDateInStages() {
        assertEquals(List.of(LocalDate.now().toString()), VotingFeature.dateCompletions(""));
        assertEquals(12, VotingFeature.dateCompletions("2026-").size());
        assertEquals(28, VotingFeature.dateCompletions("2027-02-").size());
        assertEquals(List.of("2027-02-20", "2027-02-21", "2027-02-22", "2027-02-23", "2027-02-24",
            "2027-02-25", "2027-02-26", "2027-02-27", "2027-02-28"), VotingFeature.dateCompletions("2027-02-2"));
        assertEquals(List.of(), VotingFeature.dateCompletions("2027-02-28"));
    }

    @Test void bundledTowerSchemaUsesReadableVerticalLayout() {
        var stream = getClass().getResourceAsStream("/config.yml"); assertNotNull(stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertEquals("LECTERN", config.getString("voting.tower.schema.blocks.L"));
        assertEquals("STONE", config.getString("voting.tower.schema.blocks.S"));
        assertEquals(List.of(1, 0), config.getIntegerList("voting.tower.schema.click-point"));
        assertEquals(List.of("S"), config.getStringList("voting.tower.schema.place-if-not-solid"));
        List<String> layout = config.getStringList("voting.tower.schema.layout");
        assertEquals(12, layout.size());
        assertEquals("...9", layout.getFirst());
        assertEquals("VL.S", layout.getLast());
        assertEquals(List.of('0','1','2','3','4','5','6','7','8','9'),
            layout.subList(0, 10).reversed().stream().map(row -> row.charAt(3)).toList());
    }
}
