# Farbound

![Farbound FB icon](design/assets/farbound-icon-64.png)

**A new beginning. The same world.** Standalone Fabric mod for Minecraft 26.3,
Java 25, Loader 0.19.5 and Fabric API 0.161.0+26.3. Version **1.0.0**.

## Install and open

Use `26.3/build/libs/farbound-1.0.0-mc26.3.jar` with Fabric API on the
server and all expedition clients. Remove the older Farbound JAR from
the mods folder first; install only one Farbound version. Do not install the sources
JAR. Clients without Farbound receive an installation message and cannot operate
the expedition. Install the mod before continuing an existing participant profile.

Open **`/farbound`**. This is the only command; all player and operator actions
are in the dashboard. The English dashboard follows the approved concept:
Journey, Gear & Rules, Group and Start Delay are visible together. The maximum
window width is420 GUI units, 70% of the previous600-unit maximum; it also stays within64% of screen width and84% of screen height. The whole panel
shrinks uniformly on smaller screens. The real game world remains behind the panel.
Hover controls or the small information circles for short explanations. Dropdowns
show their choices; buttons do not secretly cycle through unrelated options.
Each route/reference option and each dropdown row explains its own function.
The bottom summary follows the server-confirmed mode, distance range and your
personal inventory choice. The review dialog shows XP, ender chest, dimensions,
reference and delay as well. Rejected changes do not appear as accepted settings.

Error dialogs close with **Got it**, Enter, Space or Escape. Escape first closes
the dialog or dropdown; another Escape closes the dashboard. Confirmation dialogs
support Back/Escape, Tab to select a control, and Enter/Space to activate it.

Choose distance/reference, **Require travelling home** On/Off, your personal gear
options and an optional group. Friends lists online players with Minecraft heads;
use **+** to invite. Joined and pending invitations are marked, with pages for
longer online lists. Invitations are
voluntary. Each member reviews their own gear and marks ready. The leader selects
**Review & start**, reviews the consequences, then confirms. In solo mode this
also confirms personal readiness. If anyone else is not ready, the server refuses
the departure and shows an English explanation. Changing settings clears readiness.

A safe destination is searched for between100% and110% of the selected distance,
with at most48 attempts and90 server seconds. No travel corridor is generated.
Known protected areas are avoided; unknown bases and claims cannot be reliably detected.
After a destination is found, the dashboard closes and the central Farbound countdown
shows the configured delay (default **10 → 0**). A small pulse and quiet Vanilla note
block ticks use existing Minecraft HUD/sound functions. The final0 and local Vanilla
teleport sound appear only after a successful start. Cancellation/disconnect clears
the overlay; server lag cannot make it count down ahead of the server. No added
renderer, libraries, sound files or animation atlas. Ordinary controls use the
Vanilla button click. Sounds respect Minecraft's UI sound category and master volume.
The additional top-left timer HUD is off by default, including when preferences
cannot be read. Enable it with Timer in the expedition menu if wanted. Explicitly
saved preferences are retained. The central departure countdown works independently.

During an active expedition, Journey and personal gear show the confirmed values
as read-only information. Dimension rules remain available to the leader. Finish,
Pause & return and Emergency return share one area below the party list; paused
expeditions show Resume and Abandon there. Confirmations and server safeguards
still apply. Party badges show Exploring or At home instead of stale readiness.
Timer and Opacity: Low/Medium/High use aligned controls with a separate elapsed
time line below. The bottom status explains the next step or the homeward distance.

## Gear and return

Default: store your normal inventory, armor/offhand, ender chest and exact XP;
start empty at level0. Normal ender chests hold your expedition supplies during
the trip. Before finishing, move items out of every inventory that will be restored
into ordinary chests. Stored originals replace empty slots; no item merge or loot
export. Original home XP replaces expedition XP exactly instead of adding to it.
Builds and items left in the world remain. A fresh start normalizes health/hunger/
effects; finishing does not heal you. Advancements, statistics and recipes remain.

Inventory choices: **Store at home** (`STORE`) or **Take everything** (`KEEP`,
inventory, armor and offhand). Ender chest and XP independently support store/keep.
Taken items/XP are not rolled back. Start options are fixed after departure.
Existing EMPTY profiles remain readable with their original empty-before-start
rules; EMPTY cannot be selected for a new setup.

With home travel Off, finish whenever the safety checks pass. With home travel On,
reach the dimension and horizontal radius of your own original departure point
(default **100 blocks**, not your bed or world spawn). The menu explains the actual
server radius. Emergency return counts as abandoning the homeward attempt. All participants
finish their own trip; no leader can remotely release someone else's stored items.

Active-trip management stays on the same dashboard: finish, emergency return,
dimension rules, rule proposal review and HUD/opacity controls (**Low/Medium/High**).
Elapsed time has its own row below the controls. New group rules need
approval from every unfinished member. Offline members approve after rejoining.
A newly blocked dimension waits for everyone to leave normally; no forced teleport.

## Pause and resume

**Pause & return** is available only with STORE inventory **and** stored XP. It
saves your expedition position, items, exact XP, respawn and health/hunger/effects,
then returns you to your home profile. Expedition inventory may be full when pausing.
**Resume expedition** stores your updated home profile and returns you to the saved
expedition profile and a safe location near its saved position. Your next pause or
final return uses your latest saved home location. The required homeward finish
radius still refers to the original departure point.

An ender chest set to Store switches with the profiles. An ender chest set to Keep
travels with you throughout. KEEP inventory or XP disables pause. Other members
continue independently; pausing never teleports them or completes a homeward trip.
The timer advances while at least one ACTIVE member is online. Paused members alone
do not advance it. One expedition, including a paused one, can be open per world.

