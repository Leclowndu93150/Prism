---
sidebar_position: 2
---

# Getting Started

## Requirements

- Gradle 8.8 or newer (9.x recommended)
- JDK for your highest MC target (JDK 25 for 26.x, JDK 21 for 1.21.x, JDK 17 for 1.20.x)
- IntelliJ IDEA (recommended)

## Quick start (recommended)

Use the [Prism Mod Template](https://github.com/Leclowndu93150/prism-mod-template). Click **Use this template** on GitHub, clone your new repo, and open it in IntelliJ.

The template comes with 1.20.1 (Fabric + Forge), 1.21.1 (Fabric + NeoForge), and 26.1 (Fabric + NeoForge) pre-configured, with the Prism JAR bundled in `libs/`.

Edit `build.gradle.kts` to change your mod ID, name, and loader versions. Edit `settings.gradle.kts` to add or remove versions and loaders.

## Manual setup

If you want to set up from scratch instead of using the template:

### 1. Create the project

```bash
mkdir my-mod && cd my-mod
gradle wrapper --gradle-version 9.2
```

Copy `libs/prism-gradle-plugin-0.1.0.jar` from the [template repo](https://github.com/Leclowndu93150/prism-mod-template/tree/main/libs) into your project's `libs/` directory.

### 2. Settings file

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        maven { url = uri("https://libraries.minecraft.net/") }
    }
    dependencies {
        classpath(files("libs/prism-gradle-plugin-0.1.0.jar"))
        classpath("net.fabricmc:fabric-loom:1.15.5")
        classpath("net.neoforged:moddev-gradle:2.0.141")
        classpath("me.modmuss50:mod-publish-plugin:1.1.0")
    }
}

apply(plugin = "dev.prism.settings")

rootProject.name = "my-mod"

extensions.configure<dev.prism.gradle.dsl.PrismSettingsExtension>("prism") {
    version("1.21.1") {
        common()
        fabric()
        neoforge()
    }
}
```

The `buildscript` block loads Prism and its dependencies onto the classpath. The foojay plugin lets Gradle auto-download the correct JDK per Minecraft version.

### 3. Build file

Create `build.gradle.kts`:

```kotlin
apply(plugin = "dev.prism")

group = "com.example"
version = "1.0.0"

extensions.configure<dev.prism.gradle.dsl.PrismExtension>("prism") {
    metadata {
        modId = "mymod"
        name = "My Mod"
        description = "A Minecraft mod."
        license = "MIT"
        author("YourName")
    }

    version("1.21.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.116.9+1.21.1")
        }
        neoforge {
            loaderVersion = "21.1.222"
            loaderVersionRange = "[4,)"
        }
    }
}
```

No `build.gradle.kts` files are needed inside the version folders.

### 4. Create source directories

```
versions/
  1.21.1/
    common/src/main/java/com/example/mymod/
    common/src/main/resources/
    fabric/src/main/java/com/example/mymod/fabric/
    fabric/src/main/resources/
    neoforge/src/main/java/com/example/mymod/neoforge/
    neoforge/src/main/resources/
```

### 5. Add metadata files

Place `fabric.mod.json`, `neoforge.mods.toml`, etc. in each loader's `src/main/resources/`. Use `${variable}` placeholders for values Prism expands automatically. See [Template Variables](reference/template-variables.md) for the full list.

### 6. Build and run

```bash
./gradlew build                        # build everything
./gradlew :1.21.1:fabric:build         # build specific target
./gradlew :1.21.1:neoforge:runClient   # run specific target
```

Run configurations appear in IntelliJ with version-specific names like `Fabric Client (1.21.1)` and `NeoForge Client (1.21.1)`.

## What goes where

| Code | Location | Has Minecraft classes? |
|------|----------|----------------------|
| Shared across all versions | `common/` (requires `sharedCommon()`) | No |
| Shared across loaders for a version | `versions/{mc}/common/` | Yes (vanilla) |
| Fabric-specific | `versions/{mc}/fabric/` | Yes (with Fabric API) |
| NeoForge-specific | `versions/{mc}/neoforge/` | Yes (with NeoForge) |
| Forge-specific | `versions/{mc}/forge/` | Yes (with Forge) |

Common code can't use loader APIs (Fabric API, NeoForge events). Put loader-specific code in the loader folders.

## Java toolchain

Prism sets the correct JDK per Minecraft version automatically:

| Minecraft | Java |
|-----------|------|
| 1.18.x - 1.20.x | 17 |
| 1.21.x | 21 |
| 26.x | 25 |

With foojay, Gradle downloads the right JDK if it's not installed. Override with `javaVersion` in the version block.

## Next steps

- [Loaders](configuration/loaders.md) for loader-specific configuration
- [Dependencies](configuration/dependencies.md) for adding libraries and mods
- [Publishing](publishing.md) for CurseForge and Modrinth
- [DSL Reference](reference/dsl.md) for the complete API
