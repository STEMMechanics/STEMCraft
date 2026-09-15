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
| `NamedRegions` | Permanently names discovered biome territories and structures, with coordinate-bar and optional Pl3xMap presentation; see [Named Regions](https://github.com/STEMMechanics/stemcraft/wiki/named-regions) |
| `GameModeAliases` | Registers short aliases such as `gms`, `gmc`, `gma`, and `gmsp` |
| `GameModeInventories` | Keeps inventory state separate across gamemode profiles |

### Shared world inventories

GMI automatically groups worlds by the name before the first underscore:
`survival`, `survival_nether`, and `survival_deep` share `survival`;
`bridge_amazon` and `bridge_western` share `bridge`. A world with no underscore
uses its full name, such as `hub`. Names beginning with an underscore use their
full name to avoid an empty group.

Override a world's group in `config.yml`:

```yaml
worlds:
  survival: {}
  survival_nether:
    inventory-group: nether
  survival_deep: {}
  hub: {}
  bridge_amazon: {}
  bridge_western:
    inventory-group: nether
```

This produces four groups:

| Group | Worlds |
| --- | --- |
| `survival` | `survival`, `survival_deep` |
| `hub` | `hub` |
| `bridge` | `bridge_amazon` |
| `nether` | `survival_nether`, `bridge_western` |

An explicit nonblank `inventory-group` replaces automatic grouping for that
world. Group names are literal and case-sensitive; they are not split on
underscores or resolved through another world's settings. An explicit group can
also join an automatic group of the same name. Missing or blank values restore
automatic grouping. To isolate a world, give it a unique group name.
There is no separate GMI grouping configuration.

Game modes remain separate within each group. Profiles include inventory,
armour, Ender Chest, XP, health, hunger, and potion effects. GMI skips registered
minigame participants, whose inventory is managed by the minigame system.
Visiting a minigame world without joining still uses GMI.

Restart or run `/stemcraft reload` after editing. On reload, online players'
current profiles are saved before switching groups. Existing profiles are
retained, not merged: `survival_deep` now uses the existing `survival` profile,
while its former separate profile remains stored. A new group starts with an
empty inventory. Set an explicit group to the former group name if you want to
continue using that old profile.

## Content and UI

| Feature | Purpose |
| --- | --- |
| `CustomBooks` | Configurable books plus `/book` command surfaces |
| `QuestFeature` | Private NPC quests, updating owner-bound books, objective progress, prerequisites, and rewards |
| `InteractiveMenus` | Book/form-based interactive menu system with admin editing |
| `CustomCommands` | Config-defined command aliases with player/server sender control |
| `PlayerTabList` | Header/footer tab list rendering and update loop |
| `PlayerGameMessages` | Join, quit, death, and related configurable player messages |

`PlayerTabList` treats its header, footer, and `name_format` as MiniMessage. Both `gray` and `grey` tag spellings
are accepted. `{world}` uses the shared player-facing world name, while `{world-raw}` exposes the underlying
world folder name. By default, underscores become words and multi-part map names treat their first word as a
category: `bedwars_amazon` becomes `Bedwars: Amazon`, while dimension suffixes remain natural, such as
`survival_nether` becoming `Survival Nether` and `bedwars_forest_nether` becoming
`Bedwars: Forest Nether`. Override any generated name for Player Tab and Coordinates with:

```yaml
worlds:
  world:
    display-name: "World"
```

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
| `AgricultureFeature` | Data-pack-driven persistent crops and biome-aware forage drops |
| `ChiselFeature` | Rotates safe building-block states with the cross-platform Chisel item |
| `AnimalBarrels` | Animal Crates for carrying supported small animals |
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

## Newer guide, rendering and progression features

| Feature | Runtime contract |
| --- | --- |
| `StemBotFeature` | Owner-only Citizens guide and action interpreter; see [STEMBot](stembot.md) |
| `ProfessionsFeature` | Eight level-1-to-100 skills with world/mode filtering; see [Professions](professions.md) |
| `FormattedSigns` | Supported ampersand codes and glyphs, explicit `stemcraft.sign.format` grant |
| `VotingFeature` | Challenge voting state and per-player allowance; inspect current event configuration |
| `GeneratorMaps` | Optional generator-owned basic-map replacement; see [map renderers](generator-map-renderers.md) |
| `Afk` | Optional inactivity announcements, tab styling and kicks; see [AFK](afk.md), introduced in PR #157 |

See [Permissions](permissions.md) for individual player/admin grants. There is no required `stemcraft.player` bundle. Custom commands enforce a permission only when one is configured; fixed `/survival` and `/creative` shortcuts run their teleport as the server.
