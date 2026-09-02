package dev.stemcraft.api.service.gift;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;
import java.util.List;

/** Creates portable gifts containing one or more item stacks. */
public interface GiftService {
    /**
     * Creates an unstackable Gift containing cloned copies of the supplied items.
     *
     * @param contents item stacks to place in the Gift
     * @return the created Gift item
     * @example Creating a Gift
     * {@code
     * ItemStack gift = api.gifts().createGift(List.of(
     *     new ItemStack(Material.EMERALD, 3),
     *     new ItemStack(Material.COOKED_BEEF, 5)
     * ));
     * }
     */
    @NotNull ItemStack createGift(@NotNull List<ItemStack> contents);

    /**
     * Creates a Gift from compact item specifications.
     *
     * @param specifications item IDs with optional fixed or ranged quantities
     * @return the created Gift item
     * @example Creating a randomized Gift
     * {@code
     * ItemStack gift = api.gifts().createGiftFromSpecs(List.of(
     *     "emerald,1-3",
     *     "stemcraft:animal_crate[animal=chicken]"
     * ));
     * }
     */
    @NotNull ItemStack createGiftFromSpecs(@NotNull List<String> specifications);

    /**
     * Creates one item stack from a compact item specification.
     *
     * @param specification vanilla or custom item ID with an optional quantity
     * @return the created item, or {@code null} when the specification is invalid
     */
    @Nullable ItemStack createItem(@NotNull String specification);

    /**
     * Reads cloned contents from a Gift.
     *
     * @param gift item to inspect
     * @return cloned contents, or an empty list when the item is not a Gift
     */
    @NotNull List<ItemStack> contents(@Nullable ItemStack gift);

    /**
     * Checks whether an item is a STEMCraft Gift.
     *
     * @param item item to inspect
     * @return {@code true} when the item is a Gift
     */
    boolean isGift(@Nullable ItemStack item);
}
