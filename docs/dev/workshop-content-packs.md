# Workshop content packs

STEMCraft content packs are a STEMCraft-owned configuration layer. They are not
Minecraft data packs. A pack lives under `plugins/STEMCraft/data-packs/<pack-id>/`
and may contain:

```text
config.yml
configs/*.yml
textures/**/*.png
```

`config.yml` contains pack metadata. Definitions in `config.yml` and every YAML
file under `configs/` are combined. Existing `custom-items` and `recipes`
sections remain valid. The central content-pack service discovers files,
validates the proposed state, records ownership, and only then asks the item,
recipe, loot, and ability services to reconcile their registrations.

## Lifecycle

```text
/stemcraft pack list
/stemcraft pack info <pack>
/stemcraft pack create <pack> [template]
/stemcraft pack validate <pack>
/stemcraft pack enable <pack>
/stemcraft pack disable <pack>
/stemcraft pack reload <pack>
/stemcraft pack delete <pack>
```

Pack enablement is persisted in `content-pack-state.yml`. A pack is validated
before enable or reload. Invalid YAML, missing condition providers, duplicate
IDs, invalid materials, and invalid potion/enchantment references retain the
previous active runtime state. Delete moves the directory to
`.content-pack-trash/`; it is recoverable by moving it back and reloading.

An enabled pack should be scoped explicitly in its rules. The example pack is
disabled by default and its loot rules only match the `workshop-test` world.
Review and test it before enabling it for a survival world.

## Items and abilities

Child-friendly fields include:

```yaml
custom-items:
  example-boots:
    material: LEATHER_BOOTS
    name: "<green>Example Boots" # on-screen item name
    lore: ["<gray>One line"]
    color: "75B84A" # leather, potion, or tipped-arrow metadata
    stats:
      attack-damage: 2.0
      attack-speed: 1.6
      armor: 1.0
      toughness: 0.0
    enchantments:
      durability: 2
    potion:
      effects:
        - type: SPEED
          duration-seconds: 30
          level: 1
```

Potion levels start at 1; durations are seconds. Combat stats are readable
final totals (for example, a sword attack speed of `1.6`), not raw Bukkit
modifier values. Omitted stats preserve the vanilla material total. `name` is the item name shown
to the player. An ability `TITLE` action has separate `title` and `subtitle`
fields. Equipment slot groups are explicit (`HEAD`, `CHEST`, `LEGS`, `FEET`,
`HAND`, `OFF_HAND`, or `ARMOR`). The runtime owns its attribute keys so reloads
do not stack duplicate modifiers.

Reusable potion definitions live under `potions:` and can be referenced from
an item with `potion: definition-id`. They may contain multiple effects and an
optional colour; inline `potion.effects` mappings remain supported.

Supported triggers are `while-equipped`, `on-hit`, `on-hurt`,
`on-projectile-hit`, `on-consume`, and `on-walk`. Supported actions are
`EFFECT`, `PARTICLES`, `TITLE`, `SOUND`, and `PLANT_FLOWER`. Every action may
use `chance`, cooldown, target selection, and an ability slot. Projectile
abilities are snapshotted at launch. Hit actions run only after an uncancelled
damage event. Walking actions run on block changes and are bounded by spacing,
chance, cooldown, particle count, loaded-chunk checks, replaceable-space checks,
and the normal block-place protection event.

## Loot

The reusable loot service consumes `MOB_DEATH` and `BLOCK_BREAK` tables. A
`BLOCK_BREAK` rule can match one or more block types through the `block`
condition, so a pack can add drops for logs, leaves, ores, or any other block.
Rules are
ordered by ascending `priority`, then file order. Chance and quantity are
evaluated once per entry per event. A removal quantity is consumed across
matching stacks. A failed `REPLACE` or `REPLACE_ALL` roll leaves the original
drop untouched. Ordinary drops are preserved unless a matching rule changes
them. Matching uses the custom item's canonical ID, never its display name.

Conditions are provided by the shared condition registry:
`world`, `entity-type`, `block`, `biome`, `player-kill`, `spawn-reason`, and
`distance-tier`. The distance provider records the mob's tier at spawn. Tiers
use the existing max-horizontal-distance metric in 1,000-block bands:
0–999 is tier 0, 1,000–1,999 is tier 1, and so on. Mobs without the new stored
value use the documented compatibility fallback to their old multiplier or
current location.

