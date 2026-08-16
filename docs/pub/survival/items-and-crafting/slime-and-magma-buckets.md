# Slime and Magma Buckets

You can craft two little creatures in buckets. Both work for Java and Bedrock players when the STEMCraft resource pack is enabled.

| Slime in a Bucket                                                                                                         | Excited Slime in a Bucket                                                                                                                          | Magma Cube in a Bucket                                                                                                                |
| ------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| <img src="../../.gitbook/assets/slime-bucket.png" alt="A green slime peeking out of a metal bucket" data-size="original"> | <img src="../../.gitbook/assets/slime-bucket-excited.gif" alt="The slime bucket&#x27;s two excited animation frames looping" data-size="original"> | <img src="../../.gitbook/assets/magma-cube-bucket.png" alt="An orange magma cube peeking out of a metal bucket" data-size="original"> |
| Normal                                                                                                                    | Excited in a slime chunk                                                                                                                           | Collectible                                                                                                                           |

{% hint style="info" %}
If either bucket looks like an ordinary bucket, make sure you accepted the [server resource pack](../../getting-started/resource-pack.md).
{% endhint %}

## Make a Slime in a Bucket

Put one **Bucket** and one **Slime Ball** anywhere in a crafting table. The recipe is shapeless, so your ingredients do not have to be in these exact squares.

| Crafting table |                |       |
| :------------: | :------------: | :---: |
|   **Bucket**   | **Slime Ball** | Empty |
|      Empty     |      Empty     | Empty |
|      Empty     |      Empty     | Empty |

**You receive:** 1 Slime in a Bucket.

> **Image placeholder — crafting recipe screenshot:** Add a Minecraft crafting-table screenshot showing a Bucket in the top-left square, a Slime Ball in the top-middle square, and the Slime in a Bucket in the result slot. Use the STEMCraft resource pack, crop out unrelated inventory slots, and add short labels or arrows for young readers. Capture both Java and Bedrock versions if their crafting screens look meaningfully different.

## Find a slime chunk

A **Slime in a Bucket** is also a slime chunk detector:

1. Hold it in your main hand or off-hand.
2. Walk around and cross into a new chunk.
3. When you enter a slime chunk, the slime becomes excited.
4. When you leave, the slime calms down again.

You do not need coordinates, commands, or the debug screen. The server decides whether the chunk is a slime chunk.

### What will I see?

* **Java:** the slime changes to its excited animated appearance. Your hand may briefly lower and raise the bucket when its appearance changes. The item has not been dropped.
* **Bedrock:** an action-bar message and slime sound tell you when the slime becomes excited or calms down. Some Bedrock versions may not animate the inventory texture.
* **Either edition:** it also updates after teleporting, changing worlds, logging in, changing slots, or swapping hands.

{% hint style="success" %}
When the slime gets excited, remember the area! Slimes can spawn below **Y level 40** in that chunk when the normal spawning conditions are met.
{% endhint %}

> **Image placeholder — detector in action:** Add a wide in-game screenshot split into two labelled halves. On the left, show a player outside a slime chunk holding the normal bucket. On the right, show the same player one chunk over with the excited bucket, plus the Bedrock action-bar message if possible. Add a bright boundary line between the halves and the captions “Not a slime chunk” and “Slime chunk!” Avoid showing debug coordinates so the picture demonstrates normal player use.

## Make a Magma Cube in a Bucket

Put one **Bucket** and one **Magma Cream** anywhere in a crafting table. This recipe is also shapeless.

| Crafting table |                 |       |
| :------------: | :-------------: | :---: |
|   **Bucket**   | **Magma Cream** | Empty |
|      Empty     |      Empty      | Empty |
|      Empty     |      Empty      | Empty |

**You receive:** 1 Magma Cube in a Bucket.

> **Image placeholder — crafting recipe screenshot:** Add a Minecraft crafting-table screenshot showing a Bucket in the top-left square, Magma Cream in the top-middle square, and the Magma Cube in a Bucket in the result slot. Use the STEMCraft resource pack and crop the image tightly enough that the ingredients and result are easy to see on a phone or tablet.

The Magma Cube in a Bucket is currently a collectible. It does not detect slime chunks.

## Helpful tips

* You can place recipe ingredients in any two crafting-table squares.
* The bucket must be held in a hand to detect a slime chunk; keeping it elsewhere in your inventory is not enough.
* Dropping an excited Slime Bucket safely returns it to its normal appearance.
* These items do not place water, lava, slimes, or magma cubes.

Administrators testing the data pack can obtain the items with `/give @s stemcraft:slime_bucket` and `/give @s stemcraft:magma_cube_bucket`.
