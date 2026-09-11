package dev.stemcraft.service.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.gift.GiftService;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import dev.stemcraft.api.service.mailbox.MailboxService;
import dev.stemcraft.api.service.player.PlayerService;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MiniGameGiftRewardsTest {
    @Test
    void appliesConfiguredDelayAndWinnerPlaceholders() {
        STEMCraftAPI api = mock(STEMCraftAPI.class);
        ConfigSectionView rewards = mock(ConfigSectionView.class);
        ConfigSectionView gifts = mock(ConfigSectionView.class);
        GiftService giftService = mock(GiftService.class);
        MailboxService mailboxes = mock(MailboxService.class);
        PlayerService players = mock(PlayerService.class);
        MiniGameArena arena = mock(MiniGameArena.class);
        UUID winnerUuid = UUID.randomUUID();
        ItemStack gift = mock(ItemStack.class);
        when(gift.clone()).thenReturn(gift);

        when(rewards.getBoolean("team-randomized", false)).thenReturn(true);
        when(rewards.getString("mail.from", "STEMCraft Minigames")).thenReturn("Games");
        when(rewards.getString("mail.message", "Congratulations on winning {minigame}! Here is your prize."))
            .thenReturn("Congrats {player} on winning {minigame} {arena}");
        when(rewards.getLong("mail.delay-ticks", 300L)).thenReturn(420L);
        when(rewards.getSection("gifts")).thenReturn(gifts);
        when(gifts.getKeys(false)).thenReturn(Set.of("0"));
        when(gifts.getStringList("0")).thenReturn(List.of("emerald,1"));
        when(api.gifts()).thenReturn(giftService);
        when(giftService.createGiftFromSpecs(List.of("emerald,1")))
            .thenReturn(gift);
        when(api.mailboxes()).thenReturn(mailboxes);
        when(api.players()).thenReturn(players);
        when(players.resolveIdentityByUuid(winnerUuid))
            .thenReturn(new PlayerService.ResolvedPlayer(winnerUuid, "nomadjimbob", "java"));
        when(arena.getName()).thenReturn("amazon");

        MiniGameGiftRewards rewardService = new MiniGameGiftRewards(api, "bedwars");
        rewardService.configure(rewards);
        rewardService.reward(arena, List.of(winnerUuid));

        ArgumentCaptor<MailSendRequest> request = ArgumentCaptor.forClass(MailSendRequest.class);
        verify(mailboxes).send(request.capture());
        assertEquals("Congrats nomadjimbob on winning bedwars amazon", request.getValue().message());
        assertEquals(420L, request.getValue().deliveryDelayTicks());
    }
}
