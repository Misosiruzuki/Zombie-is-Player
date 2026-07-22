# Lightweight resource-pack profile

The automated client profile uses [F8thful](https://modrinth.com/resourcepack/f8thful) 6.0 for Minecraft 1.20.1.
It reduces vanilla-style textures from 16x16 to 8x8, so each ordinary texture has one quarter of the pixels while blocks, items, entities and UI remain recognizable during playtesting.

Only one full texture replacement is enabled. Stacking another low-resolution pack would mostly override the first pack, add loading work, and make the active test assets harder to identify.

## Install and select

```powershell
.\gradlew.bat enableLightweightResourcePack
```

The task downloads the pinned Modrinth file to `run/client/resourcepacks`, verifies its SHA-512 checksum, and updates only the resource-pack entries in `run/client/options.txt`.
Normal client runs make sure the file remains installed but preserve the selected profile.

## Vanilla visual-correctness pass

Low-resolution packs can conceal texture seams, animation errors, UV mistakes and fine zombie/player skin details. Before accepting a visual change, switch to the vanilla baseline and repeat the same VoxPilot scenario:

```powershell
.\gradlew.bat useVanillaResourcePacks
```

Re-enable the lightweight automation profile afterward with `enableLightweightResourcePack`.

## Excluded alternatives

- 1x1 and 4x4 packs: lighter in theory, but destroy the visual distinctions needed for Zombie is Player testing.
- NoGPU: an extreme option, but it replaces assets and models too aggressively for a visual development baseline.
- Multiple full replacement packs: overlapping files do not compound the performance benefit.
- Animated, connected-texture, 3D-model, PBR and shader packs: add GPU, CPU, memory or compatibility cost.
