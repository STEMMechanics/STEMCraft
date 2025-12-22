package dev.stemcraft.exceptions;

import dev.stemcraft.STEMCraft;

/**
 * Represents our core exception.
 */
public class STEMCraftException extends RuntimeException {

    /**
     * Create a new exception.
     */
    public STEMCraftException() {
        STEMCraft.getInstance().error(this.getMessage(), this);
    }

    /**
     * Create a new exception.
     * @param t
     */
    public STEMCraftException(Throwable t) {
        super(t);
        STEMCraft.getInstance().error(this.getMessage(), this);
    }

    /**
     * Create a new exception.
     * @param message
     */
    public STEMCraftException(String message) {
        super(message);
        STEMCraft.getInstance().error(message);
    }

    /**
     * Create a new exception.
     * @param message
     * @param t
     */
    public STEMCraftException(String message, Throwable t) {
        super(message, t);
        STEMCraft.getInstance().error(message, t);
    }
}
