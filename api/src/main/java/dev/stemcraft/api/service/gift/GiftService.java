package dev.stemcraft.api.service.gift;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;
import java.util.List;

/** Creates portable gifts containing one or more item stacks. */
public interface GiftService {
    @NotNull ItemStack createGift(@NotNull List<ItemStack> contents);
    @NotNull ItemStack createGiftFromSpecs(@NotNull List<String> specifications);
    @Nullable ItemStack createItem(@NotNull String specification);
    @NotNull List<ItemStack> contents(@Nullable ItemStack gift);
    boolean isGift(@Nullable ItemStack item);
}
