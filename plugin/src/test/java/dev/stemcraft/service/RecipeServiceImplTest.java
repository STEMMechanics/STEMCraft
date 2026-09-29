package dev.stemcraft.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class RecipeServiceImplTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void materialAndExactTemplatesKeepTheirTrimPatterns() {
        String suffix = "_armor_trim_smithing_template";
        int checked = 0;
        for (Material material : Material.values()) {
            String name = material.name().toLowerCase(Locale.ROOT);
            if (!name.endsWith(suffix)) continue;
            TrimPattern expected = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(NamespacedKey.minecraft(name.substring(0, name.length() - suffix.length())));
            assertNotNull(expected, name);
            assertEquals(expected, RecipeServiceImpl.trimPattern(new RecipeChoice.MaterialChoice(material)), name);
            assertEquals(expected, RecipeServiceImpl.trimPattern(RecipeChoice.exactChoice(new ItemStack(material))), name);
            checked++;
        }
        assertTrue(checked >= 18);
    }

    @Test void nonTemplateChoicesKeepTheLegacyBoltFallback() {
        assertEquals(TrimPattern.BOLT, RecipeServiceImpl.trimPattern(new RecipeChoice.MaterialChoice(Material.STONE)));
    }
}
