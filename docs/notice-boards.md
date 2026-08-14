# Notice boards

Notice boards are physical graphical map displays for short player-created survival notices. They support asynchronous requests such as asking for materials, exploration help, or companions for an activity. Trades and arrangements remain manual between players.

Each visible post contains a header, short message, and author. Creation and expiry timestamps are stored internally but are not shown. Posts are removed automatically after 14 days by default.

## Player interaction

Players click any map tile on a physical board. Java item-frame interactions and Bedrock interactions translated by Geyser use the same image-map callback.

- A player without an active notice receives the create dialog.
- A player with an active notice receives Edit, Delete, and Cancel choices.
- Editing pre-fills the current header and message and resets the 14-day lifetime when saved.
- Clicking limits each player to one active notice.

## Board administration

Players with `stemcraft.noticeboard.admin` can create a board while looking at its bottom-left backing block:

```text
/noticeboard board create <id> [columns] [rows]
```

The default board is four maps wide and three maps high. The board faces the player creating it. Remove it with `/noticeboard board delete <id>`. All physical boards display the same server notice collection.

All `/noticeboard` command paths require `stemcraft.noticeboard.admin`. Administrators can use `/noticeboard post` to create additional notices without the one-active-notice click limit, `/noticeboard mine` to list their posts, and `/noticeboard remove <post-id>` to remove any post.

Each notice column is approximately two maps wide, so a 4×3 board has two notice columns, a 6×3 board has three, and an 8×3 board has four. Notice cards retain a consistent readable width instead of stretching across wider boards.

When there are more active notices than fit on the display, the board rotates to the next group automatically every 30 seconds.

## Configuration

```yaml
notice-boards:
  enabled: true
  retention-days: 14
  server-author: "STEMCraft"
  title: "SERVER NOTICE / REQUEST BOARD ★★★★★"
  # Optional PNG, relative to plugins/STEMCraft (or an absolute path).
  # The configured title text is used when it is blank, missing, or unreadable.
  title-image: ""
  dialog:
    manage-title: "Your notice"
    create-title: "Post a notice"
    edit-title: "Edit your notice"
    lifetime: "Notices remain on the board for {days} days."
    header-label: "Header"
    message-label: "Short message"
    edit-label: "Edit"
    delete-label: "Delete"
    post-label: "Post"
    save-label: "Save"
    cancel-label: "Cancel"
  messages:
    dialog-open-failed: "/error/Could not open the notice dialog."
    required: "/error/A header and message are required."
    already-active: "/error/You already have an active notice. Click the board to edit or delete it."
    posted: "/success/Your notice has been posted for {days} days."
    save-failed: "/error/Could not save your notice."
    updated: "/success/Your notice has been updated for another {days} days."
    unavailable: "/error/That notice is no longer available."
    deleted: "/success/Your notice has been deleted."
```

The `commands` subsection in the bundled configuration also exposes every administrator command response. Message values pass through the message service, so type/context directives such as `/success/` and `/survival//info/` are supported. `{days}`, `{id}`, and `{header}` are replaced where applicable.

The server console can post without a dialog:

```text
/noticeboard post Server maintenance | Survival will restart in ten minutes.
```

The pipe separates the header from the message. Server notices use the configured `server-author`, bypass the one-notice player limit, and expire normally.

Board and notice text uses Minecraft's map font at a consistent 1.5× scale. Notices use several paper colours, stable slight rotations and offsets, a pin, and three fully supported message lines. Available board slots are deterministically shuffled per board and page, leaving natural gaps when a page is not full without moving posts on every refresh.

## Image-map API

The independent image-map service can be used by other features and plugins:

```java
api.imageMaps().create("quests:lobby", bottomLeftBlock, BlockFace.NORTH, 4, 3);
api.imageMaps().render("quests:lobby", bufferedImage);
api.imageMaps().onClick("quests:lobby", click -> {
    Player player = click.player();
    int column = click.tileColumn();
    int row = click.tileRow();
});
api.imageMaps().delete("quests:lobby");
```

The location is the bottom-left backing block as viewed from the front. The service scales and splits the image into 128×128 map tiles, manages the item frames, and reports the clicked display tile.
