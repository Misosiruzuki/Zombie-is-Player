# Zombie is Player

Zombie is Player is a Minecraft Forge 1.20.1 mod currently under development.

Development will begin after the shared Forge workspace and VoxPilot test workflow are in place.

## Development environment

- Minecraft 1.20.1
- Minecraft Forge 47.x
- Java 17
- Automated playtesting with [VoxPilot](https://github.com/Misosiruzuki/VoxPilot)
- Reproducible lightweight development profile documented in [PERFORMANCE.md](PERFORMANCE.md)

## Setup

Use Java 17, then run:

```powershell
.\gradlew.bat build
.\gradlew.bat installClientPerformanceMods installServerPerformanceMods
.\gradlew.bat genIntellijRuns
```

The client and dedicated server have separate run directories so client-only rendering mods never enter the server environment.

## Status

Forge project initialized. The optimization profile is installed and verified before gameplay implementation begins.
