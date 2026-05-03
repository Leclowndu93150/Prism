---
sidebar_position: 1
---

# Project Structure

Prism supports three distinct layout patterns. You can mix all three in a single project.

---

## Single-mod, multi-version layout

The standard setup: one mod, multiple Minecraft versions, one or more loaders per version.

### Multi-loader versions

When a version targets multiple loaders, it splits into a `common` subproject and one subproject per loader:

```
versions/
  1.21.1/
    common/src/main/java/        # shared across loaders
    common/src/main/resources/
    fabric/src/main/java/        # Fabric-specific
    fabric/src/main/resources/
    neoforge/src/main/java/      # NeoForge-specific
    neoforge/src/main/resources/
```

**settings.gradle.kts:**
```kotlin
prism {
    version("1.21.1") {
        common()
        fabric()
        neoforge()
    }
}
```

**Gradle paths:**

| Subproject | Path |
|------------|------|
| Common | `:1.21.1:common` |
| Fabric | `:1.21.1:fabric` |
| NeoForge | `:1.21.1:neoforge` |

```bash
./gradlew :1.21.1:fabric:build
./gradlew :1.21.1:neoforge:runClient
```

### Single-loader versions

When a version only targets one loader, skip `common()`. The version folder itself becomes the project:

```
versions/
  1.21.1/
    src/main/java/
    src/main/resources/
```

**settings.gradle.kts:**
```kotlin
prism {
    version("1.21.1") {
        neoforge()   // no common() = single-loader mode
    }
}
```

**Gradle path:** `:1.21.1`

```bash
./gradlew :1.21.1:build
./gradlew :1.21.1:runClient
```

### Mixing both modes

You can freely mix single-loader and multi-loader versions in the same project:

```kotlin
prism {
    version("1.20.1") {
        common()     # multi-loader: common + fabric + forge
        fabric()
        forge()
    }
    version("1.21.1") {
        neoforge()   # single-loader: just neoforge
    }
    version("1.21.4") {
        common()
        fabric()
        neoforge()
        lexForge()   # multi-loader with LexForge (1.21.1+)
    }
}
```

---

## Shared common (cross-version code)

By default, each version is fully independent. If you have pure Java code that should be shared across all versions (interfaces, annotations, utilities with no Minecraft dependency), enable a shared common project.

**settings.gradle.kts:**
```kotlin
prism {
    sharedCommon()                   // creates common/ at project root
    // or
    sharedCommon("versions/common")  // creates common/ inside versions/

    version("1.20.1") { common(); fabric(); forge() }
    version("1.21.1") { common(); fabric(); neoforge() }
}
```

This registers a `:common` Gradle project. Its source is compiled into every version's common project automatically — it is never a separate JAR but is compiled as part of each version's common classpath.

```
common/                           # or versions/common/ with sharedCommon("versions/common")
  src/main/java/
  src/main/resources/
versions/
  1.20.1/
    common/                       # version-specific, shared common compiled in
    fabric/
    forge/
  1.21.1/
    common/
    neoforge/
```

The shared common compiles with the lowest Java version across all targets. It has no access to Minecraft classes.

### Mixins in shared common

If shared common code needs Mixin or MixinExtras access, enable them in the build file:

```kotlin
prism {
    sharedCommon {
        mixin()        // adds Mixin as compileOnly
        mixinExtras()  // adds Mixin + MixinExtras as compileOnly
        dependencies {
            implementation("com.example:my-lib:1.0")
        }
        rawProject { project -> }
    }
}
```

Mixin JSON files from shared common are merged into each loader's compilation.

---

## Multi-mod workspace

Use `mod()` to host multiple independent mods in one repository. Each module has its own `modId`, metadata, publishing configuration, and versioned sources under `modules/`.

### Directory layout

```
modules/
  corpse-curios/
    versions/
      1.21.1/
        common/src/main/java/
        neoforge/src/main/java/
        fabric/src/main/java/
  corpse-cosmetic/
    versions/
      1.21.1/
        neoforge/src/main/java/
```

### Settings

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
```

### Build configuration

```kotlin
// build.gradle.kts
prism {
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
            modId = "corpse_cosmetic"
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

### Gradle paths

| Subproject | Path |
|------------|------|
| corpse-curios 1.21.1 common | `:corpse-curios:1.21.1:common` |
| corpse-curios 1.21.1 NeoForge | `:corpse-curios:1.21.1:neoforge` |
| corpse-curios 1.21.1 Fabric | `:corpse-curios:1.21.1:fabric` |
| corpse-cosmetic 1.21.1 (single-loader) | `:corpse-cosmetic:1.21.1` |

```bash
./gradlew :corpse-curios:1.21.1:neoforge:build
./gradlew :corpse-cosmetic:build          # builds all versions of that module
./gradlew build                           # builds everything
```

### Inter-module dependencies

If one module needs compile-time access to another module's common code, use `dependsOn()`:

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

Prism adds the dependency module's compiled common classes to the dependent module's classpath as `compileOnly` for every matching version and loader. At runtime, both mods must be installed — `dependsOn()` is a compile-time wiring only.

You can depend on multiple modules: `dependsOn("core-lib", "other-mod")`.

---

## Combining all three layouts

`version()` blocks and `mod()` blocks can coexist freely:

```kotlin
// settings.gradle.kts
prism {
    sharedCommon()

    // Standard single-mod versions
    version("1.20.1") { common(); fabric(); forge() }
    version("1.21.1") { neoforge() }

    // Independent mods in the same repo
    mod("my-addon") {
        version("1.21.1") { neoforge() }
    }
}
```

Directory layout for the above:

```
common/                           # sharedCommon()
versions/
  1.20.1/
    common/
    fabric/
    forge/
  1.21.1/                         # single-loader, src/ at this level
    src/main/java/
modules/
  my-addon/
    versions/
      1.21.1/                     # single-loader module
        src/main/java/
```

---

## Gradle project paths — quick reference

| Layout | Gradle path |
|--------|-------------|
| Shared common | `:common` |
| Version common | `:mcVersion:common` |
| Version loader | `:mcVersion:loaderName` |
| Single-loader version | `:mcVersion` |
| Module version common | `:moduleName:mcVersion:common` |
| Module version loader | `:moduleName:mcVersion:loaderName` |
| Module single-loader version | `:moduleName:mcVersion` |

Loader names: `fabric`, `neoforge`, `forge`, `lexforge`, `legacyforge`.

---

## Notes

**No subproject build files.** The version and module subfolders do not need `build.gradle.kts` files. Prism configures everything from the root project's build file. A build file in a subfolder will be evaluated by Gradle and may conflict with Prism's configuration.

**Common source files are not JARs.** The common subproject's source files are fed directly into each loader's compilation — they are not compiled to a separate JAR and then depended upon. This is the same approach as the MultiLoader Template and avoids all remapping issues.
