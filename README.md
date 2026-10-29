# Farbound

![Farbound](design/assets/farbound-title-original.png)

**A new beginning. The same world.**

Ever wanted to start a new world, but couldn't leave your old home behind? With Farbound, you can have both. Head far away for a fresh adventure, then return to the base you've already built — all in the same Minecraft world.

- Explore solo or invite up to seven friends.
- Choose a travel distance or enter custom Overworld **X / Z coordinates**. Farbound finds a safe surface within **100 blocks** of your target.
- Start independent expeditions while other players travel. No OP permission or player allowlist is required.
- Choose whether you must find your own way home.
- Take your gear or store it at home. Choose separately for XP and your ender chest.
- **Pause & return** to visit your base, then resume with your expedition gear and XP. Pausing requires stored inventory and XP.
- Manage invitations, settings and return from one menu: **`/farbound`**.

### Get started

For **Minecraft 26.2 or 26.3 · Fabric**. Choose the Farbound JAR matching your Minecraft version. Install [Fabric API](https://modrinth.com/mod/fabric-api) and the mod JAR from [Releases](https://github.com/topasmusic/Farbound/releases). Multiplayer requires **exactly matching Farbound 1.0.2 on the server and every joining client; missing or mismatched versions cannot join**.

Open **`/farbound`**, choose your settings, then select **Review & start**. For a chosen destination, select **Distance → Custom coordinates**, enter X and Z, and apply. Height is automatic; world borders and configured protected areas apply. A failed search changes no player profile. Custom destinations can revisit earlier expedition areas.

### Coming home

Use **Finish expedition** to return. If travelling home is required, first get within **100 blocks of your original departure point**, in the same dimension (server settings may change this radius).

Put expedition loot in ordinary chests before finishing: restoring stored home inventory and XP replaces expedition gear and XP. Pausing keeps both profiles for later. Each player can belong to one open solo or group expedition, including a paused one. Other players can run independent expeditions at the same time. Invitations are voluntary and can be declined.

The global world spawn stays unchanged. During an expedition, a usable bed sets your personal respawn; otherwise you return to that expedition's start. Finishing restores your previous personal respawn. Server operators can still disable new departures globally with `enabled=false`; old `allowedPlayers` entries no longer restrict access. Existing 1.0.0 expeditions migrate with a saved copy of their old expedition file. After migration, keep using 1.0.1 or newer; reverting requires a consistent pre-update world/player backup.

[Download](https://github.com/topasmusic/Farbound/releases) · [Report an issue](https://github.com/topasmusic/Farbound/issues) · [MIT License](LICENSE)
