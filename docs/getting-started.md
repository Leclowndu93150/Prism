# Getting Started

## Requirements

- Gradle 8.8 or newer (9.x recommended)
- JDK 21
- IntelliJ IDEA (recommended)

## 1. Create the project

Create a new directory for your mod and add the Gradle wrapper.

## 2. Settings file

Create `settings.gradle.kts` and declare which versions and loaders you want:

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

The settings plugin registers a Gradle subproject for each entry. For example, the above creates:

- `:1.20.1:common`
- `:1.20.1:fabric`
- `:1.20.1:forge`
- `:1.21.1:common`
- `:1.21.1:fabric`
- `:1.21.1:neoforge`

## 3. Build file

Create `build.gradle.kts` in the project root:

```kotlin
plugins {
    id("dev.prism")
}

group = "com.example"
version = "1.0.0"

prism {
    metadata {
        modId = "mymod"
        name = "My Mod"
        description = "A Minecraft mod."
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

You do not need `build.gradle.kts` files inside the version folders. Prism configures everything from the root.

## 4. Create source directories

Create the folder structure under `versions/`:

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

## 5. Add metadata files

Place loader-specific metadata files in the `resources` folder of each loader subproject. You can use template variables that Prism expands automatically.

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

## 6. Build

```bash
# Build everything
./gradlew build

# Build a specific target
./gradlew :1.20.1:fabric:build
./gradlew :1.21.1:neoforge:build
```

## 7. Run

Run configurations are generated for IntelliJ with version-specific names:

- `Fabric Client (1.20.1)`
- `Fabric Server (1.20.1)`
- `NeoForge Client (1.21.1)`
- `NeoForge Server (1.21.1)`
- `NeoForge Data (1.21.1)`

You can also run from the command line:

```bash
./gradlew :1.21.1:neoforge:runClient
./gradlew :1.20.1:fabric:runClient
```
