package dev.stemcraft.feature;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChiselFeatureTest {
    private static final String ITEMS = "data-packs/stemcraft-survival/configs/custom-items.yml";

    @Test
    void rotatesStairsClockwiseWithoutDependingOnAConcreteStairMaterial() {
        Directional stairs = mock(Directional.class);
        when(stairs.getFacing()).thenReturn(BlockFace.NORTH);
        when(stairs.getFaces()).thenReturn(Set.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST));

        assertTrue(ChiselFeature.rotate(Material.OAK_STAIRS, stairs));
        verify(stairs).setFacing(BlockFace.EAST);
    }

    @Test
    void rotatesLogsThroughTheirThreeAxes() {
        Orientable log = mock(Orientable.class);
        when(log.getAxis()).thenReturn(Axis.Y);
        when(log.getAxes()).thenReturn(Set.of(Axis.X, Axis.Y, Axis.Z));

        assertTrue(ChiselFeature.rotate(Material.OAK_LOG, log));
        verify(log).setAxis(Axis.Z);
    }

    @Test
    void rotatesStandingDecorationsOneStep() {
        Rotatable sign = mock(Rotatable.class);
        when(sign.getRotation()).thenReturn(BlockFace.SOUTH);

        assertTrue(ChiselFeature.rotate(Material.OAK_SIGN, sign));
        verify(sign).setRotation(BlockFace.SOUTH_SOUTH_WEST);
    }

    @Test
    void refusesUnsafeDirectionalAndPlainTerracottaBlocks() {
        Directional dispenser = mock(Directional.class);
        assertFalse(ChiselFeature.rotate(Material.DISPENSER, dispenser));
        assertFalse(ChiselFeature.isSafeDirectional(Material.TERRACOTTA));
        assertTrue(ChiselFeature.isSafeDirectional(Material.BLUE_GLAZED_TERRACOTTA));
    }

    @Test
    void bundledItemHasDurabilityCrossPlatformTextureAndIntendedRecipe() {
        YamlConfiguration config = load(ITEMS);
        assertEquals("FLINT", config.getString("custom-items.chisel.material"));
        assertEquals(1, config.getInt("custom-items.chisel.max-stack-size"));
        assertEquals(256, config.getInt("custom-items.chisel.max-damage"));
        assertEquals("stemcraft_survival:item/chisel", config.getString("custom-items.chisel.texture"));
        assertEquals("stemcraft:chisel", config.getString("recipes.shaped.chisel.result"));
        assertEquals(List.of(" I ", " S "), config.getStringList("recipes.shaped.chisel.shape"));
        assertEquals("IRON_INGOT", config.getString("recipes.shaped.chisel.ingredients.I"));
        assertEquals("STICK", config.getString("recipes.shaped.chisel.ingredients.S"));
        assertNotNull(getClass().getClassLoader().getResource(
            "data-packs/stemcraft-survival/contents/stemcraft_survival/textures/item/chisel.png"));
    }

    private static YamlConfiguration load(String resource) {
        InputStream stream = ChiselFeatureTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, "Missing bundled resource " + resource);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
