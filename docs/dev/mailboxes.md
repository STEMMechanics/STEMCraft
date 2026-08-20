# Mailboxes

The mailbox feature provides delayed player-to-player and server-to-player deliveries. Every delivery contains a written book identifying the sender, the optional message, and the supplied items.

## Player workflow

Players place a mailbox using the configured custom recipe. Closing a mailbox with items in it opens a compose dialog with recipient, message, Send, and Cancel controls. Java uses Paper dialogs; Bedrock uses a Geyser form.

When delivery completes, the recipient receives the configured notification. Notifications for offline recipients are stored and delivered when they next join. A player-specific hologram appears above every mailbox while that player has delivered mail waiting.

## Administration command

```text
/mailbox send <player> [message]
```

This queues a letter-only delivery. Player command senders are identified by UUID; console mail uses `STEMCraft` as its sender label. The other `/mailbox` subcommands inspect and administer the delivery queue.

Permission: `stemcraft.mailbox`.

## Configuration

Runtime settings are under `mailboxes` in the main `config.yml`:

```yaml
mailboxes:
  enabled: true
  hologram:
    text: ":mail_large:"
  delivery:
    base-delay: 72000
    process-mail-queue: 20
    mailbox-full-cooldown: 72000
```

`hologram.text` accepts plain text, MiniMessage formatting, and registered glyph tokens. Glyph assets are defined separately in the `stemcraft-mail` data pack.

Dialog labels and messages are configurable under `mailboxes.dialog` and `mailboxes.messages`.

## Sending mail through the API

Recipients are identified only by UUID. Their current known name is resolved internally.

Mail may be sent as a player UUID or as a named server/plugin system.

Check `result.queued()` before treating the send as successful. `result.message()` describes failures, including unknown UUIDs or payloads that cannot fit in a mailbox.

`MailboxService#hasMail(UUID)` reports whether the player has delivered mail waiting.

<!-- javadoc:all api/src/main/java/dev/stemcraft/api/service/mailbox/MailboxService.java -->
<!-- /javadoc -->

## Dynamic holograms

Mailbox indicators use the dynamic hologram API with the stable key `mailbox:<placed-object-id>`. The hologram service owns Java and Bedrock entities, per-player visibility, range checks, world changes, chunk loading, entity anchors, and resource-pack token refreshes. Breaking a mailbox deletes its dynamic hologram by the same key; mailbox records do not store hologram entity IDs.
