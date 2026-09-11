package dev.stemcraft.feature.stembot;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import dev.stemcraft.api.service.config.ConfigService;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotSkinCacheTest {
    @TempDir
    File folder;

    @Test
    void explicitTextureDoesNotLoadLegacyCacheOrConvert() {
        STEMCraftAPI api=mock(STEMCraftAPI.class);
        BotScript script=mock(BotScript.class);
        when(script.skinValue()).thenReturn("configured-value");
        when(script.skinSignature()).thenReturn("configured-signature");
        var ready=new AtomicReference<BotSkinCache.Skin>();
        var skins=new BotSkinCache();

        skins.load(api,mock(Plugin.class),script,ready::set);

        assertEquals(new BotSkinCache.Skin("configured-signature","configured-value"),ready.get());
        assertEquals(ready.get(),skins.current());
        verifyNoInteractions(api);
    }

    @Test
    void migratesMatchingLegacyCacheIntoMainConfiguration() throws Exception {
        migrate(false);
    }

    @Test
    void preservesTextureEditedSinceScriptWasLoaded() throws Exception {
        migrate(true);
    }

    private void migrate(boolean edited) throws Exception {
        STEMCraftAPI api=mock(STEMCraftAPI.class);
        ConfigService configs=mock(ConfigService.class);
        ConfigFile legacy=mock(ConfigFile.class);
        ConfigFile config=mock(ConfigFile.class);
        BotScript script=mock(BotScript.class);
        File legacyFile=new File(folder,"stembot-skin-cache.yml");
        assertTrue(legacyFile.createNewFile());
        when(api.config()).thenReturn(configs);
        when(api.getDataFolder()).thenReturn(folder);
        when(script.skinValue()).thenReturn("");
        when(script.skinSignature()).thenReturn("");
        when(script.skinUrl()).thenReturn("https://example.com/skin.png");
        when(configs.load("stembot-skin-cache.yml",false)).thenReturn(legacy);
        when(legacy.getString("source","")).thenReturn("https://example.com/skin.png|false");
        when(legacy.getString("value","")).thenReturn("generated-value");
        when(legacy.getString("signature","")).thenReturn("generated-signature");
        when(legacy.getString("value")).thenReturn("generated-value");
        when(legacy.getString("signature")).thenReturn("generated-signature");
        when(configs.load(new File(folder,"stembot.yml"),false)).thenReturn(config);
        when(config.reload()).thenReturn(true);
        when(config.getString("skin.url","")).thenReturn("https://example.com/skin.png");
        when(config.getString("skin.texture.value","")).thenReturn(edited ? "new-value" : "");
        when(config.getString("skin.texture.signature","")).thenReturn(edited ? "new-signature" : "");
        var ready=new AtomicReference<BotSkinCache.Skin>();

        new BotSkinCache().load(api,mock(Plugin.class),script,ready::set);

        assertEquals(new BotSkinCache.Skin("generated-signature","generated-value"),ready.get());
        verify(config).reload();
        if(edited) {
            verify(config,never()).set(anyString(),any());
            verify(config,never()).save();
        } else {
            verify(config).set("skin.texture.value","generated-value");
            verify(config).set("skin.texture.signature","generated-signature");
            verify(config).save();
        }
        verify(legacy,never()).save();
        assertEquals(edited,legacyFile.exists());
        verify(configs,never()).load(anyString());
    }
}
