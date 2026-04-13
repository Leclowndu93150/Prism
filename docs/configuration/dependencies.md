---
sidebar_position: 5
---

# Dependencies

## Built-in dependencies

Prism automatically adds the following to each version's common subproject:

- `org.spongepowered:mixin:0.8.5` (compileOnly, always)
- `io.github.llamalad7:mixinextras-common:0.3.5` (compileOnly + annotation processor, only when Forge is not a loader for that version)

MixinExtras is bundled by Fabric Loader and NeoForge, but not by Forge. If your version targets Forge, MixinExtras is not added to common. You can add it per-loader if needed.

For Fabric, Minecraft, Fabric Loader, and optionally Fabric API are added automatically.

For NeoForge and Forge, the loader handles Minecraft and loader dependencies through the extension.

## Common dependencies

Dependencies shared across all loaders for a version:

```kotlin
version("1.21.1") {
    common {
        implementation("some:shared-library:1.0")
        compileOnly("some:api:2.0")
    }

    fabric { loaderVersion = "0.16.2" }
    neoforge { loaderVersion = "21.1.26" }
}
```

Common dependencies are added to the common subproject's configurations and are available to all loader subprojects through the source wiring.

## Per-loader dependencies

Each loader has its own `dependencies` block:

```kotlin
fabric {
    loaderVersion = "0.16.2"
    fabricApi("0.102.1")

    dependencies {
        modImplementation("curse.maven:jei-238222:4613379")
        modCompileOnly("modrinth:sodium:0.5.3")
        implementation("com.google.code.gson:gson:2.10.1")
        jarJar("some:library:[1.0,2.0)")
    }
}

forge {
    loaderVersion = "47.4.16"

    dependencies {
        modImplementation("curse.maven:jei-238222:7391695")
        modCompileOnly("curse.maven:polymorph-388800:6450982")
        implementation("com.google.code.gson:gson:2.10.1")
    }
}

neoforge {
    loaderVersion = "21.1.26"

    dependencies {
        implementation("curse.maven:jei-238222:4613379")
        jarJar("some:library:[1.0,2.0)")
    }
}
```

### Available configurations

| Method | Description |
|--------|-------------|
| `api(dep)` | Exported API dependency |
| `implementation(dep)` | Compile and runtime dependency (no remapping) |
| `modApi(dep)` | Exported mod dependency, remapped when supported |
| `compileOnlyApi(dep)` | Exported compile-only API dependency |
| `modImplementation(dep)` | Mod dependency, remapped to dev mappings |
| `compileOnly(dep)` | Compile-time only (no remapping) |
| `modCompileOnlyApi(dep)` | Exported compile-only mod dependency, remapped when supported |
| `modCompileOnly(dep)` | Mod compile-time only, remapped to dev mappings |
| `runtimeOnly(dep)` | Runtime only (no remapping) |
| `modRuntimeOnly(dep)` | Mod runtime only, remapped to dev mappings |
| `jarJar(dep)` | Embed dependency in output JAR |
| `shadow(dep)` | Shade dependency into output JAR (merges classes, avoids split packages) |
| `localJar(path)` | Local JAR file (defaults to compileOnly) |
| `localJar(path, config)` | Local JAR file with custom configuration |
| `configuration(name, dep)` | Add a dependency to a custom Gradle configuration |
| `modConfiguration(name, dep)` | Add a dependency to `mod{Name}` when present |

Use `mod*` variants for **Minecraft mod JARs** (from CurseMaven, Modrinth Maven, or mod maven repos). These remap the dependency from production mappings (SRG/intermediary) to dev mappings so you don't get `NoSuchMethodError` / `NoSuchFieldError` crashes.

Use plain `implementation` / `compileOnly` / `runtimeOnly` for **regular Java libraries** that don't reference Minecraft classes (e.g. Gson, Apache Commons). These don't need remapping.

On Fabric, `mod*` maps to Loom's remapping configurations. On Forge, `mod*` maps to MDG Legacy's remapping transform. On NeoForge, no remapping is needed so `mod*` behaves the same as the plain variants.

