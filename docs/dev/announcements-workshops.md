# Announcements and Workshops

STEMCraft provides two independent features:

- `announcements` delivers configured, scheduled, join, manual, and workshop-promotion messages.
- `workshops` maintains the upcoming STEMMechanics workshop catalogue.

The features may be enabled or disabled separately. Announcements asks the
`WorkshopService` for the next eligible workshop and never reads the RSS feed
or the workshops database directly.

## Configuration

The bundled `config.yml` contains both sections. Existing server configuration
is preserved when bundled defaults are restored.

```yaml
announcements:
  enabled: true
  scheduled-enabled: true
  announce-on-startup: false
  default-interval: 15m
  minimum-gap: 30s
  join-enabled: true
  join-delay: 5s
  timezone: Australia/Brisbane
  prefix: "<dark_aqua>[STEMCraft]</dark_aqua> "
  join:
    first-time:
      - "<aqua>Welcome, {player}!</aqua>"
    returning:
      - "<aqua>Welcome back, {player}!</aqua>"
  scheduled:
    server-information:
      type: message
      enabled: true
      interval: 15m
      message:
        - "<yellow>Remember to read the server guide.</yellow>"
    workshop-promotion:
      type: workshops
      enabled: true
      interval: 30m
      filters:
        days-until:
          greater-than: 2
          less-than: 5
        locations: ["STEMCraft", "STEMMechanics Lab"]
      message:
        - "<aqua>Next workshop: {next_workshop_title}</aqua>"
        - "<gray>{next_workshop_date} at {next_workshop_time}</gray>"

workshops:
  enabled: true
  website-enabled: true
  rss-sync-enabled: true
  manual-enabled: true
  cache-persistence: true
  rss-url: "https://www.stemmechanics.com.au/workshops/feed"
  refresh-interval: 60m
  connection-timeout: 10s
  read-timeout: 20s
  timezone: Australia/Brisbane
  maximum-displayed: 3
  display-filter:
    locations: ["STEMCraft"]
```

Scheduled entries are keyed by their configuration ID. Each entry can override
`type`, `interval`, `enabled`, `start`, and `end`. The default `message` type is
a normal announcement. The `workshops` type selects the next eligible workshop
through `WorkshopService` and supports `filters.days-until.greater-than`,
`filters.days-until.less-than`, and `filters.locations`. Day bounds are strict,
and location matching is exact after trimming but case-insensitive. Dates accept
ISO date/time values; date-only values use the configured timezone. Messages can
be a scalar or a YAML list. Each list item is sent as its own chat line.

`workshops.display-filter.locations` applies only to the player-facing
`/workshops` list and `/workshops info` command. An empty list shows every
enabled source. The `WorkshopService` API continues to expose the full upcoming
catalogue so other features can apply their own filters.

Manual announcements are stored in SQLite, so configuration reloads and server
restarts do not replace them. Manual entries use the global interval and do not
have a configured start or end date.

## Announcement commands

All players can use `/announcements help` and `/announcements broadcast` is
available to users with `stemcraft.announcements.broadcast`. The administrative
subcommands require `stemcraft.announcements.admin`:

```text
/announcements help
/announcements list
/announcements add <message>
/announcements edit <id> <message>
/announcements remove <id>
/announcements enable <id>
/announcements disable <id>
/announcements broadcast <id>
/announcements reload
```

`add` and `edit` join the remaining arguments, so formatting tags and spaces can
be entered directly. To include multiple lines, use separate configured list
items or a message containing a newline when invoking the command through a
console integration.

Scheduled announcements rotate one due entry at a time in due-time and ID order.
The minimum-gap setting prevents several overdue entries from filling chat at
once. No scheduled message is sent when the server has no online players.
Join tasks are delayed by `join-delay` and are cancelled when the player quits.

## Workshop commands

`/workshops` is the player-facing command and shows the next three upcoming
entries by default. Entries are sorted by start time, displayed in the configured
timezone, and remain current until their end time when an end time exists. An
entry without an end time expires at its start time.

