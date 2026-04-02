<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/en/3/3b/Dark_Side_of_the_Moon.png" alt="Prism" width="300">
</p>

<h1 align="center">Prism</h1>
<p align="center">A Gradle plugin for multi-version, multi-loader Minecraft mod development in a single branch.</p>

Prism wraps [Fabric Loom](https://github.com/FabricMC/fabric-loom), [ModDevGradle](https://github.com/neoforged/ModDevGradle), and MDG Legacy. One DSL, one branch, full IDE support.

```
versions/
  1.20.1/
    common/  fabric/  forge/
  1.21.1/
    common/  fabric/  neoforge/
  26.1/
    common/  fabric/  neoforge/
```

## Features

- All versions and loaders in one branch
- Full IntelliJ run configurations per target
- Kotlin support
- Per-loader dependency blocks with Jar-in-Jar
- Custom run configurations with optional username
- CurseMaven and Modrinth Maven built in
- Version-aware datagen (split client/server for 1.21.4+)
- Handles unobfuscated MC (26.x) automatically
- Auto-detects access wideners and access transformers
- Template variable expansion in metadata files
- CurseForge, Modrinth, and Maven publishing
- Common JAR publishing for library mods

## Getting started

Install the [IntelliJ plugin](https://github.com/Leclowndu93150/Prism-Generator) for a project wizard, use the [template](https://github.com/Leclowndu93150/prism-mod-template), or set up manually:

## Install

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("https://maven.leclowndu93150.dev/releases") }
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
    }
}

plugins {
    id("dev.prism.settings") version "+"
}
```

Or use the [template](https://github.com/Leclowndu93150/prism-mod-template). Full docs at [prism.leclowndu93150.dev](https://prism.leclowndu93150.dev).

```kotlin
prism {
    metadata {
        modId = "mymod"
        name = "My Mod"
        license = "MIT"
    }

    version("1.21.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.116.9+1.21.1")

            runs {
                client("testClient") {
                    username = "Dev"
                }
            }
        }
        neoforge {
            loaderVersion = "21.1.222"
        }
    }
}
```

## Output

```
mymod-1.21.1-Fabric-1.0.0.jar
mymod-1.21.1-Neoforge-1.0.0.jar
```

## Documentation

[prism.leclowndu93150.dev](https://prism.leclowndu93150.dev)

## License

MIT
