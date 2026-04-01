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
| `datagen()`     | No       | Enables Fabric API datagen run       |

Access wideners are picked up from `common/src/main/resources/{modId}.accesswidener`.

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

Access transformers are picked up from both `common/src/main/resources/META-INF/accesstransformer.cfg` and the loader's own resources.

### Datagen

Prism detects the Minecraft version and configures datagen accordingly:

- **1.21.3 and older**: Single `data` run
- **1.21.4 and newer**: Split `clientData` and `serverData` runs

Generated resources output to `src/generated/resources` and are automatically added to the source set.

## Forge (Legacy)

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
