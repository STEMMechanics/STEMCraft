/*
 * STEMCraft - Minecraft Plugin
 * Copyright (C) 2026 James Collins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author STEMMechanics
 * @link https://github.com/STEMMechanics/STEMCraft
 */

package dev.stemcraft.service.region;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import dev.stemcraft.api.model.RegionFlagData;
import dev.stemcraft.api.model.RegionScopedData;
import dev.stemcraft.api.model.SCManagedRegion;
import dev.stemcraft.api.service.region.RegionDataExtension;
import dev.stemcraft.api.service.region.RegionService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Self-contained managed-region extension for boolean region flags.
 */
public class RegionFlagExtension implements RegionDataExtension<RegionFlagData> {
    public static final String KEY = RegionFlagData.KEY;

    private static final List<String> DEFAULT_FLAG_KEYS = List.of(
        "break-block",
        "place-block",
        "item-use",
        "inventory-modify",
        "interact",
        "pvp"
    );

    private RegionService regionService;

    /**
     * Creates a new region flag extension.
     */
    public RegionFlagExtension() { }

    @Override
    public void onEnable(@NotNull STEMCraftAPI api, @NotNull RegionService service) {
        this.regionService = service;
        api.tabComplete().register("managed-region-flags", (sender, args) -> DEFAULT_FLAG_KEYS);
        api.events().register(BlockBreakEvent.class, this::handleBlockBreak, EventPriority.HIGH, true);
        api.events().register(BlockPlaceEvent.class, this::handleBlockPlace, EventPriority.HIGH, true);
        api.events().register(PlayerInteractEvent.class, this::handlePlayerInteract, EventPriority.HIGH, true);
        api.events().register(PlayerInteractEntityEvent.class, this::handlePlayerInteractEntity, EventPriority.HIGH, true);
        api.events().register(PlayerInteractAtEntityEvent.class, this::handlePlayerInteractAtEntity, EventPriority.HIGH, true);
        api.events().register(PlayerBucketEmptyEvent.class, this::handleBucketEmpty, EventPriority.HIGH, true);
        api.events().register(PlayerBucketFillEvent.class, this::handleBucketFill, EventPriority.HIGH, true);
        api.events().register(InventoryClickEvent.class, this::handleInventoryClick, EventPriority.HIGH, true);
        api.events().register(InventoryDragEvent.class, this::handleInventoryDrag, EventPriority.HIGH, true);
        api.events().register(PlayerDropItemEvent.class, this::handlePlayerDropItem, EventPriority.HIGH, true);
        api.events().register(EntityPickupItemEvent.class, this::handleEntityPickupItem, EventPriority.HIGH, true);
        api.events().register(EntityDamageByEntityEvent.class, this::handleEntityDamageByEntity, EventPriority.HIGH, true);
    }

    @Override
    public @NotNull String key() {
        return KEY;
    }

    @Override
    public @NotNull Class<RegionFlagData> type() {
        return RegionFlagData.class;
    }

    @Override
    public @NotNull String description() {
        return "Boolean allow or deny region flags.";
    }

    @Override
    public @NotNull String commandKey() {
        return RegionFlagData.COMMAND_KEY;
    }

    @Override
    public @NotNull List<String[]> setTabCompletions() {
        return List.of(
            new String[]{"{managed-region-flags}", "allow"},
            new String[]{"{managed-region-flags}", "deny"}
        );
    }

    @Override
    public @NotNull List<String[]> getTabCompletions() {
        return List.of(
            new String[]{},
            new String[]{"{managed-region-flags}"}
        );
    }

    @Override
    public @NotNull List<String[]> clearTabCompletions() {
        return List.of(
            new String[]{},
            new String[]{"{managed-region-flags}"}
        );
    }

    @Override
    public void onSet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        ctx.checkArgsSizeAtLeast(2, "/region set <id> flag <flag-key> <allow|deny> [g:<scope>]");

