package dev.stemcraft.api.service.resourcepack;

import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.generator.ResourcePackGenerator;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.function.Consumer;

/**
 * Service for managing the resource pack.
 */
public interface ResourcePackService {

    /**
     * Retrieves the resource pack configuration.
     *
     * @return The configuration section view for the resource pack.
     */
    ConfigSectionView getConfig();

    /**
     * Retrieves the resource pack file.
     *
     * @return The resource pack file.
     */
    File getResourcePack();

    /**
     * Retrieves the resource pack hash as a hexadecimal string.
     *
     * @return The resource pack hash.
     */
    String getResourcePackHash();

    /**
     * Retrieves the resource pack host.
     *
     * @return The resource pack host.
     */
    ResourcePackHost host();

    /**
     * Registers a resource pack generator.
     *
     * @param generator The resource pack generator to register.
     */
    void registerGenerator(ResourcePackGenerator generator);

    /**
     * Sends the resource pack to the specified player.
     *
     * @param player The player to send the resource pack to.
     */
    void sendPack(Player player);

    /**
     * Sends the resource pack to all online players.
     */
    void sendPackToAll();

    /**
     * Generates the resource pack, providing status updates via the callback.
     *
     * @param statusCallback A consumer to receive status updates during generation.
     */
    void generatePack(Consumer<String> statusCallback);
}