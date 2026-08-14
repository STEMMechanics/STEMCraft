# Survival quality of life

The survival QOL feature provides small configurable improvements without changing the core survival progression.

## Farming and inventory

- Sneak-right-click a mature crop with any hoe to harvest and replant a 3×3 area of the same crop.
- Players and mobs cannot trample farmland into dirt.
- When the held stack is consumed or placed, an identical stack from the main inventory automatically moves into that hand. Item metadata must match, preventing named or custom items from being mixed.
- Disconnected, non-persistent leaves are queued for accelerated random ticks. `leaf_decay_random_tick.default_tick_speed` controls how many candidates are processed per server tick; a world `leaf-decay-tickspeed` setting overrides it.

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
