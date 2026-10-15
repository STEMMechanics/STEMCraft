package dev.stemcraft.integration.pl3xmap;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;

/** Atomic public map-file updates, shared by the browser installer and tile publisher. */
public final class NamedMapFiles {
    private NamedMapFiles() {}

    public static void writeIfChanged(Path target, byte[] bytes) throws IOException {
        if (Files.isSymbolicLink(target)) target = target.toRealPath();
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) return;
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
        try {
            Files.write(temporary, bytes);
            if (target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                var permissions = Files.exists(target) ? Files.getPosixFilePermissions(target) : Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);
                Files.setPosixFilePermissions(temporary, permissions);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
