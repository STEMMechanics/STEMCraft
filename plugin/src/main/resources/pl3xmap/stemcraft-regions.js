/* STEMCraft named regions: bounded vector tiles for Pl3xMap 26.2-554. */
(() => {
  "use strict";
  const MAX_TILES = 64;
  const MAX_REQUESTS = 4;
  const WORLD_LIMIT = 30000000;

  function planTiles(bounds, size, settings) {
    const minX = Math.max(-WORLD_LIMIT, bounds.minX), maxX = Math.min(WORLD_LIMIT, bounds.maxX);
    const minZ = Math.max(-WORLD_LIMIT, bounds.minZ), maxZ = Math.min(WORLD_LIMIT, bounds.maxZ);
    if (![minX, maxX, minZ, maxZ].every(Number.isFinite) || minX >= maxX || minZ >= maxZ) return [];
    const blocksPerPixel = Math.max((maxX - minX) / Math.max(1, size.x), (maxZ - minZ) / Math.max(1, size.y));
    let level = Math.max(0, Math.min(settings.maxLevel, Math.ceil(Math.log2(blocksPerPixel / settings.sampleBlocks))));
    for (; level <= settings.maxLevel; level++) {
      const span = settings.sampleBlocks * 2 ** level * settings.tileCells;
      const x0 = Math.floor(minX / span), x1 = Math.ceil(maxX / span) - 1;
      const z0 = Math.floor(minZ / span), z1 = Math.ceil(maxZ / span) - 1;
      if ((x1 - x0 + 1) * (z1 - z0 + 1) > MAX_TILES) continue;
      const tiles = [];
      for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++)
        tiles.push({key: `${level}/${x}/${z}`, level, x, z});
      return tiles;
    }
    return [];
  }

  function colour(argb) {
    return {colour: `#${(argb & 0xffffff).toString(16).padStart(6, "0")}`, opacity: (argb >>> 24) / 255};
  }
  function styleIndex(id, length) {
    let hash = 0;
    for (let i = 0; i < id.length; i++) hash = (Math.imul(hash, 31) + id.charCodeAt(i)) | 0;
    return ((hash % length) + length) % length;
  }
  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, character => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[character]));
  }
  function drawTile(data, settings, leaflet, renderer) {
    const group = leaflet.layerGroup();
    for (const region of data.regions) {
      const style = settings.styles[styleIndex(region.id, settings.styles.length)];
      const stroke = colour(style.strokeColour), fill = colour(style.fillColour);
      const coordinates = lines => lines.map(line => line.map(([x, z]) => [z, x]));
      leaflet.polygon(coordinates(region.rings), {
        stroke: false, fill: true, fillColor: fill.colour, fillOpacity: fill.opacity,
        fillRule: "evenodd", renderer
      }).bindTooltip(`${escapeHtml(region.name)}<br><small>${escapeHtml(region.type)}</small>`, {
        permanent: settings.permanentLabels
      }).addTo(group);
      if (region.outlines.length) leaflet.polyline(coordinates(region.outlines), {
        color: stroke.colour, opacity: stroke.opacity, weight: style.lineThickness,
        interactive: false, renderer
      }).addTo(group);
    }
    return group;
  }

  class ViewportLayer {
    constructor(map, layer, world, endpoint, leaflet, request = fetch) {
      this.map = map; this.layer = layer; this.world = world; this.endpoint = endpoint;
      this.leaflet = leaflet; this.request = request;
      this.tiles = new Map(); this.active = new Map(); this.wanted = new Set(); this.queue = [];
      this.generation = 0; this.disposed = false; this.warned = false;
      if (!map.getPane("stemcraft-regions")) map.createPane("stemcraft-regions").style.zIndex = "350";
      this.renderer = leaflet.canvas({pane: "stemcraft-regions"});
      this.onMove = () => {
        clearTimeout(this.moveTimer);
        this.moveTimer = setTimeout(() => this.update(), 150);
      };
      this.onShow = () => this.refresh();
      this.onHide = () => this.hide();
      this.onChange = () => { if (this.visible()) this.refresh(); };
      map.on("moveend zoomend resize", this.onMove);
      layer.on("add", this.onShow); layer.on("remove", this.onHide);
      world.eventSource?.addEventListener("stemcraft-regions", this.onChange);
      if (this.visible()) this.refresh();
    }
    visible() { return !this.disposed && this.map.hasLayer(this.layer); }
    url(parameters) {
      const url = new URL(this.endpoint);
      url.searchParams.set("world", this.world.name);
      for (const [key, value] of Object.entries(parameters)) url.searchParams.set(key, value);
      return url.href;
    }
    async json(url, signal) {
      const response = await this.request(url, {cache: "no-store", signal});
      if (!response.ok) throw Object.assign(new Error(`Region tile request failed (${response.status})`), {status: response.status});
      return response.json();
    }
    async refresh() {
      if (!this.visible()) return;
      clearTimeout(this.pollTimer);
      this.manifestRequest?.abort();
      const controller = this.manifestRequest = new AbortController();
      try {
        const settings = await this.json(this.url({}), controller.signal);
        if (controller.signal.aborted || !this.visible()) return;
        if (this.settings?.revision !== settings.revision) this.resetTiles();
        this.settings = settings;
        this.update();
        this.pollTimer = setTimeout(() => this.refresh(), Math.max(60, settings.refreshSeconds) * 1000);
      } catch (error) {
        if (error.name === "AbortError" || controller.signal.aborted || !this.visible()) return;
        // Other map worlds legitimately have no named-region data.
        if (error.status === 404 || error.status === 503) { this.resetTiles(); this.settings = undefined; }
        if (error.status !== 404 && error.status !== 503) this.warn(error);
        this.pollTimer = setTimeout(() => this.refresh(), 60000);
      } finally {
        if (this.manifestRequest === controller) this.manifestRequest = undefined;
      }
    }
    update() {
      if (!this.visible() || !this.settings) return;
      const bounds = this.map.getBounds();
      const plan = planTiles({minX: bounds.getWest(), maxX: bounds.getEast(),
        minZ: bounds.getSouth(), maxZ: bounds.getNorth()}, this.map.getSize(), this.settings);
      this.wanted = new Set(plan.map(tile => tile.key));
      for (const [key, group] of this.tiles) if (!this.wanted.has(key)) {
        this.layer.removeLayer(group); this.tiles.delete(key);
      }
      for (const [key, work] of this.active) if (!this.wanted.has(key)) {
        work.controller.abort(); this.active.delete(key);
      }
      this.queue = plan.filter(tile => !this.tiles.has(tile.key) && !this.active.has(tile.key));
      this.pump();
    }
    pump() {
      while (this.visible() && this.active.size < MAX_REQUESTS && this.queue.length) {
        const tile = this.queue.shift();
        const work = {controller: new AbortController(), generation: this.generation};
        this.active.set(tile.key, work);
        const {level, x, z} = tile;
        this.json(this.url({level, x, z, v: this.settings.revision}), work.controller.signal).then(data => {
          if (work.controller.signal.aborted || work.generation !== this.generation
            || !this.visible() || !this.wanted.has(tile.key)) return;
          const group = drawTile(data, this.settings, this.leaflet, this.renderer);
          group.addTo(this.layer); this.tiles.set(tile.key, group);
          this.warned = false;
        }).catch(error => {
          if (error.name === "AbortError" || work.controller.signal.aborted || !this.visible()) return;
          this.warn(error);
          clearTimeout(this.retryTimer);
          this.retryTimer = setTimeout(() => this.update(), 5000);
        }).finally(() => {
          if (this.active.get(tile.key) === work) this.active.delete(tile.key);
          this.pump();
        });
      }
    }
    warn(error) {
      if (!this.warned) console.warn("STEMCraft region overlay:", error);
      this.warned = true;
    }
    resetTiles() {
      this.generation++;
      for (const work of this.active.values()) work.controller.abort();
      this.active.clear(); this.queue = []; this.wanted.clear();
      for (const group of this.tiles.values()) this.layer.removeLayer(group);
      this.tiles.clear();
    }
    hide() {
      clearTimeout(this.moveTimer); clearTimeout(this.pollTimer); clearTimeout(this.retryTimer);
      this.manifestRequest?.abort(); this.manifestRequest = undefined;
      this.resetTiles(); this.settings = undefined;
    }
    destroy() {
      this.disposed = true; this.hide();
      this.map.off("moveend zoomend resize", this.onMove);
      this.layer.off("add", this.onShow); this.layer.off("remove", this.onHide);
      this.world.eventSource?.removeEventListener("stemcraft-regions", this.onChange);
      this.renderer.remove();
    }
  }

  function install(browser, document) {
    const endpoint = new URL(document.currentScript?.dataset.endpoint || "/stemcraft/named-regions", document.baseURI).href;
    const controllers = new Map();
    browser.addEventListener("overlayadded", event => {
      if (event.detail.key !== "stemcraft_named_regions") return;
      const map = browser.pl3xmap?.map, world = browser.pl3xmap?.worldManager.currentWorld;
      if (!map || !world) return;
      controllers.get(event.detail)?.destroy();
      controllers.set(event.detail, new ViewportLayer(map, event.detail, world, endpoint, browser.L));
    });
    browser.addEventListener("overlayremoved", event => {
      controllers.get(event.detail)?.destroy(); controllers.delete(event.detail);
    });
    browser.addEventListener("beforeunload", () => {
      for (const controller of controllers.values()) controller.destroy();
      controllers.clear();
    });
  }
  if (typeof module !== "undefined" && module.exports)
    module.exports = {planTiles, drawTile, styleIndex, escapeHtml, ViewportLayer, MAX_TILES, MAX_REQUESTS};
  if (typeof window !== "undefined" && typeof document !== "undefined") install(window, document);
})();
