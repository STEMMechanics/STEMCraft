# Named Regions

`NamedRegions` permanently names biome territories and generated structures as chunks are discovered. It is a built-in feature, not a public `STEMCraftAPI` service; integrations should currently treat its SQLite schema and internal area model as implementation details.

## Discovery model

The feature runs only in worlds listed under `named-regions.worlds`.

Biome territories are sampled on a four-by-four-block grid. Orthogonally adjacent cells in the same biome family join the same region. If a newly sampled cell connects multiple regions, unlocked regions are merged; a locked, administratively renamed region takes precedence. The implementation groups Minecraft biomes into the configured families such as `plains`, `forest`, `ocean`, `mountains`, and `snow`.

Generated structures are stored separately using their structure bounding box. Structure variants are grouped into common families where appropriate, for example all village variants use `village` and all ruined portal variants use `ruined-portal`. Buried treasure and nether fossils are disabled by default because they are small and easily discovered; other structure families default to enabled unless explicitly disabled.

Biome regions are discovered when chunks load. Structures are added when their generated chunk is inspected, but remain hidden from the map until a player enters their bounding box. Entering a different named area can display its name and adds it to the coordinate bar.

## Naming

Names are unique across all active biome regions and structures, case-insensitively. A region ID deterministically selects a starting point in its pool, while unavailable candidates are skipped.

Configuration offers two naming approaches:

- `names.pools.<type>` supplies a complete, explicit pool and takes precedence for that type.
- `names.sources.<type>` and `names.forms.<type>` generate a larger pool. One-word sources are used directly and expanded through forms containing `{root}`; multi-word sources are treated as complete names.

If a type has no source or form list, the corresponding `default` list is used. Generated candidates are compacted to at most two words. When every pooled candidate is unavailable, the feature creates a stable fallback from the friendly type and a letter code derived from the region ID.

When a region is merged or renamed, its former name is retired for `names.retirement-days`. Retired names cannot be reassigned until that period expires or an administrator releases them. Changing naming configuration affects only new names; use the regeneration command to update existing areas.

Missing `names.sources` or `names.forms` sections are restored from the bundled defaults during feature startup. This prevents older configuration files from losing the required default naming data.

## Player presentation

`display.title` and `display.subtitle` accept MiniMessage plus these placeholders:

| Placeholder | Value |
| --- | --- |
| `{name}` | Assigned area name |
| `{type}` | Friendly biome or structure family |

Either title line may be blank. Fade and display durations are configured in milliseconds.

When `coordbar.enabled` is true, the feature registers a `WORLD` coordinate-bar amendment named `named-region` at priority 50. Its `coordbar.format` supports `{name}` and `{type}`. The format must include any desired leading or trailing spacing because the coordinate-bar service does not add a separator.

## Pl3xMap integration

If Pl3xMap is installed and `map.enabled` is true, two live layers are registered:

- biome regions are polygons built from the actual discovered cell boundaries
- discovered structures are icons placed at the centre of their bounding box

The biome colour palette accepts `#RRGGBB` or `#AARRGGBB`. Each region deterministically selects a palette entry; its border uses the same RGB at full opacity. An empty palette falls back to the legacy `stroke-colour` and `fill-colour` settings. Map snapshots are rebuilt only when dirty and no more frequently than `map.update-minutes` (with a minimum Pl3xMap update interval of 60 seconds).

The optional backfill scans existing Anvil region-file headers and gradually inspects already generated chunks without generating new terrain. By default it pauses while players are online. `map.backfill-period-ticks`, `map.backfill-max-in-flight`, and `map.backfill-only-when-empty` control the load it creates.

## Administration

The command root is `/namedregion` and requires `stemcraft.command.namedregion`.

| Command | Purpose |
| --- | --- |
| `/namedregion info` | Describe the area containing the player |
| `/namedregion list` | List active areas, up to 50 entries |
| `/namedregion find <name>` | Search active and retired names |
| `/namedregion nearby` | List the ten nearest areas in the player's world |
| `/namedregion teleport <id>` | Teleport to the centre of an area |
| `/namedregion rename <id> [name]` | Assign and lock a custom name, or choose a new generated name when omitted |
| `/namedregion retired [type]` | Show currently quarantined names, optionally filtered by family |
| `/namedregion fallbacks [type]` | Find areas using generated fallback names |
| `/namedregion release <name|*>` | Make one or all retired names immediately available |
| `/namedregion regenerate <id|*>` | Regenerate one name, or start a batched job to replace every fallback name for which a pooled name is available |

Use the stable ID printed by `info`, `list`, `find`, or `nearby` for commands that take `<id>`. Renaming always locks the area. Regenerating preserves its current lock state.

Bulk regeneration processes a small number of regions per server tick to avoid blocking the watchdog. It reports progress every 250 regions, including the number processed, successfully regenerated, and still using fallbacks. Only one bulk regeneration job can run at a time.

## Persistence and lifecycle

Runtime state is stored in SQLite:

- `named_areas` holds names, kinds, types, bounds, discovery state, and the rename lock
- `named_region_cells` maps sampled world cells to biome-region IDs
- `named_region_name_history` records retired names, their release time, and the reason for retirement

The feature owns migrations for these tables through the database service. Do not edit the tables while the plugin is running: in-memory indexes are authoritative until shutdown, and map data is cached between refreshes.

The feature is discovered through `BaseFeature`. Disabling it unregisters its coordinate-bar amendment and Pl3xMap layers, cancels backfill, and clears its runtime indexes; persisted area and history data remain available for the next enable.
