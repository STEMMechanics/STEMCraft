package dev.stemcraft.service.tabcompletion;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TabCompleteServiceImplTest {
    private ServerMock server;
    private TabCompleteServiceImpl service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("tab-tests");
        server.addSimpleWorld("extra-world");
        server.addPlayer("Viewer");
        server.addPlayer("Other");

        STEMCraft plugin = mock(STEMCraft.class);
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        service = new TabCompleteServiceImpl(plugin, api);
        when(api.tabComplete()).thenReturn(service);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersCustomCompletionsAndReturnsEmptyListForUnknownProviders() {
        Player viewer = viewer();
        service.register("custom", (player, args) -> List.of("one", "two"));

        assertEquals(List.of("one", "two"), service.getCompletionList("custom", viewer));
        assertEquals(List.of(), service.getCompletionList("missing", viewer));
    }

    @Test
    void onEnableRegistersCoreTabCompletions() {
        Player viewer = viewer();
        service.onEnable();

        assertTrue(service.getCompletionList("duration", viewer).contains("1m"));
        assertTrue(service.getCompletionList("world", viewer).containsAll(List.of("tab-tests", "extra-world")));
        assertTrue(service.getCompletionList("gamemode", viewer).contains("survival"));
        assertTrue(service.getCompletionList("int", viewer).contains("100"));
        assertTrue(service.getCompletionList("player", viewer).containsAll(List.of("Viewer", "Other")));
    }

    @Test
    void entityAndMobCompletionsDistinguishMobsFromOtherEntities() {
        service.onEnable();
        List<String> entities = service.getCompletionList("entity", viewer());
        List<String> mobs = service.getCompletionList("mob", viewer());

        assertTrue(entities.containsAll(List.of("PLAYER", "ARMOR_STAND", "ARROW", "OAK_BOAT", "ZOMBIE")));
        assertFalse(entities.contains("UNKNOWN"));
        assertTrue(mobs.containsAll(List.of("ZOMBIE", "COW", "ENDER_DRAGON", "SULFUR_CUBE")));
        for (String nonMob : List.of("PLAYER", "ARMOR_STAND", "MANNEQUIN", "ARROW", "OAK_BOAT", "UNKNOWN")) {
            assertFalse(mobs.contains(nonMob), nonMob);
        }
        assertEquals(entities.stream().sorted().toList(), entities);
        assertEquals(mobs.stream().sorted().toList(), mobs);
    }

    @Test
    void behaviourCompletionsSeparateNeutralEnemiesAndPassiveUndeadMounts() {
        service.onEnable();
        List<String> hostile = service.getCompletionList("hostile", viewer());
        List<String> neutral = service.getCompletionList("neutral", viewer());
        List<String> passive = service.getCompletionList("passive", viewer());

        assertTrue(hostile.containsAll(List.of("ZOMBIE", "CREEPER", "SLIME", "HOGLIN", "ENDER_DRAGON", "PIGLIN_BRUTE")));
        assertTrue(neutral.containsAll(List.of("ENDERMAN", "ZOMBIFIED_PIGLIN", "SPIDER", "WOLF", "IRON_GOLEM", "NAUTILUS", "ZOMBIE_NAUTILUS")));
        assertTrue(passive.containsAll(List.of("COW", "VILLAGER", "GIANT", "ZOMBIE_HORSE", "CAMEL_HUSK", "SULFUR_CUBE")));

        List<String> categorized = java.util.stream.Stream.of(hostile, neutral, passive)
            .flatMap(List::stream).sorted().toList();
        // Every mob belongs to exactly one category, with no non-mob suggestions.
        assertEquals(service.getCompletionList("mob", viewer()), categorized);
    }

    @Test
    void materialCompletionsDistinguishBlocksFromInventoryItems() {
        service.onEnable();
        List<String> materials = service.getCompletionList("material", viewer());
        List<String> blocks = service.getCompletionList("block", viewer());
        List<String> items = service.getCompletionList("item", viewer());

        assertTrue(materials.containsAll(List.of("minecraft:water", "minecraft:chest", "minecraft:diamond_sword")));
        assertTrue(blocks.containsAll(List.of("minecraft:water", "minecraft:chest")));
        assertFalse(blocks.contains("minecraft:diamond_sword"));
        assertTrue(items.containsAll(List.of("minecraft:chest", "minecraft:diamond_sword")));
        assertFalse(items.contains("minecraft:water"));
        assertEquals(materials.stream().distinct().sorted().toList(), materials);
    }

    @Test
    void registryCompletionsProvideNamespacedKeys() {
        service.onEnable();
        // MockBukkit's keyStream does not initialize its lazy registry contents.
        for (var key : List.of(RegistryKey.BIOME, RegistryKey.MOB_EFFECT, RegistryKey.ENCHANTMENT,
                RegistryKey.SOUND_EVENT, RegistryKey.PARTICLE_TYPE)) {
            RegistryAccess.registryAccess().getRegistry(key).iterator();
        }
        java.util.Map<String, String> examples = java.util.Map.of(
            "biome", "minecraft:plains",
            "effect", "minecraft:speed",
            "enchantment", "minecraft:sharpness",
            "sound", "minecraft:entity.player.levelup",
            "particle", "minecraft:flame");
        examples.forEach((provider, example) -> {
            List<String> suggestions = service.getCompletionList(provider, viewer());
            assertTrue(suggestions.contains(example), provider);
            assertEquals(suggestions.stream().distinct().sorted().toList(), suggestions, provider);
        });
    }

    @Test
    void scalarCompletionsAndOverridesRemainReusable() {
        service.onEnable();
        assertEquals(List.of("true", "false"), service.getCompletionList("boolean", viewer()));
        assertEquals(List.of("peaceful", "easy", "normal", "hard"), service.getCompletionList("difficulty", viewer()));
        assertEquals(service.getCompletionList("int", viewer()), service.getCompletionList("number", viewer()));

        service.register("int", (player, args) -> List.of("64"));
        assertEquals(List.of("64"), service.getCompletionList("number", viewer()));
        service.register("item", (player, args) -> List.of("minecraft:chest", "stemcraft:gift"));
        assertEquals(List.of("minecraft:chest", "stemcraft:gift"), service.getCompletionList("item", viewer()));
    }

    private Player viewer() {
        return Objects.requireNonNull(server.getPlayer("Viewer"));
    }
}
