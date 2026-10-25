package dev.stemcraft.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerLoginMessageServiceTest {
    @Test
    void loginCategoriesAreOrderedWelcomeThenAwayThenOther() {
        Map<String, PlayerLoginMessageServiceImpl.Priority> categories = new LinkedHashMap<>();
        categories.put("other", PlayerLoginMessageServiceImpl.Priority.OTHER);
        categories.put("away-summary", PlayerLoginMessageServiceImpl.Priority.AWAY_SUMMARY);
        categories.put("welcome", PlayerLoginMessageServiceImpl.Priority.WELCOME);

        assertEquals(
            List.of("welcome", "away-summary", "other"),
            PlayerLoginMessageServiceImpl.orderedCategories(categories)
        );
    }
}
