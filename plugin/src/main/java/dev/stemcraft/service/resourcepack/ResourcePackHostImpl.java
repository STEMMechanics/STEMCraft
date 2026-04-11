package dev.stemcraft.service.resourcepack;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSection;
import dev.stemcraft.api.service.resourcepack.ResourcePackHost;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Hosts the resource pack and serves it via an HTTP endpoint.
 */
public class ResourcePackHostImpl implements ResourcePackHost {
    private static final String DEFAULT_PACK_URL_PATH = "/resource-pack.zip";

    private final STEMCraftAPI api;
    private final ResourcePackServiceImpl service;

    private String packUrl;

    /**
     * Constructor for ResourcePackHost.
     *
     * @param api The STEMCraft API instance.
     */
    public ResourcePackHostImpl(STEMCraftAPI api, ResourcePackServiceImpl service) {
        this.api = api;
        this.service = service;
    }

    /**
     * Enables the ResourcePackHost with the given configuration.
     *
     * @param config The configuration section.
     */
    public void onEnable(ConfigSection config) {
        packUrl = config.getString("url", "");
        if (packUrl.isEmpty()) {
            packUrl = api.web().getPublicUrl() + DEFAULT_PACK_URL_PATH;
        }

        registerPackEndpoint();
    }

    /**
     * Gets the URL of the resource pack.
     *
     * @return The resource pack URL.
     */
    public @NotNull String getUrl() {
        String cacheBuster = "";

        File resourcePack = service.getResourcePack();
        if(resourcePack != null) {
            long lastModified = resourcePack.lastModified();

            if(packUrl.contains("?")) {
                cacheBuster = "&v=" + lastModified;
            } else {
                cacheBuster = "?v=" + lastModified;
            }
        }

        return packUrl + cacheBuster;
    }

    /**
     * Registers the HTTP endpoint for serving the resource pack.
     */
    private void registerPackEndpoint() {
        api.web().registerEndpointHandler(DEFAULT_PACK_URL_PATH, request -> {
            if (!"GET".equalsIgnoreCase(request.method())) {
                return Map.of(
                        "responseCode", 405,
                        "body", "Method Not Allowed"
                );
            }

            if (!request.path().equals(DEFAULT_PACK_URL_PATH)) {
                return Map.of(
                        "responseCode", 404,
                        "body", "File not found"
                );
            }

            File packZip = service.getResourcePack();
            if (packZip == null || !packZip.exists()) {
                return Map.of(
                        "responseCode", 503,
                        "body", "Resource pack not available"
                );
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("contentType", "application/zip");
            resp.put("file", packZip);
            return resp;
        });
    }
}
