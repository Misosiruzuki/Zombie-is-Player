# Zombie is Player

Zombie is Player is a Minecraft Forge 1.20.1 mod currently under development.

## Implemented monsters

### あたまのいいゾンビ / Smart Zombie

- Uses the normal-width player model with the vanilla zombie texture.
- Replaces 5% of naturally spawned vanilla zombies by default.
- Stores a 41-slot player inventory, 27-slot ender chest, hunger/saturation/exhaustion, experience, selected hotbar slot, score and player abilities in persistent NBT.
- Picks dropped items up into its player inventory.
- Never distance-despawns or disappears in peaceful difficulty.
- Keeps a fully ticking 3x3 chunk area loaded, follows movement and teleportation, survives server restarts, and releases tickets when destroyed.
- Starts in non-hostile action mode. Entering any player's unobstructed 90-degree view cone within 64 blocks immediately enables combat mode.
- Returns to action mode after being outside every player's view for 60 seconds. Action mode blocks targeting, retaliation, attacks and sleep prevention.
- Uses the player's charged-attack cooldown formula. Main-hand item attack-speed modifiers control its attack interval, and its hit checks use the player's entity-reach attribute.
- Combat mode follows the supplied Minecraft 1.20.1 PvP guide: spacing, smooth aim, strafing, sprint resets, shield timing, axe shield-punishes, selective critical hits, armor/weapon hot-swaps, recovery/retreat, totems and bow pressure. See [PVP_AI.md](PVP_AI.md).
- Advanced inventory tactics include real crossbow loading (arrows, fireworks, Multishot and Piercing), Smart Zombie ender-pearl teleportation, and `mobGriefing`-aware water/lava bucket placement with source recovery.
- Action mode now performs non-hostile survival progression: player-rule block breaking at player block reach, best-tool selection, dropped-resource collection, recipe-driven 2x2/3x3 crafting, crafting-table placement/use, and real furnace fueling/smelting. See [ACTION_MODE_AI.md](ACTION_MODE_AI.md).

The generated `config/zombieisplayer-common.toml` contains:

```toml
[smart_zombie]
replacementChance = 0.05
chunkRadius = 1
```

`replacementChance` accepts `0.0` to `1.0`. `chunkRadius = 1` means 3x3 chunks; increasing it has a large multiplicative server cost.

## Development environment

- Minecraft 1.20.1
- Minecraft Forge 47.x
- Java 17
- Automated playtesting with [VoxPilot](https://github.com/Misosiruzuki/VoxPilot)
- Reproducible lightweight development profile documented in [PERFORMANCE.md](PERFORMANCE.md)
- Pinned 8x8 resource-pack profile documented in [RESOURCE_PACKS.md](RESOURCE_PACKS.md)

## Setup

Use Java 17, then run:

```powershell
.\gradlew.bat build
.\gradlew.bat installClientPerformanceMods installServerPerformanceMods
.\gradlew.bat enableLightweightResourcePack
.\gradlew.bat genIntellijRuns
```

The client and dedicated server have separate run directories so client-only rendering mods never enter the server environment.

## Status

Smart Zombie 0.7.0-dev and the optimized Forge development profile are verified on both the dedicated server and rendered client.
