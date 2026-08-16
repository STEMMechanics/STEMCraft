---
description: Hear ye, hear ye, the following is upcoming news about STEMCraft!
---

# News

## Current issues

_None issues reported_

{% hint style="info" %}
To report an issue, open a ticket in our [Discord](https://discord.gg/yNzk4x7mpD) server, create an Issue over on [GitHub](https://www.github.com/STEMMechanics/STEMCraft-Core) or send an email to [hello@stemmechanics.com.au](mailto:hello@stemmechanics.com.au)
{% endhint %}

## 13-Jan-2024: Release 1.4

### Changes

#### Players

* 🌎 Portals can no longer be created in creative game mode
* :paintbrush: Workshop groups now synchronise on joining the server (fixes some rare teleporting into a workshop but not joining the workshop cases)
* :bed: Survival players can now skip the night if at least 25% of them are sleeping
* :confused: When entering an invalid option in some commands, the correct error will be shown to the player
* :headstone: Player graves have returned and will be placed as a sign and chest if a space can be found near the players death

#### Moderators

* `/tpall` now outputs the players name correctly and notifies all affected players
* Added the `/tphere` command
* Added `/muteall` and `/unmuteall` commands
* Fixed the `/mute` command
* `/jail` and `/unjail` commands are now available which jails a player and disables their chat and commands

#### Developers

* Fixed issue with `SMPersistant` not saving data correctly. This broke the `/mute` command
* Added `SMCommon.getClosestBlockFace` and `SMCommon.regexMatch` methods
* `isSafeLocation` will now exclude certain blocks if above blocks such as beds, signs and fences

## 10-Jan-2024: Release 1.3.2

### Changes

#### Players

* ❤️ Player health is now saved/restored when teleporting or changing game modes

#### Developers

* 🤖 NPCs are ignored with announcing player deaths in console

## 9-Jan-2024: Release 1.3.1

### Changes

#### Players

* 🍿Hunger is now restored correctly when teleporting between worlds

#### Developers

* `getPlayersNearLocation` skips players not in the same world

## 6-Jan-2024: Release 1.3.0

### Changes

#### Players

* 🌎 A new survival world is generated
* 📱Bedrock support added
* 🪦 Graves temporarily disabled
* 🚑 Players are temporarily invincible after teleporting
* 🪙 Coins changed to vanilla items
* 🪣 Added the new `/workshop exit` command and you will now teleport back to your original location

#### Moderators

* Added `/speed` command
* Added workbench commands `/anvil`, `/cartographytable`, `/grindstone`, `/loom`, `/smithingtable`, `/stonecutter`
* Added the `/enchant` command that add or removes enchantments from player items
* Added the `/tptop` command that teleports a player upwards to the next safe location.
* Added `/stemcraft give` to shortcut `custom_model_data` items

### Developers

* Dropped support for ItemsAdder
* Added support for Geyser
* Added `STEMCraft:hasPlayerRecentlyJoined`
* Command context now supports `getArgFloat` and support for Locale failure
* `checkInArrayLocale` is now ignore case
* Added `cancelRunOnceDelay` to cancel run once tasks
* `safePlayerTeleport` will now return a boolean to determine failure
* Added item attributes framework that you can easily add items such as `destroy-on-drop` or `no-drop` on items
* Added `givePlayerItem` which will take item namespaces



## 8-Nov-2023: Release 1.1.0

### Changes

#### Players

* ✂️ New Stonecutter recipes:
  * Cobblestone now cuts into Gravel
  * Gravel now cuts into Sand
* 🪦 Graves will now spawn in the nearest safe location on land. If there is no suitable location, your items will just be dropped
* 🧭 New player location commands`/coord` and `/coordbar`
* 🛍️ Trader villagers will randomally spawn in survival worlds and keep a running tally of player trades
* 🌓 A players previous night vision potion will be restored when disabling night vision from the `/nightvision` command

#### Moderators

* Added `/toolreset` command to clear toolstats data on item
* Added workbench commands `/anvil`, `/cartographytable`, `/grindstone`, `/loom`, `/smithingtable`, `/stonecutter`
* Added the `/enchant` command that add or removes enchantments from player items
* Added the `/tptop` command that teleports a player upwards to the next safe location.

### Developers

* Added ability to customize stonecutter recipes in the configuration
* Tons of helper methods for coding features
* Tab completion shows options that contain the text instead of starts with
* Added `display-version` config option to override MOTD version
* Added values feature for item pricing
* Added a simple persistent saving feature for other features to utilize

## 2-Nov-2023: Release 1.0.2

This release was a bug fix release.

### Bug Fixes

* Survival inventories are now shared across dimensions. Thanks Loki for reporting this issue.

## 26-Oct-2023: Release 1.0.1

This release was a bug fix release.

### Bug Fixes

* Shift clicking while crafting will now apply stats to all the items added to the inventory
* Player heads now only drop when killed by another player in Survival game-mode
* Waystones can now be activated/deactivaed by pistons
* When teleporting to a workshop, players will now teleport to a safe location instead of the centre of the area (even if a block was in the way!)
* The Workshop code has been greatly improved to take into account player permissions and custom game-modes

## 01-Oct-2023: Release 1.0

STEMCraft is Born!

The Drustcraft server was merged under STEMMechanics and became STEMCraft. The server was upgraded to 1.20 with a new survival map.



## Drustcraft History

**2022 Jul** - Server upgraded to 1.19 and the world of Froels introduced

**2022 Mar** - Mithril Catacombs beneath the Vareal temple is found

**2022 Feb** - The Vareal temple is completed and Duinn Sky islands appear above Evalyn

**2022 Jan** - Vareal temple build is started in Evalyn

**2021 Dec** - The world of Evalyn is created with the world of Azentina removed. Server is upgraded to 1.18 and discontinued support for Bedrock clients.

**2021 Aug** - Server is updated to 1.17

**2021 May** - Battlegrounds are introduced and the town of Fort Ryker is created

**2021 Mar** - The town of Issactown is created

**2021 Feb** - The towns of Ironport, Lucas Outpost and Scarlettville are created

**2021 Jan** - Drustcraft is started

