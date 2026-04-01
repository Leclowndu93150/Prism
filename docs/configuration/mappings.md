# Mappings

## Default mappings

- **Fabric**: Official Mojang mappings (via Loom's `officialMojangMappings()`)
- **NeoForge / Forge**: Official mappings (via ModDevGradle)

## Parchment

[Parchment](https://parchmentmc.org/) adds parameter names and Javadoc to Mojang mappings.

```kotlin
version("1.21.1") {
    parchmentMinecraftVersion = "1.21.1"
    parchmentMappingsVersion = "2024.07.28"

    fabric {
        loaderVersion = "0.16.2"
    }
    neoforge {
        loaderVersion = "21.1.26"
    }
}
```

Both `parchmentMinecraftVersion` and `parchmentMappingsVersion` must be set. If only one is provided, Parchment is skipped with a warning.

Parchment is applied to both the common subproject and all loader subprojects for that version.

## NeoForm version

The common subproject compiles against vanilla Minecraft using NeoForm. Prism automatically resolves the latest NeoForm version for your Minecraft version by querying the NeoForge Maven repository.

The resolved version is cached in `~/.gradle/caches/prism/` for 24 hours.

To override:

```kotlin
version("1.20.1") {
    neoFormVersion = "1.20.1-20230727.131533"
    // ...
}
```

If you are building offline and have not previously resolved the version, you must set `neoFormVersion` manually.
