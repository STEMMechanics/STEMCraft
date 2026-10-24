package dev.stemcraft.integration.pl3xmap;

import dev.stemcraft.STEMCraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Installs only STEMCraft's asset and script tag, preserving the current map page. */
final class Pl3xMapRegionWebFiles {
    private static final String SCRIPT = "stemcraft-regions.js";
    private static final Pattern OWN_TAG = Pattern.compile(
        "(?i)[ \\t]*<script\\b(?=[^>]*\\s+id\\s*=\\s*(?:\"stemcraft-regions\"|'stemcraft-regions'))[^>]*>\\s*</script>[ \\t]*(?:\\r?\\n)?");
    private static final Pattern BODY_END = Pattern.compile(
        "(?is)<!--.*?-->|<script\\b[^>]*>.*?</script\\s*>|(?<body></body\\s*>)");
    private final STEMCraft plugin;

    Pl3xMapRegionWebFiles(STEMCraft plugin) {
        this.plugin = plugin;
    }

    void install(Path directory) throws IOException {
        Path index = directory.resolve("index.html");
        if (!Files.isRegularFile(index) || !Files.isRegularFile(directory.resolve("pl3xmap.js")))
            throw new IOException("Pl3xMap's generated index.html/pl3xmap.js are missing from " + directory);
        String original = Files.readString(index, StandardCharsets.UTF_8);
        String page = OWN_TAG.matcher(original).replaceAll("");
        var body = BODY_END.matcher(page);
        int insertion = -1;
        while (body.find()) if (body.group("body") != null) { insertion = body.start(); break; }
        if (insertion < 0) throw new IOException("Pl3xMap's index.html has no closing body tag");
        int lineStart = page.lastIndexOf('\n', insertion - 1) + 1;
        if (page.substring(lineStart, insertion).isBlank()) insertion = lineStart;

        byte[] script;
        try (InputStream resource = plugin.getResource("pl3xmap/" + SCRIPT)) {
            if (resource == null) throw new IOException("Bundled named-region browser script is missing");
            script = resource.readAllBytes();
        }
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        String tag = "  <script id=\"stemcraft-regions\" src=\"" + SCRIPT + "?v=" + version(script)
            + "\"></script>" + newline;
        String updated = page.substring(0, insertion) + tag + page.substring(insertion);

        if (!original.equals(updated)) {
            Path backup = directory.resolve(".stemcraft-regions-backup/index.html");
            Files.createDirectories(backup.getParent());
            if (!Files.exists(backup)) Files.copy(index, backup);
        }
        NamedMapFiles.writeIfChanged(directory.resolve(SCRIPT), script);
        NamedMapFiles.writeIfChanged(index, updated.getBytes(StandardCharsets.UTF_8));
    }

    void uninstall(Path directory) throws IOException {
        Path index = directory.resolve("index.html");
        if (!Files.isRegularFile(index)) return;
        String original = Files.readString(index, StandardCharsets.UTF_8);
        String updated = OWN_TAG.matcher(original).replaceAll("");
        if (!original.equals(updated))
            NamedMapFiles.writeIfChanged(index, updated.getBytes(StandardCharsets.UTF_8));
    }

    private static String version(byte[] script) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script), 0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
