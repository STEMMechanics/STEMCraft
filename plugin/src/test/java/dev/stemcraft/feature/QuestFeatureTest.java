package dev.stemcraft.feature;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandBuilder;
import dev.stemcraft.api.service.command.CommandService;
import dev.stemcraft.api.service.tabcomplete.TabCompleteService;
import dev.stemcraft.api.service.web.WebServiceRequest;
import dev.stemcraft.feature.quest.QuestObjective;
import dev.stemcraft.feature.quest.QuestDefinition;
import dev.stemcraft.feature.quest.QuestRewardItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

import dev.stemcraft.api.util.PatternUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestFeatureTest {
    @Test
    void enablesAutomaticTrackingWhenNoPreferenceHasBeenSaved() {
        assertTrue(QuestFeature.autoTrackingEnabled(false, false));
        assertTrue(QuestFeature.autoTrackingEnabled(true, true));
        assertFalse(QuestFeature.autoTrackingEnabled(true, false));
    }

    @Test
    void handlesQuestNpcInteractionsOnceFromThePrimaryHand() {
        assertTrue(QuestFeature.isPrimaryNpcInteraction(EquipmentSlot.HAND));
        assertFalse(QuestFeature.isPrimaryNpcInteraction(EquipmentSlot.OFF_HAND));
    }

    @Test
    void normalizesAdministratorQuestIds() {
        assertEquals("the-lost-book", QuestFeature.normalizeId(" The Lost Book! "));
        assertEquals("already-valid_2", QuestFeature.normalizeId("already-valid_2"));
    }

    @Test
    void calculatesThreeDimensionalLocationDistance() {
        QuestObjective objective = new QuestObjective(QuestObjective.Type.LOCATION, "", 1, false,
            "Visit", "world", 10, 20, 30, 5);

        assertEquals(25D, QuestFeature.distanceSquared(new Location(null, 13, 24, 30), objective));
    }

    @Test
    void obfuscatesQuestBookTextWhilePreservingItsLayout() {
        assertEquals("Cde Zab 01!\nPqvg", QuestFeature.obfuscateBookText("Abc Xyz 89!\nNote", 2));
    }

    @Test
    void wrapsQuestLoreAtWordBoundaries() {
        assertEquals(List.of("Marlow would like", "you to bring 4 cod."),
            QuestFeature.wrapLoreText("Marlow would like you to bring 4 cod.", 20));
    }

    @Test
    void biomeObjectivesUseTenSecondDefault() {
        QuestObjective objective = new QuestObjective(QuestObjective.Type.BIOME, "jungle", 1, false,
            "Explore a jungle biome", null, 0, 0, 0, 1);

        assertEquals(10, objective.amount());
    }

    @Test
    void pointsRelativeToThePlayersFacingDirection() {
        World world = mock(World.class);
        Location player = new Location(world, 0, 64, 0, 0, 0);

        assertEquals("↑", QuestFeature.trackingArrow(player, new Location(world, 0, 64, 10)));
        assertEquals("↓", QuestFeature.trackingArrow(player, new Location(world, 0, 64, -10)));
        assertEquals("←", QuestFeature.trackingArrow(player, new Location(world, 10, 64, 0)));
        assertEquals("→", QuestFeature.trackingArrow(player, new Location(world, -10, 64, 0)));
    }

    @Test
    void placesTrackedProgressInsideCollectAndKillDescriptions() {
        assertEquals("Gather 0/6 Oak Logs", QuestFeature.formatTrackedObjective("Gather 6 oak logs", 0, 6));
        assertEquals("Kill 0/3 Iron Golems", QuestFeature.formatTrackedObjective("Kill 3 iron golems", 0, 3));
        assertEquals("Bring 0/1 Compass", QuestFeature.formatTrackedObjective("Bring a compass", 0, 1));
        assertEquals("Find 0/1 Music Disc 13", QuestFeature.formatTrackedObjective("Find music disc 13", 0, 1));
        assertEquals("Craft 0/12 Flower Pots",
            QuestFeature.formatTrackedObjective("Craft 12 flower pots for cottage windows", 0, 12));
    }

    @Test
    void removesStorylinePrefixFromTrackedQuestTitle() {
        assertEquals("The Farther Reach",
            QuestFeature.trackingQuestTitle("Walls for Everyone: The Farther Reach"));
        assertEquals("Under the Canopy", QuestFeature.trackingQuestTitle("Under the Canopy"));
    }

    @Test
    void trackedQuestBarIsLimitedToConfiguredSurvivalWorlds() {
        List<Pattern> worlds = List.of(PatternUtil.globToRegex("survival*"));

        assertTrue(QuestFeature.matchesTrackingWorld(worlds, "survival"));
        assertTrue(QuestFeature.matchesTrackingWorld(worlds, "survival_nether"));
        assertTrue(QuestFeature.matchesTrackingWorld(worlds, "Survival_The_End"));
        assertFalse(QuestFeature.matchesTrackingWorld(worlds, "world"));
        assertFalse(QuestFeature.matchesTrackingWorld(worlds, "bw_castle"));
    }

    @Test
    void webEditorDeniesAccessWithoutAnIssuedCode() {
        QuestFeature feature = new QuestFeature(mock(STEMCraftAPI.class));
        Object response = feature.handleWebEditor(new WebServiceRequest("GET", "/quests/editor/nope",
            "/quests/editor/nope", Map.of(), Map.of(), new byte[0], "test"));

        assertEquals(403, ((Map<?, ?>) response).get("responseCode"));
        assertEquals("Not permitted", ((Map<?, ?>) response).get("body"));
    }

    @Test
    void registersQuestCommandWithPlugin() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        CommandService commands = mock(CommandService.class);
        TabCompleteService tabComplete = mock(TabCompleteService.class);
        CommandBuilder builder = mock(CommandBuilder.class, RETURNS_SELF);
        STEMCraft plugin = mock(STEMCraft.class);
        when(api.commands()).thenReturn(commands);
        when(api.tabComplete()).thenReturn(tabComplete);
        when(commands.create("quest")).thenReturn(builder);

        try (MockedStatic<STEMCraft> stemCraft = mockStatic(STEMCraft.class)) {
            stemCraft.when(STEMCraft::getPlugin).thenReturn(plugin);
            new QuestFeature(api).registerQuestCommand();
        }

        verify(builder).register(plugin);
        verify(builder).tabCompletion("abandon-all", "confirm");
    }

    @Test
    void splitsLargeStructuredRewardsIntoLegalStacks() {
        MockBukkit.mock();
        try {
            QuestDefinition quest = new QuestDefinition("reward-test", "Reward Test");
            quest.rewardItems().add(new QuestRewardItem(Material.EMERALD, 130));
            var items = QuestFeature.createRewardItems(quest);
            assertEquals(3, items.size());
            assertEquals(64, items.get(0).getAmount());
            assertEquals(64, items.get(1).getAmount());
            assertEquals(2, items.get(2).getAmount());
        } finally { MockBukkit.unmock(); }
    }

    @Test
    void createsNamedUnbreakableTributeRewardsWithLore() {
        MockBukkit.mock();
        try {
            QuestDefinition quest = new QuestDefinition("tribute", "Tribute");
            quest.rewardItems().add(new QuestRewardItem(Material.GOLDEN_HELMET, 1, "The Potato Crown",
                List.of("Technoblade Never Dies"), true));

            var item = QuestFeature.createRewardItems(quest).getFirst();
            assertTrue(item.getItemMeta().isUnbreakable());
            assertNotNull(item.getItemMeta().displayName());
            assertEquals(1, item.getItemMeta().lore().size());
        } finally { MockBukkit.unmock(); }
    }

    @Test
    void totalsExperiencePointRewardCommands() {
        QuestDefinition quest = new QuestDefinition("xp-test", "XP Test");
        quest.rewardCommands().add("experience add {player} 10 points");
        quest.rewardCommands().add("xp add {player} 5 points");
        quest.rewardCommands().add("say not experience");

        assertEquals(15, QuestFeature.experienceReward(quest));
    }
}
