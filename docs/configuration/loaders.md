---
sidebar_position: 2
---

# Loaders

## Fabric

Uses [Fabric Loom](https://github.com/FabricMC/fabric-loom) under the hood.

```kotlin
version("1.21.1") {
    fabric {
        loaderVersion = "0.16.2"
        fabricApi("0.102.1")
        datagen()

        dependencies {
            modImplementation("some:fabric-mod:1.0")
        }
    }
}
```

| Property        | Required | Description                          |
|-----------------|----------|--------------------------------------|
| `loaderVersion` | Yes      | Fabric Loader version                |
| `fabricApi()`   | No       | Adds Fabric API as a dependency      |
| `yarn()`        | No       | Use Yarn mappings instead of Mojang  |
| `datagen()`     | No       | Enables Fabric API datagen run       |

### Mappings

By default, Prism uses official Mojang mappings. To use Yarn instead:

```kotlin
fabric {
    loaderVersion = "0.18.6"
    yarn("1.21.1+build.3")
}
```

Yarn mappings are only available for obfuscated versions (pre-26.x). On 26.x, Minecraft is unobfuscated and no mappings are needed.

Access wideners are picked up from `common/src/main/resources/{modId}.accesswidener` or from the path set via `accessWidener()` in the version block.

### Mixins and raw hooks

```kotlin
fabric {
    mixins {
        config("mymod.mixins.json")
        refmap("mymod.refmap.json")
        disableAutoDetect()
    }

    rawLoom { loom -> }
    rawProject { project -> }
}
```

### Datagen

When `datagen()` is called and Fabric API is present, Prism creates a `datagen` run configuration that uses Fabric API's datagen system. Generated resources output to `src/main/generated` and are automatically added to the source set.

Run configurations generated: `client`, `server`, and optionally `datagen`.

## NeoForge

Uses [ModDevGradle](https://github.com/neoforged/ModDevGradle) under the hood.

```kotlin
version("1.21.1") {
    neoforge {
        loaderVersion = "21.1.26"
        loaderVersionRange = "[4,)"

        dependencies {
            implementation("some:neoforge-mod:1.0")
            jarJar("some:library:[1.0,2.0)")
        }
    }
}
```

| Property              | Required | Description                              |
|-----------------------|----------|------------------------------------------|
| `loaderVersion`       | Yes      | NeoForge version                         |
| `loaderVersionRange`  | No       | Version range for template expansion     |

Access transformers are picked up from both `common/src/main/resources/META-INF/accesstransformer.cfg` and the loader's own resources. If no AT file exists but an `.accesswidener` file is found (via `accessWidener()` or auto-detection), Prism automatically converts it to AT format.

### Mixins and raw hooks

```kotlin
neoforge {
    mixins {
        config("mymod.mixins.json")
    }

    rawNeoForge { ext -> }
    rawProject { project -> }
}
```

### Datagen

Prism detects the Minecraft version and configures datagen accordingly:

- **1.21.3 and older**: Single `data` run
- **1.21.4 and newer**: Split `clientData` and `serverData` runs

Generated resources output to `src/generated/resources` and are automatically added to the source set.

## LexForge (1.21.1+)

Uses [ForgeGradle 7](https://github.com/MinecraftForge/ForgeGradle) for modern Forge (1.21.1 and later). "LexForge" is MinecraftForge proper — named to distinguish it from NeoForge, which is a separate fork with its own toolchain.

```kotlin
version("1.21.1") {
    lexForge {
        loaderVersion = "52.0.0"

        dependencies {
            implementation("some:forge-mod:1.0")
        }
    }
}
```

In `settings.gradle.kts`:

```kotlin
prism {
    version("1.21.1") {
        lexForge()
    }
}
```

The loader subproject path is `:1.21.1:lexforge` (directory `versions/1.21.1/lexforge/`).

| Property              | Required | Description                                    |
|-----------------------|----------|------------------------------------------------|
| `loaderVersion`       | Yes      | Forge version (without MC prefix)              |
| `loaderVersionRange`  | No       | Version range for template expansion           |
| `mappings(c, v)`      | No       | Mappings channel and version                   |

The Forge dependency is resolved as `net.minecraftforge:forge:{mcVersion}-{loaderVersion}` via FG7's Minecraft Mavenizer.

### Mappings

Prism picks the mappings channel in this order:

1. Explicit `mappings("channel", "version")` from the DSL
2. Parchment — if `parchmentMinecraftVersion` and `parchmentMappingsVersion` are set at the version level, Prism uses channel `parchment` with version `{parchmentMappingsVersion}-{parchmentMinecraftVersion}`
3. Official — channel `official` with the Minecraft version

```kotlin
version("1.21.1") {
    parchmentMinecraftVersion = "1.21.1"
    parchmentMappingsVersion = "2024.11.17"

    lexForge {
        loaderVersion = "52.0.0"
    }
}
```

### Mixins

FG7 does not ship a mixin Gradle extension. Prism still auto-detects `.mixins.json` configs, adds the Sponge Mixin annotation processor, and writes the `MixinConfigs` attribute into the jar manifest so Forge discovers them at runtime. Refmap generation uses Sponge Mixin's defaults; to customize (refmap filename, source set binding), apply the `org.spongepowered.mixin` plugin yourself via `rawProject { }`.

### Raw hooks

```kotlin
lexForge {
    loaderVersion = "52.0.0"

    rawLexForge { minecraft -> }
    rawProject { project -> }
}
```

Run configurations generated: `client`, `server`, `data`.

## Forge (1.17 - 1.20.1)

Uses [ModDevGradle Legacy](https://github.com/neoforged/ModDevGradle) for Forge 1.17 through 1.20.1.

```kotlin
version("1.20.1") {
    forge {
        loaderVersion = "47.2.0"
        loaderVersionRange = "[47,)"

        dependencies {
            implementation("some:forge-mod:1.0")
        }
    }
}
```

| Property              | Required | Description                              |
|-----------------------|----------|------------------------------------------|
| `loaderVersion`       | Yes      | Forge version (without MC prefix)        |
| `loaderVersionRange`  | No       | Version range for template expansion     |

The version string passed to MDG Legacy is `{mcVersion}-{loaderVersion}`, e.g. `1.20.1-47.2.0`.

Run configurations generated: `client`, `server`, `data`.

### Custom remap configurations

```kotlin
forge {
    loaderVersion = "47.4.16"
    remapConfiguration("optionalMods")

    dependencies {
        modConfiguration("optionalMods", "curse.maven:jei-238222:7391695")
    }
}
```

### Mixins and raw hooks

```kotlin
forge {
    mixins {
        config("mymod.mixins.json")
        refmap("mymod.refmap.json")
    }

    rawLegacyForge { ext -> }
    rawProject { project -> }
}
```

:::warning Refmap required in JSON
Every `*.mixins.json` in the `common` or `forge` subproject that declares non-empty `mixins`/`client`/`server` arrays MUST include a top-level `"refmap": "<modid>.refmap.json"` field. Prism fails the build at configuration time if it's missing — without a refmap, MDG Legacy cannot remap the mixin targets and the mod will crash in production. Fabric is exempt (Loom writes the refmap automatically).
:::

### Forge mod dependencies must use `mod*`

On MDG Legacy, any dependency whose jar contains `META-INF/mods.toml` is a Forge mod and must be remapped. Declare it via the `mod*` variants:

```kotlin
forge {
    dependencies {
        modImplementation("curse.maven:jei-238222:4712866")   // correct
        // implementation("curse.maven:jei-238222:4712866")    // Prism rejects this
    }
}
```

The same rule applies to `common { }` on MDG Legacy versions, since the common project pulls the Forge jar through NeoForm. Prism resolves each dep, inspects the jar, and fails the build if a Forge mod is declared on a non-`mod` configuration.

## Legacy Forge (1.7.10 - 1.12.2)

Uses [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle) for old Forge versions.

```kotlin
version("1.12.2") {
    legacyForge {
        mcVersion = "1.12.2"
        forgeVersion = "14.23.5.2847"
        mappingChannel = "stable"    // default
        mappingVersion = "39"        // default
        username = "Developer"       // default

        accessTransformer("src/main/resources/META-INF/mymod_at.cfg")
        mixin()  // adds MixinTweaker

        dependencies {
            implementation("some:library:1.0")
        }
    }
}
```

| Property          | Required | Default      | Description                         |
|-------------------|----------|--------------|-------------------------------------|
| `mcVersion`       | Yes      | `1.12.2`     | Minecraft version                   |
| `forgeVersion`    | Yes      | -            | Forge version                       |
| `mappingChannel`  | No       | `stable`     | MCP mapping channel                 |
| `mappingVersion`  | No       | `39`         | MCP mapping version                 |
| `username`        | No       | `Developer`  | Dev username for run configs        |

Requires the GTNH maven in `pluginManagement`:
```kotlin
maven { url = uri("https://nexus.gtnewhorizons.com/repository/public/") }
```

Java 8 toolchain with Azul JDK. Run configurations: `runClient`, `runServer`.

Use `rawProject {}` when you need plain RetroFuturaGradle customization that Prism does not model directly.

## Custom run configurations

Every loader gets default runs (client, server, data). You can add more:

```kotlin
fabric {
    loaderVersion = "0.18.6"
    fabricApi("0.116.9+1.21.1")

    runs {
        client("testClient") {
            username = "Dev"
        }
        server("secondServer") {
            jvmArg("-Xmx4G")
        }
    }
}

neoforge {
    loaderVersion = "21.1.222"

    runs {
        client("debugClient") {
            username = "TestPlayer"
            systemProperty("forge.logging.console.level", "trace")
        }
    }
}
```

Custom runs appear in IntelliJ alongside the defaults. The `username` field is optional and sets the in-game player name for dev runs.

### Run DSL methods

| Method | Description |
|--------|-------------|
| `client(name)` | Creates a client run |
| `server(name)` | Creates a server run |
| `run(name) { client() }` | Fully custom run, set type inside |
| `username = "..."` | Set dev player name |
| `jvmArg(arg)` | Add JVM argument |
| `programArg(arg)` | Add program argument |
| `systemProperty(k, v)` | Add system property |
| `runDir = "..."` | Custom run directory |

## Kotlin support

Enable Kotlin for all subprojects of a version:

```kotlin
version("1.21.1") {
    kotlin()  // uses Kotlin 2.1.20 by default
    // or
    kotlin("2.0.21")  // specific version

    fabric { loaderVersion = "0.16.2" }
    neoforge { loaderVersion = "21.1.26" }
}
```

This applies `org.jetbrains.kotlin.jvm` to both the common and all loader subprojects, and configures the JVM target to match the Minecraft version's Java requirement.

The Kotlin plugin must be resolvable from your `pluginManagement` repositories.
