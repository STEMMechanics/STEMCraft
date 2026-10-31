package dev.stemcraft.config.migration;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.config.ConfigFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyServiceConfigMigrationTest {
    @TempDir
    Path dataFolder;

    @Test
    void movesLegacyOutboundBridgeSettingsButKeepsInboundChatSettings() throws Exception {
        Path configPath = dataFolder.resolve("config.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("chat.discord-bridge.enabled", true);
        yaml.set("chat.discord-bridge.url", "https://bot.example.test/minecraft/event");
        yaml.set("chat.discord-bridge.bearer-token", "secret");
        yaml.set("chat.discord-bridge.inbound-enabled", true);
        yaml.set("chat.discord-bridge.inbound-format", "&1[DISCORD] {displayname}: {message}");
        yaml.set("chat.discord-bridge.events.player-join", false);
        yaml.set("chat.discord-bridge.redacted-commands", java.util.List.of("login"));
        yaml.save(configPath.toFile());

        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LegacyServiceConfigMigrationTest"));
        ConfigFile configFile = mock(ConfigFile.class);
        when(configFile.getName()).thenReturn("config.yml");

        new LegacyServiceConfigMigration(plugin, configFile).apply();

        YamlConfiguration migrated = new YamlConfiguration();
        migrated.load(Files.newBufferedReader(configPath));
        assertTrue(migrated.getBoolean("web-server.event-subscriptions.enabled"));
        assertEquals(
            "https://bot.example.test/minecraft/event",
            migrated.getString("web-server.event-subscriptions.url")
        );
        assertEquals("secret", migrated.getString("web-server.event-subscriptions.bearer-token"));
        assertFalse(migrated.contains("chat.discord-bridge.url"));
        assertFalse(migrated.contains("chat.discord-bridge.events.player-join"));
        assertTrue(migrated.getBoolean("chat.discord-bridge.inbound-enabled"));
        assertEquals(
            "&1[DISCORD] {displayname}: {message}",
            migrated.getString("chat.discord-bridge.inbound-format")
        );
    }
}
