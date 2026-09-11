package dev.stemcraft.feature.quest;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestDefinitionStoreTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsEditableQuestDefinition() throws Exception {
        UUID start = UUID.randomUUID();
        UUID end = UUID.randomUUID();
        QuestDefinition quest = new QuestDefinition("leather-run", "Leather Run");
        quest.author("The Tanner");
        quest.description("Bring leather back to the tannery.");
        quest.rewardText("3 emeralds and the tanner's thanks");
        quest.timeLimitSeconds(300);
        quest.restartCooldownSeconds(86400);
        quest.globalMaxCompletions(2);
        quest.startNpc(start);
        quest.endNpc(end);
        quest.startNpcName("Rokar");
        quest.endNpcName("Tailor");
        quest.dialogue("idle").add("The road is quiet today.");
        quest.requirements().add("first-steps");
        quest.rewardCommands().add("give {player} emerald 3");
        quest.rewardItems().add(new QuestRewardItem(Material.STONE_PICKAXE, 1));
        quest.objectives().add(QuestObjective.collect(Material.LEATHER, 10, true, "Gather ten leather"));
        quest.objectives().add(QuestObjective.kill(EntityType.ZOMBIE, 2, "Clear the road"));
        quest.objectives().add(new QuestObjective(QuestObjective.Type.STRUCTURE, "SHIPWRECK", 1, false,
            "Enter a shipwreck", null, 0, 0, 0, 1));
        quest.objectives().add(new QuestObjective(QuestObjective.Type.ALTITUDE_BELOW, "0", 1, false,
            "Descend below Y 0", null, 0, 0, 0, 1));

        File file = tempDir.resolve("quests.yml").toFile();
        QuestDefinitionStore.save(file, Map.of(quest.id(), quest));
        QuestDefinition loaded = QuestDefinitionStore.load(file).get("leather-run");

        assertNotNull(loaded);
        assertEquals("Leather Run", loaded.title());
        assertEquals("The Tanner", loaded.author());
        assertEquals("3 emeralds and the tanner's thanks", loaded.rewardText());
        assertEquals(300, loaded.timeLimitSeconds());
        assertEquals(86400, loaded.restartCooldownSeconds());
        assertEquals(2, loaded.globalMaxCompletions());
        assertEquals(start, loaded.startNpc());
        assertEquals(end, loaded.endNpc());
        assertEquals("Rokar", loaded.startNpcName());
        assertEquals("Tailor", loaded.endNpcName());
        assertTrue(loaded.dialogue("idle").contains("The road is quiet today."));
        assertEquals(java.util.Set.of("first-steps"), loaded.requirements());
        assertEquals("give {player} emerald 3", loaded.rewardCommands().getFirst());
        assertEquals(new QuestRewardItem(Material.STONE_PICKAXE, 1), loaded.rewardItems().getFirst());
        assertEquals(4, loaded.objectives().size());
        assertEquals(QuestObjective.Type.COLLECT, loaded.objectives().getFirst().type());
        assertTrue(loaded.objectives().getFirst().consume());
        assertEquals(10, loaded.objectives().getFirst().amount());
        assertEquals(QuestObjective.Type.STRUCTURE, loaded.objectives().get(2).type());
        assertEquals("SHIPWRECK", loaded.objectives().get(2).target());
        assertEquals(QuestObjective.Type.ALTITUDE_BELOW, loaded.objectives().get(3).type());
    }

    @Test
    void skipsMalformedObjectivesButLoadsQuest() throws Exception {
        File file = tempDir.resolve("quests.yml").toFile();
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("quests.test.title", "Test");
        yaml.set("quests.test.objectives", java.util.List.of(Map.of("type", "not-real")));
        yaml.save(file);

        QuestDefinition loaded = QuestDefinitionStore.load(file).get("test");

        assertNotNull(loaded);
        assertTrue(loaded.objectives().isEmpty());
    }

    @Test
    void loadsBundledDefinitionsFromReader() {
        QuestDefinition loaded = QuestDefinitionStore.load(new StringReader("""
            quests:
              example:
                title: Reader Example
                enabled: false
                objectives:
                  - type: collect
                    target: BREAD
                    amount: 4
                    consume: true
            """)).get("example");

        assertNotNull(loaded);
        assertFalse(loaded.enabled());
        assertEquals(Material.BREAD.name(), loaded.objectives().getFirst().target());
        assertEquals(4, loaded.objectives().getFirst().amount());
    }
}
