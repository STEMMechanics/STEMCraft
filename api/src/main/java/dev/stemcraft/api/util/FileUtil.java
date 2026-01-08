package dev.stemcraft.api.util;

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;

public class FileUtil {
    /**
     * Copies a single file from {@code src} to {@code dest}, creating parent directories as needed.
     * Existing files are replaced.
     */
    public static void copyFile(Path src, Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /**
     * Copies a directory from {@code srcDir} to {@code destDir}, creating parent directories as needed.
     * If {@code overwrite} is {@code true}, existing files will be replaced.
     *
     * @param srcDir The source directory.
     * @param destDir The destination directory.
     * @param overwrite Whether to overwrite existing files.
     */
    public static void copyDirectory(Path srcDir, Path destDir, boolean overwrite) throws IOException {
        if (!Files.exists(srcDir)) return;

        CopyOption[] copyOptions = overwrite
                ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES}
                : new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES};

        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            /** {@inheritDoc} */
            @Override
            public @NonNull FileVisitResult preVisitDirectory(@NonNull Path dir,
                                                              @NonNull BasicFileAttributes attrs) throws IOException {
                Path rel = srcDir.relativize(dir);
                Files.createDirectories(destDir.resolve(rel));
                return FileVisitResult.CONTINUE;
            }

            /** {@inheritDoc} */
            @Override
            public @NonNull FileVisitResult visitFile(@NonNull Path file,
                                                      @NonNull BasicFileAttributes attrs) throws IOException {
                Path rel = srcDir.relativize(file);
                Path target = destDir.resolve(rel);
                Files.createDirectories(target.getParent());

                if (!overwrite && Files.exists(target)) {
                    return FileVisitResult.CONTINUE;
                }

                Files.copy(file, target, copyOptions);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Computes the SHA-1 hash of a file and returns it as a hexadecimal string.
     *
     * @param file the file to compute the hash for.
     * @return the SHA-1 hash as a hexadecimal string.
     */
    public static String sha1Hex(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-1 for " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Deletes a file or directory recursively.
     *
     * @param f the file or directory to delete.
     * @throws IOException if an I/O error occurs.
     */
    public static void deleteRecursive(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        if (!f.delete()) {
            throw new IOException("Failed to delete " + f);
        }
    }

    /**
     * Recursively gets the latest modified timestamp of a file or directory.
     *
     * @param file the file or directory.
     * @return the latest modified timestamp in milliseconds since epoch.
     */
    public long getLatestModified(File file) {
        long latest = file.lastModified();
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    latest = Math.max(latest, getLatestModified(child));
                }
            }
        }
        return latest;
    }
}