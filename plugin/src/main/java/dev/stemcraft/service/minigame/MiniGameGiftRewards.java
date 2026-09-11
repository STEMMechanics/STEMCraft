package dev.stemcraft.service.minigame;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.minigame.MiniGameArena;
import dev.stemcraft.api.service.player.PlayerService;
import dev.stemcraft.api.service.mailbox.MailSendRequest;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Parses and delivers one minigame's configured winner gifts. */
final class MiniGameGiftRewards {
    private final STEMCraftAPI api;
    private final String namespace;
    private List<List<String>> gifts = List.of();
    private boolean teamRandomized = true;
    private String sender = "STEMCraft Minigames";
    private String message = "Congratulations on winning {minigame}! Here is your prize.";
    private long deliveryDelayTicks = 300L;

    MiniGameGiftRewards(STEMCraftAPI api, String namespace) {
        this.api = api;
        this.namespace = namespace;
    }

    void configure(ConfigSectionView rewards) {
        gifts = List.of();
        if (rewards == null) return;
        teamRandomized = rewards.getBoolean("team-randomized", rewards.getBoolean("team_randomized", true));
        sender = rewards.getString("mail.from", "STEMCraft Minigames");
        message = rewards.getString("mail.message", "Congratulations on winning {minigame}! Here is your prize.");
        deliveryDelayTicks = rewards.getLong("mail.delay-ticks", 300L);
        if (deliveryDelayTicks < -1L) deliveryDelayTicks = 300L;
        ConfigSectionView giftSection = rewards.getSection("gifts");
        if (giftSection == null) return;
        List<String> keys = new ArrayList<>(giftSection.getKeys(false));
        keys.sort(Comparator.comparingInt(MiniGameGiftRewards::numericKey));
        List<List<String>> definitions = new ArrayList<>();
        for (String key : keys) {
            List<String> definition = giftSection.getStringList(key).stream().filter(value -> !value.isBlank()).toList();
            if (!definition.isEmpty()) definitions.add(definition);
        }
        gifts = List.copyOf(definitions);
    }

    void reward(MiniGameArena arena, Collection<UUID> winnerUuids) {
        if (winnerUuids == null || winnerUuids.isEmpty() || gifts.isEmpty() || api.mailboxes() == null) return;
        ItemStack shared = teamRandomized ? null : randomGift();
        for (UUID winnerUuid : new LinkedHashSet<>(winnerUuids)) {
            try {
                ItemStack gift = shared == null ? randomGift() : shared.clone();
                String playerName = winnerUuid.toString();
                PlayerService.ResolvedPlayer player = api.players() == null
                    ? null : api.players().resolveIdentityByUuid(winnerUuid);
                if (player != null) playerName = player.name();
                String renderedMessage = message
                    .replace("{player}", playerName)
                    .replace("{minigame}", namespace)
                    .replace("{arena}", arena == null ? "" : arena.getName());
                api.mailboxes().send(new MailSendRequest(sender, winnerUuid,
                    renderedMessage, List.of(gift), deliveryDelayTicks));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                api.messages().warn("Could not deliver " + namespace + " winner gift: " + exception.getMessage());
            }
        }
    }

    private ItemStack randomGift() {
        List<String> definition = gifts.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(gifts.size()));
        return api.gifts().createGiftFromSpecs(definition);
    }

    private static int numericKey(String key) {
        try { return Integer.parseInt(key); }
        catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }
}
