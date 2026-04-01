# FAQ

## Can I use Kotlin?

Yes. Add `kotlin()` to your version block:

```kotlin
version("1.21.1") {
    kotlin()  // uses 2.1.20 by default
    kotlin("2.0.21")  // or pick a version

    fabric { loaderVersion = "0.16.2" }
    neoforge { loaderVersion = "21.1.26" }
}
```

This applies the Kotlin JVM plugin to both common and all loader subprojects, with the JVM target set to match the Minecraft version.

## How do I add dependencies?

Use the `dependencies` block inside each loader config, or `common` for shared dependencies:

```kotlin
version("1.21.1") {
    common {
        implementation("some:shared-lib:1.0")
    }
    fabric {
        loaderVersion = "0.16.2"
        dependencies {
            modImplementation("curse.maven:jei-238222:4613379")
        }
    }
}
```

See [Dependencies](configuration/dependencies.md) for details.

## How do I use CurseMaven or Modrinth Maven?

```kotlin
prism {
    curseMaven()
    modrinthMaven()
}
```

Then use `curse.maven:slug-projectId:fileId` or `maven.modrinth:slug:version` in your dependency blocks.

## How do I embed a library in my JAR?

Use `jarJar()` in the dependency block:

```kotlin
fabric {
    dependencies {
        jarJar("some:library:1.0")  // uses Fabric's include
    }
}
neoforge {
    dependencies {
        jarJar("some:library:[1.0,2.0)")  // uses NeoForge's jarJar
    }
}
```

## Can I share code between versions?

No. Each version is fully independent. If you need the same class in 1.20.1 and 1.21.1, copy it into both `common` folders. This is intentional: Minecraft APIs change significantly between versions, and shared code creates more problems than it solves.

## How does datagen work?

**Fabric**: Call `datagen()` in your fabric config. Requires Fabric API. Creates a datagen run using Fabric API's system properties. Output goes to `src/main/generated`.

**NeoForge**: Prism auto-detects the Minecraft version:

- 1.21.3 and older: creates a single `data` run
- 1.21.4 and newer: creates split `clientData` and `serverData` runs

Output goes to `src/generated/resources`.

**Forge**: Creates a `data` run. Output goes to `src/generated/resources`.

## Can I publish multiple Minecraft versions?

Yes. Use `minecraftVersions()` in the version block to list all compatible versions:

```kotlin
version("1.21.1") {
    minecraftVersions("1.21", "1.21.1")
    // ...
}
```

When publishing, all listed versions will be added to CurseForge and Modrinth.

## Can I add a subproject build file?

You can, but it may conflict with Prism's configuration. Prism configures everything from the root project. If your subproject build file also applies plugins or configures the same tasks, you will get errors.

## How does the common project compile?

The common subproject uses ModDevGradle with `neoFormVersion` only (vanilla Minecraft, no loader). This gives you access to all Minecraft classes without loader modifications.

Common source files are compiled again as part of each loader's compilation, so they have access to loader APIs at compile time.

## NeoForm version resolution fails

1. Check your internet connection. Prism fetches version metadata from `maven.neoforged.net`.
2. If building offline, set `neoFormVersion` manually in the version block.
3. The resolved version is cached for 24 hours in `~/.gradle/caches/prism/`. Delete this file to force a refresh.

## Run configurations are missing

Reload the Gradle project in IntelliJ after changing the Prism configuration. Run configurations are generated during Gradle sync.

## How do I add access wideners or access transformers?

**Access wideners** (Fabric): Place `{modId}.accesswidener` in `versions/{mc}/common/src/main/resources/`.

**Access transformers** (NeoForge/Forge): Place `accesstransformer.cfg` in `versions/{mc}/common/src/main/resources/META-INF/` or in the loader's own `src/main/resources/META-INF/`. Both locations are checked.

## What Gradle version do I need?

Gradle 8.8 or newer. Gradle 9.x is recommended.

## What Java version do I need?

You need the highest JDK required by any of your target versions. Prism auto-detects and sets the correct toolchain per Minecraft version:

| Minecraft | Java |
|-----------|------|
| 1.16.x and older | 8 |
| 1.17.x | 16 |
| 1.18.x - 1.20.x | 17 |
| 1.21.x | 21 |
| 26.x and newer | 25 |

Each subproject compiles with the right JDK regardless of which JDK Gradle itself runs on. Add the [foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) to your `settings.gradle.kts` so Gradle can auto-download missing JDKs:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
```

Override with `javaVersion` in the version block if needed.
