---
sidebar_position: 4
---

# Mappings

## Default mappings

- **Fabric (pre-26.x)**: Official Mojang mappings via Loom's `officialMojangMappings()`
- **Fabric (26.x+)**: No mappings needed. Minecraft is unobfuscated. Prism sets `fabric.loom.disableObfuscation=true` automatically.
- **NeoForge / Forge (1.17+)**: Official mappings via ModDevGradle / ForgeGradle 7
- **Forge 1.16.5 (`forge16`)**: MCP mappings via ForgeGradle 6 (`snapshot` / `20210309-1.16.5` by default)
- **Legacy Forge (1.7.10–1.12.2)**: MCP mappings via RetroFuturaGradle (`stable` / `39` by default)

## Yarn mappings

Fabric projects can use [Yarn](https://github.com/FabricMC/yarn) mappings instead of Mojang mappings:

```kotlin
fabric {
    loaderVersion = "0.18.6"
    yarn("1.21.1+build.3")
}
```

When `yarn()` is set, Prism uses `net.fabricmc:yarn:{version}:v2` instead of `officialMojangMappings()`. Yarn is only available for obfuscated versions (pre-26.x).

## Unobfuscated versions (26.x+)

Starting from Minecraft 26.1 (1.21.11), the game ships with unobfuscated source. This means:

- No intermediary mappings are used
- No remapping is applied to mod JARs
- Loom's `officialMojangMappings()` is not called (would throw an error)
- Fabric dependencies use `implementation` instead of `modImplementation` (no remapping needed)
- Access wideners still work (they widen access levels and mutability, not related to obfuscation)
- Mixin refmaps use static remapping

Prism handles all of this automatically based on the Minecraft version. You do not need to configure anything differently for 26.x.

## Parchment

[Parchment](https://parchmentmc.org/) adds parameter names and Javadoc to Mojang mappings. Only applicable to obfuscated versions (pre-26.x).

```kotlin
version("1.21.1") {
    parchmentMinecraftVersion = "1.21.1"
    parchmentMappingsVersion = "2024.07.28"

    fabric {
        loaderVersion = "0.18.6"
    }
    neoforge {
        loaderVersion = "21.1.222"
    }
}
```

Both `parchmentMinecraftVersion` and `parchmentMappingsVersion` must be set. If only one is provided, Parchment is skipped with a warning.

## LexForge mappings

LexForge (ForgeGradle 7, Forge 1.21.1+) supports official Mojang mappings, Parchment, and custom channels. See [Loaders — LexForge mappings](loaders.md#mappings-1) for configuration details. The `prismDoctor` output shows the resolved channel as `fg7 official`, `fg7 parchment`, or `fg7 <channel>`.

## Forge 1.16.5 mappings (`forge16`)

`forge16` (ForgeGradle 6) uses MCP mappings. 1.16.5 has no `stable` channel, so the default is `snapshot` / `20210309-1.16.5`. Override with an explicit channel/version:

```kotlin
version("1.16.5") {
    forge16 {
        loaderVersion = "36.2.42"
        mappings("snapshot", "20210309-1.16.5")
    }
}
```

Parchment is not wired for `forge16`. The `prismDoctor` output shows the resolved channel as `fg6 <channel>`.

## Common project compilation

The common subproject compiles against vanilla Minecraft. Prism resolves the correct artifact automatically:

| Minecraft version | Artifact | Source |
|-------------------|----------|--------|
| 1.20.2+ | NeoForm (`net.neoforged:neoform`) | NeoForge Maven |
| 1.20.1 and older | MCP (`de.oceanlabs.mcp:mcp_config`) | NeoForge Maven |

For older versions where NeoForm does not exist, Prism falls back to MCP and uses MDG Legacy in vanilla mode.

The resolved NeoForm version is cached in `~/.gradle/caches/prism/neoform-versions.txt` for 24 hours. Delete this file to force a refresh. If you're offline, set the version manually:

```kotlin
version("1.20.1") {
    neoFormVersion = "1.20.1-20230612.114412"
    // ...
}
```
