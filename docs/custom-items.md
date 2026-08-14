# Configurable custom items

Simple custom items can be declared under `custom-items` in a data-pack `config.yml` or `configs/*.yml` without writing a feature class. Keeping the definition beside its texture makes the pack portable. Definitions in the plugin's main `config.yml` are also supported for items without pack assets. Each item is registered with the Item API and can be created by other plugins with `api.items().createCustomItem("fried-egg")`.

```yaml
custom-items:
  fried-egg:
    material: DRIED_KELP
    name: "<gold>Fried Egg"
    texture: "item/fried_egg"
    lore:
      - "<gray>Warm and crispy"
    placement: DENY       # DENY or VANILLA
    max-stack-size: 64    # optional
    glint: false          # optional
    food:                 # optional
      nutrition: 3
      saturation: 2.4
      always-edible: false
```

The `material` controls the server-side behaviour. For food, choose an edible base material and override its food values. `placement: DENY` prevents a block-based item from being placed. Names and lore support MiniMessage formatting.

The pack directory name supplies the namespace (`stemcraft-survival` becomes `stemcraft_survival`). The item ID and single `texture` value derive the Java item/model definitions, Bedrock identifier and icon, and a stable auto-assigned custom-model-data value. `name` is reused as Bedrock's plain display name. The texture above resolves to `contents/stemcraft_survival/textures/item/fried_egg.png`, is included in the generated Java pack, and is copied into the generated Bedrock pack.

Unusual items can override only the derived values they need:

```yaml
    overrides:
      java:
        custom-model-data: 46003
        item-model: "another_namespace:special_item"
        model: "another_namespace:item/special_item"
      bedrock:
        identifier: "another_namespace:special_item"
        icon: "special_item"
        display-name: "Special Item"
```

## Recipe results

Every configurable shaped, shapeless, cooking, or smithing-transform recipe can return a configured item using the same public namespaced ID accepted by `/give`. Recipes may be placed beside `custom-items` in the same data-pack configuration:

```yaml
recipes:
  furnace:
    fried_egg:
      input: EGG
      result: "stemcraft:fried_egg"
      amount: 1
      exp: 0.1
      time: 200
```

Material results such as `result: COOKED_COD` continue to work unchanged.

## Giving configured items

STEMCraft registers the bare `/give` command so its command tree includes configured items as well as all vanilla materials. When the item argument resolves to a configured item, selectors and counts work normally:

```text
/give @s stemcraft:fried_egg
/give James stemcraft:fried_egg 16
```

The namespace is accepted as a convenient command form; underscores in the path resolve to hyphens in the configured ID. Vanilla item requests are delegated to `minecraft:give`, retaining Minecraft's item-component syntax and behaviour. Item components after the count remain a vanilla-only facility.

When a registered campfire recipe accepts `EGG`, STEMCraft automatically inserts the egg into an empty campfire slot instead of allowing its normal throwing interaction. No custom-item flag is required; removing the campfire recipe removes the override.
