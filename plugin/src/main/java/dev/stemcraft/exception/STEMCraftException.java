package dev.stemcraft.exception;

import dev.stemcraft.STEMCraft;

/**
 * Represents our core exception.
 */
public class STEMCraftException extends RuntimeException {

    /**
     * Create a new exception.
     */
    public STEMCraftException() {
        STEMCraft.getPlugin().error(this.getMessage(), this);
    }

    /**
     * Create a new exception.
     * @param t
     */
    public STEMCraftException(Throwable t) {
        super(t);
        STEMCraft.getPlugin().error(this.getMessage(), this);
    }

    /**
     * Create a new exception.
     * @param message
     */
    public STEMCraftException(String message) {
        super(message);
        STEMCraft.getPlugin().error(message);
    }

    /**
     * Create a new exception.
     * @param message
     * @param t
     */
    public STEMCraftException(String message, Throwable t) {
        super(message, t);
        STEMCraft.getPlugin().error(message, t);
    }
}
