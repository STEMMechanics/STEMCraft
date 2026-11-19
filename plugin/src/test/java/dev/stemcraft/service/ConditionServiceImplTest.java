package dev.stemcraft.service;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.condition.ConditionContext;
import dev.stemcraft.api.service.condition.ConditionProvider;
import dev.stemcraft.STEMCraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ConditionServiceImplTest {
    @Test
    void validatesMissingProvidersAndMatchesRangesListsAndNestedConditions() {
        ConditionServiceImpl service = new ConditionServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        service.onEnable();
        ConditionContext context = new ConditionContext(null, null, null, null, "minecraft:forest",
            "ZOMBIE", "NATURAL", 2, Map.of("world", "workshop-test", "player-kill", true));

        assertTrue(service.validate(Map.of("distance-tier", 2), "rule").stream()
            .anyMatch(error -> error.contains("missing condition provider")));
        service.register(new ConditionProvider() {
            @Override public String id() { return "distance-tier"; }
            @Override public List<String> validate(Object value, String path) { return List.of(); }
            @Override public boolean matches(Object value, ConditionContext valueContext) {
                return ConditionServiceImpl.matchesValue(valueContext.distanceTier(), value);
            }
        });

        Map<String, Object> conditions = Map.of(
            "all", List.of(
                Map.of("world", "workshop-test"),
                Map.of("distance-tier", Map.of("min", 2, "max", 3)),
                Map.of("biome", List.of("minecraft:forest", "minecraft:plains"))
            ),
            "player-kill", true
        );
        assertTrue(service.validate(conditions, "rule").isEmpty());
        assertTrue(service.matches(conditions, context));
        assertFalse(service.matches(Map.of("distance-tier", Map.of("min", 3)), context));
    }

    @Test
    void rejectsDuplicateProviderIds() {
        ConditionServiceImpl service = new ConditionServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        service.register(new Provider("custom"));
        assertThrows(IllegalArgumentException.class, () -> service.register(new Provider("CUSTOM")));
    }

    @Test
    void worldProviderUsesExplicitContextValueWhenNoLocationExists() {
        ConditionServiceImpl service = new ConditionServiceImpl(mock(STEMCraft.class), mock(STEMCraftAPI.class));
        service.onEnable();
        ConditionContext context = new ConditionContext(null, null, null, null, "", "", "", 0,
            Map.of("world", "workshop-test"));
        assertTrue(service.matches(Map.of("world", "workshop-test"), context));
        assertEquals(List.of(), service.validate(Map.of("world", "workshop-test"), "rule"));
    }

    private record Provider(String id) implements ConditionProvider {
        @Override public List<String> validate(Object value, String path) { return List.of(); }
        @Override public boolean matches(Object value, ConditionContext context) { return true; }
    }
}