Administrators with `stemcraft.workshops.admin` can use:

```text
/workshops list
/workshops info <id>
/workshops add <title> | <start> | <end|-> | <location> | <url|-> | <description>
/workshops edit <id> <title> | <start> | <end|-> | <location> | <url|-> | <description>
/workshops remove <id>
```

For example:

```text
/workshops add Redstone Engineering | 2026-11-21T10:00 | 2026-11-21T12:00 | STEMMechanics Lab | https://example.test/book | Build and test a redstone machine
```

Manual workshops are persisted and editable. Website records are read-only in
Minecraft. `/workshops refresh` starts an asynchronous synchronisation and
requires `stemcraft.workshops.refresh` or the administrator permission.

## RSS synchronisation

The default feed is the [STEMMechanics workshop RSS feed](https://www.stemmechanics.com.au/workshops/feed).
The current feed is RSS 2.0 with the `sm` namespace at
`https://stemmechanics.com/ns/rss/workshop`. Each item supplies `title`, `link`,
`guid`, `description`, `pubDate`, and the event-specific fields
`sm:startDate`, `sm:endDate`, `sm:location`, `sm:price`, `sm:ages`, and
`sm:status`.

`pubDate` is the publication timestamp and is never used as an event date.
Entries without a valid `sm:startDate` are excluded from upcoming workshops.
This avoids inventing dates from titles or relying on fragile page markup. The
parser uses a bounded asynchronous HTTP request and secure StAX settings with
DTD and external entity resolution disabled. Only HTTP and HTTPS links are
retained.

Successful website records are cached. A timeout, unavailable site, malformed
feed, or invalid individual item leaves the last successful cache in place and
does not interrupt other STEMCraft features. The synchronisation status is
available through `api.workshops().syncStatus()`.

## Workshop API

Other STEMCraft features should depend on:

```java
WorkshopService workshops = api.workshops();
List<Workshop> upcoming = workshops.upcoming();
Workshop next = workshops.nextUpcoming();
List<Workshop> range = workshops.within(from, to);
Workshop byId = workshops.find(id);
WorkshopSyncStatus status = workshops.syncStatus();
```

The API returns `WorkshopService.UNAVAILABLE` when the feature is disabled or
not present. Callers should therefore treat an unavailable service and an empty
catalogue as normal states. A workshop identifies its source with
`WorkshopSource.WEBSITE` or `WorkshopSource.MANUAL` and stores start/end values
as timezone-aware `ZonedDateTime` values.

## Placeholders and safety

Announcements support MiniMessage and the existing STEMCraft message/placeholder
processing. Available announcement placeholders are:

| Placeholder | Value |
| --- | --- |
| `{player}` | The joining player; empty for a server-wide announcement |
| `{online_players}` | Current online player count |
| `{next_workshop_title}` | Next eligible workshop title |
| `{next_workshop_date}` | Next workshop date in the announcement timezone |
| `{next_workshop_time}` | Next workshop time in the announcement timezone |
| `{next_workshop_url}` | Safe HTTP(S) booking URL, or empty |
| `{next_workshop_countdown}` | Human-readable time until the workshop |

Workshop promotions are skipped when no eligible workshop exists, so their
placeholders are never shown unresolved. Feed titles and other feed-controlled
values are escaped before being inserted into MiniMessage templates. Booking
links are limited to HTTP(S) URLs with a host and are rejected when they contain
markup, quotes, backslashes, or whitespace.

## Troubleshooting

Run `/workshops refresh` and inspect the server log for a bounded refresh error.
The synchronisation status records the last attempt, last successful refresh,
error text, and website record count. If the site remains unavailable, manually
created workshops continue to work and the last successful website cache is
used when cache persistence is enabled. If the feed contains no event-specific
start dates, those entries are deliberately omitted until the feed is corrected
or a different reliable RSS endpoint is configured.

The two features can be tested independently: disable `workshops` to test
ordinary announcements, or disable `announcements` while `/workshops` and feed
synchronisation continue to operate.
