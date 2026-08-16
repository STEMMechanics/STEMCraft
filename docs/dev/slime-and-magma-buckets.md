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
