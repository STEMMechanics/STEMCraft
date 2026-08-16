# Slime and Magma Buckets

STEMCraft adds two craftable custom items:

- **Slime in a Bucket**, crafted shapelessly from a bucket and a slime ball.
- **Magma Cube in a Bucket**, crafted shapelessly from a bucket and magma cream.

Both have dedicated Java and Bedrock appearances supplied by the STEMCraft resource pack.

## Finding slime chunks

Slime in a Bucket doubles as an in-world slime chunk detector. Hold it in either your main hand or off-hand
while exploring. Its slime becomes excited and animates whenever you enter a slime chunk, then returns to
normal when you leave. No coordinates, commands, or debug screen are required.

The server determines the chunk state. Teleporting, changing worlds, logging in, changing held slots, swapping
hands, or moving the item through an inventory updates its appearance automatically. A dropped bucket returns
to its normal appearance.

Bedrock players also receive a short action-bar message and slime sound when the bucket becomes excited or
calms down.

Magma Cube in a Bucket is currently a collectible custom item and does not detect chunks.

Administrators can obtain the items for testing with `/give @s stemcraft:slime_bucket` and
`/give @s stemcraft:magma_cube_bucket`.
