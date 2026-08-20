package dev.stemcraft.feature.quest;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SurvivalCampaignIntegrityTest {
    @Test
    void campaignHasValidCountsAndReferences() throws Exception {
        MockBukkit.mock();
        try {
        var resource = getClass().getClassLoader().getResourceAsStream("quests/survival-campaign.yml");
        assertNotNull(resource);
        byte[] data = resource.readAllBytes();
        var quests = QuestDefinitionStore.load(new InputStreamReader(new java.io.ByteArrayInputStream(data), StandardCharsets.UTF_8));
        var profiles = QuestNpcProfileStore.load(new InputStreamReader(new java.io.ByteArrayInputStream(data), StandardCharsets.UTF_8));
        assertEquals(416, quests.size());
        assertEquals(49, profiles.size());
        int collect = 0, kill = 0, npc = 0, special = 0;
        for (QuestDefinition quest : quests.values()) {
            assertFalse(quest.id().matches("survival-\\d+"), quest.id() + " should have a player-friendly ID");
            assertFalse(quest.shortDescription().isBlank(), quest.id() + " should have a tooltip summary");
            assertTrue(quest.description().matches("(?s).*\\b(I|I'm|I've|me|my)\\b.*"),
                quest.id() + " should tell its story in the quest giver's voice");
            assertTrue(profiles.containsKey(quest.startNpcProfile()));
            assertTrue(profiles.containsKey(quest.endNpcProfile()));
            assertTrue(quests.keySet().containsAll(quest.requirements()));
            for (QuestObjective objective : quest.objectives()) {
                if (objective.type() == QuestObjective.Type.COLLECT) {
                    collect++;
                    assertNotNull(Material.matchMaterial(objective.target()), quest.id() + " has invalid material " + objective.target());
                }
                if (objective.type() == QuestObjective.Type.KILL || objective.type() == QuestObjective.Type.INTERACT) {
                    if (objective.type() == QuestObjective.Type.KILL) kill++;
                    if (!"HOSTILE".equals(objective.target()))
                        assertDoesNotThrow(() -> EntityType.valueOf(objective.target()), quest.id() + " has invalid entity " + objective.target());
                }
                if (objective.type() == QuestObjective.Type.NPC) {
                    npc++;
                    assertTrue(objective.target().startsWith("profile:"));
                    assertTrue(profiles.containsKey(objective.target().substring("profile:".length())));
                }
                if (java.util.Set.of(QuestObjective.Type.BIOME, QuestObjective.Type.ALTITUDE_ABOVE,
                    QuestObjective.Type.ALTITUDE_BELOW, QuestObjective.Type.STRUCTURE, QuestObjective.Type.SLEEP,
                    QuestObjective.Type.INTERACT, QuestObjective.Type.UNDERWATER, QuestObjective.Type.NIGHT).contains(objective.type())) special++;
            }
        }
        assertTrue(collect >= 75);
        assertTrue(kill >= 10);
        assertTrue(npc >= 10);
        assertTrue(special >= 20);
        long experienceQuests = quests.values().stream().filter(quest -> quest.rewardCommands().stream()
            .anyMatch(command -> command.matches("(?i)^(experience|xp) add \\{player} \\d+ points$"))).count();
        assertTrue(experienceQuests >= 60, "Dangerous, exploratory, and finale quests should award XP");
        assertTrue(quests.values().stream().filter(QuestDefinition::repeatable).noneMatch(quest -> quest.rewardCommands().stream()
            .anyMatch(command -> command.matches("(?i)^(experience|xp) add \\{player} \\d+ points$"))),
            "Repeatable quests should not become XP farms");
        assertTrue(quests.values().stream().noneMatch(quest -> quest.title().matches("(?i).*lesson\\s*\\d+.*")));
        long forks = quests.keySet().stream().filter(id -> quests.values().stream()
            .filter(quest -> quest.requirements().contains(id)).count() > 1).count();
        assertTrue(forks >= 10, "The bundled campaign should contain meaningful story branches");
        assertEquals(40, quests.values().stream().filter(quest ->
            Set.of("expansion-kael", "expansion-bruna").contains(quest.startNpcProfile())).count());
        for (String profileId : Set.of("expansion-kael", "expansion-bruna", "expansion-selene", "expansion-mira", "expansion-rowan")) {
            assertEquals(20, quests.values().stream().filter(quest -> profileId.equals(quest.startNpcProfile())).count());
        }
        QuestNpcProfile tribute = profiles.get("tribute-technoblade");
        assertNotNull(tribute);
        assertTrue(tribute.invulnerable());
        assertEquals(Set.of("survival", "survival_nether"), new HashSet<>(tribute.worlds()));
        assertEquals(10, quests.values().stream().filter(quest -> "tribute-technoblade".equals(quest.startNpcProfile())).count());
        for (String id : Set.of("seeds-on-the-homestead", "a-beacon-in-the-plains", "safe-until-morning", "smoke-from-the-woodlot",
            "the-foragers-supper", "the-pumpkin-patch", "bones-for-the-garden", "the-weaver-in-the-grass")) {
            QuestNpcProfile profile = profiles.get(quests.get(id).startNpcProfile());
            assertTrue(profile.biomes().contains("PLAINS"), id + " should remain available to plains players");
        }
        assertFalse(hasCycle(quests, new HashSet<>(), new HashSet<>(), "ready-for-the-other-side"));
        assertFalse(hasCycle(quests, new HashSet<>(), new HashSet<>(), "star-after-storm"));
        for (String id : Set.of("marlow-fishing", "apples-for-the-storehouse")) {
            QuestDefinition quest = quests.get(id);
            QuestNpcProfile profile = profiles.get(quest.startNpcProfile());
            assertTrue(quest.repeatable(), id + " should remain repeatable");
            assertTrue(quest.restartCooldownSeconds() > 0, id + " should have a restart cooldown");
            assertEquals(1.0D, profile.dailyChance());
            assertEquals(0, profile.timeFrom());
            assertEquals(24000, profile.timeUntil());
            assertFalse(profile.ai(), id + " NPC should stay at its encounter location");
        }
        } finally { MockBukkit.unmock(); }
    }

    private boolean hasCycle(java.util.Map<String, QuestDefinition> quests, java.util.Set<String> visiting,
                             java.util.Set<String> visited, String id) {
        if (visiting.contains(id)) return true;
        if (!visited.add(id)) return false;
        visiting.add(id);
        for (String requirement : quests.get(id).requirements()) if (hasCycle(quests, visiting, visited, requirement)) return true;
        visiting.remove(id);
        return false;
    }
}
