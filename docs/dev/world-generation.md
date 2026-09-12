# Custom dimensions and Survival portals

STEMCraft includes four experimental generators, each with one current implementation for `stemcraft:deep`, `stemcraft:wasteland`, `stemcraft:skylands`, and `stemcraft:faraway`. Short IDs also work. They use the existing world service, command permissions, configuration storage, and plugin lifecycle. No separate plugin, NMS, external noise library, or resource pack is required.

## Test worlds

Build with Java 25 using `./gradlew check :plugin:jar`. The plugin targets the repository's supported Paper 26.2 runtime and pinned Paper API dependency. Install the jar from `plugin/build/libs` on a test server, then use:

```text
/world listgenerators
/world generator info deep
/world create test_wasteland wasteland seed:12345
/world create test_skylands skylands seed:12345
/world create test_deep deep seed:12345
/world create test_faraway faraway seed:12345
```

Use the existing `/world` teleport, spawn, unload, and load commands. The permission remains `stemcraft.command.world`. `/world generators` aliases the existing list command. Namespaced IDs and colon-separated generator options are preserved by the world command; `seed:` remains a named option. Existing `flat`, `void`, `water`, normal generation and Bukkit `Plugin:id` generators remain supported. Bukkit generator references such as `STEMCraft:deep` resolve through the same registry after STEMCraft enables.

The built-ins accept no terrain option string. Availability is configured in `config.yml`:

```yaml
world-generation:
  deep: {enabled: true}
  wasteland: {enabled: true}
  skylands: {enabled: true}
  faraway: {enabled: true}
```

Restart after changing generator availability. Disabling one prevents new resolution and portal activation; already loaded worlds retain their attached generator. An unavailable generator fails world loading with a useful error instead of falling back to vanilla terrain.

## Current terrain and decoration

This subsystem is still in development. Old experimental generator implementations have been removed; all built-ins currently register generation version 1. Regenerate the test worlds after this consolidation. The version field remains in world metadata for future production migrations, but previous test layouts are not supported.

* The Deep has a sealed bedrock barrier at Y=181 for standard -64..320 bounds, solid rock above it, and an underground spawn at Y=53. Glow lichen, hanging glow berries and scattered glowstone light cave surfaces. Other height ranges scale proportionally.
* Skylands has a broad shared island mask across three bands, wider gaps between groups, and a guaranteed central starting island. Trees can grow on suitable surfaces in every band.
* Wasteland has rare grassy oases with shallow water, dry vegetation, and fossil remains of large animals.
* Landmarks have one potential placement per 32×32-chunk region (512×512 blocks). Suitable ground and clearance are required. Wasteland candidates split between 12–13-block animal skeletons and ruins; other dimensions use ruins. Skeletons have no chest. Ruins vary between collapsed shelters, buried foundations and abandoned camps; a quarter receive seeded loot.
* Shared surface details include grass, flowers, mushrooms, cave moss/azaleas, shroomlights and dripstone. Normal biome mob spawning remains enabled and obeys server settings. No villages, large dungeons, mob spawners or custom mobs are added.

All landmarks fit inside their owning chunk and use Paper's `LimitedRegion` API. Larger features must use the regional feature framework. Automated tests cover deterministic terrain, placement and loot; client rendering and live spawn rates still need server testing.

## Terrain and versioning

* **Wasteland:** buildable broad plains, mesas, ridged erosion, continuous warped dry channels, rare fertile regions, and deterministic small through basin-sized impacts. Impacts use a smooth bowl, raised rim and large-impact uplift.
* **Skylands:** true void below three relative-height island bands, four island scales, tapering undersides, caves, satellites, occasional vertical links and broad biome families. Shared ore clusters distribute coal, iron, copper, redstone and diamonds through the available rock, including upper islands.
* **The Deep:** solid terrain carved into broad connected caverns with shelves, pillars, passages, ravines, regional lakes and lower lava. The roof and bottom are sealed.
* **Faraway Lands:** base terrain composed with coordinate warping and quantisation, folded density, strong wall transitions, spires, cavities and repeated shelves. Large-scale regime fields vary the intensity. The central area is calm.

