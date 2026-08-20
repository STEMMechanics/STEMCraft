# Comets

`CometService` launches destructive comet world events at a supplied impact location. The feature owns the flight path, chunk tickets, heat effects, crash scar, explosions, fire, magma debris, terminal geode, water handling, and optional loot.

## Launching a comet

Use a random compass direction or supply the direction travelled into the impact and along the crash scar.

The direction's Y component is ignored. The vertical approach is derived from the generated crash path so the incoming flight and ground scar align.

## Loot

`CometLoot` specifies a placeable block material and an inclusive quantity range. Each launch randomizes the amount independently for every entry and embeds the blocks into exposed solid terrain around the terminal geode.

Direction and loot can also be supplied together.

Only placeable, non-air block materials are accepted. Quantity ranges must satisfy `0 <= minimum <= maximum <= 4096`. Loot is never placed on leaves, logs, planks, liquids, or unsupported blocks.

API calls made away from the primary server thread are transferred to it before chunks, entities, or blocks are accessed.

## API reference

<!-- javadoc:all api/src/main/java/dev/stemcraft/api/service/comet/CometService.java -->
<!-- /javadoc -->

<!-- javadoc:all api/src/main/java/dev/stemcraft/api/service/comet/CometLoot.java -->
<!-- /javadoc -->

## Configuration

The `comet` section in `config.yml` controls the event defaults:

- starting height and flight duration
- trail length and warning radius
- crash-scar length, radius, explosions, power, and fire
- heat, lethal heat, and damage radii
- geode size and magma debris count

Loot contents and quantities deliberately belong to each API call rather than global configuration, allowing different event sources to offer different rewards.
