# Features

## Mailboxes

Mailboxes provide delayed player and server deliveries containing a written letter and optional items. Offline notifications are delivered on join, and a player-specific hologram indicates waiting mail. Runtime text, dialog labels, messages, and delivery timing are configurable under `mailboxes`.

Features are discovered from `dev.stemcraft.feature` and loaded through `BaseFeature`.

## World and Travel

| Feature | Purpose |
| --- | --- |
| `HubFeature` | Defines the hub world, join routing, and `/hub` command behavior |
| `TeleportUtils` | Registers warps, spawn, world teleport, `/back`, `/top`, `/jump`, and related travel commands |
| `RandomFirstSpawn` | Applies per-world random first spawn rules and persistence |
| `Coordinates` | Action bar and boss bar coordinate displays through `/coord` and `/coordbar` |
| `GameModeAliases` | Registers short aliases such as `gms`, `gmc`, `gma`, and `gmsp` |
| `GameModeInventories` | Keeps inventory state separate across gamemode profiles |

## Content and UI

| Feature | Purpose |
| --- | --- |
| `CustomBooks` | Configurable books plus `/book` command surfaces |
| `InteractiveMenus` | Book/form-based interactive menu system with admin editing |
| `CustomCommands` | Config-defined command aliases with player/server sender control |
| `PlayerTabList` | Header/footer tab list rendering and update loop |
| `PlayerGameMessages` | Join, quit, death, and related configurable player messages |

## Survival and World Behavior

| Feature | Purpose |
| --- | --- |
| `SkipNight` | Sleep vote handling and night skipping |
| `DragonRespawnFeature` | Scheduled Ender Dragon respawn handling |
| `DistanceDifficulty` | Scales hostile difficulty by player/world distance rules |
| `PhantomSpawning` | Phantom spawn restrictions or adjustments |
| `LeafDecayRandomTickFeature` | Custom leaf decay processing and world setting integration |
| `LeafDecayTickSpeedSetting` | World setting companion used by the leaf decay system |
| `SurvivalQolFeature` | Farming, transport, inventory, mob information, and equipment warnings |
| `AnimalBarrels` | Craftable barrels for carrying supported small animals |
| `FriedEggs` | Cooked eggs with configurable food values and cooking methods |
| `CometFeature` | API-launched destructive sky events with heat, crash scars, geodes, and optional loot |

## Item, Inventory, and Death Handling

| Feature | Purpose |
| --- | --- |
| `Graves` | Grave creation and recovery workflows on death |
| `GraveStorageSupport` | Grave persistence/storage support |
| `DropPlayerHeads` | Drops player heads on death under configured conditions |
| `GunpowderBarrels` | Gunpowder barrel behavior |
| `NoAnvilRepairCost` | Removes or adjusts anvil repair penalties |
| `NoThrowingPotions` | Restricts throwable potion usage |

## Restrictions and Server Rules

| Feature | Purpose |
| --- | --- |
| `RestrictCreative` | Restricts sensitive creative-mode actions unless explicitly permitted |
| `DenySpawnEggs` | Blocks spawn egg usage where configured |
| `NaughtyMode` | Restricts players to a controlled command and behavior set |
| `RebalanceIronGolem` | Adjusts iron golem balance/behavior |

## Feature Design Rules

Common feature characteristics:

- config-driven enablement
- self-registration of listeners and commands
- preference for existing services instead of direct Bukkit utility code
- focused ownership over one gameplay concern

If a new capability is optional and domain-specific, it usually belongs in `dev.stemcraft.feature`.

Notice boards provide graphical lobby boards containing player-authored headers, short messages, and author names. Posts expire automatically and are displayed through the reusable image-map service.
