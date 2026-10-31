const {test} = require('node:test');
const assert = require('node:assert/strict');
const {EventEmitter} = require('node:events');
const {mkdtempSync, writeFileSync, readFileSync, rmSync} = require('node:fs');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {spawnSync} = require('node:child_process');
const {planTiles, drawTile, styleIndex, escapeHtml, ViewportLayer, install, MAX_TILES, MAX_REQUESTS} =
  require('../../plugin/src/main/resources/pl3xmap/stemcraft-regions.js');
const settings = {revision: 1, tileCells: 32, sampleBlocks: 4, maxLevel: 19, refreshSeconds: 300,
  permanentLabels: false, styles: [{strokeColour: 0xff112233 | 0, fillColour: 0x40112233, lineThickness: 2}]};
const flush = () => new Promise(resolve => setImmediate(resolve));

test('tile plans bound work for large screens and the whole Minecraft world', () => {
  for (const extent of [128, 1000, 16000, 30000000]) for (const pixels of [320, 1920, 8000]) {
    const plan = planTiles({minX: -extent, maxX: extent, minZ: -extent, maxZ: extent}, {x: pixels, y: pixels}, settings);
    assert.ok(plan.length > 0 && plan.length <= MAX_TILES);
    assert.equal(new Set(plan.map(tile => tile.key)).size, plan.length);
  }
  assert.deepEqual(planTiles({minX: NaN, maxX: 1, minZ: 0, maxZ: 1}, {x: 100, y: 100}, settings), []);
});
test('negative coordinates and exact tile edges request only intersecting tiles', () => {
  const plan = planTiles({minX: -128, maxX: 128, minZ: -128, maxZ: 0}, {x: 1000, y: 1000}, settings);
  assert.deepEqual(plan.map(tile => tile.key), ['0/-1/-1', '0/0/-1']);
});

function leaflet() {
  const shapes = [];
  const group = () => ({children: new Set(), addLayer(item) { this.children.add(item); },
    removeLayer(item) { this.children.delete(item); }, addTo(parent) { parent.addLayer(this); return this; }});
  return {shapes, layerGroup: group, canvas: () => ({remove() {}}),
    polygon: (coordinates, options) => shape('polygon', coordinates, options),
    polyline: (coordinates, options) => shape('polyline', coordinates, options)};
  function shape(kind, coordinates, options) {
    const item = {kind, coordinates, options, bindTooltip(text, tooltipOptions) {
      this.tooltip = text; this.tooltipOptions = tooltipOptions; return this;
    }, addTo(parent) { parent.addLayer(this); return this; }};
    shapes.push(item); return item;
  }
}
test('drawing preserves holes, uses Minecraft axes, palette alpha and escaped tooltips', () => {
  const l = leaflet();
  drawTile({regions: [{id: 'region:1', name: '<img>', type: 'Forest & woods',
    rings: [[[0, 0], [8, 0], [8, 8], [0, 8]], [[2, 2], [4, 2], [4, 4], [2, 4]]],
    outlines: [[[0, 0], [8, 0]]]}]}, settings, l, {});
  assert.equal(l.shapes[0].coordinates.length, 2);
  assert.deepEqual(l.shapes[0].coordinates[0][1], [0, 8]);
  assert.equal(l.shapes[0].options.fillRule, 'evenodd');
  assert.equal(l.shapes[0].options.fillOpacity, 64 / 255);
  assert.match(l.shapes[0].tooltip, /&lt;img&gt;/);
  assert.match(l.shapes[0].tooltip, /Forest &amp; woods/);
  assert.equal(l.shapes[1].options.color, '#112233');
  assert.equal(l.shapes[1].options.interactive, false);
  assert.equal(escapeHtml(`'"`), '&#39;&quot;');
  assert.equal(styleIndex('region:forest', 8), 3); // Java String.hashCode / floorMod
});

