# Experimental named-region viewport renderer

Set `named-regions.map.biomes.viewport-tiles: true` to load full region polygons
progressively as their bounds enter the visible map area. Files are generated
from the same `NamedMapArea` boundaries used by Pl3xMap's native `false` mode.
The renderer writes a static snapshot and does not change the NamedRegions
database schema. Restart to rebuild the snapshot after region changes.

Set `viewport-tiles: false` and restart to return to Pl3xMap's native marker
polygons. STEMCraft updates or removes its managed browser script automatically.
For a separately hosted map directory, remove an old extension script with:

```sh
sh tools/pl3xmap-regions/uninstall.sh /path/to/your/map/web
```
