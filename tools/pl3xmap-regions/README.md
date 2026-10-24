# Optional named-region map tiles

## Enable

The default display remains the original full-region polygons. The new tiled
display is optional; enable it in STEMCraft's config:

```yaml
named-regions:
  map:
    biomes:
      viewport-tiles: true
```

Replace the STEMCraft plugin jar, update the setting, restart the server, and
refresh the map page. Omitting the setting or setting it to `false` keeps the
original display. Switching it back to `false` also removes STEMCraft's browser
script tag automatically; restart and refresh the page to finish switching.

When tiled mode is enabled, STEMCraft installs its browser script, updates
Pl3xMap's existing index, and generates region files in Pl3xMap's normal web
directory. The same web server
that already serves your map serves these files. There is no separate region API,
extra port, STEMCraft web-service dependency, or additional proxy route.
An `endpoint-url` setting left over from an earlier experimental build is ignored.

The first region-tile generation runs in the background. Outlines appear when
the files are ready; large discovered worlds can take longer for their first
build. Existing stored names and in-game discovery continue to work normally.

## How it works

With `viewport-tiles: true`, the existing `stemcraft_named_regions` marker file
contains `[]`. Its layer keeps the Regions checkbox, while the browser loads only
visible geometry tiles from:

```text
tiles/<world>/stemcraft-regions/metadata.json
tiles/<world>/stemcraft-regions/<level>/<tile-x>_<tile-z>.json
```

Metadata contains only rendering settings and a revision. Each geometry tile
contains at most 32 × 32 sampled cells; a one-cell halo distinguishes real
outlines from tile edges. At the closest detail each cell represents 16 × 16
blocks. Distant zooms use larger cells and five fixed probes to choose a
representative region, including unknown terrain in the vote. Small regions can
disappear at distant zooms. These outlines are approximate, rather than claim
boundaries. Collinear points are removed, while holes are retained.

The browser holds at most 64 visible tiles, with at most four tile requests in
progress. Hiding Regions cancels requests and releases its geometry. Missing
files represent undiscovered tiles and stay empty. Tile requests include the
published revision to refresh changed data, and metadata requests bypass the
browser cache. URLs are relative to the existing map page, including maps hosted
under a path prefix.

The publisher enumerates occupied chunks to find tile locations; distant
discoveries do not generate all the empty tiles between them. It reads cached
discovery data without loading or generating Minecraft chunks. Work runs off the
server thread and is throttled by `named-regions.map.update-minutes` (default 5,
minimum 1). Unchanged tile files are left untouched. Metadata is published after
the tile files are ready and obsolete tiles have been removed, followed by a
small SSE revision notification. Metadata
polling also works when SSE is unavailable. Biome geometry is never sent through
the marker endpoint or SSE. Structure and grave overlays retain their own layers.

## Automatic browser files

In tiled mode installation runs when STEMCraft attaches to Pl3xMap and after map
enable/reload, following Pl3xMap's configured web directory. It preserves the current page and
theme, backs up the first changed index at `.stemcraft-regions-backup/index.html`,
keeps one script tag, and versions the script URL. It preserves file permissions
and existing index symlinks. Pl3xMap's read-only option is not required for this
extension; keep it if a separate theme needs it.

The existing `named-regions.map.biomes.enabled` setting removes only the biome
overlay when false. `named-regions.map.enabled: false` removes both named overlays.
Set `named-regions.map.biomes.auto-install: false` only if you manage a separate
copy of the website. That copy needs the generated region files, script and index
along with the existing map files. Remove the script tag there too when returning
to the original polygon display. The optional manual browser-file installer is:

```sh
sh tools/pl3xmap-regions/install.sh /path/to/your/map/web
```

It requires Python 3. To remove the script, disable automatic installation first,
then run `tools/pl3xmap-regions/uninstall.sh` against that web directory.

## Verify

```sh
./gradlew :api:check :plugin:check :plugin:jar --offline --console=plain
node --test tools/pl3xmap-regions/stemcraft-regions.test.cjs
```

Tests cover geometry bounds, holes and tile seams, sparse discoveries, background
publication, cancellation, reloads, installation, and serving the generated files
through the real Pl3xMap static web server with no custom HTTP route.

With tiled mode enabled, confirm the old biome marker file contains `[]`, visible
region requests use the existing `tiles/<world>/stemcraft-regions/` directory, requests
stop when Regions is unchecked, and names appear on hover. Pan across X/Z=0,
zoom out, switch worlds and check changes after a refresh interval. Permanent
labels can repeat where one region crosses multiple tiles. Local tests establish
these bounds and serving behavior; they do not measure production responsiveness.
