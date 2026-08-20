# Command Reference

This is the active command surface registered by STEMCraft.

## Core / Admin

- `/maintenance <on|off>`
- `/stemcraft <status|version|save|reload|firstjoin|welcome>`
- `/stemcraft save` flushes pending state owned by STEMCraft and extensions registered through its Save API; Bukkit world and vanilla player saves remain the server's responsibility
- `/stemcraft welcome <show|preview|clear|addline|insertline|setline|removeline|addblank|insertblank> <first|returning|anniversary [year]> ...`
- `/customcommands <list|info|create|delete|setlabel|setpermission|addcommand|setcommand|removecommand>`
- Custom command aliases run as the player by default; prefix a run entry with `server:` or `player:` to force the sender
- Custom command aliases support `{player}` and `{uuid}` placeholders in run entries
- `/webserver <start|stop|enable|disable>`
- `/world <record|create|delete|info|displayname|load|list|duplicate|listgenerators|setspawn|id|joincommands|leavecommands|addjoincommand|addleavecommand|setjoincommand|setleavecommand|removejoincommand|removeleavecommand|flags|time|weather|tickspeed|gamemode|nether|end|randomspawn>`
- `/world displayname <world> <name|clear>` sets the player-facing world name or restores its automatic name; `/world info [world]` shows the effective name and whether it is custom
- `/world create <name> [generator|plugin[:id]] [generatorOptions]` accepts STEMCraft generators and Bukkit plugin generators such as `PlotSquared` or `Plugin:id`
- `/world joincommands [world] [page]` and `/world leavecommands [world] [page]` open the in-chat world transition command editors
- `/world addjoincommand <world> <command>` and `/world addleavecommand <world> <command>` append commands run on world join/leave
- World join/leave commands run as the player by default; prefix an entry with `server:` or `player:` to force the sender per command

## Teleport / Movement

- `/portal list`
- `/portal info <id>`
- `/portal set <id> <world|here> [spawn|x y z [yaw pitch]]`
- `/portal repair <id>` rescans a rebuilt portal while preserving its destination
- `/portal delete <id>`

Custom portals teleport immediately by default. Set `custom-portals.instant-teleport` to `false` to retain the vanilla portal warm-up.
- `/tpall`
- `/tphere <player>`
- `/tpspawn <world> [player]`
- `/spawn <world> [player]`
- `/back [player]`
- `/warp <name>`
- `/setwarp <name>`
- `/delwarp <name>`
- `/tpworld <world> [player]`
- `/tpworldspawn <world> [player]`
- `/tpworldlast <world-base> [player]`
- `/top`
- `/jump`
- `/thru`
- `/hub [player]`

## Player Utility

- `/coord`
- `/coordbar`
- `/speed <fly|walk|reset> <speed> <player>`
- `/book <option>`
- `/book save <author> <title>` stores the book under a title-derived key; existing hyphens in the title are preserved
- `/resourcepack [send] [player]`
- `/resourcepack zip`
- `/mailbox send <player> [message]`
- `/mailbox <queue|view|release|hold|delete|item> ...`
- `/quest [track [active-id|auto|off]|abandon <active-id>]`
- `/quest admin` opens the clickable quest editor; see `quests.md` for all definition commands
- `/quest admin edit <id> rewarditem <add|remove> ...` manages inventory-safe item rewards; overflow is mailed
- `/quest admin npc-spawned [radius] [player]` lists spawned quest NPCs with clickable teleport and linked-quest actions
- `/quest admin player <online-player>` displays the player's available, active, ready, cooldown, completed, locked, and disabled quests
- `/quest admin test <start|advance|complete|reset> <id>` provides administrator test controls; `/quest admin test examples` restores missing bundled examples
- `/noticeboard post`
- `/noticeboard post <header> | <message>` from the server console
- `/noticeboard mine`
- `/noticeboard list [page]`
- `/noticeboard edit <post-id>` opens an edit dialog
- `/noticeboard edit <post-id> <header> | <message>` edits directly (including from console)
- `/noticeboard expiry <post-id> <duration|-1>`; examples include `1d`, `14h`, and `-1` for no expiry
- `/noticeboard remove <post-id>`
- `/noticeboard board create <id> [columns] [rows]`
- `/noticeboard board delete <id>`

All `/noticeboard` commands require `stemcraft.noticeboard.admin`. Normal players create and manage their single active notice by clicking a physical notice board; the admin `post` command may create additional notices.

## Moderation

- `/ban ...`
- `/unban ...`
- `/kick ...`
- `/warn ...`
- `/muteall <on|off>`
- `/profanity <status|check|search|list|add|remove|set|reload>`
- `/moderation <list|show|context|resolve|undo|strikes|clearstrikes>`
- `/audit <list|show|context>`
- `/report <message>`
- `/reports <list|show|resolve>`
- `/naughty <player> <duration|clear> (reason)`

## Holograms / Minigames

- `/hologram <create|update|closest|delete>`
- `/bridge ...`

## Permissions

Most commands follow `stemcraft.command.<command>`, for example:

- `stemcraft.command.world`
- `stemcraft.command.customcommands`
- `stemcraft.command.tpworld`
- `stemcraft.command.resourcepack`
- `stemcraft.command.profanity`
- `stemcraft.admin.firstjoin`
- `stemcraft.quest.admin`

For exact behavior and argument validation, see locale usage keys in:

- `plugin/src/main/resources/locales/en.yml`
