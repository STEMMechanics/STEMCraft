package dev.stemcraft.feature;

import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.world.WorldService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerTabListTest {
    @Test
    void normalizesBritishMiniMessageColourTags() {
        String configured = "<dark_gray>world</dark_grey> <grey>lobby</grey>";
        Component rendered = MiniMessage.miniMessage().deserialize(PlayerTabList.normalizeMiniMessage(configured));

        assertEquals("world lobby", PlainTextComponentSerializer.plainText().serialize(rendered));
    }

    @Test
    void trimsVisibleTextWithoutCountingFormattingTags() {
        Component rendered = MiniMessage.miniMessage().deserialize("<dark_gray>abcdefghij</dark_gray>");

        assertEquals("abcde", PlainTextComponentSerializer.plainText()
            .serialize(PlayerTabList.trimVisible(rendered, 5)));
    }

    @Test
    void worldDisplayNameUsesOverrideAndBeautifiedFallback() {
        WorldService worlds = mock(WorldService.class, CALLS_REAL_METHODS);
        World world = mock(World.class);
        ConfigSection section = mock(ConfigSection.class);
        when(world.getName()).thenReturn("bedwars_amazon");
        when(worlds.getConfigSection(world)).thenReturn(section);
        when(section.getString("display-name", "")).thenReturn("Amazon Arena");
        assertEquals("Amazon Arena", worlds.getDisplayName(world));

        when(section.getString("display-name", "")).thenReturn("");
        assertEquals("Bedwars: Amazon", worlds.getDisplayName(world));
    }

    @Test
    void defaultWorldNamesUseCategoriesWithoutSeparatingDimensionSuffixes() {
        assertEquals("World", WorldService.defaultDisplayName("world"));
        assertEquals("Challenge: Smart Homes", WorldService.defaultDisplayName("challenge_smart_homes"));
        assertEquals("Bedwars: Amazon", WorldService.defaultDisplayName("bedwars_amazon"));
        assertEquals("Survival Nether", WorldService.defaultDisplayName("survival_nether"));
        assertEquals("Survival The End", WorldService.defaultDisplayName("survival_the_end"));
        assertEquals("Bedwars: Forest Nether", WorldService.defaultDisplayName("bedwars_forest_nether"));
        assertEquals("Bedwars Nether", WorldService.defaultDisplayName("bedwars_nether"));
    }
}