Terrain models are immutable per seed and height range. Coherent 2D/3D noise, fractal octaves, ridged noise and domain warp are shared. String-labelled seed derivation is fixed. Craters evaluate all potentially intersecting regions using `Math.floorDiv`, including at negative coordinates. Density, biome and base-height queries share the same cached model. A small blended central shelf/chamber ensures a solid spawn floor and headroom. Local ores and bounded trees use separate deterministic feature placement; no feature reads World blocks during generation.

World metadata stays in the existing world configuration:

```yaml
generator:
  key: stemcraft:faraway
  version: 1
  seed: 12345
```

The seed is descriptive metadata; the Minecraft world's stored seed remains authoritative. `/world setgenerator` is still an explicit administrator operation that changes the generator for future chunks after reload. It updates the pinned version. It does not regenerate existing chunks, and changing it on an explored world can create boundaries.

The registry and metadata retain a version field for future production use. Only the current implementation of each built-in is registered during development. Missing saved versions fail closed instead of silently substituting terrain. Once worlds enter production, incompatible terrain changes should receive a new version with an explicit migration policy. Deterministic fingerprints guard the current compositions.

Internal generator instances track generated chunk count and mean noise-generation time. These are diagnostics, not persistent metrics or benchmarks. No timing value affects terrain.

## Survival portal defaults

The existing `CustomPortals` feature now also manages Survival multiblocks. Administrator `/portal` destinations continue to work. Definitions are exported once to `plugins/STEMCraft/worlds/portals.yml`; activation and partial charging are saved separately in `worlds/portal-state.yml`. Main configuration:

```yaml
custom-portals:
  enabled: true
  survival:
    enabled: true
    max-portals: 512
```

Defaults allow activation in the exact world `survival`, environment `NORMAL`. Change each definition's `source-worlds` and `source-environments` for your server. `source-worlds: ['*']` permits every world name; supported environments are `NORMAL`, `NETHER`, and `THE_END`. World-name and environment restrictions must both pass. Generated dimensions also have environment `NORMAL`, so keep the name restriction if you only want access from your Overworld.

### The Deep

Build a four-block-wide, five-block-high reinforced deepslate frame (14 blocks):

```text
DDDD
D..D
D..D
D..D
DDDD
```

Right-click any frame block with an **Echo Core**. The default Survival pack adds recipes using existing custom-item and recipe services:

* Four reinforced deepslate blocks: `DID / IAI / DID`, with D = deepslate bricks, I = iron ingots, A = amethyst shard. This explicitly makes the otherwise unobtainable frame material craftable.
* Echo Core: `AEA / EDE / AEA`, with A = amethyst shard, E = echo shard, D = diamond.

The interior remains air and displays sculk particles. Activation consumes one core outside Creative. This establishes early access without requiring Nether or End materials; finding echo shards still requires exploring ancient cities.

For administrator testing, use STEMCraft's give command explicitly:

```text
/stemcraft:give @s stemcraft:echo_core 1
/stemcraft:give @s stemcraft:sky_eye 12
```

`sky_eye` is the item ID; **Celestial Eye** is its display name. These are plugin-managed items and do not appear as new item types in vanilla's creative inventory or `/minecraft:give`. The plain IDs `echo_core` and `sky_eye` also work through `/stemcraft:give`. Ensure the STEMCraft Survival pack is enabled if either item is absent.

### Wasteland reactor

Build a 5×5 smooth-stone foundation. Cover it with a 5×5 layer of copper blocks, replacing the centre with a redstone block. Above that centre place one copper block, then an upward lightning rod. The 24 air spaces surrounding the raised copper block become the entrance when real lightning hits the rod. Weather and Channeling lightning can activate it. Cosmetic lightning does not. For an administrator test, run `/minecraft:summon minecraft:lightning_bolt X Y Z` in the source world using the rod’s block coordinates. Lightning admission is handled for weather, Channeling and commands.

