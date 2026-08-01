# Configuration

STEMCraft uses a mix of YAML configuration, locale files, bundled defaults, and SQLite-backed runtime state.

## Main Sources

| Path | Purpose |
| --- | --- |
| `config.yml` | Primary operator configuration |
| `plugin/src/main/resources/locales/*.yml` | Locale keys and user-facing text |
| `docs/` | In-repo operator and developer documentation |
| `docs/wiki/` | Source content for the GitHub wiki |
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

- `docs/wiki`

After changes are merged into the repository default branch, the `wiki-sync` workflow publishes them to the GitHub wiki repository.
