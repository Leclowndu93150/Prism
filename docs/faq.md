---
sidebar_position: 6
---

# FAQ

## Can I use Kotlin?

Yes. Add `kotlin()` to your version block:

```kotlin
version("1.21.1") {
    kotlin()           // uses 2.1.20 by default
    kotlin("2.0.21")   // or pick a version
}
```

This applies the Kotlin JVM plugin to common and all loader subprojects, with the JVM target matching the Minecraft version.

## Can I use Yarn mappings?

Yes, for Fabric on obfuscated versions (pre-26.x):

```kotlin
fabric {
    loaderVersion = "0.18.6"
    yarn("1.21.1+build.3")
}
```

If `yarn()` is not set, Prism uses official Mojang mappings by default. On 26.x (unobfuscated), no mappings are needed and Yarn is not applicable.

## Do I need common() for every version?

No. If a version only targets one loader, skip `common()`:

```kotlin
version("1.21.1") {
    neoforge()   // single-loader mode, no common/loader split
}
```

The version folder itself becomes the project (`versions/1.21.1/src/main/java/`). No subdirectories needed. Build with `./gradlew :1.21.1:build`.

Use `common()` only when you have multiple loaders and want shared code between them.

## Can I use Fabric API in common code?

No. The version-specific common project (`versions/{mc}/common/`) only has access to vanilla Minecraft classes. Loader APIs like Fabric API, NeoForge events, or Forge events are only available in the loader-specific folders.

Put shared logic that doesn't touch loader APIs in common, and put loader-specific code (event handlers, registration) in the loader folders.

## Can I share code between versions?

By default, no. Each version is fully independent.

If you have pure Java code (interfaces, annotations, utilities) that doesn't touch Minecraft APIs, enable the shared common:

```kotlin
// settings.gradle.kts
prism {
    sharedCommon()
    version("1.20.1") { ... }
    version("1.21.1") { ... }
}
```

This creates a root `common/` folder compiled with the lowest Java version across your targets. It has no access to Minecraft classes. See [Project Structure](configuration/project-structure.md) for details.

## How do I add dependencies?

Use the `dependencies` block inside each loader config, or `common` for shared dependencies:

```kotlin
version("1.21.1") {
    common {
        implementation("some:shared-lib:1.0")
    }
    fabric {
        loaderVersion = "0.18.6"
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
        jarJar("some:library:1.0")         // uses Fabric's include
    }
}
neoforge {
    dependencies {
        jarJar("some:library:[1.0,2.0)")   // uses NeoForge's jarJar
    }
}
```

## How does datagen work?

**Fabric**: Call `datagen()` in your fabric config. Requires Fabric API. Output goes to `src/main/generated`.

**NeoForge**: Prism auto-detects the Minecraft version:
- 1.21.3 and older: single `data` run
- 1.21.4 and newer: split `clientData` and `serverData` runs

Output goes to `src/generated/resources`.

**Forge**: Single `data` run. Output goes to `src/generated/resources`.

## How do I add custom run configurations?

Use the `runs` block inside any loader config:

```kotlin
fabric {
    loaderVersion = "0.18.6"
    runs {
        client("testClient") { username = "Dev" }
        server("secondServer") { }
    }
}
```

