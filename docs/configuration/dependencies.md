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
| `implementation(dep)` | Compile and runtime dependency (no remapping) |
| `modImplementation(dep)` | Mod dependency, remapped to dev mappings |
| `compileOnly(dep)` | Compile-time only (no remapping) |
| `modCompileOnly(dep)` | Mod compile-time only, remapped to dev mappings |
| `runtimeOnly(dep)` | Runtime only (no remapping) |
| `modRuntimeOnly(dep)` | Mod runtime only, remapped to dev mappings |
| `jarJar(dep)` | Embed dependency in output JAR |
| `localJar(path)` | Local JAR file (defaults to compileOnly) |
| `localJar(path, config)` | Local JAR file with custom configuration |

Use `mod*` variants for **Minecraft mod JARs** (from CurseMaven, Modrinth Maven, or mod maven repos). These remap the dependency from production mappings (SRG/intermediary) to dev mappings so you don't get `NoSuchMethodError` / `NoSuchFieldError` crashes.

Use plain `implementation` / `compileOnly` / `runtimeOnly` for **regular Java libraries** that don't reference Minecraft classes (e.g. Gson, Apache Commons). These don't need remapping.

On Fabric, `mod*` maps to Loom's remapping configurations. On Forge, `mod*` maps to MDG Legacy's remapping transform. On NeoForge, no remapping is needed so `mod*` behaves the same as the plain variants.

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
