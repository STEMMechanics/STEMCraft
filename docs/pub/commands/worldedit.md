---
description: When is an axe not an axe? when it is a wand!
cover: ../.gitbook/assets/36c65-16656274340357-1920.jpg.avif
coverY: 0
---

# WorldEdit

Here at STEMCraft we use a plugin called WorldEdit (well actually FastAsyncWorldEdit). This allows players to make small, large and complex changes to builds easily.

Below are some common commands and not a complete list. To view the complete list of commands and options for the WorldEdit plugin, visit [https://worldedit.enginehub.org](https://worldedit.enginehub.org)

{% hint style="info" %}
WorldEdit is available to all players when in the **creative** game mode. This is typically when in a creative world plot, or a workshop. Not all commands are available and some limits may apply
{% endhint %}

{% hint style="danger" %}
While we place limits on using WorldEdit (ie how many blocks you can change at once), using WorldEdit commands in a way to slow down or crash the server will lead to a ban
{% endhint %}

### Patterns

Most commands will require a pattern argument. This tells the command what type of block you want to target or use. This can be as simple as `cobblestone` or `acacia_planks`. You can also target or set multiple block types at once by seperating them with a comma and no space, `mossy_cobblestone,dirt`. If you want to randomally place several types of blocks using a percentage, you can enter `50%dirt,25%path,25%coarse_dirt`.

There are even more options you can use to select blocks, which you can read over at [https://worldedit.enginehub.org/en/latest/usage/general/patterns/](https://worldedit.enginehub.org/en/latest/usage/general/patterns/)

### Basic Commands

If you are new to using WorldEdit it is best to use these commands until you are more comfortable.&#x20;

{% hint style="info" %}
Note that in the **creative** world plot, while you can select an area larger than your plot, WorldEdit will not apply changes outside of your own plot. This makes it the perfect place to practice your WorldEdit commands!
{% endhint %}

#### //wand

Gives the player the WorldEdit wand. This wand is a wooden axe and allows the player to select the region that other WorldEdit commands will affect. Left clicking a block to set position 1 and right click to set position 2.&#x20;

#### //set \<pattern>

Sets the selected region to a pattern.&#x20;

**Example**: //fill oak\_planks

{% hint style="info" %}
There is a limit when using this command. If you have selected an area larger than the allowed limit, then only the blocks up to that limit will change.
{% endhint %}

#### //undo

Allows the player to undo the previous command. If a mistake is made with the last command this can be used to fix it. This command can be used up to 10 times in a row.

#### //redo

Allows the player to redoes what was undid. If //undo is used by accident //redo will fix it.

#### //copy

Copies the selected region to the players in-game clipboard.

#### //paste

Pasted the copied region based on the position of the player.

#### //rotate \[y-axis] \[x-axis] \[z-axis]

Rotates the copied area by a certain about of degrees.&#x20;

**Example:** //rotate 90<br>

### Advanced Commands

These commands are available to everyone, and allow fine tuning selections and creations.

#### //sel \<type>

Changes the selection type of the wand. Cuboid is the default type.

**Types**: cubiod, extend, poly, ellipsoid, sphere, cyl, convex

**Example:** //sel sphere

#### //desel

Deselects the current selected region.&#x20;

#### //pos1

Sets the block the player is at to position 1.&#x20;

#### //pos2

Sets the block the player is at to position 2.

#### //hpos1

Sets the block the player is looking at to position 1.

#### //hpos2

Sets the block the player is looking at to position 2.&#x20;

#### //chunk

Selects the chunk as a region selection. A chunk is a 16\*16\*256 block region.&#x20;

#### //expand \<amount> \[direction]

Expands the region selected by an amount based on the players direction or direction entered.

#### //contract \<amount> \[direction] <

Reduces the region selected by an amount based on the players direction or direction entered.

#### //count \<block>

Counts the number of bocks in the selected region.

#### //replace \<from-block> \<to-pattern>

Replaces a certain type of blocks in the selected region with another.

**Example:** //replace oak\_planks stone&#x20;

#### //walls \<pattern>

Creates walls along the outer edges of the selected region.

#### //center \<pattern>

Sets the center of the selected region to a block.&#x20;

#### //regen

Regenerates the region to its earliest form.&#x20;

#### //move \[distance] \[direction]

Moves the blocks in the region by a distance based on the players direction or direction entered.

#### //stack \[count]

Duplicate the blocks in a region a certain number of times based on the players direction.

#### //line \<pattern> \<size>

Creates a line between position 1 and 2 based on the block and size.&#x20;

#### //curve \<pattern> \<size>

Creates a curve between multiple points. Requires [sel](worldedit.md#sel) to be set to convex.

#### //schem

Load: Loads a schematic. **Example:** //schem load ironport

List: Lists all available schematics. **Example:** //schem list

#### //cyl \<pattern> \<radius> \[height]

Creates a cylinder around the player's feet.

#### //hcyl \<pattern> \<radius> \[height]

Creates a hollow cylinder around the player's feet.

#### //sphere \<pattern> \<radius>

Creates a sphere around the player's feet.

#### //hsphere \<pattern> \<radius>

Creates a hollow sphere around the player's feet.

#### //pyramid \<pattern> \<size>

Creates a pyramid.

#### //hpyramid \<pattern> \<size>

Creates a hollow pyramid.

#### /jumpto

Teleports to the block the player is looking at.

#### /thru

Teleports through blocks.

#### /up \[distance]

Teleports up a distance

### Brushes

Brushes allow the player to bind a command to a tool. This allows the command to repeated quickly.&#x20;

#### //brush sphere \<pattern> \[radius]

&#x20;Binds the tool to create a sphere based on the pattern and radius

#### //brush cylinder \<pattern> \[radius] \[height]

Binds the tool to create a sphere based on the pattern, radius and height.

#### //mask \<mask>

Sets tool mask to only affect a particular block.
