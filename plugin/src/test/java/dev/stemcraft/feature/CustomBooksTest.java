package dev.stemcraft.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomBooksTest {
    @Test
    void generateBookNamePreservesExistingHyphens() {
        assertEquals("server-help", CustomBooks.generateBookName("server-help"));
    }

    @Test
    void generateBookNameConvertsWhitespaceToSingleHyphens() {
        assertEquals("server-help", CustomBooks.generateBookName(" Server   Help "));
    }

    @Test
    void generateBookNameStripsUnsupportedCharactersWithoutDroppingHyphens() {
        assertEquals("server-help-2", CustomBooks.generateBookName("Server-help! #2"));
    }
}
