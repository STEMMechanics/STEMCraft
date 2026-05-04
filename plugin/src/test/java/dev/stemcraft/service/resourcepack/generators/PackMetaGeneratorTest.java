package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackMetaGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void buildStartWritesNewAndLegacyMetadataWhenRangeCrossesFormat65() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ResourcePackService service = mock(ResourcePackService.class);
        ConfigSectionView config = mock(ConfigSectionView.class);
        ConfigSection manifest = mock(ConfigSection.class);

        when(service.getConfig()).thenReturn(config);
        when(service.supportedVersion()).thenReturn(new int[] {32, 75});
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        File resourcePackDir = tempDir.toFile();
        new PackMetaGenerator(api, service).buildStart(manifest, resourcePackDir);

        JsonObject pack = readPack(resourcePackDir);
        assertEquals(32, pack.get("min_format").getAsInt());
        assertEquals(75, pack.get("max_format").getAsInt());
        assertEquals(64, pack.get("pack_format").getAsInt());

        JsonArray supportedFormats = pack.getAsJsonArray("supported_formats");
        assertEquals(32, supportedFormats.get(0).getAsInt());
        assertEquals(64, supportedFormats.get(1).getAsInt());
    }

    @Test
    void buildStartOmitsLegacyMetadataWhenOnlyNewFormatRangeIsSupported() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ResourcePackService service = mock(ResourcePackService.class);
        ConfigSectionView config = mock(ConfigSectionView.class);
        ConfigSection manifest = mock(ConfigSection.class);

        when(service.getConfig()).thenReturn(config);
        when(service.supportedVersion()).thenReturn(new int[] {65, 69});
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        File resourcePackDir = tempDir.resolve("new-format-only").toFile();
        assertTrue(resourcePackDir.mkdirs() || resourcePackDir.exists());
        new PackMetaGenerator(api, service).buildStart(manifest, resourcePackDir);

        JsonObject pack = readPack(resourcePackDir);
        assertEquals(65, pack.get("min_format").getAsInt());
        assertEquals(69, pack.get("max_format").getAsInt());
        assertFalse(pack.has("pack_format"));
        assertFalse(pack.has("supported_formats"));
    }

    @Test
    void buildStartWritesLegacyMetadataOnlyWhenTargetRangeIsPre1219() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ResourcePackService service = mock(ResourcePackService.class);
        ConfigSectionView config = mock(ConfigSectionView.class);
        ConfigSection manifest = mock(ConfigSection.class);

        when(service.getConfig()).thenReturn(config);
        when(service.supportedVersion()).thenReturn(new int[] {32, 64});
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        File resourcePackDir = tempDir.resolve("legacy-only").toFile();
        assertTrue(resourcePackDir.mkdirs() || resourcePackDir.exists());
        new PackMetaGenerator(api, service).buildStart(manifest, resourcePackDir);

        JsonObject pack = readPack(resourcePackDir);
        assertEquals(64, pack.get("pack_format").getAsInt());
        JsonArray supportedFormats = pack.getAsJsonArray("supported_formats");
        assertEquals(32, supportedFormats.get(0).getAsInt());
        assertEquals(64, supportedFormats.get(1).getAsInt());
        assertFalse(pack.has("min_format"));
        assertFalse(pack.has("max_format"));
    }

    private JsonObject readPack(File resourcePackDir) throws Exception {
        String raw = java.nio.file.Files.readString(resourcePackDir.toPath().resolve("pack.mcmeta"));
        return JsonParser.parseString(raw).getAsJsonObject().getAsJsonObject("pack");
    }
}
