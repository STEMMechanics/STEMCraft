package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.message.TokenProcessor;
import dev.stemcraft.api.service.message.MessageService;
import dev.stemcraft.service.placeholderapi.PlaceholderApiSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceholderServiceImplTest {
    private PlaceholderServiceImpl service;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        STEMCraft plugin = mock(STEMCraft.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("PlaceholderServiceImplTest"));

        STEMCraftAPI api = mock(STEMCraftAPI.class);
        MessageService messages = mock(MessageService.class);
        TokenProcessor tokens = mock(TokenProcessor.class);
        when(api.messages()).thenReturn(messages);
        when(messages.tokens()).thenReturn(tokens);
        when(tokens.apply("Hello {name}")).thenReturn("Hello token");
        when(tokens.apply("One")).thenReturn("One");
        when(tokens.apply("Two")).thenReturn("Two");

        service = new PlaceholderServiceImpl(plugin, api);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void applyReturnsInputForNullOrEmptyText() {
        assertNull(service.apply(null, (String) null));
        assertEquals("", service.apply(null, ""));
        assertFalse(service.isAvailable());
    }

    @Test
    void applyUsesTokenProcessorAndBridgeWhenBridgeIsPresent() throws Exception {
        PlaceholderApiSupport bridge = mock(PlaceholderApiSupport.class);
        when(bridge.apply(null, "Hello token")).thenReturn("Hello bridge");
        when(bridge.apply(null, "One")).thenReturn("One");
        when(bridge.apply(null, "Two")).thenReturn("Two");
        injectBridge(bridge);

        assertTrue(service.isAvailable());
        assertEquals("Hello bridge", service.apply(null, "Hello {name}"));
        assertEquals(List.of("One", "Two"), service.apply(null, List.of("One", "Two")));

        service.onDisable();
        verify(bridge).disable();
        assertFalse(service.isAvailable());
    }

    private void injectBridge(PlaceholderApiSupport bridge) throws Exception {
        Field field = PlaceholderServiceImpl.class.getDeclaredField("bridge");
        field.setAccessible(true);
        field.set(service, bridge);
    }
}
