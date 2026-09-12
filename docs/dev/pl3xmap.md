# Pl3xMap integration

Pl3xMap is a soft dependency, currently built against 26.2-554. Keep external types inside optional integration classes; core services and generator APIs should remain loadable when Pl3xMap is absent.

## Named regions and structures

`NamedRegions` publishes immutable map-area snapshots from compact named-region storage. Biome areas become polygons; discovered structures use bundled icons. Refreshes are throttled by `map.update-minutes`, with dirty-state invalidation after region changes. The layer keys are `stemcraft_named_regions` and `stemcraft_named_structures`.

STEMCraft loads at STARTUP and Pl3xMap at POSTWORLD. Register when Pl3xMap enables as well as when it is already available; watch map-world loads and map reloads. The lifecycle fix in PR #158 also unregisters event handlers on shutdown. If layers are absent entirely, check startup registration and `named-regions.map.enabled`; if present but empty, check configured world names, discovery data and refresh timing. Existing player web sessions may need a refresh.

## Generated world maps

See [generator map renderers](generator-map-renderers.md). Deep's policy changes its basic map pixels beneath the roof; it is independent of named-region overlays. Re-rendering tiles is needed for a renderer change, but it is not the remedy for an overlay that was never registered.

## Other overlays

`Pl3xMapGraveMarkers` supplies an optional graves layer. Its markers come from grave snapshots, not named-region storage. Each integration owns its keys and should not remove another integration's layers or icons.

## Verification

Run lifecycle tests with the integration's Pl3xMap dependency available. The server supplies logging/runtime dependencies that tests may need explicitly. On a live test server verify late plugin enable, world load/unload, Pl3xMap reload, and plugin disable. Confirm map JSON and browser layer controls, then render a small region before a full-world render.