Activation uses electric particles and an explosion sound, without destroying the player's reactor. The entrance remains air: walk onto the copper platform around the central pillar to travel. Copper must match the configured material exactly; oxidised/waxed variants require edited definitions. Lightning retains its normal Minecraft effects.

### Skylands

Build a horizontal 5×5 ring with the four corners omitted (12 positions). Each position has end-stone bricks on the bottom and purpur on top:

```text
 PPP
P...P
P...P
P...P
 PPP
```

Charge each of the 12 purpur blocks by right-clicking with a **Celestial Eye**. Repeated clicks on a charged block do not consume another eye. Charges survive restart. Craft four eyes using `AEA / PNP / AEA`, with A = amethyst shard, E = Eye of Ender, P = purpur block, N = Nether Star. This requires three Nether Stars for a complete portal. It gates access through End materials rather than tracking an advancement.

The nine centre cells remain air; enter their plane to travel. Build with safe access to the ring. Activation uses end-rod particles and an End-portal sound.

### Faraway Lands

Build and light a standard 4×5 Nether portal with crying obsidian in all four corners:

```text
AOOA
O..O
O..O
O..O
AOOA
```

Throw a vanilla Echo Shard into the active portal. One shard is consumed; the portal switches routing, plays an explosion sound, gains reverse-portal particles and turns the remaining obsidian frame blocks into crying obsidian. There is no destructive explosion. STEMCraft suppresses vanilla frame-validation physics inside the active entrance; deactivation restores surviving original obsidian blocks without rebuilding broken blocks. There is no separate recipe. Required obsidian, crying-obsidian corners and Nether portal interior must all be present.

Breaking a required frame or interior block clears the corruption. Rebuilding or relighting then produces a normal Nether portal until another shard is used. Sneak-right-click a frame with shears to deactivate without removing the vanilla portal blocks. Disabling the generator also prevents corrupted routing.

## Destinations, return travel and configuration

Every default destination is `{world}_<generator>`, using the source world's full name. For example, a Faraway portal in `survival_nether` would resolve `survival_nether_faraway` if that source name and `NETHER` environment were allowed. Portals first load existing worlds; with `destination.create: true`, a missing destination is created through the world service with the source world's seed. Existing destinations must have matching generator metadata. Merely sharing the expected name is insufficient.

Each source entrance now has a persistent, one-to-one counterpart. Activation starts preparing chunks near the source portal origin divided by `destination.coordinate-scale` (8.0 by default), builds a matching active exit, and links the two UUIDs. For example, origin X=800, Z=1600 targets X=100, Z=200. Negative coordinates use mathematical floor. Only X/Z scale: Y is selected by a bounded search for safe space. Ground exits must connect to a natural floor, including cavern floors in Deep. The configured base can be embedded into natural surface blocks or stand directly on the ground. No extra stone surround, slab or support pillars are added. The opening needs a supported floor and a safe nearby landing. A detached exit over empty void is rejected, and solid obstructions or containers above the base prevent placement. Existing admin portal footprints and the world border are respected. The activation/protection event fires before construction at the exit origin.

The exit uses the same configured frame, interior, rotation and effects as its entrance: a second reactor for Wasteland, Deep frame, Skylands ring or corrupted Faraway frame. Skylands exits are fully charged. Both ends work for every player, including after restart. For ground exits, travellers land beside the paired structure, outside its active interior, facing away from the portal with a level camera, with a five-second cooldown and a separate arrival lock covering the entire pad. Players must step off the configured pad before using it again; crossing the raised reactor centre does not reset the lock. Manual teleports onto a pad and joining on a pad also set this lock. The travelling player sees portal particles and hears the portal sound during chunk preparation. There is no generic spawn return pad or per-player return routing, and portal travel never substitutes world spawn when a landing is blocked.

Default destination settings under each portal type:

```yaml
destination:
  world: '{world}_wasteland'
  create: true
  coordinate-scale: 8.0
  search-radius: 32
  link-radius: 16
```

