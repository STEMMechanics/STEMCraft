# Named-region map tiles

The named-region overlay uses small vector tiles from STEMCraft's web service.
Pl3xMap's `stemcraft_named_regions` marker file contains `[]`; its layer retains
the existing Layers checkbox. The browser extension fills that layer only with
tiles intersecting the visible map, and releases tiles when they leave view.
Hiding the layer cancels requests and clears its geometry.

Each tile contains at most 32 × 32 sampled cells, with a one-cell halo used only
to calculate real outlines at tile edges. At the closest detail, each cell is
the original 4 × 4 block sample. Zooming out uses five fixed probes per larger
cell to choose a representative region; unknown terrain participates in the
vote. Small regions can disappear at distant zooms. Collinear boundary points
are removed without changing holes. These are sampled map outlines, not claim
boundaries. Permanent labels can repeat where a region crosses tiles.

The browser holds at most 64 visible tiles and starts at most four tile requests
at a time. The server reads at most 5,780 cached cells for a tile, regardless of
world size, without loading Minecraft chunks or scanning the world cell index.
SSE carries only `stemcraft-regions` revision notifications. Periodic metadata
checks provide a fallback when SSE is unavailable. Responses use `no-store`,
and tile URLs include the current revision. The structure and grave overlays
continue to use their own marker layers.

## Install

1. Replace the STEMCraft plugin jar and restart. The new plugin immediately
   replaces the old whole-world biome marker file with `[]`. Existing proxy or
   browser caches must also be purged/refreshed. The old browser has no biome
   outlines until the following browser extension is installed.
2. Enable STEMCraft's web service in `plugins/STEMCraft/config.yml`:

   ```yaml
   web_server:
     enabled: true
     ip: 127.0.0.1
     port: 8950
   named-regions:
     map:
       enabled: true
       biomes:
         enabled: true
   ```

   Merge these values into existing sections. Bind to an address reachable by
   the reverse proxy; the loopback example assumes the same host/network namespace.
   Restart after changing configuration.
3. Add the API route to the map site's proxy. For nginx on the same host:

   ```nginx
   location = /stemcraft/named-regions {
       proxy_pass http://127.0.0.1:8950/map/named-regions;
       proxy_cache off;
   }
   ```

   Query parameters pass through to the endpoint. This exposes discovered biome
   names and approximate outlines only for the configured named-region worlds.
   Keep the public endpoint on the map's origin; no CORS setup is needed.
4. From this repository, install the browser script into the **generated** map
   web directory. Stop the server while changing its web files. Install the
   optional STEMCraft theme first, if used:

   ```sh
   sh tools/pl3xmap-regions/install.sh /path/to/plugins/Pl3xMap/web
   ```

   The installer requires Python 3.
   A different same-origin public route can be passed as the second argument.
   The installer preserves the current theme, backs up the original index,
   and versions its script URL. Set Pl3xMap `settings.web-directory.read-only: true`
   before restarting so Pl3xMap retains the script tag. Refresh the browser.
   Reinstall this script after updating Pl3xMap's web files or reinstalling the
   theme. An independently hosted map needs the same script/index update and
   proxy route on that host.

To temporarily remove biome geometry without removing named regions or structure
icons, set `named-regions.map.biomes.enabled: false` and restart. This removes the
layer and disables its API. On an older plugin, `named-regions.map.enabled: false`
disables both named map overlays as an emergency workaround.
Refresh existing browser sessions, replace any old
`tiles/<world>/markers/stemcraft_named_regions.json` files with `[]`, and purge
cached copies when using that older-plugin workaround.

## SSE and mutable terrain caching

Apply these directives to the existing map SSE proxy location, retaining its
correct upstream and path handling. Match both `/sse` and `/sse/<world>` (or their
equivalents when the map is hosted below a URL prefix):

```nginx
# Inside the existing SSE location:
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 1h;
```

nginx documents [response buffering and proxy caching](https://nginx.org/en/docs/http/ngx_http_proxy_module.html).
Check the effective configuration (`nginx -T`), including inherited locations
and any CDN cache rules. These settings cannot be verified from the plugin
repository. Reload nginx after validating configuration with `nginx -t`.

Pl3xMap 26.2-554's built-in HTTP server already emits
`Cache-Control: max-age=0, must-revalidate, no-cache` for `/tiles` responses.
Preserve those upstream headers for mutable terrain images, block info,
`settings.json`, `markers.json` and legacy marker files. Remove proxy/CDN rules
that override them with long freshness times; purge existing cached copies.
The new vector endpoint sends `Cache-Control: no-store`. Its revision parameter
also changes tile URLs when the overlay changes. Versioning these overlay URLs
does not version Pl3xMap's terrain images.

## Verify

```sh
./gradlew :api:check :plugin:check :plugin:jar --offline --console=plain
node --test tools/pl3xmap-regions/stemcraft-regions.test.cjs
curl -i 'https://YOUR-MAP-HOST/stemcraft/named-regions?world=survival'
curl -i 'https://YOUR-MAP-HOST/stemcraft/named-regions?world=survival&level=0&x=0&z=0'
curl -N --max-time 30 'https://YOUR-MAP-HOST/sse/survival'
```

Use the actual map path and world name. The first API response contains rendering
settings and a revision; the second contains one bounded tile. In browser Network,
confirm `tiles/survival/markers/stemcraft_named_regions.json` contains `[]`, that
SSE has no biome `markers` geometry, and that viewport requests stop while Regions
is unchecked. Pan across X/Z=0, zoom out, switch worlds, and check names on hover.
Confirm newly discovered/renamed regions appear within the configured refresh
interval. Local tests establish request/geometry bounds, not production payload
size, responsiveness or effective proxy/CDN settings.

## Remove the browser extension

```sh
sh tools/pl3xmap-regions/uninstall.sh /path/to/plugins/Pl3xMap/web
```

The uninstaller removes only this extension's script tag and asset, preserving
the current theme. Also disable the biome overlay in STEMCraft if it is no longer
needed. No changes are made to terrain tiles or stored region names.
