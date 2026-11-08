# TaleOS Changelog

## TaleOS 0.2.0 — Turtle Automation Update

Released: 2026-11-01

### Added

- **Turtle Chunk Loader Upgrade**, craftable with 4 copper bars and 2 Deadwood planks.
  - Keeps the Turtle's current chunk loaded while equipped.
  - Follows the Turtle across chunk boundaries, releasing the previous chunk.
  - Restores the chunk ticket automatically after a server restart.
  - Releases the chunk when the upgrade is removed or the Turtle is broken.
  - Shares chunk references safely between multiple Turtles in the same chunk.
  - Multiple loaders on one Turtle do not expand the loaded area.
- Custom circuit-board **3D model, texture, and rendered inventory icon** for the Chunk Loader Upgrade.
- **Turtle Player Link**, craftable with 1 Modem and 2 copper bars.
  - Hold the item and press F to bind it to the player; F also opens its status/unlink page.
  - Owner, routes, default destination and automatic mode persist in the item's metadata.
  - New `player_link` peripheral with `getOwner`, `isAvailable`, `setRoute`, `clearRoute`, `getRoutes`, `setDefaultSlot`, `setAutoTransfer`, `transfer`, and `getPlayerSlot`.
  - Works in external and internal Turtle upgrade slots without restricting Turtle access.
  - Sends only to empty or compatible player slots: never pulls, removes, or swaps player items.
  - Requires an online owner in the same world within the configured range, with bounded transfers and throttled `player_link_full`/`player_link_unavailable` events.
  - Includes a custom model, texture, inventory icon, and `player_link` server configuration.
- **Four internal module slots** for every Turtle, in addition to the existing left and right upgrade slots.
  - Internal slots accept modems, speakers, crafting upgrades, and chunk loaders.
  - Tools and weapons remain restricted to the external slots.
  - Internal modules are not rendered on the Turtle model.
- New Lua functions: `turtle.equipInternal(index)`, `turtle.unequipInternal(index)`, and `turtle.getEquippedInternal(index)`, using indices 1–4.
- Internal modem and speaker peripheral names: `internal1` through `internal4`.
- Persistent `mods/TaleOS/chunk_loaders.json` state for automatic chunk-loader restoration.

### Changed

- Turtle inventories now contain 22 slots: 16 inventory slots, 2 external upgrades, and 4 internal modules.
- Existing Turtles are expanded automatically while preserving their inventory, IDs, files, and equipped upgrades.
- Modems, speakers, and crafting upgrades now work from internal module slots.
- Updated the English and Spanish translations, recipes, README, and TaleOS Wiki.
- The boot banner now reports `TaleOS v0.2`.

### Verified

- 291 automated tests passing.
- Real-server checks for equipping and removing internal modules, moving between chunks, releasing old chunks, and restoring loaders after a restart.
- Publication checks passing with no errors or warnings.

## TaleOS 0.1.0 — Initial Release

The first public TaleOS release, featuring programmable Lua computers, Turtles, Pocket Computers, multi-block Monitors, PenDrives, USB drives, NAS units, wireless networking, GPS, HTTP downloads, and speakers for Hytale servers.
