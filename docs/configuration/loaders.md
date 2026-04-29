---
sidebar_position: 2
---

# Loaders

## Pinned tooling versions

Prism bundles every underlying build plugin on its own classpath at fixed versions:

| Tool                                                                          | Used for                          | Pinned version  |
|-------------------------------------------------------------------------------|-----------------------------------|-----------------|
| [Fabric Loom](https://github.com/FabricMC/fabric-loom)                        | Fabric (all versions)             | `1.16.1`        |
| [ModDevGradle](https://github.com/neoforged/ModDevGradle)                     | NeoForge + Forge 1.17–1.20.1      | `2.0.141`       |
| [ForgeGradle](https://github.com/MinecraftForge/ForgeGradle)                  | LexForge 1.21.1+                  | `7.0.25`        |
| [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)       | Legacy Forge 1.7.10–1.12.2        | `2.0.2`         |

NeoForm versions are **not** pinned — Prism resolves the right NeoForm version per Minecraft version at sync time and caches the lookup in `~/.gradle/caches/prism/neoform-versions.txt`.

If you need a different pinned-tool version (e.g. an alpha for a brand-new Minecraft version, or a hotfix), see [Overriding pinned tooling](#overriding-pinned-tooling) below.

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

Access wideners are picked up from `common/src/main/resources/{modId}.accesswidener` or from the path set via `accessWidener()` in the version block. On Minecraft 26.1+, use the new class-tweaker format: file `{modId}.classtweaker` with header `classTweaker v1 official`. The body syntax (`accessible class/method/field`, `extendable`, `mutable`) is unchanged. The `accessWidener` field in `fabric.mod.json` still points at this file (the JSON key kept its old name for back-compat).

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

        // Mixins: if any *.mixins.json lives in src/main/resources and you have
        // @Mixin-annotated classes, Prism auto-wires MixinBooter + manifest attrs.
        // No extra configuration needed. Optional overrides:
        //   mixinBooter = false                // disable MixinBooter entirely
        //   mixinBooterVersion = "10.7"        // pin a different version
        //   coreMod("com.example.MyFMLPlugin") // skip auto-detect of IFMLLoadingPlugin
        //   mixins { config("extra.mixins.json"); refmap("mymod.refmap.json") }

        dependencies {
            implementation("some:library:1.0")
        }
    }
}
```

| Property             | Required | Default      | Description                         |
|----------------------|----------|--------------|-------------------------------------|
| `mcVersion`          | Yes      | `1.12.2`     | Minecraft version                   |
| `forgeVersion`       | Yes      | -            | Forge version                       |
| `mappingChannel`     | No       | `stable`     | MCP mapping channel                 |
| `mappingVersion`     | No       | `39`         | MCP mapping version                 |
| `username`           | No       | `Developer`  | Dev username for run configs        |
| `mixinBooter`        | No       | `true`       | Auto-add MixinBooter + CleanroomMC maven + AP |
| `mixinBooterVersion` | No       | `10.7`       | MixinBooter version to pin          |

Requires the GTNH maven in `pluginManagement`:
```kotlin
maven { url = uri("https://nexus.gtnewhorizons.com/repository/public/") }
```

Java 8 toolchain with Azul JDK. Run configurations: `runClient`, `runServer`.

### Mixins on 1.12.2

On 1.12.2 Sponge Mixin is loaded via a coremod. Prism automates the entire chain when it sees mixin sources in the project:

1. Adds the CleanroomMC maven (`https://repo.cleanroommc.com/releases`).
2. Adds `zone.rong:mixinbooter:<version>` as both `implementation` and `annotationProcessor` (non-transitive).
3. Auto-registers every `*.mixins.json` under `src/main/resources/` via the jar's `MixinConfigs` manifest attribute.
4. Writes these manifest attributes on the jar task:
   - `FMLCorePlugin = <FQN>` — auto-detected from any class with `@IFMLLoadingPlugin.Name` or `implements IFMLLoadingPlugin` in `src/main/{java,kotlin}`. Override with `coreMod("com.example.MyPlugin")`. If no class is found, this attribute is omitted (late-mixin mods don't need it).
   - `FMLCorePluginContainsFMLMod = "true"`
   - `ForceLoadAsMod = "true"`

"Has mixin sources" means at least one `@Mixin(` annotation is present, or the `mixins { }` block declares explicit configs. If neither applies, Prism does nothing (non-mixin mods stay untouched).

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

## Overriding pinned tooling

The four build plugins listed in [Pinned tooling versions](#pinned-tooling-versions) are loaded as transitive dependencies of the Prism settings plugin. They do **not** pass through the plugin-marker mechanism, so the usual `pluginManagement.resolutionStrategy.eachPlugin` knob is silently ignored for them.

The override happens on the **settings buildscript classpath** instead. Add a `buildscript { configurations.classpath { ... } }` block at the top of `settings.gradle.kts` — **before** the `plugins { }` block — and substitute the version you want:

```kotlin
// settings.gradle.kts — must be the first block in the file
buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "net.fabricmc" && requested.name == "fabric-loom" -> {
                    useVersion("1.17.0-alpha.7")
                    because("need class-tweaker v2 support for 26.x")
                }
                requested.group == "net.neoforged" && requested.name == "moddev-gradle" -> {
                    useVersion("2.0.150")
                }
                requested.group == "net.minecraftforge" && requested.name == "forgegradle" -> {
                    useVersion("7.1.0")
                }
                requested.group == "com.gtnewhorizons" && requested.name == "retrofuturagradle" -> {
                    useVersion("2.1.0")
                }
            }
        }
    }
}

pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://nexus.gtnewhorizons.com/repository/public/") }
        gradlePluginPortal()
    }
}

plugins {
    id("dev.prism.settings") version "+"
}
```

Substitute only the tools you actually need to bump — the `when { }` arms are independent. Order is critical: the `buildscript { }` block must appear **before** `plugins { }`, or the Prism jar (and the bundled tooling) is already on the classpath by the time your substitution runs.

Prism is compiled against each tool's pinned version's API. Bumps within the same minor line (`1.16.x`, `2.0.x`, `7.0.x`, `2.0.x`) generally work; cross-minor or cross-major bumps may compile but break at runtime if the upstream removed or renamed an API symbol Prism touches. Use at your own risk and report breakage so the pin can be raised properly.