The scale is source blocks per destination block (0.125–128). Search radius is 0–64 destination blocks, sampled in 8-block steps. Link radius is the distance within which a rebuilt entrance may reclaim an intact, unpaired generated exit. It cannot exceed search radius. An exit already belonging to another entrance is never shared or reassigned. Changing scale affects new links; existing pairs keep their saved destinations. Missing settings are added to existing configurations without replacing values already set by an administrator.

Both endpoints count toward `survival.max-portals`. Breaking or deactivating one endpoint removes the link, but leaves the counterpart's physical structure. An intact orphan exit can be reclaimed by rebuilding and activating its entrance nearby. An orphan generated exit cannot create a new portal in Survival or fall back to its spawn. Regenerating a destination world with a new UUID causes its stale link to be replaced on the next trip from the source. Construction may fail when the bounded area lacks clear space, is protected, or crosses the world border; the player stays at the entrance and receives an explanation.

Saved state is still `worlds/portal-state.yml`; links include endpoint IDs, world UUIDs/names and whether the endpoint was generated. Old per-player return-pad entries are discarded. Existing activated source portals can establish pairs on their next use. No terrain generator version change is needed for this portal update.

Each portal definition supports:

* Namespaced generator, source world names and source environments.
* Explicit bounded `frame` cells (`x,y,z:MATERIAL`) and `interior` cells (`x,y,z`), with all four horizontal rotations. `interior-material` is `AIR` or `NETHER_PORTAL`.
* Activation `INTERACT`, `CHARGE_BLOCKS`, `LIGHTNING`, or `THROW_ITEM`; custom/vanilla item ID, charge material and lightning anchor material.
* Destination name/template and whether automatic creation is allowed.
* Sneak-use deactivation item, particle, namespaced activation/deactivation sounds and cooldown.

Breaking, burning, moving or exploding required blocks clears activation. Periodic validation and validation before travel catch other changes. Required chunks must already be loaded; scans do not force-load them. A changed pattern or activation schema invalidates saved activation state. Configuration is refreshed through the existing STEMCraft reload flow. State is saved periodically and by the existing save/disable lifecycle.

The entrances use non-destructive particles. Faraway additionally changes frame materials while active. This does not create custom display entities, generate progression loot, or add cross-dimension resource prerequisites. Those can be layered on this config and API once the worlds and recipes have been play-tested.

## API

```java
WorldGeneration generation = STEMCraftAPI.api().worlds().generator();
// Also available as STEMCraftAPI.api().worldGeneration().

Optional<GeneratorDefinition> latest = generation.getGenerator(new NamespacedKey("stemcraft", "deep"));
Optional<GeneratorDefinition> version1 = generation.getGenerator(new NamespacedKey("stemcraft", "deep"), 1);
Optional<GeneratedWorld> attached = generation.getGeneratedWorld(world);
```

`GeneratedWorld` describes the actual loaded generator, even if an administrator has changed the configuration for the next load. Legacy ownerless factories have no versioned metadata. Collections are immutable snapshots.

Third parties register metadata independently from the existing public `ChunkGeneratorFactory`:

```java
var definition = new GeneratorDefinition(
    new NamespacedKey(myPlugin, "moon"), "Moon", "Low-gravity-style lunar terrain",
    GeneratorCategory.FLOATING, true, 1);

generation.registerGenerator(myPlugin, definition, options -> new MoonChunkGenerator(options));
```

Do this on the server thread while the plugin is enabled. The namespace must match the owning plugin name in lowercase. Factories must produce fresh thread-safe generators; Paper's `WorldInfo` provides the seed and height bounds. `GenerationContext` is a public immutable input record for implementations that want it. Internal noise/terrain classes are not a supported API dependency. Categories are metadata only.

`unregisterGenerator(myPlugin, key)` removes all retained versions of that key and rejects another owner. Registrations disappear automatically on owner disable; loaded worlds keep their attached generator. Third-party plugins should load their own worlds after registering or use appropriate plugin dependencies for startup ordering.

