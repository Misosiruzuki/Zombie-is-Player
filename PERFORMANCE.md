# Performance development profile

This project pins a deliberately small set of optimization mods for Forge 1.20.1 development.
They are development runtime dependencies only and are **not** bundled into the Zombie is Player jar.

| Mod | Pinned version | Side | Purpose |
|---|---:|---|---|
| [ModernFix](https://modrinth.com/mod/modernfix) | 5.27.66+mc1.20.1 | Client + server | Launch time, memory, bug fixes and general optimizations |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | 6.0.1 | Client + server | Block-state/model memory reduction |
| [Embeddium](https://modrinth.com/mod/embeddium) | 0.3.31+mc1.20.1 | Client | Terrain renderer and broad rendering optimization |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | 1.2.4+1.20.1 | Client | GUI, text, entity and immediate-mode rendering batching |
| [EntityCulling](https://modrinth.com/mod/entityculling) | 1.7.4 | Client | Skips rendering entities and block entities hidden behind geometry |

The exact Modrinth version IDs are committed in `build.gradle`. ForgeGradle deobfuscates the jars before copying them into separate run directories:

- `run/client/mods`: all five mods
- `run/server/mods`: ModernFix and FerriteCore only

Install/update the pinned profile with:

```powershell
.\gradlew.bat installClientPerformanceMods installServerPerformanceMods
```

`prepareRunClient` and `prepareRunServer` invoke the corresponding setup automatically.

## Deliberately excluded

- OptiFine: invasive transformations and poor Forge mod-development compatibility.
- Dynamic FPS: throttles an unfocused client, which conflicts with VoxPilot background tests.
- Starlight/LazyDFU: not the appropriate optimization path for Minecraft 1.20.1.
- Recipe, mob-AI or gameplay-changing optimizers: they can hide regressions in the mod under test.
- Profilers such as spark: valuable when diagnosing a problem, but not a zero-overhead default dependency.

For correctness comparisons, temporarily move the generated jars out of `run/client/mods` and rerun the same VoxPilot scenario against vanilla Forge rendering.
