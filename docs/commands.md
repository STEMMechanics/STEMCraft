# Command Reference

This is the active command surface registered by STEMCraft.

## Core / Admin

- `/maintenance <on|off>`
- `/stemcraft <status|version|reload|firstjoin>`
- `/customcommands <list|info|create|delete|setlabel|setpermission|addrun|setrun|removerun>`
- `/webserver <start|stop|enable|disable>`
- `/world <record|create|delete|info|load|list|duplicate|listgenerators|setspawn|id|flags|time|weather|tickspeed|gamemode|nether|end|randomspawn>`
- `/world create <name> [generator|plugin[:id]] [generatorOptions]` accepts STEMCraft generators and Bukkit plugin generators such as `PlotSquared` or `Plugin:id`

## Teleport / Movement

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
- `/resourcepack [send] [player]`
- `/resourcepack zip`

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

For exact behavior and argument validation, see locale usage keys in:

- `plugin/src/main/resources/locales/en.yml`