`api.worlds().portals()` provides optional `WorldPortalService` access when CustomPortals is enabled. It exposes type snapshots, instance snapshots, saved counterpart lookup (`getLinkedPortal(UUID)`) and deactivation. Calls require the server thread. `SurvivalPortalActivateEvent` fires before activation-item consumption or charge recording and may be cancelled by protection/progression integrations. Its player is null for weather-triggered lightning and automatic matching-exit preparation. Custom type registration is intentionally configuration-driven in this initial API.

## Validation and manual acceptance

Automated tests cover deterministic noise and seed derivation, negative region boundaries, overlapping crater evaluation, retained versions, plugin ownership, concurrent chunk order, height consistency, ore availability, spawn clearance, portal rotations, all bundled definitions, item consumption, partial charges, reset behaviour and saved portal state. These do not replace in-game terrain and performance inspection.

On a test server, inspect chunk joins on positive and negative coordinates, fly through each terrain family, explore at least a few thousand blocks, and record generation times. Test all four portals in both rotations, break/rebuild and restart them, check disabled generators, try mismatched destination metadata, and verify a second player has their own return destination. Verify interaction protections and lightning behavior with the server's actual plugin set. Keep production worlds on a pinned, play-tested generation version.

### Deep lighting and portal travel reliability

The Deep includes glow lichen on cave floors, berry-bearing ceiling vines and scattered glowstone in exposed rock. Regenerate the test worlds to use the consolidated generators and current lighting.

Active air-interior survival portals now contain invisible, passable LIGHT blocks. `effects.light-level` in `worlds/portals.yml` controls brightness (0–15, default 14; 0 disables it). This also applies to matching exits. Deactivation removes the light without rebuilding broken blocks. Faraway retains its native Nether portal blocks and their existing light.

Destination searches hold temporary chunk tickets until exit placement finishes. A failed attempt requires stepping out of the opening before trying again; moving around inside it no longer repeatedly starts travel or repeats a failure message. Preparation times out after 60 seconds, releasing held tickets.

### Survival travel feedback

Except for instant Skylands travel, travel has a three-second warm-up by default (`effects.warmup-ticks: 60`). `effects.screen-distortion: true` adds client-only nausea distortion during preparation, without changing actual potion effects. This is a screen wobble, not the purple Nether portal overlay, and respects the client's distortion settings. Leaving the pad cancels preparation. Completion, failure, quitting, reload and disable explicitly clear the visual effect, preserving any real nausea effect.

Failures use survival-themed messages and a fading sound, such as “The portal sputters and falls quiet. Step away and try again.” Technical generation identifiers and scaled coordinates are not shown to players. Missing return chunks do not deactivate a linked portal. Reactors accept weathered/waxed copper blocks and lightning rods, and extinguish lightning fire in the active opening.

Previously generated stone surrounds are not automatically removed. Remove an old surround manually or regenerate the destination test world to see the current placement.

### Skylands arrival and early destination preparation

Activating an entrance schedules preparation of its matching exit immediately, without waiting for a player to travel. World creation/loading and chunk preparation begin on activation; chunk generation completes asynchronously. Construction respects protection checks, and concurrent travel reuses the same linked endpoint. Failed preparation can be retried on travel. Deactivation or reload cancels pending construction, and temporary chunk tickets are released.

Skylands defaults to `destination.placement: AIR_DROP`, `effects.warmup-ticks: 0` and `effects.screen-distortion: false`. There is no artificial travel delay, although unloaded destination chunks must still finish loading. Automatically built exits float with five clear block layers beneath the lowest frame. Arrival is just below the frame with a three-block fall onto a checked solid surface or water. Lava, dangerous landing blocks and void are rejected; clearance and the landing surface are checked again on every arrival. The player-built entrance stays where it was built, and return travel lands safely beside it. Other generators retain ground placement.

Existing Skylands settings without a placement option receive these defaults on reload. Existing physical exits are not moved; remove/rebuild the pair to test airborne exits.
