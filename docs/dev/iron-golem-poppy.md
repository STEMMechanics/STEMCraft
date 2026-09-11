# Iron Golem Poppy Luring

`IronGolemPoppyFeature` is an independent feature configured by the top-level `iron-golem-poppy` section.
It has no dependency on Rotten Flesh items or their data pack and can be enabled, disabled, and reloaded on
its own.

The feature uses Paper's normal `Pathfinder`. Candidates are limited to players holding a Poppy in their main
hand and golems inside the configured range. Existing targets prevent luring, and an
`EntityTargetLivingEntityEvent` immediately releases a lured golem when combat AI acquires a target.

The closest player wins initially. An existing leader is retained until another player is at least the configured
`switch-advantage` blocks closer, preventing rapid target oscillation. Pathfinding is recalculated according to
`update-ticks`, which defaults to 10 ticks.

Manual testing should cover natural and player-built golems, combat priority, hostile player targets, multiple
players and golems, removing the Poppy, moving out of range, teleport, world change, disconnect, and chunk
unload.

