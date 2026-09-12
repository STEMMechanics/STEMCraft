# Generator map renderers

`GeneratorDefinition` accepts an optional seventh argument, a `GeneratorMapRenderer`. The existing six-argument constructor remains available and uses the ordinary map. The API has no Pl3xMap dependency.

The policy provides `displayName()`, `surfaceY(Column)` and an optional `color(Column, y, argb)` override. Columns expose minimum/maximum world height and air/visible-block checks. Return a height below the minimum for a transparent pixel. Policies must be thread-safe, bounded by column heights, and may only inspect the supplied saved-block snapshot. Never call Bukkit world APIs or load chunks from these callbacks.

`GeneratorMaps` connects when Pl3xMap enables, attaches existing and later map worlds, and removes its listeners and restores original builders on disable. Worlds must use a registered generator carrying a policy. The bridge replaces that world's `basic` renderer at runtime, preserving its tile key and leaving other renderers and worlds alone. If the administrator removed `basic`, it is not re-added. Pl3xMap remains optional; disable the bridge using `generator-maps.enabled: false` and restart.

Deep embeds its policy in `DeepGenerator.mapRenderer()`. It starts beneath the generator's roof boundary, skips solid ceiling until it reaches air, then renders the first visible surface below it. Columns with no open cavern are transparent. Water uses biome water colors; this is an opaque top-surface map, not a translucent-fluid or multi-level cave map. Elevation shading uses the selected surface instead of the roof heightmap.

After deployment run `pl3xmap fullrender <deep-world-name>` from the console and refresh the web map. Existing tiles retain the old gray roof until rerendered. This changes map pixels only: no terrain regeneration or dimension ceiling flags are needed. Pl3xMap 26.2-554 is the tested integration target.
