package dev.stemcraft.api.minigame;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class ArenaValidationResult {
    private boolean valid;
    private List<Error> errors;

    /**
     * Error class representing a validation error.
     */
    static class Error {
        @Getter
        private final String message;
        @Getter
        private final String field;

        public Error(String message, String field) {
            this.message = message;
            this.field = field;
        }
    }

    /**
     * Constructor
     */
    public ArenaValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }

    /**
     * Create a successful validation result.
     *
     * @return A successful ArenaValidationResult instance.
     */
    public static ArenaValidationResult success() {
        return new ArenaValidationResult();
    }

    /**
     * Create a failed validation result with a single error.
     *
     * @param message The error message.
     * @param field   The field associated with the error.
     * @return A failed ArenaValidationResult instance.
     */
    public static ArenaValidationResult failure(String message, String field) {
        ArenaValidationResult result = new ArenaValidationResult();

        result.valid = false;
        result.errors.add(new Error(message, field));
        return result;
    }

    /**
     * Add an error to the validation result.
     *
     * @param message The error message.
     * @param field   The field associated with the error.
     */
    public void addError(String message, String field) {
        this.valid = false;
        this.errors.add(new Error(message, field));
    }

    /**
     * Check if the validation result has errors.
     *
     * @return True if there are errors, false otherwise.
     */
    public boolean hasErrors() {
        return !valid;
    }

    /**
     * Get the list of error messages.
     *
     * @return A list of error messages.
     */
    public List<String> getErrors() {
        List<String> errorMessages = new ArrayList<>();
        for (Error error : errors) {
            errorMessages.add(error.getMessage());
        }
        return errorMessages;
    }
}
