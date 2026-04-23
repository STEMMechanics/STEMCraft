package dev.stemcraft.service.world.setting;

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

class WorldStateFlagSettingTest {
    @TempDir
    Path tempDir;

    private ServerMock server;
    private WorldMock world;
    private ConfigFileImpl config;
    private WorldStateFlagSetting setting;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("state-flag-tests");
        config = new ConfigFileImpl();
        assertTrue(config.load(tempDir.toFile(), "world-flags-test.yml", true));
        setting = new WorldStateFlagSetting("soil-dry", (flagSetting, api, service) -> { });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getMapsLegacyBooleanValuesToAllowAndDeny() {
        config.set("soil-dry", true);
        assertEquals("allow", setting.get(world, config));

        config.set("soil-dry", false);
        assertEquals("deny", setting.get(world, config));
    }

    @Test
    void setNormalizesBooleanAliasesToAllowAndDeny() {
        setting.set(world, config, "true");
        assertEquals("allow", setting.get(world, config));
        assertEquals("allow", config.getString("soil-dry", ""));

        setting.set(world, config, "false");
        assertEquals("deny", setting.get(world, config));
        assertEquals("deny", config.getString("soil-dry", ""));
    }

    @Test
    void unsetRemovesStoredValue() {
        setting.set(world, config, "deny");
        assertTrue(config.contains("soil-dry"));

        setting.set(world, config, "unset");
        assertFalse(config.contains("soil-dry"));
        assertEquals("unset", setting.get(world, config));
    }
}
