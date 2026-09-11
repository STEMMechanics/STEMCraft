package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class LocaleServiceImplTest {
    @Test
    @SuppressWarnings("unchecked")
    void resolveSupportsDottedLocaleKeys() throws Exception {
        LocaleServiceImpl service = new LocaleServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));

        Field defaultLocaleField = LocaleServiceImpl.class.getDeclaredField("defaultLocale");
        defaultLocaleField.setAccessible(true);
        defaultLocaleField.set(service, "en");

        Field localesField = LocaleServiceImpl.class.getDeclaredField("locales");
        localesField.setAccessible(true);
        Map<String, YamlConfiguration> locales = (Map<String, YamlConfiguration>) localesField.get(service);

        YamlConfiguration config = new YamlConfiguration();
        config.set("minigame.arena.join.inactive", "Arena {arena} is not joinable right now.");
        locales.put("en", config);

        assertEquals(
            "Arena {arena} is not joinable right now.",
            service.resolve("en", "minigame.arena.join.inactive")
        );
    }

    @Test
    void resolveLeavesPlainTextUntouched() {
        LocaleServiceImpl service = new LocaleServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));

        assertEquals("Not craft", service.resolve("en", "Not craft"));
    }
}