## Custom configurations

Create an extra configuration in the loader block, then add dependencies to it:

```kotlin
forge {
    loaderVersion = "47.4.16"
    configuration("localRuntime")
    remapConfiguration("optionalMods")

    dependencies {
        configuration("localRuntime", "com.example:helper:1.0")
        modConfiguration("optionalMods", "curse.maven:jei-238222:7391695")
    }
}
```

Notes:

- `configuration("name")` creates a plain custom Gradle configuration for that loader project.
- `remapConfiguration("name")` is currently Forge-only and creates both `name` and `mod{Name}` through MDG Legacy.
- `modConfiguration(name, dep)` falls back to the plain configuration with a warning if `mod{Name}` does not exist.

## Shared common dependencies

Dependencies in the `sharedCommon` block are propagated to all loader subprojects:

```kotlin
prism {
    sharedCommon {
        dependencies {
            implementation("com.example:my-library:1.0")
        }
    }
}
```

For Forge and NeoForge loaders, shared common `implementation`, `api`, and `runtimeOnly` dependencies are automatically:

- Added as `compileOnly` so they don't end up on Forge's module path (avoids `SecureJarHandler` crashes)
- Added to `additionalRuntimeClasspath` so they are visible during dev runs (`runClient`/`runServer`)

On Fabric, shared common dependencies are added normally (Fabric's classloading handles them without issues).

### Shading with Shadow

Use `shadow(dep)` to shade a dependency into the output JAR. This merges the dependency's classes directly into your mod JAR, avoiding split-package errors that `jarJar()` can cause when multiple JARs share the same Java package.

```kotlin
prism {
    sharedCommon {
        dependencies {
            shadow("com.example:my-library:1.0")       // shaded into output JAR
            shadow("com.example:my-other-lib:2.0")      // also shaded
            implementation("com.google.code.gson:gson:2.10.1")  // NOT shaded, just a normal dep
        }
    }
}

// Also works in per-loader dependency blocks:
forge {
    dependencies {
        shadow("some:library:1.0")
    }
}
```

On Forge/NeoForge, `shadow(dep)` dependencies are:
- Added to `compileOnly` (available at compile time)
- Added to `additionalRuntimeClasspath` (available during dev runs)
- Added to the `shadow` configuration (merged into the output JAR by the `shadowJar` task)

On Fabric, `shadow(dep)` maps to `implementation` + `include` (Loom's Jar-in-Jar).

Prism automatically applies the [Shadow Gradle plugin](https://github.com/GradleUp/shadow) when any `shadow()` dependency is declared. Use `shadow()` instead of `jarJar()` when your dependencies share Java packages across multiple JARs.

## Jar-in-Jar

The `jarJar()` method embeds a dependency inside your output JAR:

```kotlin
fabric {
    dependencies {
        jarJar("some:library:1.0")
    }
}
```

On Fabric, this maps to Loom's `include` configuration. On NeoForge/Forge, this maps to MDG's `jarJar` configuration.

## Local JARs

Use `localJar()` to depend on a JAR file from disk:

```kotlin
neoforge {
    loaderVersion = "21.1.26"

    dependencies {
        localJar("libs/some-mod.jar")                          // compileOnly (default)
        localJar("libs/some-lib.jar", "implementation")        // implementation
    }
}
```

Paths are relative to the root project directory. Absolute paths are also supported.

## Maven repositories

Prism provides shortcuts for common Minecraft mod repositories:

```kotlin
prism {
    curseMaven()      // https://cursemaven.com
    modrinthMaven()   // https://api.modrinth.com/maven
    maven("BlameJared", "https://maven.blamejared.com")
}
```

These repositories are added to all subprojects.

### CurseMaven dependency format

```
curse.maven:{slug}-{projectId}:{fileId}
```

Example: `curse.maven:jei-238222:4613379`

### Modrinth Maven dependency format

```
maven.modrinth:{slug}:{version}
```

Example: `maven.modrinth:sodium:mc1.21-0.5.11`
