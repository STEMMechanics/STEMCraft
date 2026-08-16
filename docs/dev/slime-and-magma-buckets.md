# Slime and Magma Buckets

The `stemcraft-slime-bucket` data pack owns both item definitions, their recipes, and the supplied textures.
The logical item IDs are `slime-bucket` and `magma-cube-bucket`.

`SlimeBuckets` uses Bukkit's `Chunk#isSlimeChunk()` result as the authoritative detector. It updates only when
a player crosses a chunk boundary or an event can change either held item: login, teleport, world change,
respawn, held-slot changes, hand swaps, inventory clicks/drags, pickup, or drop. Inventory buckets are first
normalized, then main-hand and off-hand buckets receive the `excited` visual state when appropriate.

Custom items support config-defined `visual-states`. `ItemService#applyCustomItemVisualState` changes only the
Java item model and custom-model-data components, preserving the logical custom-item ID, amount, name, lore,
enchantments, and other components. Passing `null` restores the default state.

The resource-pack generator emits Java models for every visual state. The excited texture's `.png.mcmeta`
loops its two vertical frames. Bedrock generation emits a separate Geyser model mapping for the state and
adds vertically stacked custom-item textures to `textures/flipbook_textures.json`.

Bedrock model changes force an inventory resynchronization. Because Bedrock item-atlas animation support can
vary with the active Geyser/client version, Bedrock holders also receive an action-bar message and slime sound
when the authoritative state changes.

Java clients briefly play their normal hand re-equip animation when the item model changes. This can look as
though the bucket lowers or drops, but no item entity is spawned and the server never removes the bucket from
the player's hand.

The public handbook uses copies of the supplied item textures in `docs/pub/.gitbook/assets`. Its excited GIF
is generated directly from the two 16×16 frames in `slime_bucket_excited.png`; it is documentation artwork
only and is not bundled into the game resource pack.
