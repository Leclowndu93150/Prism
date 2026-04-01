<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/en/3/3b/Dark_Side_of_the_Moon.png" alt="Prism" width="300">
</p>

<h1 align="center">Prism</h1>
<p align="center">A Gradle plugin for multi-version, multi-loader Minecraft mod development in a single branch.</p>

Prism lets you target multiple Minecraft versions and mod loaders (Fabric, NeoForge, Forge) from one project, without switching branches. Each version gets its own isolated set of source folders, and Prism handles all the wiring.

## How it works

Prism is a thin wrapper around the real toolchains. It applies [Fabric Loom](https://github.com/FabricMC/fabric-loom), [ModDevGradle](https://github.com/neoforged/ModDevGradle), and MDG Legacy to subprojects it generates for you. You get full IDE support, run configurations, and all the features of the underlying plugins.

```
versions/
  1.20.1/
    common/src/main/java/
    fabric/src/main/java/
    forge/src/main/java/
  1.21.1/
    common/src/main/java/
    fabric/src/main/java/
    neoforge/src/main/java/
```

## Quick start

**settings.gradle.kts**
```kotlin
plugins {
    id("dev.prism.settings") version "0.1.0"
}

prism {
    version("1.20.1") {
        common()
        fabric()
        forge()
    }
    version("1.21.1") {
        common()
        fabric()
        neoforge()
    }
}
```

**build.gradle.kts**
```kotlin
plugins {
    id("dev.prism")
}

prism {
    metadata {
        modId = "mymod"
        name = "My Mod"
        description = "A mod."
        license = "MIT"
        author("YourName")
    }

    version("1.20.1") {
        fabric {
            loaderVersion = "0.14.19"
            fabricApi("0.91.0")
        }
        forge {
            loaderVersion = "47.2.0"
        }
    }

    version("1.21.1") {
        fabric {
            loaderVersion = "0.16.2"
            fabricApi("0.102.1")
        }
        neoforge {
            loaderVersion = "21.1.26"
        }
    }
}
```

No `build.gradle.kts` files are needed in the version subfolders.

## Output

JARs are named `{modId}-{mcVersion}-{Loader}-{version}.jar`:

```
mymod-1.20.1-Fabric-1.0.0.jar
mymod-1.20.1-Forge-1.0.0.jar
mymod-1.21.1-Neoforge-1.0.0.jar
```

## Features

- One branch for all versions and loaders
- Full IntelliJ indexing and run configurations per target
- Wraps Fabric Loom, ModDevGradle, and MDG Legacy
- Automatic NeoForm version resolution
- Template variable expansion in metadata files
- Optional CurseForge and Modrinth publishing via [mod-publish-plugin](https://github.com/modmuss50/mod-publish-plugin)
- Parchment mappings support
- Access widener and access transformer support

## Documentation

Full documentation is available at [prism.dev](https://prism.dev) (or see the `docs/` folder).

## License

MIT