Packs may add a `difficulty-profiles` section with `range` and `multiplier`.
Profiles are namespaced by their owning pack and are registered without
changing global difficulty. Select one explicitly through the
`DistanceDifficultyService.selectWorldProfile(world, "pack-id:profile-id")`
API (or an administrative UI); an unavailable selection falls back to the
global `distance_difficulty` values.

Biome conditions may select `MOB_SPAWN`, `MOB_DEATH`, or `KILLER` through
`biome-source`. Use the bounded simulation command with deterministic context
arguments:

```text
/stemcraft loot simulate <table> <rolls> [world] [mob] [tier] [biome] [spawn-reason]
```

Add `true` as the final optional argument to model a player kill when testing
`player-kill` rules.

## Live editor

The editor stores per-player drafts and keeps the draft after the dialog closes:

```text
/itemedit list [page]
/itemedit info <item-id>
/itemedit hand
/itemedit <item-id>
/itemedit create <item-id> <material> [pack]
/itemedit set <field> <value>
/itemedit set ability-edit <trigger>:<index>:<field>:<value>
/itemedit test [player] [amount]
/itemedit undo
/itemedit redo
/itemedit save
/itemedit cancel
```

`list` uses the standard paginated chat menu; its item rows can be clicked to
open a draft. `info` shows the friendly item name, ID, material, placement and
client presentation metadata. The canonical command is `/itemedit`; `itemeditor`
is not a separate command.

`/itemedit hand` and `/itemedit <item-id>` open the shared DialogService for
Java and Bedrock players. Accepted field edits update the held preview and
tagged preview copies immediately; quantity, valid durability damage, and
unrelated metadata are retained. `cancel` restores the original held stack when
it is still in the editor's hand; tagged copies are left intact so moving,
dropping, splitting, or disconnecting cannot delete a player's item. Save
checks the source file revision, writes only the selected item, validates the
pack, publishes it, and reloads the owned registrations. A concurrent file
change is rejected instead of silently overwritten.

Ability actions can be changed or removed without hand-editing YAML. For
example, `ability-edit on-hit:0:0:chance:0.5` changes the first action in the
first `on-hit` ability, and `ability-remove on-hit:0:1` removes that ability's
second action. `ability-remove on-hit:0` removes the complete ability entry.

Permissions are intentionally separate:

```text
stemcraft.itemedit.edit
stemcraft.itemedit.test
stemcraft.itemedit.publish
stemcraft.command.stemcraft.pack
stemcraft.command.stemcraft.loot
```

## PNG textures and resource packs

Put an inventory/held-item PNG at
`data-packs/<pack>/textures/<path>.png`, then select it with:

```text
/itemedit set texture <path>.png
```

The editor validates that the file exists in the selected pack. The picker
source is the pack's recursive PNG list. An inventory/held item texture is not
an armour model: worn armour requires the Java model/texture arrangement and
Bedrock armour assets supported by the resource-pack generators. Adding or
changing a PNG or model sets the pack's “resource-pack rebuild required” flag;
run the existing resource-pack generation command and redistribute the Java
and Bedrock packs before expecting clients to see a new asset. YAML-only name,
lore, stats, potion, recipe, loot, and ability changes apply after content
reload without rebuilding resource packs.

## Example pack

`plugin/src/main/resources/data-packs/stemcraft-workshop/` contains Forest
Shard, Frost Blade, Venom Arrow, Guardian's Chestplate, Meadow Boots, and
Explorer's Tonic, plus recipes and append/remove/replace/replace-all loot
examples. It deliberately contains no binary texture asset so it can be
tested with vanilla appearances first. Children can add PNGs to its `textures`
directory, select them from the editor, reload the pack, and then rebuild the
resource packs when the visual is ready.

The web editor can use the same pack commands and API lifecycle: validate a
proposed YAML document, publish only the owned definition, then call the same
reload/reconcile path. It should not write the whole pack or bypass the
revision check.

The shared web editor is opened in-game with `/editor`. The first private link
contains the bootstrap key, then exchanges it for an HttpOnly cookie and
redirects to the clean `/editor` URL. It provides common controls for Quests,
Items, Loot, Packs, and a Components showcase. Modules return data and actions
through `EditorWebModule`; they do not serve their own HTML or token-bearing
routes. Lists and editable tables open records in dedicated subviews, while the
shared editor controls the private link, cookie session, sidebar, permissions,
status messages, and styling.
