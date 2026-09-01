package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
