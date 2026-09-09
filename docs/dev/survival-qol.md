# Survival quality of life

The survival QOL feature provides small configurable improvements without changing the core survival progression.

Each player-facing behaviour has an optional `permission` beside its `enabled` setting. A blank or missing value means no permission is required. The bundled defaults use profession-backed entitlements for convenience features while leaving accident prevention and warnings available immediately. For crop protection and stronger leads, entities without a relevant player remain protected. When powered-minecart permission is configured, a cart accelerates only while an authorized player is riding it.

| Convenience unlock | Default requirement | Permission |
| --- | --- | --- |
| Vein mining | Mining Level 10 (8,100 XP), diamond/netherite pickaxe | `stemcraft.qol.vein-mining` |
| Tree felling | Herbalism Level 10 (8,100 XP), diamond/netherite axe | `stemcraft.qol.tree-felling` |
| Automatic replacement for broken tools | Mining Level 3 (400 XP) | `stemcraft.qol.auto-refill-tools` |
| 3×3 hoe harvesting | Farming Level 3 (400 XP) | `stemcraft.qol.hoe-harvest` |
| Automatic stack refill | Engineering Level 3 (400 XP) | `stemcraft.qol.auto-refill` |
| Stronger leads | Farming Level 4 (900 XP) | `stemcraft.qol.stronger-leads` |
| Faster powered minecarts | Engineering Level 5 (1,600 XP) | `stemcraft.qol.powered-minecarts` |
| Named-mob information, when enabled | Farming Level 5 (1,600 XP) | `stemcraft.qol.named-mob-info` |

Crop-trampling protection, durability warnings, and anvil warnings remain ungated.

## Farming and inventory

- Sneak-right-click a mature crop with any hoe to harvest and replant a 3×3 area of the same crop.
- Players and mobs cannot trample farmland into dirt.
- When the held stack is consumed or placed, an identical stack from the main inventory automatically moves into that hand. Item metadata must match, preventing named or custom items from being mixed.
- When a hand-held tool breaks, another tool of exactly the same material automatically moves from storage into that hand. A stone pickaxe can only select another stone pickaxe; tools of other tiers are never substituted. The replacement keeps its own durability, enchantments, name, and other metadata.
- Disconnected, non-persistent leaves are queued for accelerated random ticks. `leaf_decay_random_tick.default_tick_speed` controls how many candidates are processed per server tick; a world `leaf-decay-tickspeed` setting overrides it.
- Naturally grown trees harvested by players schedule one matching sapling within the configured random offset. Planting waits until nearby players leave and is cancelled when constructed blocks indicate that the land is being repurposed. Pending replacements survive restarts. Breaking a sapling never schedules another replacement, including saplings planted by this feature.

## Transport and animals

- Distance-based lead breaks are prevented. Manual unleashing and holder removal still work normally.
- Minecarts accelerate while on powered rails. `powered-minecarts.multiplier` controls acceleration and `max-speed` caps it.
- Right-clicking a named mob displays its name, type, health, and owner. Using a name tag records the tagging player when the mob has no native tame owner.

## Equipment

- Anvils warn at the configured level before the vanilla 40-level `Too Expensive` limit. The old `no_anvil_repair_cost` feature is disabled by default so this warning is meaningful.
- Tools and armour warn when an incoming durability loss crosses 10% and again at 2%. Both thresholds and messages are configurable.

## Fried eggs

Cook a vanilla egg in a furnace, smoker, or over a campfire to make a Fried Egg. The registered egg campfire recipe is detected automatically, making right-click insert the otherwise throwable egg into an empty campfire slot. It has its own Java and Bedrock model and behaves as food, restoring three hunger points and 2.4 saturation by default. The item and all three recipes are declared together in the survival data-pack config, using `result: stemcraft:fried_egg`. Administrators can obtain it with `/give @s stemcraft:fried_egg`.

## Animal crates

Right-click a chicken, rabbit, frog, or cat with an empty Animal Crate to capture it. For compatibility, an ordinary vanilla barrel also works when sneak-right-clicking. The carrier becomes an unstackable `Animal Crate (Chicken)`, for example. Right-click a block with the filled crate to release it into the adjacent space; it then returns to an ordinary barrel. Filled Animal Crates cannot be placed as storage blocks.

Animal Crates preserve the animal's custom name, baby/adult state, tame owner, and species variant where applicable. Tamed animals can only be captured by their owner unless the player has `stemcraft.animalbarrel.others`. Animal Crates are deliberately unstackable.

## End dragon

After an Ender Dragon dies in an eligible End world, a persistent respawn task initiates another fight after `dragon-respawn.days`. The task survives server restarts. Set `dragon-respawn.worlds` to restrict the feature to selected End worlds.

All settings and player-facing messages are under `survival-qol`, `animal-barrels`, `custom-items`, `leaf_decay_random_tick`, and `dragon-respawn` in `config.yml`.

Admins can create a populated Animal Crate with `/give <player> stemcraft:animal_crate[animal=chicken]`.
Supported animals are `chicken`, `rabbit`, `frog`, and `cat`. Omitting the property gives an empty Animal Crate.

## Tree felling and vein mining

Sneak while breaking a log or ore to use these unlocks. `/qol tree-felling off` and `/qol vein-mining off` disable them individually. `require-sneaking`, `tools`, and `max-blocks` are configurable under each feature in `survival-qol`.

Tree felling follows connected logs of the same species, including diagonal branches, at or above the height of the original cut. It requires nearby non-persistent leaves and excludes stripped logs and wood blocks. Connected trees of the same species can be included. The default limit is 128 logs, including the original block. Leaves use the existing decay behavior. Herbalism now awards 4 XP for breaking unplaced logs; placed logs are tracked to prevent repeated placement/breaking from awarding XP.

Vein mining follows connected blocks of exactly the same ore material in all directions, including diagonals. Normal and deepslate variants are separate veins. The default limit is 64 blocks, including the original ore. Neither feature loads neighboring chunks. Additional breaks use the player's tool and normal break events, retaining enchantments, durability, XP collection and protection-plugin checks. Harvesting stops if a break is denied or the required tool is no longer held.

On startup, missing sections for these two features and their entitlement rules are restored from bundled defaults. Existing settings are preserved. Blank permissions still bypass the progression gate.

## Sleeping bags

Craft a reusable Sleeping Bag with three white wool above three leather. Place it like a bed, sleep in it, and break it to pack it up again. It currently uses the vanilla white-bed appearance. Administrators can obtain one with `/give <player> stemcraft:sleeping_bag`.

Sleeping bags use normal sleep checks and count toward `skip_night` in its configured worlds, but cancel bed-caused spawn changes so the player's previous respawn point remains intact. Both halves carry a persistent marker, preserving this behavior across chunk unloads and restarts. Other beds and spawn commands work normally. Sleeping bags cannot be used in the Nether or End. The feature is enabled under `sleeping-bags.enabled`; the item and recipe are in the survival data pack's `configs/sleeping-bag.yml`.