**Abandon expedition** while paused permanently discards the saved expedition gear
and XP after confirmation, retaining your current home profile. **Finish expedition**
requires resuming first, satisfying the home rule and clearing all slots to be
restored. Pausing does not reset the world, blocks, drops, containers or other players.
Pause/resume require the same safe, living Overworld/Survival conditions as return.
Saved profile switches resume after reconnect/restart; conflicting or unsafe
interrupted transitions lock until the operator resolves the cause.

Death never releases stored originals. Respawn uses a valid expedition bed or a
safe expedition anchor, never the home bed/world spawn as a silent fallback.
If the expedition anchor is unsafe, respawn is conservatively blocked even if a bed
still exists; an operator must secure the anchor before the player rejoins.

Start/finish require a living Survival player, in the Overworld, awake, out of combat
for ten server seconds and without a vehicle/leashed animals. Hardcore is excluded.
One open expedition per world, maximum8 members. Logout retains the profile. The
trip timer counts server ticks while at least one active member is online; all
offline/server stopped pauses it. F1 hides both HUD and countdown display.

## Dashboard actions

Use `/farbound` in every state. Preparation offers gear/rules, online invitations,
review/readiness, leave/discard and start. Searching/countdown offers leader cancel.
Active participants see finish, emergency return and eligible pause; paused players
see resume or abandon. Rule proposals have a review/approve button. Operators see
Server tools. All former subcommands, including `/farbound start`, are removed.
Each new start request reports a freshly calculated remaining cooldown in seconds;
Check wait in the dashboard can request the current remainder again.

## Abandoned preparation / development profiles

The local development starter uses the stable test name **FarboundDev**, instead
of a new random PlayerNNN on every run. This affects runClient only, not normal
launchers. Existing world/player files are not rewritten or migrated manually.
If a singleplayer owner opens Farbound and an old offline profile owns a completely
untouched draft, that draft is archived and a new preparation can open. This never
replaces an active/partially started trip, a live owner's draft or a foreign draft
on a dedicated server. The archive keeps the old preparation settings/history.

Use **Discard draft / Leave group** before departure; the leader can cancel a
search/countdown first. During an active trip use Finish or Emergency return.
On a server, an operator can use **Server tools → Archive untouched draft** to archive only an untouched
DRAFT: all member states HOME, no home/nonce transition, no online active player vault.
It refuses active/interrupted profiles; use recovery for those.

## Operator policy and recovery

`<world>/farbound/config.json` is created on first load and read on server startup.
`enabled` controls new expeditions/joins/starts without preventing an existing return.
`allowedPlayers: []` permits everyone; otherwise supply player UUIDs. Limits:
`maxDistance`10000–1000000, `maxMembers`1–8, `cooldownSeconds`0–86400,
`homeRadius`1–256 (default100). On first upgrade, a schema-less default32 becomes100;
other configured radii are preserved. Schema2 radii are never migrated again.
Overworld protection example:
`"protectedAreas": [{"x":100,"z":200,"radius":512}]`.
Known spawn/home/start surroundings are also avoided with a1024-block margin.

`extraInventoryMods` lists known extra inventory mods. These block STORE/EMPTY;
KEEP remains possible without inventing a profile adapter. Add other extra inventory
mods to this list. Unknown inventory, teleport, grave, economy and claim mods need
separate compatibility tests. Vanilla transfer entry points are hooked; other mods
that move players directly are not a secured challenge. Village Quest compatibility
has not been tested. This is a cooperative challenge, not a tamperproof competition.

Server tools require `COMMANDS_ADMIN` (normally OP3), checked again on the server
for every action. Open the dashboard as an operator:

- **Inspect profiles**: inspect UUIDs/states without releasing stored data.
- **Recover online participant**: select a player to repeat safe reconciliation.
  A blocked offline profile normally resumes on login after inspection/restart.
- **Nether / End**: visibly force a rule proposal; a new block still waits for a
  normal return. Offline members are never forcibly teleported.

On storage failure, preserve the files, inspect the log and disk/access/mod conflict,
then restart. Normal interrupted PREPARED/RESTORING transitions resume on login.
Conflicting expedition/nonce markers stay locked; no admin command bypasses them.
**Always back up world, playerdata and farbound together.** Never delete snapshots
or restore one player file in isolation. Finish all trips before removing/updating
Farbound. Use a separate test world/full world copy for first acceptance.

## Assets

The transparent platform icon is **exactly64×64 pixels**:
[farbound-icon-64.png](design/assets/farbound-icon-64.png).
The same PNG is embedded as the mod icon. The English title and FB icon were made
with the built-in Imagegen tool; prompts/originals/exports are documented in
[ASSET_NOTES.md](design/assets/ASSET_NOTES.md). Panels/toggles are ordinary Minecraft
drawing primitives, so they remain interactive and scale cleanly.

## Development and validation

Sources for Minecraft26.3 are in `26.3/`, using Java25 and Fabric Loom.
In the Topas Mods workspace, use the shared `tools/Invoke-TopasProject.ps1` starter
with project `Farbound/26.3`, task `build`, and `-PreleaseBuild=true` for releases.
Dependencies and build intermediates remain PC-local. The central test client is
separate from sources. Outside this workspace this is a standard Fabric Gradle project.

Version1.0.0 passed98 automated tests. These do not establish complete live
multiplayer, real crash/power-loss recovery, modpack compatibility, or visual/audio/
performance acceptance. Test in a backed-up world before using valuable saves.
See [release notes](RELEASE_NOTES.md) for requirements and current limitations.

## Links and license

- [Releases](https://github.com/topasmusic/Farbound/releases)
- [Report an issue](https://github.com/topasmusic/Farbound/issues)
- [Changelog](CHANGELOG.md)
- [All Rights Reserved](LICENSE)