# Rotten Flesh Uses and Poppy Luring

`RottenFleshUsesFeature` owns the runtime behaviour. Item and recipe definitions live in the bundled
`stemcraft-rotten-flesh` data pack so the existing custom-item generator emits both Java resource-pack models
and Geyser custom-item mappings.

## Items and recipes

- Rotten Flesh composting is an interaction mechanic, not a crafting recipe. A normal Rotten Flesh item raises
  the Composter by exactly one level, producing the normal ready state after eight insertions into an empty
  Composter. Vanilla extraction supplies the Bone Meal and resets the block. Custom items backed by
  `ROTTEN_FLESH`, including Zombie Bait, are explicitly excluded.
- `dog-treat` uses `BONE` as its backing material, which prevents player eating. Interacting with a tamed wolf
  performs server-authoritative healing or starts vanilla love mode when the wolf is already healthy and can breed.
- `zombie-bait` uses `ROTTEN_FLESH` as its backing material, but player consumption is cancelled.
- `rotten-flesh-stew` uses `SUSPICIOUS_STEW`; its Night Vision and Hunger effects are applied from the consume
  event and therefore behave identically for Java and Bedrock clients without changing vanilla flower stews.
- The Leather, Dog Treat, Zombie Bait, and Rotten Flesh Stew recipes are shapeless and registered through
  `RecipeServiceImpl`.

## Zombie attraction

The feature never scans every entity in every world. Every update it scans around online players who actually
carry bait and around tracked dropped bait item UUIDs. Dropped bait UUIDs are populated by item-spawn and
chunk-load events, then removed on consumption, pickup, despawn, chunk unload, or invalidation.

Carried bait explicitly acquires player targets outside vanilla follow range without permanently changing the
zombie's attributes. Hand bait uses the configured 2× multiplier and inventory bait uses 1.5×. A target assigned
only because of bait is cleared outside normal range when the bait is removed.

Dropped bait takes precedence over carried-player assignments. Paper's normal `Pathfinder` navigates to the
dropped `Item`; no block, teleport, or DisplayEntity is involved. One item is removed when the first zombie is
within the configured consumption distance. Pathfinder assignments are stopped and maps cleared for all
documented removal and lifecycle cases.

## Manual cross-platform checks

Test crafting and item appearances after regenerating both resource packs. Then cover wolf healing/breeding,
both carried-bait ranges, dropped stacks and every removal case, stew effects, teleport, world change,
disconnect, and chunk unload.
