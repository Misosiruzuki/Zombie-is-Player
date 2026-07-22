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

Smart Zombie 0.2.0-dev and the optimized Forge development profile are verified on both the dedicated server and rendered client.
