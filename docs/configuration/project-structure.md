---
sidebar_position: 1
---

# Project Structure

## Multi-loader layout

When targeting multiple loaders per version, each version has a `common` subproject and loader subprojects:

```
versions/
  1.21.1/
    common/src/main/java/        # shared across loaders
    fabric/src/main/java/        # Fabric-specific
    neoforge/src/main/java/      # NeoForge-specific
```

Settings:
```kotlin
prism {
    version("1.21.1") {
        common()
        fabric()
        neoforge()
    }
}
```

## Single-loader layout

When a version only targets one loader, skip `common()` and put everything in one folder:

```
versions/
  1.21.1/
    src/main/java/               # all code here
    src/main/resources/
```

Settings:
```kotlin
prism {
    version("1.21.1") {
        neoforge()               # no common() = single-loader mode
    }
}
```

In single-loader mode, the version folder IS the project. No common/loader split. The Gradle path is `:1.21.1` instead of `:1.21.1:neoforge`. Build with `./gradlew :1.21.1:build`.

You can mix both modes in the same project:

```kotlin
prism {
    version("1.20.1") {
        common()                 # multi-loader: common + fabric + forge
        fabric()
        forge()
    }
    version("1.21.1") {
        neoforge()               # single-loader: just neoforge
    }
}
```

## Common code

The `common` subproject is compiled against vanilla Minecraft using NeoForm (no loader APIs). Code here is shared across all loaders for that version.

Common source files are not compiled into a separate JAR. Instead, they are fed into each loader's compilation so they get compiled against the full loader classpath. This is the same approach used by the [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template).

## Loader code

Each loader subproject has access to:

- All common code for that version
- The full Minecraft source with loader modifications
- Loader-specific APIs (Fabric API, NeoForge events, Forge events)

## Shared common (cross-version)

By default, each version is fully independent. If you have pure Java code (interfaces, annotations, utilities) that doesn't touch Minecraft APIs and should be shared across all versions, you can enable a root-level `common/` project.

**settings.gradle.kts:**
```kotlin
extensions.configure<PrismSettingsExtension>("prism") {
    sharedCommon()

    version("1.20.1") { common(); fabric(); forge() }
    version("1.21.1") { common(); fabric(); neoforge() }
}
```

This creates a `:common` project at `common/` in the root. Its source is compiled into every version's common project automatically.

```
common/                        <-- shared across ALL versions
  src/main/java/
versions/
  1.20.1/
    common/                    <-- version-specific, depends on shared common
    fabric/
  1.21.1/
    common/
    neoforge/
```

The shared common compiles with the lowest Java version across your targets (e.g. Java 17 if you target 1.20.1). It has no access to Minecraft classes. Use it for things like config interfaces, annotation definitions, or utility methods.

## Independence between versions

Each version folder is independent. The only exception is the optional shared `common/` above. Version-specific common code lives in `versions/{mc}/common/` and has access to vanilla Minecraft classes for that version.

## No subproject build files

The version subfolders do not need `build.gradle.kts` files. Prism configures everything from the root project's build file. If you place a build file in a subfolder, it will be evaluated by Gradle and may conflict with Prism's configuration.

## Gradle project paths

Prism registers subprojects using Minecraft version as the parent:

```
:1.20.1:common
:1.20.1:fabric
:1.20.1:forge
:1.21.1:common
:1.21.1:fabric
:1.21.1:neoforge
```

Use these paths with Gradle commands:

```bash
./gradlew :1.20.1:fabric:build
./gradlew :1.21.1:neoforge:runClient
```
