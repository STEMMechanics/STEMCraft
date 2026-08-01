# Services

These are the runtime services instantiated in `STEMCraft.java` and exposed through `STEMCraftAPI`.

## Bootstrap Services

| Service | Implementation | Purpose |
| --- | --- | --- |
| Config | `ConfigServiceImpl` | Loads and saves YAML-backed config files and sections |
| Tasks | `TaskServiceImpl` | Synchronous, asynchronous, repeating, and persistent scheduled work |
| Messages | `MessageServiceImpl` | Formatted player/server messaging and token expansion |
| Locales | `LocaleServiceImpl` | Locale resolution and key lookup |

## Core Runtime Services

| Service | Implementation | Purpose |
| --- | --- | --- |
| Audit | `AuditServiceImpl` | Structured audit logging and audit command surfaces |
| Chat | `ChatServiceImpl` | Moderation workflows, reports, mute-all state, and report review surfaces |
| Commands | `CommandServiceImpl` | Fluent command registration API used by the rest of the plugin |
| Database | `DatabaseServiceImpl` | SQLite execution, query helpers, and migration version tracking |
| Events | `EventServiceImpl` | Simplified event listener registration |
| Holograms | `HologramServiceImpl` | Hologram creation, lookup, and command tooling |
| Items | `ItemServiceImpl` | Custom item metadata/helpers |
| Minigames | `MiniGameServiceImpl` | Arena runtime management, player occupancy, and minigame coordination |
| MOTD | `MotdServiceImpl` | Server list MOTD handling |
| Placeholders | `PlaceholderServiceImpl` | Internal token rendering plus PlaceholderAPI bridge when present |
| Players | `PlayerServiceImpl` | Player utility and state helpers |
| Player Stats | `PlayerStatsServiceImpl` | Built-in and custom stat registration, capture, and persistence |
| Profanity Filter | `ProfanityFilterServiceImpl` | Local profanity matching, configuration, and administration |
| Punishments | `PunishmentServiceImpl` | Ban, unban, warn, kick, and punishment record management |
| Recipes | `RecipeServiceImpl` | Custom recipe loading from config |
| Regions | `RegionServiceImpl` | Region tracking and listener dispatch |
| Resource Pack | `ResourcePackServiceImpl` | Pack generation, hosting, and distribution |
| Selection | `SelectionServiceImpl` | Selection/session helpers used by editing workflows |
| Tab Complete | `TabCompleteServiceImpl` | Named tab-completion providers and placeholder expansion |
| Web | `WebServiceImpl` | Embedded HTTP endpoint registration and status endpoint |
| Worlds | `WorldServiceImpl` | World lifecycle, generators, settings, links, and world command tooling |

## Service Notes

### Worlds

The world service is one of the largest domain services. It owns:

- world create, load, unload, duplicate, and delete
- generator registration and stored generator config
- world settings such as time, weather, tick speed, gamemode, nether/end links, and spawn rules
- transition commands for world join and leave events
- the `/world` command surface

### Minigames

The minigame service provides the shared framework for:

- arena lifecycle
- player and spectator tracking
- HUD refresh
- countdowns and match transitions
- common world/region protections for active arenas

### Resource Pack

The resource pack service coordinates:

- generator registration
- build targets and formats
- Java pack archive output
- Bedrock pack generation support
- web hosting integration for resource-pack delivery

### Chat, Audit, Punishments, and Profanity

These services form the moderation stack. Together they cover:

- profanity matching and classification
- incident capture
- punishment persistence and command surfaces
- audit review
- player reporting

### Tasks

The task service is more than a Bukkit scheduler wrapper. It also supports:

- named tasks
- delayed and repeating execution
- retryable workflows
- persistent tasks stored in SQLite so scheduled work can survive restarts
