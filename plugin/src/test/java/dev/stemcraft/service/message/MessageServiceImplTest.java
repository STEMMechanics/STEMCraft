package dev.stemcraft.service.message;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.service.locale.LocaleService;
import dev.stemcraft.api.service.message.MessageType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceImplTest {
    @Test
    void parsesContextAndTypeDirectivesInSupportedForms() {
        Map<String, String> contexts = Map.of("survival", "prefix");

        for (String input : java.util.List.of(
            "/survival/ /info/<gold>You have mail",
            "/info//survival/<gold>You have mail",
            "/survival/info/<gold>You have mail"
        )) {
            MessageServiceImpl.RoutedMessage routed = MessageServiceImpl.parseDirectives(
                input, MessageType.PLAIN, null, contexts.keySet());
            assertEquals(MessageType.INFO, routed.type());
            assertEquals("survival", routed.context());
            assertEquals("<gold>You have mail", routed.message());
        }
    }

    @Test
    void laterDirectivesOverrideEarlierOnes() {
        MessageServiceImpl.RoutedMessage routed = MessageServiceImpl.parseDirectives(
            "/warn//info/Message", MessageType.PLAIN, null, java.util.Set.of());

        assertEquals(MessageType.INFO, routed.type());
        assertEquals("Message", routed.message());
    }

    @Test
    void unknownContextLeavesMessageUnchanged() {
        MessageServiceImpl.RoutedMessage routed = MessageServiceImpl.parseDirectives(
            "/unknown//info/Message", MessageType.PLAIN, null, java.util.Set.of("survival"));

        assertEquals(MessageType.PLAIN, routed.type());
        assertNull(routed.context());
        assertEquals("/unknown//info/Message", routed.message());
    }

    @Test
    void contextVisibilityUsesRequestedImplicitDefaults() {
        MessageServiceImpl.MessageContext noRules = context(null);
        MessageServiceImpl.MessageContext hideOnly = new MessageServiceImpl.MessageContext(
            "prefix", java.util.List.of(), java.util.List.of(),
            java.util.List.of("survival*"), java.util.List.of(), null);
        MessageServiceImpl.MessageContext showOnly = new MessageServiceImpl.MessageContext(
            "prefix", java.util.List.of(), java.util.List.of("stemcraft.third"),
            java.util.List.of(), java.util.List.of(), null);
        MessageServiceImpl.MessageContext both = new MessageServiceImpl.MessageContext(
            "prefix", java.util.List.of(), java.util.List.of("stemcraft.always"),
            java.util.List.of("survival*"), java.util.List.of(), null);

        assertEquals(true, noRules.visibleTo("world", permission -> false));
        assertEquals(false, hideOnly.visibleTo("survival_nether", permission -> false));
        assertEquals(true, hideOnly.visibleTo("lobby", permission -> false));
        assertEquals(false, showOnly.visibleTo("world", permission -> false));
        assertEquals(true, showOnly.visibleTo("world", "stemcraft.third"::equals));
        assertEquals(true, both.visibleTo("lobby", permission -> false));
    }

    @Test
    void showRulesOverrideHideRulesAndExplicitDefaultOverridesImplicitDefault() {
        MessageServiceImpl.MessageContext bothMatch = new MessageServiceImpl.MessageContext(
            "prefix", java.util.List.of(), java.util.List.of("stemcraft.always"),
            java.util.List.of("survival*"), java.util.List.of(), false);
        MessageServiceImpl.MessageContext explicitShow = new MessageServiceImpl.MessageContext(
            "prefix", java.util.List.of("special*"), java.util.List.of(),
            java.util.List.of(), java.util.List.of(), true);

        assertEquals(true, bothMatch.visibleTo("survival", "stemcraft.always"::equals));
        assertEquals(false, bothMatch.visibleTo("lobby", permission -> false));
        assertEquals(true, explicitShow.visibleTo("lobby", permission -> false));
    }

    @Test
    void worldPatternsAreCaseInsensitiveGlobs() {
        assertEquals(true, MessageServiceImpl.MessageContext.globMatches("survival*", "Survival_Nether"));
        assertEquals(true, MessageServiceImpl.MessageContext.globMatches("*_minigame", "BRIDGE_MINIGAME"));
        assertEquals(false, MessageServiceImpl.MessageContext.globMatches("survival*", "lobby"));
    }

    private static MessageServiceImpl.MessageContext context(Boolean explicitDefault) {
        return new MessageServiceImpl.MessageContext(
            "prefix", java.util.List.of(), java.util.List.of(),
            java.util.List.of(), java.util.List.of(), explicitDefault);
    }

    @Test
    void broadcastConsoleFormatUsesAsciiTag() {
        String rendered = PlainTextComponentSerializer.plainText().serialize(
            MessageServiceImpl.formatBroadcastConsoleMessage("The player nomadjimbob is no longer banned.")
        );

        assertEquals("[broadcast] The player nomadjimbob is no longer banned.", rendered);
    }

    @Test
    void broadcastPlayerFormatKeepsConfiguredPrefix() {
        String rendered = PlainTextComponentSerializer.plainText().serialize(
            MessageServiceImpl.formatBroadcastPlayerMessage("<dark_purple><bold>[ ! ]</bold></dark_purple> ", "The player nomadjimbob is no longer banned.")
        );

        assertEquals("[ ! ] The player nomadjimbob is no longer banned.", rendered);
    }

    @Test
    void placeholderValuesRemainRawMessageData() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        LocaleService locales = mock(LocaleService.class);
        when(api.locales()).thenReturn(locales);
        MessageServiceImpl service = new MessageServiceImpl(mock(STEMCraft.class), api);

        assertEquals(
            "Cooking recipe loaded: furnace.dried_fruit",
            service.applyPlaceholders(null, "Cooking recipe loaded: {type}.{id}",
                "type", "furnace", "id", "dried_fruit")
        );
        org.mockito.Mockito.verifyNoInteractions(locales);
    }

    @Test
    void bracedPlaceholderValuesAreLocalizedExplicitly() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        LocaleService locales = mock(LocaleService.class);
        when(api.locales()).thenReturn(locales);
        when(locales.resolve((CommandSender) null, "furnace")).thenReturn("Furnace");
        when(locales.resolve((CommandSender) null, "dried_fruit")).thenReturn("Dried Fruit");
        MessageServiceImpl service = new MessageServiceImpl(mock(STEMCraft.class), api);

        assertEquals(
            "Cooking recipe loaded: Furnace.Dried Fruit",
            service.applyPlaceholders(null, "Cooking recipe loaded: {type}.{id}",
                "type", "{furnace}", "id", "{dried_fruit}")
        );
        verify(locales).resolve((CommandSender) null, "furnace");
        verify(locales).resolve((CommandSender) null, "dried_fruit");
    }
}
