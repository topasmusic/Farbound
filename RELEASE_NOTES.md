# Farbound 1.0.0 — A new beginning. The same world.

First public release for **Minecraft 26.3 / Fabric**, by TopasMusic.

- Solo or voluntary group expeditions, up to eight players, within your existing world.
- Destinations 10,000, 50,000, 100,000 or 1,000,000 blocks away, subject to server limits.
- Store your home inventory or take everything; independently store ender chest and XP.
- Optional travel-home challenge with a default 100-block horizontal home radius.
- Pause and return home, then resume with separate expedition gear and exact XP.
  Pause requires stored inventory and stored XP. Only one expedition per world.
- Online-player invitations, readiness review, personal return, group dimension votes
  and operator tools, all through **`/farbound`**.
- English dashboard, Minecraft player heads, contextual help, opacity presets and
  a central departure countdown with vanilla sounds. Extra corner HUD off by default.

## Installation

Use Java25, Fabric Loader0.19.5 or newer and Fabric API0.161.0+26.3 or newer.
Install `farbound-1.0.0-mc26.3.jar` on the server and every expedition participant's
client (or in the client for singleplayer). Do not install the sources JAR.

Store expedition loot in ordinary chests before a final restoration of stored
inventory/ender chest slots. Home XP replaces expedition XP when finalizing.
Back up the complete world, player data and Farbound state together before updates.

## Validation and limits

98 automated tests cover service/profile-switch recovery, inventory codecs, groups,
permissions and UI input/layout. No Minecraft client/server/world or GameTest was
started by the development agent. Full live multiplayer, actual crash/power-loss,
modpack compatibility and visual/audio/performance acceptance are not established
by these tests. Hardcore is unsupported; extra inventory mods require an adapter
for stored profiles. Unknown bases/claims cannot be reliably excluded from targets.

Licensed All Rights Reserved. Source, issues and releases:
https://github.com/topasmusic/Farbound
