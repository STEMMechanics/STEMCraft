# Notice boards

Notice boards are physical graphical map displays for short player-created survival notices. They support asynchronous requests such as asking for materials, exploration help, or companions for an activity. Trades and arrangements remain manual between players.

Each visible post contains a header, short message, and author. Creation and expiry timestamps are stored internally but are not shown. Posts are removed automatically after 14 days by default.

## Player commands

- `/noticeboard post` opens the cross-platform posting dialog.
- `/noticeboard mine` lists the player's active posts and short IDs.
- `/noticeboard remove <post-id>` removes one of the player's posts.

## Board administration

Players with `stemcraft.noticeboard.admin` can create a board while looking at its bottom-left backing block:

```text
/noticeboard board create <id> [columns] [rows]
```

The default board is four maps wide and three maps high. The board faces the player creating it. Remove it with `/noticeboard board delete <id>`. All physical boards display the same server notice collection.

When there are more active notices than fit on the display, the board rotates to the next group automatically every 30 seconds.

## Configuration

```yaml
notice-boards:
  enabled: true
  retention-days: 14
  title: "SERVER NOTICE / REQUEST BOARD ★★★★★"
  # Optional PNG, relative to plugins/STEMCraft (or an absolute path).
  # The configured title text is used when it is blank, missing, or unreadable.
  title-image: ""
```

Board and notice text uses Minecraft's map font. Notices use several paper colours, stable slight rotations and offsets, a pin, three generously spaced message lines, and a larger author line.

## Image-map API

The independent image-map service can be used by other features and plugins:

```java
api.imageMaps().create("quests:lobby", bottomLeftBlock, BlockFace.NORTH, 4, 3);
api.imageMaps().render("quests:lobby", bufferedImage);
api.imageMaps().delete("quests:lobby");
```

The location is the bottom-left backing block as viewed from the front. The service scales and splits the image into 128×128 map tiles and manages the item frames.
