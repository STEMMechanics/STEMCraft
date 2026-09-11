# Build a Custom Tool: Learning from the Chisel

This lesson shows how the Chisel is built and how you can use the same pattern to invent another tool. It is written for young programmers, but it uses the real files and code from STEMCraft.

## What you will make

A custom tool has several parts that cooperate:

1. **Item data** tells the server its name, durability and texture.
2. **A recipe** tells Minecraft how players obtain it.
3. **A texture** tells Java and Bedrock what it looks like.
4. **A feature class** listens for a player action and changes the game.
5. **Tests** check the rules without needing a person to try every block.
6. **Documentation** teaches players how to use it.

Think of these as members of a robot team. The texture is the costume, the recipe is the assembly plan, and the Java class is the brain.

## 1. Start safely

Create a branch before editing anything:

```bash
git switch -c feature/my-tool
```

Choose a small first version. Write down these answers before coding:

* What item does the player hold?
* What exact action activates it?
* Which blocks or creatures may it affect?
* What must it never affect?
* Does one use cost durability?
* How will a player know that it worked?

For the Chisel, the action is “right-click a supported block”. It never rotates complicated blocks such as doors, beds, chests, rails or redstone machinery.

## 2. Describe the custom item

Open `plugin/src/main/resources/data-packs/stemcraft-survival/configs/custom-items.yml`. The Chisel begins like this:

```yaml
custom-items:
  chisel:
    material: FLINT
    name: "<gray>Chisel"
    texture: "stemcraft_survival:item/chisel"
    lore:
      - "<dark_gray>Right-click a supported block to rotate it"
    placement: DENY
    max-stack-size: 1
    max-damage: 256
```

Here is what each line means:

* `chisel` is the logical ID. Code uses this ID even if the name or picture changes.
* `material` is the harmless vanilla item Minecraft uses underneath the custom appearance.
* `name` and `lore` are the words a player sees. The colour tags use MiniMessage.
* `texture` points to the image inside the data pack.
* `placement: DENY` stops an item with block-like backing data from being placed accidentally.
* `max-stack-size: 1` makes it behave like a tool.
* `max-damage` creates its durability bar. Set this only on non-stackable tools.

When inventing your tool, pick a unique lowercase ID with hyphens. Keep the description short enough to read on a phone or tablet.

## 3. Add a recipe

The Chisel recipe is in the same YAML file:

```yaml
recipes:
  shaped:
    chisel:
      result: "stemcraft:chisel"
      amount: 1
      shape:
        - " I "
        - " S "
      ingredients:
        I: IRON_INGOT
        S: STICK
```

Every character in `shape` is one crafting-table square. Spaces are empty squares. Every letter must have a matching material under `ingredients`. The result uses the custom item ID, with `stemcraft:` in front and underscores in place of hyphens when necessary.

Good recipes tell a tiny story. Iron is the cutting edge and the stick is the handle. Avoid making a simple building helper cost diamonds or rare treasure unless the tool is extremely powerful.

## 4. Draw one texture for both editions

The Chisel texture lives at:

```text
plugin/src/main/resources/data-packs/stemcraft-survival/
  contents/stemcraft_survival/textures/item/chisel.png
```

Use a 16×16 PNG with transparency. Keep the outline strong and use only a few colours so the picture is readable in a hotbar slot. The resource-pack builder uses the YAML metadata to generate the Java model and the Bedrock/Geyser item mapping from this single source.

If the item appears as flint, first check the texture path and then rebuild and accept the resource pack. A working behavior with the wrong picture usually means the Java code is fine and the resource-pack data needs attention.

## 5. Give the tool a brain

Feature classes live in `plugin/src/main/java/dev/stemcraft/feature`. STEMCraft discovers classes that extend `BaseFeature`, so no giant registration list is needed.

The Chisel registers a `PlayerInteractEvent` listener in `onEnable()`. Its handler follows a guard-clause pattern:

```text
player action
    ↓
Is it a main-hand right-click on a block?
    ↓ yes
Is the held item really stemcraft:chisel?
    ↓ yes
Is this block safe and meaningful to rotate?
    ↓ yes
Rotate → cancel normal use → sound/particles → damage tool
```

Guard clauses are early `if (...) return;` checks. They keep unrelated actions out of the feature and make the successful path easy to read.

Never identify a custom item only by its vanilla material or display name. Players can rename things, and several custom items may share one backing material. Use:

```java
api.items().isCustomItemId("chisel", event.getItem())
```

For Bedrock compatibility, react to normal Bukkit events and let Geyser translate the player's input. Avoid instructions that require a Java-only key. Also check `event.getHand()` so one tap is not processed twice.

## 6. Change block data, not the block type

A block's `Material` says what it is. Its `BlockData` stores details such as direction, axis, stair half and waterlogging.

The Chisel changes only one suitable property:

* `Directional` blocks move north → east → south → west.
* `Orientable` blocks move through the X, Y and Z axes.
* `Rotatable` decorations move one of sixteen small steps.

Because the code changes the existing `BlockData`, a stair keeps unrelated properties such as its top/bottom half and waterlogged state. Minecraft may recalculate a corner shape when its new direction meets neighbouring stairs. Do not replace a stair with a new default stair just to change its direction; that would throw useful state away.

Some blocks need special care. A door has two halves, a bed has two blocks, a chest may be paired, and redstone can activate machines. Leave those out until you have designed and tested the whole rule. “Do nothing” is a good safe answer when a tool does not understand a block.

## 7. Give useful feedback

The Chisel cancels the normal interaction only after it knows a rotation is possible. It then plays a quiet grindstone sound and creates three particles. Durability is spent only after success, and Creative players do not spend durability.

This order matters. If you cancel too early, players may be unable to use an ordinary block even though the tool did nothing. If you damage too early, failed attempts waste the tool.

The event listener runs at high priority and ignores already-cancelled events. This lets protection plugins stop the edit before the Chisel acts. Your tools must respect protected builds too.

## 8. Write tests like experiments

Tests live in `plugin/src/test/java`. `ChiselFeatureTest` asks four focused questions:

* Does a north-facing stair turn east?
* Does a vertical log move to the next axis?
* Does a standing decoration move one rotation step?
* Are unsafe directional blocks and plain terracotta rejected?

Run the focused tests while working:

```bash
./gradlew :plugin:test --tests dev.stemcraft.feature.ChiselFeatureTest
```

Then run the whole project suite:

```bash
./gradlew test
```

A test is not proof that no bug exists. It is a repeatable experiment that protects rules you already understand.

## 9. Test with real players

Before release, try both Java and Bedrock:

1. Craft the tool and confirm the recipe-book result has the right name and image.
2. Rotate every supported block family in all starting directions.
3. Try unsupported blocks and confirm nothing happens or wears down.
4. Try main hand and off hand.
5. Try Survival and Creative mode.
6. Try a protected area where building is denied.
7. Use the tool until it breaks and watch the durability bar.
8. Reconnect and confirm the item still has its identity and damage.

Touch controls can feel different from a mouse, so a Bedrock test is a real requirement, not an optional extra.

## Ideas for your next tool

Once you understand this pattern, try a carefully limited tool such as a painter that cycles safe colours, a survey tool that reports block facts, or a gardener's tool that changes one crop-related state. Begin with one action and a short allow-list. Add powerful behavior only after tests explain its boundaries.

Before opening a pull request, check that your branch contains the item YAML, recipe, texture, feature code, tests, public instructions and developer notes. Another person should be able to understand both what your tool does and what it deliberately refuses to do.
