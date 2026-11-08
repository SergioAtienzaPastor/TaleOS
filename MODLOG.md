# TaleOS development journal

## Player Link — 2026-11-08

- Project: `~/hytale/mod/taleos`; Hytale dedicated server 0.6.2, Java plugin route.
- Toolkit: `universal-modder 0.2.0`; knowledge-base search for Hytale inventory transfers returned no prior note.
- Existing integration points: `TurtleUpgradeRegistry`, `TurtleController.peripheral`, `ComputerManager.tickWorld`, `OpenCustomUIInteraction` and `ItemStack.withMetadata`.
- Player inventory API verified with `javap`: `Player.getInventory().getHotbar()/getBackpack()` and atomic `ItemContainer.moveItemStackFromSlotToSlot`.
- Requirement clarified by the user: Player Link does not restrict access to a Turtle. Holding the item and pressing F binds that item to the player; an equipped link lets any Turtle send items to that player's inventory.
- Design: owner, routes and automatic-transfer settings live in the item's BSON metadata so they survive movement, removal and Turtle drops. Only manual/automatic sending is allowed; pulling is not exposed.
- Safety: online owner, same world and configurable distance required; exact source/destination slots; bounded transfers and event frequency; Lua cannot change or clear the owner.
- Next: implement the item and F interaction, peripheral and transfer core, tests, own model/icon, docs and real-server verification.

### Implementation and test results

- Added `TaleOS_Player_Link`, F interaction through `TaleOSPlayerLink`, a status/unlink page, external/internal Turtle support and a generated model/texture/icon.
- Added metadata-backed owner/routes/default slot/automatic mode and the nine-method `player_link` peripheral. No Lua owner mutation or player-item pull exists.
- Added `player_link` config: `enabled`, `max_distance` (64) and `transfers_per_tick` (8). Existing configs without this section use defaults.
- `compileJava` passes against the real Hytale server jar.
- 20 focused tests plus a real Lua peripheral integration test pass. Tests cover metadata round trips, unlink, invalid settings, numeric-string rejection, immutable ownership, empty/full/incompatible destinations, partial stacks and real Lua calls.
- Important Hytale behavior found by tests: `moveItemStackFromSlotToSlot` can swap incompatible stacks. `PlayerLinkTransfer` now checks stack compatibility and destination acceptance before moving so Player Link cannot remove or replace player items.
- Pending: full Gradle suite, publication lint, real-server asset/startup check and manual client verification of F binding, unlink permissions, actual player slot mapping, automatic routes and distance/offline handling.

### Build and live-server verification

- `./gradlew clean build`: 291 tests, 0 failures/errors/skips across 65 suites.
- JAR `build/libs/taleos-0.2.0.jar`, SHA-256 `52a68d7594f6c257ca96266065e0be88e80a38b58be64b51bbe8d18493369c8c`; required Player Link and Chunk Loader entries and manifest verified.
- `um publish check .`: PASS, 1113 files, 0 failures, 0 warnings.
- Backups: `~/.universal-modder/backups/taleos-server-mods/20261108-124120.zip` and `~/.universal-modder/backups/taleos-test-universe/20261108-124217.zip`.
- First manual launch omitted the official assets and failed validation; corrected with `~/hytale/hytale_home/install/release/package/game/latest/Assets.zip`. A later WSL reboot explained the lost process; it was not a mod crash.
- Live Hytale 0.7.4 server booted on port 5520. Turtle #34 accepted `TaleOS_Player_Link` in `internal4`; Lua returned `player_link` and all nine methods. Unbound owner was nil and availability/configuration/transfer correctly required F binding.
- Visual F/unlink and real online-player transfer checks remain manual and are listed in TESTING.md. No claim of visual verification.