See [Loaders](configuration/loaders.md#custom-run-configurations) for the full run DSL.

## How do I escape to raw Gradle or the underlying plugin?

Use the raw hooks when Prism's DSL does not expose a setting yet:

```kotlin
version("1.21.1") {
    rawCommonProject { project -> }

    fabric {
        rawLoom { loom -> }
        rawProject { project -> }
    }

    forge {
        rawLegacyForge { ext -> }
        rawProject { project -> }
    }

    neoforge {
        rawNeoForge { ext -> }
        rawProject { project -> }
    }
}

sharedCommon {
    rawProject { project -> }
}
```

The underlying-plugin hooks run after Prism's own configuration, so they are suitable for overrides.

## How do I control mixin auto-detection?

Use the `mixins` block on Fabric, Forge, or NeoForge:

```kotlin
forge {
    mixins {
        config("mymod.mixins.json")
        refmap("mymod.refmap.json")
        disableAutoDetect()
    }
}
```

By default Prism auto-detects `*.mixins.json` files from `src/main/resources` recursively. Disable auto-detect if you want full manual control.

## How does publishing work?

See [Publishing](publishing.md). Key points:

- Display name on CurseForge/Modrinth defaults to the JAR filename (e.g. `mymod-1.21.1-NeoForge-1.0.0.jar`)
- Override with `displayName` in the publishing block
- Override the uploaded artifact with `artifactTask()` or `artifactFile()` when needed
- Publishing dependencies (requires, optional, incompatible) can be set globally, per version, or per loader
- All three levels stack

## Is there a diagnostic task?

Yes:

```bash
./gradlew prismDoctor
```

It prints the resolved loader projects, underlying plugin, mapping mode, chosen publish task, available remap configurations, and mixin settings.

## How do I add access wideners or access transformers?

Prism auto-detects these. Just place them in the right location.

**Access wideners** (Fabric): Place `{modId}.accesswidener` in either:
- `versions/{mc}/fabric/src/main/resources/` (loader-specific, takes priority)
- `versions/{mc}/common/src/main/resources/` (shared)

Access wideners work on all versions including 26.x. Unobfuscated means names aren't scrambled, not that everything is public.

**Access transformers** (NeoForge/Forge): Place `accesstransformer.cfg` in `META-INF/` under either:
- `versions/{mc}/common/src/main/resources/META-INF/`
- `versions/{mc}/neoforge/src/main/resources/META-INF/` (or `forge/`)

Both locations are checked and combined.

## Can I use a single access widener for all loaders?

Yes. Use `accessWidener()` in your version block to point to a single `.accesswidener` file:

```kotlin
version("1.21.1") {
    accessWidener("src/main/resources/mymod.accesswidener")

    fabric { loaderVersion = "0.18.6" }
    neoforge { loaderVersion = "21.1.26" }
}
```

Prism will use the file as-is for Fabric, and automatically convert it to an `accesstransformer.cfg` for NeoForge and Forge. The conversion is a pure format translation (both use Mojang-mapped names at dev time). The generated AT file is placed in `build/generated/prism/at/`.

If a loader subproject already has its own `accesstransformer.cfg`, the conversion is skipped for that loader.

This works for Forge (1.17-1.20.1) and NeoForge (1.20.2+). Legacy Forge (1.7.10-1.12.2) uses MCP mappings and requires manual access transformers via `accessTransformer("path")`.

## How does the common project compile?

For 1.20.2+, the common subproject uses ModDevGradle with `neoFormVersion` (vanilla Minecraft, no loader).

For 1.20.1 and older, it uses MDG Legacy with `mcpVersion` set to the Minecraft version.

In both cases, common has full access to vanilla Minecraft classes but not loader APIs.

Common source files are recompiled as part of each loader's compilation, so they have access to the full loader classpath at build time.

## Can I have multiple mods in one project?

Yes. Use `mod()` to create a multi-mod workspace where each module is an independent mod with its own modId, metadata, and publishing:

```kotlin
// settings.gradle.kts
prism {
    mod("corpse-curios") {
        version("1.21.1") { common(); neoforge(); fabric() }
    }
    mod("corpse-cosmetic") {
        version("1.21.1") { neoforge() }
    }
}

// build.gradle.kts
prism {
    curseMaven()

    mod("corpse-curios") {
        metadata {
            modId = "corpse_curios_compat"
            name = "Corpse x Curios Compat"
        }
        version("1.21.1") {
            neoforge { loaderVersion = "21.1.26" }
            fabric { loaderVersion = "0.18.6"; fabricApi("0.102.1") }
        }
        publishing {
            curseforge { projectId = "111111" }
        }
    }

    mod("corpse-cosmetic") {
        metadata {
            modId = "corpse_cosmetic_compat"
            name = "Corpse x Cosmetic Armor Compat"
        }
        version("1.21.1") {
            neoforge { loaderVersion = "21.1.26" }
        }
        publishing {
            curseforge { projectId = "222222" }
        }
    }
}
```

Directory layout:
```
modules/
  corpse-curios/versions/1.21.1/common/
  corpse-curios/versions/1.21.1/neoforge/
  corpse-curios/versions/1.21.1/fabric/
  corpse-cosmetic/versions/1.21.1/neoforge/
```

Gradle subprojects: `:corpse-curios:1.21.1:neoforge`, `:corpse-cosmetic:1.21.1:neoforge`, etc. Build a single module with `./gradlew :corpse-curios:1.21.1:neoforge:build` or all with `./gradlew build`.

Modules can coexist with the standard single-mod `version()` blocks if needed.

### Inter-module dependencies

If one module needs compile-time access to another module's code, use `dependsOn()`:

```kotlin
mod("core-lib") {
    metadata { modId = "core_lib" }
    version("1.21.1") { common(); neoforge(); fabric() }
}

mod("addon") {
    dependsOn("core-lib")
    metadata { modId = "addon" }
    version("1.21.1") { common(); neoforge(); fabric() }
}
```

This gives the `addon` module compile-time visibility of `core-lib`'s common code across all matching versions and loaders. At runtime, both mods must be installed. You can pass multiple module names: `dependsOn("core-lib", "other-mod")`.

Prism wires the dependency using compiled class output from the dependency module's common project, so there are no remapping issues across Fabric, Forge, and NeoForge.

## NeoForm version resolution fails

1. Check your internet connection. Prism fetches from `maven.neoforged.net`.
2. If offline, set `neoFormVersion` manually in the version block.
3. Cache is at `~/.gradle/caches/prism/neoform-versions.txt` (24h TTL). Delete to refresh.

## Run configurations are missing

Reload the Gradle project in IntelliJ after changing the Prism configuration. Runs are generated during Gradle sync.

## What Gradle version do I need?

Gradle 8.8 or newer. 9.x recommended.

## What Java version do I need?

You need the highest JDK required by any of your targets. Prism sets the correct toolchain per version automatically:

| Minecraft | Java |
|-----------|------|
| 1.18.x - 1.20.x | 17 |
| 1.21.x | 21 |
| 26.x | 25 |

Add the [foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) so Gradle auto-downloads missing JDKs:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
```

Override with `javaVersion` in the version block.
