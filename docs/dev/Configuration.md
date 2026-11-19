# Configuration

STEMCraft uses a mix of YAML configuration, locale files, bundled defaults, and SQLite-backed runtime state.

## Main Sources

| Path | Purpose |
| --- | --- |
| `config.yml` | Primary operator configuration |
| `plugin/src/main/resources/locales/*.yml` | Locale keys and user-facing text |
| `docs/` | In-repo operator and developer documentation |
| `docs/dev/` | Developer/admin documentation and source content for the GitHub wiki |
| `data-packs/` and resource-pack data | Resource-pack and related generated content |
| SQLite DB | Runtime persistence for stats, punishments, reports, tasks, and other mutable state |

## Common Config Domains

The main `config.yml` covers many areas, including:

- world definitions and world settings
- feature enablement and feature-specific sections
- hub behavior
- random first spawn rules
- custom commands
- announcements and workshop promotions
- website and manual workshops
- recipes
- player stats options
- web/resource-pack configuration
- message type prefixes and conditional contexts under `logging`
- mailbox delivery, hologram, dialog, and notification text under `mailboxes`
- named biome and structure discovery, presentation, map layers, backfill, and name generation under `named-regions` (see [Named Regions](https://github.com/STEMMechanics/stemcraft/wiki/named-regions))

## Message Contexts

Contexts are configured under `logging.contexts.<name>`. Each context has a `prefix` and optional `show-when`/`hide-when` world or permission rules. World values support `*` globs and `!` negation. A context with only `show-when` defaults to hidden; otherwise it defaults to shown. Unknown contexts are ignored without error.

Trusted configured messages may start with directives such as `/info/`, `/survival//info/`, or `/survival/info/` to select a type and context.

## Mailboxes

`mailboxes.delivery` controls queue timing, `mailboxes.hologram.text` controls the player-specific waiting-mail marker, and `mailboxes.dialog`/`mailboxes.messages` contain all compose UI and notification strings. Hologram text supports MiniMessage and generated glyph tokens.

## Locales

Locale files are the source of:

- command usage text
- status and error messages
- gameplay notifications
- help text and UI labels

When adding new user-facing behavior, new locale keys should usually be added alongside the implementation.

## World Configuration

The world service stores per-world settings under the `worlds` config section. This includes:

- load state
- stored generator settings
- time/weather/tick speed/gamemode
- linked nether and end worlds
- join and leave commands

## WorldEdit Selection Previews

`selection_preview` renders player-specific previews for cuboid, sphere/ellipsoid,
cylinder, and 2D polygon selections. `particle`, `particles-per-block`,
`particle-send-interval`, `particle-viewdistance`, and `max-selection-size-to-display`
control the outline. Defaults are one particle per two blocks, ten ticks between updates,
and a 48-block viewing distance. `max-particles-per-update` caps the total WorldEdit
particle calls per player per update (default 1,000, clamped to 16–10,000), including
markers and grids. Lines are clipped before sampling and nearby preview geometry is
cached until the selection or the viewer's block position changes. Incomplete
selections show only the primary-position marker, never a box to an unset origin.

`advanced-grid.enabled` defaults to false. Players can override that default with
`/selpreview grid [on|off]`, independently of `/selpreview [on|off]`. Both preferences
persist across reconnects and affect only automatic WorldEdit previews. Feature and
minigame highlights remain visible. `advanced-grid.max-side-length` automatically
suppresses the WorldEdit grid when any inclusive selection bounding-box side exceeds
64 blocks; shrinking the selection restores the grid without changing the preference.
`spacing` controls cuboid grid spacing too, and `max-points` defaults to 1,000.

The density, interval, particle types and default grid setting are shared with feature
highlights. The total WorldEdit budget, player preferences, cache and grid side cutoff
apply specifically to automatic WorldEdit previews. Existing saved configuration values
are preserved on reload: update the `selection_preview` section to adopt these reduced
defaults on an existing server.

## Data Model Guidance

STEMCraft generally uses:

- YAML for operator-edited, declarative configuration
- SQLite for mutable runtime state

Examples of DB-backed runtime state:

- moderation incidents and reports
- punishments
- player stats
- first-join state
- persistent scheduled tasks
- world change recorders
- random first spawn state

## Wiki Authoring

The GitHub wiki is sourced from this directory:

- `docs/dev`

After changes are merged into the repository default branch, the `wiki-sync` workflow publishes them to the GitHub wiki repository.

## Recent configuration ownership

| Configuration | Owner and purpose |
| --- | --- |
| `stembot.yml` | Dialogue, routes, movement, private chat, reply format and signed skin values |
| `config.yml` → `afk` | Inactivity detection and kick timing (PR #157) |
| `config.yml` → `generator-maps` | Optional generator-owned map policies |
| `worlds/portals.yml` | Survival multiblock definitions and bounded exit placement |
| `worlds/portal-state.yml` | Persisted portal endpoints, charging and links |
| `quests/quests.yml` | Quest definitions; active attempts live in SQLite |
| `<minigame>.yml` → `rewards.gifts` | Winner-gift item-spec lists, applied on that game's reload |
| Existing skin-owner config → `skin-request-retries` | Per-source retry cooldown, not a separate cache file |

Use `stemcraft:animal_crate[animal=chicken]` for a filled Animal Crate. The old `animal_barrel` item key is not its current identifier. Edit BoatRace's gift lists in `boatrace.yml` and use `boatrace reload` between games; its command does not currently edit the gift list directly.

FAWE's `faweregentempworld` is excluded from managed-world configuration and discovery. Stale configuration is removed without deleting FAWE's files.

## Saving values and reading enums

`ConfigSection.set(path, value)` stores enum constants as `Enum.name()` strings,
including enums nested in collections and maps. For example:

```java
config.set("gamemode", GameMode.SURVIVAL);
GameMode mode = config.getEnum("gamemode", GameMode.class, GameMode.SURVIVAL);
```

The YAML contains `gamemode: SURVIVAL`. `getEnum` ignores case and surrounding
whitespace. Missing values return the non-null default and persist its name when
`saveDefaults` is enabled. Invalid existing values return the default without
rewriting the configuration. `getEnum` is also available on `ConfigSectionView`.

Supported values are strings, booleans, characters, primitive numeric wrappers,
`BigInteger`, `BigDecimal`, and Bukkit `ConfigurationSerializable` objects.
Collections are recursively copied into lists; maps are recursively copied and
must have string keys. Passing `null` to `set` removes the path. Default values
persisted by getters use the same validation and enum conversion.

Unsupported objects, non-string map keys and cyclic containers throw
`IllegalArgumentException` before changing the stored value or dirty state. Error
messages identify the configuration path. Arrays and arbitrary Java objects must
be converted to supported values first. This validation does not guarantee the
correctness of a custom `ConfigurationSerializable.serialize()` implementation;
those objects must provide valid supported values and register their class with
Bukkit before loading. Java's `Serializable` alone is insufficient.

## Live configuration and conflict-safe saves

`ConfigService` is the common owner of YAML monitoring and persistence. Services
must not create their own `WatchService`, poller, or YAML merge implementation.
External monitoring is opt-in:

```java
ConfigFile file = api.config().load("minigames.yml", false);
api.config().watch(file);
ConfigSubscription subscription = api.config().onChange(file, event -> {
    if (event.origin() == ConfigChangeOrigin.EXTERNAL && event.affects("games.spleef")) {
        applySpleefSettingsAtTheNextSafePoint();
    }
});
```

Close subscriptions when the owning service is disabled. The ConfigService also
clears registrations during shutdown. Path subscriptions use parent/child
overlap, so a listener for `games.spleef` receives a replacement of that whole
section as well as a leaf edit.

Change events are emitted only after a valid YAML tree is active. Their origin is
`INTERNAL` for `ConfigSection.set`/`remove` and related mutations, `EXTERNAL`
for a monitored disk edit, and `RELOAD` for an explicit `ConfigFile.reload()`.
Events include changed paths, old/new value snapshots, and the accepted disk
revision. Invalid external YAML is logged and ignored; the last valid active tree
remains in use.

### Save policy

`ConfigFileImpl` keeps three pieces of state:

1. the last successfully loaded or saved baseline;
2. the current active tree; and
3. local dirty paths.

`saveChecked()` reads the file again and performs a three-way merge. Unrelated
external edits are preserved, identical concurrent values are accepted, and
overlapping parent/child or same-path edits return a `ConfigSaveResult` with
conflicting paths. No file is replaced when conflicts are present. A successful
save is written to a temporary file in the same directory and atomically replaces
the original where the filesystem supports it. The legacy `void save()` method
remains source-compatible, but throws `ConfigSaveException` when the checked save
fails; it cannot silently report a conflict as success.

`ConfigFileImpl` also serialises writes within the JVM. The final disk revision is
checked immediately before replacement and verified afterwards; if another writer
wins that race, the result is a failed save, the local paths remain dirty, and no
automatic retry or success event is emitted.

The editor-facing `ConfigFile.saveYaml(yaml, expectedRevision)` method uses the
same server-side merge and revision check. Browser drafts therefore remain a UI
concern: a stale revision is rejected, while unrelated disk edits can still be
merged for path-level changes. Editor modules should keep the draft visible and
offer reload/discard after a conflict instead of replacing it with the server
value.

Content Packs opt in to monitoring and run their existing validation/reconciliation
lifecycle after an external event. A syntactically valid but semantically invalid
pack change is restored through ConfigService. Minigames with live monitoring use
`BaseMiniGame.watchConfigWhenSafe`; a reload waits until all arenas are empty so an
active round is not interrupted. Other services remain on their existing manual
reload or restart lifecycle until they explicitly opt in.
