package dev.stemcraft.feature;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

    @Test void bundledTowerTemplateUsesSemanticPlaceholdersAndVerticalLamps() {
        var stream = getClass().getResourceAsStream("/config.yml"); assertNotNull(stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<Map<?, ?>> template = config.getMapList("voting.tower.template");

        assertEquals(List.of("{lectern}", "{lever}", "{sign}", "{lamp}"),
            template.stream().map(entry -> entry.get("block")).toList());
        Map<?, ?> lampRepeat = (Map<?, ?>) template.get(3).get("repeat");
        assertEquals(10, lampRepeat.get("count"));
        assertEquals(List.of(0, 1, 0), lampRepeat.get("step"));
    }
}
