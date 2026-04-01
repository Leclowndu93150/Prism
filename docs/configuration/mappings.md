---
sidebar_position: 4
---

# Mappings

## Default mappings

- **Fabric (pre-26.x)**: Official Mojang mappings via Loom's `officialMojangMappings()`
- **Fabric (26.x+)**: No mappings needed. Minecraft is unobfuscated. Prism sets `fabric.loom.disableObfuscation=true` automatically.
- **NeoForge / Forge**: Official mappings via ModDevGradle

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

## Common project compilation

The common subproject compiles against vanilla Minecraft. Prism resolves the correct artifact automatically:

| Minecraft version | Artifact | Source |
|-------------------|----------|--------|
| 1.20.2+ | NeoForm (`net.neoforged:neoform`) | NeoForge Maven |
| 1.20.1 and older | MCP (`de.oceanlabs.mcp:mcp_config`) | NeoForge Maven |

For older versions where NeoForm does not exist, Prism falls back to MCP and uses MDG Legacy in vanilla mode.

The resolved version is cached in `~/.gradle/caches/prism/` for 24 hours.

To override:

```kotlin
version("1.20.1") {
    neoFormVersion = "1.20.1-20230612.114412"
    // ...
}
```
