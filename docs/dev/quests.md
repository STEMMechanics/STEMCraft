# Quests

The quest feature provides private, book-driven quests attached to living NPC entities. Definitions are stored in `plugins/STEMCraft/quests/quests.yml`; active and completed player state is stored in SQLite.

Players start a quest by right-clicking its start NPC when the yellow question-mark font glyph is visible. The written quest book is tagged to that player and updates as objectives advance. The yellow exclamation-mark font glyph identifies the current NPC objective or a ready turn-in NPC. Both markers use the existing `:question_yellow:` and `:exclamation_yellow:` resource-pack tokens, with plain-character fallbacks when the tokens are unavailable.

Quest books may safely be stored, dropped, moved, or picked up without changing quest progress. A non-owner sees temporarily obfuscated pages when opening one; the physical book remains unchanged. Each acceptance has a persistent attempt revision. Abandoned and completed attempts become invalid, and stale books are removed when encountered in an opened inventory, moved, picked up, opened, or presented for turn-in. A completed quest can only be turned in while its owner is carrying the current tagged book.

The final page provides track and abandon actions. Java players see clickable `[Track] [Abandon]` buttons. Bedrock players see `/quest track <quest-id>` followed by `/quest abandon <quest-id>` to enter manually because their client does not preserve interactive book events.

Definitions may provide a concise `short-description` for the inventory tooltip while keeping `description` as the full story printed inside the book. If omitted, the full description is also used in the tooltip.

Supported ordered objectives are:

- `collect`: own a material and optionally consume it at turn-in
- `kill`: kill a number of an entity type
- `location`: enter a radius around a world coordinate
- `npc`: right-click a bound living entity
- `biome`: enter a configured biome
- `altitude_above` / `altitude_below`: cross a configured Y level
- `underwater`: descend below a configured Y level while in water
- `night`: venture out between 13000 and 23000 world ticks
- `sleep`: be sleeping when the world successfully skips the night
- `structure`: enter the generated bounding box of a shipwreck, outpost, ocean ruin, ruined portal, mineshaft, or buried treasure
- `interact`: interact with a configured living entity type, such as a captive iron golem

Collect objectives are rechecked while the player moves or interacts with a quest NPC. All collect requirements are checked again at turn-in so required items cannot be discarded after an objective advances.

Quests may define `time-limit-seconds`, `restart-cooldown-seconds`, and `global-max-completions`. A timed attempt fails when its real-time deadline passes. Completed repeatable quests and failed attempts may be restarted after the configured cooldown. Global limits are checked atomically on the server thread at turn-in; players who accepted a competitive quest but arrive after its final reward was claimed fail at hand-in. NPC profiles may define `lifetime-seconds`; an expired limited appearance is consumed for that Minecraft day.

NPC profiles use `npc-type`. `PLAYER` creates a Citizens NPC when Citizens is installed and falls back to a villager otherwise; native types such as `WOLF` always remain that type. NPCs wander by default with an 8-block horizontal radius, 3-block vertical radius, and 5-second delay, while keeping destinations inside their configured spawn biomes. They pause and face players within six blocks or when clicked, then resume wandering after the player leaves. Override only the required values under `behaviour` (`type: STATIONARY`, `wander-radius`, `wander-vertical-radius`, `wander-delay-seconds`, or `look-at-players`). Player profiles may also specify `skin.url`; Citizens resolves and caches the skin.

When an NPC's availability window or limited lifetime ends, departure is staggered by up to `quest.npc-leaving.random-delay-ticks`. An open quest menu or recent interaction postpones the transition to `LEAVING`. Leaving NPCs no longer stop for interactions; they use a random `dialogue.leaving` line (or a generic fallback) and walk away without normal biome or wander-radius restrictions. They despawn after reaching `player-distance` from every player, or after `timeout-ticks` as a pathfinding/following failsafe.

## Player commands

- `/quest` — clickable list of the player's active quests, including the quest giver; Java players can open a carried current quest book from the list, while the View action is disabled when the book is missing
- `/quest view <id>` — open the carried current book for an active quest on Java Edition
- `/quest abandon <id>` — immediately abandon an active quest and remove carried copies; completion suggests only active quest IDs
- `/quest abandon-all` — show a confirmation button for abandoning every active quest; `/quest abandon-all confirm` performs it
- `/quest track` — show whether tracking is off, automatic, or following a specific quest
- `/quest track auto` — automatically select a carried active quest book when none is tracked and move to another carried active quest when one ends
- `/quest track <id>` — track that active quest while its current book is in the player's inventory and disable automatic selection
- `/quest track off` — turn off both specific and automatic quest tracking

