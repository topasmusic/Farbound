# Changelog

## 1.0.0 · 22 October 2026

First public release, incorporating all seven test builds. See RELEASE_NOTES.md
for features, dependencies, validation and known limits. No additional gameplay
changes after test build7; final version and project contact metadata added.


## 1.0.0-unreleased.7 · Test build7 · 22 October 2026

- Active journey/gear shown as read-only values; active/paused headings and party
  badges reflect the current state. Shorter Travel home label and less repeated help.
- Group Finish/Pause/Emergency return under the party list; Resume/Abandon use the
  same area while paused. Eight party rows remain visible above these actions.
- Align Timer and labelled Opacity controls at the same height, with a dedicated
  elapsed-time line below to prevent overlap. Homeward progress appears in the
  bottom status. Confirmation flows and server/profile behavior are unchanged.
- Default the additional top-left HUD to off, also on preference read errors.
  The central countdown remains active and Timer can still be enabled in the UI.
  Existing explicitly saved client preferences are preserved.

## 1.0.0-unreleased.6 · Test build6 · 22 October 2026

- One expedition setup with Require travelling home On/Off. Distance/reference and
  personal gear stay independent. Actual home radius explained in the dashboard.
- Pause & return / Resume with separate home/expedition gear, exact XP, respawn,
  health/hunger/effects and saved positions. Requires STORE inventory and XP;
  optional stored ender chest switches too. KEEP ender travels throughout.
- Paused members return individually, never complete the homeward trip, and can
  resume or explicitly abandon their saved trip. One open expedition per world.
- Journalled PAUSING/RESUMING/ABANDONING recovery; interrupted switch errors lock
  before the player can change a profile covered by a saved switch snapshot.
- Friends lists online heads and +/Joined/Invited with paging. Inventory offers only
  STORE/KEEP; existing EMPTY profiles preserve their original behavior.
- `/farbound` is the only command. Preparation/active/paused actions and operator
  inspection/recovery/draft archive/forced rules are in the dashboard. Clients
  need Farbound; operator permissions are enforced on the server.
- Low/Medium/High opacity and separate elapsed-time row. Home radius defaults to100;
  old schema-less32 migrates once, custom radii survive. Fresh cooldown remainder
  on each request, also checked before accepting a new invitation.
- Offline build/sources PASS and96 automated tests PASS, including real item-codec
  profile roundtrips, six restart boundaries, failed teleports and screen actions.
  No client/server/world/GameTest started; live acceptance remains NOT RUN.

## 1.0.0-unreleased.5 · Test build5 · 21 October 2026

- Fix blocked Got it/Back/Confirm buttons and dropdown selections: use Minecraft26.3
  input constants and semantic key checks instead of obsolete GLFW codes.
- Escape/Enter/Space and dropdown arrow keys work; repair stale keyboard focus
  after opening a dialog so Tab can reach confirmation. Modal input blocks the
  background and new errors close existing dropdowns.
- A new failed action can show the same error text again after Got it; routine
  server updates keep the acknowledged notice dismissed.
- Give Fresh start/Walk home, My location/World spawn, Solo/Friends and each menu
  choice their own hover explanation. Inventory/XP/ender chest help follows the
  selected state. An open menu does not display background tooltips.
- Verify that the bottom summary follows server-confirmed mode, distance and
  personal inventory; the existing dynamic behavior needed no rewrite.
- Rapid rejected network actions return current server state and cannot keep
  extending the throttle window. Existing server-side permissions and profile
  safeguards remain in force.
-70 automated tests pass:18 new screen tests,7 multi-identity service tests and1
  real registered network-handler test. No game/server started; live multiplayer
  and visual/audio acceptance remain NOT RUN.

## 1.0.0-unreleased.4 · Test build4 · 21 October 2026

- Stable FarboundDev identity for local development launches; prevent random offline
  test UUIDs leaving inaccessible preparations between sessions.
- Singleplayer owner can reopen after an untouched old profile draft is archived;
  dedicated-server foreign preparations and active/profile transitions are preserved.
- `/farbound abort` gives the confirmation syntax. `abort confirm` also leaves a
  safe unstarted preparation. Active trips retain all inventory/return safeguards.
- Admin-only `admin discard confirm` archives untouched drafts, refusing transition
  markers/home snapshots or active online vaults. Archives retained, no player deletion.
-44 tests pass; no Minecraft client/server/world started by Codex.

## 1.0.0-unreleased.3 · Test build 3 · 21 October 2026

- Fix incorrect texture parameter order that stretched a participant's skin across
  the title and Journey card. Title and face/hat rendering now use explicit target
  sizes and source pixel regions.
- Player list shows the participant's Minecraft head with the hat layer, confined
  to its own row. The existing generated title asset is sampled at its full size.
- Move gear information circles away from labels and draw a crisp information mark.
- Keep the dashboard within64% of screen width and84% of screen height even at high
  Minecraft GUI scales, in addition to the420×315-unit maximum.
-38 tests pass, including new texture API-boundary and high-GUI-scale regressions.
  No client/server/world launched by Codex; visual recheck remains manual.

## 1.0.0-unreleased.2 · Test build 2 · 21 October 2026

- English player/operator messages throughout, including command fallback and recovery notices.
- Compact dashboard matching the approved visual reference: Journey, Gear & Rules,
  Group and Start Delay together; visible dropdown choices, toggles and hover help.
- Window width reduced to70% of the previous maximum, with uniform fitting at smaller
  GUI sizes. Typed invitation names/caret survive periodic state updates.
- Review-and-start flow, voluntary group readiness, active-trip management and
  guarded confirmation dialogs use the same translucent navy/cyan theme.
- Lightweight central server-driven countdown (default10 to0), slight pulse and local
  Vanilla countdown/teleport sounds. No added renderer or animation/audio dependency.
- Vanilla clicks on buttons, dropdown choices and confirmations.
- Generated icy FARBOUND title and transparent64×64 FB platform/mod icon.
- Existing profile safety rules and test build1 artifacts retained. Runtime visual/
  audio/multiplayer acceptance remains to be performed manually.

## 1.0.0-unreleased.1 · Teststand 1 · 21.10.2026

- Erste Farbound-Testversion für Minecraft26.3/Fabric/Java25: Solo-/Gruppenexpedition,
  begrenzte sichere Fernzielsuche, Countdown und pausierbarer Expeditionstimer.
- Freiwillige Einladung/Bereitschaft, frischer Start oder Heimweg, persönliche Rückkehr.
- Inventar-/Enderchest-/XP-Verwahrung mit Profilmarkern und kontrollierter Wiederaufnahme;
  normale Expeditions-Enderchest, exakte XP-Rückgabe und leere Rückgabeslots als Pflicht.
- Teilnehmerbezogene Dimensions-/Respawnregeln, zustimmungspflichtige Regelvorschläge,
  Betreiberfreigaben/Limits/Schutzkreise und privilegierte Diagnose/Regelerzwingung.
- Eigenes transparentes blaues Vier-Reiter-Menü/Cyan-Buttons, HUD/Deckkraft und
  vollständige Befehlsbedienung ohne Menüclient.
- Ingame-/Mehrspieler-/visuelle Abnahme offen; siehe manuelle Checkliste.



