package dev.stemcraft.feature.quest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import org.bukkit.entity.EntityType;

import static org.junit.jupiter.api.Assertions.*;

class QuestNpcProfileStoreTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsConditionalSurvivalProfile() throws Exception {
        QuestNpcProfile tim = new QuestNpcProfile("tim", "Tim");
        tim.world("survival");
        tim.minDistance(30);
        tim.maxDistance(100);
        tim.uniquenessRadius(1200);
        tim.despawnRadius(150);
        tim.minimumLevel(3);
        tim.timeFrom(0);
        tim.timeUntil(9000);
        tim.dailyChance(0.25);
        tim.npcType(EntityType.PLAYER);
        tim.wanderRadius(12);
        tim.wanderDelaySeconds(8);
        tim.skinUrl("https://example.test/tim.png");
        tim.biomes().add("PLAINS");
        tim.idleDialogue().add("Fine day for gathering timber.");
        tim.leavingDialogue().add("Time I was heading home.");
        tim.anchor("survival", 12.5, 70, -24.5, 42);
        tim.anchorInteracted(true);

        var file = tempDir.resolve("npcs.yml").toFile();
        QuestNpcProfileStore.save(file, Map.of("tim", tim));
        QuestNpcProfile loaded = QuestNpcProfileStore.load(file).get("tim");

        assertNotNull(loaded);
        assertEquals("survival", loaded.world());
        assertEquals(30, loaded.minDistance());
        assertEquals(100, loaded.maxDistance());
        assertEquals(1200, loaded.uniquenessRadius());
        assertEquals(150, loaded.despawnRadius());
        assertEquals(3, loaded.minimumLevel());
        assertEquals(9000, loaded.timeUntil());
        assertEquals(0.25, loaded.dailyChance());
        assertEquals(EntityType.PLAYER, loaded.npcType());
        assertEquals(QuestNpcProfile.Behaviour.WANDER, loaded.behaviour());
        assertEquals(12, loaded.wanderRadius());
        assertEquals(8, loaded.wanderDelaySeconds());
        assertEquals("https://example.test/tim.png", loaded.skinUrl());
        assertEquals("Fine day for gathering timber.", loaded.idleDialogue().getFirst());
        assertEquals("Time I was heading home.", loaded.leavingDialogue().getFirst());
        assertTrue(loaded.hasAnchor());
        assertEquals("survival", loaded.anchorWorld());
        assertEquals(12.5, loaded.anchorX());
        assertEquals(42, loaded.anchorPeriod());
        assertTrue(loaded.anchorInteracted());
    }
}
