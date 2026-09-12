# Selection previews

STEMCraft's WorldEdit preview is separate from arbitrary feature highlights. `/selpreview` toggles the former; `/selpreview on|off` sets it explicitly. `/selpreview grid [on|off]` controls the advanced grid independently. Preferences persist per player. Minigame-region and location highlights remain available when WorldEdit previews are hidden.

The advanced grid defaults off and is suppressed when any selection side exceeds the configured maximum (64 blocks by default). Render work must be bounded by sampling/budgets and player distance, rather than proportional to region volume. Never use `(0,0,0)` as an unset primary position.

`WorldEditRegionSupport` handles selectors that return null or throw `IncompleteRegionException`. Supported complete shapes are copied into snapshots; cylinder and ellipsoid previews retain their shape. Unsupported shapes return no preview rather than throwing in the recurring render task. Stored-region consumers can impose stricter supported-shape requirements than display code.

When investigating lag, check exception spam before increasing particle budgets. Test unset cuboid/convex selectors, complete cylinders, negative coordinates and a large selection. Hiding the server preview does not change the selection or disable a client's own CUI.
