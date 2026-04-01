# Getting Started

## Requirements

- Gradle 8.8 or newer (9.x recommended)
- JDK for your highest MC target (JDK 25 for 26.x, JDK 21 for 1.21.x, JDK 17 for 1.20.x)
- IntelliJ IDEA (recommended)

## Quick start

The easiest way to get started is to use the [Prism Mod Template](https://github.com/Leclowndu93150/prism-mod-template). Click "Use this template" on GitHub and clone your new repo.

The template comes with 1.20.1 (Fabric + Forge), 1.21.1 (Fabric + NeoForge), and 26.1 (Fabric + NeoForge) pre-configured.

## Manual setup

### 1. Create the project

Create a new directory for your mod and add the Gradle wrapper.

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
        classpath("net.fabricmc:fabric-loom:1.9.2")
        classpath("net.neoforged:moddev-gradle:2.0.141")
        classpath("me.modmuss50:mod-publish-plugin:1.1.0")
    }
}

apply(plugin = "dev.prism.settings")

rootProject.name = "my-mod"

extensions.configure<dev.prism.gradle.dsl.PrismSettingsExtension>("prism") {
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

The settings plugin registers a Gradle subproject for each entry. The above creates:

- `:1.20.1:common`, `:1.20.1:fabric`, `:1.20.1:forge`
- `:1.21.1:common`, `:1.21.1:fabric`, `:1.21.1:neoforge`

The foojay plugin lets Gradle auto-download the correct JDK for each version.

### 3. Build file

Create `build.gradle.kts` in the project root:

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

    version("1.20.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.92.7+1.20.1")
        }
        forge {
            loaderVersion = "47.4.18"
            loaderVersionRange = "[47,)"
        }
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
  1.20.1/
    common/src/main/java/com/example/mymod/
    common/src/main/resources/
    fabric/src/main/java/com/example/mymod/fabric/
    fabric/src/main/resources/
    forge/src/main/java/com/example/mymod/forge/
    forge/src/main/resources/
  1.21.1/
    common/src/main/java/com/example/mymod/
    common/src/main/resources/
    fabric/src/main/java/com/example/mymod/fabric/
    fabric/src/main/resources/
    neoforge/src/main/java/com/example/mymod/neoforge/
    neoforge/src/main/resources/
```

### 5. Add metadata files

Place loader-specific metadata files in the `resources` folder of each loader subproject. Use `${variable}` placeholders that Prism expands automatically.

**Fabric** (`versions/1.20.1/fabric/src/main/resources/fabric.mod.json`):
```json
{
  "schemaVersion": 1,
  "id": "${mod_id}",
  "version": "${version}",
  "name": "${mod_name}",
  "description": "${description}",
  "authors": ["${mod_author}"],
  "license": "${license}",
  "environment": "*",
  "entrypoints": {
    "main": ["com.example.mymod.fabric.FabricMod"]
  },
  "depends": {
    "fabricloader": ">=${fabric_loader_version}",
    "minecraft": "${minecraft_version}"
  }
}
```

See [Template Variables](reference/template-variables.md) for the full list.

### 6. Build

```bash
./gradlew build                        # build everything
./gradlew :1.20.1:fabric:build         # build specific target
```

### 7. Run

Run configurations are generated for IntelliJ with version-specific names:

- `Fabric Client (1.20.1)`
- `Fabric Server (1.20.1)`
- `NeoForge Client (1.21.1)`
- `NeoForge Server (1.21.1)`

From the command line:

```bash
./gradlew :1.21.1:neoforge:runClient
./gradlew :1.20.1:fabric:runClient
```

## Java toolchain

Prism auto-detects the required Java version per Minecraft version. With the foojay resolver, Gradle downloads the right JDK automatically. Each subproject compiles with its correct JDK even if Gradle runs on a different one.

| Minecraft | Java |
|-----------|------|
| 1.18.x - 1.20.x | 17 |
| 1.21.x | 21 |
| 26.x | 25 |
