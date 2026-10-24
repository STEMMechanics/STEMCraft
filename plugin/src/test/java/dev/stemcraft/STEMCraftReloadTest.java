package dev.stemcraft;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.service.ChatServiceImpl;
import dev.stemcraft.service.LocaleServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class STEMCraftReloadTest {
    @Test
    void fullReloadRefreshesTheChatService() throws Exception {
        STEMCraft plugin = mock(STEMCraft.class, CALLS_REAL_METHODS);
        STEMCraftAPI api = mock(STEMCraftAPI.class, RETURNS_DEEP_STUBS);
        ConfigFile config = mock(ConfigFile.class);
        RecordingChatService chat = new RecordingChatService();
        LocaleServiceImpl locales = mock(LocaleServiceImpl.class);

        when(api.config().load("config.yml")).thenReturn(config);
        when(config.reload()).thenReturn(true);
        when(config.getBoolean("debug", false)).thenReturn(false);
        setStaticField("api", api);
        setField(plugin, "configFile", config);
        setField(plugin, "chat", chat);
        setField(plugin, "locales", locales);
        setField(plugin, "loadedFeatures", new ArrayList<>());

        STEMCraft.ReloadSummary summary = plugin.reloadStemCraft(false);

        assertTrue(summary.configReloaded());
        assertTrue(chat.reloaded);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = STEMCraft.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = STEMCraft.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static final class RecordingChatService extends ChatServiceImpl {
        private boolean reloaded;

        private RecordingChatService() {
            super(null, null);
        }

        @Override
        public void onReload() {
            reloaded = true;
        }
    }
}