        String flagKey = normalizeFlagKey(ctx.getArg(0));
        boolean value = parseFlagValue(ctx.getArg(1), ctx);
        RegionFlagData settings = getOrCreate(region, scope);
        settings.setFlag(flagKey, value);
        set(region, scope, settings);
        ctx.returnSuccess("Region flag '" + flagKey + "' updated for '" + region.getId() + "'" + scopeSuffix(scope) + ".");
    }

    @Override
    public void onGet(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        if (scope != null) {
            RegionFlagData settings = get(region, scope);
            if (settings == null || settings.isEmpty()) {
                ctx.returnInfo("Region '" + region.getId() + "' has no region flags for scope '" + scope + "'.");
            }
            RegionFlagData scopedSettings = Objects.requireNonNull(settings, "settings");

            if (ctx.numArgs() == 0) {
                ctx.info("Region flags for '" + region.getId() + "' [" + scope + "]:");
                scopedSettings.flags().forEach((flagKey, value) -> ctx.info(" - " + flagKey + ": " + value));
                return;
            }

            String flagKey = normalizeFlagKey(ctx.getArg(0));
            if (!scopedSettings.hasFlag(flagKey)) {
                ctx.returnInfo("Region flag '" + flagKey + "' for '" + region.getId() + "' [" + scope + "]: (unset)");
            }
            ctx.returnInfo("Region flag '" + flagKey + "' for '" + region.getId() + "' [" + scope + "]: " + scopedSettings.getFlag(flagKey));
        }

        Map<String, RegionFlagData> scopedData = getAll(region);
        if (scopedData.isEmpty()) {
            ctx.returnInfo("Region '" + region.getId() + "' has no region flags.");
        }

        if (ctx.numArgs() == 0) {
            ctx.info("Region flags for '" + region.getId() + "':");
            scopedData.forEach((entryScope, data) -> ctx.info(" - " + entryScope + ": " + describeFlagMap(data)));
            return;
        }

        String flagKey = normalizeFlagKey(ctx.getArg(0));
        List<String> lines = new ArrayList<>();
        scopedData.forEach((entryScope, data) -> {
            if (data.hasFlag(flagKey)) {
                lines.add(entryScope + "=" + data.getFlag(flagKey));
            }
        });
        if (lines.isEmpty()) {
            ctx.returnInfo("Region flag '" + flagKey + "' for '" + region.getId() + "': (unset)");
        }
        ctx.returnInfo("Region flag '" + flagKey + "' for '" + region.getId() + "': " + String.join(", ", lines));
    }

    @Override
    public void onClear(@NotNull CommandContext ctx, @NotNull SCManagedRegion region, @Nullable String scope) {
        if (scope != null) {
            RegionFlagData settings = get(region, scope);
            if (settings == null || settings.isEmpty()) {
                ctx.returnInfo("Region '" + region.getId() + "' has no region flags for scope '" + scope + "'.");
            }
            RegionFlagData scopedSettings = Objects.requireNonNull(settings, "settings");

            if (ctx.numArgs() == 0) {
                remove(region, scope);
                ctx.returnSuccess("Region flags cleared for '" + region.getId() + "'" + scopeSuffix(scope) + ".");
            }

            String flagKey = normalizeFlagKey(ctx.getArg(0));
            scopedSettings.removeFlag(flagKey);
            persist(region, scope, scopedSettings);
            ctx.returnSuccess("Region flag '" + flagKey + "' cleared for '" + region.getId() + "'" + scopeSuffix(scope) + ".");
        }

        if (ctx.numArgs() == 0) {
            remove(region, null);
            ctx.returnSuccess("Region flags cleared for '" + region.getId() + "'.");
        }

        String flagKey = normalizeFlagKey(ctx.getArg(0));
        Map<String, RegionFlagData> scopedData = getAll(region);
        if (scopedData.isEmpty()) {
            ctx.returnInfo("Region '" + region.getId() + "' has no region flags.");
        }

        scopedData.forEach((entryScope, data) -> {
            data.removeFlag(flagKey);
            persist(region, RegionScopedData.ALL.equals(entryScope) ? null : entryScope, data);
        });
        ctx.returnSuccess("Region flag '" + flagKey + "' cleared for '" + region.getId() + "'.");
    }

    @Override
    public @NotNull List<String> describe(@NotNull SCManagedRegion region) {
        Map<String, RegionFlagData> scopedData = getAll(region);
        if (scopedData.isEmpty()) {
            return List.of();
        }

        List<String> out = new ArrayList<>();
        scopedData.forEach((scope, data) -> out.add(scope + ": " + describeFlagMap(data)));
        return out;
    }

    /**
     * Returns the flag payload for the given region and scope, creating it when missing.
     *
     * @param region The managed region to read or update.
     * @param scope The optional audience scope.
     * @return The region flag payload.
     */
    public @NotNull RegionFlagData getOrCreate(@NotNull SCManagedRegion region, @Nullable String scope) {
        RegionFlagData settings = get(region, scope);
        if (settings != null) {
            return settings;
        }

        RegionFlagData created = new RegionFlagData();
        set(region, scope, created);
        return created;
    }

    private void persist(@NotNull SCManagedRegion region, @Nullable String scope, @NotNull RegionFlagData settings) {
        if (settings.isEmpty()) {
            remove(region, scope);
            return;
        }
        set(region, scope, settings);
    }

    private void handleBlockBreak(@NotNull BlockBreakEvent event) {
        if (isDenied(event.getPlayer(), event.getBlock().getLocation(), "break-block")) {
            event.setCancelled(true);
        }
    }

    private void handleBlockPlace(@NotNull BlockPlaceEvent event) {
        if (isDenied(event.getPlayer(), event.getBlockPlaced().getLocation(), "place-block")) {
            event.setCancelled(true);
        }
    }

    private void handlePlayerInteract(@NotNull PlayerInteractEvent event) {
        Location location = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : event.getPlayer().getLocation();
        if (isInteractionAction(event.getAction()) && isDenied(event.getPlayer(), location, "interact")) {
            event.setCancelled(true);
            return;
        }

        if (hasUsableItem(event) && isDenied(event.getPlayer(), location, "item-use")) {
            event.setCancelled(true);
        }
    }

    private void handlePlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handlePlayerInteractAtEntity(@NotNull PlayerInteractAtEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleBucketEmpty(@NotNull PlayerBucketEmptyEvent event) {
        Location location = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        if (isDenied(event.getPlayer(), location, "item-use") || isDenied(event.getPlayer(), location, "place-block")) {
            event.setCancelled(true);
        }
    }

    private void handleBucketFill(@NotNull PlayerBucketFillEvent event) {
        if (isDenied(event.getPlayer(), event.getBlockClicked().getLocation(), "item-use")) {
            event.setCancelled(true);
        }
    }

    private void handleInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Location location = event.getClickedInventory() != null && event.getClickedInventory().getLocation() != null
            ? event.getClickedInventory().getLocation()
            : player.getLocation();
        if (isDenied(player, location, "inventory-modify")) {
            event.setCancelled(true);
        }
    }

    private void handleInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Location location = event.getView().getTopInventory().getLocation() != null
            ? event.getView().getTopInventory().getLocation()
            : player.getLocation();
        if (isDenied(player, location, "inventory-modify")) {
            event.setCancelled(true);
        }
    }

    private void handlePlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (isDenied(event.getPlayer(), event.getPlayer().getLocation(), "inventory-modify")) {
            event.setCancelled(true);
        }
    }

    private void handleEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (isDenied(player, player.getLocation(), "inventory-modify")) {
            event.setCancelled(true);
        }
    }

    private void handleEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) {
            return;
        }

        if (isDenied(attacker, victim.getLocation(), "pvp") || isDenied(attacker, attacker.getLocation(), "pvp")) {
            event.setCancelled(true);
        }
    }

    private void handleEntityInteraction(@NotNull Player player, @NotNull Entity target, @NotNull Cancellable cancellable) {
        Location location = target.getLocation();
        if (isDenied(player, location, "interact")) {
            cancellable.setCancelled(true);
            return;
        }

        if (player.getInventory().getItemInMainHand().getType() != Material.AIR && isDenied(player, location, "item-use")) {
            cancellable.setCancelled(true);
        }
    }

    private boolean isDenied(@NotNull Player player, @Nullable Location location, @NotNull String flagKey) {
        RegionFlagData settings = resolveScopedData(player, location);
        return settings != null && Boolean.FALSE.equals(settings.getFlag(flagKey));
    }

    private @Nullable RegionFlagData resolveScopedData(@NotNull Player player, @Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        for (SCManagedRegion region : getRegionMatches(location)) {
            RegionFlagData settings = get(region, player);
            if (settings != null && !settings.isEmpty()) {
                return settings;
            }
        }

        return null;
    }

    private @NotNull Iterable<SCManagedRegion> getRegionMatches(@NotNull Location location) {
        return regionService.getManagedRegionsAt(location);
    }

    private @NotNull String normalizeFlagKey(@NotNull String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean parseFlagValue(@NotNull String raw, @NotNull CommandContext ctx) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("allow") || value.equals("true")) {
            return true;
        }
        if (value.equals("deny") || value.equals("false")) {
            return false;
        }
        ctx.returnError("Invalid flag value '" + raw + "'. Expected allow or deny.");
        return false;
    }

    private @NotNull String describeFlagMap(@NotNull RegionFlagData data) {
        List<String> out = new ArrayList<>();
        data.flags().forEach((flagKey, value) -> out.add(flagKey + "=" + (Boolean.TRUE.equals(value) ? "allow" : "deny")));
        return out.isEmpty() ? "(unset)" : String.join(", ", out);
    }

    private @NotNull String scopeSuffix(@Nullable String scope) {
        return scope == null ? "" : " [" + scope + "]";
    }

    private boolean isInteractionAction(@NotNull Action action) {
        return action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK || action == Action.PHYSICAL;
    }

    private boolean hasUsableItem(@NotNull PlayerInteractEvent event) {
        return event.getItem() != null && event.getItem().getType() != Material.AIR;
    }

    private @Nullable Player resolvePlayerDamager(@NotNull Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
