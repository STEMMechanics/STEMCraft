# Configurable custom items

Simple custom items can be declared under `custom-items` in `config.yml` without writing a feature class. Each item is registered with the Item API and can be created by other plugins with `api.items().createCustomItem("fried-egg")`.

```yaml
custom-items:
  fried-egg:
    material: DRIED_KELP
    name: "<gold>Fried Egg"
    lore:
      - "<gray>Warm and crispy"
    placement: DENY       # DENY or VANILLA
    max-stack-size: 64    # optional
    glint: false          # optional
    food:                 # optional
      nutrition: 3
      saturation: 2.4
      always-edible: false
    clients:              # optional custom Java/Bedrock appearance
      java:
        custom-model-data: 46003
        item-model: "stemcraft_survival:fried_egg"
        model: "stemcraft_survival:item/fried_egg"
        texture: "stemcraft_survival:item/fried_egg"
      bedrock:
        identifier: "stemcraft:fried_egg"
        icon: "fried_egg"
        texture: "stemcraft_survival:item/fried_egg"
        display-name: "Fried Egg"
```

The `material` controls the server-side behaviour. For food, choose an edible base material and override its food values. `placement: DENY` prevents a block-based item from being placed. Names and lore support MiniMessage formatting.

Client definitions use the existing resource-pack custom-item pipeline. The texture path above resolves to `assets/stemcraft_survival/textures/item/fried_egg.png` inside the generated Java pack and is copied into the generated Bedrock pack. Java model and item-definition JSON are generated automatically.

## Recipe results

Every configurable shaped, shapeless, cooking, or smithing-transform recipe can return a configured item by prefixing its ID with `custom:`:

```yaml
recipes:
  furnace:
    fried_egg:
      input: EGG
      result: "custom:fried-egg"
      amount: 1
      exp: 0.1
      time: 200
```

Material results such as `result: COOKED_COD` continue to work unchanged.
