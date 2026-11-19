/* STEMCraft full-region viewport renderer for Pl3xMap 26.2-554. */
(() => {
  "use strict";
  const MAX_REQUESTS = 6;
  const MAX_CACHED_REGIONS = 64;
  const PROGRESS_DELAY_MS = 350;
  const REGION_CACHE_DATABASE = "stemcraft-pl3xmap-regions";
  const REGION_CACHE_STORE = "regions";
  let regionCacheDatabase;

  function openRegionCache() {
    if (typeof indexedDB === "undefined") return Promise.resolve(null);
    if (!regionCacheDatabase) {
      regionCacheDatabase = new Promise(resolve => {
        const request = indexedDB.open(REGION_CACHE_DATABASE, 1);
        request.onupgradeneeded = () => {
          const database = request.result;
          if (!database.objectStoreNames.contains(REGION_CACHE_STORE))
            database.createObjectStore(REGION_CACHE_STORE, {keyPath: "key"});
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => resolve(null);
        request.onblocked = () => resolve(null);
      }).catch(() => null);
    }
    return regionCacheDatabase;
  }

  async function readPersistentRegion(key) {
    const database = await openRegionCache();
    if (!database) return undefined;
    return new Promise(resolve => {
      try {
        const request = database.transaction(REGION_CACHE_STORE, "readonly").objectStore(REGION_CACHE_STORE).get(key);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => resolve(undefined);
      } catch (_) { resolve(undefined); }
    });
  }

  async function writePersistentRegion(key, contentHash, data) {
    const database = await openRegionCache();
    if (!database) return;
    return new Promise(resolve => {
      try {
        const transaction = database.transaction(REGION_CACHE_STORE, "readwrite");
        transaction.objectStore(REGION_CACHE_STORE).put({key, contentHash, data});
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => resolve();
        transaction.onabort = () => resolve();
      } catch (_) { resolve(); }
    });
  }

  async function deletePersistentRegion(key) {
    const database = await openRegionCache();
    if (!database) return;
    return new Promise(resolve => {
      try {
        const transaction = database.transaction(REGION_CACHE_STORE, "readwrite");
        transaction.objectStore(REGION_CACHE_STORE).delete(key);
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => resolve();
        transaction.onabort = () => resolve();
      } catch (_) { resolve(); }
    });
  }

  function regionIdMismatch(expected, actual, url, source) {
    return new Error(`Region file ID mismatch: expected ${expected}, received ${String(actual)} (${source}: ${url})`);
  }

  function styleIndex(id, length) {
    let hash = 0;
    for (let i = 0; i < id.length; i++) hash = (Math.imul(hash, 31) + id.charCodeAt(i)) | 0;
    return ((hash % length) + length) % length;
  }

  function colour(argb) {
    return {
      colour: `#${(argb & 0xffffff).toString(16).padStart(6, "0")}`,
      opacity: (argb >>> 24) / 255
    };
  }

  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, character => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    }[character]));
  }

  function planRegions(bounds, regions) {
    const minX = Math.min(bounds.minX, bounds.maxX), maxX = Math.max(bounds.minX, bounds.maxX);
    const minZ = Math.min(bounds.minZ, bounds.maxZ), maxZ = Math.max(bounds.minZ, bounds.maxZ);
    return regions.filter(region => {
      const [left, top, right, bottom] = region.bounds;
      return right >= minX && left <= maxX && bottom >= minZ && top <= maxZ;
    }).map(region => {
      const [left, top, right, bottom] = region.bounds;
      const dx = (left + right) / 2 - (minX + maxX) / 2;
      const dz = (top + bottom) / 2 - (minZ + maxZ) / 2;
      return {...region, distance: dx * dx + dz * dz};
    }).sort((a, b) => a.distance - b.distance);
  }

  function pointInRing(x, z, ring) {
    let inside = false;
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [xi, zi] = ring[i], [xj, zj] = ring[j];
      const cross = (x - xi) * (zj - zi) - (z - zi) * (xj - xi);
      if (Math.abs(cross) < 1e-7 && x >= Math.min(xi, xj) && x <= Math.max(xi, xj)
        && z >= Math.min(zi, zj) && z <= Math.max(zi, zj)) return true;
      if ((zi > z) !== (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside;
    }
    return inside;
  }

  function contains(region, x, z) {
    const [minX, minZ, maxX, maxZ] = region.bounds;
    if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
    let inside = false;
    for (const ring of region.data.rings) if (pointInRing(x, z, ring)) inside = !inside;
    return inside;
  }

  function installControlStyles(document) {
    if (document.getElementById("stemcraft-region-controls-style")) return;
    const style = document.createElement("style");
    style.id = "stemcraft-region-controls-style";
    style.textContent = `
      .stemcraft-region-search-control { position: relative; transition: transform 0.2s ease-in; }
      #sidebar.sidebar--expanded ~ .stemcraft-region-search-control {
        transform: translateX(calc(-1 * var(--ui-sidebar-width, 25rem)));
      }
      .stemcraft-region-search-panel {
        position: absolute; top: 0; right: calc(100% + 0.6rem); width: min(30rem, calc(100vw - 6rem)); height: auto;
        display: flex; flex-direction: column; gap: 0.7rem; z-index: 1200;
      }
      .stemcraft-region-search-panel[hidden], .stemcraft-region-progress[hidden] { display: none !important; }
      .stemcraft-region-search-heading { font-weight: 700; }
      .stemcraft-region-search-input {
        box-sizing: border-box; width: 100%; padding: 0.7rem; border-radius: var(--ui-border-radius, 0.3rem);
        border: var(--ui-border, 1px solid #777); background: var(--ui-background, #171717);
        color: var(--ui-text, #fff); font: inherit;
      }
      .stemcraft-region-search-results { display: grid; gap: 0.4rem; max-height: min(55vh, 36rem); overflow: auto; }
      .stemcraft-region-search-result {
        display: block; box-sizing: border-box; width: 100%; height: auto; padding: 0.65rem 0.75rem;
        line-height: 1.25; white-space: normal; text-align: left;
      }
      .stemcraft-region-search-result small { display: block; margin-top: 0.25rem; opacity: 0.75; }
      .stemcraft-region-search-status { opacity: 0.8; font-size: 0.9em; }
      .stemcraft-region-progress { display: flex; align-items: center; gap: 0.65rem; white-space: nowrap; }
      .stemcraft-region-spinner {
        box-sizing: border-box; width: 1.25rem; height: 1.25rem; border-radius: 50%;
        border: 0.2rem solid currentColor; border-right-color: transparent; animation: stemcraft-region-spin 0.8s linear infinite;
      }
      @keyframes stemcraft-region-spin { to { transform: rotate(360deg); } }
      @media (max-width: 600px) {
        .stemcraft-region-search-panel { width: min(25rem, calc(100vw - 5rem)); }
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  }

  function drawRegion(data, settings, leaflet, renderer, scale) {
    const style = settings.styles[styleIndex(data.id, settings.styles.length)];
    const stroke = colour(style.strokeColour), fill = colour(style.fillColour);
    const content = `${escapeHtml(data.name)}<br><small>${escapeHtml(data.type)}</small>`;
    // Match Pl3xMap's marker conversion: block centers are scaled by 2^-maxZoomOut.
    const rings = data.rings.map(ring => ring.map(([x, z]) => [(z + 0.5) * scale, (x + 0.5) * scale]));
    const group = leaflet.layerGroup();
    const polygon = leaflet.polygon(rings, {
      stroke: true,
      color: stroke.colour,
      opacity: stroke.opacity,
      weight: style.lineThickness,
      fill: true,
      fillColor: fill.colour,
      fillOpacity: fill.opacity,
      fillRule: "nonzero",
      renderer,
      interactive: false,
      smoothFactor: 0,
      noClip: false
    }).addTo(group);

    if (settings.permanentLabels) {
      leaflet.tooltip({permanent: true, direction: "center", opacity: 1})
        .setLatLng(polygon.getBounds().getCenter()).setContent(content).addTo(group);
    }
    return group;
  }

  class ViewportLayer {
    constructor(map, layer, world, directory, leaflet, request = fetch.bind(globalThis)) {
      this.map = map; this.layer = layer; this.world = world; this.directory = directory;
      this.leaflet = leaflet; this.request = request;
      this.document = map.getContainer().ownerDocument;
      installControlStyles(this.document);
      this.coordinateScale = 1 / Math.pow(2, map.getMaxZoomOut());
      this.groups = new Map(); this.active = new Map(); this.cache = new Map(); this.dataByRegion = new Map();
      this.failedRegions = new Map();
      this.wanted = new Set(); this.queue = []; this.generation = 0;
      this.disposed = false; this.warned = false; this.manifestLoading = false;
      this.searchResults = []; this.pendingHighlightId = undefined; this.highlightLayer = undefined;
      this.progressDelayTimer = undefined;
      this.hoverTooltip = leaflet.tooltip({direction: "top", sticky: true, opacity: 0.95});
      this.hoveredRegionId = undefined;
      if (!map.getPane("stemcraft-regions")) map.createPane("stemcraft-regions").style.zIndex = "450";
      if (!map.getPane("stemcraft-region-highlight")) map.createPane("stemcraft-region-highlight").style.zIndex = "460";
      this.renderer = leaflet.canvas({pane: "stemcraft-regions"});
      this.highlightRenderer = leaflet.canvas({pane: "stemcraft-region-highlight"});
      this.searchControl = this.createSearchControl();
      this.progressControl = this.createProgressControl();
      this.searchControl.addTo(map);
      this.progressControl.addTo(map);
      this.onMove = () => {
        this.hideRegionTitle();
        clearTimeout(this.moveTimer);
        this.moveTimer = setTimeout(() => this.update(), 120);
      };
      this.onShow = () => this.refresh();
      this.onHide = () => this.hide();
      this.onHover = event => this.showRegionTitle(event);
      this.onPointerLeave = () => this.hideRegionTitle();
      this.onMapClick = event => {
        if (!this.searchRoot?.contains(event.originalEvent?.target)) this.closeSearch();
      };
      map.on("moveend zoomend resize", this.onMove);
      map.on("mousemove", this.onHover);
      map.on("click", this.onMapClick);
      map.getContainer().addEventListener("mouseleave", this.onPointerLeave);
      layer.on("add", this.onShow); layer.on("remove", this.onHide);
      if (this.visible()) this.refresh();
    }

    createSearchControl() {
      const control = this.leaflet.control({position: "topright"});
      control.onAdd = () => {
        const root = this.document.createElement("div");
        root.className = "stemcraft-region-search-control";
        const button = this.document.createElement("button");
        button.type = "button";
        button.className = "leaflet-control-button stemcraft-region-search-toggle";
        button.title = "Search named regions";
        button.setAttribute("aria-label", "Search named regions");
        button.setAttribute("aria-expanded", "false");
        button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="10.8" cy="10.8" r="6.8"></circle><path d="m16 16 5 5"></path></svg>';

        const panel = this.document.createElement("div");
        panel.className = "leaflet-control-panel stemcraft-region-search-panel";
        panel.hidden = true;
        const heading = this.document.createElement("div");
        heading.className = "stemcraft-region-search-heading";
        heading.textContent = "Find a region";
        const input = this.document.createElement("input");
        input.className = "stemcraft-region-search-input";
        input.type = "search";
        input.autocomplete = "off";
        input.placeholder = "Region name or biome";
        input.setAttribute("aria-label", "Search region names and biomes");
        const results = this.document.createElement("div");
        results.className = "stemcraft-region-search-results";
        results.setAttribute("role", "listbox");
        const status = this.document.createElement("div");
        status.className = "stemcraft-region-search-status";
        panel.append(heading, input, results, status);
        root.append(button, panel);

        button.addEventListener("click", () => this.toggleSearch());
        input.addEventListener("input", () => this.renderSearchResults());
        input.addEventListener("keydown", event => {
          if (event.key === "Escape") {
            event.stopPropagation(); this.closeSearch();
          } else if (event.key === "Enter" && this.searchResults.length) {
            event.preventDefault(); this.focusRegion(this.searchResults[0]);
          }
        });
        this.searchRoot = root; this.searchButton = button; this.searchPanel = panel;
        this.searchInput = input; this.searchList = results; this.searchStatus = status;
        this.leaflet.DomEvent.disableClickPropagation(root);
        this.leaflet.DomEvent.disableScrollPropagation(root);
        return root;
      };
      return control;
    }

    createProgressControl() {
      const control = this.leaflet.control({position: "bottomright"});
      control.onAdd = () => {
        const root = this.document.createElement("div");
        root.className = "leaflet-control-panel stemcraft-region-progress";
        root.hidden = true;
        const spinner = this.document.createElement("span");
        spinner.className = "stemcraft-region-spinner";
        spinner.setAttribute("aria-hidden", "true");
        const label = this.document.createElement("span");
        label.setAttribute("role", "status"); label.setAttribute("aria-live", "polite");
        root.append(spinner, label);
        this.progressRoot = root; this.progressLabel = label;
        return root;
      };
      return control;
    }

    toggleSearch() {
      if (this.searchPanel.hidden) {
        this.searchPanel.hidden = false;
        this.searchButton.setAttribute("aria-expanded", "true");
        this.renderSearchResults();
        requestAnimationFrame(() => this.searchInput.focus());
      } else this.closeSearch();
    }

    closeSearch() {
      if (!this.searchPanel) return;
      this.searchPanel.hidden = true;
      this.searchButton.setAttribute("aria-expanded", "false");
    }

    renderSearchResults() {
      if (!this.searchList) return;
      this.searchList.replaceChildren();
      this.searchResults = [];
      if (!this.settings) {
        this.searchStatus.textContent = "Loading region list…";
        return;
      }
      const query = this.searchInput.value.trim().toLocaleLowerCase();
      if (!query) {
        this.searchStatus.textContent = "Type a region name or biome to search.";
        return;
      }
      const matches = this.settings.regions.filter(region =>
        `${region.name || region.id} ${region.type || ""}`.toLocaleLowerCase().includes(query))
        .sort((a, b) => String(a.name || a.id).localeCompare(String(b.name || b.id)));
      this.searchResults = matches.slice(0, 30);
      for (const region of this.searchResults) {
        const button = this.document.createElement("button");
        button.type = "button"; button.className = "stemcraft-region-search-result";
        button.setAttribute("role", "option");
        const name = this.document.createElement("span");
        name.textContent = region.name || region.id;
        const type = this.document.createElement("small");
        type.textContent = region.type || "Region";
        button.append(name, type);
        button.addEventListener("click", () => this.focusRegion(region));
        this.searchList.appendChild(button);
      }
      this.searchStatus.textContent = matches.length
        ? `${matches.length} match${matches.length === 1 ? "" : "es"}${matches.length > 30 ? " — showing first 30" : ""}`
        : "No matching regions.";
    }

    focusRegion(region) {
      const [minX, minZ, maxX, maxZ] = region.bounds;
      const scale = this.coordinateScale;
      this.removeHighlight();
      this.pendingHighlightId = region.id;
      const cached = this.cache.get(region.id);
      const data = this.dataByRegion.get(region.id)?.data
        || (cached?.contentHash === region.contentHash ? cached.data : undefined);
      if (data) this.showHighlight(data);
      this.closeSearch();
      const bounds = [
        [(minZ + 0.5) * scale, (minX + 0.5) * scale],
        [(maxZ + 0.5) * scale, (maxX + 0.5) * scale]
      ];
      const options = {padding: [48, 48]};
      const maxZoom = this.map.getMaxZoom();
      if (Number.isFinite(maxZoom)) options.maxZoom = maxZoom;
      this.map.fitBounds(bounds, options);
    }

    updateProgress(force = false) {
      if (!this.progressRoot || !this.progressLabel) return;
      const pending = this.active.size + this.queue.length;
      const loading = this.visible() && (this.manifestLoading || pending > 0 || this.failedRegions.size > 0);
      if (!loading) {
        clearTimeout(this.progressDelayTimer); this.progressDelayTimer = undefined;
        this.progressRoot.hidden = true;
        return;
      }
      if (this.progressRoot.hidden && !force) {
        if (this.progressDelayTimer === undefined) {
          this.progressDelayTimer = setTimeout(() => {
            this.progressDelayTimer = undefined;
            this.updateProgress(true);
          }, PROGRESS_DELAY_MS);
        }
        return;
      }
      this.progressRoot.hidden = false;
      if (this.manifestLoading) this.progressLabel.textContent = "Loading region list…";
      else if (pending === 0 && this.failedRegions.size > 0) {
        const error = this.failedRegions.values().next().value;
        const reason = error?.status || error?.name || "request error";
        this.progressLabel.textContent = `Region load failed (${reason}); retrying`;
      }
      else {
        const loaded = [...this.wanted].filter(id => this.groups.has(id)).length;
        const failures = this.failedRegions.size ? ` · ${this.failedRegions.size} failed` : "";
        this.progressLabel.textContent = `Loading regions ${loaded} of ${this.wanted.size}${failures}`;
      }
    }

    showHighlight(data) {
      this.removeHighlight();
      this.pendingHighlightId = undefined;
      const rings = data.rings.map(ring => ring.map(([x, z]) => [
        (z + 0.5) * this.coordinateScale, (x + 0.5) * this.coordinateScale
      ]));
      this.highlightLayer = this.leaflet.polygon(rings, {
        pane: "stemcraft-region-highlight", renderer: this.highlightRenderer,
        stroke: true, color: "#fff23b", opacity: 1, weight: 5,
        fill: false, interactive: false, smoothFactor: 0, dashArray: "8 6", lineCap: "round", lineJoin: "round"
      }).addTo(this.map);
    }

    removeHighlight() {
      clearTimeout(this.highlightTimer);
      if (this.highlightLayer && this.map.hasLayer(this.highlightLayer)) this.map.removeLayer(this.highlightLayer);
      this.highlightLayer = undefined;
    }

    visible() { return !this.disposed && this.map.hasLayer(this.layer); }

    async json(path, revision, signal, fresh = false) {
      const url = new URL(path, this.directory);
      url.searchParams.set("v", fresh ? Date.now() : revision);
      // Snapshot paths and revision query parameters are immutable; only metadata is mutable.
      const response = await this.request(url.href, {cache: fresh ? "no-store" : "force-cache", signal});
      if (!response.ok) throw Object.assign(new Error(`Region file request failed (${response.status})`), {status: response.status});
      return response.json();
    }

    async loadRegion(feature, signal) {
      const cacheKey = `${new URL(this.directory).href}|${feature.id}`;
      const path = `${this.settings.snapshot}/${feature.file}`;
      if (feature.contentHash) {
        const stored = await readPersistentRegion(cacheKey);
        if (signal.aborted) {
          const error = new Error("Region request was aborted"); error.name = "AbortError"; throw error;
        }
        if (stored?.contentHash === feature.contentHash) {
          if (stored.data?.id === feature.id) return stored.data;
          // Older builds cached the response before validating its ID. Remove such entries.
          await deletePersistentRegion(cacheKey);
        }
      }
      let data = await this.json(path, this.settings.revision, signal);
      if (data?.id !== feature.id) {
        // A stale HTTP cache can return a different file for an immutable snapshot URL.
        // Bypass it once, with a unique query value, before marking this region as failed.
        data = await this.json(path, this.settings.revision, signal, true);
      }
      if (data?.id !== feature.id) {
        await deletePersistentRegion(cacheKey);
        const url = new URL(path, this.directory).href;
        throw regionIdMismatch(feature.id, data?.id, url, "fresh response");
      }
      if (feature.contentHash) writePersistentRegion(cacheKey, feature.contentHash, data).catch(() => {});
      return data;
    }

    async refresh() {
      if (!this.visible()) return;
      this.manifestRequest?.abort();
      const controller = this.manifestRequest = new AbortController();
      this.manifestLoading = true;
      this.updateProgress();
      try {
        const metadata = await this.json("metadata.json", 0, controller.signal, true);
        if (!/^snapshots\/[0-9a-f]{32}$/.test(metadata.snapshot))
          throw new Error("Region snapshot path is invalid");
        const index = await this.json(`${metadata.snapshot}/index.json`, metadata.revision, controller.signal);
        if (controller.signal.aborted || !this.visible()) return;
        if (index.revision !== metadata.revision)
          throw Object.assign(new Error("Region index is still being published"), {status: 503});
        if (this.settings?.revision !== metadata.revision) this.resetRegions();
        this.settings = {...metadata, regions: index.regions};
        this.renderSearchResults();
        this.update();
      } catch (error) {
        if (error.name === "AbortError" || controller.signal.aborted || !this.visible()) return;
        if (error.status !== 404 && error.status !== 503) this.warn(error);
        clearTimeout(this.pollTimer);
        this.pollTimer = setTimeout(() => this.refresh(), error.status === 404 || error.status === 503 ? 2000 : 5000);
      } finally {
        if (this.manifestRequest === controller) {
          this.manifestRequest = undefined;
          this.manifestLoading = false;
          this.updateProgress();
        }
      }
    }

    update() {
      if (!this.visible() || !this.settings) return;
      clearTimeout(this.retryTimer); this.retryTimer = undefined;
      this.failedRegions.clear();
      const bounds = this.map.getBounds();
      const scale = this.coordinateScale;
      const plan = planRegions({minX: bounds.getWest() / scale - 0.5, maxX: bounds.getEast() / scale - 0.5,
        minZ: bounds.getSouth() / scale - 0.5, maxZ: bounds.getNorth() / scale - 0.5}, this.settings.regions);
      this.wanted = new Set(plan.map(region => region.id));
      for (const [id, group] of this.groups) if (!this.wanted.has(id) && this.layer.hasLayer(group))
        this.layer.removeLayer(group);
      for (const [id, work] of this.active) if (!this.wanted.has(id)) {
        work.controller.abort(); this.active.delete(id);
      }
      for (const region of plan) {
        const group = this.groups.get(region.id);
        if (!group) continue;
        if (!this.layer.hasLayer(group)) this.layer.addLayer(group);
        this.groups.delete(region.id); this.groups.set(region.id, group);
        const cached = this.cache.get(region.id);
        if (cached) { this.cache.delete(region.id); this.cache.set(region.id, cached); }
      }
      this.trimCachedGroups();
      this.queue = plan.filter(region => !this.groups.has(region.id) && !this.active.has(region.id));
      this.pump();
      this.updateProgress();
    }

    trimCachedGroups() {
      let retained = 0;
      for (const id of this.groups.keys()) if (!this.wanted.has(id)) retained++;
      if (retained <= MAX_CACHED_REGIONS) return;
      for (const [id, group] of this.groups) {
        if (this.wanted.has(id)) continue;
        this.groups.delete(id); this.dataByRegion.delete(id); this.cache.delete(id);
        if (this.layer.hasLayer(group)) this.layer.removeLayer(group);
        if (--retained <= MAX_CACHED_REGIONS) break;
      }
    }

    pump() {
      while (this.visible() && this.active.size < MAX_REQUESTS && this.queue.length) {
        const feature = this.queue.shift();
        const work = {controller: new AbortController(), generation: this.generation};
        this.active.set(feature.id, work);
        const cached = this.cache.get(feature.id);
        const memoryHit = cached !== undefined && cached.contentHash === feature.contentHash
          && cached.data?.id === feature.id;
        if (cached && !memoryHit) this.cache.delete(feature.id);
        const load = memoryHit ? Promise.resolve(cached.data)
          : this.loadRegion(feature, work.controller.signal);
        load.then(data => {
          if (work.controller.signal.aborted || work.generation !== this.generation
            || !this.visible() || !this.wanted.has(feature.id)) return;
          if (data?.id !== feature.id) {
            const url = new URL(`${this.settings.snapshot}/${feature.file}`, this.directory).href;
            throw regionIdMismatch(feature.id, data?.id, url, memoryHit ? "memory cache" : "loaded data");
          }
          if (!memoryHit) this.remember(feature.id, data, feature.contentHash);
          const group = drawRegion(data, this.settings, this.leaflet, this.renderer, this.coordinateScale);
          group.addTo(this.layer);
          this.groups.set(feature.id, group);
          this.dataByRegion.set(feature.id, {data, bounds: feature.bounds});
          this.failedRegions.delete(feature.id);
          if (this.pendingHighlightId === feature.id) this.showHighlight(data);
          this.warned = false;
        }).catch(error => {
          if (error.name === "AbortError" || work.controller.signal.aborted || !this.visible()) return;
          if (!this.wanted.has(feature.id)) return;
          this.failedRegions.set(feature.id, error);
          if (error.status === 404) this.refresh();
          this.warn(error);
          this.updateProgress();
          clearTimeout(this.retryTimer);
          this.retryTimer = setTimeout(() => {
            this.retryTimer = undefined;
            this.update();
          }, 5000);
        }).finally(() => {
          if (this.active.get(feature.id) === work) this.active.delete(feature.id);
          this.pump();
        });
      }
      this.updateProgress();
    }

    remember(id, data, contentHash) {
      this.cache.delete(id);
      this.cache.set(id, {contentHash, data});
      while (this.cache.size > MAX_CACHED_REGIONS) this.cache.delete(this.cache.keys().next().value);
    }

    showRegionTitle(event) {
      if (!this.visible() || !this.settings) return this.hideRegionTitle();
      const x = event.latlng.lng / this.coordinateScale - 0.5;
      const z = event.latlng.lat / this.coordinateScale - 0.5;
      let hit = null;
      for (const region of this.dataByRegion.values()) {
        if (contains(region, x, z)) { hit = region.data; break; }
      }
      if (!hit) return this.hideRegionTitle();
      if (this.hoveredRegionId !== hit.id) {
        this.hoverTooltip.setContent(`${escapeHtml(hit.name)}<br><small>${escapeHtml(hit.type)}</small>`);
      }
      this.hoverTooltip.setLatLng(event.latlng);
      if (!this.map.hasLayer(this.hoverTooltip)) this.hoverTooltip.addTo(this.map);
      this.hoveredRegionId = hit.id;
    }

    hideRegionTitle() {
      if (this.map.hasLayer(this.hoverTooltip)) this.map.removeLayer(this.hoverTooltip);
      this.hoveredRegionId = undefined;
    }

    warn(error) {
      if (!this.warned) console.warn("STEMCraft region overlay:", error);
      this.warned = true;
    }

    resetRegions() {
      this.generation++;
      for (const work of this.active.values()) work.controller.abort();
      this.active.clear(); this.queue = []; this.wanted.clear(); this.cache.clear();
      for (const group of this.groups.values()) this.layer.removeLayer(group);
      this.groups.clear(); this.dataByRegion.clear(); this.hideRegionTitle();
      this.failedRegions.clear();
      this.pendingHighlightId = undefined; this.removeHighlight();
      this.updateProgress();
    }

    hide() {
      clearTimeout(this.moveTimer); clearTimeout(this.pollTimer); clearTimeout(this.retryTimer);
      this.manifestRequest?.abort(); this.manifestRequest = undefined;
      this.manifestLoading = false;
      this.resetRegions(); this.settings = undefined;
      this.renderSearchResults(); this.updateProgress();
    }

    destroy() {
      this.disposed = true; this.hide();
      this.map.off("moveend zoomend resize", this.onMove);
      this.map.off("mousemove", this.onHover);
      this.map.off("click", this.onMapClick);
      this.map.getContainer().removeEventListener("mouseleave", this.onPointerLeave);
      this.layer.off("add", this.onShow); this.layer.off("remove", this.onHide);
      this.searchControl.remove(); this.progressControl.remove();
      this.renderer.remove(); this.highlightRenderer.remove();
    }
  }

  function install(browser, document) {
    const controllers = new Map();
    const attach = layer => {
      if (!layer || layer.key !== "stemcraft_named_regions") return;
      const map = browser.pl3xmap?.map, world = browser.pl3xmap?.worldManager.currentWorld;
      if (!map || !world || controllers.has(layer)) return;
      const worldPath = world.name.replaceAll(":", "-");
      const directory = new URL(`tiles/${encodeURIComponent(worldPath)}/stemcraft-regions/`, document.baseURI).href;
      controllers.set(layer, new ViewportLayer(map, layer, world, directory, browser.L));
    };
    const detach = layer => {
      const controller = controllers.get(layer);
      if (!controller) return;
      controller.destroy(); controllers.delete(layer);
    };
    browser.addEventListener("overlayadded", event => attach(event.detail));
    browser.addEventListener("overlayremoved", event => detach(event.detail));
    const map = browser.pl3xmap?.map;
    const onLayerAdd = event => attach(event.layer), onLayerRemove = event => detach(event.layer);
    if (map?.on) {
      map.on("layeradd", onLayerAdd); map.on("layerremove", onLayerRemove);
      map.eachLayer?.(attach);
    }
    browser.addEventListener("beforeunload", () => {
      map?.off?.("layeradd", onLayerAdd); map?.off?.("layerremove", onLayerRemove);
      for (const controller of controllers.values()) controller.destroy();
      controllers.clear();
    });
  }

  if (typeof module !== "undefined" && module.exports)
    module.exports = {planRegions, styleIndex, escapeHtml, ViewportLayer, install, MAX_REQUESTS};
  if (typeof window !== "undefined" && typeof document !== "undefined") install(window, document);
})();
