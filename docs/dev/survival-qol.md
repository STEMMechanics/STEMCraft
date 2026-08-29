# Survival quality of life

The survival QOL feature provides small configurable improvements without changing the core survival progression.

Each player-facing behaviour has an optional `permission` beside its `enabled` setting. A blank or missing value means no permission is required. The bundled defaults use profession-backed entitlements for convenience features while leaving accident prevention and warnings available immediately. For crop protection and stronger leads, entities without a relevant player remain protected. When powered-minecart permission is configured, a cart accelerates only while an authorized player is riding it.

| Convenience unlock | Default requirement | Permission |
| --- | --- | --- |
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

## Animal barrels

Hold an ordinary vanilla barrel and sneak-right-click a chicken, rabbit, frog, or cat to capture it. One barrel is consumed and replaced by an unstackable `Animal Barrel (Chicken)`, for example. Right-click a block with the filled barrel to release it into the adjacent space; it then returns to an ordinary barrel. Filled Animal Barrels cannot be placed as storage blocks.

Animal Barrels preserve the animal's custom name, baby/adult state, tame owner, and species variant where applicable. Tamed animals can only be captured by their owner unless the player has `stemcraft.animalbarrel.others`. Animal Barrels are deliberately unstackable.

## End dragon

After an Ender Dragon dies in an eligible End world, a persistent respawn task initiates another fight after `dragon-respawn.days`. The task survives server restarts. Set `dragon-respawn.worlds` to restrict the feature to selected End worlds.

All settings and player-facing messages are under `survival-qol`, `animal-barrels`, `custom-items`, `leaf_decay_random_tick`, and `dragon-respawn` in `config.yml`.

Admins can create a populated animal barrel with `/give <player> stemcraft:animal_barrel[animal=chicken]`.
Supported animals are `chicken`, `rabbit`, `frog`, and `cat`. Omitting the property gives an empty Animal Barrel.
