---
sidebar_position: 5
---

# Dependencies

## Built-in dependencies

Prism automatically adds the following to the common subproject:

- `org.spongepowered:mixin:0.8.5` (compileOnly)
- `io.github.llamalad7:mixinextras-common:0.3.5` (compileOnly + annotation processor)

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
        jarJar("some:library:[1.0,2.0)")
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
| `implementation(dep)` | Compile and runtime dependency |
| `modImplementation(dep)` | Mod dependency (remapped on Fabric via `modImplementation`) |
| `compileOnly(dep)` | Compile-time only |
| `modCompileOnly(dep)` | Mod compile-time only (remapped on Fabric) |
| `runtimeOnly(dep)` | Runtime only |
| `modRuntimeOnly(dep)` | Mod runtime only (remapped on Fabric) |
| `jarJar(dep)` | Embed dependency in output JAR |

For Fabric subprojects, `mod*` variants are mapped to Loom's remapping configurations (`modImplementation`, `modCompileOnly`, etc.). For NeoForge/Forge, they map to standard Gradle configurations since MDG handles remapping differently.

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
