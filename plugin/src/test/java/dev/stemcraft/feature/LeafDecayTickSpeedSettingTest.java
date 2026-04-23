package dev.stemcraft.feature;

import dev.stemcraft.config.ConfigFileImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeafDecayTickSpeedSettingTest {
    @TempDir
    Path tempDir;

    private WorldMock world;
    private ConfigFileImpl config;
    private LeafDecayTickSpeedSetting setting;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("leaf-decay-speed-tests");
        config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "leaf-decay-speed-test.yml", true));
        setting = new LeafDecayTickSpeedSetting();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void storesPositiveIntegerValues() {
        setting.set(world, config, "12");

        assertEquals("12", setting.get(world, config));
        assertEquals(12, config.getInt(LeafDecayTickSpeedSetting.KEY, 0));
    }

    @Test
    void resetRemovesStoredValue() {
        setting.set(world, config, "8");
        assertTrue(config.contains(LeafDecayTickSpeedSetting.KEY));

        setting.set(world, config, "unset");

        assertFalse(config.contains(LeafDecayTickSpeedSetting.KEY));
        assertEquals("unset", setting.get(world, config));
    }

    @Test
    void ignoresNonPositiveValues() {
        config.set(LeafDecayTickSpeedSetting.KEY, 0);
        assertEquals("unset", setting.get(world, config));

        config.set(LeafDecayTickSpeedSetting.KEY, -4);
        assertEquals("unset", setting.get(world, config));
    }
}
