package dev.stemcraft.service.resourcepack.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import dev.stemcraft.api.service.resourcepack.ResourcePackBuildContext;
import dev.stemcraft.api.service.resourcepack.ResourcePackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

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
        when(service.supportedRange()).thenReturn(new PackFormatRange(32, 75));
        when(service.buildPlan()).thenReturn(List.of(
            new ResourcePackBuildContext(new PackFormatRange(32, 75), 75, false, null)
        ));
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        File resourcePackDir = tempDir.toFile();
        new PackMetaGenerator(api, service).buildStart(
            new ResourcePackBuildContext(new PackFormatRange(32, 75), 75, false, null),
            manifest,
            resourcePackDir
        );

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
        when(service.supportedRange()).thenReturn(new PackFormatRange(65, 69));
        when(service.buildPlan()).thenReturn(List.of(
            new ResourcePackBuildContext(new PackFormatRange(65, 69), 69, false, null)
        ));
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        File resourcePackDir = tempDir.resolve("new-format-only").toFile();
        assertTrue(resourcePackDir.mkdirs() || resourcePackDir.exists());
        new PackMetaGenerator(api, service).buildStart(
            new ResourcePackBuildContext(new PackFormatRange(65, 69), 69, false, null),
            manifest,
            resourcePackDir
        );

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
        when(service.supportedRange()).thenReturn(new PackFormatRange(32, 64));
        when(service.buildPlan()).thenReturn(List.of(
            new ResourcePackBuildContext(new PackFormatRange(32, 64), 64, false, null)
        ));
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        File resourcePackDir = tempDir.resolve("legacy-only").toFile();
        assertTrue(resourcePackDir.mkdirs() || resourcePackDir.exists());
        new PackMetaGenerator(api, service).buildStart(
            new ResourcePackBuildContext(new PackFormatRange(32, 64), 64, false, null),
            manifest,
            resourcePackDir
        );

        JsonObject pack = readPack(resourcePackDir);
        assertEquals(64, pack.get("pack_format").getAsInt());
        JsonArray supportedFormats = pack.getAsJsonArray("supported_formats");
        assertEquals(32, supportedFormats.get(0).getAsInt());
        assertEquals(64, supportedFormats.get(1).getAsInt());
        assertFalse(pack.has("min_format"));
        assertFalse(pack.has("max_format"));
    }

    @Test
    void buildStartWritesOverlayEntriesFromBuildPlan() throws Exception {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ResourcePackService service = mock(ResourcePackService.class);
        ConfigSectionView config = mock(ConfigSectionView.class);
        ConfigSection manifest = mock(ConfigSection.class);

        when(service.getConfig()).thenReturn(config);
        when(service.supportedRange()).thenReturn(new PackFormatRange(64, 75));
        when(service.buildPlan()).thenReturn(List.of(
            new ResourcePackBuildContext(new PackFormatRange(64, 75), 75, false, null),
            new ResourcePackBuildContext(new PackFormatRange(80, 82), 82, true, "overlay_80_82")
        ));
        when(config.getString("description", "A STEMCraft Resource Pack")).thenReturn("STEMCraft");

        File resourcePackDir = tempDir.resolve("with-overlay").toFile();
        assertTrue(resourcePackDir.mkdirs() || resourcePackDir.exists());
        new PackMetaGenerator(api, service).buildStart(
            new ResourcePackBuildContext(new PackFormatRange(64, 75), 75, false, null),
            manifest,
            resourcePackDir
        );

        JsonObject root = readRoot(resourcePackDir);
        JsonObject overlays = root.getAsJsonObject("overlays");
        JsonArray entries = overlays.getAsJsonArray("entries");
        JsonObject entry = entries.get(0).getAsJsonObject();
        assertEquals("overlay_80_82", entry.get("directory").getAsString());
        assertEquals(80, entry.get("min_format").getAsInt());
        assertEquals(82, entry.get("max_format").getAsInt());
    }

    private JsonObject readPack(File resourcePackDir) throws Exception {
        return readRoot(resourcePackDir).getAsJsonObject("pack");
    }

    private JsonObject readRoot(File resourcePackDir) throws Exception {
        String raw = java.nio.file.Files.readString(resourcePackDir.toPath().resolve("pack.mcmeta"));
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
