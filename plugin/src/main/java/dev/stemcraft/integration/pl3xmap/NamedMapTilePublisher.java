package dev.stemcraft.integration.pl3xmap;

import java.nio.file.Path;
import java.util.function.LongConsumer;

/** Optional map bridge; tile generation itself has no dependency on Pl3xMap classes. */
public interface NamedMapTilePublisher {
    boolean supports(String world);
    void refresh(String world, Path directory, LongConsumer published);
    void close();
}