function fixture(visible = true, extent = 1024) {
  const layer = new EventEmitter(); layer.children = new Set();
  layer.addLayer = item => layer.children.add(item); layer.removeLayer = item => layer.children.delete(item);
  const map = {visible, on() {}, off() {}, hasLayer: () => map.visible,
    getPane: () => ({}), createPane: () => ({style: {}}), getSize: () => ({x: extent, y: extent}),
    getBounds: () => ({getWest: () => 0, getEast: () => extent, getSouth: () => 0, getNorth: () => extent}),
    eachLayer: callback => (map.initialLayers || []).forEach(callback)};
  const events = new EventTarget();
  return {map, layer, events, world: {name: 'survival', eventSource: events}, leaflet: leaflet()};
}
test('hidden layers fetch nothing; hiding aborts requests and rejects late responses', async t => {
  const f = fixture(false), calls = [], pending = [];
  const request = async (url, options) => {
    calls.push({url, options});
    assert.equal(options.cache, 'no-store');
    if (new URL(url).pathname.endsWith('/metadata.json')) return {ok: true, json: async () => settings};
    // Deliberately ignore AbortSignal to test the stale-response guard too.
    return new Promise(resolve => pending.push({resolve, options}));
  };
  const controller = new ViewportLayer(f.map, f.layer, f.world, 'https://map.test/world-map/tiles/survival/stemcraft-regions/', f.leaflet, request);
  t.after(() => controller.destroy());
  await flush(); assert.equal(calls.length, 0);
  f.map.visible = true; f.layer.emit('add'); await flush();
  assert.equal(pending.length, MAX_REQUESTS);
  assert.equal(controller.active.size, MAX_REQUESTS);
  f.map.visible = false; f.layer.emit('remove');
  assert.ok(pending.every(item => item.options.signal.aborted));
  pending.forEach(item => item.resolve({ok: true, json: async () => ({regions: []})}));
  await flush();
  assert.equal(f.layer.children.size, 0); assert.equal(controller.active.size, 0);
  assert.equal(calls.length, 1 + MAX_REQUESTS);
});
test('default requests retain the global fetch receiver', async t => {
  const f = fixture(true, 128), calls = [];
  const originalFetch = globalThis.fetch;
  let controller;
  globalThis.fetch = async function (url, options) {
    assert.equal(this, globalThis);
    calls.push({url, options});
    return new URL(url).pathname.endsWith('/metadata.json')
      ? {ok: true, json: async () => settings}
      : {ok: true, json: async () => ({regions: []})};
  };
  t.after(() => {
    globalThis.fetch = originalFetch;
    controller?.destroy();
  });
  controller = new ViewportLayer(f.map, f.layer, f.world,
    'https://map.test/world-map/tiles/survival/stemcraft-regions/', f.leaflet);
  await flush();
  assert.equal(calls.length, 2);
});
test('installer attaches to a default-visible overlay added before the script', async t => {
  const f = fixture(true, 128), calls = [];
  f.layer.key = 'stemcraft_named_regions';
  f.map.initialLayers = [f.layer];
  const request = async (url, options) => {
    calls.push({url, options});
    return new URL(url).pathname.endsWith('/metadata.json')
      ? {ok: true, json: async () => settings}
      : {ok: true, json: async () => ({regions: []})};
  };
  const browser = new EventTarget();
  browser.pl3xmap = {map: f.map, worldManager: {currentWorld: f.world}};
  browser.L = f.leaflet;
  install(browser, {baseURI: 'https://map.test/world-map/'}, request);
  await flush();
  assert.equal(calls.length, 2);
  assert.equal(new URL(calls[0].url).pathname,
    '/world-map/tiles/survival/stemcraft-regions/metadata.json');
  browser.dispatchEvent(new Event('beforeunload'));
  await flush();
  assert.equal(calls.length, 2);
});
test('SSE invalidates visible tiles only when revision changes', async t => {
  const f = fixture(true, 128), calls = [];
  let revision = 1;
  const request = async url => {
    calls.push(url);
    return {ok: true, json: async () => !new URL(url).pathname.endsWith('/metadata.json') ? {regions: []} : {...settings, revision}};
  };
  const controller = new ViewportLayer(f.map, f.layer, f.world, 'https://map.test/world-map/tiles/survival/stemcraft-regions/', f.leaflet, request);
  t.after(() => controller.destroy());
  await flush(); assert.equal(calls.length, 2); assert.equal(f.layer.children.size, 1);
  assert.equal(new URL(calls[0]).pathname, '/world-map/tiles/survival/stemcraft-regions/metadata.json');
  assert.equal(new URL(calls[1]).pathname, '/world-map/tiles/survival/stemcraft-regions/0/0_0.json');
  assert.ok(calls.every(url => new URL(url).origin === 'https://map.test'));
  const old = [...f.layer.children][0];
  revision = 2; f.events.dispatchEvent(new Event('stemcraft-regions')); await flush();
  assert.equal(calls.length, 4); assert.equal(f.layer.children.size, 1);
  assert.ok(!f.layer.children.has(old)); assert.match(calls[3], /v=2/);
  f.events.dispatchEvent(new Event('stemcraft-regions')); await flush();
  assert.equal(calls.length, 5);
  controller.destroy(); revision = 3;
  f.events.dispatchEvent(new Event('stemcraft-regions')); await flush();
  assert.equal(calls.length, 5);
});
test('undiscovered static tiles stay empty without repeated failed requests', async t => {
  const f = fixture(true, 128), calls = [];
  const request = async url => {
    calls.push(url);
    return new URL(url).pathname.endsWith('/metadata.json')
      ? {ok: true, json: async () => settings} : {ok: false, status: 404};
  };
  const controller = new ViewportLayer(f.map, f.layer, f.world,
    'https://map.test/world-map/tiles/survival/stemcraft-regions/', f.leaflet, request);
  t.after(() => controller.destroy());
  await flush(); assert.equal(calls.length, 2);
  assert.equal(controller.tiles.size, 1);
  assert.equal(controller.warned, false);
  controller.update(); await flush();
  assert.equal(calls.length, 2);
});
test('installer is idempotent and uninstall preserves the existing theme', () => {
  const dir = mkdtempSync(join(tmpdir(), 'stemcraft-region-web-'));
  try {
    writeFileSync(join(dir, 'pl3xmap.js'), '');
    writeFileSync(join(dir, 'index.html'), '<html><body><script src="stemcraft-theme.js"></script></body></html>');
    const install = () => {
      const result = spawnSync('sh', [join(__dirname, 'install.sh'), dir], {encoding: 'utf8'});
      assert.equal(result.status, 0, result.stderr);
    };
    install(); install();
    const page = readFileSync(join(dir, 'index.html'), 'utf8');
    assert.equal((page.match(/id="stemcraft-regions"/g) || []).length, 1);
    assert.match(page, /stemcraft-regions.js\?v=[a-f0-9]{12}/);
    assert.doesNotMatch(page, /data-endpoint/);
    assert.match(page, /stemcraft-theme.js/);
    const result = spawnSync('sh', [join(__dirname, 'uninstall.sh'), dir], {encoding: 'utf8'});
    assert.equal(result.status, 0, result.stderr);
    const restored = readFileSync(join(dir, 'index.html'), 'utf8');
    assert.match(restored, /stemcraft-theme.js/); assert.doesNotMatch(restored, /stemcraft-regions/);
  } finally { rmSync(dir, {recursive: true, force: true}); }
});
