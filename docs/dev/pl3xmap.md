# Pl3xMap integration

Pl3xMap is a soft dependency. The 26.3 branch is currently built and tested against Pl3xMap 26.2-554 because no 26.3 artifact is published yet. Keep external types inside optional integration classes; core services and generator APIs should remain loadable when Pl3xMap is absent.

## Named regions and structures

Discovered structures use bundled icons from immutable snapshots. Biome geometry
uses the [viewport browser extension and API](../../tools/pl3xmap-regions/README.md):
the client loads only visible vector tiles, with detail reduced at distant zooms.
The `stemcraft_named_regions` marker file is always `[]` and its live marker
updates are disabled. SSE carries only small revision notifications for biome
changes, throttled by `map.update-minutes`. The layer keys remain
`stemcraft_named_regions` and `stemcraft_named_structures`.

Install the browser extension and route its API to STEMCraft's web service before
expecting biome outlines. The plugin alone stops the old geometry payloads.
`named-regions.map.biomes.enabled: false` removes only the biome overlay and
disables its API; it retains structure icons and in-game names. Set
`named-regions.map.enabled: false` to remove both named overlays. Hiding a legacy
marker layer in the browser does not prevent Pl3xMap from fetching its data.

STEMCraft loads at STARTUP and Pl3xMap at POSTWORLD. Register when Pl3xMap enables as well as when it is already available; watch map-world loads and map reloads. The lifecycle fix in PR #158 also unregisters event handlers on shutdown. If layers are absent entirely, check startup registration and `named-regions.map.enabled`; if present but empty, check configured world names, discovery data and refresh timing. Existing player web sessions may need a refresh.

## Generated world maps

See [generator map renderers](generator-map-renderers.md). Deep's policy changes its basic map pixels beneath the roof; it is independent of named-region overlays. Re-rendering tiles is needed for a renderer change, but it is not the remedy for an overlay that was never registered.

## Other overlays

`Pl3xMapGraveMarkers` supplies an optional graves layer. Its markers come from grave snapshots, not named-region storage. Each integration owns its keys and should not remove another integration's layers or icons.

Grave snapshots are immutable and published when a grave is created, resolved,
reset or loaded. Map workers never traverse the live grave index. Unchanged
snapshots reuse marker objects. The default `graves.map.update-seconds` is 10
(range 1-3600); `graves.map.enabled: false` disables this overlay while keeping
the grave feature. Restart after changing these settings.

## Terrain render scheduling and CPU investigation

The STEMCraft integration does **not** call Pl3xMap's full-render commands or
`RegionProcessor.addRegions`, and it does not request terrain renders when named
areas or grave markers change. Those updates produce marker JSON under the map's
output directory, separate from the world's `.mca` region files. The generator
bridge replaces the existing `basic` renderer builder once per map-world load;
it does not add another terrain renderer or enqueue regions.

There are two possible indirect sources of extra terrain updates:

- Floor Shuffle, Volcano and other block-changing features alter real world
  blocks. When Paper saves those chunks, Pl3xMap's region-file watcher can enqueue
  the changed `.mca` region for rendering. This is an automatic region update,
  rather than a STEMCraft request to render the entire world. Consider excluding
  dedicated minigame worlds from Pl3xMap if their changing floors do not need maps.
- Named-region backfill loads existing chunks asynchronously and requests unload
  afterward. If Paper saves or upgrades those chunks, the same file watcher can
  see region changes. Discovery writes its names/cells to our database; it does
  not edit terrain or send a render request. Backfill defaults to one request in
  flight, every 10 ticks, and pauses while players are online. Temporarily set
  `named-regions.map.backfill-existing-chunks: false` and restart to isolate it.
  Disabling only `named-regions.map.enabled` does **not** disable backfill.

Deep's custom renderer can increase the cost of a region already queued by
Pl3xMap, because it scans beneath the roof for a cavern floor. A debugger test
confirmed a scan beginning at Y=180 with bounds -64..319; it walked the roof and
cavern instead of using just the ordinary surface heightmap. It only reads saved
chunk snapshots and does not load Bukkit chunks or generate terrain. Setting
`generator-maps.enabled: false` and restarting restores the ordinary renderer
for comparison; Deep's next rendered tiles will show the roof.

The grave overlay previously requested marker rebuilding/serialization/file
writes at a one-second interval. That avoidable background work is now throttled
and cached, but marker work is separate from terrain-render scheduling. Named
overlays default to five-minute updates, with a 60-second minimum. Structure
snapshots rebuild only when dirty and the refresh interval has elapsed; biome
tiles read bounded samples from the cached storage instead of rebuilding
whole-world polygons. See the extension instructions for SSE proxy settings,
mutable terrain cache rules and a browser/network verification checklist.

To attribute a live CPU spike, capture all threads during the same workload:

```text
/spark profiler start --thread * --timeout 60
```

This includes off-thread render and marker workers; see the
[spark command documentation](https://spark.lucko.me/docs/Command-Usage).
Compare idle operation with block-changing games running, then test backfill or
the generator bridge individually. A profile and region-file activity are needed
to establish which indirect path occurs on a particular server; the local tests
do not measure production CPU savings.

## Verification

Run lifecycle tests with the integration's Pl3xMap dependency available. The server supplies logging/runtime dependencies that tests may need explicitly. On a live test server verify late plugin enable, world load/unload, Pl3xMap reload, and plugin disable. Confirm map JSON and browser layer controls, then render a small region before a full-world render.
