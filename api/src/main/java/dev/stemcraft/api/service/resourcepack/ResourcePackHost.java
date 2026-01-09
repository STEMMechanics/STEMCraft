package dev.stemcraft.api.service.resourcepack;

import org.jetbrains.annotations.NotNull;

/**
 * Hosts the resource pack and serves it via an HTTP endpoint.
 */
public interface ResourcePackHost {

    /**
     * Gets the URL of the resource pack.
     *
     * @return The resource pack URL.
     */
    @NotNull String getUrl();
}
