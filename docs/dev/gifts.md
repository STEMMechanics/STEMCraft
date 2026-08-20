# Gifts

The Gift service creates a single, unstackable custom item containing any number of item stacks. Right-clicking a block with a Gift consumes it and drops its contents at the adjacent ground location.

The `stemcraft:gift` custom item is defined in `data-packs/stemcraft-gifts/configs/custom-items.yml`, keeping its material, display name, placement rule, and future client model configuration outside the service code.

## API

Specifications use `<item>[,<quantity-or-range>]`. The quantity defaults to `1`. The item may be a vanilla material, namespaced custom item, or parameterized custom item.

Use `api.gifts().isGift(item)` and `api.gifts().contents(item)` to inspect gifts. Returned contents are cloned.

<!-- javadoc:all api/src/main/java/dev/stemcraft/api/service/gift/GiftService.java -->
<!-- /javadoc -->

## Admin command

The `stemcraft.command.gift` permission grants access to:

- `/gift create` — open an empty 54-slot editor and receive the resulting Gift on close.
- `/gift edit` — remove the Gift held in the main hand, edit its contents, and receive the updated Gift on close.
- `/gift inspect` — view the held Gift's contents in a read-only inventory.

An empty create produces no item. Closing an edited Gift with no contents safely returns the original Gift.

## Minigame winner rewards

BedWars, Bridge, Boat Race, Minefield, and TNT Run can deliver a random Gift to each winner through their mailbox. Add this to the minigame's configuration file:

Minigame reward mail defaults to a 300-tick (15-second) delay. Configure `mail.delay-ticks` as `-1` to use the normal mailbox delay, `0` for the next queue pass, or any positive tick delay.

```yaml
rewards:
  # true: independently select a gift and quantities for every winning team member.
  # false: every member of the winning team receives the same randomized gift.
  team-randomized: true
  mail:
    from: "STEMCraft Minigames"
    message: "Congrats {player} on winning {minigame} {arena}! Here is your prize."
    delay-ticks: 300
  gifts:
    0:
      - "emerald,1-3"
    1:
      - "cooked_beef,1-5"
      - "egg,1-3"
      - "stemcraft:animal_barrel[animal=chicken]"
```

One numbered entry is chosen at random. All lines inside that entry are placed inside the resulting Gift. Omitting `rewards` or leaving `gifts` empty disables rewards.

Reward mail messages support `{player}`, `{minigame}`, and `{arena}`. The arena placeholder uses its configured display name.
