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
- recipes
- player stats options
- web/resource-pack configuration
- message type prefixes and conditional contexts under `logging`
- mailbox delivery, hologram, dialog, and notification text under `mailboxes`

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
control the outline. `advanced-grid.enabled` adds geometry-appropriate surface grids;
`spacing` and `max-points` bound their density and cost.

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
