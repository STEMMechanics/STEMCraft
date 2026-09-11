# Configurable agriculture and cooking

The generic agriculture engine loads `agriculture.crops` and `agriculture.foraging` sections from every enabled data pack. Crop state and player-placed forage sources are stored in SQLite, so growth stages and natural-only drop protection survive restarts.

```yaml
agriculture:
  crops:
    rice:
      seed: "stemcraft:rice_shoots"
      soil: MUD
      water-above: true
      light-min: 9
      stages: [SEAGRASS, SEAGRASS, SEAGRASS, SEAGRASS]
      seconds-per-stage: 180
      mature-drop: "stemcraft:rice"
      mature-min: 2
      mature-max: 4
      seed-drop: "stemcraft:rice_shoots"
      seed-min: 1
      seed-max: 2
      fortune: true
```

Crop stages are Bukkit material names. Growth is based on elapsed wall-clock time but visual advancement only touches loaded chunks. The seed, mature drop and seed drop may reference any registered custom item.

Forage definitions support block and biome filters, probability, amount ranges, required tools, Fortune scaling and persistent natural-only protection:

```yaml
agriculture:
  foraging:
    coconut:
      blocks: [JUNGLE_LEAVES]
      biomes: [BEACH, JUNGLE, SPARSE_JUNGLE]
      chance: 0.06
      item: "stemcraft:coconut"
      natural-only: true
      fortune: true
```

Custom items used as recipe ingredients are matched exactly. Shaped, shapeless, furnace, smoker, blast-furnace and campfire recipes accept the same namespaced custom-item identifiers as recipe results.

Food definitions support direct healing, damage, returned containers and probabilistic status effects:

```yaml
food:
  nutrition: 10
  saturation: 12.0
  heal: 2.0
  damage: 0.0
  returns: BOWL
  effects:
    - type: HASTE
      duration-seconds: 75
      amplifier: 0
      probability: 1.0
```

Paper potion mixes can be declared under `recipes.brewing`. Inputs may be a vanilla base potion such as `AWKWARD` or an existing custom effect such as `effect:HASTE`.

The bundled implementation is under `data-packs/stemcraft-cooking`; it contains all item definitions, recipes, agriculture definitions and the source textures consumed by both resource-pack generators.
