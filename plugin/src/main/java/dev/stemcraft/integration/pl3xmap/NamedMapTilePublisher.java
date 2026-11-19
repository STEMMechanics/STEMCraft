package dev.stemcraft.integration.pl3xmap;

import java.nio.file.Path;

/** Publishes a static, read-only viewport snapshot for one Pl3xMap world. */
public interface NamedMapTilePublisher {
    boolean supports(String world);
    void refresh(String world, Path directory);
    void close();
}
