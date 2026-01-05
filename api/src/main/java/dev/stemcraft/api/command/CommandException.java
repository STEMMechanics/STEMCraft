package dev.stemcraft.api.command;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.util.PlaceholderUtil;
import lombok.Getter;

public class CommandException extends RuntimeException {
    /**
     * The messages to send to the command sender
     */
    @Getter
    private final String message;

    /**
     * Constructor
     */
    public CommandException() {
        super("");
        this.message = "";
    }

    /**
     * Create a new command exception with messages for the command sender
     */
    public CommandException(String message, Object... placeholders) {
        super("");
        this.message = PlaceholderUtil.apply(
                STEMCraftAPI.api().locales().resolve(message),
                placeholders
        );
    }
}