## Chat administration

Administrators with `stemcraft.quest.admin` use `/quest admin`. It displays clickable create, edit, test, delete, and reload actions. Text entry commands shown by the editor cover every persisted quest field.

`/quest admin npc-spawned [radius] [player]` lists currently spawned quest NPCs. With no radius it lists every spawned profile; with a radius it measures from the command caller or the named online player. Each result provides clickable teleport and linked-quest actions. `/quest admin player <online-player>` groups that player's quests into active, ready, available, cooldown, completed, locked, and disabled states.

`/quest admin editor` creates a private web-editor URL valid for 24 hours or until the server restarts. Requests without a currently issued code receive `403 Not permitted`. The structured editor provides searchable quest and NPC lists; story, dialogue and spawn forms; NPC/material/entity dropdowns; repeatable objective and reward rows; prerequisite selection; enable/repeat switches; create, duplicate, delete, reorder, save and discard controls. Its quest list is displayed as a progression forest: starting quests are roots and chained quests are nested beneath their first prerequisite. Quests with multiple prerequisites appear once beneath their primary path and retain clickable join links to every prerequisite; forks, continuations and endings are also annotated. Siblings are ordered by the starting NPC's minimum player level and then title. The original YAML editor remains under **Advanced YAML**. Saves are validated, keep `.bak` copies, and reload immediately. Treat the URL as a password and do not share it.

Conditional quest NPCs may be killed, but drop no items or experience. A killed profile is recorded for the current Minecraft day and cannot respawn until the following day, when its normal relevance, timing, spacing, biome, level, and daily-chance rules apply again.

Each NPC appearance has a persistent anchor for its configured spawn period. A daytime, nighttime, or midnight-crossing window each counts as one period: unloading the NPC because players walked away does not move it, and returning during that period restores it at the same anchor. In a later spawn period, an NPC that players interacted with relocates only within 250 blocks of its previous anchor. An NPC nobody found may spawn around another eligible player so undiscovered characters do not remain stranded indefinitely. Tracked quests point back to the anchored NPC rather than teleporting that character along with the player.

Interacting with a quest giver first completes a ready quest or advances an NPC objective. Otherwise it opens a private, per-player inventory containing the quests currently available from that NPC. Moving offer books into the player inventory and closing the menu accepts them; confirmation is sent only after closure. Returning an active quest book to the NPC menu abandons that quest. Any other items placed into the menu are dropped beside the NPC when it closes. Separate players can use the same NPC concurrently because each interaction has its own inventory and temporary-book session.

Biome objectives require the player to remain continuously in the target biome. Their `amount` is the required number of seconds; omitted or legacy values of `1` use the 10-second default. Leaving the biome resets that objective's timer.

Quest auto-tracking is enabled by default and preferences persist across reconnects. Only active quests whose current books are in the player's inventory can be tracked. Automatic mode prioritizes timed quests with under five minutes remaining, then briefly shows a quest whose objective the player just progressed for five seconds. It then returns to a ready turn-in, an NPC objective, another timed quest, or the most recently started carried quest in that order. Accepting additional quests does not replace a higher-priority selection. Putting a specifically tracked book away hides its bar until the book returns; selecting a specific quest switches from automatic to specific tracking, while `off` clears both modes. Players who explicitly selected automatic, specific, or off keep that choice when the default changes. Its yellow quest glyph and individual quest title are followed by the live objective in a white, zero-progress boss bar. Storyline prefixes before a colon are omitted from the tracked title, and collect or kill objective context beginning with `for` is omitted; for example, `Walls for Everyone: The Farther Reach` and `Craft 12 flower pots for cottage windows` render as `The Farther Reach - Craft 0/12 Flower Pots`. Collect, kill, and biome objectives show progress. NPCs, fixed locations, and the nearest matching loaded kill target within 64 blocks add an arrow relative to the direction the player is facing; no distance is shown. Tracked NPC objectives and turn-ins lease the NPC from the lifecycle: normal expiry and distance despawning are suspended, and a missing NPC is force-spawned in its configured world even when its ordinary time, chance, or death-day restrictions would prevent spawning. The quest bar is independent of other bars, so it stacks with `/coordbar`, Wither health, and other plugin boss bars.

