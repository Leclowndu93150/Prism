# Loaders

## Fabric

Uses [Fabric Loom](https://github.com/FabricMC/fabric-loom) under the hood.

```kotlin
version("1.21.1") {
    fabric {
        loaderVersion = "0.16.2"
        fabricApi("0.102.1")  // optional
    }
}
```

| Property        | Required | Description                          |
|-----------------|----------|--------------------------------------|
| `loaderVersion` | Yes      | Fabric Loader version                |
| `fabricApi()`   | No       | Adds Fabric API as a dependency      |

Access wideners are automatically picked up from `common/src/main/resources/{modId}.accesswidener`.

## NeoForge

Uses [ModDevGradle](https://github.com/neoforged/ModDevGradle) under the hood.

```kotlin
version("1.21.1") {
    neoforge {
        loaderVersion = "21.1.26"
        loaderVersionRange = "[4,)"  // optional, for mods.toml
    }
}
```

| Property              | Required | Description                              |
|-----------------------|----------|------------------------------------------|
| `loaderVersion`       | Yes      | NeoForge version                         |
| `loaderVersionRange`  | No       | Version range for template expansion     |

Access transformers are picked up from `common/src/main/resources/META-INF/accesstransformer.cfg`.

Run configurations generated: `client`, `server`, `data`.

## Forge (Legacy)

Uses [ModDevGradle Legacy](https://github.com/neoforged/ModDevGradle) for Forge 1.17 through 1.20.1.

```kotlin
version("1.20.1") {
    forge {
        loaderVersion = "47.2.0"
        loaderVersionRange = "[47,)"  // optional
    }
}
```

| Property              | Required | Description                              |
|-----------------------|----------|------------------------------------------|
| `loaderVersion`       | Yes      | Forge version (without MC prefix)        |
| `loaderVersionRange`  | No       | Version range for template expansion     |

The version string passed to MDG Legacy is `{mcVersion}-{loaderVersion}`, so `1.20.1-47.2.0`.

Run configurations generated: `client`, `server`, `data`.

## Adding dependencies per loader

Add dependencies in each loader's `build.gradle.kts` ... wait, there are no subproject build files. Dependencies specific to a loader should be added to the common subproject's configurations or through the Prism DSL (not yet supported for arbitrary deps).

For now, if you need loader-specific dependencies beyond the built-in ones, you can access the subproject directly:

```kotlin
// In root build.gradle.kts, after the prism block:
project(":1.21.1:fabric").afterEvaluate {
    dependencies {
        add("modImplementation", "some:mod:1.0")
    }
}
```
