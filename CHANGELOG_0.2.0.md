# TaleOS 0.2.0 — Turtle Automation Update

## Added

- **Turtle Chunk Loader Upgrade**, craftable with 4 copper bars and 2 Deadwood planks.
  - Keeps the Turtle's current chunk loaded while equipped.
  - Follows the Turtle across chunk boundaries, releasing the previous chunk.
  - Restores the chunk ticket automatically after a server restart.
  - Releases the chunk when the upgrade is removed or the Turtle is broken.
  - Shares chunk references safely between multiple Turtles in the same chunk.
  - Multiple loaders on one Turtle do not expand the loaded area.
- Custom circuit-board **3D model, texture, and rendered inventory icon** for the Chunk Loader Upgrade.
- **Turtle Player Link**, craftable with 1 Modem and 2 copper bars.
  - Hold and press F to bind the item to a player, with an optional status/unlink page.
  - Configure exact inventory routes, a default player slot, and manual or automatic transfers through the `player_link` Lua peripheral.
  - Owner and settings persist in item metadata when moving, removing, or storing the link.
  - Works in external and internal Turtle slots and does not restrict who can use the Turtle.
  - Sends only to empty or compatible stacks; never pulls, removes, or swaps player items.
  - Handles offline players, other worlds, distance limits, full inventories, and partial stacks safely.
  - Adds `player_link` server configuration and throttled status events.
- **Four internal module slots** for every Turtle, in addition to the existing left and right upgrade slots.
  - Accepts modems, speakers, crafting upgrades, and chunk loaders.
  - Tools and weapons remain restricted to the external slots.
  - Internal modules are not rendered on the Turtle model.
- New Lua functions:
  - `turtle.equipInternal(index)`
  - `turtle.unequipInternal(index)`
  - `turtle.getEquippedInternal(index)`
- Internal modem and speaker peripheral names: `internal1` through `internal4`.
- Persistent `mods/TaleOS/chunk_loaders.json` state for automatic chunk-loader restoration.

## Changed

- Turtle inventories now contain 22 slots: 16 inventory slots, 2 external upgrades, and 4 internal modules.
- Existing Turtles are expanded automatically while preserving their inventory, IDs, files, and equipped upgrades.
- Modems, speakers, and crafting upgrades now work from internal module slots.
- Updated the English and Spanish translations, recipes, README, and TaleOS Wiki.
- The boot banner now reports `TaleOS v0.2`.

## Verified

- 291 automated tests passing.
- Real-server checks for equipping and removing internal modules, moving between chunks, releasing old chunks, and restoring loaders after a restart.
- Publication checks passing with no errors or warnings.