```text
/quest admin create <id> <title...>
/quest admin edit <id> <title|author|description> <text...>
/quest admin edit <id> <enabled|repeatable> <true|false>
/quest admin edit <id> <startnpc|endnpc>
/quest admin edit <id> require <add|remove> <quest-id>
/quest admin edit <id> reward add <command...>
/quest admin edit <id> reward remove <number>
/quest admin edit <id> rewarditem add <material> <amount>
/quest admin edit <id> rewarditem remove <number>
/quest admin edit <id> objective add collect <material> <amount> [consume] [label...]
/quest admin edit <id> objective add kill <entity> <amount> [label...]
/quest admin edit <id> objective add location <radius> [label...]
/quest admin edit <id> objective add npc [label...]
/quest admin edit <id> objective add biome <biome> [label...]
/quest admin edit <id> objective add <above|below|underwater> <y> [label...]
/quest admin edit <id> objective add structure <structure> [label...]
/quest admin edit <id> objective add <sleep|night> [label...]
/quest admin edit <id> objective add interact <entity> [label...]
/quest admin edit <id> objective remove <number>
/quest admin edit <id> spawnnpc <start|end> [radius]
/quest admin delete <id> confirm
/quest admin reload
/quest admin editor
```

`startnpc`, `endnpc`, and NPC objectives bind the living entity the administrator is looking at within eight blocks. `spawnnpc` creates a persistent, invulnerable, stationary villager at the administrator when the radius is zero; a positive radius chooses a random surface position around the administrator. Existing or externally managed NPC entities may be bound instead.

Reward commands run as the console after turn-in and support `{player}` and `{uuid}`.

Item rewards should use `rewarditem`, not a `give` command. Structured items are inserted into the player's inventory and any overflow is sent through the mailbox system. If overflow cannot be queued, the inventory and consumed quest items are restored and the quest remains ready for another turn-in attempt. Command rewards remain available for experience, permissions, economy, and other non-item effects.

The `/quest admin` and `/quest admin edit <id>` screens are clickable chat menus. Text fields and additions place an editable command into chat; toggles, removals, navigation, and NPC actions run immediately. The player-facing reward description is edited separately from the console commands and is displayed on the final page of the quest book.

## Named NPCs and dialogue

Spawned quest villagers receive a random name from `quest.npc-names`. Binding an existing named entity preserves its name; binding an unnamed entity assigns a random name. The editor can change start and end NPC names later and updates a loaded bound entity immediately.

Quest titles, descriptions, objectives, reward descriptions, and dialogue support `{start-npc}` and `{end-npc}`. Dialogue also supports `{npc}` for the character currently speaking. This allows text such as `Take a fresh cod to {end-npc}` to become `Take a fresh cod to Tailor` in that quest's book.

Each quest has random dialogue pools editable from chat:

- `offer` — said when an eligible player accepts the quest
- `idle` — said when the NPC has no quest available for that player
- `incomplete` — said when the player has the quest but has not met its requirements
- `objective` — said when visiting an NPC objective
- `complete` — said after a successful book turn-in

Multiple lines may be added to each pool; one is selected randomly per interaction.

## Bundled examples

The bundled survival campaign is a collection of storylines rather than a numbered tutorial. Early shelter quests branch into homesteading, livestock, rivers, crops, night defence and mining. Later branches cover cartography, distant biomes, enchanting, deep mining and Nether preparation, alongside shorter independent encounters and rare timed offers. Several long homestead and defence branches remain available in plains biomes so exploration is encouraged but never required merely to keep receiving quests.

On a new installation, the survival campaign and its NPC profiles are copied into `quests/quests.yml` and `quests/npcs.yml`. Six disabled example definitions are included as editing references:

- `example-gathering-supplies` — collected and consumed items
- `example-pest-control` — mob kills and a prerequisite
- `example-the-old-trail` — location visit
- `example-message-for-the-scholar` — NPC visit
- `example-expedition` — several ordered objective types
- `example-fish-for-tailor` — named-NPC placeholders, item delivery, and state-specific random dialogue

They are disabled because NPC UUIDs and meaningful world coordinates are server-specific. Bind or spawn their start/end NPCs, replace the scholar placeholder NPC objective, adjust example locations, and then enable them through the chat editor.

After initialization, the generated files are authoritative and administrator edits are not overwritten on later starts.
