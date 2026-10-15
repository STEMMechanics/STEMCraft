package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.STEMCraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Pl3xMapRegionWebFilesTest {
    @TempDir Path web;
    private static final String SCRIPT = "console.log('bundled regions');";

    @Test void installsBundledScriptAndPreservesThemeAndOriginalPageBackup() throws Exception {
        String original = "<html><body><div id=\"map\"></div><script src=\"stemcraft-theme.js\"></script></body></html>";
        page(original);
        installer().install(web);
        String updated = Files.readString(web.resolve("index.html"));
        assertTrue(updated.contains("<script src=\"stemcraft-theme.js\"></script>"));
        assertTrue(updated.contains("<div id=\"map\"></div>"));
        assertTrue(updated.matches("(?s).*src=\"stemcraft-regions.js\\?v=[0-9a-f]{12}\".*"));
        assertFalse(updated.contains("data-endpoint="));
        assertTrue(updated.indexOf("id=\"stemcraft-regions\"") < updated.indexOf("</body>"));
        assertEquals(SCRIPT, Files.readString(web.resolve("stemcraft-regions.js")));
        assertEquals(original, Files.readString(web.resolve(".stemcraft-regions-backup/index.html")));
    }

    @Test void repeatedInstallsKeepOneTagAndDoNotTouchUnchangedFilesOrBackup() throws Exception {
        String original = "<html>\r\n<body>\r\n  </body>\r\n</html>";
        page(original);
        var installer = installer();
        installer.install(web);
        Path index = web.resolve("index.html"), script = web.resolve("stemcraft-regions.js");
        String first = Files.readString(index);
        var timestamp = FileTime.fromMillis(1000);
        Files.setLastModifiedTime(index, timestamp);
        Files.setLastModifiedTime(script, timestamp);
        installer.install(web);
        installer.install(web);
        assertEquals(first, Files.readString(index));
        assertEquals(timestamp, Files.getLastModifiedTime(index));
        assertEquals(timestamp, Files.getLastModifiedTime(script));
        assertEquals(1, first.split("id=\"stemcraft-regions\"", -1).length - 1);
        assertFalse(first.replace("\r\n", "").contains("\n"));
        assertEquals(original, Files.readString(web.resolve(".stemcraft-regions-backup/index.html")));
    }

    @Test void updatesOldOrDuplicateTagsWithoutRemovingOtherScriptsOrChangingBackup() throws Exception {
        String original = "<html><body><script id='stemcraft-regions' src='old.js'></script>"
            + "<script data-id='stemcraft-regions' src='keep.js'></script>"
            + "<script src='another-old.js' id=\"stemcraft-regions\"></script></body></html>";
        page(original);
        installer().install(web);
        String first = Files.readString(web.resolve("index.html"));
        assertFalse(first.contains("src='old.js'"));
        assertFalse(first.contains("another-old.js"));
        assertTrue(first.contains("<script data-id='stemcraft-regions' src='keep.js'></script>"));
        installer().install(web);
        assertEquals(first, Files.readString(web.resolve("index.html")));
        assertEquals(original, Files.readString(web.resolve(".stemcraft-regions-backup/index.html")));
    }

    @Test void restoresTagAfterPl3xMapRegeneratesIndexAndVersionsChangedScript() throws Exception {
        String original = "<html><body>Original map</body></html>";
        page(original);
        var content = new AtomicReference<>(SCRIPT);
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource("pl3xmap/stemcraft-regions.js")).thenAnswer(invocation ->
            new ByteArrayInputStream(content.get().getBytes(StandardCharsets.UTF_8)));
        var installer = new Pl3xMapRegionWebFiles(plugin);
        installer.install(web);
        String first = Files.readString(web.resolve("index.html"));
        content.set("console.log('updated regions');");
        String regenerated = "<html><body>Updated Pl3xMap<script src='new-theme.js'></script></body></html>";
        Files.writeString(web.resolve("index.html"), regenerated);
        installer.install(web);
        String updated = Files.readString(web.resolve("index.html"));
        assertTrue(updated.contains("Updated Pl3xMap<script src='new-theme.js'></script>"));
        assertFalse(updated.contains("data-endpoint="));
        assertNotEquals(first.substring(first.indexOf("?v="), first.indexOf("?v=") + 15),
            updated.substring(updated.indexOf("?v="), updated.indexOf("?v=") + 15));
        assertEquals(content.get(), Files.readString(web.resolve("stemcraft-regions.js")));
        assertEquals(original, Files.readString(web.resolve(".stemcraft-regions-backup/index.html")));
    }

    @Test void removesObsoleteEndpointAndSkipsClosingBodyTextInCommentsAndInlineScripts() throws Exception {
        page("<html><body><script>const text = '</body>';</script><!-- </body> -->Actual"
            + "<script id='stemcraft-regions' src='old.js' data-endpoint='/obsolete'></script></body></html>");
        new Pl3xMapRegionWebFiles(plugin()).install(web);
        String updated = Files.readString(web.resolve("index.html"));
        assertTrue(updated.contains("const text = '</body>';"));
        assertTrue(updated.contains("<!-- </body> -->Actual  <script id=\"stemcraft-regions\""));
        assertFalse(updated.contains("data-endpoint="));
    }

    @Test void rejectsMissingFilesOrMalformedIndexWithoutOverwritingThePage() throws Exception {
        var installer = installer();
        assertThrows(IOException.class, () -> installer.install(web));
        assertFalse(Files.exists(web.resolve("index.html")));
        page("<html>Custom incomplete page");
        assertThrows(IOException.class, () -> installer.install(web));
        assertEquals("<html>Custom incomplete page", Files.readString(web.resolve("index.html")));
        assertFalse(Files.exists(web.resolve("stemcraft-regions.js")));
        assertFalse(Files.exists(web.resolve(".stemcraft-regions-backup")));
    }

    @Test void missingBundledScriptOrFailedAssetWriteLeavesIndexUnchanged() throws Exception {
        String original = "<html><body>Original</body></html>";
        page(original);
        var missing = new Pl3xMapRegionWebFiles(mock(STEMCraft.class));
        assertThrows(IOException.class, () -> missing.install(web));
        assertEquals(original, Files.readString(web.resolve("index.html")));
        Files.createDirectory(web.resolve("stemcraft-regions.js"));
        assertThrows(IOException.class, () -> installer().install(web));
        assertEquals(original, Files.readString(web.resolve("index.html")));
    }

    @Test void preservesIndexPermissionsAndMakesNewScriptReadableToWebServers() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(web.getFileSystem().supportedFileAttributeViews().contains("posix"));
        page("<html><body>Original</body></html>");
        Set<PosixFilePermission> permissions = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);
        Files.setPosixFilePermissions(web.resolve("index.html"), permissions);
        installer().install(web);
        assertEquals(permissions, Files.getPosixFilePermissions(web.resolve("index.html")));
        assertTrue(Files.getPosixFilePermissions(web.resolve("stemcraft-regions.js")).contains(PosixFilePermission.OTHERS_READ));
    }

    @Test void updatesLinkedIndexWithoutReplacingTheLink() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(web.getFileSystem().supportedFileAttributeViews().contains("posix"));
        page("<html><body>Shared theme</body></html>");
        Path shared = web.resolve("shared-index.html");
        Files.move(web.resolve("index.html"), shared);
        Files.createSymbolicLink(web.resolve("index.html"), shared.getFileName());
        installer().install(web);
        assertTrue(Files.isSymbolicLink(web.resolve("index.html")));
        assertTrue(Files.readString(shared).contains("id=\"stemcraft-regions\""));
        assertTrue(Files.readString(shared).contains("Shared theme"));
    }

    private void page(String content) throws IOException {
        Files.writeString(web.resolve("index.html"), content);
        Files.writeString(web.resolve("pl3xmap.js"), "// Pl3xMap");
    }
    private Pl3xMapRegionWebFiles installer() { return new Pl3xMapRegionWebFiles(plugin()); }
    private STEMCraft plugin() {
        var plugin = mock(STEMCraft.class);
        when(plugin.getResource("pl3xmap/stemcraft-regions.js")).thenAnswer(invocation ->
            new ByteArrayInputStream(SCRIPT.getBytes(StandardCharsets.UTF_8)));
        return plugin;
    }
}
