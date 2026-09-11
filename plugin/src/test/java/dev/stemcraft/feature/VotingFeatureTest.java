package dev.stemcraft.feature;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
