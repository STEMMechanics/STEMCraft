package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.config.ConfigService;
import dev.stemcraft.api.service.event.EventService;
import org.junit.jupiter.api.Test;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MotdServiceImplTest {

    @Test
    void formatLegacyMotdKeepsTwoLinesForLegacyConfiguredValues() {
        String formatted = MotdServiceImpl.formatLegacyMotd(
            "§9■ §e§lSTEMCRAFT",
            "§r                   §e★ §bEnjoy your stay! §e★"
        );

        assertEquals("§9■ §e§lSTEMCRAFT\n                   §e★ §bEnjoy your stay! §e★", formatted);
        assertTrue(formatted.contains("\n"));
    }

    @Test
    void formatLegacyMotdConvertsMiniMessageMarkupToLegacySections() {
        assertEquals(
            "§9STEMCRAFT\n§eWelcome",
            MotdServiceImpl.formatLegacyMotd(
                "<blue>STEMCRAFT</blue>",
                "<yellow>Welcome</yellow>"
            )
        );
    }

    @Test
    void renderLineAddsPixelAwarePaddingWhenCentered() {
        assertEquals(
            "                   <yellow>★</yellow> <aqua>Enjoy your stay!</aqua> <yellow>★</yellow>",
            MotdServiceImpl.renderLine("<yellow>★</yellow> <aqua>Enjoy your stay!</aqua> <yellow>★</yellow>", true)
        );
        assertEquals(
            "<yellow>★</yellow> <aqua>Enjoy your stay!</aqua> <yellow>★</yellow>",
            MotdServiceImpl.renderLine("<yellow>★</yellow> <aqua>Enjoy your stay!</aqua> <yellow>★</yellow>", false)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void onReloadRefreshesDefaultsFromConfigAndSettersPersistChanges() {
        STEMCraft plugin = mock(STEMCraft.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ConfigService configService = mock(ConfigService.class);
        EventService eventService = mock(EventService.class);
        ConfigFile rootConfig = mock(ConfigFile.class);
        ConfigSection motdSection = mock(ConfigSection.class);
        Map<String, String> values = new HashMap<>();
        values.put("title", "<yellow>Initial</yellow>");
        values.put("text", "<gray>Initial text</gray>");
        Map<String, Boolean> centered = new HashMap<>();
        centered.put("center-title", false);
        centered.put("center-text", false);

        when(api.config()).thenReturn(configService);
        when(api.events()).thenReturn(eventService);
        when(configService.load("config.yml")).thenReturn(rootConfig);
        when(rootConfig.isSection("motd")).thenReturn(true);
        when(rootConfig.getSection("motd", false)).thenReturn(motdSection);
        when(motdSection.getString("title", "<gold><bold>STEMCraft</bold></gold>"))
            .thenAnswer(invocation -> values.getOrDefault("title", invocation.getArgument(1)));
        when(motdSection.getString("text", ""))
            .thenAnswer(invocation -> values.getOrDefault("text", invocation.getArgument(1)));
        when(motdSection.getBoolean("center-title", false))
            .thenAnswer(invocation -> centered.getOrDefault("center-title", invocation.getArgument(1)));
        when(motdSection.getBoolean("center-text", false))
            .thenAnswer(invocation -> centered.getOrDefault("center-text", invocation.getArgument(1)));
        doReturn(mock(Listener.class)).when(eventService)
            .register(any(Class.class), any(), any(EventPriority.class), anyBoolean());
        doAnswer(invocation -> {
            values.put("title", "<green>Reloaded</green>");
            values.put("text", "<aqua>Reloaded text</aqua>");
            centered.put("center-title", true);
            centered.put("center-text", true);
            return true;
        }).when(rootConfig).reload();

        MotdServiceImpl service = new MotdServiceImpl(plugin, api);
        service.onEnable();

        assertEquals("<yellow>Initial</yellow>", service.defaultMotd().motdTitle());
        assertEquals("<gray>Initial text</gray>", service.defaultMotd().motdText());
        assertEquals(false, service.isDefaultTitleCentered());
        assertEquals(false, service.isDefaultTextCentered());

        service.onReload();

        assertEquals("<green>Reloaded</green>", service.defaultMotd().motdTitle());
        assertEquals("<aqua>Reloaded text</aqua>", service.defaultMotd().motdText());
        assertEquals(true, service.isDefaultTitleCentered());
        assertEquals(true, service.isDefaultTextCentered());

        service.updateDefaultTitle("<gold>Admin title</gold>", false);
        service.updateDefaultText("<white>Admin text</white>", false);

        assertEquals("<gold>Admin title</gold>", service.defaultMotd().motdTitle());
        assertEquals("<white>Admin text</white>", service.defaultMotd().motdText());
        assertEquals("<gold>Admin title</gold>", service.current().motdTitle());
        assertEquals("<white>Admin text</white>", service.current().motdText());
        assertEquals(false, service.isDefaultTitleCentered());
        assertEquals(false, service.isDefaultTextCentered());

        verify(rootConfig).reload();
        verify(motdSection).set("title", "<gold>Admin title</gold>");
        verify(motdSection).set("center-title", false);
        verify(motdSection).set("text", "<white>Admin text</white>");
        verify(motdSection).set("center-text", false);
        verify(motdSection, times(2)).save();
    }
}
