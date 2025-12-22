package dev.stemcraft.api.commands;

import dev.stemcraft.api.STEMCraftAPI;
import lombok.Getter;

public class STEMCraftCommandException extends RuntimeException {
    /**
     * The messages to send to the command sender
     */
    @Getter
    private final String message;

    /**
     * Constructor
     */
    public STEMCraftCommandException() {
        super("");
        this.message = "";
    }

    /**
     * Create a new command exception with messages for the command sender
     */
    public STEMCraftCommandException(String message, Object... placeholders) {
        super("");
        this.message = STEMCraftAPI.api().locale().get(message, placeholders);
    }
}